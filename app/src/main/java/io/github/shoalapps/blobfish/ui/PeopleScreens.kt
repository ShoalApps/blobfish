package io.github.shoalapps.blobfish.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
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
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.data.Author
import io.github.shoalapps.blobfish.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a follower or following list is narrowed down. */
private enum class PeopleFilter(val label: String) { ALL("Everyone"), MUTUALS("Mutuals"), FOLLOW_YOU("Follow you"), NOT_BACK("Don't follow you back") }

/**
 * Someone's followers ("followers") or the people they follow ("follows"), with a search box
 * and filters at the top. Each person shows their bio and how you're connected: whether they
 * follow you, whether you follow them, or both (mutuals).
 */
@Composable
fun PeopleScreen(kind: String, actor: String, nav: Nav, onBack: () -> Unit) {
    val vm = viewModel(key = "$kind-$actor") {
        PagedVm<Author> { c -> if (kind == "followers") Graph.atp.followers(actor, c) else Graph.atp.follows(actor, c) }
    }
    val signedIn = Graph.store.session.collectAsState().value != null
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.ALL) }
    // Follows changed here, so rows update straight away (did -> follow record, or null).
    val changed = remember { mutableStateMapOf<String, String?>() }
    val following = { a: Author -> if (a.did in changed) changed[a.did] != null else a.followUri != null }
    val narrowing = query.isNotBlank() || filter != PeopleFilter.ALL
    // Searching and filtering look through the whole list, so fetch the rest while either is on.
    LaunchedEffect(narrowing, vm.hasMore, vm.loadingMore, vm.loading) {
        if (narrowing && vm.hasMore && !vm.loadingMore && !vm.loading && vm.items.size < 10_000) vm.loadMore()
    }
    val q = query.trim().removePrefix("@").lowercase()
    val shown = remember(vm.items, q, filter, changed.toMap()) {
        vm.items.filter { a ->
            (q.isEmpty() || a.handle.lowercase().contains(q) || a.displayName.orEmpty().lowercase().contains(q) || a.description.orEmpty().lowercase().contains(q)) &&
                when (filter) {
                    PeopleFilter.ALL -> true
                    PeopleFilter.MUTUALS -> a.followedBy && following(a)
                    PeopleFilter.FOLLOW_YOU -> a.followedBy
                    PeopleFilter.NOT_BACK -> following(a) && !a.followedBy
                }
        }
    }
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (kind == "followers") "Followers" else "Following") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 680.dp), state = state, contentPadding = PaddingValues(bottom = 96.dp)) {
                item(key = "search") {
                    OutlinedTextField(
                        query, { query = it },
                        placeholder = { Text(if (kind == "followers") "Search followers" else "Search following") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (signedIn) item(key = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeopleFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                                leadingIcon = if (filter == f) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                            )
                        }
                    }
                }
                if (narrowing && !vm.loading) item(key = "count") {
                    Text(
                        (if (shown.size == 1) "1 person" else "${shown.size} people") + if (vm.hasMore) " so far, still looking…" else "",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                when {
                    vm.loading -> item { CenterLoading() }
                    vm.error != null && vm.items.isEmpty() -> item { Message(vm.error!!, "Try again") { vm.refresh() } }
                    vm.items.isEmpty() -> item { Message(if (kind == "followers") "No followers yet" else "Not following anyone yet") }
                    shown.isEmpty() && !vm.hasMore -> item { Message(if (q.isNotEmpty()) "No one matches \"${query.trim()}\"" else "No one here") }
                    else -> items(shown, key = { it.did }) { a ->
                        PersonCard(
                            a, following(a), signedIn && a.did != Graph.store.session.value?.did,
                            onFollow = {
                                val was = changed[a.did] ?: if (a.did in changed) null else a.followUri
                                if (was == null) {
                                    changed[a.did] = "pending"
                                    vm.viewModelScope.launch { runCatching { Graph.atp.follow(a.did) }.onSuccess { changed[a.did] = it }.onFailure { changed[a.did] = null } }
                                } else {
                                    changed[a.did] = null
                                    vm.viewModelScope.launch { runCatching { if (was != "pending") Graph.atp.undo(was) }.onFailure { changed[a.did] = was } }
                                }
                            },
                            modifier = Modifier.animateItem(),
                        ) { nav.profile(a.did) }
                    }
                }
                if (vm.loadingMore) item(key = "more") { CenterLoading() }
            }
        }
    }
}

/**
 * A person in a list, easy to scan: picture, name, handle, how you're connected and the start of
 * their bio, with a follow button.
 */
@Composable
private fun PersonCard(a: Author, following: Boolean, canFollow: Boolean, onFollow: () -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val relation = when {
        a.followedBy && following -> "Mutuals"
        a.followedBy -> "Follows you"
        following -> "You follow"
        else -> null
    }
    Row(
        modifier.fillMaxWidth().clickable(onClickLabel = "Open profile", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(a.avatar, 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                VerifiedBadge(a, 16.dp)
            }
            Text("@${a.handle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (relation != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (a.followedBy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when { relation == "Mutuals" -> Icons.Rounded.SyncAlt; a.followedBy -> Icons.Rounded.PersonAdd; else -> Icons.Rounded.Check },
                            null, Modifier.size(14.dp),
                            tint = if (a.followedBy) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            relation, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp),
                            color = if (a.followedBy) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            a.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (canFollow) {
            val label = if (following) "Following" else if (a.followedBy) "Follow back" else "Follow"
            if (following) OutlinedButton(onClick = onFollow, modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "Unfollow ${a.name}" }) { Text(label) }
            else Button(onClick = onFollow, modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "$label ${a.name}" }) { Text(label) }
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
private suspend fun upload(context: android.content.Context, uri: Uri, maxSide: Int) = uploadImage(context, uri, maxSide).first

/** Like [upload], also giving the uploaded picture's width and height. */
internal suspend fun uploadImage(context: android.content.Context, uri: Uri, maxSide: Int): Triple<kotlinx.serialization.json.JsonObject, Int, Int> = withContext(Dispatchers.IO) {
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
    Triple(Graph.atp.uploadBlob(bytes, "image/jpeg"), bitmap.width, bitmap.height)
}
