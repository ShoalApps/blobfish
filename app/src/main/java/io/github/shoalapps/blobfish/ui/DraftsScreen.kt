@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.clickable
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
import io.github.shoalapps.blobfish.Graph
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
import io.github.shoalapps.blobfish.LocalisedText as Text
import io.github.shoalapps.blobfish.data.Draft
import io.github.shoalapps.blobfish.data.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DraftsScreen(nav: Nav, onBack: () -> Unit) {
    val session by Graph.store.session.collectAsState()
    val owner = session?.let { it.did + "|" + it.pds }
    // Drop rows, dialogs and any open composer as soon as the account changes.
    key(owner) { AccountDrafts(owner, nav, onBack) }
}

@Composable
private fun AccountDrafts(owner: String?, nav: Nav, onBack: () -> Unit) {
    val changes by Graph.drafts.changes.collectAsState()
    val drafts = remember(owner, changes) { owner?.let { Graph.drafts.list(it) }.orEmpty() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Post?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf<Draft?>(null) }
    fun discard(draft: Draft) {
        if (owner == null || Graph.offline.owner() != owner) return
        runCatching { Graph.drafts.discard(owner, draft.replyTo, draft.id) }
            .onSuccess { unavailable = null }
            .onFailure { toast(context, "Couldn't discard your draft. Please try again.") }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Drafts") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (drafts.isEmpty()) item { Text(if (owner == null) "Sign in to see your drafts" else "No drafts") }
            items(drafts, key = { if (it.draft.replyTo == null) "post:${it.draft.id}" else "reply:${it.draft.replyTo}" }) { saved ->
                val draft = saved.draft
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().clickable(enabled = !opening && !composing) {
                        if (owner == null || Graph.offline.owner() != owner) return@clickable
                        if (draft.replyTo == null) {
                            replyTo = null
                            selectedId = draft.id
                            composing = true
                        } else if (draft.replyTarget?.parent != null) {
                            replyTo = draft.replyTarget.toPost()
                            selectedId = draft.id
                            composing = true
                        } else {
                            opening = true
                            scope.launch {
                                try {
                                    val post = Graph.atp.thread(draft.replyTo).focus.post
                                    if (Graph.offline.owner() == owner) {
                                        replyTo = post
                                        selectedId = draft.id
                                        composing = true
                                    }
                                } catch (e: CancellationException) { throw e
                                } catch (_: Exception) {
                                    if (Graph.offline.owner() == owner) unavailable = draft
                                } finally { opening = false }
                            }
                        }
                    }.padding(16.dp)) {
                        Text(draft.text.ifBlank { "No text" }, localise = false)
                        Text((if (draft.replyTo == null) "New post" else "Reply") + " · " +
                            draft.pictures.size + (if (draft.pictures.size == 1) " picture" else " pictures"),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !opening && !composing, onClick = { discard(draft) }) { Text("Delete draft") }
                    }
                }
            }
            if (opening) item { Text("Opening draft…") }
        }
    }
    unavailable?.let { draft ->
        AlertDialog(
            onDismissRequest = { unavailable = null },
            title = { Text("Couldn't fetch the original post") },
            text = { Text("The post you're replying to may be unavailable, or your connection may be down. Your draft is still saved.") },
            confirmButton = { TextButton(onClick = { unavailable = null }) { Text("Keep draft") } },
            dismissButton = { TextButton(onClick = { discard(draft) }) { Text("Discard draft") } },
        )
    }
    if (composing) Composer(replyTo, nav, draftId = selectedId, refreshReply = true) { composing = false; replyTo = null }
}
