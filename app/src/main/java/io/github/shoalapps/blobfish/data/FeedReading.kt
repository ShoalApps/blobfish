package io.github.shoalapps.blobfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FeedAnchor(val owner: String, val feed: String, val postId: String, val offset: Int, val savedAt: Long)

/** Pure policy shared by storage, pagination and the Compose list. A repost has its post's identity. */
class FeedReading<T>(private val key: (T) -> String) {
    data class Merge<T>(val items: List<T>, val unseen: Set<String>)

    fun unique(items: List<T>): List<T> = items.distinctBy(key)

    /** Preserve the old order, update overlapping rows, and prepend genuinely new rows in server order. */
    fun refresh(old: List<T>, incoming: List<T>, unseen: Set<String>, atTop: Boolean): Merge<T> {
        val fresh = unique(incoming)
        val oldIds = old.map(key).toSet()
        val added = fresh.filter { key(it) !in oldIds }
        val updates = fresh.associateBy(key)
        return Merge(added + unique(old).map { updates[key(it)] ?: it },
            if (atTop || old.isEmpty()) emptySet() else unseen + added.map(key))
    }

    fun append(old: List<T>, page: List<T>): List<T> = unique(old + page)
    fun index(items: List<T>, anchor: FeedAnchor?): Int =
        anchor?.let { a -> items.indexOfFirst { key(it) == a.postId }.takeIf { it >= 0 } } ?: 0
    fun contains(items: List<T>, anchor: FeedAnchor?): Boolean = anchor == null || items.any { key(it) == anchor.postId }
    fun shouldSeek(items: List<T>, anchor: FeedAnchor?, pages: Int, cursor: String?): Boolean =
        !contains(items, anchor) && pages < MAX_SEEK_PAGES && cursor != null

    companion object {
        const val MAX_SEEK_PAGES = 3
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(raw: String?): List<FeedAnchor> = runCatching {
            json.decodeFromString<List<FeedAnchor>>(raw ?: "[]")
                .filter { it.owner.isNotBlank() && it.feed.isNotBlank() && it.postId.isNotBlank() && it.offset >= 0 && it.savedAt >= 0 }
                .sortedByDescending { it.savedAt }.distinctBy { it.owner to it.feed }.take(50)
        }.getOrDefault(emptyList())
        fun encode(anchors: List<FeedAnchor>): String = json.encodeToString(anchors)
        fun save(anchors: List<FeedAnchor>, anchor: FeedAnchor): List<FeedAnchor> =
            (listOf(anchor) + anchors.filterNot { it.owner == anchor.owner && it.feed == anchor.feed })
                .sortedByDescending { it.savedAt }.take(50)
    }
}

val FeedItem.readingKey: String get() = post.uri
