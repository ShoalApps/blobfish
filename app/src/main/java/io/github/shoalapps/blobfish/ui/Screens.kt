@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.blobfish.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.data.Moderation
import io.github.shoalapps.blobfish.data.AtpException
import io.github.shoalapps.blobfish.data.Author
import io.github.shoalapps.blobfish.data.FeedItem
import io.github.shoalapps.blobfish.data.Notification
import io.github.shoalapps.blobfish.data.Page
import io.github.shoalapps.blobfish.data.Post
import io.github.shoalapps.blobfish.data.Profile
import io.github.shoalapps.blobfish.data.Thread
import io.github.shoalapps.blobfish.data.ThreadNode
import io.github.shoalapps.blobfish.data.compact
import io.github.shoalapps.blobfish.data.shortAgo
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
    /** More pages are waiting to be loaded. */
    var hasMore by mutableStateOf(true); private set
    var loadingMore by mutableStateOf(false); private set
    private var cursor: String? = null
    private var more = true
        set(v) { field = v; hasMore = v }
    private var busy = false
        set(v) { field = v; loadingMore = v }

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
    val parent = item.parent
    if (parent != null && Moderation.decide(parent, prefs, Graph.store.session.value != null) !is Moderation.Decision.Hide) {
        // Like Bluesky: the post being replied to, then the reply, joined by the thread line.
        Column {
            PostCard(parent, nav, lineBelow = true)
            PostCard(item.post, nav, lineAbove = true)
        }
    } else {
        PostCard(item.post, nav, repostedBy = item.repostedBy, replyTo = item.replyTo)
    }
}

// ---------------- Home ----------------

@Composable
fun HomeScreen(nav: Nav, onSearch: () -> Unit, onCompose: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    // Your pinned feeds as tabs, in the order you set in Bluesky, like Bluesky's home.
    val feeds by produceState(listOf(io.github.shoalapps.blobfish.data.Atp.PinnedFeed("feed", Graph.atp.discoverFeed, "Discover")), session?.did) {
        runCatching { Graph.atp.pinnedFeeds() }.onSuccess { if (it.isNotEmpty()) value = it }
    }
    var tab by remember(feeds) { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Wordmark() },
                    actions = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") } },
                )
                if (feeds.size > 1) {
                    androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, feeds.lastIndex), edgePadding = 12.dp) {
                        feeds.forEachIndexed { i, f -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(f.name, maxLines = 1) }) }
                    }
                }
            }
        },
        floatingActionButton = {
            if (session != null) FloatingActionButton(onClick = onCompose) { Icon(Icons.Rounded.Edit, "New post") }
        },
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding())) {
            val f = feeds[tab.coerceIn(0, feeds.lastIndex)]
            val vm = viewModel(key = "feed-${session?.did}-${f.kind}-${f.uri}") {
                PagedVm { c ->
                    when (f.kind) {
                        "timeline" -> Graph.atp.timeline(c)
                        "list" -> Graph.atp.listFeed(f.uri, c)
                        else -> Graph.atp.feed(f.uri, c)
                    }
                }
            }
            PagedList(vm, empty = if (f.kind == "timeline") "Follow some people to fill your timeline" else "Nothing here yet") { FeedRow(it, nav, following = f.kind == "timeline") }
        }
    }
}

/** "Blobfish" with the blobfish on the app's cookie mark. */
@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
        Box(Modifier.size(30.dp).clip(shape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.ui.res.painterResource(io.github.shoalapps.blobfish.R.drawable.ic_launcher_mono), null, Modifier.requiredSize(50.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text("Blobfish", style = MaterialTheme.typography.titleLargeEmphasized, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
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
                    // The conversation above, joined by Bluesky's thread line down to the post.
                    itemsIndexed(t.parents, key = { _, p -> p.uri }) { i, p -> PostCard(p, nav, noteWhenHidden = true, lineAbove = i > 0, lineBelow = true) }
                    item(key = t.focus.post.uri) { AnchorPost(t.focus.post, nav, lineAbove = t.parents.isNotEmpty()) }
                    t.focus.replies.forEach { replies(it, nav, depth = 0, lineAbove = false) }
                }
            }
        }
    }
}

