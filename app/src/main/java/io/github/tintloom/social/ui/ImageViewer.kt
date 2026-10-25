package io.github.tintloom.social.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.tintloom.social.data.Image

/**
 * A post's photos full screen: swipe between them, pinch or double-tap to zoom, tap to hide the
 * controls. Alt text sits at the bottom so it is never lost.
 */
@Composable
fun ImageViewer(images: List<Image>, start: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { images.size }
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    var chrome by remember { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("image-viewer")) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = zoomed[pager.currentPage] != true,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    images[page],
                    onZoomed = { zoomed[page] = it },
                    onTap = { chrome = !chrome },
                )
            }
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                // A soft fade behind the buttons keeps them readable over bright photos.
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent))),
                )
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                        if (images.size > 1) {
                            Text(
                                "${pager.currentPage + 1} / ${images.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            val current = images[pager.currentPage]
                            IconButton(onClick = { share(context, current.full) }) { Icon(Icons.Rounded.Share, "Share", tint = Color.White) }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                IconButton(onClick = { save(context, current.full) }) { Icon(Icons.Rounded.Download, "Save", tint = Color.White) }
                            }
                        }
                    }
                    val alt = images[pager.currentPage].alt
                    if (alt.isNotBlank()) {
                        Text(
                            alt,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = .6f))
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(image: Image, onZoomed: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset): Offset {
        val maxX = size.width * (scale - 1) / 2
        val maxY = size.height * (scale - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    fun set(s: Float, o: Offset) {
        scale = s.coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else clamp(o)
        onZoomed(scale > 1f)
    }
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // Pinch always zooms; one finger only pans once zoomed in, so swiping still changes photo.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            set(scale * event.calculateZoom(), offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { at ->
                        if (scale > 1f) {
                            set(1f, Offset.Zero)
                        } else {
                            val s = 2.5f
                            // Zoom towards where the finger was.
                            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                            set(s, (centre - at) * (s - 1))
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = image.full,
            contentDescription = image.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Saves to Pictures/Tintloom through the system downloader, which needs no storage permission. */
private fun save(context: Context, url: String) {
    val name = "bluesky-" + url.substringAfterLast('/').substringBefore('@').takeLast(16) + ".jpg"
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Tintloom/$name")
    runCatching {
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        toast(context, "Saving to Pictures")
    }.onFailure { toast(context, "Couldn't save that image") }
}
