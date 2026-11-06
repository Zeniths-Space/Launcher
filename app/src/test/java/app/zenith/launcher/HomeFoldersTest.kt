package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFoldersTest {
    @Test fun storedFormRoundTrips() {
        val folders = listOf(HomeFolder(1, "Games", listOf("a/b#0", "c/d#0")), HomeFolder(2, "", listOf("e/f#0", "g/h#0")))
        assertEquals(folders, HomeFolders.parse(HomeFolders.format(folders)))
    }

    @Test fun namesLoseTabsAndLineBreaks() =
        assertEquals("a b c", HomeFolders.parse(HomeFolders.format(listOf(HomeFolder(1, "a\tb\nc", listOf("x", "y"))))).single().name)

    @Test fun keysRoundTrip() {
        assertEquals(7, HomeFolders.idOf(HomeFolders.key(7)))
        assertNull(HomeFolders.idOf("com.app/.Main#0"))
    }

    @Test fun appOntoAppMakesFolderInTargetSlot() {
        val (pages, folders) = HomeFolders.merge(listOf(listOf("a", "b", "c")), emptyList(), 0, 2, 0, 0)!!
        assertEquals(listOf(listOf("folder:1", "b")), pages)
        assertEquals(listOf(HomeFolder(1, "", listOf("a", "c"))), folders)
    }

    @Test fun appOntoFolderJoinsIt() {
        val start = listOf(HomeFolder(3, "X", listOf("a", "b")))
        val (pages, folders) = HomeFolders.merge(listOf(listOf("folder:3"), listOf("c")), start, 1, 0, 0, 0)!!
        assertEquals(listOf(listOf("folder:3"), emptyList()), pages)
        assertEquals(listOf("a", "b", "c"), folders.single().apps)
    }

    @Test fun folderCantBeDroppedIntoSomething() = assertNull(HomeFolders.merge(listOf(listOf("folder:1", "a")), listOf(HomeFolder(1, "", listOf("x", "y"))), 0, 0, 0, 1))

    @Test fun removingDownToOneAppDissolvesFolder() {
        val (pages, folders) = HomeFolders.removeApp(listOf(listOf("", "folder:1")), listOf(HomeFolder(1, "", listOf("a", "b"))), "a")
        assertEquals(listOf(listOf("", "b")), pages)
        assertEquals(emptyList<HomeFolder>(), folders)
    }

    @Test fun removingFromSlotLeavesFoldersAlone() {
        val folders = listOf(HomeFolder(1, "", listOf("a", "b")))
        assertEquals(listOf(listOf("folder:1")) to folders, HomeFolders.removeApp(listOf(listOf("folder:1", "c")), folders, "c"))
    }

    @Test fun removeFolderClearsSlot() =
        assertEquals(listOf(listOf("", "c")) to emptyList<HomeFolder>(), HomeFolders.removeFolder(listOf(listOf("folder:1", "c")), listOf(HomeFolder(1, "", listOf("a", "b"))), 1))

    @Test fun looksRoundTrip() {
        val folder = HomeFolder(4, "Big", listOf("a", "b"), large = true, style = FolderStyle.Symbol, color = 3)
        assertEquals(listOf(folder), HomeFolders.parse(HomeFolders.format(listOf(folder))))
    }

    @Test fun oldLinesReadWithDefaultLooks() = assertEquals(HomeFolder(1, "Old", listOf("a", "b")), HomeFolders.parse("1\ta\u001Fb\tOld").single())

    @Test fun largeFolderCoversFourCells() {
        val pages = listOf(listOf("", "folder:1"))
        val folders = listOf(HomeFolder(1, "", listOf("a", "b"), large = true))
        assertEquals(setOf(1, 2, 5, 6), HomeFolders.largeCells(pages, folders, 0, columns = 4, rows = 4))
        assertEquals(1, HomeFolders.anchorAt(pages, folders, 0, 6, 4, 4))
        assertEquals(3, HomeFolders.anchorAt(pages, folders, 0, 3, 4, 4))
    }

    @Test fun largeInLastColumnShowsSmall() =
        assertEquals(emptySet<Int>(), HomeFolders.largeCells(listOf(listOf("", "", "", "folder:1")), listOf(HomeFolder(1, "", listOf("a", "b"), large = true)), 0, 4, 4))

    @Test fun fitsLargeNeedsFreeCells() {
        val pages = listOf(listOf("folder:1", "", "", "", "", "", "x"))
        assertEquals(true, HomeFolders.fitsLarge(pages, 0, 0, 4, 4, emptySet(), "folder:1"))
        assertEquals(false, HomeFolders.fitsLarge(pages, 0, 1, 4, 4, emptySet(), "folder:1"))
        assertEquals(false, HomeFolders.fitsLarge(pages, 0, 0, 4, 4, setOf(4), "folder:1"))
        assertEquals(false, HomeFolders.fitsLarge(pages, 0, 3, 4, 4, emptySet(), "folder:1"))
    }
}
