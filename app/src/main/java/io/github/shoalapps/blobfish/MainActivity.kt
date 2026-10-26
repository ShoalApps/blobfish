@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.blobfish

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.shoalapps.blobfish.data.Post
import io.github.shoalapps.blobfish.ui.Composer
import io.github.shoalapps.blobfish.ui.HomeScreen
import io.github.shoalapps.blobfish.ui.Nav
import io.github.shoalapps.blobfish.ui.NotificationsScreen
import io.github.shoalapps.blobfish.ui.ProfileScreen
import io.github.shoalapps.blobfish.ui.SearchScreen
import io.github.shoalapps.blobfish.ui.SettingsScreen
import io.github.shoalapps.blobfish.ui.ModerationScreen
import io.github.shoalapps.blobfish.ui.SignInScreen
import io.github.shoalapps.blobfish.ui.SocialTheme
import io.github.shoalapps.blobfish.ui.ThreadScreen
import io.github.shoalapps.blobfish.ui.YouScreen

private data class Tab(val route: String, val label: String, val on: ImageVector, val off: ImageVector)

private val xTab = Tab("x", "X", Icons.Rounded.AlternateEmail, Icons.Rounded.AlternateEmail)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("search", "Search", Icons.Rounded.Search, Icons.Outlined.Search),
    Tab("notifications", "Notifications", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
    Tab("you", "You", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),
)

