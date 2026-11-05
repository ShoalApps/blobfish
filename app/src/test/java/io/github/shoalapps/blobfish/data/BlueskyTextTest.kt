package io.github.shoalapps.blobfish.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BlueskyTextTest {
    @Test fun countsExtendedEmojiAndCombiningSequences() {
        assertEquals(0, graphemeCount(""))
        for (cluster in listOf("a", "e\u0301", "a\u0301\u0327", "😀", "👍🏽", "🇬🇧", "👨‍👩‍👧‍👦",
            "👩🏽‍💻", "👩🏽\u0301‍💻", "🏳️‍🌈", "1️⃣", "❤️", "\r\n",
            "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F")) {
            assertEquals(cluster, 1, graphemeCount(cluster))
            assertEquals(cluster, 3, graphemeCount("x${cluster}y"))
        }
    }

    @Test fun flagsPairFromStartOfEachRegionalRunAndZwjDoesNotJoinOrdinaryLetters() {
        assertEquals(2, graphemeCount("🇬🇧🇫🇷"))
        assertEquals(2, graphemeCount("🇬🇧🇫"))
        assertEquals(5, graphemeCount("🇬🇧 x 🇫🇷"))
        assertEquals(2, graphemeCount("a‍b"))
        assertEquals(2, graphemeCount("👩‍a"))
        assertEquals(2, graphemeCount("\n\u0301"))
    }

    @Test fun limitsAllowCombiningTextAndRejectGraphemeAndByteOverflow() {
        for (limit in listOf(BlueskyText.post, BlueskyText.displayName, BlueskyText.bio)) {
            val atLimit = "e\u0301".repeat(limit.graphemes)
            assertEquals(limit.graphemes, graphemeCount(atLimit))
            assertTrue(limit.accepts(atLimit))
            assertFalse(limit.accepts(atLimit + "x"))
            val byteOverflow = "a" + "\u0301".repeat(limit.bytes)
            assertEquals(1, graphemeCount(byteOverflow))
            assertFalse(limit.accepts(byteOverflow))
        }
        assertTrue(BlueskyText.post.accepts("👩🏽‍💻".repeat(100)))
    }

    @Test fun limitsMatchCheckedInLexicons() {
        val root = listOf(File("../compat/bluesky/lexicons"), File("compat/bluesky/lexicons")).first { it.isDirectory }
        fun properties(name: String) = Json.parseToJsonElement(File(root, "$name.json").readText())
            .jsonObject.getValue("defs").jsonObject.getValue("main").jsonObject
            .getValue("record").jsonObject.getValue("properties").jsonObject
        val fields = listOf(BlueskyText.post to properties("app.bsky.feed.post").getValue("text"),
            BlueskyText.displayName to properties("app.bsky.actor.profile").getValue("displayName"),
            BlueskyText.bio to properties("app.bsky.actor.profile").getValue("description"))
        for ((limit, field) in fields) {
            assertEquals(field.jsonObject.getValue("maxGraphemes").jsonPrimitive.int, limit.graphemes)
            assertEquals(field.jsonObject.getValue("maxLength").jsonPrimitive.int, limit.bytes)
        }
    }
}