/**
 * Replies the way Bluesky lays them out: a reply's first answer continues straight below it on the
 * same thread line; further answers branch off, indented.
 */
private fun LazyListScope.replies(n: ThreadNode, nav: Nav, depth: Int, lineAbove: Boolean) {
    val continues = n.replies.isNotEmpty() && depth < 6
    item(key = n.post.uri) {
        Box(Modifier.padding(start = (depth * 28).dp)) {
            PostCard(n.post, nav, noteWhenHidden = true, lineAbove = lineAbove, lineBelow = continues)
        }
    }
    if (!continues) return
    n.replies.forEachIndexed { i, c -> replies(c, nav, if (i == 0) depth else depth + 1, lineAbove = i == 0) }
}

// ---------------- Profile ----------------

class ProfileVm(private val actor: String) : ViewModel() {
    var profile by mutableStateOf<Profile?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    val posts = PagedVm { c -> Graph.atp.authorFeed(actor, c) }
    val replies by lazy { PagedVm { c -> Graph.atp.authorFeed(actor, c, "posts_with_replies") } }
    val media by lazy { PagedVm { c -> Graph.atp.authorFeed(actor, c, "posts_with_media") } }
    var tab by mutableStateOf(0)
    val current get() = when (tab) { 1 -> replies; 2 -> media; else -> posts }
    init { load() }
    fun load() { viewModelScope.launch { runCatching { Graph.atp.profile(actor) }.onSuccess { profile = it }.onFailure { error = friendly(it) } } }
}

