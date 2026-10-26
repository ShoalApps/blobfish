package io.github.shoalapps.blobfish.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads Twitter's own data (as its website receives it) into Blobfish's posts and people, so
 * Twitter shows in Blobfish's layout. Posts get "x:<id>" URIs and people "x:<id>" DIDs, so the rest
 * of the app can tell them from Bluesky's.
 */
object TwitterParse {
    const val PREFIX = "x:"

    fun isTwitter(uriOrDid: String) = uriOrDid.startsWith(PREFIX)

    /** Handles of the people read so far, by their "x:<id>", so tapping someone opens their profile. */
    val handles = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The handle for an "x:<id>" (someone already read) or an "x:@handle" (a mention). */
    fun handleOf(did: String): String? = if (did.startsWith(PREFIX + "@")) did.removePrefix(PREFIX + "@") else handles[did]

    /** A page of a timeline (home, a profile's posts, search), with ads left out. */
    fun timeline(root: JsonObject): Page<FeedItem> {
        val pinnedIds = mutableSetOf<String>()
        val entries = instructions(root).flatMap { ins ->
            when (ins.str("type")) {
                "TimelineAddEntries" -> ins.arr("entries")
                "TimelinePinEntry" -> listOfNotNull(ins.obj("entry")).onEach { pinnedIds += it.str("entryId").orEmpty() }
                else -> emptyList()
            }
        }
        val items = entries.flatMap { e ->
            val id = e.str("entryId").orEmpty()
            when {
                id.startsWith("promoted") -> emptyList()
                // Conversations in a home timeline: the posts in order, the last one being the newest.
                id.startsWith("home-conversation") || id.startsWith("profile-conversation") ->
                    e.obj("content")?.arr("items").orEmpty().mapNotNull { it.obj("item")?.obj("itemContent")?.let(::feedItem) }
                else -> listOfNotNull(e.obj("content")?.obj("itemContent")?.let(::feedItem)?.let { if (id in pinnedIds) it.copy(pinned = true) else it })
            }
        }
            // The pinned post comes first, as on the website.
            .sortedByDescending { it.pinned }.filter { !it.post.uri.endsWith(":promoted") }
        val cursor = entries.firstOrNull { it.obj("content")?.str("cursorType") == "Bottom" }?.obj("content")?.str("value")
        return Page(items, cursor)
    }

    /** A post and its replies, from a post's page. */
    fun thread(root: JsonObject, focusId: String): Thread? {
        val entries = instructions(root).filter { it.str("type") == "TimelineAddEntries" }.flatMap { it.arr("entries") }
        val posts = mutableListOf<Post>()
        val replies = mutableListOf<ThreadNode>()
        for (e in entries) {
            val content = e.obj("content") ?: continue
            content.obj("itemContent")?.let { tweet(it.obj("tweet_results")?.obj("result")) }?.let { posts += it }
            // Each reply thread is a module of posts, the first replying to the focused post.
            val chain = content.arr("items").mapNotNull { tweet(it.obj("item")?.obj("itemContent")?.obj("tweet_results")?.obj("result")) }
            if (chain.isNotEmpty()) replies += chain.reversed().fold(null as ThreadNode?) { acc, p -> ThreadNode(p, listOfNotNull(acc)) }!!
        }
        val focus = posts.firstOrNull { it.uri == PREFIX + focusId } ?: return null
        return Thread(posts.takeWhile { it.uri != focus.uri }, ThreadNode(focus, replies))
    }

