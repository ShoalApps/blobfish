package io.github.shoalapps.blobfish

import io.github.shoalapps.blobfish.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CompatibilityTest {
    private fun fixture(name: String) = Json.parseToJsonElement(javaClass.getResource("/compat/$name.json")!!.readText()) as JsonObject

    @Test fun unknownEmbedKeepsTextAndCountsOnlyType() {
        val counter = TypeCounter()
        val post = BlueskyParse(unknown = counter::record).parsePost(fixture("unknown-embed"))!!
        assertEquals("Still readable", post.text)
        assertEquals(listOf("app.bsky.embed.future#view"), post.unsupportedTypes)
        assertEquals(mapOf("app.bsky.embed.future#view" to 1), counter.snapshot())
        assertEquals("https://bsky.app/profile/test.example/post/one", webUrl(post))
    }

    @Test fun malformedPostsAreSkippedIndividually() {
        val parser = BlueskyParse()
        assertNull(parser.parsePost(fixture("missing-field")))
        assertNull(parser.parsePost(fixture("wrong-type")))
        assertEquals(2, parser.feedPage(fixture("new-reason")).items.size)
    }

    @Test fun newReasonKeepsPostAndAddsNote() {
        val names = mutableListOf<String>()
        val page = BlueskyParse(unknown = { names += it }).feedPage(fixture("new-reason"))
        assertEquals("Still readable", page.items.first().post.text)
        assertEquals(listOf("app.bsky.feed.defs#reasonFuture"), page.items.first().post.unsupportedTypes)
        assertEquals(listOf("app.bsky.feed.defs#reasonFuture"), names)
    }

    @Test fun unknownFacetKeepsPlainText() {
        val post = BlueskyParse().parsePost(fixture("unknown-facet"))!!
        assertTrue(post.facets.isEmpty())
        assertTrue(post.unsupportedTypes.contains("app.bsky.richtext.facet#future"))
        assertEquals("Still readable", post.text)
    }

    @Test fun replyRestrictionsSurviveSavingADraft() {
        val fixture = fixture("unknown-embed")
        val viewer = JsonObject(mapOf("replyDisabled" to kotlinx.serialization.json.JsonPrimitive(true)))
        val post = BlueskyParse().parsePost(JsonObject(fixture + ("viewer" to viewer)))!!
        val parent = io.github.shoalapps.blobfish.data.DraftParent.from(post)
        val draft = io.github.shoalapps.blobfish.data.DraftReply(post.cid, parent = parent)
        assertTrue(post.replyDisabled)
        assertTrue(draft.toPost()!!.replyDisabled)
    }

    @Test fun malformedOptionalShapesAreTolerated() {
        val base = fixture("unknown-embed").toMutableMap()
        for (key in listOf("embed", "viewer", "labels", "likeCount")) base[key] = JsonArray(emptyList())
        val post = BlueskyParse().parsePost(JsonObject(base))!!
        assertEquals(0, post.likeCount)
        assertEquals("Still readable", post.text)
    }

    @Test fun typeCounterCapsAndRestoresNamesOnly() {
        val saved = mutableListOf<Map<String, Int>>()
        val counter = TypeCounter(changed = { saved += it })
        repeat(101) { counter.record("app.bsky.example#type$it") }
        counter.record("app.bsky.example#type0")
        counter.record("post text with spaces")
        assertEquals(100, counter.snapshot().size)
        assertEquals(2, counter.snapshot()["app.bsky.example#type0"])
        assertEquals(counter.snapshot(), TypeCounter(saved.last()).snapshot())
        val full = TypeCounter(mapOf("TweetFuture" to Int.MAX_VALUE))
        full.record("TweetFuture")
        assertEquals(Int.MAX_VALUE, full.snapshot()["TweetFuture"])
    }

    @Test fun bundledAssetLoaderIsOfflineAndAcceptsExplicitBootstrap() {
        val file = listOf(File("src/main/assets/bluesky-compat.json"), File("app/src/main/assets/bluesky-compat.json")).first { it.exists() }
        val data = BlueskyCompatibility.parse(file.readText())
        assertEquals(data.features.count { it.rendered }, data.shown)
        assertTrue(data.supportedVariants.contains("app.bsky.richtext.facet#link"))
    }

    @Test fun generatedAssetFormat() {
        val data = BlueskyCompatibility.parse("""{"date":"2026-11-05","upstreamCommit":"0123456789012345678901234567890123456789","features":[{"id":"app.bsky.example","description":"Example","rendered":false}],"supportedVariants":[]}""")
        assertEquals(0, data.shown)
        assertEquals("Example", data.features.single().description)
        assertTrue(runCatching { BlueskyCompatibility.parse("""{"upstreamCommit":"invented"}""") }.isFailure)
    }

    @Test fun xUnknownModulesCountNamesAndBadEntriesDoNotLoseFeed() {
        val names = mutableListOf<String>()
        val previous = TwitterParse.unknownType
        try {
            TwitterParse.unknownType = { names += it }
            val root = fixture("x-bad-entry")
            assertEquals(listOf("Good"), TwitterParse.timeline(root).items.map { it.post.text })
            assertEquals(listOf("FutureModule"), names)
        } finally { TwitterParse.unknownType = previous }
    }

    @Test fun xAccountSettingsOnlyUsesViewerIdentity() {
        val me = TwitterParse.accountSettings(Json.parseToJsonElement("""{"screen_name":"sam","name":"Sam","profile_image_url_https":"https://example.test/avatar.jpg"}""") as JsonObject)!!
        assertEquals("sam", me.handle)
        assertEquals("Sam", me.name)
        assertNotNull(me.avatar)
        assertNull(TwitterParse.accountSettings(JsonObject(emptyMap())))
    }

    @Test fun xViewerIsReadFromThePageAndOnlyTrustsItsOwnImageHost() {
        fun page(json: String) = kotlinx.serialization.json.JsonPrimitive(json).toString()
        val me = io.github.shoalapps.blobfish.ui.XIdentity.fromJson(page("""{"h":"someone","n":"Some One","a":"https://pbs.twimg.com/profile_images/1/a_normal.jpg"}"""))!!
        org.junit.Assert.assertEquals("someone", me.handle)
        org.junit.Assert.assertEquals("https://pbs.twimg.com/profile_images/1/a_400x400.jpg", me.avatar)
        org.junit.Assert.assertNull(io.github.shoalapps.blobfish.ui.XIdentity.fromJson(page("""{"h":"someone","a":"https://elsewhere.example/a.jpg"}"""))!!.avatar)
        org.junit.Assert.assertNull(io.github.shoalapps.blobfish.ui.XIdentity.fromJson("null"))
        org.junit.Assert.assertNull(io.github.shoalapps.blobfish.ui.XIdentity.fromJson(null))
        org.junit.Assert.assertNull(io.github.shoalapps.blobfish.ui.XIdentity.fromJson(page("not json")))
    }

    /** Shaped like Bluesky's real app.bsky.embed.gallery#view lexicon. */
    @Test fun galleriesShowAsPicturesWithNoCantShowNote() {
        val json = kotlinx.serialization.json.Json
        val post = json.parseToJsonElement("""{"uri":"at://a/b/c","cid":"c","author":{"did":"did:a","handle":"a.test"},
            "record":{"${'$'}type":"app.bsky.feed.post","text":"hi"},
            "embed":{"${'$'}type":"app.bsky.embed.gallery#view","items":[
              {"${'$'}type":"app.bsky.embed.gallery#viewImage","thumbnail":"https://x/t.jpg","fullsize":"https://x/f.jpg","alt":"one","aspectRatio":{"width":2,"height":1}}]}}""") as kotlinx.serialization.json.JsonObject
        val parsed = io.github.shoalapps.blobfish.data.BlueskyParse({ _, _, v -> v }, {}).parsePost(post)!!
        org.junit.Assert.assertEquals(listOf("https://x/f.jpg"), parsed.images.map { it.full })
        org.junit.Assert.assertEquals(2f, parsed.images.single().aspect!!, 0f)
        org.junit.Assert.assertTrue(parsed.unsupportedTypes.isEmpty())
    }

    @Test fun aBlockedQuoteIsNotCalledAMissingFeature() {
        val json = kotlinx.serialization.json.Json
        val post = json.parseToJsonElement("""{"uri":"at://a/b/c","cid":"c","author":{"did":"did:a","handle":"a.test"},
            "record":{"${'$'}type":"app.bsky.feed.post","text":"hi"},
            "embed":{"${'$'}type":"app.bsky.embed.record#view","record":{"${'$'}type":"app.bsky.embed.record#viewBlocked","uri":"at://x/y/z"}}}""") as kotlinx.serialization.json.JsonObject
        val parsed = io.github.shoalapps.blobfish.data.BlueskyParse({ _, _, v -> v }, {}).parsePost(post)!!
        org.junit.Assert.assertTrue(parsed.unsupportedTypes.toString(), parsed.unsupportedTypes.isEmpty())
    }
}