/** A profile's top, laid out like Bluesky's: banner, avatar, buttons, name and badges, counts, bio, Profile Links. */
@Composable
private fun ProfileHeader(p: Profile, nav: Nav, followed: String?, isMe: Boolean, signedIn: Boolean, onFollow: () -> Unit, onUnfollow: () -> Unit) {
    val context = LocalContext.current
    // Tapping the banner or picture shows it full size.
    var viewing by remember { mutableStateOf<String?>(null) }
    viewing?.let { url -> ImageViewer(listOf(io.github.shoalapps.blobfish.data.Image(url, url, "", null)), 0) { viewing = null } }
    Column {
        Box {
            AsyncImage(
                model = p.banner, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = p.banner != null) { viewing = p.banner },
            )
            Box(Modifier.padding(start = 16.dp).offset(y = 44.dp).align(Alignment.BottomStart)) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(3.dp)
                            .then(if (p.liveUrl != null) Modifier.border(3.dp, Color(0xFFE11D48), CircleShape).padding(3.dp) else Modifier),
                    ) { Avatar(p.author.avatar, 84.dp, Modifier.clickable(enabled = p.author.avatar != null) { viewing = p.author.avatar?.replace("/avatar_thumbnail/", "/avatar/") }) }
                    // Bluesky's LIVE badge, linking to the stream.
                    p.liveUrl?.let { url ->
                        Text(
                            "LIVE", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(y = 6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE11D48)).clickable { openLink(context, url) }.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(40.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (isMe) androidx.compose.material3.OutlinedButton(onClick = nav.editProfile) { Text("Edit profile") }
            if (signedIn && !isMe) {
                if (followed == null) Button(onClick = onFollow) { Text(if (p.followsYou) "Follow back" else "Follow") }
                else androidx.compose.material3.FilledTonalButton(onClick = onUnfollow) { Text("Following") }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.author.name, style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                VerifiedBadge(p.author, 22.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.followsYou) {
                    Text("Follows you", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Text("@${p.author.handle}" + (p.author.pronouns?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(Triple(p.followers, "followers", "followers"), Triple(p.follows, "following", "follows"), Triple(p.posts, "posts", null)).forEach { (n, label, kind) ->
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(compact(n)) }
                            append(" $label")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (kind != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.people(kind, p.author.did) } else Modifier,
                    )
                }
            }
            p.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                RichTextView(it, emptyList(), nav)
            }
            if (p.links.isNotEmpty()) {
                // Profile Links: straight to the site, Patreon and Ko-fi in green like Bluesky.
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.links.forEach { l -> ProfileLinkChip(l) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfileLinkChip(l: io.github.shoalapps.blobfish.data.ProfileLink) {
    val context = LocalContext.current
    val host = remember(l.url) { runCatching { android.net.Uri.parse(l.url).host?.removePrefix("www.") }.getOrNull().orEmpty() }
    val payment = host.endsWith("patreon.com") || host.endsWith("ko-fi.com")
    val bg = if (payment) Color(0xFF15803D) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (payment) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .combinedClickable(onClick = { openLink(context, l.url) }, onLongClick = { toast(context, l.url) })
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.icon != null) AsyncImage(l.icon, null, Modifier.size(20.dp).clip(CircleShape))
        else Icon(Icons.Rounded.Link, null, Modifier.size(20.dp), tint = fg)
        Text(l.title ?: host, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
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
        PagedList(vm.current, Modifier.padding(top = pad.calculateTopPadding()), header = {
            item(key = "header") {
                val p = vm.profile
                if (p == null) {
                    if (vm.error != null) Message(vm.error!!, "Try again") { vm.load() } else CenterLoading()
                    return@item
                }
                ProfileHeader(p, nav, followed, isMe = session?.did == p.author.did, signedIn = session != null,
                    onFollow = {
                        followed = "pending"
                        scope.launch { runCatching { followed = Graph.atp.follow(p.author.did) }.onFailure { followed = null; toast(context, friendly(it)) } }
                    },
                    onUnfollow = {
                        val was = followed!!
                        followed = null
                        scope.launch { runCatching { Graph.atp.undo(was) }.onFailure { followed = was; toast(context, friendly(it)) } }
                    },
                )
            }
            item(key = "tabs") {
                // Bluesky's profile tabs.
                PrimaryTabRow(selectedTabIndex = vm.tab) {
                    listOf("Posts", "Replies", "Media").forEachIndexed { i, label ->
                        Tab(selected = vm.tab == i, onClick = { vm.tab = i }, text = { Text(label) })
                    }
                }
            }
        }) { FeedRow(it, nav) }
    }
}

/** The profile picture in a slowly morphing, turning Material shape, like Swordfish's About page. */
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
            Box(Modifier.size(112.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.ui.res.painterResource(io.github.shoalapps.blobfish.R.drawable.ic_widget_badge), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
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
    var appPassword by remember { mutableStateOf(false) }
    val session by Graph.store.session.collectAsState()
    // Back from Bluesky's page and signed in: done.
    LaunchedEffect(session?.did) { if (session != null) onDone() }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Sign in") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(24.dp).imePadding()
                .wrapContentWidth().widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.ui.res.painterResource(io.github.shoalapps.blobfish.R.drawable.ic_widget_badge), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Sign in with Bluesky", style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(
                "You'll sign in on Bluesky's own page, with your usual password and two-factor. Blobfish never sees your password.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                handle, { handle = it }, Modifier.fillMaxWidth().padding(top = 20.dp),
                label = { Text("Your handle (optional)") }, placeholder = { Text("you.bsky.social") }, singleLine = true,
                supportingText = { Text("Only needed if your account isn't on bsky.social") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { io.github.shoalapps.blobfish.data.BskyOAuth.start(context, Graph.http, handle) }
                            .onSuccess { url ->
                                // Bluesky's page in a browser tab; it sends you back to the app when you're done.
                                runCatching { androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, android.net.Uri.parse(url)) }
                                    .onFailure { openLink(context, url) }
                            }
                            .onFailure { error = friendly(it) }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) { if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(28.dp)) else Text("Continue to Bluesky") }
            TextButton(onClick = { openLink(context, "https://bsky.app/") }) { Text("New to Bluesky? Create an account") }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 16.dp))
            TextButton(onClick = { appPassword = !appPassword }) { Text(if (appPassword) "Hide app password sign-in" else "Use an app password instead") }
            if (appPassword) {
                Text(
                    "For servers without the sign-in page. Create one in Bluesky under Settings → Privacy and security → App passwords.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("App password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                androidx.compose.material3.OutlinedButton(
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
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Sign in with app password") }
            }
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
    // Up to four pictures, each with alt text, like Bluesky.
    val pictures = remember { androidx.compose.runtime.mutableStateListOf<Pair<android.net.Uri, String>>() }
    var altFor by remember { mutableStateOf<Int?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        uris.take(4 - pictures.size).forEach { pictures += it to "" }
    }
    val limit = 300
    val count = text.codePointCount(0, text.length)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel") }
                Text(if (replyTo != null) "Reply to ${replyTo.author.name}" else "New post", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${limit - count}", color = if (count > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !busy && (text.isNotBlank() || pictures.isNotEmpty()) && count <= limit,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                val images = pictures.map { (uri, alt) ->
                                    val (blob, w, h) = uploadImage(context, uri, 2000)
                                    io.github.shoalapps.blobfish.data.Atp.Attachment(blob, alt, w, h)
                                }
                                Graph.atp.post(text.trim(), replyTo, images)
                            }
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
            if (pictures.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pictures.forEachIndexed { i, (uri, alt) ->
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(uri, alt, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            IconButton(onClick = { pictures.removeAt(i) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(2.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f))) {
                                Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (alt.isBlank()) "+ALT" else "ALT ✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).clickable { altFor = i }.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = pictures.size < 4, onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.Image, "Add pictures", tint = MaterialTheme.colorScheme.primary)
                }
                if (pictures.isNotEmpty()) Text("${pictures.size}/4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            altFor?.let { i ->
                var draft by remember(i) { mutableStateOf(pictures.getOrNull(i)?.second.orEmpty()) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { altFor = null },
                    title = { Text("Alt text") },
                    text = { OutlinedTextField(draft, { if (it.length <= 2000) draft = it }, placeholder = { Text("Describe the picture for people who can't see it") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { pictures.getOrNull(i)?.let { pictures[i] = it.first to draft.trim() }; altFor = null }) { Text("Save") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { altFor = null }) { Text("Cancel") } },
                )
            }
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
                    row { sh -> Link("Signed in as @${sess.handle}", "Sign out of this account", sh) { store.setSession(null); io.github.shoalapps.blobfish.data.BskyOAuth.forgetKey() } }
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
            group("X") {
                row { sh -> Toggle("X tab", "x.com in its own tab, signed in on X's own page", s.showX, sh) { v -> store.updateSettings { it.copy(showX = v) } } }
                if (s.showX && io.github.shoalapps.blobfish.ui.XView.signedIn) {
                    row { sh -> Link("Sign out of X", null, sh) { io.github.shoalapps.blobfish.ui.XView.signOut() } }
                }
            }
            group("Moderation") {
                row { sh -> Link("Moderation", "Content filters, muted words, hidden posts, muted accounts, replies and reposts", sh, onModeration) }
            }
            group("Updates") {
                if (io.github.shoalapps.blobfish.ShoalManager.installed(context)) {
                    row { sh -> Link("Managed by Shoal", "Updates are checked and installed by Shoal", sh) { io.github.shoalapps.blobfish.ShoalManager.checkForUpdates(context) } }
                } else {
                    row { sh -> Toggle("Check for updates", "Look for a new version on GitHub once a day", s.autoUpdate, sh) { v -> store.updateSettings { it.copy(autoUpdate = v) } } }
                }
                row { sh -> Link("Check for updates now", "Version $version · opens Shoal", sh) { io.github.shoalapps.blobfish.ShoalManager.checkForUpdates(context) } }
            }
            group("About") {
                if (io.github.shoalapps.blobfish.Telemetry.available) row { sh -> Toggle("Send error reports", "Anonymous crash and error reports help fix bugs. Never your accounts or history.", io.github.shoalapps.blobfish.Telemetry.enabled, sh) { v -> io.github.shoalapps.blobfish.Telemetry.setEnabled(context, v) } }
                row { sh -> Link("Privacy policy", "What Blobfish sends, and to whom", sh) { openLink(context, "https://shoalapps.github.io/privacy.html") } }
                row { sh -> Link("Legal", "Licences, trademarks and notices", sh) { openLink(context, "https://shoalapps.github.io/legal.html") } }
                row { sh -> Link("Support Shoal", "Shoal is free and has no ads; donations on Liberapay keep it going", sh) { openLink(context, "https://liberapay.com/ShoalApps") } }
                row { sh -> Link("Source code", "github.com/ShoalApps/blobfish", sh) { openLink(context, "https://github.com/ShoalApps/blobfish") } }
                row { sh -> Link("Shoal", "shoalapps.github.io: all the apps and their credits", sh) { openLink(context, "https://shoalapps.github.io") } }
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
