package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {
    @Test fun roundTrip() {
        val values = mapOf(
            "home" to "a/b#0\tx\nc/d#0\\e",
            "show_labels" to true,
            "home_columns" to 5,
            "font_version" to 1_700_000_000_000L,
            "empty" to "",
        )
        assertEquals(values, Backup.decode(Backup.encode(values)))
    }

    @Test fun rejectsOtherFiles() {
        assertNull(Backup.decode("hello"))
        assertNull(Backup.decode(""))
        assertEquals(mapOf("ok" to 1), Backup.decode("${Backup.Header}\ni\tok\t1\ni\tbad\tx\nz\tq\t1\nnot a line"))
    }
}
