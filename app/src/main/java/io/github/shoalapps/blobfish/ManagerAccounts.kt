package io.github.shoalapps.blobfish

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONObject

/**
 * Accounts you signed into in Shoal. The Manager shares them through a provider only
 * apps signed with the same key can read; this app picks them up on launch and on return.
 */
object ManagerAccounts {
    data class Snapshot(
        val version: Long,
        val cookies: Map<String, String>,
        val youtubePageId: String?,
        val blueskyHandle: String?,
        val blueskyAppPassword: String?,
    )

    private fun authority(context: Context) =
        "io.github.shoalapps.shoal" + (if (context.packageName.endsWith(".debug")) ".debug" else "") + ".accounts"

    /** The Manager's accounts, or null if it isn't installed (or is too old to share them). */
    fun read(context: Context): Snapshot? = runCatching {
        context.contentResolver.query(Uri.parse("content://${authority(context)}/all"), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val o = JSONObject(c.getString(0))
            val cookies = o.optJSONObject("cookies")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } }.orEmpty()
            Snapshot(o.optLong("version"), cookies, o.optString("youtubePageId").takeIf { it.isNotEmpty() && it != "null" },
                o.optString("blueskyHandle").takeIf { it.isNotEmpty() && it != "null" }, o.optString("blueskyAppPassword").takeIf { it.isNotEmpty() && it != "null" })
        }
    }.getOrNull()

    /** Whether the Manager has changed anything since this app last looked. */
    fun changed(context: Context, s: Snapshot): Boolean {
        val prefs = context.getSharedPreferences("manager_accounts", Context.MODE_PRIVATE)
        if (prefs.getLong("version", -1) == s.version) return false
        prefs.edit().putLong("version", s.version).apply()
        return true
    }

    /** Puts a website sign-in's cookies into this app's WebView cookie jar. */
    fun applyCookies(site: String, cookie: String?) {
        val cm = CookieManager.getInstance()
        if (cookie == null) return
        val domain = "." + Uri.parse(site).host!!.removePrefix("www.")
        for (pair in cookie.split(";")) {
            val p = pair.trim()
            if (p.contains("=")) cm.setCookie(site, "$p; Domain=$domain; Path=/; Secure; Max-Age=31536000")
        }
        cm.flush()
    }
}
