package io.github.swordfishapps.social.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.swordfishapps.social.Graph
import io.github.swordfishapps.social.data.Author
import io.github.swordfishapps.social.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Someone's followers ("followers") or the people they follow ("follows"). */
@Composable
fun PeopleScreen(kind: String, actor: String, nav: Nav, onBack: () -> Unit) {
    val vm = viewModel(key = "$kind-$actor") {
        PagedVm<Author> { c -> if (kind == "followers") Graph.atp.followers(actor, c) else Graph.atp.follows(actor, c) }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (kind == "followers") "Followers" else "Following") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        PagedList(vm, Modifier.padding(top = pad.calculateTopPadding()), empty = if (kind == "followers") "No followers yet" else "Not following anyone yet") { a ->
            PersonRow(a) { nav.profile(a.did) }
        }
    }
}

/**
 * Edit your Bluesky profile: picture, banner, name and bio. Saved to your account on Bluesky's
 * servers, so every Bluesky app shows the change.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = Graph.store.session.value
    var profile by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var newAvatar by remember { mutableStateOf<Uri?>(null) }
    var newBanner by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(session?.did) {
        val did = session?.did ?: return@LaunchedEffect
        runCatching { Graph.atp.profile(did) }.onSuccess { p ->
            profile = p
            name = p.author.displayName.orEmpty()
            bio = p.description.orEmpty()
        }.onFailure { toast(context, friendly(it)) }
    }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> newAvatar = u } }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> newBanner = u } }
    val images = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    Button(enabled = profile != null && !saving, modifier = Modifier.padding(end = 8.dp), onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                val avatarBlob = newAvatar?.let { upload(context, it, maxSide = 1000) }
                                val bannerBlob = newBanner?.let { upload(context, it, maxSide = 3000) }
                                Graph.atp.updateProfile(name.trim(), bio.trim(), avatarBlob, bannerBlob)
                            }.onSuccess {
                                Graph.syncProfile()
                                toast(context, "Profile saved")
                                onBack()
                            }.onFailure { toast(context, friendly(it)) }
                            saving = false
                        }
                    }) { Text(if (saving) "Saving…" else "Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())
                .wrapContentWidth().widthIn(max = 680.dp),
        ) {
            Box {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.primaryContainer).clickable { pickBanner.launch(images) }) {
                    AsyncImage(newBanner ?: profile?.banner, "Banner", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    CameraBadge(Modifier.align(Alignment.Center))
                }
                Box(Modifier.padding(start = 16.dp).offset(y = 44.dp).align(Alignment.BottomStart).size(90.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(3.dp).clip(CircleShape).clickable { pickAvatar.launch(images) }) {
                    AsyncImage(newAvatar ?: profile?.author?.avatar, "Profile picture", Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop)
                    CameraBadge(Modifier.align(Alignment.Center))
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 32.dp)) {
                OutlinedTextField(name, { if (it.length <= 64) name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    bio, { if (it.length <= 256) bio = it }, label = { Text("Description") },
                    supportingText = { Text("${bio.length}/256") }, minLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraBadge(modifier: Modifier) {
    Box(modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(20.dp), tint = Color.White)
    }
}

/** Shrinks a picked image to Bluesky's limits (1 MB) as JPEG and uploads it. */
private suspend fun upload(context: android.content.Context, uri: Uri, maxSide: Int) = withContext(Dispatchers.IO) {
    // Works on every Android version: read the size, decode at a fraction, then scale to fit.
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)!!.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IllegalArgumentException("That picture couldn't be read")
    val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    var quality = 90
    var bytes: ByteArray
    do {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 10
    } while (bytes.size > 950_000 && quality > 30)
    Graph.atp.uploadBlob(bytes, "image/jpeg")
}
