package io.github.tintloom.social

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Hands this app's settings, history and sign-ins to its Blobfish replacement, which has a new
 * package name and so can't read them itself. Only apps signed with the same release key may ask
 * (a signature permission), so nothing else on the phone can read this.
 */
class LegacyExport : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val ctx = context!!
        val prefs = JSONObject()
        val dir = File(ctx.applicationInfo.dataDir, "shared_prefs")
        for (f in dir.listFiles().orEmpty()) {
            if (!f.name.endsWith(".xml")) continue
            val name = f.name.removeSuffix(".xml")
            val values = JSONObject()
            for ((k, v) in ctx.getSharedPreferences(name, 0).all) {
                values.put(k, when (v) {
                    is String -> JSONObject().put("s", v)
                    is Boolean -> JSONObject().put("b", v)
                    is Int -> JSONObject().put("i", v)
                    is Long -> JSONObject().put("l", v)
                    is Float -> JSONObject().put("f", v.toDouble())
                    is Set<*> -> JSONObject().put("set", JSONArray(v.map { it.toString() }))
                    else -> continue
                })
            }
            prefs.put(name, values)
        }
        // Website sign-ins (YouTube, TikTok, Instagram) live in the WebView's cookies.
        val cookies = JSONObject()
        runCatching {
            val cm = CookieManager.getInstance()
            for (site in listOf("https://www.youtube.com", "https://accounts.google.com", "https://www.tiktok.com", "https://www.instagram.com")) {
                cm.getCookie(site)?.let { cookies.put(site, it) }
            }
        }
        val all = JSONObject().put("prefs", prefs).put("cookies", cookies).toString()
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(all)) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

/** Shown once: the app now lives on as Blobfish, which brings your data across. */
object ShoalNotice {
    fun show(activity: android.app.Activity) {
        val prefs = activity.getSharedPreferences("shoal_notice", 0)
        if (prefs.getBoolean("shown", false) || activity.packageName.endsWith(".debug")) return
        prefs.edit().putBoolean("shown", true).apply()
        android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Tintloom is now Blobfish")
            .setMessage("This app has a new name and lives on as Blobfish. Install it and your settings, history and sign-ins come with you. This version won't get updates any more.")
            .setPositiveButton("Get Blobfish") { _, _ ->
                runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/ShoalApps/blobfish/releases/latest/download/Blobfish.apk"))) }
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
