package io.github.shoalapps.blobfish

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.common.ui.SupportCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared library's card, on this app's own versions of Compose and Material. */
@RunWith(AndroidJUnit4::class)
class SupportCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theSharedCardShowsAndItsButtonWorks() {
        var donated = 0
        compose.setContent {
            MaterialTheme {
                SupportCard("Support Shoal", "Keeps it going.", "Donate", "Donate on Liberapay", Icons.Rounded.Favorite) { donated++ }
            }
        }
        compose.onNodeWithText("Support Shoal").assertIsDisplayed()
        compose.onNodeWithText("Donate").performClick()
        compose.runOnIdle { assertEquals(1, donated) }
    }
}
