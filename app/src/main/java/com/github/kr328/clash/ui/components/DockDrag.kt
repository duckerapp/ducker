package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The glass dock's lens: led by the finger, then handed to the actual pager with a finite residual; after a tap it
 * rides on the pager itself. Never a wait for an obsolete page.
 */
internal class DockDrag(private val scope: CoroutineScope) {
    private var owned by mutableStateOf(false)
    private val follow = Animatable(0f)
    private var remainder by mutableFloatStateOf(0f)
    private var motion: Job? = null
    private var generation = 0L
    private var aim: Int? = null
    private var pagerSpeed by mutableFloatStateOf(0f)

    /** The finger leads the lens. */
    var dragging by mutableStateOf(false)
        private set

    /** How fast the lens moves, pixels a second: it stretches with it. */
    val speed: Float get() = if (owned) abs(follow.velocity) else pagerSpeed

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

    /** A tap sent the pager to [tab]: the lens rides on the pager to it, see [ride]. */
    fun aim(tab: Int) {
        cancel()
        aim = tab
    }

    /**
     * Rides the lens on the pager toward the tapped tab: it is wherever the page is, so the two cannot drift apart,
     * and [speed] follows the page's own speed for the glass to stretch with. Returns the tab once the page has
     * landed on it, or null when another gesture took the dock or the page came to rest elsewhere.
     */
    suspend fun ride(position: () -> Float, slot: Float): Int? {
        // A tap's click lands in the same input pass as its release; allow one frame for it anyway.
        val tab = aim ?: run { withFrameNanos { }; aim } ?: return null
        var last = position()
        var lastTime = withFrameNanos { it }
        var still = 0L

        try {
            while (aim == tab) {
                val now = withFrameNanos { it }
                val at = position()
                val seconds = (now - lastTime) / 1e9f

                if (seconds > 0f) pagerSpeed += (abs(at - last) * slot / seconds - pagerSpeed) * SPEED_SMOOTHING
                if (abs(at - tab) < LANDED) return tab

                // The page went elsewhere (a swipe took it, or it was already there): stop once it rests.
                still = if (at == last) still + (now - lastTime) else 0L
                if (still > STILL_NS) return null

                last = at
                lastTime = now
            }

            return null
        } finally {
            pagerSpeed = 0f
            if (aim == tab) aim = null
        }
    }

    fun cancel() {
        supersede()
        aim = null
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
    }
}

// Within this much of a page the lens has landed: a few pixels of the dock.
private const val LANDED = 0.004f
// The page has rested this long somewhere else than the tapped tab: the ride is over.
private const val STILL_NS = 150_000_000L
// Each frame's speed counts this much: frame times vary, and the glass should not flicker with them.
private const val SPEED_SMOOTHING = 0.5f
