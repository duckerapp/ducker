package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import kotlin.math.abs

/** Speeds are in dp/s so a display-density change cannot change the release decision. */
internal fun sheetShouldClose(offset: Float, height: Float, velocityDp: Float): Boolean =
    velocityDp > 500f || (velocityDp > -500f && offset > height * 0.33f)

/** A bounded Hermite release: retain velocity toward the target without bounce or overshoot. */
internal fun sheetReleaseSpec(distance: Float, velocity: Float, maximumMillis: Int): TweenSpec<Float> {
    val travel = abs(distance)
    val toward = (if (distance < 0f) -velocity else velocity).coerceAtLeast(0f)
    // A slope above three would reverse this curve before its endpoint. Shorten its
    // duration instead; only a sub-millisecond release needs its starting slope capped.
    val duration = if (toward > 0f) minOf(maximumMillis.toFloat(), 3000f * travel / toward)
        .toInt().coerceAtLeast(1) else maximumMillis
    val slope = if (travel > 0f) (toward * duration / (1000f * travel)).coerceIn(0f, 3f) else 0f
    return tween(duration, easing = Easing { t ->
        t * t * (3f - 2f * t) + slope * t * (1f - t) * (1f - t)
    })
}
