package io.github.shoalapps.blobfish

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.blobfish.ui.XBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** A reply must never fall through to the unverified new-post web intent. */
@RunWith(AndroidJUnit4::class)
class ReplySafetyTest {
    @Test fun twitterReplyIsRejectedBeforeOpeningTheComposer() = runBlocking {
        assertFalse(XBridge.compose("This must not be posted", "123456789"))
    }
}
