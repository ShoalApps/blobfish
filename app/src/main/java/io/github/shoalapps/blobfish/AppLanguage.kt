package io.github.shoalapps.blobfish

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Bundled UI catalogues. No messages, account details or content are sent to a translation service. */
object AppLanguage {
    private lateinit var app: Context
    private val catalogues = ConcurrentHashMap<String, Map<String, String>>()
    private val resolved = ConcurrentHashMap<String, Map<String, String>>()
    private val patterns = ConcurrentHashMap<String, List<Pair<Regex, String>>>()
    private fun prefs(c: Context) = c.getSharedPreferences("app-language", Context.MODE_PRIVATE)
    fun init(context: Context) { app = context.applicationContext }
    fun chosen(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags()
            .substringBefore(',')
    } else prefs(context).getString("tag", "").orEmpty()

    /** An empty preference deliberately leaves Android's system/per-app language in charge. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = chosen(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun set(context: Context, tag: String) {
        prefs(context).edit().putString("tag", tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        }
        Locale.setDefault(if (tag.isBlank()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag))
        if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
    }

    private fun read(context: Context, tag: String): Map<String, String> = catalogues.getOrPut(tag) {
        runCatching {
            val json = JSONObject(context.assets.open("i18n/$tag.json").bufferedReader().use { it.readText() })
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun translate(text: String): String = if (::app.isInitialized) {
        val tag = chosen(app).ifBlank { app.resources.configuration.locales[0].toLanguageTag() }
        translate(app, text, tag)
    } else text

    fun translate(context: Context, text: String, tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val language = when (locale.language) { "fil" -> "tl"; "no" -> "nb"; else -> locale.language }
        if (language == "en" || text.isBlank()) return text
        val candidates = listOf(tag, if (locale.country.isNotBlank()) "$language-${locale.country}" else "",
            if (locale.script == "Hant") "zh-TW" else "", language).filter { it.isNotBlank() }
        val table = resolved.getOrPut(tag) { linkedMapOf<String, String>().apply {
            candidates.asReversed().forEach { putAll(read(context, it)) }
        } }
        table[text]?.let { return it }
        val templates = patterns.getOrPut(tag) {
            table.filterKeys { it.contains(Regex("\\{[0-9]+\\}")) }.map { (source, target) ->
                val parts = source.split(Regex("\\{[0-9]+\\}"))
                Regex("^" + parts.joinToString("(.+?)") { Regex.escape(it) } + "$") to target
            }
        }
        for ((pattern, target) in templates) {
            val match = pattern.matchEntire(text) ?: continue
            var result = target
            match.groupValues.drop(1).forEachIndexed { index, value -> result = result.replace("{$index}", value) }
            return result
        }
        return text
    }

    @Composable
    fun text(source: String): String {
        val context = LocalContext.current
        val tag = LocalConfiguration.current.locales[0].toLanguageTag()
        return remember(source, tag) { translate(context, source, tag) }
    }

    @Composable
    fun SettingsRow() {
        val context = LocalContext.current
        var open by remember { mutableStateOf(false) }
        val selected = chosen(context)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(text("App language"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { open = true }) {
                Text(if (selected.isBlank()) text("Follow system language") else Locale.forLanguageTag(selected).getDisplayName(Locale.forLanguageTag(selected)))
            }
            Text(text("Some translations are automatic. Untranslated text appears in English."), style = MaterialTheme.typography.bodySmall)
        }
        if (open) {
            val tags = remember {
                runCatching { JSONArray(context.assets.open("i18n/languages.json").bufferedReader().use { it.readText() })
                    .let { array -> (0 until array.length()).map { array.getString(it) } } }.getOrDefault(listOf("en-GB"))
            }
            AlertDialog(onDismissRequest = { open = false }, title = { Text(text("Choose app language")) }, text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { open = false; set(context, "") }) { Text(text("Follow system language")) }
                    tags.sortedBy { Locale.forLanguageTag(it).getDisplayName(Locale.forLanguageTag(it)) }.forEach { tag ->
                        TextButton(onClick = { open = false; set(context, tag) }) {
                            Text(Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag)))
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { open = false }) { Text(text("Close")) } })
        }
    }
}
