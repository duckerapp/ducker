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

    @Test fun `release cannot wait for a page superseded by a tap or swipe`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.begin(50f)
            drag.move(250f)
            frames.advance(20)
            drag.release(50f)
            // The requested page is never reached. The pager moves elsewhere instead.
            frames.advance(150)
            assertEquals(150f, drag.center(150f), 0.001f)
            assertFalse(drag.dragging)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `new tap cancels handoff and follows actual pager immediately`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.begin(50f)
            drag.move(250f)
            frames.advance(12)
            drag.release(50f)
            frames.advance(2)
            drag.cancel()
            assertEquals(50f, drag.center(50f))
            frames.advance(150)
            assertEquals(250f, drag.center(250f))
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `catching and reversing an old settle retains the new gesture`() {
        Frames(1f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.begin(50f)
            drag.move(250f)
            frames.advance(15)
            drag.release(50f)
            frames.advance(2)
            drag.begin(150f)
            drag.move(50f)
            frames.advance(100)
            assertTrue(drag.dragging)
            assertEquals(50f, drag.center(250f), 0.001f)
            drag.release(150f)
            frames.advance(150)
            assertEquals(150f, drag.center(150f), 0.001f)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `zero motion leaves no settle or frozen selection`() {
        Frames(0f).use { frames ->
            val drag = DockDrag(frames.scope)
            drag.begin(250f)
            drag.move(50f)
            frames.advance(2)
            drag.release(250f)
            frames.advance(3)
            assertEquals(150f, drag.center(150f))
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
