package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockSlotsTest {
    @Test fun placeInEmptySlotLeavesGap() = assertEquals(listOf(null, "b", null, "a"), DockSlots.place(listOf("a", "b", null, null), 0, 3))

    @Test fun placeLeftShiftsOthersRight() = assertEquals(listOf("d", "a", "b", "c"), DockSlots.place(listOf("a", "b", "c", "d"), 3, 0))

    @Test fun placeRightShiftsOthersLeft() = assertEquals(listOf("b", "c", "a", "d"), DockSlots.place(listOf("a", "b", "c", "d"), 0, 2))

    @Test fun placeStopsAtNearestGap() = assertEquals(listOf("e", "a", null, "b", null), DockSlots.place(listOf("a", null, null, "b", "e"), 4, 0))

    @Test fun placeOnItselfIsUnchanged() = assertEquals(listOf("a", "b"), DockSlots.place(listOf("a", "b"), 1, 1))

    @Test fun removeLeavesGap() = assertEquals(listOf("", "b"), DockSlots.remove(listOf("a", "b"), "a"))

    @Test fun addFillsFirstGap() = assertEquals(listOf("a", "c", "b"), DockSlots.add(listOf("a", "", "b"), "c", 5))

    @Test fun addPastTheEndPads() = assertEquals(listOf("a", "c"), DockSlots.add(listOf("a"), "c", 5))

    @Test fun addWhenFullIsUnchanged() = assertEquals(listOf("a", "b"), DockSlots.add(listOf("a", "b"), "c", 2))

    @Test fun roomCountsSlotsPastTheList() {
        assertEquals(true, DockSlots.hasRoom(listOf("a"), 2))
        assertEquals(false, DockSlots.hasRoom(listOf("a", "b", ""), 2))
    }

    @Test fun insertTakesEmptySlot() = assertEquals(listOf("a", "", "k"), DockSlots.insert(listOf("a"), "k", 2, 4))

    @Test fun insertOntoAppMakesRoom() = assertEquals(listOf("k", "a", "b"), DockSlots.insert(listOf("a", "b"), "k", 0, 3))

    @Test fun insertMovesAppAlreadyThere() = assertEquals(listOf("b", "a"), DockSlots.insert(listOf("a", "b"), "a", 1, 2))

    @Test fun insertWhenFullIsUnchanged() = assertEquals(listOf("a", "b"), DockSlots.insert(listOf("a", "b"), "k", 0, 2))
}
