package com.elara.launcher

import java.text.Collator
import java.util.Locale

/**
 * One launchable activity. [key] is unique across profiles (a work-profile copy of an app is a
 * separate entry), [userSerial] tells profiles apart without keeping a UserHandle in pure code.
 */
data class AppEntry(val label: String, val packageName: String, val className: String, val userSerial: Long) {
    val key: String get() = "$packageName/$className#$userSerial"
}

/** Alphabetical by label in the device's language, then by package so the order is stable. */
fun sortApps(apps: List<AppEntry>, locale: Locale = Locale.getDefault()): List<AppEntry> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return apps.sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName) }
}

/**
 * Apps matching [query], best first: label starts with it, then a word in the label starts with it,
 * then the label contains it anywhere; alphabetical within each. Case and accents are ignored.
 */
fun searchApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = fold(query.trim())
    if (q.isEmpty()) return apps
    return apps.mapNotNull { app ->
        val label = fold(app.label)
        when {
            label.startsWith(q) -> 0
            label.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            q in label -> 2
            else -> null
        }?.let { it to app }
    }.sortedBy { it.first }.map { it.second }
}

/** Lowercase without accents, so "camera" finds "Caméra". */
private fun fold(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** [apps] with the names the user gave them ([labels] by [AppEntry.key]), in order by the new names. */
fun renameApps(apps: List<AppEntry>, labels: Map<String, String>, locale: Locale = Locale.getDefault()): List<AppEntry> =
    if (labels.isEmpty()) apps else sortApps(apps.map { app -> labels[app.key]?.let { app.copy(label = it) } ?: app }, locale)

/** App names, a line each: key, tab, name. Keys have no tab; names are kept to one line without tabs. */
object AppLabels {
    fun parse(stored: String?): Map<String, String> = stored.orEmpty().lines().mapNotNull { line ->
        val (key, label) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        (key to label).takeIf { key.isNotEmpty() && label.isNotEmpty() }
    }.toMap()

    fun format(labels: Map<String, String>): String = labels.entries.joinToString("\n") { "${it.key}\t${clean(it.value)}" }

    fun clean(name: String) = name.replace(Regex("[\\t\\r\\n]"), " ").trim()
}
