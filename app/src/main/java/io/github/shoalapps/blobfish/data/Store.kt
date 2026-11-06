package io.github.shoalapps.blobfish.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    /** 0 = follow the phone, 1 = light, 2 = dark */
    val theme: Int = 0,
    val offlineMode: Boolean = false,
    val dynamicColor: Boolean = true,
    /** Render inline code and fenced code blocks the way mu does. */
    val richFormatting: Boolean = true,
    val showReposts: Boolean = true,
    val showReplies: Boolean = true,
    val autoUpdate: Boolean = true,
    val nsfwOnly: Boolean = false,
    val breakReminderMinutes: Int = 0,
    /** Your X account in the account switcher (x.com, signed in on X's own login). */
    val showX: Boolean = true,
    /** Which account Butterflyfish is showing: "bluesky" or "x". */
    val service: String = "bluesky",
    /** Call X "Twitter", with the bird, everywhere in Butterflyfish. On unless you turn it off. */
    val twitterNames: Boolean = true,
    /** Your hosting provider (PDS or entryway) for signing in without a handle. Bluesky's by default. */
    val server: String = DEFAULT_SERVER,
    /** The AppView that builds feeds, profiles and threads; blank for Bluesky's. */
    val appView: String = "",
) {
    companion object {
        const val DEFAULT_SERVER = "https://bsky.social"
        const val BLUESKY_APPVIEW = "https://public.api.bsky.app"
    }
}

@Serializable
private data class SavedSession(val did: String, val handle: String, val access: String, val refresh: String, val pds: String, val oauth: Boolean = false, val tokenUrl: String? = null)

/** Settings and your Bluesky session, kept privately on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _session = MutableStateFlow(
        prefs.getString("session", null)?.let { runCatching { json.decodeFromString<SavedSession>(it) }.getOrNull() }
            ?.let { Session(it.did, it.handle, it.access, it.refresh, it.pds, it.oauth, it.tokenUrl) },
    )
    val session: StateFlow<Session?> = _session

    private val _hiddenFollows = MutableStateFlow(readHiddenFollows(_session.value))
    val hiddenFollows: StateFlow<Map<String, String>> = _hiddenFollows

    private fun hiddenFollowKey(session: Session?) =
        "hidden-follows|" + (session?.let { it.did + "|" + it.pds } ?: "public")

    private fun readHiddenFollows(session: Session?): Map<String, String> =
        prefs.getString(hiddenFollowKey(session), null)?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        }.orEmpty()

    @Synchronized
    fun toggleHiddenFollow(did: String, handle: String) {
        val saved = _hiddenFollows.value
        val updated = if (did in saved) saved - did else saved + (did to handle)
        prefs.edit().putString(hiddenFollowKey(_session.value), json.encodeToString(updated)).apply()
        _hiddenFollows.value = updated
    }

    private val _deletedPosts = MutableStateFlow(prefs.getStringSet("deleted-posts", emptySet()).orEmpty().toSet())
    val deletedPosts: StateFlow<Set<String>> = _deletedPosts

    @Synchronized
    fun rememberDeletedPost(uri: String) {
        val updated = (_deletedPosts.value - uri + uri).toList().takeLast(500).toSet()
        prefs.edit().putStringSet("deleted-posts", updated).apply()
        _deletedPosts.value = updated
    }

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        io.github.shoalapps.blobfish.Graph.offline.changed()
        prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), _settings.value)).apply()
    }

    private val _feedGeneration = MutableStateFlow(0)
    val feedGeneration: StateFlow<Int> = _feedGeneration

    /** Invalidates retained view models and pending writes when an account changes. */
    @Synchronized fun setSession(s: Session?) {
        val previous = _session.value
        if (previous?.let { it.did + "|" + it.pds } != s?.let { it.did + "|" + it.pds }) {
            _feedGeneration.value += 1
            if (s == null && previous != null) {
                val owner = "bluesky|" + previous.did + "|" + previous.pds
                clearFeedAnchors(owner, waitForDisk = false)
            }
        }
        if (hiddenFollowKey(previous) != hiddenFollowKey(s)) _hiddenFollows.value = readHiddenFollows(s)
        _session.value = s
        io.github.shoalapps.blobfish.Graph.offline.changed()
        if (s == null) { prefs.edit().remove("session").apply(); setModeration(Moderation.Prefs()) }
        else prefs.edit().putString("session", json.encodeToString(SavedSession.serializer(), SavedSession(s.did, s.handle, s.access, s.refresh, s.pds, s.oauth, s.tokenUrl))).apply()
    }

    /** Moderation preferences: a copy of your account's (or the defaults when signed out). */
    private val _moderation = MutableStateFlow(
        prefs.getString("moderation", null)?.let { runCatching { json.decodeFromString<Moderation.Prefs>(it) }.getOrNull() } ?: Moderation.Prefs(),
    )
    val moderation: StateFlow<Moderation.Prefs> = _moderation

    init {
        // The old "Show reposts" / "Show replies" switches now live in moderation's feed options.
        val old = _settings.value
        if (!prefs.getBoolean("migratedFeedOptions", false)) {
            if (!old.showReposts || !old.showReplies) {
                setModeration(_moderation.value.copy(hideReposts = !old.showReposts, hideReplies = !old.showReplies))
            }
            prefs.edit().putBoolean("migratedFeedOptions", true).apply()
        }
    }

    fun setModeration(p: Moderation.Prefs) {
        _moderation.value = p
        prefs.edit().putString("moderation", json.encodeToString(Moderation.Prefs.serializer(), p)).apply()
    }

    // Called on Dispatchers.IO. Commit completes before the background save finishes.
    @Synchronized fun feedAnchor(owner: String, feed: String): FeedAnchor? =
        FeedReading.decode(prefs.getString("feedAnchors", null)).firstOrNull { it.owner == owner && it.feed == feed }

    @Synchronized fun saveFeedAnchor(anchor: FeedAnchor, valid: () -> Boolean = { true }) {
        if (!valid()) return
        val anchors = FeedReading.save(FeedReading.decode(prefs.getString("feedAnchors", null)), anchor)
        prefs.edit().putString("feedAnchors", FeedReading.encode(anchors)).commit()
    }

    @Synchronized fun clearFeedAnchors(owner: String, waitForDisk: Boolean = true) {
        val anchors = FeedReading.decode(prefs.getString("feedAnchors", null)).filterNot { it.owner == owner }
        val edit = prefs.edit().putString("feedAnchors", FeedReading.encode(anchors))
        if (waitForDisk) edit.commit() else edit.apply()
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()
}
