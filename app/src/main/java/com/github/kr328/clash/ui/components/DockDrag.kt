package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The glass dock's lens: led by the finger, or travelling to a tapped tab on its own time, then handed to the actual
 * pager with a finite residual, never a wait for an obsolete page.
 */
internal class DockDrag(private val scope: CoroutineScope) {
    private var owned by mutableStateOf(false)
    private val follow = Animatable(0f)
    private var remainder by mutableFloatStateOf(0f)
    private var motion: Job? = null
    private var travel: Job? = null
    private var generation = 0L

    /** The finger leads the lens. */
    var dragging by mutableStateOf(false)
        private set

    /** How fast the lens moves, pixels a second: it stretches with it. */
    val speed: Float get() = if (owned) abs(follow.velocity) else 0f

    fun center(pagerCenter: Float): Float = if (owned) follow.value else pagerCenter + remainder

    fun begin(from: Float) {
        cancel()
        owned = true
        dragging = true
        motion = scope.launch { follow.snapTo(from) }
    }

    fun move(target: Float) {
        if (!dragging) return
        // One spring chasing the finger: a new target keeps the speed it has. Restarting from rest on every
        // event (8 ms apart at 120 Hz) left the lens trembling in place behind the finger.
        motion = scope.launch { follow.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 900f)) }
    }

    fun release(pagerCenter: Float) {
        if (!dragging) return
        dragging = false
        handOff(pagerCenter, follow.velocity)
    }

    /**
     * A tap: the lens lifts off [from], flows to [to] with a little overshoot and settles; then the pager, wherever it
     * is by then, takes it over. Slower than the page on purpose: the switch is the dock's moment to be seen.
     */
    fun travel(from: Float, to: Float, pagerCenter: () -> Float) {
        cancel()
        owned = true
        val ticket = generation
        val job = scope.launch {
            follow.snapTo(from)
            follow.animateTo(to, spring(dampingRatio = TRAVEL_DAMPING, stiffness = TRAVEL_STIFFNESS))
            if (generation == ticket) handOff(pagerCenter(), 0f)
        }
        travel = job
        motion = job
    }

    /** Waits for a tap's travel to arrive; returns at once without one. */
    suspend fun arrived() {
        travel?.join()
    }

    fun cancel() {
        supersede()
        owned = false
        dragging = false
        remainder = 0f
    }

    private fun handOff(pagerCenter: Float, velocity: Float) {
        supersede()
        val ticket = generation
        remainder = follow.value - pagerCenter
        owned = false
        motion = scope.launch {
            try {
                animate(remainder, 0f, velocity, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    remainder = value
                }
            } finally {
                if (generation == ticket) remainder = 0f
            }
        }
    }

    private fun supersede() {
        generation++
        motion?.cancel()
        motion = null
        travel = null
    }
}

// About half a second to settle, with a slight overshoot: a drop of glass, not a slide.
private const val TRAVEL_DAMPING = 0.72f
private const val TRAVEL_STIFFNESS = 260f
