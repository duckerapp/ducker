package com.github.kr328.clash.ui.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The log's copy for the clipboard keeps the newest lines within its budget (D-369). */
class ClipReportTest {
    private val text = (1..1000).joinToString("\n", postfix = "\n") { "line $it ".padEnd(99, 'x') }

    @Test
    fun `text that fits is kept as it is`() {
        assertEquals(text, newestLines(text, text.length))
    }

    @Test
    fun `the newest whole lines stay, under a note of how many went`() {
        val clip = newestLines(text, 10_000)
        val lines = clip.lines().dropLast(1)

        assertTrue(clip.length <= 10_000, "${clip.length}")
        assertEquals("line 1000 ", lines.last().take(10))
        assertTrue(lines.drop(1).all { it.length == 99 }, "a line was cut")
        assertEquals("# ${1000 - (lines.size - 1)} older lines left out", lines.first().substringBefore(" to fit"))
    }

    @Test
    fun `a budget smaller than the note leaves the note alone`() {
        assertEquals(1, newestLines(text, 50).lines().dropLast(1).size)
    }
}
