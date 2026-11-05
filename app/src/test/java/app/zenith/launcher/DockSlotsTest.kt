package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockSlotsTest {
    @Test fun moveToEmptySlotLeavesGap() = assertEquals(listOf("", "b", "", "a"), DockSlots.move(listOf("a", "b"), 0, 3))

    @Test fun moveOntoAppSwaps() = assertEquals(listOf("b", "a"), DockSlots.move(listOf("a", "b"), 0, 1))

    @Test fun moveLastAwayTrimsTrailingEmpties() = assertEquals(listOf("a", "b"), DockSlots.move(listOf("a", "", "b"), 2, 1))

    @Test fun removeLeavesGap() = assertEquals(listOf("", "b"), DockSlots.remove(listOf("a", "b"), "a"))

    @Test fun addFillsFirstGap() = assertEquals(listOf("a", "c", "b"), DockSlots.add(listOf("a", "", "b"), "c", 5))

    @Test fun addWhenFullIsUnchanged() = assertEquals(listOf("a", "b"), DockSlots.add(listOf("a", "b"), "c", 2))

    @Test fun roomCountsSlotsPastTheList() {
        assertEquals(true, DockSlots.hasRoom(listOf("a"), 2))
        assertEquals(false, DockSlots.hasRoom(listOf("a", "b", ""), 2))
    }
}
