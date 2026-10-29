package io.github.shoalapps.blobfish.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Keep playable streams separate from still images, including record-with-media and quotes. */
object MediaParsing {
    fun bluesky(embed: JsonObject?): List<PlayableMedia> {
        val media = embed?.get("media").obj ?: embed ?: return emptyList()
        val playlist = media.str("playlist")?.takeIf { it.startsWith("https://") } ?: return emptyList()
        val aspect = media["aspectRatio"].obj
        return listOf(PlayableMedia(
            sources = listOf(MediaSource(playlist, "application/x-mpegURL")),
            thumbnail = media.str("thumbnail"), alt = media.str("alt").orEmpty(),
            aspect = aspect?.let { it.int("width").toFloat() / it.int("height").coerceAtLeast(1) },
            loop = media.str("presentation") == "gif",
        ))
    }

    fun twitter(media: List<JsonObject>): List<PlayableMedia> = media.mapNotNull { item ->
        val type = item.str("type")
        if (type == "photo") return@mapNotNull null
        val info = item["video_info"].obj ?: item["audio_info"].obj ?: return@mapNotNull null
        val sources = (info["variants"] as? JsonArray).orEmpty().mapNotNull { variant ->
            val v = variant.obj ?: return@mapNotNull null
            val url = v.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val mime = v.str("content_type") ?: return@mapNotNull null
            if (!(mime.startsWith("audio/") || mime.startsWith("video/") || mime.contains("mpegurl", true))) return@mapNotNull null
            MediaSource(url, mime, (v["bitrate"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0)
        }.distinctBy { it.url }
        if (sources.isEmpty()) return@mapNotNull null
        val size = item["original_info"].obj
        val ratio = (info["aspect_ratio"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
        PlayableMedia(
            sources, item.str("media_url_https"), item.str("ext_alt_text").orEmpty(),
            aspect = if (ratio?.size == 2 && ratio[1] > 0) ratio[0] / ratio[1]
                else size?.takeIf { it.int("height") > 0 }?.let { it.int("width").toFloat() / it.int("height") },
            audio = type == "audio" || sources.all { it.mimeType.startsWith("audio/") },
            loop = type == "animated_gif",
        )
    }
}
