package io.github.shoalapps.blobfish.data

/**
 * Turns a post's text into blocks using mu's rules (eurosky-social-app, src/lib/code/parse.ts):
 * only inline `code` and multi-line ``` fences are formatting. A one-line ```triple``` is inline
 * code, unbalanced backticks stay literal, and every other character, such as **these**, is shown
 * as the author typed it. Bluesky's own facets are kept. Plain Kotlin, so it can be unit tested.
 */
object RichText {
    sealed interface Block
    data class Paragraph(val text: String, val spans: List<Span>) : Block
    data class Code(val language: String?, val code: String) : Block

    sealed interface Span { val start: Int; val end: Int }
    data class InlineCode(override val start: Int, override val end: Int) : Span
    data class Link(override val start: Int, override val end: Int, val uri: String) : Span
    data class Mention(override val start: Int, override val end: Int, val did: String) : Span
    data class Tag(override val start: Int, override val end: Int, val tag: String) : Span

    /** A facet as Bluesky stores it: a UTF-8 byte range and what it points to. */
    @kotlinx.serialization.Serializable
    data class Facet(val byteStart: Int, val byteEnd: Int, val kind: Kind, val value: String) {
        enum class Kind { LINK, MENTION, TAG }
    }

    // Order matters: 1 = lang and 2 = body of a multi-line fence, 3 = one-line triple, 4 = inline.
    private val code = Regex("""```([^\n`]*)\n([\s\S]*?)\n?```|```([^\n`]+?)```|`([^`\n]+?)`""")

    /** Character index in [text] for a UTF-8 byte offset, as used by facets. */
    fun charIndex(text: String, byteOffset: Int): Int {
        var bytes = 0
        var i = 0
        while (i < text.length && bytes < byteOffset) {
            val cp = text.codePointAt(i)
            bytes += when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            i += Character.charCount(cp)
        }
        return i
    }

    /** UTF-8 byte offset of a character index, for building facets when posting. */
    fun byteIndex(text: String, charIndex: Int): Int = text.substring(0, charIndex.coerceIn(0, text.length)).toByteArray(Charsets.UTF_8).size

    fun parse(text: String, facets: List<Facet> = emptyList(), markdown: Boolean = true): List<Block> {
        // Facets in character positions of the whole post.
        val facetSpans = facets.mapNotNull { f ->
            val s = charIndex(text, f.byteStart)
            val e = charIndex(text, f.byteEnd)
            if (e <= s) null else when (f.kind) {
                Facet.Kind.LINK -> Link(s, e, f.value)
                Facet.Kind.MENTION -> Mention(s, e, f.value)
                Facet.Kind.TAG -> Tag(s, e, f.value)
            }
        }
        val blocks = mutableListOf<Block>()
        var cursor = 0
        for (m in if (markdown) code.findAll(text) else emptySequence()) {
            if (m.groups[2] == null) continue // Inline code stays in its paragraph.
            // A fence that overlaps a link is part of the link, not code.
            if (facetSpans.any { it.start < m.range.last + 1 && it.end > m.range.first }) continue
            if (m.range.first > cursor) paragraph(text, cursor, m.range.first, facetSpans, markdown)?.let(blocks::add)
            blocks += Code(m.groupValues[1].trim().ifBlank { null }, m.groupValues[2].replace("\r\n", "\n").replace('\r', '\n'))
            cursor = m.range.last + 1
        }
        if (cursor < text.length) paragraph(text, cursor, text.length, facetSpans, markdown)?.let(blocks::add)
        return blocks
    }

    /** Builds one paragraph from text[from, to), removing code markers and keeping spans aligned. */
    private fun paragraph(text: String, from: Int, to: Int, facets: List<Span>, markdown: Boolean = true): Paragraph? {
        val raw = text.substring(from, to).trim('\n')
        if (raw.isBlank()) return null
        val offset = from + text.substring(from, to).indexOf(raw)
        val local = facets.filter { it.start >= offset && it.end <= offset + raw.length }.map { shift(it, -offset) }

        // Find code spans that don't cut through a facet; markers are the characters to drop.
        val removed = BooleanArray(raw.length)
        val codeSpans = mutableListOf<InlineCode>()
        for (m in if (markdown) code.findAll(raw) else emptySequence()) {
            val r = m.range
            if (m.groups[2] != null) continue // A fence left literal because a facet runs through it.
            if (local.any { it.start < r.last + 1 && it.end > r.first }) continue
            val inner = (m.groups[3] ?: m.groups[4])?.range ?: continue
            for (i in r.first until inner.first) removed[i] = true
            for (i in inner.last + 1..r.last) removed[i] = true
            for (i in inner) if (raw[i] == '\r') removed[i] = true
            codeSpans += InlineCode(inner.first, inner.last + 1)
        }
        // Map every original index to its position once markers are removed.
        val map = IntArray(raw.length + 1)
        val out = StringBuilder()
        for (i in raw.indices) {
            map[i] = out.length
            if (!removed[i]) out.append(raw[i])
        }
        map[raw.length] = out.length
        val spans = (codeSpans + local).map { remap(it, map) }.filter { it.end > it.start }.sortedBy { it.start }
        return Paragraph(out.toString(), spans)
    }

