@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.swordfishapps.social.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.swordfishapps.social.Graph
import io.github.swordfishapps.social.data.Moderation
import io.github.swordfishapps.social.data.AtpException
import io.github.swordfishapps.social.data.Author
import io.github.swordfishapps.social.data.FeedItem
import io.github.swordfishapps.social.data.Notification
import io.github.swordfishapps.social.data.Page
import io.github.swordfishapps.social.data.Post
import io.github.swordfishapps.social.data.Profile
import io.github.swordfishapps.social.data.Thread
import io.github.swordfishapps.social.data.ThreadNode
import io.github.swordfishapps.social.data.compact
import io.github.swordfishapps.social.data.shortAgo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun friendly(e: Throwable): String = when (e) {
    is AtpException -> e.message ?: "Bluesky had a problem"
    is java.net.UnknownHostException, is java.net.ConnectException -> "You're offline. Check your connection and try again."
    is java.net.SocketTimeoutException -> "Bluesky is taking too long to answer. Try again."
    else -> e.message ?: "Something went wrong"
}

// ---------------- Paged lists ----------------

/** Any cursor-paged list: loads, refreshes and loads more when you near the end. */
class PagedVm<T>(private val load: suspend (String?) -> Page<T>) : ViewModel() {
    var items by mutableStateOf<List<T>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var cursor: String? = null
    private var more = true
    private var busy = false

    init { refresh(first = true) }

    fun refresh(first: Boolean = false) {
        if (!first) refreshing = true
        error = null
        viewModelScope.launch {
            try {
                val p = load(null)
                items = p.items; cursor = p.cursor; more = p.cursor != null
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = friendly(e) }
            loading = false; refreshing = false
        }
    }

    fun loadMore() {
        if (busy || !more || loading) return
        busy = true
        viewModelScope.launch {
            runCatching { load(cursor) }.onSuccess { p -> items = items + p.items; cursor = p.cursor; more = p.cursor != null }
            busy = false
        }
    }
}

@Composable
fun <T> PagedList(
    vm: PagedVm<T>,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
    empty: String = "Nothing here yet",
    row: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = modifier.fillMaxSize()) {
        // A readable column on wide screens, like Bluesky's web app.
        LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 680.dp), state = state, contentPadding = PaddingValues(bottom = 96.dp)) {
            header()
            when {
                vm.loading -> item { CenterLoading() }
                vm.error != null && vm.items.isEmpty() -> item { Message(vm.error!!, "Try again") { vm.refresh() } }
                vm.items.isEmpty() -> item { Message(empty) }
                else -> items(vm.items) { row(it) }
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, nav: Nav, following: Boolean = false) {
    val prefs = Graph.store.moderation.collectAsState().value
    // Reply, repost and quote options are for the Following timeline only, as in Bluesky's app.
    if (following && Moderation.hiddenInFeed(item, prefs)) return
    PostCard(item.post, nav, repostedBy = item.repostedBy, replyTo = item.replyTo)
}

// ---------------- Home ----------------

@Composable
fun HomeScreen(nav: Nav, onSearch: () -> Unit, onCompose: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val discover = viewModel(key = "discover") { PagedVm { c -> Graph.atp.feed(Graph.atp.discoverFeed, c) } }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Wordmark() },
                    actions = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") } },
                )
                if (session != null) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Following") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Discover") })
                    }
                }
            }
        },
        floatingActionButton = {
            if (session != null) FloatingActionButton(onClick = onCompose) { Icon(Icons.Rounded.Edit, "New post") }
        },
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding())) {
            if (session != null && tab == 0) {
                val following = viewModel(key = "following-${session!!.did}") { PagedVm { c -> Graph.atp.timeline(c) } }
                PagedList(following, empty = "Follow some people to fill your timeline") { FeedRow(it, nav, following = true) }
            } else {
                PagedList(discover) { FeedRow(it, nav) }
            }
        }
    }
}

