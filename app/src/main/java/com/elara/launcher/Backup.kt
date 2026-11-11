package com.elara.launcher

/**
 * The text form of a layout backup: a header line, then one line per setting,
 * "type<TAB>key<TAB>value", type b (yes/no), i (whole number), l (long number) or s (text, with
 * backslash, tab and line break escaped). Plain text, so a person can read what it holds.
 */
internal object Backup {
    const val Header = "elara-launcher-backup 1"

    fun encode(values: Map<String, Any>): String = buildString {
        append(Header).append('\n')
        values.toSortedMap().forEach { (key, value) ->
            val type = when (value) {
                is Boolean -> 'b'
                is Int -> 'i'
                is Long -> 'l'
                is String -> 's'
                else -> return@forEach
            }
            append(type).append('\t').append(key).append('\t').append(if (value is String) escape(value) else value.toString()).append('\n')
        }
    }

    /** The settings in [text], or null when it isn't a backup this version can read. Bad lines are skipped. */
    fun decode(text: String): Map<String, Any>? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != Header) return null
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split('\t', limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            val (type, key, raw) = parts
            val value: Any = when (type) {
                "b" -> raw.toBooleanStrictOrNull()
                "i" -> raw.toIntOrNull()
                "l" -> raw.toLongOrNull()
                "s" -> unescape(raw)
                else -> null
            } ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                append(
                    when (s[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> s[i + 1]
                    },
                )
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
