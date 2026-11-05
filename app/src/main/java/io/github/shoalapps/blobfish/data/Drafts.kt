package io.github.shoalapps.blobfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class DraftPicture(val uri: String, val alt: String = "")

@Serializable
data class Draft(val text: String = "", val replyTo: String? = null, val pictures: List<DraftPicture> = emptyList())

/** One private draft for each account and reply target. Pictures belong to that draft alone. */
class Drafts(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun folder(owner: String, replyTo: String?): File {
        val key = json.encodeToString(listOf(owner, replyTo))
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, hash)
    }

    @Synchronized fun load(owner: String, replyTo: String?): Draft? {
        val file = File(folder(owner, replyTo), "draft.json")
        if (!file.exists()) return null
        return json.decodeFromString<Draft>(file.readText())
    }

    /** Copy even persistable content URIs, so removing the original cannot break a draft. */
    @Synchronized fun copyPicture(owner: String, replyTo: String?, open: () -> InputStream): DraftPicture {
        val folder = folder(owner, replyTo).apply { mkdirs() }
        val file = File(folder, UUID.randomUUID().toString() + ".image")
        try {
            open().use { input -> FileOutputStream(file).use { out -> input.copyTo(out); out.fd.sync() } }
            return DraftPicture(file.toURI().toString())
        } catch (e: Exception) { file.delete(); throw e }
    }

    @Synchronized fun save(owner: String, draft: Draft) {
        // Opening the composer and typing nothing shouldn't leave anything behind.
        if (draft.text.isBlank() && draft.pictures.isEmpty()) { discard(owner, draft.replyTo); return }
        val previous = load(owner, draft.replyTo)
        val folder = folder(owner, draft.replyTo).apply { mkdirs() }
        val temporary = File(folder, "draft.tmp")
        try {
            FileOutputStream(temporary).use { out ->
                out.write(json.encodeToString(draft).toByteArray()); out.fd.sync()
            }
            Files.move(temporary.toPath(), File(folder, "draft.json").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
        // Only remove old attachments after the replacement has reached disk.
        previous?.pictures?.filter { old -> draft.pictures.none { it.uri == old.uri } }?.forEach { deletePicture(folder, it) }
    }

    @Synchronized fun discard(owner: String, replyTo: String?) {
        val folder = folder(owner, replyTo)
        val file = File(folder, "draft.json")
        check(!file.exists() || file.delete()) { "Couldn't discard this draft. Please try again." }
        // Includes copies left by an interrupted picker or write. Never follow a saved URI elsewhere.
        folder.listFiles().orEmpty().forEach { it.delete() }
        folder.delete()
    }

    private fun deletePicture(folder: File, picture: DraftPicture) {
        val file = runCatching { File(java.net.URI(picture.uri)) }.getOrNull() ?: return
        if (file.canonicalFile.parentFile == folder.canonicalFile && file.name.endsWith(".image")) file.delete()
    }
}
