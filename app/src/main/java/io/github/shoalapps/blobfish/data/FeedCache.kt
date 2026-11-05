package io.github.shoalapps.blobfish.data

import io.github.shoalapps.blobfish.Graph
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
private data class SavedFeed(val items: List<FeedItem>, val cursor: String?)

/** Uses the existing private, bounded offline cache, in exactly the AppView/account namespace. */
class FeedCache(val owner: String, val feed: String, private val namespace: String) {
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): Page<FeedItem>? = runCatching {
        val saved = json.decodeFromString<SavedFeed>(Graph.offline.cached("reading|$feed", namespace).toString())
        fun pending(post: Post): Post = post.copy(
            myLike = Graph.offline.recordState("app.bsky.feed.like", post.uri, post.myLike),
            myRepost = Graph.offline.recordState("app.bsky.feed.repost", post.uri, post.myRepost),
            quote = post.quote?.let(::pending),
        )
        val items = if (owner.startsWith("bluesky|")) saved.items.map {
            it.copy(post = pending(it.post), parent = it.parent?.let(::pending))
        } else saved.items
        Page(items, saved.cursor)
    }.getOrNull()
    fun write(items: List<FeedItem>, cursor: String?) {
        Graph.offline.remember("reading|$feed", json.parseToJsonElement(json.encodeToString(SavedFeed(items, cursor))).jsonObject, namespace)
    }
}
