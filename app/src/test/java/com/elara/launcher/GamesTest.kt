package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class GamesTest {
    @Test fun detectedLessRemovedPlusAdded() {
        assertEquals(setOf("b", "c"), Games.members(detected = setOf("a", "b"), added = setOf("c"), removed = setOf("a")))
    }

    @Test fun addedWinsOverRemovedWhenBoth() {
        assertEquals(setOf("a"), Games.members(setOf("a"), added = setOf("a"), removed = setOf("a")))
    }
}
