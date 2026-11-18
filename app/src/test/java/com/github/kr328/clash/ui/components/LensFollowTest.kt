package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class LensFollowTest {
    /** A frame every 16 ms, letting the other coroutines run in between. */
    private class Frames : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            time += 16_000_000L
            return onFrame(time)
        }
    }

    @Test
    fun aFingerLiftedBeforeTheSwellsFirstFrameStillSwellsTheLens() = runBlocking(Frames()) {
        val lens = Animatable(0f)
        val touch = LensTouch()
        var highest = 0f
        val watch = launch {
            while (isActive) {
                highest = maxOf(highest, lens.value)
                yield()
            }
        }
        touch.finger(true)
        // The switch's effect starts on the touch and is restarted by the lift before the lens has moved.
        val first = launch(start = CoroutineStart.UNDISPATCHED) { lens.follow(touch, motion = true) }
        touch.finger(false)
        first.cancelAndJoin()
        lens.follow(touch, motion = true)
        watch.cancelAndJoin()
        assertEquals(1f, highest)
        assertEquals(0f, lens.value)
    }
}
