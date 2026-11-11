package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SpacesTest {
    private val personal = Space(1, "Personal")
    private val work = Space(2, "Work", SpaceIcon.Work, days = 0b0111110, from = 9 * 60, to = 17 * 60)
    private val night = Space(3, "Night", SpaceIcon.Night, days = 0b1111111, from = 22 * 60, to = 7 * 60)
    private val all = listOf(personal, work, night)

    // 2026-11-11 is a Wednesday; 2026-11-14 a Saturday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    @Test fun schedule() {
        assertEquals("Work", Spaces.scheduled(all, at(11, 9))!!.name)
        assertEquals("Personal", Spaces.scheduled(all, at(11, 17))!!.name)
        assertEquals("Night", Spaces.scheduled(all, at(11, 23))!!.name)
        assertEquals("Night", Spaces.scheduled(all, at(12, 6, 59))!!.name)
        assertEquals("Personal", Spaces.scheduled(all, at(12, 7))!!.name)
        assertEquals("Personal", Spaces.scheduled(all, at(14, 10))!!.name)
    }

    @Test fun overnightBelongsToItsStartDay() {
        val friNight = Space(4, "Fri", days = 1 shl 5, from = 22 * 60, to = 2 * 60)
        assertTrue(Spaces.covers(friNight, at(13, 23)))
        assertTrue(Spaces.covers(friNight, at(14, 1)))
        assertFalse(Spaces.covers(friNight, at(12, 23)))
        assertFalse(Spaces.covers(friNight, at(15, 1)))
    }

    @Test fun nextChange() {
        assertEquals(at(11, 17), Spaces.nextChange(all, at(11, 10, 30)))
        assertEquals(at(11, 22), Spaces.nextChange(all, at(11, 17)))
        assertEquals(null, Spaces.nextChange(listOf(personal), at(11, 10)))
    }

    @Test fun storedForm() {
        val s = listOf(personal, work.copy(layout = SpaceLayout("a/b#0\n\tx", "f", "", "d\nd", "h")))
        assertEquals(s, Spaces.parse(Spaces.format(s)))
        assertEquals(emptyList<Space>(), Spaces.parse("junk\n1\tx"))
    }

    @Test fun describe() {
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        assertEquals("Mon–Fri · 9:00–17:00", Spaces.describe(work, names, "Every day", "Mon–Fri", "Weekends"))
        assertEquals("Every day · 22:00–7:00", Spaces.describe(night, names, "Every day", "Mon–Fri", "Weekends"))
        assertEquals(null, Spaces.describe(personal, names, "Every day", "Mon–Fri", "Weekends"))
    }
}
