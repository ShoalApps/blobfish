package io.github.tintloom.social.data

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
)

@Serializable
private data class SavedSession(val did: String, val handle: String, val access: String, val refresh: String, val pds: String)

/** Settings and your Bluesky session, kept privately on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _session = MutableStateFlow(
        prefs.getString("session", null)?.let { runCatching { json.decodeFromString<SavedSession>(it) }.getOrNull() }
            ?.let { Session(it.did, it.handle, it.access, it.refresh, it.pds) },
    )
    val session: StateFlow<Session?> = _session

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), _settings.value)).apply()
    }

    fun setSession(s: Session?) {
        _session.value = s
        if (s == null) prefs.edit().remove("session").apply()
        else prefs.edit().putString("session", json.encodeToString(SavedSession.serializer(), SavedSession(s.did, s.handle, s.access, s.refresh, s.pds))).apply()
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()
}
