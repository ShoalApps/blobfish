package io.github.shoalapps.blobfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class DraftPicture(val uri: String, val alt: String = "")

/** Strong references for the parent and the thread root, which may be different posts. */
@Serializable
data class DraftReply(val cid: String, val rootUri: String? = null, val rootCid: String? = null,
    val parent: DraftParent? = null) {
    init {
        require(cid.isNotBlank())
        require((rootUri == null) == (rootCid == null))
        require(rootUri == null || rootUri.isNotBlank())
        require(rootCid == null || rootCid.isNotBlank())
    }

    /** Only display data comes from the snapshot; the saved strong references stay authoritative. */
    fun toPost(): Post? = parent?.let {
        Post(it.uri, cid, Author(it.authorDid, it.authorHandle, it.authorName, null), it.text,
            emptyList(), null, 0, 0, 0, images = emptyList(), card = null, quote = null,
            myLike = null, myRepost = null, replyRoot = rootUri?.let { uri -> Ref(uri, requireNotNull(rootCid)) })
    }
}

/** Small, self-contained copy of what the reply composer needs to reopen offline. */
@Serializable
data class DraftParent(val uri: String, val authorDid: String, val authorHandle: String,
    val authorName: String?, val text: String) {
    init {
        require(uri.isNotBlank())
        require(authorDid.isNotBlank())
        require(authorHandle.isNotBlank())
    }

    companion object {
        fun from(post: Post) = DraftParent(post.uri, post.author.did, post.author.handle,
            post.author.displayName, post.text)
    }
}

// Old records keep their original folder and picture paths. New standalone drafts use UUIDs.
const val LEGACY_DRAFT_ID = "legacy"

@Serializable
data class Draft(val text: String = "", val replyTo: String? = null, val pictures: List<DraftPicture> = emptyList(),
    val replyTarget: DraftReply? = null, val id: String = LEGACY_DRAFT_ID) {
    init {
        require(replyTarget?.parent == null || replyTarget.parent.uri == replyTo)
    }
}

data class SavedDraft(val draft: Draft, val savedAt: Long)

/** Private standalone drafts, and one reply draft per target. Pictures belong to one draft. */
class Drafts(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    /** Allocate an independent draft without creating files until it has content. */
    fun newStandalone(): Draft = Draft(id = UUID.randomUUID().toString())

    private fun folder(owner: String, replyTo: String?, id: String = LEGACY_DRAFT_ID): File {
        val key = json.encodeToString(if (replyTo != null || id == LEGACY_DRAFT_ID)
            listOf(owner, replyTo) else listOf(owner, null, id))
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, hash)
    }

    @Synchronized fun load(owner: String, replyTo: String?, id: String = LEGACY_DRAFT_ID): Draft? {
        val file = File(folder(owner, replyTo, id), "draft.json")
        if (!file.exists()) return null
        return json.decodeFromString<Draft>(file.readText())
    }

    /** Verify the account, target and id against the folder hash, including old records. */
    @Synchronized fun list(owner: String): List<SavedDraft> = directory.listFiles().orEmpty().mapNotNull { entry ->
        runCatching {
            val file = File(entry, "draft.json")
            val record = json.parseToJsonElement(file.readText()).jsonObject
            val draft = json.decodeFromJsonElement<Draft>(record)
            if (entry.name != folder(owner, draft.replyTo, draft.id).name ||
                (draft.text.isBlank() && draft.pictures.isEmpty())) return@runCatching null
            SavedDraft(draft, record["savedAt"]?.jsonPrimitive?.long ?: file.lastModified())
        }.getOrNull()
    }.sortedWith(compareByDescending<SavedDraft> { it.savedAt }.thenBy { it.draft.replyTo.orEmpty() }.thenBy { it.draft.id })

    /** Copy even persistable content URIs, so removing the original cannot break a draft. */
    // Not synchronized: a slow picture mustn't hold up saving, which happens on the main thread.
    // Each copy gets its own new file, so nothing else can touch it.
    fun copyPicture(owner: String, replyTo: String?, id: String = LEGACY_DRAFT_ID, open: () -> InputStream): DraftPicture {
        val folder = folder(owner, replyTo, id).apply { mkdirs() }
        val file = File(folder, UUID.randomUUID().toString() + ".image")
        try {
            open().use { input -> FileOutputStream(file).use { out -> input.copyTo(out); out.fd.sync() } }
            return DraftPicture(file.toURI().toString())
        } catch (e: Exception) { file.delete(); throw e }
    }

    @Synchronized fun save(owner: String, draft: Draft) {
        // Opening the composer and typing nothing shouldn't leave anything behind.
        if (draft.text.isBlank() && draft.pictures.isEmpty()) { discard(owner, draft.replyTo, draft.id); return }
        val previous = load(owner, draft.replyTo, draft.id)
        val folder = folder(owner, draft.replyTo, draft.id).apply { mkdirs() }
        val temporary = File(folder, "draft.tmp")
        try {
            FileOutputStream(temporary).use { out ->
                val record = JsonObject(json.encodeToJsonElement(draft).jsonObject + ("savedAt" to JsonPrimitive(now())))
                out.write(record.toString().toByteArray()); out.fd.sync()
            }
            Files.move(temporary.toPath(), File(folder, "draft.json").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        revision.value += 1
        // Only remove old attachments after the replacement has reached disk.
        previous?.pictures?.filter { old -> draft.pictures.none { it.uri == old.uri } }?.forEach { deletePicture(folder, it) }
    }

    @Synchronized fun discard(owner: String, replyTo: String?, id: String = LEGACY_DRAFT_ID) {
        val folder = folder(owner, replyTo, id)
        val file = File(folder, "draft.json")
        check(!file.exists() || file.delete()) { "Couldn't discard this draft. Please try again." }
        // Includes copies left by an interrupted picker or write. Never follow a saved URI elsewhere.
        folder.listFiles().orEmpty().forEach { it.delete() }
        folder.delete()
        revision.value += 1
    }

    private fun deletePicture(folder: File, picture: DraftPicture) {
        val file = runCatching { File(java.net.URI(picture.uri)) }.getOrNull() ?: return
        if (file.canonicalFile.parentFile == folder.canonicalFile && file.name.endsWith(".image")) file.delete()
    }
}
