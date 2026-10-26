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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Blobfish showing your X account: x.com full screen, with its own navigation, and a small
 * switcher in the corner to go back to Bluesky.
 */
@Composable
fun XAccountScreen(onSwitch: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        XScreen()
        Surface(
            onClick = onSwitch, shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 6.dp, end = 10.dp)
                .semantics { contentDescription = "Switch account" },
        ) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AccountAvatar(Graph.myAvatar, bluesky = true, size = 32.dp)
            }
        }
    }
}
