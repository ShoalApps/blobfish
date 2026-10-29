package io.github.shoalapps.blobfish.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.AtomicFile
import io.github.shoalapps.blobfish.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class SavedWrite(val id: String = UUID.randomUUID().toString(), val owner: String,
    val endpoint: String, val body: JsonObject, val state: String = "pending")

/** Fixed record keys make retrying a lost response safe, including posts and replies. */
object RecordKeys {
    private var last = 0L
    @Synchronized fun next(): String {
        last = maxOf(last + 1, (System.currentTimeMillis() * 1000 shl 10) or java.security.SecureRandom().nextInt(1024).toLong())
        val alphabet = "234567abcdefghijklmnopqrstuvwxyz"
        var value = last
        return CharArray(13).also { out -> for (i in 12 downTo 0) { out[i] = alphabet[(value and 31).toInt()]; value = value ushr 5 } }.concatToString()
    }
    fun target(body: JsonObject) = listOf("repo", "collection", "rkey").joinToString("|") { body[it].toString() }
    private fun writeKey(write: SavedWrite): String = when {
        write.endpoint.startsWith("com.atproto.repo.") -> target(write.body)
        write.endpoint in setOf("app.bsky.graph.muteActor", "app.bsky.graph.unmuteActor") -> "mute|" + write.body["actor"]
        else -> write.endpoint
    }
    fun add(list: List<SavedWrite>, write: SavedWrite) = list.filterNot {
        it.owner == write.owner && it.state != "sending" && writeKey(it) == writeKey(write)
    } + write
}

class OfflineStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = AtomicFile(File(context.filesDir, "outbox.json"))
    private val cache = File(context.filesDir, "offline-views").apply { mkdirs() }
    private val lock = Mutex()
    val online = MutableStateFlow(false)
    val writes = MutableStateFlow(runCatching {
        json.decodeFromString<List<SavedWrite>>(file.openRead().bufferedReader().use { it.readText() })
            .map { if (it.state == "sending") it.copy(state = "pending") else it }
    }.getOrDefault(emptyList()))
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    fun owner() = Graph.store.session.value?.let { it.did + "|" + it.pds }
    fun available() = !Graph.store.settings.value.offlineMode &&
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    fun allowed() = available() && !(cm?.isActiveNetworkMetered != false &&
        cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
    fun changed() {
        val reconnected = !online.value && available()
        online.value = available()
        if (reconnected) retry()
        if (allowed()) Graph.scope.launch { flush() }
    }
    fun init() {
        retry()
        schedule()
        changed()
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed()
            override fun onLost(network: Network) = changed()
        })
        androidx.core.content.ContextCompat.registerReceiver(Graph.app, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) = changed()
        }, android.content.IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    @Synchronized private fun save(list: List<SavedWrite>) {
        val out = file.startWrite()
        try { out.write(json.encodeToString(list).toByteArray()); file.finishWrite(out); writes.value = list }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    @Synchronized private fun state(id: String, value: String?) {
        save(writes.value.mapNotNull { if (it.id != id) it else value?.let { v -> it.copy(state = v) } })
    }
    @Synchronized fun discard(id: String) {
        if (writes.value.none { it.id == id && it.state == "sending" }) state(id, null)
    }
    suspend fun enqueue(endpoint: String, body: JsonObject) {
        val owner = owner() ?: throw IOException("Sign in before saving an action")
        check(body["repo"] == null || body["repo"]?.jsonPrimitive?.content == Graph.store.session.value?.did)
        synchronized(this) { save(RecordKeys.add(writes.value, SavedWrite(owner = owner, endpoint = endpoint, body = body))) }
        schedule()
        Graph.scope.launch { flush() }
    }
    fun schedule() {
        if (writes.value.none { it.owner == owner() && it.state in setOf("pending", "failed") }) return
        val request = androidx.work.OneTimeWorkRequestBuilder<BlueskySyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
        androidx.work.WorkManager.getInstance(Graph.app).enqueueUniqueWork("bluesky-outbox-sync", androidx.work.ExistingWorkPolicy.KEEP, request)
    }
    suspend fun flush() = lock.withLock {
        while (allowed()) {
            val owner = owner() ?: break
            val next = synchronized(this) { writes.value.firstOrNull { it.owner == owner && it.state == "pending" }?.also { state(it.id, "sending") } } ?: break
            try {
                if (owner() != owner) { state(next.id, "pending"); break }
                Graph.atp.sendSaved(next)
                runCatching { updateViews(next) }
                state(next.id, null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                state(next.id, "pending"); throw e
            } catch (_: Exception) { state(next.id, "failed"); break }
        }
    }
    @Synchronized fun retry() { save(writes.value.map { if (it.state == "failed") it.copy(state = "pending") else it }) }
    /** Restore pending desired state when a saved feed is reopened after a restart. */
    fun recordState(collection: String, subject: String, original: String?): String? {
        var result = original
        writes.value.filter { it.owner == owner() }.forEach { w ->
            if (w.body["collection"]?.jsonPrimitive?.content != collection) return@forEach
            val repo = w.body["repo"]?.jsonPrimitive?.content ?: return@forEach
            val key = w.body["rkey"]?.jsonPrimitive?.content ?: return@forEach
            val uri = "at://"+repo+"/"+collection+"/"+key
            if (w.endpoint.endsWith("deleteRecord") && result == uri) result = null
            val record = w.body["record"] as? JsonObject
            val target = record?.get("subject")?.let { if (it is JsonObject) it["uri"]?.jsonPrimitive?.content else it.jsonPrimitive.content }
            if (w.endpoint.endsWith("putRecord") && target == subject) result = uri
        }
        return result
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun path(key: String, namespace: String) = File(cache, hash(namespace) + "-" + hash(key) + ".json")
    @Synchronized private fun updateViews(write: SavedWrite) {
        val collection = write.body["collection"]?.jsonPrimitive?.content ?: return
        val field = when (collection) {
            "app.bsky.feed.like" -> "like"
            "app.bsky.feed.repost" -> "repost"
            "app.bsky.graph.follow" -> "following"
            else -> return
        }
        val repo = write.body["repo"]?.jsonPrimitive?.content ?: return
        val key = write.body["rkey"]?.jsonPrimitive?.content ?: return
        val uri = "at://"+repo+"/"+collection+"/"+key
        val record = write.body["record"] as? JsonObject
        val subject = record?.get("subject")
        val target = if (subject is JsonObject) subject["uri"]?.jsonPrimitive?.content else (subject as? JsonPrimitive)?.content
        fun patch(element: JsonElement): JsonElement = when (element) {
            is JsonArray -> JsonArray(element.map(::patch))
            is JsonObject -> {
                val result = element.mapValues { patch(it.value) }.toMutableMap()
                val viewer = (result["viewer"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                if (write.endpoint.endsWith("deleteRecord") && viewer[field]?.jsonPrimitive?.content == uri) viewer.remove(field)
                if (write.endpoint.endsWith("putRecord") && target != null &&
                    (element["uri"]?.let { (it as? JsonPrimitive)?.content } == target ||
                     element["did"]?.let { (it as? JsonPrimitive)?.content } == target)) viewer[field] = JsonPrimitive(uri)
                if (element["viewer"] != null || viewer.isNotEmpty()) result["viewer"] = JsonObject(viewer)
                JsonObject(result)
            }
            else -> element
        }
        val prefix = hash(write.owner + "|" + Graph.store.settings.value.appView) + "-"
        cache.listFiles().orEmpty().filter { it.name.startsWith(prefix) && it.extension == "json" }.forEach { saved ->
            val updated = runCatching { patch(json.parseToJsonElement(saved.readText())).toString() }.getOrNull() ?: return@forEach
            val atomic = AtomicFile(saved)
            val out = atomic.startWrite()
            try { out.write(updated.toByteArray()); atomic.finishWrite(out) }
            catch (e: Exception) { atomic.failWrite(out) }
        }
    }
    @Synchronized fun cached(key: String, namespace: String): JsonObject = runCatching {
        json.parseToJsonElement(path(key, namespace).readText()).jsonObject
    }.getOrElse { throw IOException("No saved copy of this view yet. Open it while online first.") }
    @Synchronized fun remember(key: String, value: JsonObject, namespace: String) {
        val data = value.toString()
        if (data.length > 2_000_000) return
        val atomic = AtomicFile(path(key, namespace))
        val out = atomic.startWrite()
        try { out.write(data.toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); return }
        val files = cache.listFiles().orEmpty().sortedByDescending { it.lastModified() }
        var size = 0L
        files.forEachIndexed { i, f -> size += f.length(); if (i >= 100 || size > 20_000_000) f.delete() }
    }
}


class BlueskySyncWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Graph.offline.allowed()) return Result.retry()
        Graph.offline.retry()
        Graph.offline.flush()
        return if (Graph.offline.writes.value.any { it.owner == Graph.offline.owner() && it.state in setOf("pending", "failed") })
            Result.retry() else Result.success()
    }
}
