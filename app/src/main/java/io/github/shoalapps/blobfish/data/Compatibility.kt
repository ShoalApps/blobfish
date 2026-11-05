package io.github.shoalapps.blobfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Names only: never post text, user IDs, or whole responses. Counts saturate rather than overflow. */
class TypeCounter(initial: Map<String, Int> = emptyMap(), private val changed: (Map<String, Int>) -> Unit = {}) {
    private val counts = initial.entries.filter { valid(it.key) && it.value > 0 }.take(100).associate { it.toPair() }.toMutableMap()
    @Synchronized fun record(type: String) {
        if (!valid(type) || (type !in counts && counts.size >= 100)) return
        counts[type] = ((counts[type] ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        changed(counts.toMap())
    }
    @Synchronized fun snapshot(): Map<String, Int> = counts.toMap()
    private fun valid(type: String) = type.length in 1..256 && type.all { it.isLetterOrDigit() || it in "._:#-" }
}

@Serializable data class CompatFeature(val id: String, val description: String, val rendered: Boolean)
@Serializable data class BlueskyCompatibility(
    val date: String? = null,
    val upstreamCommit: String? = null,
    val features: List<CompatFeature> = emptyList(),
    val supportedVariants: List<String> = emptyList(),
) {
    val shown get() = features.count { it.rendered }
    companion object {
        /** No Android dependency: fixtures and the bundled asset use exactly the same loader. */
        fun parse(text: String): BlueskyCompatibility {
            val result = Json { ignoreUnknownKeys = true }.decodeFromString<BlueskyCompatibility>(text)
            result.date?.let { java.time.LocalDate.parse(it) }
            require(result.upstreamCommit == null || result.upstreamCommit.matches(Regex("[0-9a-f]{40}")))
            require(result.features.map { it.id }.distinct().size == result.features.size)
            return result
        }
    }
}
