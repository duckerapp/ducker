package com.github.kr328.clash.service.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementStoreTest {
    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_800_000_000_000L

    @Test
    fun `a text is added once, while it stays the same`() {
        val first = emptyList<Announcement>().recorded("a", "Работы с 02:00", previous = "", now = start)!!
        assertEquals(listOf(Announcement("a", "Работы с 02:00", start)), first)

        assertNull(first.recorded("a", " Работы с 02:00\n", previous = "Работы с 02:00", now = start + 3600_000))

        val second = first.recorded("a", "Работы закончены", previous = "Работы с 02:00", now = start + day)!!
        assertEquals(listOf("Работы с 02:00", "Работы закончены"), second.map { it.text })
        assertFalse(second.last().known)
    }

    @Test
    fun `an empty announcement adds nothing and each subscription keeps its own`() {
        val a = emptyList<Announcement>().recorded("a", "A", "", start)!!
        assertNull(a.recorded("a", "  ", "A", start + 1))

        val both = a.recorded("b", "A", "", start + 2)!!
        assertEquals(listOf("a", "b"), both.map { it.profile })
    }

    @Test
    fun `the text already shown before the history began is known, a new one is not`() {
        assertTrue(emptyList<Announcement>().recorded("a", "Old", previous = "Old", now = start)!!.single().known)
        assertFalse(emptyList<Announcement>().recorded("a", "New", previous = "Old", now = start)!!.single().known)
    }

    @Test
    fun `entries older than ten days go, and a text that outlived them comes back as known`() {
        val old = listOf(Announcement("a", "Same", start), Announcement("b", "Other", start + 5 * day))
        val later = old.recorded("a", "Same", previous = "Same", now = start + 11 * day)!!

        assertEquals(listOf(Announcement("b", "Other", start + 5 * day), Announcement("a", "Same", start + 11 * day, known = true)), later)
        // Only pruning: written back so the file shrinks.
        assertEquals(listOf(Announcement("b", "Other", start + 5 * day)), old.recorded("a", "", "", start + 11 * day))
    }

    @Test
    fun `a subscription keeps its last thirty`() {
        var list = emptyList<Announcement>()
        repeat(40) { list = list.recorded("a", "Text $it", "", start + it * 60_000L)!! }

        assertEquals(30, list.size)
        assertEquals("Text 10", list.first().text)
        assertEquals("Text 39", list.last().text)
    }
}
