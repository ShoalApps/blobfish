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

    private fun paragraph(text: String) = RichText.parse(text).single() as RichText.Paragraph
    private fun codeIn(p: RichText.Paragraph) = p.spans.filterIsInstance<RichText.InlineCode>().map { p.text.substring(it.start, it.end) }

    // The next five mirror mu's own src/lib/code/parse.test.ts.
    @Test fun inlineCodeDropsItsBackticks() {
        val p = paragraph("run `npm test` now")
        assertEquals("run npm test now", p.text)
        assertEquals(listOf("npm test"), codeIn(p))
    }

    @Test fun oneLineTripleFenceIsInlineCode() {
        val p = paragraph("see ```some code``` here")
        assertEquals("see some code here", p.text)
        assertEquals(listOf("some code"), codeIn(p))
    }

    @Test fun languageLabelsMayHaveSymbolsAndTrailingSpace() {
        assertEquals("c++", (RichText.parse("```c++\nint x;\n```").single() as RichText.Code).language)
        assertEquals("c#", (RichText.parse("```c#\nint x;\n```").single() as RichText.Code).language)
        assertEquals("ts", (RichText.parse("```ts  \nconst x = 1\n```").single() as RichText.Code).language)
    }

    @Test fun multiLineBodiesKeepTheirLines() {
        assertEquals("a\nb", (RichText.parse("```js\na\nb\n```").single() as RichText.Code).code)
    }

    @Test fun unmatchedOrEmptyBackticksStayLiteral() {
        assertEquals("a lone ` backtick", paragraph("a lone ` backtick").text)
        val empty = paragraph("a `` b")
        assertEquals("a `` b", empty.text)
        assertTrue(empty.spans.isEmpty())
    }

    @Test fun otherMarkdownIsShownAsTyped() {
        val text = "a **bold** and *soft* and _lean_ and ~~gone~~ and # not a heading"
        val p = paragraph(text)
        assertEquals(text, p.text)
        assertTrue(p.spans.isEmpty())
    }

    @Test fun markersInsideCodeStayLiteral() {
        assertEquals(listOf("**x**"), codeIn(paragraph("`**x**`")))
    }

    @Test fun windowsLineEndingsDoNotLeakIntoCode() {
        assertEquals("a\nb", (RichText.parse("```\r\na\r\nb\r\n```").single() as RichText.Code).code.trim())
    }

    @Test fun aFenceThroughALinkIsLeftLiteral() {
        val text = "```\nhttps://example.com\n```"
        val start = RichText.byteIndex(text, 4)
        val p = RichText.parse(text, listOf(Facet(start, start + 19, Facet.Kind.LINK, "https://example.com"))).single() as RichText.Paragraph
        assertEquals(text, p.text)
        assertEquals(1, p.spans.filterIsInstance<RichText.Link>().size)
    }

    @Test fun switchedOffShowsEverythingAsTyped() {
        val text = "`x`\n```\ny\n```"
        assertEquals(text, (RichText.parse(text, markdown = false).single() as RichText.Paragraph).text)
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
        val text = "`hi` @alice.bsky.social"
        val start = RichText.byteIndex(text, 5)
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
