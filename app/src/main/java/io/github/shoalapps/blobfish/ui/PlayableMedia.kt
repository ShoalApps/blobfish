@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.shoalapps.blobfish.ui

import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.github.shoalapps.blobfish.LocalisedText as Text
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
import io.github.shoalapps.blobfish.data.PlayableMedia

/** Streams are prepared only after a tap. No feed autoplay, prefetch or hidden audio. */
@Composable
fun PlayableMediaCard(media: PlayableMedia, originalUrl: String, modifier: Modifier = Modifier) {
    var playing by remember(media) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(if (media.audio) 3f else (media.aspect ?: (16f / 9)).coerceIn(.6f, 3f))
        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable { playing = true }.testTag("post-playable-media"), contentAlignment = Alignment.Center) {
        media.thumbnail?.let { AsyncImage(it, media.alt.ifBlank { null }, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        FilledIconButton(onClick = { playing = true }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.PlayArrow, if (media.audio) "Play audio" else "Play video", Modifier.size(32.dp))
        }
    }
    if (playing) MediaDialog(media, originalUrl) { playing = false }
}

@Composable
private fun MediaDialog(media: PlayableMedia, originalUrl: String, close: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember(media) { mutableStateOf(false) }
    val player = remember(media) {
        val connection = context.getSystemService(ConnectivityManager::class.java)
        val saver = connection.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            val height = if (saver) 360 else if (connection.isActiveNetworkMetered) 720 else Int.MAX_VALUE
            trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, height).build()
            repeatMode = if (media.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (media.loop) volume = 0f
            addListener(object : Player.Listener {
                override fun onPlayerError(exception: PlaybackException) { error = true }
            })
            media.source(connection.isActiveNetworkMetered, saver)?.let {
                setMediaItem(MediaItem.Builder().setUri(it.url).setMimeType(it.mimeType).build())
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close) { Text("Close", color = Color.White) }
            }
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0; setShowSubtitleButton(true) } },
                update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxWidth().weight(1f).testTag("media-player"))
            if (error) {
                Text("This stream could not be played. Try again or open the original post.", color = Color.White, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { error = false; player.prepare(); player.play() }) { Text("Try again") }
            }
            TextButton(onClick = { player.pause(); openOriginalMedia(context, originalUrl) }) { Text("Open original post") }
        }
    }
}
