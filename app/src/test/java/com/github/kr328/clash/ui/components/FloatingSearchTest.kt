package com.github.kr328.clash.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingSearchTest {
    private fun FloatingSearch.scroll(y: Float, overscroll: Float = 0f) =
        onPostScroll(Offset(0f, y), Offset(0f, overscroll), NestedScrollSource.UserInput)

    @Test
    fun `a small reversal does not flash the search`() {
        val search = FloatingSearch(8f)
        search.scroll(-30f)
        search.scroll(4f)
        assertFalse(search.shown)
        search.scroll(5f)
        assertTrue(search.shown)
        search.scroll(-2f)
        assertTrue(search.shown)
        search.scroll(-7f)
        assertFalse(search.shown)
    }

    @Test
    fun `edge overscroll does not count as consumed list movement`() {
        val search = FloatingSearch(8f)
        search.scroll(0f, overscroll = 50f)
        assertFalse(search.shown)
        search.scroll(20f)
        assertTrue(search.shown)
        search.scroll(0f, overscroll = -50f)
        assertTrue(search.shown)
    }
}
