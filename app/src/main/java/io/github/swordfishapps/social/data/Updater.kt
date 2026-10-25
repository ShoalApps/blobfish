package io.github.swordfishapps.social.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Keeps the app up to date from GitHub Releases. Every push to main publishes a build tagged
 * `build-<number>`, whose number is also the app's version code, so comparing them is enough.
 */
object Updater {
    private const val REPO = "https://github.com/SwordfishApps/social"

    data class Release(val build: Int, val apkUrl: String, val pageUrl: String)

    /** The build number in a release tag like "build-23", or null for anything else. */
    fun buildOf(tagOrUrl: String): Int? =
        Regex("""build-(\d+)""").find(tagOrUrl)?.groupValues?.get(1)?.toIntOrNull()

    fun currentBuild(context: Context): Int = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    }.getOrDefault(0)

    /** Development builds have their own package and are never offered release updates. */
    fun canUpdate(context: Context) = !context.packageName.endsWith(".debug")

    /**
     * Finds the latest release from the redirect github.com/…/releases/latest sends,
     * which isn't subject to the API's rate limits.
     */
    suspend fun latest(http: OkHttpClient): Release? = withContext(Dispatchers.IO) {
        val client = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val req = Request.Builder().url("$REPO/releases/latest").head().build()
        client.newCall(req).execute().use { resp ->
            val page = resp.header("Location") ?: return@withContext null
            val tag = page.substringAfterLast("/tag/", "").ifEmpty { return@withContext null }
            val build = buildOf(tag) ?: return@withContext null
            Release(build, "$REPO/releases/download/$tag/SwordfishSocial.apk", page)
        }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(http: OkHttpClient, release: Release, dir: File, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val out = File(dir, "update-${release.build}.apk")
            http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
                check(resp.isSuccessful) { "Download failed (${resp.code})" }
                val body = resp.body ?: error("Empty download")
                val total = body.contentLength().takeIf { it > 0 }
                out.outputStream().use { sink ->
                    body.byteStream().use { src ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            done += n
                            if (total != null) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            out
        }

    /**
     * Hands the APK to Android's installer. Android asks the user to confirm, and only accepts it
     * if it is signed with the same key as the installed app.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { sink -> apk.inputStream().use { it.copyTo(sink) } }
            val callback = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Shows Android's install confirmation when the installer asks for it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
