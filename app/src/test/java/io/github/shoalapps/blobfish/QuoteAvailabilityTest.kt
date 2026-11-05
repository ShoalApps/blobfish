package io.github.shoalapps.blobfish

import io.github.shoalapps.blobfish.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class QuoteAvailabilityTest {
    private fun fixture(name: String): JsonObject = Json.parseToJsonElement(
        javaClass.getResource("/compat/$name.json")!!.readText()).jsonObject

    @Test fun unavailableQuotesAreKnownAndRetainDistinctStatesWithAndWithoutMedia() {
        val states = mapOf("deleted" to QuoteAvailability.DELETED, "blocked" to QuoteAvailability.BLOCKED,
            "detached" to QuoteAvailability.DETACHED)
        for ((name, expected) in states) {
            val fixture = fixture("quote-$name")
            val record = fixture.getValue("embed").jsonObject
            val withMedia = buildJsonObject {
                put("\$type", "app.bsky.embed.recordWithMedia#view")
                put("record", record)
                putJsonObject("media") {
                    put("\$type", "app.bsky.embed.images#view")
                    putJsonArray("images") {
                        add(buildJsonObject { put("thumb", "local"); put("fullsize", "local"); put("alt", "A photo") })
                    }
                }
            }
            for (embed in listOf(record, withMedia)) {
                val unknown = mutableListOf<String>()
                val post = BlueskyParse(unknown = { unknown += it }).parsePost(
                    JsonObject(fixture + ("embed" to embed)))!!
                assertEquals(name, expected, post.quoteAvailability)
                assertNull(post.quote)
                assertEquals("A post with a quote", post.text)
                assertTrue(post.unsupportedTypes.isEmpty())
                assertTrue(unknown.isEmpty())
                if (embed == withMedia) assertEquals("A photo", post.images.single().alt)
            }
        }
    }

    @Test fun availableQuoteKeepsItsPostAndHasAvailableState() {
        val base = fixture("quote-deleted")
        val embed = buildJsonObject {
            put("\$type", "app.bsky.embed.record#view")
            putJsonObject("record") {
                put("\$type", "app.bsky.embed.record#viewRecord")
                put("uri", "at://did:plc:other/app.bsky.feed.post/one")
                put("cid", "quote-cid")
                put("author", base.getValue("author"))
                put("value", base.getValue("record"))
                put("indexedAt", "2026-01-01T00:00:00Z")
            }
        }
        val post = BlueskyParse().parsePost(JsonObject(base + ("embed" to embed)))!!
        assertEquals(QuoteAvailability.AVAILABLE, post.quoteAvailability)
        assertEquals("quote-cid", post.quote!!.cid)
        assertTrue(post.unsupportedTypes.isEmpty())
    }

    @Test fun absentAndUnknownQuotesDoNotPretendToBeDeleted() {
        val base = fixture("quote-deleted")
        assertNull(BlueskyParse().parsePost(JsonObject(base - "embed"))!!.quoteAvailability)
        val embed = buildJsonObject {
            put("\$type", "app.bsky.embed.record#view")
            putJsonObject("record") { put("\$type", "app.bsky.embed.record#future") }
        }
        val post = BlueskyParse().parsePost(JsonObject(base + ("embed" to embed)))!!
        assertNull(post.quoteAvailability)
        assertEquals(listOf("app.bsky.embed.record#future"), post.unsupportedTypes)
    }

    @Test fun oldSavedFeedStillDecodesWithoutAvailabilityField() {
        val snapshot = fixture("old-feed-cache")
        val items = Json.decodeFromJsonElement<List<FeedItem>>(snapshot.getValue("items"))
        assertEquals("Cached post", items.single().post.text)
        assertNull(items.single().post.quoteAvailability)
        for (availability in QuoteAvailability.entries) {
            val post = items.single().post.copy(quoteAvailability = availability)
            assertEquals(post, Json.decodeFromJsonElement<Post>(Json.encodeToJsonElement(post)))
        }
    }
}
