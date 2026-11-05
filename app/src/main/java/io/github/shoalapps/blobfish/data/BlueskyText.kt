package io.github.shoalapps.blobfish.data

import java.text.BreakIterator
import java.util.Locale

/** Lexicon string limits: maxGraphemes and UTF-8 maxLength, respectively. */
object BlueskyText {
    val post = Limit(300, 3000)
    val displayName = Limit(64, 640)
    val bio = Limit(256, 2560)

    data class Limit(val graphemes: Int, val bytes: Int) {
        fun accepts(text: String): Boolean = graphemeCount(text) <= graphemes && fitsBytes(text)
        fun fitsBytes(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size <= bytes
    }
}

/**
 * Count user-perceived characters, without Android dependencies or locale-sensitive word rules.
 * BreakIterator supplies character boundaries (including combining marks, Hangul and CRLF).
 * Older Android/JVM iterators split emoji sequences, so apply the extended grapheme rules for
 * modifiers, variation selectors, tags, ZWJ pictographs and pairs of regional indicators too.
 */
fun graphemeCount(text: String): Int {
    if (text.isEmpty()) return 0
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    var count = 1
    var offset = Character.charCount(text.codePointAt(0))
    var previous = text.codePointAt(0)
    var regionalRun = if (regional(previous)) 1 else 0
    while (offset < text.length) {
        val current = text.codePointAt(offset)
        val boundary = when {
            previous == 0x0D && current == 0x0A -> false
            control(previous) || control(current) -> true
            extend(current) || current == 0x200D -> false
            regional(previous) && regional(current) -> regionalRun % 2 == 0
            previous == 0x200D && pictograph(current) -> {
                var before = offset - 1 // ZWJ is one UTF-16 unit.
                while (before > 0 && extend(text.codePointBefore(before))) {
                    before -= Character.charCount(text.codePointBefore(before))
                }
                before == 0 || !pictograph(text.codePointBefore(before))
            }
            previous == 0x200D -> true
            else -> iterator.isBoundary(offset)
        }
        if (boundary) count++
        regionalRun = if (regional(current)) regionalRun + 1 else 0
        previous = current
        offset += Character.charCount(current)
    }
    return count
}

private fun regional(cp: Int) = cp in 0x1F1E6..0x1F1FF
private fun extend(cp: Int): Boolean = when (Character.getType(cp)) {
    Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> true
    else -> cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F || cp in 0xE0100..0xE01EF || cp == 0x200C
}
private fun control(cp: Int): Boolean = cp != 0x200D && !extend(cp) && when (Character.getType(cp)) {
    Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt() -> true
    else -> false
}
private fun pictograph(cp: Int): Boolean = (cp in 0x1F000..0x1FAFF && !regional(cp) && cp !in 0x1F3FB..0x1F3FF) || cp in 0x2600..0x27BF ||
    cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF || cp in 0x2190..0x21FF ||
    cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 ||
    cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
