package io.github.swordfishapps.social

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import io.github.swordfishapps.social.data.Atp
import io.github.swordfishapps.social.data.Moderation
import io.github.swordfishapps.social.data.Store
import io.github.swordfishapps.social.data.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class SocialApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        DataMover.run(this)
        Graph.app = this
        Graph.syncModeration()
        Graph.syncProfile()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(180)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
            .build()
}

/** One instance of each piece for the whole app. */
object Graph {
    lateinit var app: Application
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "SwordfishSocial/0.1 (Android)").build()) }
            .build()
    }
    val store by lazy { Store(app) }
    val atp by lazy { Atp(http, store) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Your profile picture, for the You tab. */
    var myAvatar by mutableStateOf<String?>(null)

    /** Loads your profile picture (on start and after signing in); kept for next time. */
    fun syncProfile() {
        val s = store.session.value
        if (s == null) { myAvatar = null; return }
        myAvatar = myAvatar ?: app.getSharedPreferences("social", android.content.Context.MODE_PRIVATE).getString("avatar:${s.did}", null)
        scope.launch {
            runCatching { atp.profile(s.did) }.onSuccess { p ->
                myAvatar = p.author.avatar
                app.getSharedPreferences("social", android.content.Context.MODE_PRIVATE).edit().putString("avatar:${s.did}", p.author.avatar).apply()
            }
        }
    }

    /** Fetches your account's moderation preferences (on start and after signing in). */
    fun syncModeration() {
        if (store.session.value == null) return
        scope.launch { runCatching { atp.moderationPrefs() }.onSuccess(store::setModeration) }
    }

    /**
     * Changes a moderation preference: applied at once, then saved to your account so every
     * Bluesky app follows it. Returns a message if saving failed (the change is undone).
     */
    suspend fun changeModeration(change: (Moderation.Prefs) -> Moderation.Prefs): String? {
        val before = store.moderation.value
        val after = change(before)
        store.setModeration(after)
        if (store.session.value == null) return null
        return runCatching { atp.saveModerationPrefs(after) }.exceptionOrNull()?.let {
            store.setModeration(before)
            it.message ?: "Couldn't save to your account"
        }
    }

    /** A newer release found on GitHub, its download progress, and any message about it. */
    object Update {
        var available by mutableStateOf<Updater.Release?>(null)
        var progress by mutableStateOf<Float?>(null)
        var message by mutableStateOf<String?>(null)
    }

    fun checkForUpdate(force: Boolean = false) {
        // Swordfish Manager handles updates when it is installed.
        if (!force && io.github.swordfishapps.social.SwordfishManager.installed(app)) return
        if (!Updater.canUpdate(app)) {
            if (force) Update.message = "Development builds don't update themselves"
            return
        }
        val now = System.currentTimeMillis()
        if (!force && (!store.settings.value.autoUpdate || now - store.lastUpdateCheck < 20 * 3600_000L)) return
        scope.launch {
            val latest = runCatching { Updater.latest(http) }.getOrNull()
            if (latest != null) store.lastUpdateCheck = now
            when {
                latest == null -> if (force) Update.message = "Couldn't reach GitHub. Try again later."
                latest.build > Updater.currentBuild(app) -> Update.available = latest
                force -> Update.message = "You're on the latest version"
            }
        }
    }

    fun installUpdate() {
        val release = Update.available ?: return
        Update.available = null
        Update.progress = 0f
        scope.launch {
            runCatching {
                val apk = Updater.download(http, release, app.cacheDir) { Update.progress = it }
                withContext(Dispatchers.Main) { Updater.install(app, apk) }
            }.onFailure { Update.message = "Couldn't download the update. Try again later." }
            Update.progress = null
        }
    }
}
