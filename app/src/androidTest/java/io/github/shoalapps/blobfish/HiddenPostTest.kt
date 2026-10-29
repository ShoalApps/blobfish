package io.github.shoalapps.blobfish

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shoalapps.blobfish.data.*
import io.github.shoalapps.blobfish.ui.*
import org.junit.*

class HiddenPostTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: Moderation.Prefs
    @Before fun setPreferences() {
        previous = Graph.store.moderation.value
        Graph.store.setModeration(Moderation.Prefs())
    }
    @After fun restorePreferences() { Graph.store.setModeration(previous) }
    @Test fun hiddenPostCannotBeRevealedFromAnyCard() {
        val post = Post("at://hidden/post/1", "c", Author("did:hidden", "hidden.example", "Hidden author", null),
            "Hidden body", emptyList(), null, 0, 0, 0, images = emptyList(), card = null, quote = null,
            myLike = null, myRepost = null, replyRoot = null, labels = listOf("porn"))
        val nav = Nav({}, {}, {}, {})
        compose.setContent { MaterialTheme { Column { PostCard(post, nav); AnchorPost(post, nav, false); QuoteCard(post, nav) } } }
        compose.onNodeWithText("Hidden author").assertDoesNotExist()
        compose.onNodeWithText("Hidden body").assertDoesNotExist()
        compose.onNodeWithText("Show").assertDoesNotExist()
        compose.onNodeWithTag("post-playable-media").assertDoesNotExist()
    }
}
