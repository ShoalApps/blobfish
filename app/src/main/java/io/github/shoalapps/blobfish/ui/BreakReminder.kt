package io.github.shoalapps.blobfish.ui

import android.os.SystemClock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.shoalapps.blobfish.LocalisedText as Text
import kotlinx.coroutines.delay

@Composable
internal fun BreakReminder(minutes: Int, onCloseApp: () -> Unit) {
    if (minutes <= 0) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val interval = minutes * 60_000L
    var remaining by rememberSaveable(minutes) { mutableLongStateOf(interval) }
    var visible by rememberSaveable(minutes) { mutableStateOf(false) }

    LaunchedEffect(lifecycle, minutes, visible) {
        if (visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                delay(remaining)
            } finally {
                remaining = (remaining - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
            }
            visible = true
        }
    }

    if (visible) {
        fun resume() {
            remaining = interval
            visible = false
        }
        AlertDialog(
            onDismissRequest = ::resume,
            title = { Text("Time for a break?") },
            text = { Text("You've reached your Butterflyfish break reminder.") },
            confirmButton = { TextButton(onClick = { resume(); onCloseApp() }) { Text("Take a break") } },
            dismissButton = { TextButton(onClick = ::resume) { Text("Keep browsing") } },
        )
    }
}
