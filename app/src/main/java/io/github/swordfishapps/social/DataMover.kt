package io.github.swordfishapps.social

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONObject

/**
 * Swordfish apps used to be called Tintloom and had different package names, so Android sees them
 * as new apps. On first launch this brings across the old app's settings, history and sign-ins,
 * through a hand-off only apps signed with the same key can read.
 */
object DataMover {
    private const val OLD = "io.github.tintloom.social"

    /** The old app this one replaces (its debug build for debug builds). */
    fun oldPackage(context: Context) = if (context.packageName.endsWith(".debug")) "$OLD.debug" else OLD

    fun oldInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(oldPackage(context), 0) }.isSuccess

    /** Whether data came across this time, so the app can offer to uninstall the old one. */
    var justMoved = false
        private set

    /** Runs before anything reads settings. Safe to call every launch. */
    fun run(context: Context) {
        val marker = context.getSharedPreferences("moved", Context.MODE_PRIVATE)
        if (marker.getBoolean("done", false)) return
        if (!oldInstalled(context)) {
            marker.edit().putBoolean("done", true).apply()
            return
        }
        // Older Tintloom builds can't hand over yet; try again after the old app updates.
        val json = runCatching {
            context.contentResolver.query(Uri.parse("content://${oldPackage(context)}.movedata/all"), null, null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: return
        runCatching { import(context, JSONObject(json)) }.onSuccess {
            marker.edit().putBoolean("done", true).commit()
            justMoved = true
        }
    }

    /** Once the data has moved: offer to remove the old Tintloom app, which no longer updates. */
    fun offerUninstall(activity: android.app.Activity) {
        val marker = activity.getSharedPreferences("moved", Context.MODE_PRIVATE)
        if (!marker.getBoolean("done", false) || marker.getBoolean("offered", false) || !oldInstalled(activity)) return
        marker.edit().putBoolean("offered", true).apply()
        android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Welcome to Swordfish")
            .setMessage(if (justMoved) "Your settings, history and sign-ins came across from the Tintloom version. You can uninstall it now; it won't get updates any more."
                else "The Tintloom version of this app is still installed. It won't get updates any more, so you can uninstall it.")
            .setPositiveButton("Uninstall it") { _, _ ->
                runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, Uri.parse("package:${oldPackage(activity)}"))) }
            }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun import(context: Context, all: JSONObject) {
        val prefs = all.optJSONObject("prefs") ?: JSONObject()
        for (file in prefs.keys()) {
            if (file == "moved" || file == "swordfish_notice" || file.startsWith("WebView")) continue
            val values = prefs.getJSONObject(file)
            val edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            for (key in values.keys()) {
                val v = values.getJSONObject(key)
                when {
                    v.has("s") -> edit.putString(key, v.getString("s"))
                    v.has("b") -> edit.putBoolean(key, v.getBoolean("b"))
                    v.has("i") -> edit.putInt(key, v.getInt("i"))
                    v.has("l") -> edit.putLong(key, v.getLong("l"))
                    v.has("f") -> edit.putFloat(key, v.getDouble("f").toFloat())
                    v.has("set") -> edit.putStringSet(key, (0 until v.getJSONArray("set").length()).map { v.getJSONArray("set").getString(it) }.toSet())
                }
            }
            edit.commit()
        }
        val cookies = all.optJSONObject("cookies") ?: return
        val cm = CookieManager.getInstance()
        for (site in cookies.keys()) {
            val domain = "." + Uri.parse(site).host!!.removePrefix("www.").removePrefix("accounts.")
            for (pair in cookies.getString(site).split(";")) {
                val p = pair.trim()
                if (p.contains("=")) cm.setCookie(site, "$p; Domain=$domain; Path=/; Secure; Max-Age=31536000")
            }
        }
        cm.flush()
    }
}
