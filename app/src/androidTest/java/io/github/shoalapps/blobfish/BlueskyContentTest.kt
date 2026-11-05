package io.github.shoalapps.blobfish

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shoalapps.blobfish.data.*
import io.github.shoalapps.blobfish.ui.*
import org.junit.*

/** Synthetic posts and empty image URLs: no account, API or image network requests. */
class BlueskyContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: Moderation.Prefs
    private val nav = Nav({}, {}, {}, {})
    private fun post() = Post("at://did:test/app.bsky.feed.post/one", "cid",
        Author("did:test", "test.example", "Test", null), "Body", emptyList(), null, 0, 0, 0,
        images = emptyList(), card = null, quote = null, myLike = null, myRepost = null, replyRoot = null)

    @Before fun setPreferences() {
        previous = Graph.store.moderation.value
        Graph.store.setModeration(Moderation.Prefs())
    }
    @After fun restorePreferences() { Graph.store.setModeration(previous) }

    @Test fun quotePlaceholdersSayWhyTheQuoteIsUnavailable() {
        compose.setContent {
            MaterialTheme {
                Column {
                    PostQuote(post().copy(quoteAvailability = QuoteAvailability.DELETED), nav)
                    PostQuote(post().copy(quoteAvailability = QuoteAvailability.BLOCKED), nav)
                    PostQuote(post().copy(quoteAvailability = QuoteAvailability.DETACHED), nav)
                }
            }
        }
        compose.onNodeWithText("Post deleted").assertIsDisplayed()
        compose.onNodeWithText("Blocked").assertIsDisplayed()
        compose.onNodeWithText("Removed by the quoted author").assertIsDisplayed()
        compose.onAllNodesWithTag("quote-placeholder").assertCountEquals(3)
        compose.onAllNodesWithTag("quote-placeholder").fetchSemanticsNodes().forEach {
            Assert.assertFalse(it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        }
    }

    @Test fun placeholderAppearsInBothFeedAndThreadPost() {
        val post = post().copy(quoteAvailability = QuoteAvailability.DELETED)
        compose.setContent { MaterialTheme { Column { PostCard(post, nav); AnchorPost(post, nav, false) } } }
        compose.onAllNodesWithText("Post deleted").assertCountEquals(2)
    }

    @Test fun overflowOpensFourthImageAndRemainingImagesAndAltTextAreReachable() {
        val gallery = post().copy(images = (1..6).map { Image("", "", "Photo $it description", 1f) })
        compose.setContent { MaterialTheme { PostCard(gallery, nav) } }
        compose.onAllNodesWithTag("post-image").assertCountEquals(4)
        compose.onNodeWithText("+2").assertIsDisplayed().performClick()
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        compose.onNodeWithText("4 / 6").assertIsDisplayed()
        compose.onNodeWithText("Photo 4 description").assertIsDisplayed()
        compose.onNodeWithTag("image-viewer").performTouchInput { swipeLeft() }
        compose.onNodeWithText("5 / 6").assertIsDisplayed()
        compose.onNodeWithText("Photo 5 description").assertIsDisplayed()
        compose.onNodeWithTag("image-viewer").performTouchInput { swipeLeft() }
        compose.onNodeWithText("6 / 6").assertIsDisplayed()
        compose.onNodeWithText("Photo 6 description").assertIsDisplayed()
        for (page in 5 downTo 1) {
            compose.onNodeWithTag("image-viewer").performTouchInput { swipeRight() }
            compose.onNodeWithText("$page / 6").assertIsDisplayed()
            compose.onNodeWithText("Photo $page description").assertIsDisplayed()
        }
    }

    @Test fun fourImagesHaveNoOverflowBadge() {
        val gallery = post().copy(images = (1..4).map { Image("", "", "Photo $it", 1f) })
        compose.setContent { MaterialTheme { PostCard(gallery, nav) } }
        compose.onAllNodesWithTag("post-image").assertCountEquals(4)
        compose.onNodeWithTag("gallery-overflow").assertDoesNotExist()
    }
}
