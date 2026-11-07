package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppsTest {
    private fun app(label: String, pkg: String = label.lowercase(), user: Long = 0) = AppEntry(label, pkg, "Main", user)

    private val apps = sortApps(
        listOf(app("Calculator"), app("camera"), app("Google Camera", "gcam"), app("Caméra Pro", "cpro"), app("Chrome"), app("WhatsApp")),
        Locale.ENGLISH,
    )

    @Test
    fun `sorted alphabetically ignoring case and accents`() {
        assertEquals(listOf("Calculator", "camera", "Caméra Pro", "Chrome", "Google Camera", "WhatsApp"), apps.map { it.label })
    }

    @Test
    fun `search ranks label start, then word start, then anywhere`() {
        assertEquals(listOf("camera", "Caméra Pro", "Google Camera"), searchApps(apps, "cam").map { it.label })
        assertEquals(listOf("WhatsApp"), searchApps(apps, "app").map { it.label })
        assertEquals(listOf("Chrome"), searchApps(apps, " CHR ").map { it.label })
        assertEquals(apps, searchApps(apps, ""))
        assertEquals(emptyList<AppEntry>(), searchApps(apps, "xyz"))
    }

    @Test
    fun `the same app in two profiles stays two entries`() {
        assertEquals(2, setOf(app("Chrome", user = 0).key, app("Chrome", user = 10).key).size)
    }

    @Test
    fun `renamed apps take their new names and sort by them`() {
        val calc = app("Calculator")
        val renamed = renameApps(apps, mapOf(calc.key to "Zz Sums", "gone/Main#0" to "Nothing"), Locale.ENGLISH)
        assertEquals("Zz Sums", renamed.last().label)
        assertEquals(calc.key, renamed.last().key)
        assertEquals(apps.size, renamed.size)
        assertEquals(apps, renameApps(apps, emptyMap(), Locale.ENGLISH))
    }

    @Test
    fun `app labels survive being stored`() {
        val labels = mapOf("a.b/a.b.Main#0" to "My app", "c.d/c.d.X#10" to "Work mail")
        assertEquals(labels, AppLabels.parse(AppLabels.format(labels)))
        assertEquals(mapOf("k" to "two lines"), AppLabels.parse(AppLabels.format(mapOf("k" to " two\tlines\n"))))
        assertEquals(emptyMap<String, String>(), AppLabels.parse(null))
        assertEquals(emptyMap<String, String>(), AppLabels.parse("no tab here\n\tno key"))
    }
}
