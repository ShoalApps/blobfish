package io.github.shoalapps.blobfish.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.shoalapps.blobfish.data.Post
import io.github.shoalapps.blobfish.data.TwitterParse
import kotlinx.coroutines.launch

/**
 * Your Twitter account in Blobfish's own layout: Home ("For you" and "Following"), Search and
 * You, with posts and profiles opening in the same screens as Bluesky's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterRoot(onSwitch: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var replyTo by remember { mutableStateOf<Post?>(null) }
    var composing by remember { mutableStateOf(false) }
    val links = remember(nav) {
        Nav(
            post = { uri -> nav.navigate("post/" + Uri.encode(uri)) },
            profile = { did -> nav.navigate("profile/" + Uri.encode(did)) },
            tag = { t -> nav.navigate("search?q=" + Uri.encode("#$t")) },
            reply = { p -> replyTo = p; composing = true },
        )
    }
    val tabs = listOf(Triple("home", "Home", Icons.Rounded.Home to Icons.Outlined.Home), Triple("search", "Search", Icons.Rounded.Search to Icons.Outlined.Search), Triple("you", "You", Icons.Rounded.AccountCircle to Icons.Outlined.AccountCircle))
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route == null || tabs.any { it.first == route }) NavigationBar {
                tabs.forEach { (r, label, icons) ->
                    NavigationBarItem(
                        selected = route == r || (route == null && r == "home"),
                        onClick = { nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                        icon = { Icon(if (route == r) icons.first else icons.second, null) }, label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = { if (route == null || route == "home") FloatingActionButton(onClick = { replyTo = null; composing = true }) { Icon(Icons.Rounded.Edit, "New post") } },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding()).background(MaterialTheme.colorScheme.surface)) {
            composable("home") { TwitterHome(links, onSwitch) }
            composable("search") { TwitterSearch(links, null) }
            composable("search?q={q}") { e -> TwitterSearch(links, e.arguments?.getString("q")) }
            composable("you") {
                // null: still looking; "": couldn't tell who you are.
                val me by produceState<String?>(null) { value = XBridge.myHandle() ?: "" }
                when (val h = me) {
                    null -> Box(Modifier.fillMaxSize().statusBarsPadding()) { CenterLoading() }
                    "" -> Box(Modifier.statusBarsPadding()) { Message("Couldn't load your ${XBrand.name} profile") }
                    else -> ProfileScreen(TwitterParse.PREFIX + "@" + h, links, onBack = null, onAccounts = onSwitch)
                }
            }
            composable("post/{uri}") { e -> ThreadScreen(Uri.decode(e.arguments?.getString("uri").orEmpty()), links, onBack = { nav.popBackStack() }) }
            composable("profile/{actor}") { e -> ProfileScreen(Uri.decode(e.arguments?.getString("actor").orEmpty()), links, onBack = { nav.popBackStack() }) }
        }
    }
    if (composing) TwitterComposer(replyTo) { composing = false; replyTo = null }
}

/** Home: "For you" and "Following", like Twitter's own tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwitterHome(nav: Nav, onSwitch: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = { Text(XBrand.name) },
                    actions = { IconButton(onClick = onSwitch) { Icon(Icons.Rounded.SwitchAccount, "Switch account") } },
                    windowInsets = WindowInsets(0),
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("For you", "Following").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
    ) { pad ->
        // Each tab loads only once it's opened.
        val vm = if (tab == 0) viewModel(key = "x-home") { PagedVm { c -> XBridge.home(following = false, cursor = c) } }
            else viewModel(key = "x-following") { PagedVm { c -> XBridge.home(following = true, cursor = c) } }
        PagedList(vm, Modifier.padding(top = pad.calculateTopPadding()), empty = "Nothing here yet") { item ->
            PostCard(item.post, nav, repostedBy = item.repostedBy, replyTo = item.replyTo, pinned = item.pinned)
        }
    }
}

/** Search, newest posts first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwitterSearch(nav: Nav, initial: String?) {
    var query by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search ${XBrand.name}") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = androidx.compose.foundation.shape.CircleShape,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submitted = query.trim().takeIf { it.isNotEmpty() } }),
        )
        submitted?.let { q ->
            val vm = viewModel(key = "x-search-$q") { PagedVm { c -> XBridge.search(q, c) } }
            PagedList(vm, empty = "No posts found") { item -> PostCard(item.post, nav, repostedBy = item.repostedBy) }
        } ?: Message("Search posts on ${XBrand.name}")
    }
}

/** A new post or reply, sent through Twitter's own composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwitterComposer(replyTo: Post?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(if (replyTo != null) "Reply to @${replyTo.author.handle}" else "New post") },
        text = {
            OutlinedTextField(
                text, { if (it.length <= 280) text = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("What's happening?") }, minLines = 4,
                supportingText = { Text("${text.length} / 280") },
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = XBridge.compose(text.trim(), replyTo?.uri?.removePrefix(TwitterParse.PREFIX))
                    toast(context, if (ok) "Posted" else "Couldn't post; try again")
                    busy = false
                    if (ok) onDone()
                }
            }) { Text(if (busy) "Posting…" else "Post") }
        },
        dismissButton = { androidx.compose.material3.TextButton(enabled = !busy, onClick = onDone) { Text("Cancel") } },
    )
}
