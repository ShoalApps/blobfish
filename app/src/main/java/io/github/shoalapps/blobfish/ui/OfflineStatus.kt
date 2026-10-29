package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.github.shoalapps.blobfish.LocalisedText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shoalapps.blobfish.Graph
import kotlinx.coroutines.launch

@Composable fun OfflineStatus() {
    val online by Graph.offline.online.collectAsState()
    val writes by Graph.offline.writes.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    if (!online || writes.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(12.dp)) {
        if (!online) Text("Offline · saved views. Media is available only if already cached.")
        if (writes.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) { Text("Saved Bluesky actions ("+writes.size+")") }
            if (expanded) {
                Text("Saved on this phone. Actions sync to the same Bluesky account after reconnection. Twitter actions need a connection.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { Graph.scope.launch { Graph.offline.retry(); Graph.offline.flush() } }) { Text("Retry sync") }
                writes.forEach { w ->
                    Text(w.endpoint.substringAfterLast('.') + " · " + if (w.owner != Graph.offline.owner()) "Paused for another account" else w.state,
                        style = MaterialTheme.typography.bodySmall)
                    if (w.state != "sending") TextButton(onClick = { Graph.scope.launch { Graph.offline.discard(w.id) } }) { Text("Discard") }
                }
            }
        }
    }
}
