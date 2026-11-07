package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetsTest {
    private val clock = HomeWidget(id = 7, page = 0, col = 0, row = 0, w = 2, h = 1)

    @Test fun storedFormRoundTrips() {
        val widgets = listOf(clock, HomeWidget(9, 1, 1, 2, 3, 2))
        assertEquals(widgets, HomeWidgets.parse(HomeWidgets.format(widgets)))
    }

    @Test fun damagedLinesAreSkipped() = assertEquals(listOf(clock), HomeWidgets.parse("7,0,0,0,2,1\nnope\n1,2,3"))

    @Test fun coversItsCells() = assertEquals(setOf(0, 1, 4, 5), HomeWidget(1, 0, 0, 0, 2, 2).slots(columns = 4, rows = 5))

    @Test fun shrinksIntoSmallerGrid() = assertEquals(HomeWidget(1, 0, 0, 3, 3, 2), HomeWidget(1, 0, 2, 4, 4, 2).fitted(columns = 3, rows = 5))

    @Test fun fitsOnlyOnFreeCells() {
        val pages = listOf(listOf("", "", "a"))
        assertTrue(HomeWidgets.fits(clock, pages, emptyList(), 4, 5))
        assertFalse(HomeWidgets.fits(clock.copy(col = 1), pages, emptyList(), 4, 5))
        assertFalse(HomeWidgets.fits(clock.copy(col = 3), pages, emptyList(), 4, 5))
        assertFalse(HomeWidgets.fits(HomeWidget(8, 0, 1, 0, 1, 1), emptyList(), listOf(clock), 4, 5))
    }

    @Test fun placeTakesFirstFreeSpot() =
        assertEquals(HomeWidget(8, 0, 2, 0, 2, 1), HomeWidgets.place(8, 2, 1, listOf(listOf("a")), listOf(clock.copy(col = 0, w = 1).copy(col = 1)), 4, 5, first = 0))

    @Test fun placeGoesToNewPageWhenFull() =
        assertEquals(HomeWidget(8, 1, 0, 0, 2, 2), HomeWidgets.place(8, 2, 2, listOf(List(4) { "x$it" }), emptyList(), 2, 2, first = 0))

    @Test fun removePageShiftsLaterWidgets() =
        assertEquals(listOf(HomeWidget(2, 0, 0, 0, 1, 1)), HomeWidgets.removePage(listOf(clock, HomeWidget(2, 1, 0, 0, 1, 1)), 0))

    @Test fun appMovesSkipWidgetCells() {
        // Slots 1 and 2 are under a widget: "c" moving to the front pushes "a" past them.
        val pages = listOf(listOf("a", "", "", "c"))
        assertEquals(listOf(listOf("c", "", "", "a")), HomePages.move(pages, 0, 3, 0, 0, 4) { setOf(1, 2) })
    }

    @Test fun appCantMoveOntoWidget() = assertNull(HomePages.move(listOf(listOf("a")), 0, 0, 0, 1, 4) { setOf(1) })

    @Test fun addSkipsWidgetCells() = assertEquals(listOf(listOf("", "", "k")), HomePages.add(listOf(emptyList()), "k", 4) { setOf(0, 1) })
}
