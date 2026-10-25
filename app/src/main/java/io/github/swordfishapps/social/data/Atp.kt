package io.github.swordfishapps.social.data

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
) {
    val name get() = displayName?.takeIf { it.isNotBlank() } ?: handle
}

data class Image(val thumb: String, val full: String, val alt: String, val aspect: Float?)
data class LinkCard(val uri: String, val title: String, val description: String, val thumb: String?)

data class Post(
    val uri: String,
    val cid: String,
    val author: Author,
    val text: String,
    val facets: List<RichText.Facet>,
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
)

data class Ref(val uri: String, val cid: String)

data class FeedItem(
    val post: Post,
    val repostedBy: Author?,
    val replyTo: Author?,
    /** The post this replies to, shown above it in feeds like Bluesky does. */
    val parent: Post? = null,
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

data class Session(val did: String, val handle: String, val access: String, val refresh: String, val pds: String)

class AtpException(val status: Int, message: String) : Exception(message)

// ---------------- Client ----------------

/**
 * A small AT Protocol client over plain HTTP. Signed out, it reads Bluesky's public AppView; signed
 * in, it talks to your PDS, which forwards app.bsky.* calls to the AppView for you.
 */
class Atp(private val http: OkHttpClient, private val store: Store) {
    private val json = Json { ignoreUnknownKeys = true }
    private val publicHost = "https://public.api.bsky.app"

    /** Bluesky's own "Discover" feed. */
    val discoverFeed = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

    private val session get() = store.session.value

    private suspend fun call(
        method: String,
        nsid: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonObject? = null,
        auth: Boolean = session != null,
        retried: Boolean = false,
    ): JsonObject = withContext(Dispatchers.IO) {
        val s = if (auth) session else null
        val host = s?.pds ?: if (method == "GET") publicHost else "https://bsky.social"
        val url = "$host/xrpc/$nsid".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        val req = Request.Builder().url(url).apply {
            if (s != null) header("Authorization", "Bearer ${s.access}")
            // Labels are only included from labelers the request names: Bluesky's own, plus yours.
            header("atproto-accept-labelers", (listOf("${Moderation.BLUESKY_LABELER};redact") + store.moderation.value.labelers).joinToString(", "))
            if (method == "POST") post((body ?: JsonObject(emptyMap())).toString().toRequestBody("application/json".toMediaType()))
        }.build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (resp.code == 400 && obj.str("error") == "ExpiredToken" && s != null && !retried) {
                refreshSession()
                return@withContext call(method, nsid, params, body, auth, retried = true)
            }
            if (!resp.isSuccessful) throw AtpException(resp.code, obj.str("message") ?: obj.str("error") ?: "Bluesky said ${resp.code}")
            obj
        }
    }

    // ---- Sign-in ----

    /** Signs in with a handle (or email) and an app password, finding the account's own server. */
    suspend fun signIn(identifier: String, password: String) {
        val obj = call("POST", "com.atproto.server.createSession", body = buildJsonObject {
            put("identifier", identifier.trim().removePrefix("@"))
            put("password", password.trim())
        }, auth = false)
        store.setSession(sessionFrom(obj, fallbackPds = "https://bsky.social"))
    }

    private suspend fun refreshSession() = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext
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

    suspend fun timeline(cursor: String? = null): Page<FeedItem> =
        call("GET", "app.bsky.feed.getTimeline", mapOf("limit" to "30", "cursor" to cursor)).let(::feedPage)

    /** filter: posts_and_author_threads (Posts), posts_with_replies (Replies) or posts_with_media (Media). */
    suspend fun authorFeed(actor: String, cursor: String? = null, filter: String = "posts_and_author_threads"): Page<FeedItem> =
        call("GET", "app.bsky.feed.getAuthorFeed", mapOf("actor" to actor, "limit" to "30", "filter" to filter, "cursor" to cursor)).let(::feedPage)

    suspend fun profile(actor: String): Profile = parseProfile(call("GET", "app.bsky.actor.getProfile", mapOf("actor" to actor)))

    suspend fun thread(uri: String): Thread {
        val t = call("GET", "app.bsky.feed.getPostThread", mapOf("uri" to uri, "depth" to "6", "parentHeight" to "20"))["thread"].obj
            ?: throw AtpException(404, "Post not found")
        val parents = mutableListOf<Post>()
        var p = t["parent"].obj
        while (p != null && p["post"] != null) {
            parsePost(p["post"].obj!!)?.let { parents.add(0, it) }
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

    suspend fun searchPosts(q: String, cursor: String? = null): Page<Post> {
        val o = call("GET", "app.bsky.feed.searchPosts", mapOf("q" to q, "limit" to "30", "cursor" to cursor))
        return Page((o["posts"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parsePost) }, o.str("cursor"))
    }

    suspend fun searchPeople(q: String): List<Author> {
        val o = call("GET", "app.bsky.actor.searchActors", mapOf("q" to q, "limit" to "25"))
        return (o["actors"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseAuthor) }
    }

    suspend fun notifications(cursor: String? = null): Page<Notification> {
        val o = call("GET", "app.bsky.notification.listNotifications", mapOf("limit" to "40", "cursor" to cursor))
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

    // ---- Writing ----

    private fun requireSession() = session ?: throw AtpException(401, "Sign in to do that")

    private suspend fun createRecord(collection: String, record: JsonObject): String {
        val s = requireSession()
        return call("POST", "com.atproto.repo.createRecord", body = buildJsonObject {
            put("repo", s.did)
            put("collection", collection)
            put("record", record)
        }).str("uri")!!
    }

    private suspend fun deleteRecord(uri: String) {
        val s = requireSession()
        val (collection, rkey) = uri.removePrefix("at://").split("/").let { it[1] to it[2] }
        call("POST", "com.atproto.repo.deleteRecord", body = buildJsonObject {
            put("repo", s.did); put("collection", collection); put("rkey", rkey)
        })
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

    suspend fun follow(did: String): String = createRecord("app.bsky.graph.follow", buildJsonObject {
        put("\$type", "app.bsky.graph.follow"); put("subject", did); put("createdAt", now())
    })

    /** Posts [text], turning links, @handles and #tags into facets. [replyTo] makes it a reply. */
    suspend fun post(text: String, replyTo: Post? = null): String {
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

    private fun feedPage(o: JsonObject): Page<FeedItem> {
        val items = (o["feed"] as? JsonArray).orEmpty().mapNotNull { e ->
            val f = e.obj ?: return@mapNotNull null
            val post = f["post"].obj?.let(::parsePost) ?: return@mapNotNull null
            val reason = f["reason"].obj
            val by = if (reason.str("\$type")?.endsWith("reasonRepost") == true) reason!!["by"].obj?.let(::parseAuthor) else null
            val parentObj = f["reply"].obj?.get("parent").obj
            val replyTo = parentObj?.get("author").obj?.let(::parseAuthor)
            // Reposted replies show without their parent, as in Bluesky.
            val parent = if (by == null) parentObj?.let(::parsePost) else null
            FeedItem(post, by, replyTo, parent)
        }
        return Page(items, o.str("cursor"))
    }

    fun parseAuthor(o: JsonObject): Author? {
        val did = o.str("did") ?: return null
        val viewer = o["viewer"].obj
        return Author(
            did, o.str("handle") ?: did, o.str("displayName"), o.str("avatar"),
            labels = labelsOf(o),
            muted = viewer?.get("muted")?.let { (it as? JsonPrimitive)?.content == "true" } == true,
            blocking = viewer.str("blocking") != null,
            blockedBy = viewer?.get("blockedBy")?.let { (it as? JsonPrimitive)?.content == "true" } == true,
            following = viewer.str("following") != null,
            verified = o["verification"].obj.str("verifiedStatus") == "valid",
            verifier = o["verification"].obj.str("trustedVerifierStatus") == "valid",
            pronouns = o.str("pronouns")?.takeIf { it.isNotBlank() },
        )
    }

    /** Label values on a post or account, leaving out negations (labels that were taken back). */
    private fun labelsOf(o: JsonObject): List<String> = (o["labels"] as? JsonArray).orEmpty().mapNotNull { l ->
        val label = l.obj ?: return@mapNotNull null
        if ((label["neg"] as? JsonPrimitive)?.content == "true") null else label.str("val")
    }

    private fun parseProfile(o: JsonObject) = Profile(
        author = parseAuthor(o)!!,
        description = o.str("description"),
        banner = o.str("banner"),
        followers = o.int("followersCount"),
        follows = o.int("followsCount"),
        posts = o.int("postsCount"),
        myFollow = o["viewer"].obj.str("following"),
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

    fun parsePost(o: JsonObject): Post? {
        val record = o["record"].obj ?: return null
        val author = o["author"].obj?.let(::parseAuthor) ?: return null
        val embed = o["embed"].obj
        return Post(
            uri = o.str("uri") ?: return null,
            cid = o.str("cid") ?: return null,
            author = author,
            text = record.str("text").orEmpty(),
            facets = parseFacets(record["facets"]),
            createdAt = record.str("createdAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            replyCount = o.int("replyCount"),
            repostCount = o.int("repostCount"),
            likeCount = o.int("likeCount"),
            quoteCount = o.int("quoteCount"),
            images = images(embed),
            card = card(embed),
            quote = quote(embed),
            myLike = o["viewer"].obj.str("like"),
            myRepost = o["viewer"].obj.str("repost"),
            replyRoot = record["reply"].obj?.get("root").obj?.let { r -> Ref(r.str("uri") ?: return@let null, r.str("cid") ?: return@let null) },
            labels = labelsOf(o),
            tags = parseFacets(record["facets"]).filter { it.kind == RichText.Facet.Kind.TAG }.map { it.value } +
                (record["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
        )
    }

    private fun parseFacets(e: JsonElement?): List<RichText.Facet> = (e as? JsonArray).orEmpty().flatMap { f ->
        val o = f.obj ?: return@flatMap emptyList()
        val s = o["index"].obj.int("byteStart"); val end = o["index"].obj.int("byteEnd")
        (o["features"] as? JsonArray).orEmpty().mapNotNull { feat ->
            val t = feat.obj.str("\$type") ?: return@mapNotNull null
            when {
                t.endsWith("#link") -> RichText.Facet(s, end, RichText.Facet.Kind.LINK, feat.obj.str("uri") ?: return@mapNotNull null)
                t.endsWith("#mention") -> RichText.Facet(s, end, RichText.Facet.Kind.MENTION, feat.obj.str("did") ?: return@mapNotNull null)
                t.endsWith("#tag") -> RichText.Facet(s, end, RichText.Facet.Kind.TAG, feat.obj.str("tag") ?: return@mapNotNull null)
                else -> null
            }
        }
    }

    private fun media(embed: JsonObject?): JsonObject? = embed?.get("media").obj ?: embed

    private fun images(embed: JsonObject?): List<Image> = (media(embed)?.get("images") as? JsonArray).orEmpty().mapNotNull { e ->
        val i = e.obj ?: return@mapNotNull null
        val ar = i["aspectRatio"].obj
        Image(
            i.str("thumb") ?: return@mapNotNull null,
            i.str("fullsize") ?: i.str("thumb")!!,
            i.str("alt").orEmpty(),
            ar?.let { a -> a.int("width").toFloat() / a.int("height").coerceAtLeast(1) },
        )
    }

    private fun card(embed: JsonObject?): LinkCard? {
        val ext = media(embed)?.get("external").obj ?: return null
        return LinkCard(ext.str("uri") ?: return null, ext.str("title").orEmpty(), ext.str("description").orEmpty(), ext.str("thumb"))
    }

    private fun quote(embed: JsonObject?): Post? {
        val rec = (embed?.get("record").obj?.let { it["record"].obj ?: it }) ?: return null
        if (rec.str("\$type")?.endsWith("viewRecord") != true) return null
        val author = rec["author"].obj?.let(::parseAuthor) ?: return null
        val value = rec["value"].obj ?: return null
        return Post(
            uri = rec.str("uri") ?: return null, cid = rec.str("cid") ?: return null, author = author,
            text = value.str("text").orEmpty(), facets = parseFacets(value["facets"]),
            createdAt = value.str("createdAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            replyCount = 0, repostCount = 0, likeCount = 0,
            images = images((rec["embeds"] as? JsonArray)?.firstOrNull()?.obj), card = null, quote = null,
            myLike = null, myRepost = null, replyRoot = null,
        )
    }
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
