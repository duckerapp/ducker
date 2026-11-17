package com.github.kr328.clash.ui.components

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

/** Finger control followed by a finite handoff to the actual pager, never a wait for an obsolete page. */
internal class DockDrag(private val scope: CoroutineScope) {
    private var finger by mutableStateOf(false)
    private var center by mutableFloatStateOf(0f)
    private var remainder by mutableFloatStateOf(0f)
    private var motion: Job? = null
    private var generation = 0L

    val dragging: Boolean get() = finger

    fun center(pagerCenter: Float): Float = if (finger) center else pagerCenter + remainder

    fun begin(pagerCenter: Float) {
        cancel()
        center = pagerCenter
        finger = true
    }

    fun move(target: Float) {
        if (!finger) return
        supersede()
        motion = scope.launch {
            animate(center, target, animationSpec = spring(dampingRatio = 0.8f, stiffness = 1400f)) { value, _ ->
                center = value
            }
        }
    }

    fun release(pagerCenter: Float) {
        if (!finger) return
        supersede()
        val ticket = generation
        remainder = center - pagerCenter
        finger = false
        motion = scope.launch {
            try {
                animate(remainder, 0f, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    remainder = value
                }
            } finally {
                if (generation == ticket) remainder = 0f
            }
        }
    }

    fun cancel() {
        supersede()
        finger = false
        remainder = 0f
    }

    private fun supersede() {
        generation++
        motion?.cancel()
        motion = null
    }
}
