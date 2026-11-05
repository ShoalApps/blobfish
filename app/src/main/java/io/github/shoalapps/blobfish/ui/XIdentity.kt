package io.github.shoalapps.blobfish.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.shoalapps.blobfish.data.Author

/** Viewer identity only, cached privately so account chrome does not wait for a website request. */
object XIdentity {
    private lateinit var prefs: android.content.SharedPreferences
    var author by mutableStateOf<Author?>(null); private set
    fun init(context: Context) {
        prefs = context.getSharedPreferences("x-identity", Context.MODE_PRIVATE)
        prefs.getString("handle", null)?.let { h ->
            author = Author("x:@$h", h, prefs.getString("name", null), prefs.getString("avatar", null))
        }
    }
    @Synchronized fun remember(value: Author) {
        val old = author?.takeIf { it.handle.equals(value.handle, ignoreCase = true) }
        author = value.copy(displayName = value.displayName ?: old?.displayName, avatar = value.avatar ?: old?.avatar)
        prefs.edit().putString("handle", value.handle).putString("name", author?.displayName).putString("avatar", author?.avatar).apply()
    }
    /**
     * Who the website says is signed in, read from the state it boots with. A read only: if X
     * changes this, the result is simply null and the account shows what's already known.
     */
    const val READ_VIEWER = """(function(){try{var s=window.__INITIAL_STATE__;var u=s.entities.users.entities[s.session.user_id];return JSON.stringify({h:u.screen_name,n:u.name,a:u.profile_image_url_https})}catch(e){return null}})()"""

    fun fromPage(result: String?) {
        fromJson(result)?.let { android.os.Handler(android.os.Looper.getMainLooper()).post { if (XView.signedIn) remember(it) } }
    }

    /** evaluateJavascript hands back a JSON string holding JSON, or the word null. */
    internal fun fromJson(result: String?): Author? = runCatching {
        val json = kotlinx.serialization.json.Json
        val inner = (json.parseToJsonElement(result ?: return null) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val o = json.parseToJsonElement(inner) as kotlinx.serialization.json.JsonObject
        fun text(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val handle = text("h") ?: return null
        // A bigger picture than the website's small default, and only ever X's own image host.
        val avatar = text("a")?.takeIf { it.startsWith("https://pbs.twimg.com/") }?.replace("_normal.", "_400x400.")
        Author("x:@$handle", handle, text("n"), avatar)
    }.getOrNull()

    @Synchronized fun clear() { author = null; prefs.edit().clear().apply() }
}

@androidx.compose.runtime.Composable
fun XAccountIdentity() {
    val me = XIdentity.author
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        AccountAvatar(me?.avatar, bluesky = false, size = androidx.compose.ui.unit.Dp(40f))
        androidx.compose.foundation.layout.Column {
            io.github.shoalapps.blobfish.LocalisedText(me?.name ?: "X account", localise = false)
            io.github.shoalapps.blobfish.LocalisedText(me?.let { "@${it.handle}" } ?: "Account details not available yet", localise = false)
        }
    }
}
