package io.github.shoalapps.blobfish

import io.github.shoalapps.blobfish.data.TwitterParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading Twitter's own timeline data (made-up posts, in the shape its website receives). */
class TwitterParseTest {
    private fun user(id: String, sn: String, name: String) =
        """{"__typename":"User","rest_id":"$id","core":{"name":"$name","screen_name":"$sn"},"avatar":{"image_url":"https://pbs.twimg.com/profile_images/1/a_normal.jpg"},"is_blue_verified":true,"relationship_perspectives":{"following":true,"followed_by":false}}"""

    private fun tweet(id: String, text: String, extra: String = "") =
        """{"__typename":"Tweet","rest_id":"$id","core":{"user_results":{"result":${user("10", "sam", "Sam")}}},"legacy":{"full_text":"$text","created_at":"Mon Oct 26 10:00:00 +0000 2026","favorite_count":5,"retweet_count":2,"reply_count":1,"quote_count":0,"favorited":true,"retweeted":false,"id_str":"$id","entities":{"urls":[{"url":"https://t.co/abc","expanded_url":"https://example.com/page"}],"user_mentions":[{"screen_name":"alex"}],"hashtags":[{"text":"cats"}]}$extra}}"""

    private val home = """{"data":{"home":{"home_timeline_urt":{"instructions":[{"type":"TimelineAddEntries","entries":[
        {"entryId":"tweet-1","content":{"itemContent":{"tweet_results":{"result":${tweet("1", "Hi @alex see https://t.co/abc #cats &amp; more")}}}}},
        {"entryId":"promoted-tweet-2","content":{"itemContent":{"promotedMetadata":{},"tweet_results":{"result":${tweet("2", "Buy now")}}}}},
        {"entryId":"tweet-3","content":{"itemContent":{"tweet_results":{"result":{"__typename":"Tweet","rest_id":"3","core":{"user_results":{"result":${user("20", "kim", "Kim")}}},"legacy":{"full_text":"RT","retweeted_status_result":{"result":${tweet("4", "Original")}}}}}}}},
        {"entryId":"cursor-bottom-9","content":{"cursorType":"Bottom","value":"NEXT"}}
    ]}]}}}}"""

    private val page = TwitterParse.timeline(Json.parseToJsonElement(home) as JsonObject)

    @Test fun readsPostsWithoutAds() {
        assertEquals(listOf("x:1", "x:4"), page.items.map { it.post.uri })
        assertEquals("NEXT", page.cursor)
    }

    @Test fun postDetails() {
        val p = page.items[0].post
        assertEquals("Hi @alex see example.com/page #cats & more", p.text)
        assertEquals("x:10", p.author.did)
        assertEquals("sam", p.author.handle)
        assertTrue(p.author.verified)
        assertTrue(p.author.avatar!!.endsWith("a_400x400.jpg"))
        assertEquals(5, p.likeCount); assertEquals("x-like", p.myLike); assertNull(p.myRepost)
        assertNotNull(p.createdAt)
        assertEquals(listOf("MENTION", "LINK", "TAG"), p.facets.map { it.kind.name })
    }

    @Test fun repostsShowTheOriginal() {
        val item = page.items[1]
        assertEquals("Original", item.post.text)
        assertEquals("kim", item.repostedBy?.handle)
    }

    /** Optional: a real page saved locally (never committed), when present. */
    @Test fun realPageIfSaved() {
        val f = java.io.File(System.getenv("X_SAMPLE") ?: return)
        if (!f.exists()) return
        var text = f.readText()
        if (text.startsWith("\"")) text = Json.parseToJsonElement(text).let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        val real = TwitterParse.timeline(Json.parseToJsonElement(text) as JsonObject)
        println("real page: ${real.items.size} posts, cursor ${real.cursor != null}, images ${real.items.sumOf { it.post.images.size }}, quotes ${real.items.count { it.post.quote != null }}, reposts ${real.items.count { it.repostedBy != null }}")
        assertTrue(real.items.size >= 10)
        assertTrue(real.items.all { it.post.author.handle.isNotBlank() })
    }
}
