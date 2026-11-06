package io.github.shoalapps.blobfish.data

import io.github.shoalapps.blobfish.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

// ---------------- Models ----------------

@kotlinx.serialization.Serializable
data class Author(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatar: String?,
    /** Moderation labels on the account. */
    val labels: List<String> = emptyList(),
    val muted: Boolean = false,
    val blocking: Boolean = false,
    val blockedBy: Boolean = false,
    val following: Boolean = false,
    /** Verified by a trusted verifier (Bluesky's blue tick). */
    val verified: Boolean = false,
    /** A trusted verifier itself, like Bluesky or a news organisation (the scalloped badge). */
    val verifier: Boolean = false,
    val pronouns: String? = null,
    /** They follow you. */
    val followedBy: Boolean = false,
    /** Your follow record for them, for unfollowing. */
    val followUri: String? = null,
    /** Their bio, where the API includes it (follower and following lists, search). */
    val description: String? = null,
) {
    val name get() = displayName?.takeIf { it.isNotBlank() } ?: handle
}

@kotlinx.serialization.Serializable
data class Image(val thumb: String, val full: String, val alt: String, val aspect: Float?)
@kotlinx.serialization.Serializable
data class LinkCard(val uri: String, val title: String, val description: String, val thumb: String?)

@kotlinx.serialization.Serializable
data class MediaSource(val url: String, val mimeType: String, val bitrate: Long = 0)
@kotlinx.serialization.Serializable
data class PlayableMedia(val sources: List<MediaSource>, val thumbnail: String?, val alt: String, val aspect: Float?, val audio: Boolean = false, val loop: Boolean = false) {
    fun source(metered: Boolean, dataSaver: Boolean): MediaSource? {
        sources.firstOrNull { it.mimeType.contains("mpegurl", ignoreCase = true) }?.let { return it }
        val cap = if (dataSaver) 768_000L else if (metered) 2_500_000L else Long.MAX_VALUE
        return sources.filter { it.bitrate <= cap }.maxByOrNull { it.bitrate } ?: sources.minByOrNull { it.bitrate }
    }
}

@kotlinx.serialization.Serializable
enum class QuoteAvailability { AVAILABLE, DELETED, BLOCKED, DETACHED }

@kotlinx.serialization.Serializable
data class Post(
    val uri: String,
    val cid: String,
    val author: Author,
    val text: String,
    val facets: List<RichText.Facet>,
    @kotlinx.serialization.Serializable(with = FeedInstantSerializer::class)
    val createdAt: Instant?,
    val replyCount: Int,
    val repostCount: Int,
    val likeCount: Int,
    val quoteCount: Int = 0,
    val images: List<Image>,
    val card: LinkCard?,
    val quote: Post?,
    /** The record URI of your like, if you've liked it; used to unlike. */
    val myLike: String?,
    val myRepost: String?,
    val replyRoot: Ref?,
    /** Moderation labels on the post itself. */
    val labels: List<String> = emptyList(),
    /** Hashtags, from the text and the post's extra tags; muted words check these. */
    val tags: List<String> = emptyList(),
    val media: List<PlayableMedia> = emptyList(),
    val unsupportedTypes: List<String> = emptyList(),
    /** Null means no quote (or a cache written before availability was recorded). */
    val quoteAvailability: QuoteAvailability? = null,
    val replyDisabled: Boolean = false,
) {
    val hasMedia get() = images.isNotEmpty() || media.isNotEmpty() || card != null
}

@kotlinx.serialization.Serializable
data class Ref(val uri: String, val cid: String)

@kotlinx.serialization.Serializable
data class FeedItem(
    val post: Post,
    val repostedBy: Author?,
    val replyTo: Author?,
    /** The post this replies to, shown above it in feeds like Bluesky does. */
    val parent: Post? = null,
    /** Pinned to the top of its author's profile. */
    val pinned: Boolean = false,
)

/** A Profile Link: a highlighted link under the bio (Bluesky 1.136, October 2026). */
data class ProfileLink(val url: String, val title: String?, val icon: String?)
data class Page<T>(val items: List<T>, val cursor: String?)

data class Profile(
    val author: Author,
    val description: String?,
    val banner: String?,
    val followers: Int,
    val follows: Int,
    val posts: Int,
    val myFollow: String?,
    val links: List<ProfileLink> = emptyList(),
    /** Live on Twitch, Streamplace and the like, with where to watch. */
    val liveUrl: String? = null,
    val followsYou: Boolean = false,
)