    private fun shift(s: Span, by: Int): Span = when (s) {
        is InlineCode -> s.copy(start = s.start + by, end = s.end + by)
        is Link -> s.copy(start = s.start + by, end = s.end + by)
        is Mention -> s.copy(start = s.start + by, end = s.end + by)
        is Tag -> s.copy(start = s.start + by, end = s.end + by)
    }

    private fun remap(s: Span, map: IntArray): Span = when (s) {
        is InlineCode -> s.copy(start = map[s.start], end = map[s.end])
        is Link -> s.copy(start = map[s.start], end = map[s.end])
        is Mention -> s.copy(start = map[s.start], end = map[s.end])
        is Tag -> s.copy(start = map[s.start], end = map[s.end])
    }

    // ---- Code highlighting ----

    enum class Token { KEYWORD, STRING, COMMENT, NUMBER }
    data class Highlight(val start: Int, val end: Int, val token: Token)

    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "if", "else", "when", "for", "while", "return", "import", "package",
        "def", "lambda", "let", "const", "function", "async", "await", "new", "public", "private", "static", "void",
        "int", "bool", "true", "false", "null", "None", "True", "False", "nil", "fn", "struct", "enum", "impl", "use",
        "mut", "pub", "match", "in", "is", "not", "and", "or", "from", "as", "try", "catch", "throw", "interface", "type",
        "export", "default", "this", "self", "super", "extends", "override", "suspend", "go", "func", "echo", "then", "fi", "do", "done",
    )
    private val tokens = Regex(
        """(?<comment>//[^\n]*|#[^\n{]*|/\*.*?\*/|--[^\n]*)|(?<string>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`[^`]*`)|(?<number>\b\d+(?:\.\d+)?\b)|(?<word>\b[A-Za-z_]\w*\b)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Small, language-agnostic highlighter: comments, strings, numbers and common keywords. */
    fun highlight(code: String): List<Highlight> = tokens.findAll(code).mapNotNull { m ->
        val r = m.range
        when {
            m.groups["comment"] != null -> Highlight(r.first, r.last + 1, Token.COMMENT)
            m.groups["string"] != null -> Highlight(r.first, r.last + 1, Token.STRING)
            m.groups["number"] != null -> Highlight(r.first, r.last + 1, Token.NUMBER)
            m.value in keywords -> Highlight(r.first, r.last + 1, Token.KEYWORD)
            else -> null
        }
    }.toList()

    // ---- Finding facets when posting ----

    private val urlRegex = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]]""")
    private val mentionRegex = Regex("""(?<![\w@])@([a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,})""")
    private val tagRegex = Regex("""(?<![\w#])#([\p{L}\p{N}_]*[\p{L}_][\p{L}\p{N}_]*)""")

    /** Links, @handles and #tags in a draft, as character ranges (handles still need resolving to DIDs). */
    data class Found(val start: Int, val end: Int, val kind: Facet.Kind, val value: String)

    fun detect(text: String): List<Found> {
        val out = mutableListOf<Found>()
        urlRegex.findAll(text).forEach { out += Found(it.range.first, it.range.last + 1, Facet.Kind.LINK, it.value) }
        mentionRegex.findAll(text).forEach { m ->
            if (out.none { it.start <= m.range.first && it.end > m.range.first }) {
                out += Found(m.range.first, m.range.last + 1, Facet.Kind.MENTION, m.groupValues[1])
            }
        }
        tagRegex.findAll(text).forEach { m ->
            if (out.none { it.start <= m.range.first && it.end > m.range.first }) {
                out += Found(m.range.first, m.range.last + 1, Facet.Kind.TAG, m.groupValues[1])
            }
        }
        return out.sortedBy { it.start }
    }
}
