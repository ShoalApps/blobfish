@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import io.github.shoalapps.blobfish.LocalisedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.R
import kotlinx.coroutines.launch

/** X's name and logo, or Twitter's when you'd rather call it that. */
object XBrand {
    val name: String get() = if (Graph.store.settings.value.twitterNames) "Twitter" else "X"
    val logo: Int get() = if (Graph.store.settings.value.twitterNames) R.drawable.logo_twitter else R.drawable.logo_x
    /** Twitter's blue for the bird; X is black. */
    val colour: Color get() = if (Graph.store.settings.value.twitterNames) Color(0xFF1D9BF0) else Color.Black
}

private val BlueskyBlue = Color(0xFF1083FE)

/** A service's logo on a small coloured disc. */
@Composable
fun ServiceLogo(logo: Int, colour: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(colour), contentAlignment = Alignment.Center) {
        Icon(painterResource(logo), null, Modifier.size(size * 0.58f), tint = Color.White)
    }
}

/** An account's picture with its service's logo in the corner (Bluesky's butterfly, or X's mark). */
@Composable
fun AccountAvatar(avatar: String?, bluesky: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        if (bluesky) Avatar(avatar, size)
        else ServiceLogo(XBrand.logo, XBrand.colour, size)
        if (bluesky || avatar != null) {
            ServiceLogo(
                if (bluesky) R.drawable.logo_bluesky else XBrand.logo, if (bluesky) BlueskyBlue else XBrand.colour, size * 0.42f,
                Modifier.align(Alignment.BottomEnd).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/**
 * Your accounts, to switch between: Bluesky (shown in Blobfish's own layout) and X (its website,
 * signed in on X's own page). Each shows its service's logo.
 */
@Composable
fun AccountSwitcher(onDismiss: () -> Unit, onSignInBluesky: () -> Unit) {
    val settings by Graph.store.settings.collectAsState()
    val session by Graph.store.session.collectAsState()
    fun use(service: String) { Graph.store.updateSettings { it.copy(service = service) }; onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val s = session
            AccountRow(
                avatar = { AccountAvatar(Graph.myAvatar, bluesky = true, size = 48.dp) },
                title = s?.handle?.let { "@$it" } ?: "Bluesky",
                subtitle = if (s != null) "Bluesky" else "Not signed in",
                current = settings.service != "x",
            ) { if (s == null) { onDismiss(); onSignInBluesky() } else use("bluesky") }
            if (settings.showX) AccountRow(
                avatar = { AccountAvatar(null, bluesky = false, size = 48.dp) },
                title = XBrand.name,
                subtitle = if (XView.signedIn) "Signed in on ${XBrand.name}'s own page" else "Sign in on ${XBrand.name}'s own page",
                current = settings.service == "x",
            ) { use("x") }
            if (s == null) Row(
                Modifier.fillMaxWidth().clickable { onDismiss(); onSignInBluesky() }.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text("Sign in to Bluesky", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            SwitchRow("Show my ${XBrand.name} account", "x.com, signed in on its own page", settings.showX) { v ->
                Graph.store.updateSettings { it.copy(showX = v, service = if (!v) "bluesky" else it.service) }
            }
            SwitchRow("Call it Twitter", "Use Twitter's name and bird instead of X's", settings.twitterNames) { v ->
                Graph.store.updateSettings { it.copy(twitterNames = v) }
                XView.rebrand()
            }
        }
    }
}

@Composable
private fun AccountRow(avatar: @Composable () -> Unit, title: String, subtitle: String, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { selected = current; role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current) Icon(Icons.Rounded.CheckCircle, "Current account", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { role = Role.Switch; stateDescription = if (checked) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

/**
 * Blobfish on your Twitter account: Twitter in Blobfish's own layout (home, search, your profile,
 * posts and profiles in the same screens as Bluesky's), read from its website kept hidden behind.
 * Until you've signed in, the website's sign-in page shows instead.
 */
@Composable
fun XAccountScreen(onSwitch: () -> Unit) {
    var signedIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(XView.signedIn) }
    // Signing in happens on the website; check now and then until it's done.
    androidx.compose.runtime.LaunchedEffect(signedIn) {
        while (!signedIn) { kotlinx.coroutines.delay(1000); signedIn = XView.signedIn }
    }
    Box(Modifier.fillMaxSize()) {
        if (!signedIn) {
            XScreen()
            SwitchButton(onSwitch, Modifier.align(Alignment.TopEnd))
            return@Box
        }
        // The website, laid out (its lists load as they're scrolled) but invisible, behind everything.
        XHidden(Modifier.fillMaxSize().androidx_alpha0())
        TwitterRoot(onSwitch)
    }
}

@Composable
private fun SwitchButton(onSwitch: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onSwitch, shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.statusBarsPadding().padding(top = 6.dp, end = 10.dp).semantics { contentDescription = "Switch account" },
    ) { Box(Modifier.padding(8.dp)) { Icon(androidx.compose.material.icons.Icons.Rounded.SwitchAccount, null) } }
}

private fun Modifier.androidx_alpha0() = this.then(Modifier.graphicsLayer { alpha = 0f })

/**
 * Choosing a server: Bluesky's, or your own address. Checked before it's saved.
 * [appView] picks between a hosting provider (where accounts live) and an AppView (which builds feeds).
 */
@Composable
fun ServerDialog(appView: Boolean, onDismiss: () -> Unit) {
    val settings by Graph.store.settings.collectAsState()
    val current = if (appView) settings.appView else settings.server
    val isDefault = if (appView) current.isBlank() else current == io.github.shoalapps.blobfish.data.Settings.DEFAULT_SERVER
    var custom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!isDefault) }
    var address by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(if (isDefault) "" else current) }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun save(value: String) = Graph.store.updateSettings { if (appView) it.copy(appView = value) else it.copy(server = value) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (appView) "AppView" else "Hosting provider") },
        text = {
            Column {
                Text(
                    if (appView) "The service that builds your feeds, profiles and threads. Bluesky runs the main one; others, like community-run AppViews, can be used instead."
                    else "Where your account lives. Bluesky Social for most people; your own address if your account is on another server (a PDS).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Choice(if (appView) "Bluesky" else "Bluesky Social", !custom) { custom = false }
                Choice("Custom", custom) { custom = true }
                if (custom) androidx.compose.material3.OutlinedTextField(
                    address, { address = it; error = null }, Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text("Address") }, placeholder = { Text(if (appView) "appview.example.com" else "pds.example.com") }, singleLine = true,
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = !busy && (!custom || address.isNotBlank()), onClick = {
                if (!custom) { save(if (appView) "" else io.github.shoalapps.blobfish.data.Settings.DEFAULT_SERVER); onDismiss(); return@TextButton }
                busy = true
                scope.launch {
                    runCatching { Graph.atp.checkServer(address, appView) }
                        .onSuccess { save(it); onDismiss() }
                        .onFailure { error = friendly(it) }
                    busy = false
                }
            }) { Text(if (busy) "Checking…" else "Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A server's address without the https://. */
fun shortHost(url: String) = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
