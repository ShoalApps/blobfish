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
    val dynamicColor: Boolean = true,
    /** Render markdown-style formatting (code blocks, bold, italic) the way mu does. */
    val richFormatting: Boolean = true,
    val showReposts: Boolean = true,
    val showReplies: Boolean = true,
    val autoUpdate: Boolean = true,
    /** An X tab showing x.com, signed in on X's own login. */
    val showX: Boolean = true,
)

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

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), _settings.value)).apply()
    }

    fun setSession(s: Session?) {
        _session.value = s
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

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()
}
