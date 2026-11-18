package com.github.kr328.clash.ui.components

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassOwnershipTest {
    private class Frames(scale: Float) : AutoCloseable {
        private val duration = object : MotionDurationScale { override val scaleFactor = scale }
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined + clock + duration)
        private var now = 1_000_000_000L
        fun advance(count: Int = 1) = repeat(count) { clock.sendFrame(now); now += 16_000_000L }
        override fun close() = scope.cancel()
    }

    @Test fun `a released drag flies to its tab and waits there for the page`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            var page = 50f
            drag.begin(50f)
            drag.move(180f)
            frames.advance(20)
            drag.fly(0f, 150f) { page }
            // The page on its way all the while: never still, it does not call the lens back.
            repeat(40) {
                page += 1f
                frames.advance()
            }
            // Landed ahead of the page: there, whatever the page says, and free to settle.
            assertEquals(150f, drag.center(page), 0.5f)
            assertTrue(drag.resting)
            page = 150f
            frames.advance(2)
            // The page has come: the lens is the page's again.
            assertEquals(90f, drag.center(90f))
            assertFalse(drag.dragging)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `a page resting elsewhere takes the lens back`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.fly(0f, 250f) { 100f }
            // The flight out, the page's rest, the flight back.
            frames.advance(120)
            assertEquals(100f, drag.center(100f), 0.5f)
            frames.advance(5)
            assertEquals(70f, drag.center(70f))
            assertTrue(drag.resting)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `a new gesture takes the lens over from a flight`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.fly(50f, 250f) { 50f }
            frames.advance(2)
            drag.begin(drag.center(50f))
            drag.move(60f)
            frames.advance(60)
            assertTrue(drag.dragging)
            assertFalse(drag.resting)
            assertEquals(60f, drag.center(250f), 0.5f)
            drag.cancel()
            assertEquals(150f, drag.center(150f))
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `zero motion leaves no flight or frozen selection`() {
        Frames(0f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.begin(250f)
            drag.move(50f)
            frames.advance(2)
            drag.fly(0f, 150f) { 150f }
            frames.advance(3)
            assertEquals(120f, drag.center(120f))
            assertTrue(drag.resting)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `a tapped lens flies ahead of the page`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            var page = 0f
            var midSpeed = 0f
            drag.fly(0f, 200f) { page }
            repeat(12) {
                page += 5f
                frames.advance()
                if (it == 3) midSpeed = drag.speed
            }
            // ~0.19 s: the lens nearly there, the page a third of the way.
            assertTrue(drag.center(page) > 180f, "${drag.center(page)}")
            assertTrue(midSpeed > 500f)
            repeat(40) {
                page = minOf(200f, page + 5f)
                frames.advance()
            }
            assertEquals(0f, drag.speed)
            assertEquals(30f, drag.center(30f))
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `closing nested confirmation preserves editor progress`() {
        val blur = SheetGlass()
        val editor = Any()
        val confirmation = Any()
        blur.update(editor, 1f)
        blur.update(confirmation, 0.8f)
        blur.remove(confirmation)
        assertEquals(1f, blur.shown)
        blur.update(editor, 0.3f)
        assertEquals(0.3f, blur.shown)
        blur.remove(editor)
        assertEquals(0f, blur.shown)
    }

    @Test fun `hidden kept sheet releases only its own blur and can reopen`() {
        val blur = SheetGlass()
        val kept = Any()
        val editor = Any()
        blur.update(kept, 1f)
        blur.update(editor, 0.7f)
        blur.update(kept, 0f)
        assertEquals(0.7f, blur.shown)
        blur.remove(editor)
        assertEquals(0f, blur.shown)
        blur.update(kept, 0.5f)
        assertEquals(0.5f, blur.shown)
        blur.update(kept, 0f)
        assertEquals(0f, blur.shown)
    }

    @Test fun `the screen's still pictures stay wanted while any glass sheet wants them`() {
        val sheets = SheetGlass()
        val kept = Any()
        val confirmation = Any()
        sheets.want(kept, true)
        sheets.want(confirmation, true)
        sheets.want(confirmation, false)
        assertTrue(sheets.wanted)
        // A sheet that leaves the composition releases its want with its blur.
        sheets.remove(kept)
        assertFalse(sheets.wanted)
        sheets.want(kept, false)
        assertFalse(sheets.wanted)
    }

    @Test fun `a sheet sees another open one but not itself or one leaving`() {
        val sheets = SheetGlass()
        val editor = Any()
        val confirmation = Any()
        assertFalse(sheets.othersOpen(confirmation))
        sheets.want(editor, true)
        sheets.update(editor, 1f)
        assertTrue(sheets.othersOpen(confirmation))
        assertFalse(sheets.othersOpen(editor))
        // Leaving: still on screen, no longer under a new sheet.
        sheets.want(editor, false)
        assertFalse(sheets.othersOpen(confirmation))
    }

    @Test fun `host scopes and out of order disposal cannot clear another sheet`() {
        val first = SheetGlass()
        val second = SheetGlass()
        val a = Any()
        val b = Any()
        first.update(a, 0.8f)
        first.update(b, 1f)
        second.update(a, 0.4f)
        first.remove(a)
        first.remove(a)
        assertEquals(1f, first.shown)
        assertEquals(0.4f, second.shown)
        first.remove(b)
        assertEquals(0f, first.shown)
        assertEquals(0.4f, second.shown)
    }
}
