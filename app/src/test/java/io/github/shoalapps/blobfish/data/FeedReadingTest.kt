package io.github.shoalapps.blobfish.data

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class FeedReadingTest {
    private data class Row(val id: String, val text: String = id)
    private val policy = FeedReading<Row> { it.id }
    private fun anchor(owner: String = "a", feed: String = "home", id: String = "b", time: Long = 1) =
        FeedAnchor(owner, feed, id, 23, time)

    @Test fun refreshPrependsInOrderAndUpdatesWithoutMovingExistingRows() {
        val result = policy.refresh(listOf(Row("b"), Row("c"), Row("d")),
            listOf(Row("n"), Row("m"), Row("c", "updated"), Row("b"), Row("n")), emptySet(), false)
        assertEquals(listOf("n", "m", "b", "c", "d"), result.items.map { it.id })
        assertEquals("updated", result.items[3].text)
        assertEquals(setOf("n", "m"), result.unseen)
        assertEquals(2, policy.index(result.items, anchor()))
    }

    @Test fun duplicatePagesAndRepeatedRefreshDoNotInflateNewCount() {
        val first = policy.refresh(listOf(Row("b")), listOf(Row("n"), Row("n")), emptySet(), false)
        val second = policy.refresh(first.items, listOf(Row("n")), first.unseen, false)
        assertEquals(1, second.unseen.size)
        assertEquals(listOf(Row("n"), Row("b"), Row("c")), policy.append(second.items, listOf(Row("b"), Row("c"), Row("c"))))
        assertTrue(policy.refresh(second.items, listOf(Row("z")), second.unseen, true).unseen.isEmpty())
        assertTrue(policy.refresh(emptyList(), listOf(Row("z")), emptySet(), false).unseen.isEmpty())
    }

    @Test fun missingAnchorFallsBackAndSeekingStopsAtThreePages() {
        val rows = listOf(Row("c"))
        assertEquals(0, policy.index(rows, anchor()))
        assertTrue(policy.shouldSeek(rows, anchor(), 1, "cursor"))
        assertFalse(policy.shouldSeek(rows, anchor(), 3, "cursor"))
        assertFalse(policy.shouldSeek(rows, anchor(), 1, null))
        assertFalse(policy.shouldSeek(listOf(Row("b")), anchor(), 1, "cursor"))
        assertFalse(policy.shouldSeek(rows, null, 1, "cursor"))
    }

    @Test fun anchorsAreScopedCappedAndSurviveSerialization() {
        var anchors = emptyList<FeedAnchor>()
        repeat(55) { i -> anchors = FeedReading.save(anchors, anchor(feed = "$i", time = i.toLong())) }
        assertEquals(50, anchors.size)
        assertFalse(anchors.any { it.feed == "0" })
        anchors = FeedReading.save(anchors, anchor(feed = "54", time = 60))
        anchors = FeedReading.save(anchors, anchor(owner = "other", feed = "54", time = 61))
        assertEquals(2, anchors.count { it.feed == "54" })
        assertEquals(anchors, FeedReading.decode(FeedReading.encode(anchors)))
        assertEquals(23, anchors.first().offset)
    }

    @Test fun malformedDataIsIgnoredAndInvalidEntriesAreDropped() {
        assertTrue(FeedReading.decode("{broken").isEmpty())
        assertTrue(FeedReading.decode(null).isEmpty())
        assertTrue(FeedReading.decode("{}").isEmpty())
        assertTrue(FeedReading.decode("[{\"owner\":\"a\"}]").isEmpty())
        val good = anchor()
        val raw = FeedReading.encode(listOf(good, good.copy(offset = -1), good.copy(owner = "")))
        assertEquals(listOf(good), FeedReading.decode(raw))
    }

    private fun item(reposter: String? = null): FeedItem = FeedItem(
        Post("at://post/one", "cid", Author("did:a", "a.test", null, null), "hello", emptyList(),
            Instant.parse("2026-01-01T00:00:00Z"), 0, 0, 0, images = emptyList(), card = null,
            quote = null, myLike = null, myRepost = null, replyRoot = null),
        reposter?.let { Author(it, it, null, null) }, null)

    @Test fun originalAndMultipleRepostsUseOneLazyColumnIdentity() {
        val feed = FeedReading<FeedItem> { it.readingKey }
        val rows = feed.unique(listOf(item(), item("alice"), item("bob")))
        assertEquals(1, rows.size)
        val merged = feed.refresh(rows, listOf(item("carol"), item("carol")), emptySet(), false)
        assertEquals(1, merged.items.map { it.readingKey }.toSet().size)
        assertTrue(merged.unseen.isEmpty())
    }

    @Test fun snapshotRoundTripsPostsWithInstantsAndFacets() {
        val original = item().let { it.copy(post = it.post.copy(facets = listOf(RichText.Facet(0, 5, RichText.Facet.Kind.TAG, "hello")))) }
        assertEquals(original, Json.decodeFromString<FeedItem>(Json.encodeToString(original)))
    }
}
