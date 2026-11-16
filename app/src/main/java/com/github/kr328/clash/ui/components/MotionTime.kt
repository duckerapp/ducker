package com.github.kr328.clash.ui.components

import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.CoroutineContext

internal val CoroutineContext.motionScale: Float
    get() = this[MotionDurationScale]?.scaleFactor ?: 1f

/** Integrate frame deltas so changing the scale never rewinds an animation already in flight. */
internal class MotionTime(private var previous: Long = 0L, private val firstStep: Long = 0L) {
    private var elapsed = 0.0

    fun advance(frame: Long, scale: Float): Long {
        val delta = if (previous <= 0L || previous > frame) firstStep else frame - previous
        previous = frame
        // Zero is handled by each caller's terminal state, rather than an infinite time step.
        if (scale > 0f) elapsed += delta / scale.toDouble()
        return elapsed.toLong()
    }
}
