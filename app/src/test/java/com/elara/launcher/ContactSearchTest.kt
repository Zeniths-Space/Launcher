package com.elara.launcher

import com.elara.launcher.ContactSearch.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactSearchTest {
    private val rows = listOf(
        Row(1, "a", "Sita Ramdam", "111", false),
        Row(2, "b", "Ram Sharma", "222", false),
        Row(2, "b", "Ram Sharma", "333", true),
        Row(3, "c", "Ram Bahadur", "444", false),
        Row(4, "d", "", "555", false),
    )

    @Test fun oneHitPerContactWithDefaultNumber() {
        val hits = ContactSearch.group(rows, "ram")
        assertEquals(listOf("Ram Sharma", "Ram Bahadur", "Sita Ramdam"), hits.map { it.name })
        assertEquals("333", hits.first().number)
    }

    @Test fun topCardOnlyWhenNoAppStartsWithQuery() {
        val hits = ContactSearch.group(rows, "ram")
        val insta = AppEntry("Instagram", "i", "I", 0)
        assertEquals("Ram Sharma", ContactSearch.top(hits, listOf(insta), "ram")?.name)
        assertNull(ContactSearch.top(hits, listOf(AppEntry("Ramro Patro", "r", "R", 0)), "ram"))
        // "amd" is only inside a name: no top card.
        assertNull(ContactSearch.top(ContactSearch.group(rows, "amd"), emptyList(), "amd"))
    }

    @Test fun initials() {
        assertEquals("RS", ContactHit(1, "", "Ram Sharma", "1").initials)
        assertEquals("M", ContactHit(1, "", "mum", "1").initials)
    }
}
