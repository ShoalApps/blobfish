package io.github.shoalapps.blobfish.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecordKeysTest {
    private fun write(endpoint: String = "com.atproto.repo.putRecord", owner: String = "a", key: String = "key", state: String = "pending") =
        SavedWrite(owner = owner, endpoint = endpoint, state = state, body = buildJsonObject {
            put("repo", owner); put("collection", "app.bsky.feed.like"); put("rkey", key)
        })

    @Test fun keysAreValidMonotonicTids() {
        val keys = (1..1000).map { RecordKeys.next() }
        assertTrue(keys.all { it.matches(Regex("[234567abcdefghijklmnopqrstuvwxyz]{13}")) })
        assertEquals(1000, keys.toSet().size)
        assertEquals(keys.sorted(), keys)
    }
    @Test fun undoReplacesThePendingWriteAtTheSameRecord() {
        val add = write()
        val remove = write(endpoint = "com.atproto.repo.deleteRecord")
        assertEquals(listOf(remove), RecordKeys.add(listOf(add), remove))
    }
    @Test fun inFlightWritesRemainBeforeTheirUndo() {
        val add = write(state = "sending")
        val remove = write(endpoint = "com.atproto.repo.deleteRecord")
        assertEquals(listOf(add, remove), RecordKeys.add(listOf(add), remove))
    }
    @Test fun accountsAndDifferentPostsRemainSeparate() {
        val a = write()
        val b = write(owner = "b")
        val c = write(key = "another-post")
        assertEquals(listOf(a,b,c), RecordKeys.add(listOf(a,b), c))
    }
    @Test fun preferencesNeverOverwritePendingRecordWrites() {
        val record = write()
        val preferences = SavedWrite(owner = "a", endpoint = "app.bsky.actor.putPreferences", body = JsonObject(emptyMap()))
        assertEquals(listOf(record, preferences), RecordKeys.add(listOf(record), preferences))
    }
}
