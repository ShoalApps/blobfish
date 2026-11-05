package io.github.shoalapps.blobfish.data

import kotlinx.serialization.json.*
import java.time.Instant

/** Pure, tolerant AppView parsing. Account overlays and local diagnostics are injected. */
class BlueskyParse(
    private val recordState: (String, String, String?) -> String? = { _, _, value -> value },
    private val unknown: (String) -> Unit = {},
) {
    private val reasons = setOf("app.bsky.feed.defs#reasonRepost", "app.bsky.feed.defs#reasonPin")
    private val embeds = setOf("app.bsky.embed.images#view", "app.bsky.embed.external#view",
        "app.bsky.embed.video#view", "app.bsky.embed.record#view", "app.bsky.embed.recordWithMedia#view",
        "app.bsky.embed.gallery#view")
    /** Unavailable quotes are known variants and have their own placeholder. */
    private val quotes = setOf("app.bsky.embed.record#viewRecord", "app.bsky.embed.record#viewNotFound",
        "app.bsky.embed.record#viewBlocked", "app.bsky.embed.record#viewDetached")
    private val features = setOf("app.bsky.richtext.facet#link", "app.bsky.richtext.facet#mention", "app.bsky.richtext.facet#tag")

    private fun unsupported(record: JsonObject, embed: JsonObject?): List<String> {
        val found = linkedSetOf<String>()
        fun check(o: JsonObject?, known: Set<String>, missing: String) {
            if (o != null && o.str("\$type") !in known) found += o.str("\$type") ?: missing
        }
        val recordType = record.str("\$type")
        if (recordType != null && recordType != "app.bsky.feed.post") found += recordType
        check(embed, embeds, "Missing embed type")
        check(embed?.get("media").obj, embeds, "Missing embed type")
        val quote = embed?.get("record").obj?.let { it["record"].obj ?: it }
        check(quote, quotes, "Missing quoted record type")
        (record["facets"] as? JsonArray).orEmpty().forEach { facet ->
            (facet.obj?.get("features") as? JsonArray).orEmpty().forEach {
                if (it.obj == null) found += "Missing facet type"
                else {
                    check(it.obj, features, "Missing facet type")
                    val required = when (it.obj.str("\$type")) {
                        "app.bsky.richtext.facet#link" -> "uri"
                        "app.bsky.richtext.facet#mention" -> "did"
                        "app.bsky.richtext.facet#tag" -> "tag"
                        else -> null
                    }
                    if (required != null && it.obj.str(required) == null) found += "Missing facet value"
                }
            }
        }
        return found.toList()
    }
    fun feedPage(o: JsonObject): Page<FeedItem> = Page(
        (o["feed"] as? JsonArray).orEmpty().mapNotNull { it.obj?.let(::parseFeedItem) }, o.str("cursor"))

    private fun parseFeedItem(f: JsonObject): FeedItem? = try { feedItemValue(f) } catch (_: Exception) { null }

    private fun feedItemValue(f: JsonObject): FeedItem? {
        var post = f["post"].obj?.let(::parsePost) ?: return null
        val reason = f["reason"].obj
        val reasonType = reason.str("\$type")
        if (f["reason"] != null && (reason == null || reasonType !in reasons)) {
            reasonType?.let(unknown)
            post = post.copy(unsupportedTypes = post.unsupportedTypes + (reasonType ?: "Missing reason type"))
        }
        val by = if (reason.str("\$type") == "app.bsky.feed.defs#reasonRepost") reason?.get("by").obj?.let(::parseAuthor) else null
        val parentObj = f["reply"].obj?.get("parent").obj
        val replyTo = parentObj?.get("author").obj?.let(::parseAuthor)
        // Reposted replies show without their parent, as in Bluesky.
        val parent = if (by == null) parentObj?.let(::parsePost) else null
        return FeedItem(post, by, replyTo, parent, pinned = reason.str("\$type") == "app.bsky.feed.defs#reasonPin")
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
            following = recordState("app.bsky.graph.follow", did, viewer.str("following")) != null,
            verified = o["verification"].obj.str("verifiedStatus") == "valid",
            verifier = o["verification"].obj.str("trustedVerifierStatus") == "valid",
            pronouns = o.str("pronouns")?.takeIf { it.isNotBlank() },
            followedBy = viewer.str("followedBy") != null,
            followUri = recordState("app.bsky.graph.follow", did, viewer.str("following")),
            description = o.str("description")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Label values on a post or account, leaving out negations (labels that were taken back). */
    private fun labelsOf(o: JsonObject): List<String> = (o["labels"] as? JsonArray).orEmpty().mapNotNull { l ->
        val label = l.obj ?: return@mapNotNull null
        if ((label["neg"] as? JsonPrimitive)?.content == "true") null else label.str("val")
    }

    fun parsePost(o: JsonObject): Post? = try { parsePostValue(o) } catch (_: Exception) { null }

    private fun parsePostValue(o: JsonObject): Post? {
        val record = o["record"].obj ?: return null
        val author = o["author"].obj?.let(::parseAuthor) ?: return null
        val embed = o["embed"].obj
        val unsupported = unsupported(record, embed).toMutableList()
        if (o["embed"] != null && embed == null) unsupported += "Missing embed object"
        if (record["facets"] != null && record["facets"] !is JsonArray) unsupported += "Missing facets array"
        unsupported.filter { !it.startsWith("Missing ") }.forEach(unknown)
        return Post(
            unsupportedTypes = unsupported,
            uri = o.str("uri") ?: return null,
            cid = o.str("cid") ?: return null,
            author = author,
            text = record.str("text") ?: return null,
            facets = parseFacets(record["facets"]),
            createdAt = record.str("createdAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            replyCount = o.int("replyCount"),
            repostCount = o.int("repostCount"),
            likeCount = o.int("likeCount"),
            quoteCount = o.int("quoteCount"),
            images = images(embed),
            media = MediaParsing.bluesky(embed),
            card = card(embed),
            quote = quote(embed),
            quoteAvailability = quoteAvailability(embed),
            myLike = recordState("app.bsky.feed.like", o.str("uri").orEmpty(), o["viewer"].obj.str("like")),
            myRepost = recordState("app.bsky.feed.repost", o.str("uri").orEmpty(), o["viewer"].obj.str("repost")),
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
                t == "app.bsky.richtext.facet#link" -> RichText.Facet(s, end, RichText.Facet.Kind.LINK, feat.obj.str("uri") ?: return@mapNotNull null)
                t == "app.bsky.richtext.facet#mention" -> RichText.Facet(s, end, RichText.Facet.Kind.MENTION, feat.obj.str("did") ?: return@mapNotNull null)
                t == "app.bsky.richtext.facet#tag" -> RichText.Facet(s, end, RichText.Facet.Kind.TAG, feat.obj.str("tag") ?: return@mapNotNull null)
                else -> null
            }
        }
    }

    private fun media(embed: JsonObject?): JsonObject? = embed?.get("media").obj ?: embed

    /** Picture posts: "images", or a gallery's "items", which call the small version "thumbnail". */
    private fun images(embed: JsonObject?): List<Image> = ((media(embed)?.get("images") ?: media(embed)?.get("items")) as? JsonArray).orEmpty().mapNotNull { e ->
        val i = e.obj ?: return@mapNotNull null
        val ar = i["aspectRatio"].obj
        val thumb = i.str("thumb") ?: i.str("thumbnail") ?: return@mapNotNull null
        Image(
            thumb,
            i.str("fullsize") ?: thumb,
            i.str("alt").orEmpty(),
            ar?.let { a -> a.int("width").toFloat() / a.int("height").coerceAtLeast(1) },
        )
    }

    private fun card(embed: JsonObject?): LinkCard? {
        val ext = media(embed)?.get("external").obj ?: return null
        return LinkCard(ext.str("uri") ?: return null, ext.str("title").orEmpty(), ext.str("description").orEmpty(), ext.str("thumb"))
    }

    private fun quotedRecord(embed: JsonObject?): JsonObject? =
        embed?.get("record").obj?.let { it["record"].obj ?: it }

    private fun quoteAvailability(embed: JsonObject?): QuoteAvailability? = when (quotedRecord(embed).str("\$type")) {
        "app.bsky.embed.record#viewRecord" -> QuoteAvailability.AVAILABLE
        "app.bsky.embed.record#viewNotFound" -> QuoteAvailability.DELETED
        "app.bsky.embed.record#viewBlocked" -> QuoteAvailability.BLOCKED
        "app.bsky.embed.record#viewDetached" -> QuoteAvailability.DETACHED
        else -> null
    }

    private fun quote(embed: JsonObject?): Post? {
        val rec = quotedRecord(embed) ?: return null
        if (rec.str("\$type") != "app.bsky.embed.record#viewRecord") return null
        val author = rec["author"].obj?.let(::parseAuthor) ?: return null
        val value = rec["value"].obj ?: return null
        val quotedEmbed = (rec["embeds"] as? JsonArray)?.firstOrNull()?.obj
        val unsupported = unsupported(value, quotedEmbed).toMutableList()
        if ((rec["embeds"] as? JsonArray)?.size?.let { it > 1 } == true) unsupported += "Missing additional quote embeds"
        if (value["embed"] != null && quotedEmbed == null) unsupported += "Missing quoted embed view"
        unsupported.filter { !it.startsWith("Missing ") }.forEach(unknown)
        return Post(
            unsupportedTypes = unsupported,
            uri = rec.str("uri") ?: return null, cid = rec.str("cid") ?: return null, author = author,
            text = value.str("text").orEmpty(), facets = parseFacets(value["facets"]),
            createdAt = value.str("createdAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            replyCount = 0, repostCount = 0, likeCount = 0,
            images = images((rec["embeds"] as? JsonArray)?.firstOrNull()?.obj),
            media = MediaParsing.bluesky((rec["embeds"] as? JsonArray)?.firstOrNull()?.obj),
            card = card((rec["embeds"] as? JsonArray)?.firstOrNull()?.obj), quote = null,
            labels = labelsOf(rec),
            myLike = null, myRepost = null, replyRoot = null,
        )
    }
}