    /** Someone's profile, from their profile page. */
    fun profile(root: JsonObject): Profile? {
        val u = root.obj("data")?.obj("user")?.obj("result") ?: return null
        val a = author(u) ?: return null
        val legacy = u.obj("legacy")
        val rel = u.obj("relationship_perspectives")
        return Profile(
            author = a,
            description = (u.obj("profile_bio")?.str("description") ?: legacy?.str("description"))?.takeIf { it.isNotBlank() },
            // Newer pages group these (relationship_counts, tweet_counts, banner, profile_bio); older ones kept them in "legacy".
            banner = (u.obj("banner")?.str("image_url") ?: legacy?.str("profile_banner_url"))?.let { "$it/1500x500" },
            followers = u.obj("relationship_counts")?.int("followers") ?: legacy?.int("followers_count") ?: 0,
            follows = u.obj("relationship_counts")?.int("following") ?: legacy?.int("friends_count") ?: 0,
            posts = u.obj("tweet_counts")?.int("tweets") ?: legacy?.int("statuses_count") ?: 0,
            myFollow = if (rel?.bool("following") == true || legacy?.bool("following") == true) "x-follow" else null,
            links = listOfNotNull(
                (u.obj("profile_bio")?.obj("entities")?.obj("url")?.arr("urls")?.firstOrNull() ?: legacy?.obj("entities")?.obj("url")?.arr("urls")?.firstOrNull())
                    ?.str("expanded_url")?.let { ProfileLink(it, null, null) },
            ),
            followsYou = rel?.bool("followed_by") == true || legacy?.bool("followed_by") == true,
        )
    }

