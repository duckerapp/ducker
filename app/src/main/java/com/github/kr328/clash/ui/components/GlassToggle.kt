package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A switch that only shows [checked]; the row around it takes taps and speaks for it. [onDrag]: the thumb dragged by a
 * finger sets it. The glass style (D-541): Kyant's liquid toggle (AndroidLiquidGlass, Apache-2.0, see LiquidGlass.kt) —
 * a track in the accent when on, a thumb that turns into a clear lens of the dock's kind while it travels or is held,
 * stretched by its speed. Elsewhere Material's switch.
 */
@Composable
internal fun DuckerSwitch(checked: Boolean, modifier: Modifier = Modifier, onDrag: ((Boolean) -> Unit)? = null) {
    val glass = LocalGlassBackdrop.current != null || LocalFrosted.current
    val bubble = remember(glass) { if (glass) bubbleShader() else null }
    if (bubble == null) Switch(checked = checked, onCheckedChange = null, modifier = modifier)
    else GlassToggle(checked, bubble, modifier, onDrag)
}

@Composable
private fun GlassToggle(checked: Boolean, bubble: GlassBubble, modifier: Modifier, onDrag: ((Boolean) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val off = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val on = chosenFill()
    // White on the light theme's black track, dark on the dark theme's white one.
    val thumbOn = scheme.onPrimaryContainer
    val base = scheme.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = scope.coroutineContext.motionScale > 0f
    val density = LocalDensity.current

    val position = remember { Animatable(if (checked) 1f else 0f) }
    // 0 — the white thumb, 1 — the clear lens.
    val lens = remember { Animatable(0f) }
    val held = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val drag = rememberUpdatedState(onDrag)

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (position.targetValue == target && position.value == target) return@LaunchedEffect
        if (!motion) {
            position.snapTo(target)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { lens.animateTo(1f, tween(LENS_IN_MS)) }
            position.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 420f))
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lens.animateTo(0f, tween(LENS_OUT_MS))
    }

    val dragState = rememberDraggableState { delta ->
        val travel = with(density) { (TRACK_WIDTH - THUMB_WIDTH - PAD * 2).toPx() }
        val step = (if (rtl) -delta else delta) / travel
        scope.launch { position.snapTo((position.value + step).coerceIn(0f, 1f)) }
    }

    Box(
        modifier.size(TRACK_WIDTH, TRACK_HEIGHT)
            .then(
                if (onDrag != null) Modifier.draggable(
                    dragState,
                    Orientation.Horizontal,
                    onDragStarted = { if (motion) launch { held.animateTo(1f, tween(LENS_IN_MS)) } },
                    onDragStopped = {
                        val now = position.value >= 0.5f
                        launch { held.animateTo(0f, tween(LENS_OUT_MS)) }
                        if (now != checked) drag.value?.invoke(now)
                        else position.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                    },
                ) else Modifier,
            )
            .drawBehind {
                val p = position.value
                val presence = maxOf(lens.value, held.value)
                val radius = size.height / 2f
                drawRoundRect(lerp(off, on, p), cornerRadius = CornerRadius(radius))

                val pad = PAD.toPx()
                val thumbW = THUMB_WIDTH.toPx()
                val thumbH = THUMB_HEIGHT.toPx()
                val travel = size.width - thumbW - 2 * pad
                val x = pad + thumbW / 2f + travel * p
                val center = Offset(if (rtl) size.width - x else x, size.height / 2f)
                // Stretched along its way by its speed, as the dock's lens.
                val speed = (abs(position.velocity) * 0.04f).coerceAtMost(0.22f)
                val swell = 1f + (LENS_SWELL - 1f) * presence
                val halfW = thumbW / 2f * swell * (1f + speed)
                val halfH = thumbH / 2f * swell * (1f - speed * 0.5f)

                if (presence < 0.99f) {
                    val thumb = lerp(Color.White, thumbOn, p).copy(alpha = 1f - presence)
                    val corner = CornerRadius(halfH)
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.08f * (1f - presence)),
                        Offset(center.x - halfW, center.y - halfH + 1.dp.toPx()), Size(2 * halfW, 2 * halfH), corner,
                    )
                    drawRoundRect(thumb, Offset(center.x - halfW, center.y - halfH), Size(2 * halfW, 2 * halfH), corner)
                }

                if (presence > 0.01f) {
                    // Only the track, magnified: around it the lens is clear and the card shows through.
                    drawGlassBubble(layer, bubble, center, halfW, halfH, presence, base) {
                        drawRoundRect(lerp(off, on, p), cornerRadius = CornerRadius(radius))
                    }
                }
            },
    )
}

private val TRACK_WIDTH = 64.dp
private val TRACK_HEIGHT = 28.dp
private val THUMB_WIDTH = 40.dp
private val THUMB_HEIGHT = 24.dp
private val PAD = 2.dp
private const val LENS_SWELL = 1.5f
private const val LENS_IN_MS = 120
private const val LENS_OUT_MS = 220
