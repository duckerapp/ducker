package com.github.kr328.clash.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A provider's name in the header (Q-95), with a font where every character is 10 px wide at 32 sp. */
class BrandFitTest {
    private fun fit(name: String, room: Float) = fitBrand(name, room, base = 32f, floor = 18f) { it.length * 10f }

    @Test
    fun `a name that fits stays whole at full size`() {
        assertEquals(BrandFit("Pond", 32f), fit("Pond", 200f))
    }

    @Test
    fun `one line shrinks down to three quarters`() {
        val shown = fit("Invisible Man", 110f)

        assertEquals("Invisible Man", shown.text)
        assertTrue(shown.size in 24f..32f)
    }

    @Test
    fun `two lines break at a space, never inside a word`() {
        val shown = fit("Invisible Man", 90f)

        assertEquals("Invisible\nMan", shown.text)
        assertTrue(shown.size > 24f)
    }

    @Test
    fun `the break makes the lines most even`() {
        assertEquals("Invisible\nMan VPN", fit("Invisible Man VPN", 100f).text)
    }

    @Test
    fun `a long single word shrinks, then ends with an ellipsis at the floor`() {
        assertEquals("SuperFastVPN", fit("SuperFastVPN", 100f).text)
        assertEquals(18f, fit("SuperFastVPNPlusUltra", 100f).size)
    }

    @Test
    fun `an uneven break is taken only when one line would fall below the floor`() {
        assertEquals("A Supercalifragilistic", fit("A Supercalifragilistic", 160f).text)
        assertEquals("A\nSupercalifragilistic", fit("A Supercalifragilistic", 120f).text)
    }
}
