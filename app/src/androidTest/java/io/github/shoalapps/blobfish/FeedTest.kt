package io.github.shoalapps.blobfish

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.runner.RunWith

/** Needs the network: reads Bluesky's public Discover feed, signed out. */
@RunWith(AndroidJUnit4::class)
class FeedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun nodes(tag: String): SemanticsNodeInteractionCollection = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
    private fun count(tag: String) = nodes(tag).fetchSemanticsNodes().size

    /** Scrolls until a post with a photo is on screen. */
    private fun findPhoto() {
        compose.waitUntil(30_000) { count("post-actions") > 0 }
        repeat(15) {
            if (count("post-image") > 0) return
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTrue("no photo posts in the feed", count("post-image") > 0)
    }

    @Test fun photosStayInsideTheirPost() {
        findPhoto()
        repeat(6) {
            // Where each one really is, not clipped to the screen (clipping squashes off-screen items together).
            // Only items wholly on screen count: the list keeps partly scrolled-away items around at stale positions.
            // Anything under the top bar is a post scrolled past, left at a stale position: skip it.
            val barBottom = compose.onAllNodesWithText("Blobfish").fetchSemanticsNodes().maxOfOrNull { it.boundsInRoot.bottom } ?: 0f
            fun rects(tag: String) = nodes(tag).fetchSemanticsNodes()
                .filter { it.boundsInRoot.height.toInt() == it.size.height && it.positionInRoot.y >= barBottom }
                .map { androidx.compose.ui.geometry.Rect(it.positionInRoot, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            val images = rects("post-image")
            val actions = rects("post-actions")
            images.forEach { img ->
                actions.forEach { row ->
                    if (img.overlaps(row)) {
                        val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        java.io.File(compose.activity.getExternalFilesDir(null), "overlap.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    assertTrue("a photo ($img) covers a post's buttons ($row)", !img.overlaps(row))
                }
            }
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    @Test fun photoOpensFullScreenAndCloses() {
        findPhoto()
        nodes("post-image").onFirst().performClick()
        compose.waitUntil(5_000) { count("image-viewer") > 0 }
        assertTrue(compose.onAllNodes(hasContentDescription("Share")).fetchSemanticsNodes().isNotEmpty())
        // The buttons fade after a moment, so close with Back, which always works.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { count("image-viewer") == 0 }
        // Back closes it too.
        nodes("post-image").onFirst().performClick()
        compose.waitUntil(5_000) { count("image-viewer") > 0 }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { count("image-viewer") == 0 }
    }
}