data class ThreadNode(val post: Post, val replies: List<ThreadNode>)
data class Thread(val parents: List<Post>, val focus: ThreadNode)

data class Notification(val reason: String, val author: Author, val subject: String?, val text: String?, val at: Instant?, val read: Boolean)

/** Your sign-in. [oauth] sessions came from Bluesky's sign-in page and sign each request (DPoP). */
data class Session(val did: String, val handle: String, val access: String, val refresh: String, val pds: String, val oauth: Boolean = false, val tokenUrl: String? = null)

class AtpException(val status: Int, message: String) : Exception(message)

// ---------------- Client ----------------

/**
 * A small AT Protocol client over plain HTTP. Signed out, it reads Bluesky's public AppView; signed
 * in, it talks to your PDS, which forwards app.bsky.* calls to the AppView for you.
 */
class Atp(private val http: OkHttpClient, private val store: Store) {
    private val json = Json { ignoreUnknownKeys = true }
    /** Bluesky's own "Discover" feed. */
    private val MANY = "\u0000"
    val discoverFeed = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

    private val session get() = store.session.value

    private suspend fun call(
        method: String,
        nsid: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonObject? = null,
        auth: Boolean = session != null,
        retried: Boolean = false,
        sendQueued: Boolean = false,
        expectedOwner: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        if (method == "POST" && !sendQueued && nsid in setOf("app.bsky.actor.putPreferences",
            "app.bsky.graph.muteActor", "app.bsky.graph.unmuteActor", "com.atproto.repo.putRecord")) {
            Graph.offline.enqueue(nsid, body ?: JsonObject(emptyMap()))
            return@withContext JsonObject(emptyMap())
        }
        // Capture account and AppView together for this response's cache namespace. An account
        // switch during the request must never put the new account's posts in the old one's cache.
        val requestSession = session
        val requestAppView = store.settings.value.appView
        val namespace = (requestSession?.let { it.did + "|" + it.pds } ?: "public") + "|" + requestAppView
        val cacheKey = nsid + params.toSortedMap().toString()
        val s = if (auth) requestSession else null
        if (expectedOwner != null && s?.let { it.did + "|" + it.pds } != expectedOwner)
            throw AtpException(401, "This saved action belongs to another account")
        if (method == "GET" && !Graph.offline.available()) return@withContext Graph.offline.cached(cacheKey, namespace)
        if (body?.get("repo") != null && body.str("repo") != s?.did)
            throw AtpException(401, "This saved action belongs to a different account")
        val host = s?.pds ?: if (method == "GET") requestAppView.ifBlank { Settings.BLUESKY_APPVIEW } else store.settings.value.server
        val url = "$host/xrpc/$nsid".toHttpUrl().newBuilder().apply {
            // A value holding several values (joined with MANY) becomes the parameter repeated.
            params.forEach { (k, v) -> v?.split(MANY)?.forEach { addQueryParameter(k, it) } }
        }.build()
        val req = Request.Builder().url(url).apply {
            if (s != null && s.oauth) {
                header("Authorization", "DPoP ${s.access}")
                header("DPoP", BskyOAuth.proof(method, url.toString(), BskyOAuth.nonceFor(url.toString()), s.access))
            } else if (s != null) header("Authorization", "Bearer ${s.access}")
            // Labels are only included from labelers the request names: Bluesky's own, plus yours.
            header("atproto-accept-labelers", (listOf("${Moderation.BLUESKY_LABELER};redact") + store.moderation.value.labelers).joinToString(", "))
            // A chosen AppView: your server forwards Bluesky calls there instead of to Bluesky's.
            val appView = requestAppView
            if (s != null && appView.isNotBlank() && nsid.startsWith("app.bsky.")) header("atproto-proxy", "did:web:${appView.toHttpUrl().host}#bsky_appview")
            if (method == "POST") post((body ?: JsonObject(emptyMap())).toString().toRequestBody("application/json".toMediaType()))
        }.build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (s != null && s.oauth) {
                BskyOAuth.remember(url.toString(), resp)
                val challenge = resp.header("WWW-Authenticate").orEmpty()
                // The PDS wants a fresh nonce: try again with it.
                if (resp.code == 401 && challenge.contains("use_dpop_nonce") && !retried) return@withContext call(method, nsid, params, body, auth, retried = true, sendQueued = sendQueued, expectedOwner = expectedOwner)
                // The access token ran out: refresh it and try again.
                if (resp.code == 401 && (challenge.contains("invalid_token") || obj.str("error") == "invalid_token") && !retried) {
                    refreshSession()
                    return@withContext call(method, nsid, params, body, auth, retried = true, sendQueued = sendQueued, expectedOwner = expectedOwner)
                }
            }
            if (resp.code == 400 && obj.str("error") == "ExpiredToken" && s != null && !retried) {
                refreshSession()
                return@withContext call(method, nsid, params, body, auth, retried = true, sendQueued = sendQueued, expectedOwner = expectedOwner)
            }
            if (!resp.isSuccessful) throw AtpException(resp.code, obj.str("message") ?: obj.str("error") ?: "Bluesky said ${resp.code}")
            if (method == "GET") Graph.offline.remember(cacheKey, obj, namespace)
            obj
        }
    }

    // ---- Sign-in ----

    /** Completes a sign-in from Bluesky's page (see [BskyOAuth]). */
    fun useOAuthSession(s: Session) = store.setSession(s)

    /** Signs in with a handle (or email) and an app password, finding the account's own server. */
    suspend fun signIn(identifier: String, password: String) {
        val id = identifier.trim().removePrefix("@")
        // A handle: sign in on that account's own server. An email: on your hosting provider.
        val server = withContext(Dispatchers.IO) {
            if (!id.contains('@') && id.contains('.')) runCatching { BskyOAuth.didDoc(http, BskyOAuth.didFor(http, id)).first }.getOrNull() else null
        } ?: store.settings.value.server
        val obj = callAt(server, "com.atproto.server.createSession", buildJsonObject {
            put("identifier", id)
            put("password", password.trim())
        })
        store.setSession(sessionFrom(obj, fallbackPds = server))
    }

    /** A signed-out POST to a particular server. */
    private suspend fun callAt(server: String, nsid: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$server/xrpc/$nsid").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { resp ->
            val obj = runCatching { json.parseToJsonElement(resp.body?.string().orEmpty()) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (!resp.isSuccessful) throw AtpException(resp.code, obj.str("message") ?: obj.str("error") ?: "The server said ${resp.code}")
            obj
        }
    }

    /**
     * Checks an address before it's saved: a hosting provider must describe itself as an AT Protocol
     * server, an AppView must answer a profile lookup. Returns the tidied address.
     */
    suspend fun checkServer(address: String, appView: Boolean): String = withContext(Dispatchers.IO) {
        val url = address.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val probe = if (appView) "$url/xrpc/app.bsky.actor.getProfile?actor=bsky.app" else "$url/xrpc/com.atproto.server.describeServer"
        val ok = runCatching { http.newCall(Request.Builder().url(probe).build()).execute().use { it.isSuccessful } }.getOrDefault(false)
        if (!ok) throw AtpException(404, if (appView) "That doesn't look like an AppView" else "That doesn't look like an AT Protocol server")
        url
    }

    private suspend fun refreshSession() = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext
        if (s.oauth) {
            runCatching { BskyOAuth.refresh(http, s) }.onSuccess { store.setSession(it) }.onFailure { store.setSession(null); throw AtpException(401, "Please sign in again") }
            return@withContext
        }
        val req = Request.Builder().url("${s.pds}/xrpc/com.atproto.server.refreshSession")
            .header("Authorization", "Bearer ${s.refresh}")
            .post(ByteArray(0).toRequestBody())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) { store.setSession(null); throw AtpException(resp.code, "Please sign in again") }
            val obj = json.parseToJsonElement(resp.body!!.string()) as JsonObject
            store.setSession(sessionFrom(obj, s.pds))
        }
    }

    private fun sessionFrom(o: JsonObject, fallbackPds: String): Session {
        val pds = (o["didDoc"].obj?.get("service") as? JsonArray)
            ?.firstOrNull { it.obj.str("id")?.endsWith("atproto_pds") == true }
            ?.obj.str("serviceEndpoint") ?: fallbackPds
        return Session(o.str("did")!!, o.str("handle")!!, o.str("accessJwt")!!, o.str("refreshJwt")!!, pds.trimEnd('/'))
    }

    // ---- Moderation preferences (kept in your account, shared with every Bluesky app) ----

    private val prefType = "app.bsky.actor.defs#"

    /** Your account's moderation preferences. */
    suspend fun moderationPrefs(): Moderation.Prefs {
        val all = (call("GET", "app.bsky.actor.getPreferences")["preferences"] as? JsonArray).orEmpty().mapNotNull { it.obj }
        fun of(type: String) = all.lastOrNull { it.str("\$type") == prefType + type }
        val feedView = all.lastOrNull { it.str("\$type") == prefType + "feedViewPref" && it.str("feed") == "home" }
        fun flag(o: JsonObject?, key: String, default: Boolean) = (o?.get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: default
        return Moderation.Prefs(
            adultContent = flag(of("adultContentPref"), "enabled", false),
            rules = all.filter { it.str("\$type") == prefType + "contentLabelPref" && it.str("labelerDid") == null }
                .mapNotNull { p ->
                    val label = p.str("label") ?: return@mapNotNull null
                    val rule = when (p.str("visibility")) { "ignore", "show" -> "SHOW"; "warn" -> "WARN"; "hide" -> "HIDE"; else -> return@mapNotNull null }
                    Moderation.canonical(label) to rule
                }.toMap(),
            mutedWords = (of("mutedWordsPref")?.get("items") as? JsonArray).orEmpty().mapNotNull { e ->
                val w = e.obj ?: return@mapNotNull null
                Moderation.MutedWord(
                    value = w.str("value") ?: return@mapNotNull null,
                    targets = (w["targets"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.ifEmpty { listOf("content", "tag") },
                    actorTarget = w.str("actorTarget") ?: "all",
                    expiresAt = w.str("expiresAt"),
                    id = w.str("id"),
                )
            },
            hiddenPosts = (of("hiddenPostsPref")?.get("items") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet(),
            hideReplies = flag(feedView, "hideReplies", false),
            hideRepliesByUnfollowed = flag(feedView, "hideRepliesByUnfollowed", true),
            hideReposts = flag(feedView, "hideReposts", false),
            hideQuotePosts = flag(feedView, "hideQuotePosts", false),
            labelers = (of("labelersPref")?.get("labelers") as? JsonArray).orEmpty().mapNotNull { it.obj.str("did") },
        )
    }

    /**
     * Saves moderation preferences to your account. Preferences this app doesn't manage (saved
     * feeds, interests, and so on) are kept as they are.
     */
    suspend fun saveModerationPrefs(p: Moderation.Prefs) {
        requireSession()
        val managed = setOf("adultContentPref", "mutedWordsPref", "hiddenPostsPref").map { prefType + it }
        val current = (call("GET", "app.bsky.actor.getPreferences")["preferences"] as? JsonArray).orEmpty().mapNotNull { it.obj }
        val kept = current.filter { o ->
            val t = o.str("\$type")
            when {
                t in managed -> false
                t == prefType + "contentLabelPref" -> o.str("labelerDid") != null // only Bluesky-wide rules are ours
                t == prefType + "feedViewPref" -> o.str("feed") != "home"
                else -> true
            }
        }
        val ours = buildList {
            add(buildJsonObject { put("\$type", prefType + "adultContentPref"); put("enabled", p.adultContent) })
            for ((label, _) in Moderation.adultLabels + Moderation.otherLabels) {
                val visibility = when (p.rules[label]?.let { runCatching { Moderation.Rule.valueOf(it) }.getOrNull() } ?: Moderation.defaultRules[label]) {
                    Moderation.Rule.SHOW -> "ignore"; Moderation.Rule.WARN -> "warn"; else -> "hide"
                }
                add(buildJsonObject { put("\$type", prefType + "contentLabelPref"); put("label", label); put("visibility", visibility) })
            }
            add(buildJsonObject {
                put("\$type", prefType + "mutedWordsPref")
                put("items", JsonArray(p.mutedWords.map { w ->
                    buildJsonObject {
                        put("id", w.id ?: java.util.UUID.randomUUID().toString())
                        put("value", w.value)
                        put("targets", JsonArray(w.targets.map { JsonPrimitive(it) }))
                        put("actorTarget", w.actorTarget)
                        w.expiresAt?.let { put("expiresAt", it) }
                    }
                }))
            })
            add(buildJsonObject { put("\$type", prefType + "hiddenPostsPref"); put("items", JsonArray(p.hiddenPosts.map { JsonPrimitive(it) })) })
            add(buildJsonObject {
                put("\$type", prefType + "feedViewPref"); put("feed", "home")
                put("hideReplies", p.hideReplies); put("hideRepliesByUnfollowed", p.hideRepliesByUnfollowed)
                put("hideReposts", p.hideReposts); put("hideQuotePosts", p.hideQuotePosts)
            })
        }
        call("POST", "app.bsky.actor.putPreferences", body = buildJsonObject { put("preferences", JsonArray(kept + ours)) })
    }

    /** Accounts you've muted. */
    suspend fun mutes(): List<Author> =
        (call("GET", "app.bsky.graph.getMutes", mapOf("limit" to "100"))["mutes"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseAuthor) }

    suspend fun mute(did: String) { call("POST", "app.bsky.graph.muteActor", body = buildJsonObject { put("actor", did) }) }
    suspend fun unmute(did: String) { call("POST", "app.bsky.graph.unmuteActor", body = buildJsonObject { put("actor", did) }) }

    // ---- Reading ----

    suspend fun feed(feed: String, cursor: String? = null): Page<FeedItem> =
        call("GET", "app.bsky.feed.getFeed", mapOf("feed" to feed, "limit" to "30", "cursor" to cursor)).let(::feedPage)

    /** A pinned home tab: your Following timeline, a custom feed, or a list. */
    data class PinnedFeed(val kind: String, val uri: String, val name: String)

    /**
     * Your pinned feeds in the order you set in Bluesky (savedFeedsPrefV2), named from Bluesky,
     * for the home screen's tabs. Signed out, just Discover.
     */
    suspend fun pinnedFeeds(): List<PinnedFeed> {
        if (session == null) return listOf(PinnedFeed("feed", discoverFeed, "Discover"))
        val prefs = (call("GET", "app.bsky.actor.getPreferences")["preferences"] as? JsonArray).orEmpty().mapNotNull { it.obj }
        val saved = prefs.lastOrNull { it.str("\$type") == prefType + "savedFeedsPrefV2" }?.get("items") as? JsonArray
        val pinned = saved.orEmpty().mapNotNull { it.obj }.filter { (it["pinned"] as? JsonPrimitive)?.content == "true" }
            .mapNotNull { o -> (o.str("type") ?: return@mapNotNull null) to (o.str("value") ?: return@mapNotNull null) }
        if (pinned.isEmpty()) return listOf(PinnedFeed("timeline", "following", "Following"), PinnedFeed("feed", discoverFeed, "Discover"))
        val feedUris = pinned.filter { it.first == "feed" }.map { it.second }
        val names = mutableMapOf<String, String>()
        if (feedUris.isNotEmpty()) runCatching {
            val o = call("GET", "app.bsky.feed.getFeedGenerators", mapOf("feeds" to feedUris.joinToString(MANY)))
            (o["feeds"] as? JsonArray).orEmpty().mapNotNull { it.obj }.forEach { f -> names[f.str("uri").orEmpty()] = f.str("displayName").orEmpty() }
        }
        pinned.filter { it.first == "list" }.forEach { (_, uri) ->
            runCatching { names[uri] = call("GET", "app.bsky.graph.getList", mapOf("list" to uri, "limit" to "1"))["list"].obj.str("name").orEmpty() }
        }
        return pinned.map { (type, value) ->
            when (type) {
                "timeline" -> PinnedFeed("timeline", value, "Following")
                else -> PinnedFeed(type, value, names[value]?.takeIf { it.isNotBlank() } ?: if (type == "list") "List" else "Feed")
            }
        }
    }

    suspend fun listFeed(list: String, cursor: String? = null): Page<FeedItem> =
        call("GET", "app.bsky.feed.getListFeed", mapOf("list" to list, "limit" to "30", "cursor" to cursor)).let(::feedPage)

    suspend fun timeline(cursor: String? = null): Page<FeedItem> =
        call("GET", "app.bsky.feed.getTimeline", mapOf("limit" to "30", "cursor" to cursor)).let(::feedPage)

    /** filter: posts_and_author_threads (Posts), posts_with_replies (Replies) or posts_with_media (Media). */
    suspend fun authorFeed(actor: String, cursor: String? = null, filter: String = "posts_and_author_threads"): Page<FeedItem> =
        call("GET", "app.bsky.feed.getAuthorFeed", authorFeedParameters(actor, cursor, filter)).let(::feedPage)

    suspend fun hiddenFollowFeed(actor: String, cursor: String?): Page<FeedItem> =
        call("GET", "app.bsky.feed.getAuthorFeed", authorFeedParameters(actor, cursor, "posts_no_replies"), auth = false)
            .let(::feedPage)

    suspend fun profile(actor: String): Profile = parseProfile(call("GET", "app.bsky.actor.getProfile", mapOf("actor" to actor)))

    suspend fun thread(uri: String): Thread {
        val t = call("GET", "app.bsky.feed.getPostThread", mapOf("uri" to uri, "depth" to "6", "parentHeight" to "20"))["thread"].obj
            ?: throw AtpException(404, "Post not found")
        val parents = mutableListOf<Post>()
        var p = t["parent"].obj
        while (p != null && p["post"] != null) {
            p["post"].obj?.let(::parsePost)?.let { parents.add(0, it) }
            p = p["parent"].obj
        }
        return Thread(parents, parseNode(t) ?: throw AtpException(404, "Post not found"))
    }

    private fun parseNode(o: JsonObject): ThreadNode? {
        val post = o["post"].obj?.let(::parsePost) ?: return null
        val replies = (o["replies"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseNode) }
            .sortedByDescending { it.post.likeCount }
        return ThreadNode(post, replies)
    }

    suspend fun searchPosts(q: String, cursor: String? = null, latest: Boolean = false): Page<Post> {
        val o = call("GET", "app.bsky.feed.searchPosts", mapOf("q" to q, "limit" to "30", "cursor" to cursor, "sort" to if (latest) "latest" else "top"))
        return Page((o["posts"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parsePost) }, o.str("cursor"))
    }

    suspend fun searchPeople(q: String, cursor: String?): Page<Author> {
        val response = call("GET", "app.bsky.actor.searchActors", mapOf("q" to q, "limit" to "25", "cursor" to cursor))
        val people = (response["actors"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseAuthor) }
        return Page(people, response.str("cursor"))
    }

    suspend fun notifications(cursor: String?, reasons: List<String>, owner: String): Page<Notification> {
        val o = call("GET", "app.bsky.notification.listNotifications", mapOf("limit" to "40", "cursor" to cursor, "reasons" to reasons.takeIf { it.isNotEmpty() }?.joinToString(MANY)),
            expectedOwner = owner)
        val items = (o["notifications"] as? JsonArray).orEmpty().mapNotNull { e ->
            val n = e.obj ?: return@mapNotNull null
            Notification(
                reason = n.str("reason") ?: "",
                author = parseAuthor(n["author"].obj ?: return@mapNotNull null) ?: return@mapNotNull null,
                subject = n.str("reasonSubject") ?: n.str("uri"),
                text = n["record"].obj.str("text"),
                at = n.str("indexedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                read = n["isRead"].bool == true,
            )
        }
        return Page(items, o.str("cursor"))
    }

    suspend fun markNotificationsRead(seenAt: Instant, owner: String) {
        check(Graph.offline.available()) { "Connect to Bluesky to mark notifications as read" }
        call("POST", "app.bsky.notification.updateSeen", body = buildJsonObject {
            put("seenAt", seenAt.toString())
        }, expectedOwner = owner)
    }

    // ---- Writing ----

    internal suspend fun sendSaved(write: SavedWrite) {
        if (Graph.offline.owner() != write.owner) throw AtpException(401, "Different account")
        call("POST", write.endpoint, body = write.body, sendQueued = true, expectedOwner = write.owner)
    }

    private fun requireSession() = session ?: throw AtpException(401, "Sign in to do that")

    private suspend fun createRecord(collection: String, record: JsonObject): String {
        val s = requireSession()
        val key = RecordKeys.next()
        val body = buildJsonObject {
            put("repo", s.did); put("collection", collection); put("rkey", key); put("record", record)
        }
        Graph.offline.enqueue("com.atproto.repo.putRecord", body)
        return "at://"+s.did+"/"+collection+"/"+key
    }

    private suspend fun deleteRecord(uri: String) {
        val s = requireSession()
        val (collection, rkey) = uri.removePrefix("at://").split("/").let { it[1] to it[2] }
        require(uri.startsWith("at://"+s.did+"/")) { "This action belongs to another account" }
        Graph.offline.enqueue("com.atproto.repo.deleteRecord", buildJsonObject {
            put("repo", s.did); put("collection", collection); put("rkey", rkey)
        })
    }

    suspend fun deleteOwnPost(uri: String, owner: String) {
        val s = requireSession()
        val prefix = "at://${s.did}/app.bsky.feed.post/"
        val key = uri.removePrefix(prefix)
        require(uri.startsWith(prefix) && key.matches(Regex("[A-Za-z0-9._~:-]{1,512}")) && key !in setOf(".", "..")) {
            "This post belongs to another account"
        }
        check(Graph.offline.available()) { "Connect to Bluesky to delete this post" }
        val body = buildJsonObject {
            put("repo", s.did)
            put("collection", "app.bsky.feed.post")
            put("rkey", key)
        }
        call("POST", "com.atproto.repo.deleteRecord", body = body, expectedOwner = owner)
        store.rememberDeletedPost(uri)
    }

    private fun subject(p: Post) = buildJsonObject { put("uri", p.uri); put("cid", p.cid) }
    private fun now() = Instant.now().toString()

    suspend fun like(p: Post): String = createRecord("app.bsky.feed.like", buildJsonObject {
        put("\$type", "app.bsky.feed.like"); put("subject", subject(p)); put("createdAt", now())
    })
    suspend fun repost(p: Post): String = createRecord("app.bsky.feed.repost", buildJsonObject {
        put("\$type", "app.bsky.feed.repost"); put("subject", subject(p)); put("createdAt", now())
    })
    suspend fun undo(recordUri: String) = deleteRecord(recordUri)

    // ---- People and your profile ----

    suspend fun followers(actor: String, cursor: String? = null): Page<Author> {
        val o = call("GET", "app.bsky.graph.getFollowers", mapOf("actor" to actor, "limit" to "50", "cursor" to cursor))
        return Page((o["followers"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseAuthor) }, o.str("cursor"))
    }

    suspend fun follows(actor: String, cursor: String? = null): Page<Author> {
        val o = call("GET", "app.bsky.graph.getFollows", mapOf("actor" to actor, "limit" to "50", "cursor" to cursor))
        return Page((o["follows"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseAuthor) }, o.str("cursor"))
    }

    /** Uploads an image to your PDS; returns the blob reference a record can point to. */
    suspend fun uploadBlob(bytes: ByteArray, mime: String): JsonObject = withContext(Dispatchers.IO) {
        val s = requireSession()
        val req = Request.Builder().url("${s.pds}/xrpc/com.atproto.repo.uploadBlob")
            .header("Authorization", "Bearer ${s.access}")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val obj = runCatching { json.parseToJsonElement(resp.body!!.string()) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (!resp.isSuccessful) throw AtpException(resp.code, obj.str("message") ?: "Couldn't upload the picture")
            obj["blob"].obj ?: throw AtpException(500, "Couldn't upload the picture")
        }
    }

    /**
     * Saves your profile: reads the current record, changes only what's given (null leaves a field
     * as it is) and writes it back, so fields this app doesn't know about are kept.
     */
    suspend fun updateProfile(displayName: String? = null, description: String? = null, avatar: JsonObject? = null, banner: JsonObject? = null) {
        val s = requireSession()
        val current = runCatching {
            call("GET", "com.atproto.repo.getRecord", mapOf("repo" to s.did, "collection" to "app.bsky.actor.profile", "rkey" to "self"))
        }.getOrNull()
        val record = (current?.get("value").obj ?: buildJsonObject { put("\$type", "app.bsky.actor.profile") }).toMutableMap()
        displayName?.let { record["displayName"] = JsonPrimitive(it) }
        description?.let { record["description"] = JsonPrimitive(it) }
        avatar?.let { record["avatar"] = it }
        banner?.let { record["banner"] = it }
        call("POST", "com.atproto.repo.putRecord", body = buildJsonObject {
            put("repo", s.did)
            put("collection", "app.bsky.actor.profile")
            put("rkey", "self")
            put("record", JsonObject(record))
            current?.str("cid")?.let { put("swapRecord", it) }
        })
    }

    suspend fun follow(did: String): String = createRecord("app.bsky.graph.follow", buildJsonObject {
        put("\$type", "app.bsky.graph.follow"); put("subject", did); put("createdAt", now())
    })

    /** Posts [text], turning links, @handles and #tags into facets. [replyTo] makes it a reply. */
    /** A picture to attach: the uploaded blob, its alt text and size. */
    data class Attachment(val blob: JsonObject, val alt: String, val width: Int, val height: Int)

    suspend fun post(text: String, replyTo: Post? = null, images: List<Attachment> = emptyList()): String {
        check(replyTo?.replyDisabled != true) { "Replies are restricted on this post" }
        val facets = RichText.detect(text).mapNotNull { f ->
            val feature = when (f.kind) {
                RichText.Facet.Kind.LINK -> buildJsonObject { put("\$type", "app.bsky.richtext.facet#link"); put("uri", f.value) }
                RichText.Facet.Kind.TAG -> buildJsonObject { put("\$type", "app.bsky.richtext.facet#tag"); put("tag", f.value) }
                RichText.Facet.Kind.MENTION -> {
                    val did = runCatching { call("GET", "com.atproto.identity.resolveHandle", mapOf("handle" to f.value)).str("did") }.getOrNull()
                        ?: return@mapNotNull null
                    buildJsonObject { put("\$type", "app.bsky.richtext.facet#mention"); put("did", did) }
                }
            }
            buildJsonObject {
                putJsonObject("index") {
                    put("byteStart", RichText.byteIndex(text, f.start))
                    put("byteEnd", RichText.byteIndex(text, f.end))
                }
                putJsonArray("features") { add(feature) }
            }
        }
        return createRecord("app.bsky.feed.post", buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", text)
            put("createdAt", now())
            if (facets.isNotEmpty()) put("facets", JsonArray(facets))
            if (images.isNotEmpty()) putJsonObject("embed") {
                put("\$type", "app.bsky.embed.images")
                putJsonArray("images") {
                    images.forEach { a ->
                        add(buildJsonObject {
                            put("image", a.blob)
                            put("alt", a.alt)
                            putJsonObject("aspectRatio") { put("width", a.width); put("height", a.height) }
                        })
                    }
                }
            }
            if (replyTo != null) {
                val root = replyTo.replyRoot ?: Ref(replyTo.uri, replyTo.cid)
                putJsonObject("reply") {
                    putJsonObject("root") { put("uri", root.uri); put("cid", root.cid) }
                    put("parent", subject(replyTo))
                }
            }
        })
    }

    // ---- Parsing ----

    private val parser = BlueskyParse(
        recordState = { collection, uri, value -> Graph.offline.recordState(collection, uri, value) },
        unknown = { CompatibilityRuntime.bluesky.record(it) },
    )
    private fun feedPage(o: JsonObject) = parser.feedPage(o)
    fun parseAuthor(o: JsonObject) = parser.parseAuthor(o)
    fun parsePost(o: JsonObject) = parser.parsePost(o)

    private fun parseProfile(o: JsonObject) = Profile(
        author = parseAuthor(o) ?: throw AtpException(502, "Bluesky returned an incomplete profile"),
        description = o.str("description"),
        banner = o.str("banner"),
        followers = o.int("followersCount"),
        follows = o.int("followsCount"),
        posts = o.int("postsCount"),
        myFollow = Graph.offline.recordState("app.bsky.graph.follow", o.str("did").orEmpty(), o["viewer"].obj.str("following")),
        links = (o["links"] as? JsonArray).orEmpty().mapNotNull { e ->
            val l = e.obj ?: return@mapNotNull null
            ProfileLink(l.str("url") ?: return@mapNotNull null, l.str("title")?.takeIf { it.isNotBlank() }, l.str("icon"))
        },
        liveUrl = o["status"].obj?.let { st ->
            val active = (st["isActive"] as? JsonPrimitive)?.content == "true"
            if (st.str("status") == "app.bsky.actor.status#live" && active) st["embed"].obj?.get("external").obj.str("uri") else null
        },
        followsYou = o["viewer"].obj.str("followedBy") != null,
    )


}

// Forgiving JSON helpers.
internal val JsonElement?.obj get() = this as? JsonObject
internal fun JsonObject?.str(key: String) = (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject?.int(key: String) = (this?.get(key) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
internal val JsonElement?.bool get() = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

/** "3m", "5h", "2d", or a date for anything older, the way Bluesky shows times. */
fun shortAgo(at: Instant?): String {
    if (at == null) return ""
    val s = (System.currentTimeMillis() / 1000 - at.epochSecond).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK)
            .format(at.atZone(java.time.ZoneId.systemDefault()))
    }
}

/** "1.2K", "3.4M" for counts. */
fun compact(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.UK, "%.1fK", n / 1000.0).replace(".0K", "K")
    else -> String.format(java.util.Locale.UK, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
}

/** The bsky.app web link for a post, for sharing. */
fun webUrl(p: Post): String = "https://bsky.app/profile/${p.author.handle}/post/${p.uri.substringAfterLast('/')}"
