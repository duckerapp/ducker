package com.github.kr328.clash.ui.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetGestureTest {
    @Test
    fun `equivalent dp gestures agree at every density`() {
        for (density in listOf(1f, 1.5f, 2f, 3f, 4f)) {
            assertTrue(sheetShouldClose(60f * density, 600f * density, 600f * density / density))
            assertFalse(sheetShouldClose(60f * density, 600f * density, 400f * density / density))
            assertTrue(sheetShouldClose(240f * density, 600f * density, 0f))
            assertFalse(sheetShouldClose(240f * density, 600f * density, -600f))
        }
    }

    @Test
    fun `density three retains the accepted flick threshold and distance rule`() {
        assertFalse(sheetShouldClose(0f, 900f, 1500f / 3f))
        assertTrue(sheetShouldClose(0f, 900f, 1501f / 3f))
        assertFalse(sheetShouldClose(298f, 900f, -1500f / 3f))
        assertTrue(sheetShouldClose(298f, 900f, -1499f / 3f))
    }

    @Test
    fun `release keeps its velocity toward either endpoint`() {
        for (direction in listOf(-1f, 1f)) {
            val distance = 300f * direction
            val velocity = 1800f * direction
            val spec = sheetReleaseSpec(distance, velocity, 220)
            val dt = 0.0001f
            val initial = distance * spec.easing.transform(dt) / (dt * spec.durationMillis / 1000f)
            assertTrue(abs(initial - velocity) < 2f, "$initial != $velocity")
        }
    }

    @Test
    fun `release stays bounded monotone and finishes within existing time budget`() {
        for (distance in listOf(-600f, -5f, 0f, 5f, 600f)) {
            for (velocity in listOf(-100000f, -2000f, 0f, 2000f, 100000f)) {
                val spec = sheetReleaseSpec(distance, velocity, 220)
                assertTrue(spec.durationMillis in 1..220)
                var previous = 0f
                for (step in 0..1000) {
                    val value = spec.easing.transform(step / 1000f)
                    assertTrue(value >= previous - 0.000001f && value in 0f..1.000001f)
                    previous = value
                }
                assertEquals(0f, spec.easing.transform(0f))
                assertEquals(1f, spec.easing.transform(1f))
                assertTrue(1f - spec.easing.transform(0.9999f) < 0.000001f)
            }
        }
    }

    @Test
    fun `fast short releases finish sooner without reversing toward an opposing velocity`() {
        assertTrue(sheetReleaseSpec(100f, 5000f, 220).durationMillis < 220)
        assertEquals(220, sheetReleaseSpec(100f, -5000f, 220).durationMillis)
    }
}