    private fun instructions(root: JsonObject): List<JsonObject> {
        // The timeline sits at a different depth for each page (home, profile, search, thread);
        // find the "instructions" list wherever it is.
        fun find(o: JsonElement?, depth: Int): JsonArray? {
            if (depth > 8) return null
            return when (o) {
                is JsonObject -> (o["instructions"] as? JsonArray) ?: o.values.firstNotNullOfOrNull { find(it, depth + 1) }
                else -> null
            }
        }
        return find(root, 0)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    private fun feedItem(itemContent: JsonObject): FeedItem? {
        if (itemContent.obj("promotedMetadata") != null) return null
        val result = itemContent.obj("tweet_results")?.obj("result") ?: return null
        val t = unwrap(result) ?: return null
        val legacy = t.obj("legacy") ?: return null
        // A repost: show the original, "reposted by" the person who reposted it.
        legacy.obj("retweeted_status_result")?.obj("result")?.let { original ->
            return tweet(original)?.let { FeedItem(it, repostedBy = author(t.obj("core")?.obj("user_results")?.obj("result")), replyTo = null) }
        }
        val post = tweet(t) ?: return null
        return FeedItem(post, repostedBy = null, replyTo = null)
    }

    /** Some posts come wrapped (with visibility notes); this is the post itself. */
    private fun unwrap(result: JsonObject): JsonObject? = when (result.str("__typename")) {
        "TweetWithVisibilityResults" -> result.obj("tweet")
        "TweetTombstone", "TweetUnavailable" -> null
        else -> result
    }

    fun tweet(result: JsonObject?): Post? {
        val t = result?.let(::unwrap) ?: return null
        val legacy = t.obj("legacy") ?: return null
        val id = t.str("rest_id") ?: legacy.str("id_str") ?: return null
        val author = author(t.obj("core")?.obj("user_results")?.obj("result")) ?: return null
        // Long posts keep their full text in a note.
        val note = t.obj("note_tweet")?.obj("note_tweet_results")?.obj("result")
        val rawText = note?.str("text") ?: legacy.str("full_text").orEmpty()
        val entities = note?.obj("entity_set") ?: legacy.obj("entities")
        val (text, facets) = textAndFacets(rawText, entities, legacy.obj("extended_entities"))
        val media = legacy.obj("extended_entities")?.arr("media") ?: legacy.obj("entities")?.arr("media").orEmpty()
        val images = media.mapNotNull { m ->
            val url = m.str("media_url_https") ?: return@mapNotNull null
            val w = m.obj("original_info")?.int("width") ?: 0
            val h = m.obj("original_info")?.int("height") ?: 0
            Image(thumb = "$url?name=small", full = if (m.str("type") == "photo") "$url?name=orig" else url, alt = m.str("ext_alt_text").orEmpty(), aspect = if (w > 0 && h > 0) w.toFloat() / h else null)
        }
        val quote = t.obj("quoted_status_result")?.obj("result")?.let { tweet(it) }
        return Post(
            uri = PREFIX + id,
            cid = "",
            author = author,
            text = text,
            facets = facets,
            createdAt = legacy.str("created_at")?.let(::parseDate),
            replyCount = legacy.int("reply_count") ?: 0,
            repostCount = legacy.int("retweet_count") ?: 0,
            likeCount = legacy.int("favorite_count") ?: 0,
            quoteCount = legacy.int("quote_count") ?: 0,
            images = images,
            card = null,
            quote = quote,
            myLike = if (legacy.bool("favorited") == true) "x-like" else null,
            myRepost = if (legacy.bool("retweeted") == true) "x-repost" else null,
            replyRoot = legacy.str("in_reply_to_status_id_str")?.let { Ref(PREFIX + it, "") },
            tags = entities?.arr("hashtags").orEmpty().mapNotNull { it.str("text") },
        )
    }

    fun author(u: JsonObject?): Author? {
        u ?: return null
        val id = u.str("rest_id") ?: return null
        val core = u.obj("core")
        val legacy = u.obj("legacy")
        val handle = core?.str("screen_name") ?: legacy?.str("screen_name") ?: return null
        val avatar = (u.obj("avatar")?.str("image_url") ?: legacy?.str("profile_image_url_https"))?.replace("_normal.", "_400x400.")
        val rel = u.obj("relationship_perspectives")
        handles[PREFIX + id] = handle
        return Author(
            did = PREFIX + id,
            handle = handle,
            displayName = core?.str("name") ?: legacy?.str("name"),
            avatar = avatar,
            following = rel?.bool("following") == true,
            followedBy = rel?.bool("followed_by") == true,
            followUri = if (rel?.bool("following") == true) "x-follow" else null,
            verified = u.bool("is_blue_verified") == true || u.obj("verification")?.bool("verified") == true,
            // Governments and organisations (Twitter's grey and gold ticks) get the scalloped badge.
            verifier = u.obj("verification")?.str("verified_type").let { it == "Government" || it == "Business" },
            description = (u.obj("profile_bio")?.str("description") ?: legacy?.str("description"))?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Twitter's text with its short links swapped for where they go, the media link dropped, and
     * links, mentions and hashtags as Blobfish's facets (UTF-8 byte ranges, like Bluesky's).
     */
    internal fun textAndFacets(raw: String, entities: JsonObject?, extended: JsonObject?): Pair<String, List<RichText.Facet>> {
        var text = unescape(raw)
        // The picture/video link at the end isn't part of what they wrote.
        val mediaUrls = (extended?.arr("media").orEmpty() + entities?.arr("media").orEmpty()).mapNotNull { it.str("url") }.toSet()
        mediaUrls.forEach { text = text.replace(it, "") }
        text = text.trimEnd()
        val links = entities?.arr("urls").orEmpty().mapNotNull { u -> u.str("url")?.let { short -> short to (u.str("expanded_url") ?: short) } }
        links.forEach { (short, full) -> text = text.replace(short, full.removePrefix("https://").removePrefix("http://").removePrefix("www.").let { if (it.length > 40) it.take(38) + "…" else it }) }
        val facets = mutableListOf<RichText.Facet>()
        fun add(display: String, kind: RichText.Facet.Kind, value: String, from: Int = 0): Int {
            val at = text.indexOf(display, from)
            if (at < 0) return from
            val start = text.substring(0, at).toByteArray().size
            facets += RichText.Facet(start, start + display.toByteArray().size, kind, value)
            return at + display.length
        }
        links.forEach { (short, full) ->
            val shown = full.removePrefix("https://").removePrefix("http://").removePrefix("www.").let { if (it.length > 40) it.take(38) + "…" else it }
            add(shown, RichText.Facet.Kind.LINK, full)
        }
        var from = 0
        entities?.arr("user_mentions").orEmpty().forEach { m ->
            val sn = m.str("screen_name") ?: return@forEach
            from = add("@$sn", RichText.Facet.Kind.MENTION, PREFIX + "@" + sn, from)
        }
        from = 0
        entities?.arr("hashtags").orEmpty().forEach { h ->
            val tag = h.str("text") ?: return@forEach
            from = add("#$tag", RichText.Facet.Kind.TAG, tag, from)
        }
        return text to facets.sortedBy { it.byteStart }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    private val dateFormat = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)
    internal fun parseDate(s: String): Instant? = runCatching { ZonedDateTime.parse(s, dateFormat).toInstant() }.getOrNull()

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    private fun JsonElement?.obj(k: String) = (this as? JsonObject)?.get(k) as? JsonObject
}
