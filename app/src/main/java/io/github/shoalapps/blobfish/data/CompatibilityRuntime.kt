package io.github.shoalapps.blobfish.data

import android.content.Context

/** Private, device-local type names and aggregate sightings. No telemetry is sent. */
object CompatibilityRuntime {
    var bluesky = TypeCounter(); private set
    var x = TypeCounter(); private set
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("compatibility-types", Context.MODE_PRIVATE)
        fun counter(prefix: String): TypeCounter {
            val initial = prefs.all.mapNotNull { (key, value) ->
                if (key.startsWith(prefix) && value is Int) key.removePrefix(prefix) to value else null
            }.toMap()
            return TypeCounter(initial) { counts ->
                prefs.edit().apply { counts.forEach { (id, count) -> putInt(prefix + id, count) } }.apply()
            }
        }
        bluesky = counter("bsky:"); x = counter("x:")
        TwitterParse.unknownType = { x.record(it) }
    }
}
