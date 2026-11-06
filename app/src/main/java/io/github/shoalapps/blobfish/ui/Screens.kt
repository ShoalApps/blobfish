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
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
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
import io.github.shoalapps.blobfish.LocalisedText as Text
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import io.github.shoalapps.blobfish.data.TwitterParse
import io.github.shoalapps.blobfish.data.Moderation
import io.github.shoalapps.blobfish.data.AtpException
import io.github.shoalapps.blobfish.data.Author
import io.github.shoalapps.blobfish.data.FeedItem
import io.github.shoalapps.blobfish.data.Notification
import io.github.shoalapps.blobfish.data.Page
import io.github.shoalapps.blobfish.data.Post
import io.github.shoalapps.blobfish.data.Draft
import io.github.shoalapps.blobfish.data.LEGACY_DRAFT_ID
import io.github.shoalapps.blobfish.data.Profile
import io.github.shoalapps.blobfish.data.Thread
import io.github.shoalapps.blobfish.data.ThreadNode
import io.github.shoalapps.blobfish.data.compact
import io.github.shoalapps.blobfish.data.shortAgo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import io.github.shoalapps.blobfish.AppLanguage
import io.github.shoalapps.common.ui.SupportCard

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
    var hasMore by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var pageError by mutableStateOf<String?>(null); private set
    private var cursor: String? = null
    private var request: kotlinx.coroutines.Job? = null

    init { refresh() }

    fun refresh() {
        if (refreshing) return
        request?.cancel()
        refreshing = !loading
        loadingMore = false
        error = null
        pageError = null
        request = viewModelScope.launch {
            try {
                val page = load(null)
                ensureActive()
                items = page.items
                cursor = page.cursor
                hasMore = cursor != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                error = friendly(e)
            }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        val next = cursor ?: return
        if (loadingMore || loading || refreshing) return
        loadingMore = true
        pageError = null
        request = viewModelScope.launch {
            try {
                val page = load(next)
                ensureActive()
                items = items + page.items
                cursor = page.cursor?.takeUnless { it == next }
                hasMore = cursor != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                pageError = friendly(e)
            }
            loadingMore = false
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
    val online by Graph.offline.online.collectAsState()
    var wasOffline by remember(vm) { mutableStateOf(false) }
    LaunchedEffect(vm, online) {
        if (online && wasOffline) vm.refresh()
        wasOffline = !online
    }
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(vm, nearEnd, vm.hasMore, vm.items.size, online) {
        if (online && nearEnd && vm.hasMore && vm.pageError == null) vm.loadMore()
    }
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = modifier.fillMaxSize()) {
        // A readable column on wide screens, like Bluesky's web app.
        LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 680.dp), state = state, contentPadding = PaddingValues(bottom = 96.dp)) {
            item { OfflineStatus() }
            header()
            when {
                vm.loading -> item { CenterLoading() }
                vm.error != null && vm.items.isEmpty() -> item { Message(vm.error!!, "Try again") { vm.refresh() } }
                vm.items.isEmpty() -> item { Message(empty) }
                else -> {
                    vm.error?.let { error -> item { Message(error, "Try again") { vm.refresh() } } }
                    items(vm.items) { row(it) }
                    when {
                        vm.loadingMore -> item { CenterLoading() }
                        vm.pageError != null -> item { Message(vm.pageError!!, "Try again") { vm.loadMore() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, nav: Nav, following: Boolean = false) {
    val prefs = Graph.store.moderation.collectAsState().value
    // Reply, repost and quote options are for the Following timeline only, as in Bluesky's app.
    if (following && Moderation.hiddenInFeed(item, prefs)) return
    if (Moderation.decide(item.post, prefs, Graph.store.session.value != null) is Moderation.Decision.Hide) return
    val parent = item.parent?.takeUnless {
        !TwitterParse.isTwitter(item.post.author.did) && Graph.store.settings.value.nsfwOnly && !Moderation.isNsfw(it)
    }
    if (parent != null && Moderation.decide(parent, prefs, Graph.store.session.value != null) !is Moderation.Decision.Hide) {
        // Like Bluesky: the post being replied to, then the reply, joined by the thread line.
        Column {
            PostCard(parent, nav, lineBelow = true)
            PostCard(item.post, nav, lineAbove = true, pinned = item.pinned && !item.post.uri.startsWith("x:"))
        }
    } else {
        PostCard(item.post, nav, repostedBy = item.repostedBy, replyTo = item.replyTo, pinned = item.pinned)
    }
}

// ---------------- Home ----------------

@Composable
fun HomeScreen(nav: Nav, onSearch: () -> Unit, onCompose: () -> Unit, onDrafts: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    val feedGeneration by Graph.store.feedGeneration.collectAsState()
    val settings by Graph.store.settings.collectAsState()
    val draftChanges by Graph.drafts.changes.collectAsState()
    val owner = session?.let { it.did + "|" + it.pds }
    val hasDrafts = remember(owner, draftChanges) { owner?.let { Graph.drafts.list(it).isNotEmpty() } == true }
    // Your pinned feeds as tabs, in the order you set in Bluesky, like Bluesky's home.
    val feeds by produceState(listOf(io.github.shoalapps.blobfish.data.Atp.PinnedFeed("feed", Graph.atp.discoverFeed, "Discover")), owner, settings.appView) {
        value = listOf(io.github.shoalapps.blobfish.data.Atp.PinnedFeed("feed", Graph.atp.discoverFeed, "Discover"))
        runCatching { Graph.atp.pinnedFeeds() }.onSuccess { if (it.isNotEmpty()) value = it }
    }
    val hiddenFollows by Graph.store.hiddenFollows.collectAsState()
    val homeFeeds = remember(feeds, hiddenFollows.isNotEmpty()) {
        if (hiddenFollows.isEmpty()) feeds
        else feeds + io.github.shoalapps.blobfish.data.Atp.PinnedFeed("hidden", "", "Hidden follows")
    }
    var tab by remember(homeFeeds, owner) { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Wordmark() },
                    actions = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") } },
                )
                if (homeFeeds.size > 1) {
                    androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, homeFeeds.lastIndex), edgePadding = 12.dp) {
                        homeFeeds.forEachIndexed { i, f -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(f.name, localise = f.kind == "hidden", maxLines = 1) }) }
                    }
                }
            }
        },
        floatingActionButton = {
            if (session != null) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasDrafts) androidx.compose.material3.FilledTonalButton(onClick = onDrafts) { Text("Drafts") }
                FloatingActionButton(onClick = onCompose) { Icon(Icons.Rounded.Edit, "New post") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(top = pad.calculateTopPadding())) {
            val f = homeFeeds[tab.coerceIn(0, homeFeeds.lastIndex)]
            if (f.kind == "hidden") {
                HiddenFollowsFeed(hiddenFollows, owner, settings.appView, nav)
                return@Box
            }
            val feedOwner = "bluesky|" + (owner ?: "public")
            val appView = settings.appView
            val generation = feedGeneration
            val feedId = "$appView|${f.kind}|${f.uri}"
            val vm = viewModel(key = "feed-$feedGeneration-$feedOwner-$feedId") {
                ReadingFeedVm(
                    io.github.shoalapps.blobfish.data.FeedCache(feedOwner, feedId, (owner ?: "public") + "|" + appView),
                    active = { Graph.store.feedGeneration.value == generation &&
                        Graph.store.settings.value.appView == appView &&
                        Graph.offline.owner() == owner },
                ) { c ->
                    when (f.kind) {
                        "timeline" -> Graph.atp.timeline(c)
                        "list" -> Graph.atp.listFeed(f.uri, c)
                        else -> Graph.atp.feed(f.uri, c)
                    }
                }
            }
            ReadingFeedList(vm, bluesky = true, empty = if (f.kind == "timeline") "Follow some people to fill your timeline" else "Nothing here yet") { FeedRow(it, nav, following = f.kind == "timeline") }
        }
    }
}

