package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerFoldersTest {
    private fun app(label: String) = AppEntry(label, label.lowercase(), "Main", 0)
    private val folder = DrawerFolders.entry(HomeFolder(3, "Work", emptyList()), "Folder")

    @Test
    fun `games first, then folders, then apps`() {
        val list = DrawerFolders.pinFirst(listOf(app("Camera"), Games.entry, app("Maps")), listOf(folder))
        assertEquals(listOf("Games", "Work", "Camera", "Maps"), list.map { it.label })
        assertEquals(3, DrawerFolders.idOf(folder))
        assertEquals(2, DrawerFolders.pinnedCount(list))
    }

    @Test
    fun `no games entry when it is off`() {
        val list = DrawerFolders.pinFirst(listOf(app("Camera")), listOf(folder))
        assertEquals(listOf("Work", "Camera"), list.map { it.label })
        assertEquals(1, DrawerFolders.pinnedCount(list))
    }
}
