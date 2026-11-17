package com.github.kr328.clash.ui.components

import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
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
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A switch that only shows [checked]; the row around it takes taps and speaks for it. [onDrag]: the thumb dragged by a
 * finger sets it. The glass style (D-541): Kyant's liquid toggle (AndroidLiquidGlass, Apache-2.0, see LiquidGlass.kt) —
 * a track in the accent when on, a white thumb under a clear lens of the dock's kind that swells under a finger and
 * settles back on release, stretched by its speed (the owner, 18.11). Elsewhere Material's switch.
 */
@Composable
internal fun DuckerSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onDrag: ((Boolean) -> Unit)? = null,
    /** The row's presses: held, the lens over the thumb swells. */
    pressed: InteractionSource? = null,
) {
    val glass = LocalGlassBackdrop.current != null || LocalFrosted.current
    val bubble = remember(glass) { if (glass) bubbleShader() else null }
    if (bubble == null) Switch(checked = checked, onCheckedChange = null, modifier = modifier)
    else GlassToggle(checked, bubble, modifier, onDrag, pressed)
}

@Composable
private fun GlassToggle(checked: Boolean, bubble: GlassBubble, modifier: Modifier, onDrag: ((Boolean) -> Unit)?, pressed: InteractionSource?) {
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
    // The lens over the white thumb: 0 — its size, 1 — swollen under a finger.
    val held = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val drag = rememberUpdatedState(onDrag)
    val under = rememberPaneUnder()
    val at = remember { floatArrayOf(0f, 0f) }
    val touch = remember { LensTouch() }
    LaunchedEffect(pressed) { pressed?.let { touch.watch(it) } }
    LaunchedEffect(touch.count, touch.down, motion) { held.follow(touch, motion) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (position.targetValue == target && position.value == target) return@LaunchedEffect
        if (!motion) {
            position.snapTo(target)
            return@LaunchedEffect
        }
        position.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 420f))
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val dragState = rememberDraggableState { delta ->
        val travel = with(density) { (TRACK_WIDTH - THUMB_WIDTH - PAD * 2).toPx() }
        val step = (if (rtl) -delta else delta) / travel
        scope.launch { position.snapTo((position.value + step).coerceIn(0f, 1f)) }
    }

    Box(
        modifier.size(TRACK_WIDTH, TRACK_HEIGHT)
            .onGloballyPositioned { val p = it.positionInRoot(); at[0] = p.x; at[1] = p.y }
            .fingerOn(touch)
            .then(
                if (onDrag != null) Modifier.draggable(
                    dragState,
                    Orientation.Horizontal,
                    onDragStopped = {
                        val now = position.value >= 0.5f
                        if (now != checked) drag.value?.invoke(now)
                        else position.animateTo(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                    },
                ) else Modifier,
            )
            .drawBehind {
                val p = position.value
                val radius = size.height / 2f
                val trackColor = lerp(off, on, p)
                val trackCorner = CornerRadius(radius)
                drawRoundRect(trackColor, cornerRadius = trackCorner)

                val pad = PAD.toPx()
                val thumbW = THUMB_WIDTH.toPx()
                val thumbH = THUMB_HEIGHT.toPx()
                val travel = size.width - thumbW - 2 * pad
                val x = pad + thumbW / 2f + travel * p
                val center = Offset(if (rtl) size.width - x else x, size.height / 2f)
                val thumb = lerp(Color.White, thumbOn, p)
                val shadowDrop = 1.dp.toPx()
                drawThumb(center, thumbW / 2f, thumbH / 2f, thumb, shadowDrop)

                // Over the white thumb, always, a lens of the dock's kind: swollen under a finger, stretched by its speed.
                val speed = (abs(position.velocity) * 0.04f).coerceAtMost(0.22f)
                val swell = 1f + (LENS_SWELL - 1f) * held.value
                val halfW = thumbW / 2f * swell * (1f + speed)
                val halfH = thumbH / 2f * swell * (1f - speed * 0.5f)
                val origin = Offset(at[0], at[1])
                drawGlassBubble(layer, bubble, center, halfW, halfH, 1f, base, zoom = 1f + LENS_ZOOM * held.value) {
                    under(origin)
                    drawRoundRect(trackColor, cornerRadius = trackCorner)
                    drawThumb(center, thumbW / 2f, thumbH / 2f, thumb, shadowDrop)
                }
            },
    )
}

/**
 * The fingers on a control and on the row that speaks for it. [count] counts touches: a row's short tap reports its press
 * and release together, so [down] may never be seen true, and the lens must still swell once.
 */
private class LensTouch {
    var count by mutableIntStateOf(0)
    private var own by mutableStateOf(false)
    private var row by mutableStateOf(false)
    val down get() = own || row
    var seen = 0

    fun finger(on: Boolean) {
        if (on) count++
        own = on
    }

    suspend fun watch(source: InteractionSource) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> { count++; row = true }
                is PressInteraction.Release, is PressInteraction.Cancel -> row = false
            }
        }
    }
}

