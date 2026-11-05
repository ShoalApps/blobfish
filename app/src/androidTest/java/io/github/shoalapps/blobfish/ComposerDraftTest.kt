package io.github.shoalapps.blobfish

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.blobfish.data.Author
import io.github.shoalapps.blobfish.data.Post
import io.github.shoalapps.blobfish.ui.Composer
import io.github.shoalapps.blobfish.ui.Nav
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What's typed, ignoring the placeholder, which also counts as text. */
private val emptyBox = androidx.compose.ui.test.SemanticsMatcher.expectValue(
    androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(""),
)

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ComposerDraftTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val nav = Nav(post = {}, profile = {}, tag = {}, reply = {})

    private val owner = "bluesky|signed-out"

    @org.junit.Before fun clean() {
        Graph.store.setSession(null)
        Graph.drafts.list(owner).forEach { Graph.drafts.discard(owner, it.draft.replyTo, it.draft.id) }
    }
    @org.junit.After fun cleanup() = clean()

    @Test fun newPostStartsEmptyAndKeepsTheWaitingDraft() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) Composer(null, nav) { open = false } }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).performTextInput("A thought worth keeping")
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        val waiting = Graph.drafts.list(owner).single().draft
        rule.runOnIdle { open = true }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).assert(emptyBox)
        rule.onNode(hasSetTextAction()).performTextInput("Another thought")
        rule.onNodeWithText("Discard draft").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(waiting, Graph.drafts.list(owner).single().draft)
        rule.runOnIdle { open = true }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(waiting, Graph.drafts.list(owner).single().draft)
    }

    @Test fun replyStillReopensAutomaticallyUntilDiscarded() {
        val reply = Post("at://reply", "cid", Author("did:author", "author", null, null),
            "Original", emptyList(), null, 0, 0, 0, images = emptyList(), card = null,
            quote = null, myLike = null, myRepost = null, replyRoot = null)
        var open by mutableStateOf(true)
        rule.setContent { if (open) Composer(reply, nav) { open = false } }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).performTextInput("A reply worth keeping")
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        rule.runOnIdle { open = true }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).assertTextEquals("A reply worth keeping")
        rule.onNodeWithText("Discard draft").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertNull(Graph.drafts.load(owner, reply.uri))
        rule.runOnIdle { open = true }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).assert(emptyBox)
    }
}
