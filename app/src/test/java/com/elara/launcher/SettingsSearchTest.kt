package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun labels(q: String) = SettingsSearch.find(q).map { it.label }

    @Test fun byNameThenWord() {
        assertEquals("Wi-Fi", labels("wifi").first())
        assertEquals("Wi-Fi", labels("wi-f").first())
        assertEquals(listOf("Bluetooth"), labels("blue").take(1))
        assertTrue("Display" in labels("brightness"))
        assertTrue("Mobile hotspot" in labels("hotspot"))
        assertTrue(labels("x").isEmpty())
        assertTrue(labels("zzzz").isEmpty())
    }

    @Test fun topCardNeedsAStartMatch() {
        val wifi = SettingsSearch.pages.first { it.label == "Wi-Fi" }
        assertTrue(SettingsSearch.strong(wifi, "wifi"))
        assertTrue(!SettingsSearch.strong(wifi, "ifi"))
    }
}
