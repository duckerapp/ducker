package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionScaleTest {
    private class Scale(override var scaleFactor: Float) : MotionDurationScale

    private class Frames(scale: Float) : AutoCloseable {
        val scale = Scale(scale)
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined + clock + this.scale)
        private var now = 1_000_000_000L
        fun frame() { clock.sendFrame(now); now += 16_000_000L }
        override fun close() = scope.cancel()
    }

    @Test fun `time integrates scale changes without rewinding`() {
        val time = MotionTime()
        assertEquals(0L, time.advance(1_000_000_000L, 1f))
        assertEquals(16_000_000L, time.advance(1_016_000_000L, 1f))
        assertEquals(24_000_000L, time.advance(1_032_000_000L, 2f))
        assertEquals(56_000_000L, time.advance(1_048_000_000L, 0.5f))
        assertEquals(56_000_000L, time.advance(1_064_000_000L, 0f))
        assertEquals(72_000_000L, time.advance(1_080_000_000L, 1f))
        assertEquals(1f, EmptyCoroutineContext.motionScale)
    }

    @Test fun `overscroll keeps normal spring samples at scale one`() {
        Frames(1f).use { frames ->
            val bounce = pulled(frames.scope)
            val initial = bounce.offset
            val spring = androidx.compose.animation.core.TargetBasedAnimation(
                androidx.compose.animation.core.spring<Float>(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                Float.VectorConverter,
                initial, 0f,
            )
            runBlocking { bounce.applyToFling(Velocity.Zero) { it } }
            repeat(12) { index ->
                frames.frame()
                val played = 8_333_333L + index * 16_000_000L
                assertEquals(spring.getValueFromNanos(played), bounce.offset, 0.0001f)
            }
        }
    }

    @Test fun `zero scale releases direct pull and still notifies parents`() {
        Frames(0f).use { frames ->
            val bounce = pulled(frames.scope)
            assertEquals(25f, bounce.offset)
            var notified = false
            runBlocking { bounce.applyToFling(Velocity.Zero) { notified = true; it } }
            frames.frame()
            assertTrue(notified)
            assertEquals(0f, bounce.offset)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `disabling motion during overscroll ends it on the next frame`() {
        Frames(2f).use { frames ->
            val bounce = pulled(frames.scope)
            runBlocking { bounce.applyToFling(Velocity.Zero) { it } }
            repeat(6) { frames.frame() }
            assertTrue(bounce.offset > 0f)
            frames.scale.scaleFactor = 0f
            frames.frame()
            assertEquals(0f, bounce.offset)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `zero scale stops the outward phase without scheduling a return phase`() {
        Frames(1f).use { frames ->
            val bounce = BounceOverscroll(frames.scope)
            runBlocking { bounce.applyToFling(Velocity(0f, 1000f)) { Velocity.Zero } }
            frames.frame()
            assertTrue(bounce.offset > 0f)
            frames.scale.scaleFactor = 0f
            frames.frame()
            assertEquals(0f, bounce.offset)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    @Test fun `finger catching overscroll cancels the old scaled return`() {
        Frames(2f).use { frames ->
            val bounce = pulled(frames.scope)
            runBlocking { bounce.applyToFling(Velocity.Zero) { it } }
            frames.frame()
            bounce.applyToScroll(Offset(0f, -2f), NestedScrollSource.UserInput) { Offset.Zero }
            val caught = bounce.offset
            frames.scale.scaleFactor = 0f
            repeat(3) { frames.frame() }
            assertEquals(caught, bounce.offset)
            runBlocking { bounce.applyToFling(Velocity.Zero) { it } }
            frames.frame()
            assertEquals(0f, bounce.offset)
        }
    }

    @Test fun `TV scroll snaps at zero and accepts another request`() {
        Frames(0f).use { frames ->
            var rest = 120f
            var total = 0f
            val state = ScrollableState { it.also { delta -> total += delta; rest -= delta } }
            var job = frames.scope.launch { state.springScroll({ rest }) }
            repeat(3) { frames.frame() }
            assertEquals(120f, total)
            assertTrue(job.isCompleted)
            rest = -40f
            job = frames.scope.launch { state.springScroll({ rest }) }
            repeat(3) { frames.frame() }
            assertEquals(80f, total)
            assertTrue(job.isCompleted)
        }
    }

    @Test fun `TV scroll slows at two and zero completes a retargeted scroll`() {
        fun progress(scale: Float): Float = Frames(scale).use { frames ->
            var rest = 300f
            val state = ScrollableState { it.also { delta -> rest -= delta } }
            val job = frames.scope.launch { state.springScroll({ rest }) }
            repeat(5) { frames.frame() }
            val progress = 300f - rest
            rest = -80f
            frames.scale.scaleFactor = 0f
            repeat(3) { frames.frame() }
            assertEquals(0f, rest)
            assertTrue(job.isCompleted)
            progress
        }
        assertTrue(progress(1f) > progress(2f))
    }

    @Test fun `TV scroll stops at an edge with motion disabled`() {
        Frames(0f).use { frames ->
            var consumed = 0f
            val state = ScrollableState { consumed = it.coerceIn(-10f, 10f); consumed }
            val job = frames.scope.launch { state.springScroll({ 100f }) }
            frames.frame()
            assertEquals(10f, consumed)
            assertTrue(job.isCompleted)
            assertFalse(frames.clock.hasAwaiters)
        }
    }

    private fun pulled(scope: CoroutineScope) = BounceOverscroll(scope).also {
        it.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput) { Offset.Zero }
    }
}
