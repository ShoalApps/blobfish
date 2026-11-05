@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.LocalisedText as Text
import io.github.shoalapps.blobfish.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** Home feeds keep their tail and reading position when a fresh head arrives. */
class ReadingFeedVm(
    /** Null keeps it to this screen, for X, whose accounts can't be told apart safely. */
    private val cache: FeedCache?,
    private val active: () -> Boolean,
    private val load: suspend (String?) -> Page<FeedItem>,
) : ViewModel() {
    private val policy = readingFeedPolicy()
    var items by mutableStateOf<List<FeedItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var unseen by mutableStateOf<Set<String>>(emptySet()); private set
    var restoreVersion by mutableStateOf(0); private set
    var restoring by mutableStateOf(true); private set
    var anchor: FeedAnchor? = null; private set
    var atTop = true
    // Bound only while this list is composed; called before publishing a new list in the same frame.
    var beforePublish: ((List<FeedItem>) -> Unit)? = null
    private var cursor: String? = null
    private var busy = false
    private var refreshAfterPlacement = true
    private var saveJob: Job? = null
    @Volatile private var saveSerial = 0L

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                cache?.let { Graph.store.feedAnchor(it.owner, it.feed) to it.read() } ?: (null to null)
            }
            if (!active()) return@launch
            anchor = saved.first
            saved.second?.let { items = policy.unique(it.items); cursor = it.cursor }
            loading = false
            restoreVersion++
        }
    }

    /** The cache has been placed on screen; only now start the network refresh. */
    fun attached() { refreshAfterPlacement = true; restoring = true }

    fun placed() {
        restoring = false
        if (refreshAfterPlacement) {
            refreshAfterPlacement = false
            refresh(seek = !policy.contains(items, anchor))
        }
    }

    fun refresh(seek: Boolean = false) {
        if (busy || loading || !active()) return
        busy = true; refreshing = true; error = null
        if (seek) restoring = true
        viewModelScope.launch {
            try {
                var page = load(null)
                var fetched = policy.unique(page.items)
                var pages = 1
                while (seek && policy.shouldSeek(fetched, anchor, pages, page.cursor)) {
                    // Missing anchors are best effort; even a failed page falls back silently.
                    val next = try { load(page.cursor) } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { break }
                    page = next; fetched = policy.append(fetched, next.items); pages++
                }
                if (!active()) return@launch
                val merged = policy.refresh(items, fetched, unseen, atTop)
                beforePublish?.invoke(merged.items)
                val hadItems = items.isNotEmpty()
                items = merged.items; unseen = merged.unseen
                // A retained tail needs its old pagination cursor, not the new head's cursor.
                if (!hadItems || seek) cursor = page.cursor
                if (seek) { restoring = true; restoreVersion++ }
                persist()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (active() && !seek) error = friendly(e)
                if (seek && active()) { anchor = null; restoreVersion++ }
            }
            finally { busy = false; refreshing = false }
        }
    }

    fun loadMore() {
        if (busy || loading || restoring || cursor == null || !active()) return
        busy = true; loadingMore = true
        viewModelScope.launch {
            try {
                val page = load(cursor)
                if (!active()) return@launch
                val merged = policy.append(items, page.items)
                items = merged; cursor = page.cursor
                persist()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Leave the existing list readable; a later scroll can retry. */ }
            finally { busy = false; loadingMore = false }
        }
    }

    private suspend fun persist() {
        val snapshot = items
        val next = cursor
        val saving = cache ?: return
        withContext(Dispatchers.IO) { if (active()) saving.write(snapshot, next) }
    }

    fun clearUnseen() { unseen = emptySet() }

    fun savePosition(id: String?, offset: Int, immediate: Boolean = false) {
        val saving = cache ?: return
        if (id == null || restoring || !active()) return
        val value = FeedAnchor(saving.owner, saving.feed, id, offset.coerceAtLeast(0), System.currentTimeMillis())
        anchor = value
        saveJob?.cancel()
        val serial = ++saveSerial
        // Application scope survives disposal/navigation. IO and commit are both off the main thread.
        saveJob = Graph.scope.launch {
            if (!immediate) delay(350)
            if (active() && serial == saveSerial) Graph.store.saveFeedAnchor(value) { active() && serial == saveSerial }
        }
    }
}

