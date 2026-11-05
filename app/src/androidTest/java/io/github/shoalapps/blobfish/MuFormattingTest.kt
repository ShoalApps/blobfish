package io.github.shoalapps.blobfish

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shoalapps.blobfish.data.*
import io.github.shoalapps.blobfish.ui.*
import org.junit.*

/** Synthetic posts, no network: formatting follows mu's rules wherever posts appear. */
class MuFormattingTest {
    @get:Rule val compose = createComposeRule()
    private val nav = Nav({}, {}, {}, {})
    private fun post(text: String) = Post("at://did:test/app.bsky.feed.post/one", "cid",
        Author("did:test", "test.example", "Test", null), text, emptyList(), null, 0, 0, 0,
        images = emptyList(), card = null, quote = null, myLike = null, myRepost = null, replyRoot = null)

    @Test fun otherMarkdownIsShownExactlyAsTyped() {
        compose.setContent { MaterialTheme { PostCard(post("**bold** *soft* ~~gone~~"), nav) } }
        compose.onNodeWithText("**bold** *soft* ~~gone~~").assertIsDisplayed()
    }

    @Test fun inlineCodeLosesItsBackticks() {
        compose.setContent { MaterialTheme { PostCard(post("run `npm test` now"), nav) } }
        compose.onNodeWithText("run npm test now").assertIsDisplayed()
    }

    @Test fun fenceBecomesAPanelWithoutItsMarkersOrLabel() {
        compose.setContent { MaterialTheme { PostCard(post("look:\n```kotlin\nval x = 1\n```"), nav) } }
        compose.onNodeWithText("look:").assertIsDisplayed()
        compose.onNodeWithText("val x = 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy code").assertIsDisplayed()
        compose.onNodeWithText("kotlin").assertDoesNotExist()
    }

    @Test fun quotesAndBiosKeepTheirBackticks() {
        compose.setContent { MaterialTheme { RichTextView("see `this`", emptyList(), nav, code = false) } }
        compose.onNodeWithText("see `this`").assertIsDisplayed()
    }
}
