@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shoalapps.blobfish.data.BlueskyCompatibility
import io.github.shoalapps.blobfish.data.CompatibilityRuntime
import io.github.shoalapps.blobfish.LocalisedText as Text

/** Offline information bundled with this version; diagnostics contain type names and counts only. */
@Composable
fun CompatibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember {
        runCatching { context.assets.open("bluesky-compat.json").bufferedReader().use { BlueskyCompatibility.parse(it.readText()) } }
    }
    val data = loaded.getOrNull()
    val bluesky = remember { CompatibilityRuntime.bluesky.snapshot() }
    val x = remember { CompatibilityRuntime.x.snapshot() }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Staying up to date") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Bluesky", style = MaterialTheme.typography.headlineSmall)
                Text(data?.date?.let { "Schema from $it" } ?: "Schema snapshot not available in this version")
                Text("We check Bluesky's public schemas regularly. New data can flow automatically; new screens need building. A successful build is required before a schema update is accepted.")
                Text("${data?.shown ?: 0} of ${data?.features?.size ?: 0} kinds of post content shown (some only in part)")
                data?.upstreamCommit?.let { commit ->
                    TextButton(onClick = { openLink(context, "https://github.com/bluesky-social/atproto/commit/$commit") }) { Text("Schema source commit ${commit.take(7)}") }
                }
                if (loaded.isFailure) Text("Couldn't read the bundled compatibility information.")
                Text("Post content Butterflyfish doesn't show yet", style = MaterialTheme.typography.titleMedium)
            }
            items(data?.features?.filter { !it.rendered }.orEmpty(), key = { "feature:${it.id}" }) { feature ->
                Column {
                    Text(feature.description, localise = false, style = MaterialTheme.typography.bodyMedium)
                    Text(feature.id, localise = false, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("Types met in your Bluesky feeds", style = MaterialTheme.typography.titleMedium)
                Text("Local sightings only: type names and counts, capped at 100 names per service. Repeated reads may count again. Nothing is sent to us.")
                if (bluesky.isEmpty()) Text("No unrecognised types counted yet")
            }
            items(bluesky.toList(), key = { "bsky:${it.first}" }) { (id, count) -> Text("$id · $count", localise = false) }
            item {
                Text("X", style = MaterialTheme.typography.headlineSmall)
                Text("X has no public client source. Butterflyfish follows data returned to X's own website. Website changes can still break reading and may need app updates. Public API tracker changes inform a review issue; they never auto-change the app.")
                Text("Unrecognised X items", style = MaterialTheme.typography.titleMedium)
                if (x.isEmpty()) Text("No unrecognised types counted yet")
            }
            items(x.toList(), key = { "x:${it.first}" }) { (id, count) -> Text("$id · $count", localise = false) }
            item {
                TextButton(onClick = { openLink(context, "https://github.com/ShoalApps/butterflyfish/issues?q=is%3Aissue+%22Bluesky+changed%3A+features+to+add%22") }) { Text("Bluesky features update issue") }
                TextButton(onClick = { openLink(context, "https://github.com/ShoalApps/butterflyfish/issues?q=is%3Aissue+%22X+changes+to+check%22") }) { Text("X changes to check") }
                TextButton(onClick = { io.github.shoalapps.blobfish.WhatsNew.open() }) { Text("What's new") }
            }
        }
    }
}
