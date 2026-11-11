package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPacksTest {
    @Test fun componentKeys() {
        assertEquals("com.whatsapp/com.whatsapp.Main", IconPacks.componentKey("ComponentInfo{com.whatsapp/com.whatsapp.Main}"))
        assertEquals("org.app/org.app.Home", IconPacks.componentKey("ComponentInfo{org.app/.Home}"))
        assertNull(IconPacks.componentKey("ComponentInfo{broken}"))
        assertNull(IconPacks.componentKey(":LAUNCHER_ACTION_APP_DRAWER"))
        assertNull(IconPacks.componentKey(null))
    }
}
