package io.github.shoalapps.blobfish.data

import io.github.shoalapps.blobfish.data.Moderation.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModerationTest {
    private val alice = Author("did:alice", "alice.bsky.social", "Alice", null)
    private fun post(text: String = "hello", labels: List<String> = emptyList(), author: Author = alice, images: Boolean = false, tags: List<String> = emptyList()) = Post(
        uri = "at://did:alice/app.bsky.feed.post/1", cid = "c", author = author, text = text, facets = emptyList(), createdAt = null,
        replyCount = 0, repostCount = 0, likeCount = 0,
        images = if (images) listOf(Image("t", "f", "", 1f)) else emptyList(), card = null, quote = null,
        myLike = null, myRepost = null, replyRoot = null, labels = labels, tags = tags,
    )
    private val defaults = Moderation.Prefs()

    @Test fun adultContentIsHiddenUntilTurnedOn() {
        assertTrue(Moderation.decide(post(labels = listOf("porn")), defaults, true) is Decision.Hide)
        assertTrue(Moderation.decide(post(labels = listOf("nudity")), defaults, true) is Decision.Hide)
        val adult = defaults.copy(adultContent = true)
        // Bluesky's defaults once adult content is on: porn hidden, sexual warned, nudity shown.
        assertTrue(Moderation.decide(post(labels = listOf("porn")), adult, true) is Decision.Hide)
        assertEquals(Decision.BlurMedia("Sexually suggestive"), Moderation.decide(post(labels = listOf("sexual"), images = true), adult, true))
        assertEquals(Decision.Show, Moderation.decide(post(labels = listOf("nudity")), adult, true))
        // Your own rules win.
        val showAll = adult.copy(rules = mapOf("porn" to "SHOW", "sexual" to "SHOW"))
        assertEquals(Decision.Show, Moderation.decide(post(labels = listOf("porn", "sexual")), showAll, true))
    }

    @Test fun graphicMediaWarnsAndOldNamesCount() {
        assertEquals(Decision.Warn("Graphic media"), Moderation.decide(post(labels = listOf("gore")), defaults, true))
        assertEquals(Decision.BlurMedia("Graphic media"), Moderation.decide(post(labels = listOf("graphic-media"), images = true), defaults, true))
        assertTrue(Moderation.decide(post(labels = listOf("graphic-media")), defaults.copy(rules = mapOf("graphic-media" to "HIDE")), true) is Decision.Hide)
    }

    @Test fun moderatorAndAccountRules() {
        assertTrue(Moderation.decide(post(labels = listOf("!hide")), defaults, true) is Decision.Hide)
        assertEquals(Decision.Warn("Content warning"), Moderation.decide(post(labels = listOf("!warn")), defaults, true))
        val private = alice.copy(labels = listOf("!no-unauthenticated"))
        assertTrue(Moderation.decide(post(author = private), defaults, signedIn = false) is Decision.Hide)
        assertEquals(Decision.Show, Moderation.decide(post(author = private), defaults, signedIn = true))
        assertTrue(Moderation.decide(post(author = alice.copy(blocking = true)), defaults, true) is Decision.Hide)
        assertTrue(Moderation.decide(post(author = alice.copy(muted = true)), defaults, true) is Decision.Hide)
        assertTrue(Moderation.decide(post(), defaults.copy(hiddenPosts = setOf("at://did:alice/app.bsky.feed.post/1")), true) is Decision.Hide)
    }

    @Test fun mutedWords() {
        val prefs = defaults.copy(mutedWords = listOf(Moderation.MutedWord("spoiler"), Moderation.MutedWord("rust", targets = listOf("tag"))))
        assertEquals("spoiler", Moderation.mutedWordIn(post("Big SPOILER ahead!"), prefs))
        // Whole words only: "spoilers" isn't "spoiler".
        assertNull(Moderation.mutedWordIn(post("no spoilers here"), prefs))
        // Tag-only words ignore the text but catch the hashtag.
        assertNull(Moderation.mutedWordIn(post("I love rust"), prefs))
        assertEquals("rust", Moderation.mutedWordIn(post("hi", tags = listOf("Rust")), prefs))
        // Languages without spaces match inside words.
        assertEquals("ネタバレ", Moderation.mutedWordIn(post("これはネタバレです"), defaults.copy(mutedWords = listOf(Moderation.MutedWord("ネタバレ")))))
        // Expired words and people you follow (when excluded) don't count.
        val now = Instant.parse("2026-10-25T12:00:00Z")
        assertNull(Moderation.mutedWordIn(post("spoiler"), defaults.copy(mutedWords = listOf(Moderation.MutedWord("spoiler", expiresAt = "2026-10-24T00:00:00Z"))), now))
        val followed = alice.copy(following = true)
        assertNull(Moderation.mutedWordIn(post("spoiler", author = followed), defaults.copy(mutedWords = listOf(Moderation.MutedWord("spoiler", actorTarget = "exclude-following")))))
    }

    @Test fun feedOptions() {
        val bob = Author("did:bob", "bob", null, null)
        val reply = FeedItem(post(), repostedBy = null, replyTo = bob)
        // By default, replies to people you don't follow are hidden; to people you follow, shown.
        assertTrue(Moderation.hiddenInFeed(reply, defaults))
        assertFalse(Moderation.hiddenInFeed(FeedItem(post(), null, bob.copy(following = true)), defaults))
        assertTrue(Moderation.hiddenInFeed(FeedItem(post(), bob, null), defaults.copy(hideReposts = true)))
        assertFalse(Moderation.hiddenInFeed(FeedItem(post(), bob, null), defaults))
    }
    @Test fun hiddenLabelsAlwaysOutrankWarnings() {
        val hidden = post(labels = listOf("!warn", "porn"))
        assertTrue(Moderation.decide(hidden, defaults, true) is Decision.Hide)
        assertTrue(Moderation.decide(hidden.copy(author = alice.copy(muted = true)), defaults, true) is Decision.Hide)
    }

    @Test fun videoWarningsCoverThePlayer() {
        val video = post(labels = listOf("graphic-media")).copy(media = listOf(PlayableMedia(listOf(MediaSource("https://example.com/video.m3u8", "application/x-mpegURL")), null, "", 1f)))
        assertEquals(Decision.BlurMedia("Graphic media"), Moderation.decide(video, defaults, true))
    }

}
