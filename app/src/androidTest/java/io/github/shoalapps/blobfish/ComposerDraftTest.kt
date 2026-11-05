package io.github.shoalapps.blobfish

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.blobfish.ui.Composer
import io.github.shoalapps.blobfish.ui.Nav
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ComposerDraftTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val nav = Nav(post = {}, profile = {}, tag = {}, reply = {})

    @org.junit.Before fun clean() { Graph.drafts.discard("bluesky|signed-out", null) }

    @Test fun unfinishedWordsComeBackUntilDiscarded() {
        var open by mutableStateOf(true)
        rule.setContent { if (open) Composer(null, nav) { open = false } }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.onNode(hasSetTextAction()).performTextInput("A thought worth keeping")
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.runOnIdle { open = false }
        rule.waitForIdle()
        rule.runOnIdle { open = true }
        // It shows twice: in the box and in the live preview.
        rule.waitUntilAtLeastOneExists(hasText("A thought worth keeping"), 5000)
        rule.onNodeWithText("Discard draft").performClick()
        rule.waitForIdle()
        rule.runOnIdle { open = true }
        rule.waitUntilExactlyOneExists(hasSetTextAction(), 5000)
        rule.waitUntil(3000) { rule.onAllNodes(hasText("A thought worth keeping")).fetchSemanticsNodes().isEmpty() }
    }
}
