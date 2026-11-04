package app.zenith.launcher

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
}