@Composable
private fun HiddenFollowsFeed(people: Map<String, String>, owner: String?, appView: String, nav: Nav) {
    var selected by remember(owner) { mutableStateOf<String?>(null) }
    val actor = selected?.takeIf { it in people } ?: people.keys.firstOrNull() ?: return
    Column {
        androidx.compose.material3.PrimaryScrollableTabRow(
            selectedTabIndex = people.keys.indexOf(actor),
            edgePadding = 12.dp,
        ) {
            people.forEach { (did, handle) ->
                Tab(selected = actor == did, onClick = { selected = did }, text = { Text("@$handle", localise = false) })
            }
        }
        val vm = viewModel(key = "hidden-follows-$owner-$appView-$actor") {
            ReadingFeedVm(cache = null, active = { Graph.offline.owner() == owner && Graph.store.settings.value.appView == appView }) { cursor ->
                Graph.atp.hiddenFollowFeed(actor, cursor)
            }
        }
        androidx.compose.runtime.key(actor, owner, appView) {
            ReadingFeedList(vm, bluesky = true, empty = "No public posts found", headerCount = 1, header = {
                item {
                    TextButton(onClick = { Graph.store.toggleHiddenFollow(actor, people.getValue(actor)) }) {
                        Text("Remove hidden follow")
                    }
                }
            }) { FeedRow(it, nav) }
        }
    }
}

