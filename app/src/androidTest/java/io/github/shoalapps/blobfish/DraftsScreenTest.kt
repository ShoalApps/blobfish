package io.github.shoalapps.blobfish

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.blobfish.data.Draft
import io.github.shoalapps.blobfish.data.Session
import io.github.shoalapps.blobfish.ui.DraftsScreen
import io.github.shoalapps.blobfish.ui.Nav
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DraftsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val nav = Nav(post = {}, profile = {}, tag = {}, reply = {})
    private val session = Session(did = "test-did", handle = "test-handle", access = "test-access", refresh = "test-refresh", pds = "test-pds")
    private val owner get() = session.did + "|" + session.pds

    @Before fun signIn() { Graph.store.setSession(session) }
    @After fun signOut() {
        Graph.drafts.discard(owner, null)
        Graph.drafts.discard("other|account", null)
        Graph.store.setSession(null)
    }

    @Test fun myDraftOpensAgainAndOnlyMineShow() {
        Graph.drafts.save(owner, Draft("Mine to finish"))
        Graph.drafts.save("other|account", Draft("Someone else's"))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Mine to finish"), 5000)
        rule.onNodeWithText("New post · 0 pictures").assertExists()
        rule.onNodeWithText("Someone else's").assertDoesNotExist()
        rule.onNodeWithText("Mine to finish").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Discard draft"), 5000)
    }

    @Test fun deletingRemovesIt() {
        Graph.drafts.save(owner, Draft("Going away"))
        rule.setContent { DraftsScreen(nav) {} }
        rule.waitUntilAtLeastOneExists(hasText("Delete draft"), 5000)
        rule.onNodeWithText("Delete draft").performClick()
        rule.waitUntilAtLeastOneExists(hasText("No drafts"), 5000)
    }
}
