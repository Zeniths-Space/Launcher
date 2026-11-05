package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePagesTest {
    @Test fun storedFormRoundTrips() {
        val pages = listOf(listOf("a", "", "b"), listOf("", "c"))
        assertEquals(pages, HomePages.parse(HomePages.format(pages)))
    }

    @Test fun emptyPagesAreDropped() = assertEquals(listOf(listOf("a")), HomePages.parse(HomePages.format(listOf(listOf("", ""), listOf("a")))))

    @Test fun nothingStoredIsNoPages() = assertEquals(emptyList<List<String>>(), HomePages.parse(null))

    @Test fun moveWithinPageMakesRoom() =
        assertEquals(listOf(listOf("c", "a", "b")), HomePages.move(listOf(listOf("a", "b", "c")), 0, 2, 0, 0, 4))

    @Test fun moveToAnotherPageEntersByNearestGap() =
        assertEquals(listOf(listOf("", "b"), listOf("x", "a", "y")), HomePages.move(listOf(listOf("a", "b"), listOf("x", "y")), 0, 0, 1, 1, 3))

    @Test fun moveToFullPageFails() = assertNull(HomePages.move(listOf(listOf("a"), listOf("x", "y")), 0, 0, 1, 0, 2))

    @Test fun moveToNewPage() = assertEquals(listOf(listOf("", "b"), listOf("", "", "a")), HomePages.move(listOf(listOf("a", "b")), 0, 0, 1, 2, 4))

    @Test fun addFillsFirstGapThenNewPage() {
        assertEquals(listOf(listOf("a", "k")), HomePages.add(listOf(listOf("a")), "k", 2))
        assertEquals(listOf(listOf("a", "b"), listOf("k")), HomePages.add(listOf(listOf("a", "b")), "k", 2))
    }

    @Test fun removeLeavesGap() = assertEquals(listOf(listOf("", "b")), HomePages.remove(listOf(listOf("a", "b")), "a"))
}