/** "Butterflyfish" with the blobfish on the app's cookie mark. */
@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
        Box(Modifier.size(30.dp).clip(shape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.ui.res.painterResource(io.github.shoalapps.blobfish.R.drawable.ic_launcher_mono), null, Modifier.requiredSize(50.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text("Butterflyfish", style = MaterialTheme.typography.titleLargeEmphasized, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---------------- Search ----------------

class SearchVm : ViewModel() {
    var query by mutableStateOf("")
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var people by mutableStateOf<List<Author>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var peopleLoading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var peopleError by mutableStateOf<String?>(null); private set
    var searched by mutableStateOf(false); private set

    var latest by mutableStateOf(false); private set
    var postCursor by mutableStateOf<String?>(null); private set
    var loadingMore by mutableStateOf(false); private set
    var pageError by mutableStateOf<String?>(null); private set
    var peopleCursor by mutableStateOf<String?>(null); private set
    var peopleLoadingMore by mutableStateOf(false); private set
    private var submittedQuery = ""
    private var peopleJob: kotlinx.coroutines.Job? = null
    private var postsJob: kotlinx.coroutines.Job? = null

    fun search(q: String = query) {
        val submitted = q.trim()
        if (submitted.isEmpty()) return
        query = q
        submittedQuery = submitted
        searched = true
        refreshPosts()
        refreshPeople()
    }

    fun refreshPeople() {
        peopleJob?.cancel()
        people = emptyList()
        peopleCursor = null
        peopleLoading = true
        peopleLoadingMore = false
        requestPeople(null)
    }

    fun loadMorePeople() {
        val cursor = peopleCursor ?: return
        if (peopleLoading || peopleLoadingMore) return
        peopleLoadingMore = true
        requestPeople(cursor)
    }

    private fun requestPeople(cursor: String?) {
        val submitted = submittedQuery
        peopleError = null
        peopleJob = viewModelScope.launch {
            try {
                val page = Graph.atp.searchPeople(submitted, cursor)
                ensureActive()
                people = (people + page.items).distinctBy { it.did }
                peopleCursor = page.cursor?.takeUnless { it == cursor }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                peopleError = friendly(e)
            }
            peopleLoading = false
            peopleLoadingMore = false
        }
    }

    fun sortPosts(showLatest: Boolean) {
        if (latest == showLatest) return
        latest = showLatest
        if (searched) refreshPosts()
    }

    fun refreshPosts() {
        postsJob?.cancel()
        posts = emptyList()
        postCursor = null
        error = null
        loadingMore = false
        loading = true
        requestPosts(null)
    }

    fun loadMorePosts() {
        val cursor = postCursor ?: return
        if (loading || loadingMore) return
        loadingMore = true
        requestPosts(cursor)
    }

    private fun requestPosts(cursor: String?) {
        val submitted = submittedQuery
        val showLatest = latest
        pageError = null
        postsJob = viewModelScope.launch {
            try {
                val page = Graph.atp.searchPosts(submitted, cursor, showLatest)
                ensureActive()
                posts = (posts + page.items).distinctBy { it.uri }
                postCursor = page.cursor?.takeUnless { it == cursor }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                if (cursor != null) pageError = friendly(e)
                else error = if (e is AtpException && e.status == 401 && Graph.store.session.value == null) {
                    "Sign in to search posts. People search works signed out."
                } else friendly(e)
            }
            loading = false
            loadingMore = false
        }
    }
}

@Composable
fun SearchScreen(nav: Nav, onBack: () -> Unit, initial: String? = null, vm: SearchVm = viewModel()) {
    LaunchedEffect(initial) { if (initial != null && vm.query != initial) vm.search(initial) }
    val settings by Graph.store.settings.collectAsState()
    val prefs by Graph.store.moderation.collectAsState()
    val signedIn = Graph.store.session.collectAsState().value != null
    val posts = remember(vm.posts, settings.nsfwOnly, prefs, signedIn) {
        if (!settings.nsfwOnly) vm.posts else vm.posts.filter {
            Moderation.isNsfw(it) && Moderation.decide(it, prefs, signedIn) !is Moderation.Decision.Hide
        }
    }
    val postListState = rememberLazyListState()
    val peopleListState = rememberLazyListState()
    val online by Graph.offline.online.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val listState = if (tab == 0) postListState else peopleListState
    val nearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= layout.totalItemsCount - 5
        }
    }
    val scanningFilteredPages = settings.nsfwOnly && posts.isEmpty()
    LaunchedEffect(tab, nearEnd, scanningFilteredPages, settings.nsfwOnly, vm.postCursor, vm.loading, vm.loadingMore, vm.pageError, online) {
        if (tab == 0 && online && (nearEnd || scanningFilteredPages) && vm.postCursor != null &&
            !vm.loading && !vm.loadingMore && vm.pageError == null
        ) {
            if (settings.nsfwOnly) kotlinx.coroutines.delay(750)
            vm.loadMorePosts()
        }
    }
    LaunchedEffect(tab, nearEnd, online, vm.peopleCursor, vm.peopleLoadingMore, vm.peopleError) {
        if (tab == 1 && nearEnd && online && vm.peopleError == null) vm.loadMorePeople()
    }
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
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
            if (tab == 0 && vm.searched) item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = !vm.latest, onClick = { vm.sortPosts(false) }, label = { Text("Top") })
                    androidx.compose.material3.FilterChip(selected = vm.latest, onClick = { vm.sortPosts(true) }, label = { Text("Latest") })
                }
            }
            when {
                (if (tab == 0) vm.loading else vm.peopleLoading) -> item { CenterLoading() }
                tab == 0 && vm.error != null -> item { Message(vm.error!!, "Try again", vm::refreshPosts) }
                tab == 1 && vm.peopleError != null && vm.peopleCursor == null -> item { Message(vm.peopleError!!, "Try again", vm::refreshPeople) }
                vm.searched && tab == 0 && posts.isEmpty() -> item { Message(when {
                    scanningFilteredPages && online && vm.postCursor != null && vm.pageError == null -> "Looking for NSFW posts…"
                    settings.nsfwOnly -> "No matching NSFW posts"
                    else -> "No posts found. Try another search."
                }) }
                vm.searched && tab == 1 && vm.people.isEmpty() -> item { Message("No people found. Try another search.") }
                tab == 0 -> items(posts, key = { it.uri }) { PostCard(it, nav) }
                else -> items(vm.people, key = { it.did }) { a -> PersonRow(a) { nav.profile(a.did) } }
            }
            if (tab == 1 && vm.peopleCursor != null) item {
                when {
                    vm.peopleLoadingMore -> CenterLoading()
                    vm.peopleError != null -> Message(vm.peopleError!!, "Try again", vm::loadMorePeople)
                    else -> TextButton(onClick = vm::loadMorePeople) { Text("Load more") }
                }
            }
            if (tab == 0 && vm.postCursor != null) item {
                when {
                    vm.loadingMore -> CenterLoading()
                    vm.pageError != null -> Message(vm.pageError!!, "Try again", vm::loadMorePosts)
                    else -> TextButton(onClick = vm::loadMorePosts) { Text("Load more") }
                }
            }
        }
    }
}

