package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    private fun app(label: String) = AppEntry(label, "p.$label", "c", 0)

    @Test fun initialIsCapitalisedWithoutAccent() {
        assertEquals('E', initialOf("éclair"))
        assertEquals('#', initialOf("2048"))
        assertEquals('#', initialOf(""))
    }

    @Test fun firstPositionPerInitial() {
        val list = listOf("2048", "Artha", "Asana", "Bitwarden", "Camera").map(::app)
        assertEquals(listOf('#' to 0, 'A' to 1, 'B' to 3, 'C' to 4), initialsOf(list))
    }
}
