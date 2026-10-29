package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import io.github.shoalapps.blobfish.LocalisedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.data.Author
import io.github.shoalapps.blobfish.data.Moderation
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Bluesky's moderation settings. Signed in, every change is saved to your account, so the
 * official app and any other Bluesky app follow the same rules.
 */
@Composable
fun ModerationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p by Graph.store.moderation.collectAsState()
    val signedIn = Graph.store.session.collectAsState().value != null
    var addingWord by remember { mutableStateOf(false) }
    var mutes by remember { mutableStateOf<List<Author>?>(null) }
    val change: ((Moderation.Prefs) -> Moderation.Prefs) -> Unit = { f ->
        scope.launch { Graph.changeModeration(f)?.let { toast(context, it) } }
    }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            runCatching { Graph.atp.moderationPrefs() }.onSuccess(Graph.store::setModeration)
            mutes = runCatching { Graph.atp.mutes() }.getOrNull()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Moderation") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 680.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    if (signedIn) "Saved to your Bluesky account, so every Bluesky app follows the same rules."
                    else "Sign in to keep these with your account. Until then they apply on this phone only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            group("Content filters") {
                row { sh ->
                    Toggle("Enable adult content", "Each kind then follows its rule below", p.adultContent, sh) { v -> change { it.copy(adultContent = v) } }
                }
                for ((label, title) in Moderation.adultLabels + Moderation.otherLabels) {
                    val adult = Moderation.adultLabels.any { it.first == label }
                    row { sh ->
                        RuleRow(title, if (adult && !p.adultContent) null else p.rule(label), sh) { rule ->
                            change { it.copy(rules = it.rules + (label to rule.name)) }
                        }
                    }
                }
            }
            group("Muted words and tags") {
                val now = Instant.now()
                for (w in p.mutedWords) {
                    row { sh ->
                        Row2(
                            w.value,
                            listOfNotNull(
                                if (w.targets == listOf("tag")) "Tags only" else "Text and tags",
                                if (w.actorTarget == "exclude-following") "not from people you follow" else null,
                                w.expiresAt?.let { e -> if (w.expired(now)) "expired" else "until ${e.take(10)}" },
                            ).joinToString(" · "),
                            sh,
                        ) {
                            IconButton(onClick = { change { pr -> pr.copy(mutedWords = pr.mutedWords - w) } }) { Icon(Icons.Rounded.Close, "Remove ${w.value}") }
                        }
                    }
                }
                row { sh -> Link("Add a muted word or tag", "Posts containing it are hidden", sh) { addingWord = true } }
            }
            group("Following feed") {
                row { sh -> Toggle("Show replies", null, !p.hideReplies, sh) { v -> change { it.copy(hideReplies = !v) } } }
                if (!p.hideReplies) {
                    row { sh -> Toggle("Only replies to people you follow", "Hide replies to accounts you don't follow", p.hideRepliesByUnfollowed, sh) { v -> change { it.copy(hideRepliesByUnfollowed = v) } } }
                }
                row { sh -> Toggle("Show reposts", null, !p.hideReposts, sh) { v -> change { it.copy(hideReposts = !v) } } }
                row { sh -> Toggle("Show quote posts", null, !p.hideQuotePosts, sh) { v -> change { it.copy(hideQuotePosts = !v) } } }
            }
            group("Hidden posts") {
                row { sh ->
                    Link(
                        if (p.hiddenPosts.isEmpty()) "No hidden posts" else "${p.hiddenPosts.size} hidden posts",
                        if (p.hiddenPosts.isEmpty()) "Hide a post from its ⋮ menu" else "Show them all again",
                        sh,
                    ) { if (p.hiddenPosts.isNotEmpty()) change { it.copy(hiddenPosts = emptySet()) } }
                }
            }
            if (signedIn) group("Muted accounts") {
                val list = mutes
                when {
                    list == null -> row { sh -> Row2("Loading…", null, sh) {} }
                    list.isEmpty() -> row { sh -> Row2("No muted accounts", "Mute someone from a post's ⋮ menu", sh) {} }
                    else -> for (a in list) row { sh ->
                        Row2(a.name, "@${a.handle}", sh) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Graph.atp.unmute(a.did) }
                                        .onSuccess { mutes = mutes?.minus(a) }
                                        .onFailure { toast(context, it.message ?: "Couldn't unmute") }
                                }
                            }) { Text("Unmute") }
                        }
                    }
                }
            }
        }
    }

    if (addingWord) AddMutedWord(onDismiss = { addingWord = false }) { w -> addingWord = false; change { it.copy(mutedWords = it.mutedWords + w) } }
}

/** Show / Warn / Hide for one kind of content; [rule] is null when adult content is off (always hidden). */
@Composable
private fun RuleRow(title: String, rule: Moderation.Rule?, shapes: ListItemShapes, onChange: (Moderation.Rule) -> Unit) {
    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (rule == null) {
                Text("Hidden while adult content is off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val options = listOf(Moderation.Rule.SHOW to "Show", Moderation.Rule.WARN to "Warn", Moderation.Rule.HIDE to "Hide")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { i, (r, label) ->
                    SegmentedButton(selected = rule == r, onClick = { onChange(r) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun Row2(title: String, body: String?, shapes: ListItemShapes, trailing: @Composable () -> Unit) {
    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing()
        }
    }
}

@Composable
private fun AddMutedWord(onDismiss: () -> Unit, onAdd: (Moderation.MutedWord) -> Unit) {
    var text by remember { mutableStateOf("") }
    var tagsOnly by remember { mutableStateOf(false) }
    var exceptFollowing by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    val durations = listOf("Forever" to null, "24 hours" to Duration.ofDays(1), "7 days" to Duration.ofDays(7), "30 days" to Duration.ofDays(30))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute a word or tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Word, phrase or #tag") }, modifier = Modifier.fillMaxWidth())
                Text("For how long", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    durations.forEachIndexed { i, (label, _) ->
                        SegmentedButton(selected = duration == i, onClick = { duration = i }, shape = SegmentedButtonDefaults.itemShape(i, durations.size)) { Text(label, maxLines = 1) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tagsOnly, { tagsOnly = it }); Text("Only in tags") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(exceptFollowing, { exceptFollowing = it }); Text("Still show posts from people I follow") }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                onAdd(
                    Moderation.MutedWord(
                        value = text.trim().removePrefix("#"),
                        targets = if (tagsOnly) listOf("tag") else listOf("content", "tag"),
                        actorTarget = if (exceptFollowing) "exclude-following" else "all",
                        expiresAt = durations[duration].second?.let { Instant.now().plus(it).toString() },
                    ),
                )
            }) { Text("Mute") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