@Composable
fun PersonRow(a: Author, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(a.avatar, 44.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.name, localise = false, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
        viewModelScope.launch {
            // Twitter posts come from its website (XBridge); Bluesky's from Bluesky.
            runCatching { if (TwitterParse.isTwitter(uri)) XBridge.thread(uri.removePrefix(TwitterParse.PREFIX)) else Graph.atp.thread(uri) }
                .onSuccess { thread = it }.onFailure { error = friendly(it) }
        }
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
                    itemsIndexed(t.parents, key = { _, p -> p.uri }) { i, p -> PostCard(p, nav, lineAbove = i > 0, lineBelow = true) }
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
            PostCard(n.post, nav, lineAbove = lineAbove, lineBelow = continues)
        }
    }
    if (!continues) return
    n.replies.forEachIndexed { i, c -> replies(c, nav, if (i == 0) depth else depth + 1, lineAbove = i == 0) }
}

// ---------------- Profile ----------------

class ProfileVm(private val actor: String, private val owner: String, private val namespace: String,
    private val active: () -> Boolean) : ViewModel() {
    private fun cache(kind: String) = if (TwitterParse.isTwitter(actor)) null else io.github.shoalapps.blobfish.data.FeedCache(owner, "profile|$actor|$kind", namespace)
    var profile by mutableStateOf<Profile?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    /** A Twitter profile ("x:<id>" or "x:@handle"): read from its website, by handle. */
    private val twitter = TwitterParse.isTwitter(actor)
    private val xHandle get() = TwitterParse.handleOf(actor) ?: actor.removePrefix(TwitterParse.PREFIX).removePrefix("@")
    /** Twitter's posts load with its profile page, so they wait for it. */
    private val profileLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()
    val posts = ReadingFeedVm(cache("posts"), active) { c -> if (twitter) { profileLoaded.await(); XBridge.posts(xHandle, c) } else Graph.atp.authorFeed(actor, c) }
    val replies by lazy { ReadingFeedVm(cache("replies"), active) { c -> if (twitter) XBridge.posts(xHandle, c, "replies") else Graph.atp.authorFeed(actor, c, "posts_with_replies") } }
    val media by lazy { ReadingFeedVm(cache("media"), active) { c -> if (twitter) XBridge.posts(xHandle, c, "media") else Graph.atp.authorFeed(actor, c, "posts_with_media") } }
    var tab by mutableStateOf(0)
    val current get() = when (tab) { 1 -> replies; 2 -> media; else -> posts }
    init { load() }
    fun load() {
        viewModelScope.launch {
            runCatching { if (twitter) XBridge.profile(xHandle) else Graph.atp.profile(actor) }
                .onSuccess { if (active()) profile = it }.onFailure { if (active()) error = friendly(it) }
            profileLoaded.complete(Unit)
        }
    }
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
            // Profile editing is Bluesky's for now (Twitter profiles are edited on Twitter).
            if (isMe && !TwitterParse.isTwitter(p.author.did)) androidx.compose.material3.OutlinedButton(onClick = nav.editProfile) { Text("Edit profile") }
            if (signedIn && !isMe) {
                if (followed == null) Button(onClick = onFollow) { Text(if (p.followsYou) "Follow back" else "Follow") }
                else androidx.compose.material3.FilledTonalButton(onClick = onUnfollow) { Text("Following") }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (!isMe && !TwitterParse.isTwitter(p.author.did)) {
                val hiddenFollows by Graph.store.hiddenFollows.collectAsState()
                val saved = p.author.did in hiddenFollows
                TextButton(onClick = {
                    Graph.store.toggleHiddenFollow(p.author.did, p.author.handle)
                }) { Text(if (saved) "Remove hidden follow" else "Follow privately") }
                if (saved) Text(
                    "Saved on this device. No Bluesky follow or notification.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.author.name, localise = false, style = MaterialTheme.typography.headlineSmallEmphasized, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
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
                RichTextView(it, emptyList(), nav, code = false)
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
fun ProfileScreen(actor: String, nav: Nav, onBack: (() -> Unit)?, onSettings: (() -> Unit)? = null, onAccounts: (() -> Unit)? = null) {
    val generationState by Graph.store.feedGeneration.collectAsState()
    val settingsState by Graph.store.settings.collectAsState()
    val generation = generationState
    val appView = settingsState.appView
    val twitter = TwitterParse.isTwitter(actor)
    val bskyOwner = Graph.offline.owner()
    val owner = "bluesky|" + (bskyOwner ?: "public")
    val namespace = (bskyOwner ?: "public") + "|" + appView
    val vm = viewModel(key = "profile-$owner-$generation-$appView-$actor") {
        ProfileVm(actor, owner, namespace, active = {
            twitter || Graph.store.feedGeneration.value == generation && Graph.store.settings.value.appView == appView && Graph.offline.owner() == bskyOwner
        })
    }
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
                actions = {
                    // Your own profile: switch accounts (Bluesky or X), as in Bluesky's app.
                    if (onAccounts != null && Graph.store.settings.collectAsState().value.showX) IconButton(onClick = onAccounts) {
                        Icon(Icons.Rounded.SwitchAccount, "Switch account")
                    }
                    if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        ReadingFeedList(vm.current, Modifier.padding(top = pad.calculateTopPadding()), bluesky = !twitter, headerCount = 2, header = {
            item(key = "header") {
                val p = vm.profile
                if (p == null) {
                    if (vm.error != null) Message(vm.error!!, "Try again") { vm.load() } else CenterLoading()
                    return@item
                }
                val twitter = TwitterParse.isTwitter(p.author.did)
                ProfileHeader(p, nav, followed,
                    isMe = if (twitter) XBridge.me.equals(p.author.handle, ignoreCase = true) else session?.did == p.author.did,
                    signedIn = if (twitter) XView.signedIn else session != null,
                    onFollow = {
                        followed = "pending"
                        scope.launch {
                            runCatching { followed = if (twitter) { if (!XBridge.follow(p.author.handle, true)) error("Couldn't reach ${XBrand.name}"); "x-follow" } else Graph.atp.follow(p.author.did) }
                                .onFailure { followed = null; toast(context, friendly(it)) }
                        }
                    },
                    onUnfollow = {
                        val was = followed!!
                        followed = null
                        scope.launch {
                            runCatching { if (twitter) { if (!XBridge.follow(p.author.handle, false)) error("Couldn't reach ${XBrand.name}") } else Graph.atp.undo(was) }
                                .onFailure { followed = was; toast(context, friendly(it)) }
                        }
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
fun YouScreen(nav: Nav, onSignIn: () -> Unit, onSettings: () -> Unit, onAccounts: () -> Unit = {}) {
    val session by Graph.store.session.collectAsState()
    val s = session
    if (s != null) {
        ProfileScreen(s.did, nav, onBack = null, onSettings = onSettings, onAccounts = onAccounts)
        return
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = {}, actions = {
            if (Graph.store.settings.collectAsState().value.showX) IconButton(onClick = onAccounts) { Icon(Icons.Rounded.SwitchAccount, "Switch account") }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }) },
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
                "You'll sign in on Bluesky's own page, with your usual password and two-factor. Butterflyfish never sees your password.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
            // Hosting provider, as in Bluesky's app: Bluesky Social, or your own server.
            val server = Graph.store.settings.collectAsState().value.server
            var choosingServer by remember { mutableStateOf(false) }
            if (choosingServer) ServerDialog(appView = false) { choosingServer = false }
            Surface(
                onClick = { choosingServer = true }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hosting provider", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (server == io.github.shoalapps.blobfish.data.Settings.DEFAULT_SERVER) "Bluesky Social" else shortHost(server), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedTextField(
                handle, { handle = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Your handle (optional)") }, placeholder = { Text("you.bsky.social") }, singleLine = true,
                supportingText = { Text("With a handle, you sign in on that account's own server") },
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

/** A terminal flag keeps a pending debounce or disposal from restoring a discarded/sent draft. */
private class ComposerDraft(private val owner: String, replyTo: Post?, selectedId: String?) {
    private val restored = runCatching {
        if (replyTo != null || selectedId != null)
            Graph.drafts.load(owner, replyTo?.uri, selectedId ?: LEGACY_DRAFT_ID)
        else null
    }
    val error = restored.exceptionOrNull()
    var value by mutableStateOf((restored.getOrNull() ?: if (replyTo == null) Graph.drafts.newStandalone() else Draft(replyTo = replyTo.uri)).let { draft ->
        draft.copy(replyTarget = replyTo?.let {
            (draft.replyTarget ?: io.github.shoalapps.blobfish.data.DraftReply(it.cid, it.replyRoot?.uri, it.replyRoot?.cid))
                .copy(parent = io.github.shoalapps.blobfish.data.DraftParent.from(it))
        } ?: draft.replyTarget)
    })
    private var finished = false
    fun save() { if (!finished && error == null) Graph.drafts.save(owner, value) }
    fun discard() {
        Graph.drafts.discard(owner, value.replyTo, value.id)
        finished = true
    }
    fun sent() {
        // The outbox now owns the post, even if deleting the local draft fails.
        finished = true
        Graph.drafts.discard(owner, value.replyTo, value.id)
    }
}

/** New post or reply, with a live preview of the formatting. */
@Composable
fun Composer(replyTo: Post?, nav: Nav, draftId: String? = null, refreshReply: Boolean = false, onDismiss: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    val owner = session?.let { it.did + "|" + it.pds }
    val openedBy = remember { owner }
    if (owner != openedBy) {
        LaunchedEffect(owner) { onDismiss() }
        return
    }
    androidx.compose.runtime.key(owner, replyTo?.uri, draftId) {
        DraftComposer(owner, replyTo, nav, draftId, refreshReply, onDismiss)
    }
}

@Composable
private fun DraftComposer(owner: String?, replyTo: Post?, nav: Nav, draftId: String?, refreshReply: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { ComposerDraft(owner ?: "bluesky|signed-out", replyTo, draftId) }
    var parent by remember { mutableStateOf(replyTo) }
    var parentUnavailable by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
    val online by Graph.offline.online.collectAsState()
    LaunchedEffect(refreshReply, online) {
        if (refreshReply && replyTo != null && Graph.offline.available()) {
            try {
                val fresh = Graph.atp.thread(replyTo.uri).focus.post
                if (Graph.offline.owner() == owner) {
                    parent = fresh
                    parentUnavailable = false
                    draft.value = draft.value.copy(replyTarget = draft.value.replyTarget?.copy(
                        parent = io.github.shoalapps.blobfish.data.DraftParent.from(fresh)))
                }
            } catch (e: CancellationException) { throw e
            } catch (e: AtpException) {
                if (e.status == 404) parentUnavailable = true
            } catch (_: Exception) { /* Keep the saved parent when refresh fails. */ }
        }
    }
    val text = draft.value.text
    val pictures = draft.value.pictures
    // Keep the saved strong references even when reopened from a freshly fetched post.
    val target = parent?.let { post ->
        draft.value.replyTarget?.let { saved ->
            post.copy(cid = saved.cid, replyRoot = saved.rootUri?.let {
                io.github.shoalapps.blobfish.data.Ref(it, saved.rootCid ?: error("Missing thread root"))
            })
        } ?: post
    }
    var busy by remember { mutableStateOf(false) }
    var altFor by remember { mutableStateOf<Int?>(null) }
    fun save(): Boolean = runCatching { draft.save() }.onFailure {
        toast(context, "Couldn't save your draft. Please try again.")
    }.isSuccess
    val dismiss = { if (!busy && save()) onDismiss() }
    LaunchedEffect(draft.value) {
        kotlinx.coroutines.delay(400)
        save()
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, draft) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) save()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); save() }
    }
    LaunchedEffect(draft) {
        if (draft.error != null) toast(context, "Couldn't open your draft. Close this and try again.")
    }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        busy = true
        scope.launch {
            // Finish copying and saving even if Android recreates the activity during the picker result.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching {
                    for (uri in uris.take(4 - draft.value.pictures.size)) {
                        val picture = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            Graph.drafts.copyPicture(owner ?: "bluesky|signed-out", draft.value.replyTo, draft.value.id) {
                                context.contentResolver.openInputStream(uri) ?: error("Couldn't open this picture")
                            }
                        }
                        draft.value = draft.value.copy(pictures = draft.value.pictures + picture)
                        draft.save()
                    }
                }.onFailure { toast(context, "Couldn't save this picture. Please try again.") }
                busy = false
            }
        }
    }
    val limit = io.github.shoalapps.blobfish.data.BlueskyText.post.graphemes
    val count = io.github.shoalapps.blobfish.data.graphemeCount(text)
    val validText = io.github.shoalapps.blobfish.data.BlueskyText.post.accepts(text)
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !busy || it != androidx.compose.material3.SheetValue.Hidden },
    )) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
            if (!io.github.shoalapps.blobfish.data.BlueskyText.post.fitsBytes(text)) {
                Text("This text takes up too much space. Shorten it to continue.", color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = dismiss) { Icon(Icons.Rounded.Close, "Close") }
                Text(if (replyTo != null) "Reply to ${parent?.author?.name ?: replyTo.author.name}" else "New post", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${limit - count}", color = if (!validText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !busy && draft.error == null && owner != null && (text.isNotBlank() || pictures.isNotEmpty()) && validText,
                    onClick = {
                        busy = true
                        postError = null
                        scope.launch {
                            runCatching {
                                draft.save()
                                check(Graph.offline.owner() == owner) { "Your account changed. Reopen this draft to post it." }
                                if (target != null) {
                                    check(Graph.offline.available() || !parentUnavailable) {
                                        "The post you're replying to was deleted or is no longer available. Your draft is still saved."
                                    }
                                    if (Graph.offline.available()) {
                                        try { Graph.atp.thread(target.uri) }
                                        catch (e: AtpException) {
                                            if (e.status == 404) error("The post you're replying to was deleted or is no longer available. Your draft is still saved.")
                                            throw e
                                        }
                                    }
                                }
                                val images = pictures.map { (uri, alt) ->
                                    val (blob, w, h) = uploadImage(context, android.net.Uri.parse(uri), 2000)
                                    io.github.shoalapps.blobfish.data.Atp.Attachment(blob, alt, w, h)
                                }
                                check(Graph.offline.owner() == owner) { "Your account changed. Reopen this draft to post it." }
                                Graph.atp.post(text.trim(), target, images)
                            }
                                .onSuccess {
                                    runCatching { draft.sent() }.onFailure { toast(context, "Your post is saved to send, but the draft couldn't be removed.") }
                                    toast(context, "Saved to send. Check saved actions for sync status.")
                                    onDismiss()
                                }
                                .onFailure { postError = friendly(it) }
                            busy = false
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text("Post") }
            }
            OutlinedTextField(
                text, { draft.value = draft.value.copy(text = it) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !busy && draft.error == null,
                placeholder = { Text("What's up? Use ``` for code, **bold**, *italic*") },
                minLines = 4,
            )
            postError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            if (pictures.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pictures.forEachIndexed { i, (uri, alt) ->
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(android.net.Uri.parse(uri), alt, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            IconButton(enabled = !busy, onClick = { draft.value = draft.value.copy(pictures = pictures.filterIndexed { index, _ -> index != i }); save() }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(2.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f))) {
                                Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (alt.isBlank()) "+ALT" else "ALT ✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).clickable(enabled = !busy) { altFor = i }.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy && draft.error == null && pictures.size < 4, onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.Image, "Add pictures", tint = MaterialTheme.colorScheme.primary)
                }
                if (pictures.isNotEmpty()) Text("${pictures.size}/4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = {
                    runCatching { draft.discard() }
                        .onSuccess { onDismiss() }
                        .onFailure { toast(context, "Couldn't discard your draft. Please try again.") }
                }) { Text("Discard draft") }
            }
            altFor?.let { i ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { save(); altFor = null },
                    title = { Text("Alt text") },
                    text = { OutlinedTextField(
                        pictures.getOrNull(i)?.alt.orEmpty(),
                        { description ->
                            if (description.length <= 2000) draft.value = draft.value.copy(pictures = pictures.mapIndexed { index, picture ->
                                if (index == i) picture.copy(alt = description) else picture
                            })
                        },
                        placeholder = { Text("Describe the picture for people who can't see it") },
                        minLines = 3, modifier = Modifier.fillMaxWidth(),
                    ) },
                    confirmButton = { TextButton(onClick = { if (save()) altFor = null }) { Text("Done") } },
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
    var compatibility by remember { mutableStateOf(false) }
    if (compatibility) {
        androidx.activity.compose.BackHandler { compatibility = false }
        CompatibilityScreen { compatibility = false }
        return
    }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    // Which server dialog is open: false = hosting provider, true = AppView.
    var servers by remember { mutableStateOf<Boolean?>(null) }
    servers?.let { ServerDialog(appView = it) { servers = null } }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "support") {
                SupportCard(
                    title = AppLanguage.text("Support Shoal"),
                    description = AppLanguage.text("Free, open source and ad-free. Donations on Liberapay keep it going."),
                    donateLabel = AppLanguage.text("Donate"),
                    onDonateLabel = "Donate on Liberapay",
                    heartIcon = Icons.Rounded.Favorite,
                ) { openLink(context, "https://liberapay.com/ShoalApps") }
            }
            item(key = "compatibility") {
                androidx.compose.material3.FilledTonalButton(onClick = { compatibility = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column { Text("Staying up to date"); Text("Bluesky schemas, X changes and features still to add", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (s.service == "x") item(key = "x-identity") { XAccountIdentity() }
            group("Account") {
                if (s.service == "x") {
                    row { sh -> Link(XIdentity.author?.let { "Signed in as @${it.handle}" } ?: "${XBrand.name} account", "Sign out of this account", sh) {
                        XView.signOut()
                        store.updateSettings { it.copy(service = "bluesky") }
                    } }
                } else {
                    val sess = session
                    if (sess != null) {
                        row { sh -> Link("Signed in as @${sess.handle}", "Sign out of this account", sh) { store.setSession(null); io.github.shoalapps.blobfish.data.BskyOAuth.forgetKey() } }
                    } else {
                        row { sh -> Link("Sign in to Bluesky", "Post, reply, like and see your timeline", sh, onSignIn) }
                    }
                }
            }
            item { OfflineStatus() }
            group("Offline") {
                row { sh -> Toggle("Offline mode", "Use saved Bluesky views; account actions wait to sync", s.offlineMode, sh) { v -> store.updateSettings { it.copy(offlineMode = v) } } }
            }
            item(key = "app-language") { io.github.shoalapps.blobfish.AppLanguage.SettingsRow() }
            group("Wellbeing") {
                row { shapes ->
                    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Break reminder", style = MaterialTheme.typography.bodyLarge)
                            Text("Remind me to take a break after time spent in Butterflyfish.", style = MaterialTheme.typography.bodySmall)
                            val intervals = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "60 min")
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                intervals.forEachIndexed { index, (minutes, label) ->
                                    SegmentedButton(
                                        selected = s.breakReminderMinutes == minutes,
                                        onClick = { store.updateSettings { it.copy(breakReminderMinutes = minutes) } },
                                        shape = SegmentedButtonDefaults.itemShape(index, intervals.size),
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
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
                row { sh -> Toggle("Rich formatting", "Show code blocks and inline code in posts, like mu", s.richFormatting, sh) { v -> store.updateSettings { it.copy(richFormatting = v) } } }
            }
            group(XBrand.name) {
                row { sh -> Toggle("Show my ${XBrand.name} account", "In the account switcher: x.com, signed in on its own page", s.showX, sh) { v -> store.updateSettings { it.copy(showX = v, service = if (!v) "bluesky" else it.service) } } }
                row { sh -> Toggle("Call it Twitter", "Use Twitter's name and bird instead of X's", s.twitterNames, sh) { v -> store.updateSettings { it.copy(twitterNames = v) }; XView.rebrand() } }
                if (s.service != "x" && s.showX && io.github.shoalapps.blobfish.ui.XView.signedIn) {
                    row { sh -> Link("Sign out of ${XBrand.name}", null, sh) { io.github.shoalapps.blobfish.ui.XView.signOut() } }
                }
            }
            group("Servers") {
                row { sh -> Link("Hosting provider", if (s.server == io.github.shoalapps.blobfish.data.Settings.DEFAULT_SERVER) "Bluesky Social" else shortHost(s.server), sh) { servers = false } }
                row { sh -> Link("AppView", if (s.appView.isBlank()) "Bluesky" else shortHost(s.appView), sh) { servers = true } }
            }
            // Moderation is Bluesky's, and its screen lives in the Bluesky side of the app.
            if (s.service != "x") group("Moderation") {
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
                row { sh -> Link("What's new", "Changes in this version", sh) { io.github.shoalapps.blobfish.WhatsNew.open() } }
                if (io.github.shoalapps.blobfish.Telemetry.available) row { sh -> Toggle("Send error reports", "Anonymous crash and error reports help fix bugs. Never your accounts or history.", io.github.shoalapps.blobfish.Telemetry.enabled, sh) { v -> io.github.shoalapps.blobfish.Telemetry.setEnabled(context, v) } }
                row { sh -> Link("Privacy policy", "What Butterflyfish sends, and to whom", sh) { openLink(context, "https://shoalapps.github.io/privacy.html") } }
                row { sh -> Link("Legal", "Licences, trademarks and notices", sh) { openLink(context, "https://shoalapps.github.io/legal.html") } }
                row { sh -> Link("Source code", "github.com/ShoalApps/butterflyfish", sh) { openLink(context, "https://github.com/ShoalApps/butterflyfish") } }
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