/** "Swordfish" with the swordfish on the app's cookie mark. */
@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
        Box(Modifier.size(30.dp).clip(shape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.ui.res.painterResource(io.github.swordfishapps.social.R.drawable.ic_launcher_mono), null, Modifier.requiredSize(50.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text("Swordfish", style = MaterialTheme.typography.titleLargeEmphasized, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---------------- Search ----------------

class SearchVm : ViewModel() {
    var query by mutableStateOf("")
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var people by mutableStateOf<List<Author>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun search(q: String = query) {
        if (q.isBlank()) return
        query = q
        loading = true; error = null
        viewModelScope.launch {
            launch { people = runCatching { Graph.atp.searchPeople(q) }.getOrDefault(emptyList()) }
            runCatching { Graph.atp.searchPosts(q).items }
                .onSuccess { posts = it }
                .onFailure { error = if (Graph.store.session.value == null) "Sign in to search posts. People search works signed out." else friendly(it) }
            loading = false
        }
    }
}

@Composable
fun SearchScreen(nav: Nav, onBack: () -> Unit, initial: String? = null, vm: SearchVm = viewModel()) {
    LaunchedEffect(initial) { if (initial != null && vm.query != initial) vm.search(initial) }
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    title = {
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = { vm.query = it },
                            placeholder = { Text("Search Bluesky") },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        )
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Posts") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("People") })
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            when {
                vm.loading -> item { CenterLoading() }
                tab == 0 && vm.error != null -> item { Message(vm.error!!) }
                tab == 0 -> items(vm.posts) { PostCard(it, nav) }
                else -> items(vm.people) { a -> PersonRow(a) { nav.profile(a.did) } }
            }
        }
    }
}

@Composable
fun PersonRow(a: Author, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(a.avatar, 44.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("@${a.handle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- Notifications ----------------

@Composable
fun NotificationsScreen(nav: Nav, onSignIn: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { TopAppBar(title = { Text("Notifications") }) }) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding())) {
            if (session == null) {
                Message("Sign in to see who's liked, reposted and replied to you.", "Sign in", onSignIn)
            } else {
                val vm = viewModel(key = "notifs-${session!!.did}") { PagedVm { c -> Graph.atp.notifications(c) } }
                PagedList(vm, empty = "No notifications yet") { n -> NotificationRow(n, nav) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, nav: Nav) {
    val (icon, verb) = when (n.reason) {
        "like" -> Icons.Rounded.Favorite to "liked your post"
        "repost" -> Icons.Rounded.Repeat to "reposted your post"
        "follow" -> Icons.Rounded.PersonAdd to "followed you"
        "reply" -> Icons.Rounded.Edit to "replied"
        "mention" -> Icons.Rounded.Edit to "mentioned you"
        "quote" -> Icons.Rounded.Repeat to "quoted your post"
        else -> Icons.Rounded.Edit to n.reason
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { n.subject?.takeIf { it.startsWith("at://") && "/app.bsky.feed.post/" in it }?.let(nav.post) ?: nav.profile(n.author.did) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 14.dp)) {
            Avatar(n.author.avatar, 32.dp, Modifier.clickable { nav.profile(n.author.did) })
            Text(
                "${n.author.name} $verb · ${shortAgo(n.at)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            n.text?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
        }
    }
}

// ---------------- Thread ----------------

class ThreadVm(private val uri: String) : ViewModel() {
    var thread by mutableStateOf<Thread?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    init { load() }
    fun load() {
        error = null
        viewModelScope.launch { runCatching { Graph.atp.thread(uri) }.onSuccess { thread = it }.onFailure { error = friendly(it) } }
    }
}

@Composable
fun ThreadScreen(uri: String, nav: Nav, onBack: () -> Unit) {
    val vm = viewModel(key = uri) { ThreadVm(uri) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Post") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            val t = vm.thread
            when {
                vm.error != null -> item { Message(vm.error!!, "Try again") { vm.load() } }
                t == null -> item { CenterLoading() }
                else -> {
                    items(t.parents) { PostCard(it, nav, noteWhenHidden = true) }
                    item { PostCard(t.focus.post, nav, big = true) }
                    replies(t.focus.replies, nav, depth = 0)
                }
            }
        }
    }
}

private fun LazyListScope.replies(nodes: List<ThreadNode>, nav: Nav, depth: Int) {
    nodes.forEach { n ->
        item(key = n.post.uri) {
            Box(Modifier.padding(start = (depth * 16).coerceAtMost(64).dp)) { PostCard(n.post, nav, noteWhenHidden = true) }
        }
        if (depth < 4) replies(n.replies, nav, depth + 1)
    }
}

// ---------------- Profile ----------------

class ProfileVm(private val actor: String) : ViewModel() {
    var profile by mutableStateOf<Profile?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    val posts = PagedVm { c -> Graph.atp.authorFeed(actor, c) }
    init { load() }
    fun load() { viewModelScope.launch { runCatching { Graph.atp.profile(actor) }.onSuccess { profile = it }.onFailure { error = friendly(it) } } }
}

@Composable
fun ProfileScreen(actor: String, nav: Nav, onBack: (() -> Unit)?, onSettings: (() -> Unit)? = null) {
    val vm = viewModel(key = "profile-$actor") { ProfileVm(actor) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by Graph.store.session.collectAsState()
    var followed by remember(vm.profile) { mutableStateOf(vm.profile?.myFollow) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(vm.profile?.author?.name.orEmpty(), maxLines = 1) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
    ) { pad ->
        PagedList(vm.posts, Modifier.padding(top = pad.calculateTopPadding()), header = {
            item {
                val p = vm.profile
                if (p == null) {
                    if (vm.error != null) Message(vm.error!!, "Try again") { vm.load() } else CenterLoading()
                    return@item
                }
                Column {
                    Box {
                        AsyncImage(
                            model = p.banner, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Box(Modifier.padding(start = 16.dp).offset(y = 44.dp).align(Alignment.BottomStart)) {
                            MorphingAvatar(p.author.avatar, 88.dp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        if (session != null && session!!.did != p.author.did) {
                            if (followed == null) {
                                Button(onClick = {
                                    followed = "pending"
                                    scope.launch { runCatching { followed = Graph.atp.follow(p.author.did) }.onFailure { followed = null; toast(context, friendly(it)) } }
                                }) { Text("Follow") }
                            } else {
                                androidx.compose.material3.FilledTonalButton(onClick = {
                                    val was = followed!!
                                    followed = null
                                    scope.launch { runCatching { Graph.atp.undo(was) }.onFailure { followed = was; toast(context, friendly(it)) } }
                                }) { Text("Following") }
                            }
                        } else Spacer(Modifier.height(40.dp))
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(p.author.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text("@${p.author.handle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${compact(p.followers)} followers · ${compact(p.follows)} following · ${compact(p.posts)} posts",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        p.description?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(8.dp))
                            RichTextView(it, emptyList(), nav)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        }) { FeedRow(it, nav) }
    }
}

/** The profile picture in a slowly morphing, turning Material shape, like Swordfish Video's About page. */
@Composable
fun MorphingAvatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf) }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "avatar")
    val p by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    val turn by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(30_000, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
    Box(
        Modifier.size(size).clip(MorphShape(morph, p, turn)).background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

// ---------------- You ----------------

@Composable
fun YouScreen(nav: Nav, onSignIn: () -> Unit, onSettings: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    val s = session
    if (s != null) {
        ProfileScreen(s.did, nav, onBack = null, onSettings = onSettings)
        return
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = {}, actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") } }) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val shape = remember { PolygonShape(MaterialShapes.Clover8Leaf) }
            Box(Modifier.size(112.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer))
            Text("Join the conversation", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(top = 20.dp))
            Text(
                "Sign in with your Bluesky account to post, reply, like and see your timeline.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Button(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

// ---------------- Sign in ----------------

@Composable
fun SignInScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Sign in to Bluesky") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).padding(24.dp).imePadding()) {
            Text(
                "Use an app password, not your main password. Create one in Bluesky under Settings → Privacy and security → App passwords.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { openLink(context, "https://bsky.app/settings/app-passwords") }) { Text("Open app passwords") }
            OutlinedTextField(
                handle, { handle = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Handle or email") }, placeholder = { Text("you.bsky.social") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("App password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                enabled = !busy && handle.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { Graph.atp.signIn(handle, password) }
                            .onSuccess { Graph.syncModeration(); Graph.syncProfile(); onDone() }
                            .onFailure { error = friendly(it) }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp),
            ) { if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(28.dp)) else Text("Sign in") }
        }
    }
}

// ---------------- Composer ----------------

/** New post or reply, with a live preview of the formatting. */
@Composable
fun Composer(replyTo: Post?, nav: Nav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val limit = 300
    val count = text.codePointCount(0, text.length)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel") }
                Text(if (replyTo != null) "Reply to ${replyTo.author.name}" else "New post", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${limit - count}", color = if (count > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !busy && text.isNotBlank() && count <= limit,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { Graph.atp.post(text.trim(), replyTo) }
                                .onSuccess { toast(context, "Posted"); onDismiss() }
                                .onFailure { toast(context, friendly(it)) }
                            busy = false
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text("Post") }
            }
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("What's up? Use ``` for code, **bold**, *italic*") },
                minLines = 4,
            )
            if (text.isNotBlank()) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                RichTextView(text, emptyList(), nav)
            }
        }
    }
}

// ---------------- Settings & About ----------------

internal class GroupBuilder {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()
    fun row(content: @Composable (ListItemShapes) -> Unit) { rows += content }
}

internal fun LazyListScope.group(title: String, build: GroupBuilder.() -> Unit) {
    val rows = GroupBuilder().apply(build).rows
    if (rows.isEmpty()) return
    item(key = "t-$title") {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
    }
    rows.forEachIndexed { i, r ->
        item(key = "$title-$i") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2)) { r(ListItemDefaults.segmentedShapes(i, rows.size)) }
        }
    }
}

@Composable
internal fun tileColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun Toggle(title: String, body: String?, checked: Boolean, shapes: ListItemShapes, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        checked = checked, onCheckedChange = onChange, shapes = shapes,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
internal fun Link(title: String, body: String?, shapes: ListItemShapes, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, shapes = shapes,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignIn: () -> Unit, onModeration: () -> Unit) {
    val context = LocalContext.current
    val store = Graph.store
    val s by store.settings.collectAsState()
    val session by store.session.collectAsState()
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            group("Account") {
                val sess = session
                if (sess != null) {
                    row { sh -> Link("Signed in as @${sess.handle}", "Sign out of this account", sh) { store.setSession(null) } }
                } else {
                    row { sh -> Link("Sign in to Bluesky", "Post, reply, like and see your timeline", sh, onSignIn) }
                }
            }
            group("Appearance") {
                row { sh ->
                    Surface(shape = sh.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                            val labels = listOf("Auto", "Light", "Dark")
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                labels.forEachIndexed { i, l ->
                                    SegmentedButton(selected = s.theme == i, onClick = { store.updateSettings { it.copy(theme = i) } }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l) }
                                }
                            }
                        }
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    row { sh -> Toggle("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor, sh) { v -> store.updateSettings { it.copy(dynamicColor = v) } } }
                }
            }
            group("Posts") {
                row { sh -> Toggle("Rich formatting", "Show code blocks, bold and italic in posts, like mu", s.richFormatting, sh) { v -> store.updateSettings { it.copy(richFormatting = v) } } }
            }
            group("Moderation") {
                row { sh -> Link("Moderation", "Content filters, muted words, hidden posts, muted accounts, replies and reposts", sh, onModeration) }
            }
            group("Updates") {
                if (io.github.swordfishapps.social.SwordfishManager.installed(context)) {
                    row { sh -> Link("Managed by Swordfish Manager", "Updates are checked and installed by Swordfish Manager", sh) { io.github.swordfishapps.social.SwordfishManager.checkForUpdates(context) } }
                } else {
                    row { sh -> Toggle("Check for updates", "Look for a new version on GitHub once a day", s.autoUpdate, sh) { v -> store.updateSettings { it.copy(autoUpdate = v) } } }
                }
                row { sh -> Link("Check for updates now", "Version $version · opens Swordfish Manager", sh) { io.github.swordfishapps.social.SwordfishManager.checkForUpdates(context) } }
            }
            group("About") {
                row { sh -> Link("Source code", "github.com/SwordfishApps/social", sh) { openLink(context, "https://github.com/SwordfishApps/social") } }
                row { sh -> Link("Swordfish", "swordfishapps.github.io: all the apps and their credits", sh) { openLink(context, "https://swordfishapps.github.io") } }
                row { sh ->
                    Link("GNU GPL v3 or later", "Free software. Not affiliated with or endorsed by Bluesky Social PBC.", sh) {
                        openLink(context, "https://www.gnu.org/licenses/gpl-3.0.html")
                    }
                }
            }
            group("Built with") {
                listOf(
                    "AT Protocol" to "https://atproto.com",
                    "mu (rich formatting idea)" to "https://mu.social",
                    "Jetpack Compose & Material 3" to "https://developer.android.com/compose",
                    "OkHttp" to "https://square.github.io/okhttp/",
                    "Coil" to "https://coil-kt.github.io/coil/",
                    "kotlinx.serialization" to "https://github.com/Kotlin/kotlinx.serialization",
                ).forEach { (name, url) -> row { sh -> Link(name, null, sh) { openLink(context, url) } } }
            }
        }
    }
}