/** Swells while a finger is down, settles back after; a touch already gone still swells fully once. */
private suspend fun Animatable<Float, *>.follow(touch: LensTouch, motion: Boolean) {
    val touched = touch.count != touch.seen
    touch.seen = touch.count
    if (!motion) {
        snapTo(0f)
    } else if (touch.down) {
        animateTo(1f, tween(LENS_IN_MS))
    } else {
        if (value < 1f && (value > 0f || touched)) animateTo(1f, tween((LENS_IN_MS * (1f - value)).toInt()))
        animateTo(0f, tween(LENS_OUT_MS))
    }
}

/** A finger on this, from its touch until it lifts, whatever gesture takes the touch meanwhile. */
private fun Modifier.fingerOn(touch: LensTouch): Modifier = pointerInput(touch) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touch.finger(true)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            touch.finger(false)
        }
    }
}

/** The white thumb, its own size whatever the lens over it does, on a faint shadow. */
private fun DrawScope.drawThumb(center: Offset, halfW: Float, halfH: Float, color: Color, drop: Float) {
    val corner = CornerRadius(halfH)
    drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(center.x - halfW, center.y - halfH + drop), Size(2 * halfW, 2 * halfH), corner)
    drawRoundRect(color, Offset(center.x - halfW, center.y - halfH), Size(2 * halfW, 2 * halfH), corner)
}

private val TRACK_WIDTH = 64.dp
private val TRACK_HEIGHT = 28.dp
private val THUMB_WIDTH = 40.dp
private val THUMB_HEIGHT = 24.dp
private val PAD = 2.dp
// The lens grows, its picture barely: the thumb keeps room inside it (the owner, 18.11).
private const val LENS_SWELL = 1.6f
private const val LENS_ZOOM = 0.06f
private const val LENS_IN_MS = 120
private const val LENS_OUT_MS = 220

/**
 * A slider with Material's steps, speech and remote keys. The glass style (D-541): Kyant's liquid slider — a thin track in
 * the accent up to the value, a white thumb under a clear lens of the dock's kind that swells while held or dragged,
 * magnifying the thumb and the track under it. Elsewhere Material's look in the app's colours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuckerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlassBackdrop.current != null || LocalFrosted.current
    val bubble = remember(glass) { if (glass) bubbleShader() else null }
    if (bubble == null) {
        Slider(
            value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                activeTickColor = scheme.onPrimary,
                inactiveTrackColor = scheme.surfaceContainerHighest,
                inactiveTickColor = scheme.onSurfaceVariant,
            ),
            modifier = modifier,
        )
        return
    }

    val dark = scheme.background.luminance() < 0.5f
    val off = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val on = chosenFill()
    val base = scheme.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { MutableInteractionSource() }
    val touch = remember { LensTouch() }
    val motion = rememberCoroutineScope().coroutineContext.motionScale > 0f
    // The lens over the thumb: 0 — its size, 1 — swollen under a finger.
    val lens = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val under = rememberPaneUnder()
    val at = remember { floatArrayOf(0f, 0f) }

    LaunchedEffect(touch.count, touch.down, motion) { lens.follow(touch, motion) }

    // The track in the thumb's own place, for the lens: the accent to its middle, the rest after it.
    fun DrawScope.track(start: Float, end: Float, split: Float, height: Float) {
        val top = size.height / 2f - height / 2f
        val corner = CornerRadius(height / 2f)
        drawRoundRect(off, Offset(start, top), Size(end - start, height), corner)
        val (from, to) = if (rtl) split to end else start to split
        if (to > from) drawRoundRect(on, Offset(from, top), Size(to - from, height), corner)
    }

    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled,
        interactionSource = interaction,
        // A finger anywhere on it: Material reports a press only once it knows it is no scroll, a tap all at once.
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA).then(if (enabled) Modifier.fingerOn(touch) else Modifier),
        thumb = {
            Box(
                Modifier.size(THUMB_WIDTH, THUMB_HEIGHT)
                    .onGloballyPositioned { val p = it.positionInRoot(); at[0] = p.x; at[1] = p.y }
                    .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val shadowDrop = 1.dp.toPx()
                    drawThumb(center, size.width / 2f, size.height / 2f, Color.White, shadowDrop)
                    // Over the white thumb, always, a lens of the dock's kind, swollen under a finger.
                    val swell = 1f + (LENS_SWELL - 1f) * lens.value
                    val halfW = size.width / 2f * swell
                    val halfH = size.height / 2f * swell
                    val origin = Offset(at[0], at[1])
                    val thick = SLIDER_TRACK.toPx()
                    val width = size.width
                    val height = size.height
                    drawGlassBubble(layer, bubble, center, halfW, halfH, 1f, base, zoom = 1f + LENS_ZOOM * lens.value) {
                        under(origin)
                        track(-width, width * 2f, center.x, thick)
                        drawThumb(center, width / 2f, height / 2f, Color.White, shadowDrop)
                    }
                },
            )
        },
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(THUMB_HEIGHT).drawBehind {
                    val range = state.valueRange
                    val fraction = if (range.endInclusive > range.start) {
                        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    } else 0f
                    val split = if (rtl) size.width * (1f - fraction) else size.width * fraction
                    track(0f, size.width, split, SLIDER_TRACK.toPx())
                },
            )
        },
    )
}

private val SLIDER_TRACK = 6.dp
private const val DISABLED_ALPHA = 0.5f
