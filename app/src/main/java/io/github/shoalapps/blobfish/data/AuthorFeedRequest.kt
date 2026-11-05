package io.github.shoalapps.blobfish.data

/** Pins belong only in the profile's Posts tab, never Replies or Media. */
fun authorFeedParameters(actor: String, cursor: String?, filter: String): Map<String, String?> = mapOf(
    "actor" to actor, "limit" to "30", "filter" to filter, "cursor" to cursor,
    "includePins" to (filter == "posts_and_author_threads").toString(),
)
