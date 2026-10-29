package io.github.shoalapps.blobfish

import io.github.shoalapps.blobfish.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MediaParsingTest {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject
    // Shape of the reported textless Bluesky video, including its actual 4:3 aspect ratio.
    private val video = obj("""{"${'$'}type":"app.bsky.embed.video#view","playlist":"https://video.bsky.app/watch/example/playlist.m3u8","thumbnail":"https://video.bsky.app/watch/example/thumbnail.jpg","aspectRatio":{"width":960,"height":720}}""")
    @Test fun textlessBlueskyVideoKeepsItsPlaylist() {
        val m = MediaParsing.bluesky(video).single()
        assertTrue(m.sources.single().url.endsWith("playlist.m3u8"))
        assertEquals(4f / 3, m.aspect!!, .001f)
        assertFalse(m.audio)
    }
    @Test fun recordWithMediaKeepsVideoAndGifHint() {
        val wrapped = buildJsonObject { put("media", JsonObject(video + ("presentation" to JsonPrimitive("gif")))) }
        assertTrue(MediaParsing.bluesky(wrapped).single().loop)
    }
    @Test fun twitterVariantsArePlayableRatherThanPosterImages() {
        val item = obj("""{"type":"video","media_url_https":"https://pbs.twimg.com/poster.jpg","video_info":{"aspect_ratio":[16,9],"variants":[{"content_type":"video/mp4","url":"https://video.twimg.com/low.mp4","bitrate":256000},{"content_type":"application/x-mpegURL","url":"https://video.twimg.com/video.m3u8"},{"content_type":"video/mp4","url":"https://video.twimg.com/high.mp4","bitrate":4000000}]}}""")
        val m = MediaParsing.twitter(listOf(item)).single()
        assertEquals(3, m.sources.size)
        assertTrue(m.source(true, true)!!.url.endsWith("m3u8"))
        assertEquals(16f / 9, m.aspect!!, .001f)
    }
    @Test fun audioAndAnimatedGifVariantsAreKept() {
        val audio = obj("""{"type":"audio","audio_info":{"variants":[{"content_type":"audio/mp4","url":"https://video.twimg.com/audio.m4a"}]}}""")
        val gif = obj("""{"type":"animated_gif","video_info":{"variants":[{"content_type":"video/mp4","url":"https://video.twimg.com/gif.mp4"}]}}""")
        val all = MediaParsing.twitter(listOf(audio, gif))
        assertTrue(all[0].audio)
        assertTrue(all[1].loop)
    }
    @Test fun meteredProgressiveStreamsUseSmallerVariants() {
        val media = PlayableMedia(listOf(MediaSource("low", "video/mp4", 256000), MediaSource("mid", "video/mp4", 2000000), MediaSource("high", "video/mp4", 4000000)), null, "", null)
        assertEquals("low", media.source(true, true)!!.url)
        assertEquals("mid", media.source(true, false)!!.url)
        assertEquals("high", media.source(false, false)!!.url)
    }
    @Test fun photosAndInvalidStreamsAreNotPlayableMedia() {
        assertTrue(MediaParsing.twitter(listOf(obj("""{"type":"photo"}"""))).isEmpty())
        assertTrue(MediaParsing.bluesky(obj("""{"playlist":"javascript:bad"}""")).isEmpty())
    }
}
