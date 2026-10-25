package io.github.shoalapps.blobfish

import io.github.shoalapps.blobfish.data.RichText
import io.github.shoalapps.blobfish.data.RichText.Facet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {
    @Test fun plainTextIsOneParagraph() {
        val p = RichText.parse("hello world").single() as RichText.Paragraph
        assertEquals("hello world", p.text)
        assertTrue(p.spans.isEmpty())
    }

    @Test fun fencedCodeBecomesItsOwnBlock() {
        val blocks = RichText.parse("look:\n```kotlin\nval x = 1\n```\nneat")
        assertEquals(3, blocks.size)
        val code = blocks[1] as RichText.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
        assertEquals("neat", (blocks[2] as RichText.Paragraph).text)
    }

    @Test fun codeWithoutALanguage() {
        val code = RichText.parse("```\nls -la\n```").single() as RichText.Code
        assertEquals(null, code.language)
        assertEquals("ls -la", code.code)
    }

    @Test fun inlineMarkdownDropsItsMarkers() {
        val p = RichText.parse("a **bold** and *soft* and `code` and ~~gone~~").single() as RichText.Paragraph
        assertEquals("a bold and soft and code and gone", p.text)
        val styles = p.spans.filterIsInstance<RichText.Styled>().associate { p.text.substring(it.start, it.end) to it.style }
        assertEquals(RichText.Style.BOLD, styles["bold"])
        assertEquals(RichText.Style.ITALIC, styles["soft"])
        assertEquals(RichText.Style.CODE, styles["code"])
        assertEquals(RichText.Style.STRIKE, styles["gone"])
    }

    @Test fun underscoresInsideWordsAreNotItalic() {
        val p = RichText.parse("snake_case_name stays").single() as RichText.Paragraph
        assertEquals("snake_case_name stays", p.text)
    }

    @Test fun facetsUseUtf8ByteOffsets() {
        // "🎉 " is 5 bytes, so the link starts at byte 5 but character 3.
        val text = "🎉 bsky.app rocks"
        val facet = Facet(5, 13, Facet.Kind.LINK, "https://bsky.app")
        val p = RichText.parse(text, listOf(facet)).single() as RichText.Paragraph
        val link = p.spans.filterIsInstance<RichText.Link>().single()
        assertEquals("bsky.app", p.text.substring(link.start, link.end))
    }

    @Test fun facetsStayAlignedAfterMarkersAreRemoved() {
        val text = "**hi** @alice.bsky.social"
        val start = RichText.byteIndex(text, 7)
        val facet = Facet(start, start + "@alice.bsky.social".length, Facet.Kind.MENTION, "did:plc:alice")
        val p = RichText.parse(text, listOf(facet)).single() as RichText.Paragraph
        assertEquals("hi @alice.bsky.social", p.text)
        val m = p.spans.filterIsInstance<RichText.Mention>().single()
        assertEquals("@alice.bsky.social", p.text.substring(m.start, m.end))
    }

    @Test fun markdownInsideALinkIsLeftAlone() {
        val text = "https://example.com/a_b_c"
        val facet = Facet(0, text.length, Facet.Kind.LINK, text)
        val p = RichText.parse(text, listOf(facet)).single() as RichText.Paragraph
        assertEquals(text, p.text)
    }

    @Test fun byteAndCharIndexesRoundTrip() {
        val text = "héllo 🌍 wörld"
        for (i in listOf(0, 2, 6, 8, text.length)) assertEquals(i, RichText.charIndex(text, RichText.byteIndex(text, i)))
    }

    @Test fun highlighterFindsTheUsualSuspects() {
        val code = "val n = 42 // answer\nprint(\"hi\")"
        val kinds = RichText.highlight(code).associate { code.substring(it.start, it.end) to it.token }
        assertEquals(RichText.Token.KEYWORD, kinds["val"])
        assertEquals(RichText.Token.NUMBER, kinds["42"])
        assertEquals(RichText.Token.COMMENT, kinds["// answer"])
        assertEquals(RichText.Token.STRING, kinds["\"hi\""])
    }

    @Test fun detectsLinksMentionsAndTagsInDrafts() {
        val found = RichText.detect("hi @bob.bsky.social see https://shoalapps.github.io. #android #1")
        assertEquals(listOf(Facet.Kind.MENTION, Facet.Kind.LINK, Facet.Kind.TAG), found.map { it.kind })
        assertEquals("bob.bsky.social", found[0].value)
        assertEquals("https://shoalapps.github.io", found[1].value)
        assertEquals("android", found[2].value)
    }
}
