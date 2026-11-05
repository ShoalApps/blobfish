package io.github.shoalapps.blobfish

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.blobfish.data.Draft
import io.github.shoalapps.blobfish.data.Session
import io.github.shoalapps.blobfish.ui.DraftsScreen
import io.github.shoalapps.blobfish.ui.HomeScreen
import io.github.shoalapps.blobfish.ui.Composer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.shoalapps.blobfish.ui.Nav
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What's typed, ignoring the placeholder, which also counts as text. */
private val emptyBox = androidx.compose.ui.test.SemanticsMatcher.expectValue(
    androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(""),
)

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DraftsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val nav = Nav(post = {}, profile = {}, tag = {}, reply = {})
    private val session = Session(did = "test-did", handle = "test-handle", access = "test-access", refresh = "test-refresh", pds = "test-pds")
    private val owner get() = session.did + "|" + session.pds

    private var wasOffline = false
    @Before fun signIn() {
        wasOffline = Graph.store.settings.value.offlineMode
        Graph.store.updateSettings { it.copy(offlineMode = true) }
        Graph.store.setSession(session)
        clearDrafts()
    }
    private fun clearDrafts() {
        listOf(owner, "other|account").forEach { account ->
            Graph.drafts.list(account).forEach { Graph.drafts.discard(account, it.draft.replyTo, it.draft.id) }
        }
    }
    @After fun signOut() {
        rule.runOnIdle { rule.activity.setContent {} }
        rule.waitForIdle()
        clearDrafts()
        Graph.offline.writes.value.filter { it.owner == owner }.forEach { Graph.offline.discard(it.id) }
        Graph.store.updateSettings { it.copy(offlineMode = wasOffline) }
        Graph.store.setSession(null)
    }

    @Test fun myDraftOpensAgainAndOnlyMineShow() {
        val first = Graph.drafts.newStandalone().copy(text = "Mine to finish")
        val second = Graph.drafts.newStandalone().copy(text = "Another of mine")
        Graph.drafts.save(owner, first)
        Graph.drafts.save(owner, second)
        Graph.drafts.save("other|account", Draft("Someone else's"))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Mine to finish"), 5000)
        rule.onNodeWithText("Another of mine").assertExists()
        rule.onNodeWithText("Someone else's").assertDoesNotExist()
        rule.onNodeWithText("Mine to finish").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Discard draft"), 5000)
        rule.onNode(hasSetTextAction()).assertTextEquals("Mine to finish")
        rule.onNodeWithText("Discard draft").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mine to finish").assertDoesNotExist()
        rule.onNodeWithText("Another of mine").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Discard draft"), 5000)
        rule.onNode(hasSetTextAction()).assertTextEquals("Another of mine")
    }

    @Test fun newPostLeavesTheDraftsButtonAvailable() {
        Graph.drafts.save(owner, Graph.drafts.newStandalone().copy(text = "Waiting for me"))
        var composing by mutableStateOf(false)
        var showingDrafts by mutableStateOf(false)
        rule.setContent {
            if (showingDrafts) DraftsScreen(nav) { showingDrafts = false }
            else HomeScreen(nav, onSearch = {}, onCompose = { composing = true }, onDrafts = { showingDrafts = true })
            if (composing) Composer(null, nav) { composing = false }
        }
        rule.waitUntilAtLeastOneExists(hasText("Drafts"), 5000)
        rule.onNodeWithContentDescription("New post").performClick()
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).assert(emptyBox)
        rule.onNodeWithText("Discard draft").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Drafts").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Waiting for me"), 5000)
        rule.onNodeWithText("Waiting for me").performClick()
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).assertTextEquals("Waiting for me")
    }

    @Test fun postingRemovesOnlyTheChosenDraft() {
        val sending = Graph.drafts.newStandalone().copy(text = "Ready to send")
        val waiting = Graph.drafts.newStandalone().copy(text = "Keep this one")
        Graph.drafts.save(owner, sending)
        Graph.drafts.save(owner, waiting)
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Ready to send"), 5000)
        rule.onNodeWithText("Ready to send").performClick()
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNodeWithText("Post", ignoreCase = false).performClick()
        rule.waitUntil(5000) { Graph.drafts.load(owner, null, sending.id) == null }
        rule.waitUntilExactlyOneExists(hasText("Keep this one"), 5000)
        org.junit.Assert.assertEquals(waiting, Graph.drafts.list(owner).single().draft)
        org.junit.Assert.assertTrue(Graph.offline.writes.value.any { it.owner == owner })
    }

    @Test fun changingAccountsHidesRowsAndTheOpenComposer() {
        Graph.drafts.save(owner, Graph.drafts.newStandalone().copy(text = "Private thought"))
        Graph.drafts.save("other|account", Graph.drafts.newStandalone().copy(text = "Other thought"))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Private thought"), 5000)
        rule.onNodeWithText("Private thought").performClick()
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.runOnIdle { Graph.store.setSession(session.copy(did = "other", pds = "account")) }
        rule.waitUntilExactlyOneExists(hasText("Other thought"), 5000)
        rule.onNodeWithText("Private thought").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun aReplyDraftOpensFromWhatWasSavedWithoutFetchingTheParent() {
        val uri = "at://did:example:gone/app.bsky.feed.post/nothing"
        val parent = io.github.shoalapps.blobfish.data.DraftParent(uri, "did:example:gone", "someone.example", "Some One", "The original words")
        Graph.drafts.save(owner, Draft("My reply", uri, replyTarget = io.github.shoalapps.blobfish.data.DraftReply("cid", parent = parent)))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("My reply"), 5000)
        rule.onNodeWithText("My reply").performClick()
        // The post can't be fetched, as it doesn't exist, yet the composer opens with who it replies to.
        rule.waitUntilAtLeastOneExists(hasText("Reply to Some One"), 5000)
        rule.onNodeWithText("Couldn't fetch the original post").assertDoesNotExist()
        Graph.drafts.discard(owner, uri)
    }

    @Test fun deletingRemovesIt() {
        Graph.drafts.save(owner, Graph.drafts.newStandalone().copy(text = "Going away"))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Delete draft"), 5000)
        rule.onNodeWithText("Delete draft").performClick()
        rule.waitUntilAtLeastOneExists(hasText("No drafts"), 5000)
    }
}
