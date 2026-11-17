package com.github.kr328.clash.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PillSegmentsTest {
    // Three segments of unequal width, left to right and as a right-to-left layout places them.
    private val ltr = floatArrayOf(0f, 100f, 100f, 60f, 160f, 140f)
    private val rtl = floatArrayOf(200f, 100f, 140f, 60f, 0f, 140f)

    @Test
    fun aFingerAtThePillsMiddleGivesBackItsPosition() {
        for (spans in listOf(ltr, rtl)) {
            for (position in listOf(0f, 0.25f, 0.5f, 1f, 1.7f, 2f)) {
                val (left, width) = pillSpan(spans, position)
                assertEquals(position, positionAt(spans, left + width / 2f), 1e-4f)
            }
        }
    }

    @Test
    fun pastTheEndsThePillStaysAtTheEnd() {
        assertEquals(0f, positionAt(ltr, -40f))
        assertEquals(2f, positionAt(ltr, 400f))
        assertEquals(0f, positionAt(rtl, 400f))
        assertEquals(2f, positionAt(rtl, -40f))
    }
}
