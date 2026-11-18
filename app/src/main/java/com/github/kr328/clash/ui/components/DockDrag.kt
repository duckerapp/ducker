package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The one owner of the glass dock's lens place (F-530): led by the finger while it drags; sent to a tab — tapped, or
 * the one a drag let go over — it flies there on its own spring, ahead of the page as iOS's (~0.15 s; the owner, 18.11,
 * let go of the lens riding on the page), and waits there for the page; otherwise it is wherever the page is.
 */
internal class DockDrag(private val scope: CoroutineScope) {
    private var owned by mutableStateOf(false)
    private val follow = Animatable(0f)
    private var motion: Job? = null
    private var generation = 0L

    /** The finger leads the lens. */
    var dragging by mutableStateOf(false)
        private set

    /** The lens is on its way to a tab. */
    var flying by mutableStateOf(false)
        private set

    /** How fast the lens moves, pixels a second: it stretches with it. */
    val speed: Float get() = if (owned) abs(follow.velocity) else 0f

    /** Neither the finger nor a flight moves the lens: it may settle back into the veil. */
    val resting: Boolean get() = !dragging && !flying

    fun center(pagerCenter: Float): Float = if (owned) follow.value else pagerCenter

    fun begin(from: Float) {
        supersede()
        owned = true
        dragging = true
        flying = false
        motion = scope.launch { follow.snapTo(from) }
    }

    fun move(target: Float) {
        if (!dragging) return
        // One spring chasing the finger: a new target keeps the speed it has. Restarting from rest on every
        // event (8 ms apart at 120 Hz) left the lens trembling in place behind the finger.
        motion = scope.launch { follow.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 900f)) }
    }

    /**
     * Sends the lens from [from] (or from where it is, if it is already led) to [to] ahead of the page, then hands it
     * back to the page once [pagerCenter] reaches it — or comes to rest elsewhere (a swipe took the page), when it
     * flies over to the page.
     */
    fun fly(from: Float, to: Float, pagerCenter: () -> Float) {
        val led = owned
        supersede()
        val ticket = generation
        owned = true
        dragging = false
        flying = true
        motion = scope.launch {
            try {
                if (!led) follow.snapTo(from)
                follow.animateTo(to, FLY_SPRING)
            } finally {
                if (generation == ticket) flying = false
            }
            var last = pagerCenter()
            var lastTime = withFrameNanos { it }
            var still = 0L
            while (generation == ticket) {
                val now = withFrameNanos { it }
                val at = pagerCenter()
                if (abs(at - to) < LANDED_PX) break
                still = if (abs(at - last) < 0.5f) still + (now - lastTime) else 0L
                if (still > STILL_NS) {
                    flying = true
                    try {
                        follow.animateTo(at, FLY_SPRING)
                    } finally {
                        if (generation == ticket) flying = false
                    }
                    break
                }
                last = at
                lastTime = now
            }
            if (generation == ticket) owned = false
        }
    }

    fun cancel() {
        supersede()
        owned = false
        dragging = false
        flying = false
    }

    private fun supersede() {
        generation++
        motion?.cancel()
        motion = null
    }
}

/** The lens's flight to a tab: iOS's takes ~0.15 s and barely passes its place. */
private val FLY_SPRING = spring<Float>(dampingRatio = 0.75f, stiffness = 600f)
// Within this many pixels of the tab the page has come: the lens is the page's again.
private const val LANDED_PX = 1f
// The page has rested this long somewhere other than the tab: the flight is over.
private const val STILL_NS = 150_000_000L