/** The You tab shows your Bluesky profile picture when you're signed in. */
@Composable
private fun TabIcon(t: Tab, selected: Boolean) {
    val avatar = Graph.myAvatar
    val signedIn = Graph.store.session.collectAsState().value != null
    if (t.route == "you" && signedIn && avatar != null) {
        coil3.compose.AsyncImage(
            model = avatar,
            contentDescription = t.label,
            modifier = Modifier.size(26.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        )
    } else {
        Icon(if (selected) t.on else t.off, t.label)
    }
}

class MainActivity : ComponentActivity() {
    private val link = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Telemetry.askOnce(this)
        DataMover.offerUninstall(this)
        enableEdgeToEdge()
        link.value = intent?.data
        Graph.checkForUpdate()
        setContent {
            val settings by Graph.store.settings.collectAsState()
            SocialTheme(settings) { Root(link.value) { link.value = null } }
        }
    }

    override fun onResume() {
        super.onResume()
        Graph.pullManagerAccounts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        link.value = intent.data
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun Root(link: Uri?, onLinkHandled: () -> Unit) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var composing by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Post?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val nav = remember {
        Nav(
            post = { uri -> navController.navigate("post/${Uri.encode(uri)}") },
            profile = { actor -> navController.navigate("profile/${Uri.encode(actor)}") },
            tag = { tag -> navController.navigate("search?q=${Uri.encode("#$tag")}") },
            reply = { p -> replyTo = p; composing = true },
            people = { kind, actor -> navController.navigate("people/$kind/${Uri.encode(actor)}") },
            editProfile = { navController.navigate("editprofile") },
        )
    }
    // bsky.app/profile/<handle> and bsky.app/profile/<handle>/post/<id> open inside the app.
    LaunchedEffect(link) {
        // Back from Bluesky's sign-in page.
        if (link?.scheme == "io.github.shoalapps") {
            runCatching { io.github.shoalapps.blobfish.data.BskyOAuth.finish(context, Graph.http, link) }
                .onSuccess { Graph.atp.useOAuthSession(it); Graph.syncModeration(); Graph.syncProfile(); io.github.shoalapps.blobfish.ui.toast(context, "Signed in as @${it.handle}") }
                .onFailure { io.github.shoalapps.blobfish.ui.toast(context, io.github.shoalapps.blobfish.ui.friendly(it)) }
            onLinkHandled()
            return@LaunchedEffect
        }
        // From the widget: blobfish://compose, notifications, search, you.
        if (link?.scheme == "blobfish") {
            when (link.host) {
                "compose" -> { replyTo = null; composing = true }
                "notifications", "search", "you" -> navController.goTab(link.host!!)
                // From Shoal's Accounts screen.
                "signin" -> navController.navigate("signin")
                "x" -> { Graph.store.updateSettings { it.copy(showX = true) }; navController.goTab("x") }
            }
            onLinkHandled()
            return@LaunchedEffect
        }
        val segs = link?.pathSegments ?: return@LaunchedEffect
        if (segs.size >= 4 && segs[2] == "post") nav.post("at://${segs[1]}/app.bsky.feed.post/${segs[3]}")
        else if (segs.size >= 2) nav.profile(segs[1])
        onLinkHandled()
    }
    val showX = Graph.store.settings.collectAsState().value.showX
    val tabs = if (showX) tabs.take(3) + xTab + tabs.drop(3) else tabs
    val isTab = tabs.any { it.route == route } || route == null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                WideNavigationRail {
                    tabs.forEach { t ->
                        WideNavigationRailItem(
                            selected = route == t.route,
                            onClick = { navController.goTab(t.route) },
                            icon = { TabIcon(t, route == t.route) },
                            label = { Text(t.label) },
                            railExpanded = false,
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (!wide && isTab) {
                        ShortNavigationBar {
                            tabs.forEach { t ->
                                ShortNavigationBarItem(
                                    selected = route == t.route,
                                    onClick = { navController.goTab(t.route) },
                                    icon = { TabIcon(t, route == t.route) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(navController, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                    composable("home") { HomeScreen(nav, onSearch = { navController.goTab("search") }, onCompose = { replyTo = null; composing = true }) }
                    composable("search") { SearchScreen(nav, onBack = { navController.popBackStack() }) }
                    composable("search?q={q}") { e -> SearchScreen(nav, onBack = { navController.popBackStack() }, initial = e.arguments?.getString("q")) }
                    composable("notifications") { NotificationsScreen(nav, onSignIn = { navController.navigate("signin") }) }
                    composable("you") { YouScreen(nav, onSignIn = { navController.navigate("signin") }, onSettings = { navController.navigate("settings") }) }
                    composable("post/{uri}") { e -> ThreadScreen(Uri.decode(e.arguments?.getString("uri").orEmpty()), nav, onBack = { navController.popBackStack() }) }
                    composable("profile/{actor}") { e -> ProfileScreen(Uri.decode(e.arguments?.getString("actor").orEmpty()), nav, onBack = { navController.popBackStack() }) }
                    composable("signin") { SignInScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }, onSignIn = { navController.navigate("signin") }, onModeration = { navController.navigate("moderation") }) }
                    composable("moderation") { ModerationScreen(onBack = { navController.popBackStack() }) }
                    composable("people/{kind}/{actor}") { e -> io.github.shoalapps.blobfish.ui.PeopleScreen(e.arguments?.getString("kind").orEmpty(), Uri.decode(e.arguments?.getString("actor").orEmpty()), nav, onBack = { navController.popBackStack() }) }
                    composable("editprofile") { io.github.shoalapps.blobfish.ui.EditProfileScreen(onBack = { navController.popBackStack() }) }
                    composable("x") { io.github.shoalapps.blobfish.ui.XScreen() }
                }
            }
        }
        UpdatePrompt(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = if (!wide && isTab) 72.dp else 16.dp))
    }
    if (composing) Composer(replyTo, nav) { composing = false; replyTo = null }
}

@Composable
private fun UpdatePrompt(modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val available = Graph.Update.available
    val message = Graph.Update.message
    LaunchedEffect(available) {
        if (available == null) return@LaunchedEffect
        val r = host.showSnackbar("A new version of Blobfish is ready", actionLabel = "Update", withDismissAction = true, duration = SnackbarDuration.Indefinite)
        if (r == SnackbarResult.ActionPerformed) Graph.installUpdate() else Graph.Update.available = null
    }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        host.showSnackbar(message)
        Graph.Update.message = null
    }
    Box(modifier.padding(horizontal = 12.dp)) {
        SnackbarHost(host)
        Graph.Update.progress?.let { p ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Downloading update… ${(p * 100).toInt()}%")
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
