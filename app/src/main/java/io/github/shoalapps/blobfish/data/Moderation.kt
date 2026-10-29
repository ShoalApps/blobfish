package io.github.shoalapps.blobfish.data

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Bluesky's moderation, applied the way the official app does: your account's preferences (adult
 * content, what to do with each label, muted words, hidden posts, feed options) decide whether each
 * post is shown, shown behind a warning, or hidden. Signed out, Bluesky's defaults apply.
 */
object Moderation {
    /** Bluesky's moderation service; its labels are only returned when a request asks for them. */
    const val BLUESKY_LABELER = "did:plc:ar7c4by46qjdydhdevvrndac"

    /** The labels people choose a rule for, in Bluesky's order, with what Settings calls them. */
    val adultLabels = listOf("porn" to "Adult content", "sexual" to "Sexually suggestive", "nudity" to "Non-sexual nudity")
    val otherLabels = listOf("graphic-media" to "Graphic media")

    enum class Rule { SHOW, WARN, HIDE }

    /** Bluesky's defaults (the same whether or not adult content is turned on). */
    val defaultRules = mapOf("porn" to Rule.HIDE, "sexual" to Rule.WARN, "nudity" to Rule.SHOW, "graphic-media" to Rule.WARN)

    @Serializable
    data class MutedWord(
        val value: String,
        /** "content" (text and tags) or "tag" (tags only) */
        val targets: List<String> = listOf("content", "tag"),
        /** "all", or "exclude-following" to still see people you follow */
        val actorTarget: String = "all",
        val expiresAt: String? = null,
        val id: String? = null,
    ) {
        fun expired(now: Instant) = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isBefore(now) == true
    }

    @Serializable
    data class Prefs(
        val adultContent: Boolean = false,
        /** label value to Rule name */
        val rules: Map<String, String> = emptyMap(),
        val mutedWords: List<MutedWord> = emptyList(),
        val hiddenPosts: Set<String> = emptySet(),
        val hideReplies: Boolean = false,
        val hideRepliesByUnfollowed: Boolean = true,
        val hideReposts: Boolean = false,
        val hideQuotePosts: Boolean = false,
        /** Labelers you subscribe to, besides Bluesky's own. */
        val labelers: List<String> = emptyList(),
    ) {
        fun rule(label: String): Rule {
            val canonical = canonical(label)
            if (!adultContent && adultLabels.any { it.first == canonical }) return Rule.HIDE
            return rules[canonical]?.let { runCatching { Rule.valueOf(it) }.getOrNull() } ?: defaultRules[canonical] ?: Rule.SHOW
        }
    }

    /** Older label names Bluesky still recognises. */
    fun canonical(label: String) = when (label) {
        "gore", "nsfl", "corpse" -> "graphic-media"
        "nsfw" -> "porn"
        else -> label
    }

    sealed interface Decision {
        data object Show : Decision
        /** Show the post, but cover its pictures until tapped. */
        data class BlurMedia(val reason: String) : Decision
        /** Cover the whole post until tapped. */
        data class Warn(val reason: String) : Decision
        data class Hide(val reason: String) : Decision
    }

    /** What to do with a post. [following] is whether you follow its author. */
    fun decide(post: Post, prefs: Prefs, signedIn: Boolean, now: Instant = Instant.now()): Decision {
        if (post.uri in prefs.hiddenPosts) return Decision.Hide("Post hidden by you")
        if (post.author.blocking || post.author.blockedBy) return Decision.Hide("Blocked")
        val labels = post.labels + post.author.labels
        if ("!hide" in labels) return Decision.Hide("Hidden by moderators")
        if ("!no-unauthenticated" in labels && !signedIn) return Decision.Hide("This account asked to be shown only to signed-in people")
        mutedWordIn(post, prefs, now)?.let { return Decision.Hide("Muted word: $it") }
        if (post.author.muted) return Decision.Hide("Post by an account you muted")

        // Strictest rule among the post's own labels (they cover its pictures) and its author's.
        var worst: Decision = if ("!warn" in labels) Decision.Warn("Content warning") else Decision.Show
        for (label in labels) {
            val name = canonical(label)
            val title = (adultLabels + otherLabels).firstOrNull { it.first == name }?.second ?: continue
            when (prefs.rule(name)) {
                Rule.HIDE -> return Decision.Hide(title)
                Rule.WARN -> if (worst == Decision.Show) worst = if (post.hasMedia) Decision.BlurMedia(title) else Decision.Warn(title)
                Rule.SHOW -> Unit
            }
        }
        return worst
    }

    /** Feed-only rules: replies, reposts and quote posts. */
    fun hiddenInFeed(item: FeedItem, prefs: Prefs): Boolean {
        if (item.repostedBy != null && prefs.hideReposts) return true
        if (item.replyTo != null) {
            if (prefs.hideReplies) return true
            if (prefs.hideRepliesByUnfollowed && !item.replyTo.following && !item.post.author.following && item.replyTo.did != item.post.author.did) return true
        }
        if (item.post.quote != null && prefs.hideQuotePosts) return true
        return false
    }

    /** The first muted word the post contains, if any. */
    fun mutedWordIn(post: Post, prefs: Prefs, now: Instant = Instant.now()): String? {
        val text = post.text.lowercase()
        val tags = post.tags.map { it.lowercase().removePrefix("#") }
        for (w in prefs.mutedWords) {
            if (w.expired(now)) continue
            if (w.actorTarget == "exclude-following" && post.author.following) continue
            val word = w.value.lowercase().trim().removePrefix("#")
            if (word.isEmpty()) continue
            if ("tag" in w.targets && word in tags) return w.value
            if ("content" in w.targets && containsWord(text, word)) return w.value
        }
        return null
    }

    /**
     * Whole-word match for space-separated languages; substring match for languages written
     * without spaces (Chinese, Japanese, Thai...), as Bluesky does.
     */
    fun containsWord(text: String, word: String): Boolean {
        if (word.any { Character.UnicodeScript.of(it.code) in noSpaceScripts }) return text.contains(word)
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) return false
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + word.length)
            if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) return true
            from = i + 1
        }
    }

    private val noSpaceScripts = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER,
    )
}