@Composable
fun ReadingFeedList(vm: ReadingFeedVm, modifier: Modifier = Modifier, empty: String = "Nothing here yet",
    headerCount: Int = 0, header: LazyListScope.() -> Unit = {}, row: @Composable (FeedItem) -> Unit) {
    if (vm.loading) {
        Box(modifier.fillMaxSize()) { CenterLoading() }
        return
    }
    // Construct the list at its cached anchor, so the first rendered frame is already positioned.
    key(vm) {
        val policy = readingFeedPolicy()
        val found = vm.anchor != null && policy.contains(vm.items, vm.anchor)
        val state = rememberLazyListState(
            initialFirstVisibleItemIndex = if (found) policy.index(vm.items, vm.anchor) + headerCount else 0,
            initialFirstVisibleItemScrollOffset = if (found) vm.anchor?.offset ?: 0 else 0,
        )
        val scope = rememberCoroutineScope()
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        fun save(immediate: Boolean = false) {
            vm.savePosition(vm.items.getOrNull((state.firstVisibleItemIndex - headerCount).coerceAtLeast(0))?.readingKey,
                if (state.firstVisibleItemIndex < headerCount) 0 else state.firstVisibleItemScrollOffset, immediate)
        }
        DisposableEffect(vm, lifecycle) {
            vm.attached()
            vm.beforePublish = { next ->
                val index = state.firstVisibleItemIndex
                val offset = state.firstVisibleItemScrollOffset
                val top = index < headerCount || (index == 0 && offset == 0)
                vm.atTop = top
                val id = vm.items.getOrNull(index - headerCount)?.readingKey
                val target = if (top) index else next.indexOfFirst { it.readingKey == id }.coerceAtLeast(0) + headerCount
                state.requestScrollToItem(target, offset)
            }
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) save(true)
            }
            lifecycle.addObserver(observer)
            onDispose { save(true); vm.beforePublish = null; lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(vm.restoreVersion) {
            if (vm.loading) return@LaunchedEffect
            val policy = readingFeedPolicy()
            val found = policy.contains(vm.items, vm.anchor)
            val target = if (found && vm.anchor != null) policy.index(vm.items, vm.anchor) + headerCount else 0
            state.scrollToItem(target, if (found) vm.anchor?.offset ?: 0 else 0)
            vm.atTop = state.firstVisibleItemIndex < headerCount || (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0)
            vm.placed()
        }
        LaunchedEffect(vm) {
            snapshotFlow { Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
                .distinctUntilChanged().collect { (scrolling, index, offset) ->
                    if (!vm.loading && !vm.restoring) {
                        vm.atTop = index < headerCount || (index == 0 && offset == 0)
                        if (vm.atTop) vm.clearUnseen()
                        if (!scrolling) save()
                    }
                }
        }
        val online by Graph.offline.online.collectAsState()
        var initialOnline by remember { mutableStateOf(true) }
        LaunchedEffect(online) {
            if (!initialOnline && online) vm.refresh()
            initialOnline = false
        }
        LaunchedEffect(vm.items.size, vm.loadingMore, vm.restoring, vm.refreshing, state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged().collect { last ->
                    if (!vm.refreshing && last >= 0 && last >= vm.items.size + headerCount - 6) vm.loadMore()
                }
        }
        Column(modifier.fillMaxSize()) {
            OfflineStatus()
            PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 680.dp), state = state,
                    contentPadding = PaddingValues(bottom = 96.dp)) {
                    header()
                    when {
                        vm.loading -> item { CenterLoading() }
                        vm.error != null && vm.items.isEmpty() -> item { Message(vm.error!!, "Try again") { vm.refresh() } }
                        vm.items.isEmpty() -> item { Message(empty) }
                        else -> items(vm.items, key = { it.readingKey }) { row(it) }
                    }
                }
                if (vm.unseen.isNotEmpty()) {
                    androidx.compose.material3.Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
                        TextButton(onClick = { scope.launch { state.scrollToItem(0); vm.clearUnseen(); save() } }) {
                            Text(if (vm.unseen.size == 1) "1 new post" else "${vm.unseen.size} new posts")
                        }
                    }
                }
            }
        }
    }
}
