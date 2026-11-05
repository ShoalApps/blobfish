package io.github.shoalapps.blobfish

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.shoalapps.blobfish.data.*
import io.github.shoalapps.blobfish.ui.ReadingFeedList
import io.github.shoalapps.blobfish.ui.ReadingFeedVm
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Synthetic cache and deferred loader only; never contacts either service. */
class ReadingFeedTest {
    @get:Rule val compose = createComposeRule()

    private fun item(id: String, repost: String? = null) = FeedItem(
        Post(id, "cid", Author("did:test", "test.example", null, null), id, emptyList(), null,
            0, 0, 0, images = emptyList(), card = null, quote = null, myLike = null, myRepost = null, replyRoot = null),
        repost?.let { Author(it, it, null, null) }, null)

    @Test fun cachedAnchorSurvivesPrependAndDuplicateRepostsAndPillReturnsToTop() {
        val owner = "synthetic|${UUID.randomUUID()}"
        val cache = FeedCache(owner, "home", owner)
        val initial = (0..39).map { item("post-$it") }
        cache.write(initial, null)
        Graph.store.saveFeedAnchor(FeedAnchor(owner, "home", "post-20", 17, System.currentTimeMillis()))
        val response = CompletableDeferred<Page<FeedItem>>()
        var active = true
        val vm = ReadingFeedVm(cache, { active }) { response.await() }
        try {
            compose.setContent {
                MaterialTheme {
                    ReadingFeedList(vm) { row -> Text(row.post.text, Modifier.height(80.dp)) }
                }
            }
            compose.waitUntil(5_000) { !vm.loading && !vm.restoring && vm.refreshing }
            compose.onNodeWithText("post-20").assertIsDisplayed()
            val before = compose.onNodeWithText("post-20").fetchSemanticsNode().positionInRoot.y
            // Several reasons for the same underlying post must yield one LazyColumn key.
            response.complete(Page(listOf(item("new-post"), item("new-post", "reposter-a"),
                item("post-0", "reposter-b")) + initial, null))
            compose.waitUntil(5_000) { !vm.refreshing }
            compose.waitForIdle()
            compose.onNodeWithText("post-20").assertIsDisplayed()
            val after = compose.onNodeWithText("post-20").fetchSemanticsNode().positionInRoot.y
            assertEquals(before, after, 1f)
            assertEquals(41, vm.items.size)
            compose.onNodeWithText("1 new post").performClick()
            compose.onNodeWithText("new-post").assertIsDisplayed()
            compose.onNodeWithText("1 new post").assertDoesNotExist()
        } finally {
            active = false
            Graph.store.clearFeedAnchors(owner)
        }
    }

    @Test fun unavailableAnchorFallsBackAfterAtMostThreeSyntheticPages() {
        val owner = "synthetic|${UUID.randomUUID()}"
        val cache = FeedCache(owner, "home", owner)
        Graph.store.saveFeedAnchor(FeedAnchor(owner, "home", "gone", 70, System.currentTimeMillis()))
        var calls = 0
        var active = true
        val vm = ReadingFeedVm(cache, { active }) {
            calls++
            Page(listOf(item("page-$calls")), if (calls < 3) "cursor-$calls" else null)
        }
        try {
            compose.setContent { MaterialTheme { ReadingFeedList(vm) { Text(it.post.text, Modifier.height(80.dp)) } } }
            compose.waitUntil(5_000) { calls >= 3 && !vm.restoring && !vm.refreshing }
            compose.onNodeWithText("page-1").assertIsDisplayed()
            compose.runOnIdle { assertEquals(3, calls); assertEquals(null, vm.error) }
        } finally {
            active = false
            Graph.store.clearFeedAnchors(owner)
        }
    }
}
