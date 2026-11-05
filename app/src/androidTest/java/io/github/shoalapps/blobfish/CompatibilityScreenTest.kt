package io.github.shoalapps.blobfish

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shoalapps.blobfish.ui.CompatibilityScreen
import org.junit.Rule
import org.junit.Test

/** Uses the actual bundled asset and local counters. No navigation clicks that open a URL. */
class CompatibilityScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bundledCompatibilityScreenIsReadableOffline() {
        compose.setContent { MaterialTheme { CompatibilityScreen(onBack = {}) } }
        compose.onNodeWithText("Staying up to date").assertIsDisplayed()
        compose.onNodeWithText("Bluesky", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Post content Butterflyfish doesn't show yet").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Unrecognised X items"))
        compose.onNodeWithText("Unrecognised X items").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("What's new"))
        compose.onNodeWithText("What's new").assertIsDisplayed()
    }
}
