package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPacksTest {
    @Test fun searchMatchesEveryWord() {
        val names = listOf("google_camera", "camera", "chrome", "open_camera")
        assertEquals(listOf("google_camera", "camera", "open_camera"), IconPacks.search(names, "Cam"))
        assertEquals(listOf("google_camera"), IconPacks.search(names, "camera goo"))
        assertEquals(names, IconPacks.search(names, "  "))
        assertEquals(emptyList<String>(), IconPacks.search(names, "xyz"))
    }

    @Test fun componentKeys() {
        assertEquals("com.whatsapp/com.whatsapp.Main", IconPacks.componentKey("ComponentInfo{com.whatsapp/com.whatsapp.Main}"))
        assertEquals("org.app/org.app.Home", IconPacks.componentKey("ComponentInfo{org.app/.Home}"))
        assertNull(IconPacks.componentKey("ComponentInfo{broken}"))
        assertNull(IconPacks.componentKey(":LAUNCHER_ACTION_APP_DRAWER"))
        assertNull(IconPacks.componentKey(null))
    }
}
