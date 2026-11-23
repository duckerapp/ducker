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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.composed

/**
 * A switch that only shows [checked]; the row around it takes taps and speaks for it. [onDrag]: the thumb dragged by a
 * finger sets it. The glass style (D-541), after iOS's switch (the owner's video, 18.11): a track in the accent when on;
 * a touch turns the white thumb into a clear lens 1.5× its size, taller than the track, that rides to the other end,
 * gives like rubber past it, and as it settles clouds over and turns back into the white thumb. Elsewhere Material's
 * switch.
 */
@Composable
internal fun DuckerSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onDrag: ((Boolean) -> Unit)? = null,
    /** The row's presses: held, the thumb turns into the lens. */
    pressed: InteractionSource? = null,
    enabled: Boolean = true,
) {
    val glass = LocalGlassBackdrop.current != null || LocalFrosted.current
    val lens = remember(glass) { if (glass) lensShader() else null }
    if (lens == null) Switch(checked = checked, onCheckedChange = null, modifier = modifier, enabled = enabled)
    else GlassToggle(checked, lens, modifier.alpha(if (enabled) 1f else DISABLED_ALPHA), onDrag.takeIf { enabled }, pressed, enabled)
}

@Composable
private fun GlassToggle(checked: Boolean, lens: GlassLens, modifier: Modifier, onDrag: ((Boolean) -> Unit)?, pressed: InteractionSource?, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val tuning = LocalGlassTuning.current
    val look = tuning.lens(GlassTuningGroup.Switch, if (dark) LensLook.Switch else LensLook.SwitchLight, !dark)
    val off = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val on = chosenFill()
    // White when on; off, grey on the dark theme, where a dark thumb on the light track read as a black blot (the owner,
    // 18.11), and white on the light one.
    val thumbOff = if (dark) THUMB_OFF_DARK else Color.White
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = scope.coroutineContext.motionScale > 0f
    // How far past an end the thumb can be pulled, in its travel.
    val give = TOGGLE_GIVE / (TRACK_WIDTH - SWITCH_THUMB_WIDTH - PAD * 2)

    // The thumb's place along its travel: 0 off, 1 on, a little past either while pulled.
    val position = remember { Animatable(if (checked) 1f else 0f) }
    // 0 — the white thumb; 1 — the lens, swollen under a finger.
    val held = remember { Animatable(0f) }
    // Clouds the lens: as the white thumb turns into it, and as it turns back.
    val frost = remember { Animatable(0f) }
    // 1 — the thumb's white; it clears as a touch turns the thumb into the lens and returns once the lens has settled.
    val fill = remember { Animatable(1f) }
    // The track's accent: it turns as the thumb sets off, along its ride; while dragged, as it passes the middle.
    val lit = remember { Animatable(if (checked) 1f else 0f) }
    var pulling by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val drag = rememberUpdatedState(onDrag)
    val under = rememberPaneUnder()
    val at = remember { floatArrayOf(0f, 0f) }
    val touch = remember { LensTouch() }
    val checkedNow = rememberUpdatedState(checked)
    LaunchedEffect(pressed, enabled) { if (enabled) pressed?.let { touch.watch(it) } }
    LaunchedEffect(touch.count, touch.down, motion) {
        if (!motion) {
            frost.snapTo(0f)
            fill.snapTo(1f)
            held.follow(touch, motion)
            return@LaunchedEffect
        }
        // As iOS's (the owner's second video, 18.11): the white clouds over at once into a frosted lens, which clears as
        // it rides and while held; home, it frosts and shrinks back to the thumb's size, and then the white returns.
        if (fill.value > 0.5f) frost.snapTo(1f)
        launch { fill.animateTo(0f, tween(FILL_OUT_MS)) }
        launch { frost.animateTo(0f, tween(FROST_CLEAR_MS)) }
        var whiten: Job? = null
        held.follow(touch, motion, out = SWITCH_LENS_OUT_MS) {
            // The lens settles as the thumb comes home, not after the spring's last wobble: it hung there big and clear
            // for ~280 ms where iOS's settles at once (the owner's video of ours beside iOS's, 18.11). A row's tap sets
            // the switch a frame or two after the finger lifts: wait for that before asking where home is.
            repeat(2) { withFrameNanos { } }
            snapshotFlow { abs(position.value - if (checkedNow.value) 1f else 0f) < HOME }.first { it }
            launch { frost.animateTo(1f, tween(SWITCH_LENS_OUT_MS)) }
            // The white comes in with the shrink, quickly at first, then slowly to the full white. Coming in after the
            // shrink, it held at a milky grey and then jumped to white in four frames (the owner, slowed down, 18.11);
            // iOS's rises along the shrink and creeps to white over ~0.3 s.
            whiten = launch { fill.animateTo(1f, tween(FILL_IN_MS, easing = LinearOutSlowInEasing)) }
        }
        if (touch.down) return@LaunchedEffect
        whiten?.join() ?: fill.animateTo(1f, tween(FILL_IN_MS, easing = LinearOutSlowInEasing))
        frost.snapTo(0f)
    }
    LaunchedEffect(motion) {
        snapshotFlow { if (pulling) position.value >= 0.5f else checkedNow.value }.collectLatest { now ->
            val target = if (now) 1f else 0f
            if (motion) lit.animateTo(target, tween(TRACK_FADE_MS, easing = FastOutSlowInEasing)) else lit.snapTo(target)
        }
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (position.targetValue == target && position.value == target) return@LaunchedEffect
        if (!motion) {
            position.snapTo(target)
            return@LaunchedEffect
        }
        // The thumb never moves without its lens, whatever set it (the owner, 18.11): the move counts as a touch.
        touch.count++
        position.animateTo(target, THUMB_SPRING)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    Box(
        modifier.size(TRACK_WIDTH, TRACK_HEIGHT)
            .onGloballyPositioned { val p = it.positionInRoot(); at[0] = p.x; at[1] = p.y }
            .then(if (enabled) Modifier.fingerOn(touch) else Modifier)
            .then(
                // The switch takes its own taps and drags: left to the row, a tap on the switch lit the whole row; and
                // a finger on it never scrolls the list, it only moves the thumb sideways (the owner, 18.11).
                if (onDrag != null) Modifier.pointerInput(rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        val travel = (TRACK_WIDTH - SWITCH_THUMB_WIDTH - PAD * 2).toPx()
                        var dragging = false
                        // Where the finger would have the thumb, unbounded; past an end the thumb gives like rubber.
                        var wanted = position.value
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if (!change.pressed) {
                                change.consume()
                                if (!dragging) drag.value?.invoke(!checkedNow.value)
                                break
                            }
                            if (!dragging && (change.position - down.position).getDistance() > slop) {
                                dragging = true
                                pulling = true
                            }
                            if (!dragging) continue
                            change.consume()
                            val delta = change.position.x - change.previousPosition.x
                            wanted += (if (rtl) -delta else delta) / travel
                            val next = rubber(wanted, give)
                            scope.launch { position.snapTo(next) }
                        }
                        if (!dragging) return@awaitEachGesture
                        pulling = false
                        val now = position.value >= 0.5f
                        if (now != checkedNow.value) drag.value?.invoke(now)
                        else scope.launch { position.animateTo(if (now) 1f else 0f, THUMB_SPRING) }
                    }
                } else Modifier,
            )
            .drawBehind {
                val p = position.value
                val radius = size.height / 2f
                val trackColor = lerp(off, on, lit.value)
                val trackCorner = CornerRadius(radius)
                drawRoundRect(trackColor, cornerRadius = trackCorner)

                val pad = PAD.toPx()
                val thumbW = SWITCH_THUMB_WIDTH.toPx()
                val thumbH = THUMB_HEIGHT.toPx()
                val travel = size.width - thumbW - 2 * pad
                val x = pad + thumbW / 2f + travel * p
                val center = Offset(if (rtl) size.width - x else x, size.height / 2f)
                val thumb = lerp(thumbOff, Color.White, lit.value)
                val s = held.value
                if (s <= 0f && fill.value >= 1f) {
                    drawThumb(center, thumbW / 2f, thumbH / 2f, thumb, 1.dp.toPx())
                    return@drawBehind
                }

                // Stretched along its way by its speed, and further as it is pulled past an end.
                val past = (maxOf(p - 1f, -p, 0f) / give).coerceAtMost(1f)
                val stretch = (abs(position.velocity) * 0.012f).coerceAtMost(0.08f) + past * 0.08f
                val swell = 1f + ((tuning?.value(GlassTuningGroup.Switch, "swell", SWITCH_LENS_SWELL, !dark) ?: SWITCH_LENS_SWELL) - 1f) * s
                val halfW = thumbW / 2f * swell * (1f + stretch)
                val halfH = thumbH / 2f * swell * (1f - stretch * 0.5f)
                val origin = Offset(at[0], at[1])
                val trackSize = size
                lens.draw(this, layer, center, halfW, halfH, s, frost.value, dark, look) {
                    under(origin)
                    // The switch's size, not the lens layer's this scope has.
                    drawRoundRect(trackColor, Offset.Zero, trackSize, trackCorner)
                }
                val white = fill.value
                if (white > 0f) {
                    drawRoundRect(thumb.copy(alpha = white), Offset(center.x - halfW, center.y - halfH), Size(2 * halfW, 2 * halfH), CornerRadius(halfH))
                }
            },
    )
}

/** [x] past 0 or 1 by at most [over]: the further it is pulled, the less it moves. */
internal fun rubber(x: Float, over: Float): Float = when {
    x > 1f -> 1f + over * (x - 1f) / (x - 1f + over)
    x < 0f -> -over * -x / (-x + over)
    else -> x
}

/**
 * The fingers on a control and on the row that speaks for it. [count] counts touches: a row's short tap reports its press
 * and release together, so [down] may never be seen true, and the lens must still swell once.
 */
internal class LensTouch {
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

/**
 * Swells while a finger is down, settles back after; a touch already gone still swells fully once. [rest]: what the
 * lens waits for, swollen, before it settles (a segment's lens, its landing).
 */
internal suspend fun Animatable<Float, *>.follow(
    touch: LensTouch,
    motion: Boolean,
    out: Int = LENS_OUT_MS,
    outEasing: Easing = FastOutSlowInEasing,
    inMs: Int = LENS_IN_MS,
    rest: suspend () -> Unit = {},
) {
    if (!motion) {
        touch.seen = touch.count
        snapTo(0f)
        return
    }
    // A touch is seen only once the lens has swollen for it: marked seen at the start, a finger lifted before the swell's
    // first frame restarted this with the lens still at 0 and nothing owed, and the thumb rode without it (the owner, 18.11).
    if (value < 1f && (touch.down || touch.count != touch.seen || value > 0f)) animateTo(1f, tween((inMs * (1f - value)).toInt()))
    touch.seen = touch.count
    if (touch.down) return
    rest()
    animateTo(0f, tween(out, easing = outEasing))
}

/** A finger on this, from its touch until it lifts, whatever gesture takes the touch meanwhile. */
internal fun Modifier.fingerOn(touch: LensTouch): Modifier = pointerInput(touch) {
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

private val THUMB_OFF_DARK = Color(0xFFB4B4BA)
private val TRACK_WIDTH = 64.dp
private val TRACK_HEIGHT = 28.dp
private val THUMB_WIDTH = 40.dp
// iOS's switch: the thumb 0.58 of the track wide (the owner's video, 18.11).
private val SWITCH_THUMB_WIDTH = 37.dp
// A little smaller than the video's 1.6×, against an iPhone in hand (the owner, 18.11).
internal const val SWITCH_LENS_SWELL = 1.55f
private val THUMB_HEIGHT = 24.dp
private val PAD = 2.dp
/** Room around the slider's thumb its lens grows from, as the segments' pad around their pill. */
private val SLIDER_LENS_PAD = 4.dp
private const val LENS_IN_MS = 120
private const val LENS_OUT_MS = 220
/** How far past an end the switch's thumb gives under a pulling finger: iOS's, about 8 dp in the owner's video. */
private val TOGGLE_GIVE = 10.dp
// Along the thumb's ride, from its start, as iOS's second video (18.11); 150 ms from the middle read as a jump.
private const val TRACK_FADE_MS = 250
// The tap as iOS's (the owner's second video, 18.11): the white gone in 60 ms, the frost cleared as the lens rides,
// the lens home frosting and shrinking in 140 ms, the white coming back with it over 420 ms, most of it early.
private const val FILL_OUT_MS = 60
private const val FROST_CLEAR_MS = 220
private const val SWITCH_LENS_OUT_MS = 140
private const val FILL_IN_MS = 420
/** The thumb's ride: firmer than it was (0.62), so it lands without a visible bounce, as iOS's. */
private val THUMB_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = 420f)
/** Near enough home, in its travel, for the lens to start settling. */
private const val HOME = 0.04f

/**
 * A slider with Material's steps, speech and remote keys. The glass style (D-541): a thin track in the accent up to the
 * value and a white thumb that turns under a finger into the config editor's bars' lens one to one (the owner, 23.11:
 * its own bubble lens read as worn), sized from the thumb as the segments' lens from their pill. Elsewhere Material's
 * look in the app's colours.
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
    val lensGlass = remember(glass) { if (glass) lensShader() else null }
    val tuning = LocalGlassTuning.current
    val light = scheme.background.luminance() >= 0.5f
    fun sliderValue(key: String, fallback: Float) = tuning?.value(GlassTuningGroup.SliderLens, key, fallback, light) ?: fallback
    if (lensGlass == null) {
        val haptics = LocalHapticFeedback.current
        // Each step ticks here, as the glass slider's drag does: the screens ticked too, and on glass every step twice.
        // Against the last value ticked: Material repeats a step's value until the screen catches up.
        val ticked = remember { floatArrayOf(value) }
        SideEffect { ticked[0] = value }
        Slider(
            value = value,
            onValueChange = {
                if (steps > 0 && it != ticked[0]) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                ticked[0] = it
                onValueChange(it)
            },
            valueRange = valueRange, steps = steps, enabled = enabled,
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { MutableInteractionSource() }
    val touch = remember { LensTouch() }
    val motion = rememberCoroutineScope().coroutineContext.motionScale > 0f
    // The lens over the thumb: 0 — its size, 1 — swollen under a finger.
    val lens = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val under = rememberPaneUnder()
    val at = remember { floatArrayOf(0f, 0f) }
    // The track's start in the window and its width: under the lens the track ends where it does.
    val trackAt = remember { floatArrayOf(0f, 0f) }

    LaunchedEffect(touch.count, touch.down, motion) { lens.follow(touch, motion) }

    // The track in the thumb's own place, for the lens: the accent to its middle, the rest after it.
    fun DrawScope.track(start: Float, end: Float, split: Float, height: Float, middle: Float = size.height / 2f) {
        val top = middle - height / 2f
        val corner = CornerRadius(height / 2f)
        drawRoundRect(off, Offset(start, top), Size(end - start, height), corner)
        val (from, to) = if (rtl) split to end else start to split
        if (to > from) drawRoundRect(on, Offset(from, top), Size(to - from, height), corner)
    }

    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled,
        interactionSource = interaction,
        // A finger anywhere on it: Material reports a press only once it knows it is no scroll, a tap all at once.
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (enabled) Modifier.fingerOn(touch).sliderDrag(valueRange, steps, rtl, value, onValueChange) else Modifier),
        thumb = {
            Box(
                Modifier.size(THUMB_WIDTH, THUMB_HEIGHT)
                    .onGloballyPositioned { val p = it.positionInRoot(); at[0] = p.x; at[1] = p.y }
                    .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val shadowDrop = 1.dp.toPx()
                    drawThumb(center, size.width / 2f, size.height / 2f, Color.White, shadowDrop)
                    val s = lens.value
                    if (s < 0.01f) return@drawBehind
                    // The bars' lens: the thumb as a segment's pill, the room around it as the segments' pad.
                    val lensHalfHeight = (size.height / 2f + SLIDER_LENS_PAD.toPx()) * sliderValue("height", TOOLBAR_LENS_HEIGHT)
                    val dockHalfWidth = lensHalfHeight * sliderValue("aspect", TOOLBAR_LENS_ASPECT)
                    val wide = maxOf(size.width / 2f * (1f + sliderValue("widen", TOOLBAR_WIDEN)), dockHalfWidth)
                    val halfW = androidx.compose.ui.util.lerp(size.width / 2f, wide, s)
                    val halfH = androidx.compose.ui.util.lerp(size.height / 2f, lensHalfHeight, s)
                    val origin = Offset(at[0], at[1])
                    val thick = SLIDER_TRACK.toPx()
                    val width = size.width
                    val height = size.height
                    val look = tuning.lens(GlassTuningGroup.SliderLens, if (light) TOOLBAR_LOOK_LIGHT else TOOLBAR_LOOK, light)
                    drawDockLens(lensGlass, layer, center, halfW, halfH, s, !light, look) {
                        under(origin)
                        // At the thumb's middle: this scope is the lens layer's, taller, and the line slid below the
                        // lens, a gap in the scale under the held thumb (the owner, 18.11). From the track's own start to
                        // its end: drawn three thumbs each way, the lens showed a strip past the track's ends (the owner,
                        // 23.11).
                        val start = trackAt[0] - at[0]
                        track(start, start + trackAt[1], center.x, thick, center.y)
                        drawThumb(center, width / 2f, height / 2f, Color.White, shadowDrop)
                    }
                },
            )
        },
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(THUMB_HEIGHT)
                    .onGloballyPositioned { trackAt[0] = it.positionInRoot().x; trackAt[1] = it.size.width.toFloat() }
                    .drawBehind {
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

/**
 * The glass slider's own drag: past the slop a finger moves only the thumb, never the list (the owner, 18.11). Material's
 * drag left that to the scroll. Material still takes the press (the thumb jumps there), keys and speech.
 */
private fun Modifier.sliderDrag(
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    rtl: Boolean,
    value: Float,
    onValueChange: (Float) -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val now = rememberUpdatedState(value)
    val change = rememberUpdatedState(onValueChange)
    pointerInput(range, steps, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var moving = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!event.pressed) break
                if (!moving && (event.position - down.position).getDistance() > slop) moving = true
                if (!moving) continue
                event.consume()
                val thumb = THUMB_WIDTH.toPx()
                val along = ((event.position.x - thumb / 2f) / (size.width - thumb)).coerceIn(0f, 1f)
                val next = sliderValue(if (rtl) 1f - along else along, range, steps)
                if (next != now.value) {
                    if (steps > 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    change.value(next)
                }
            }
        }
    }
}

/** The value at [fraction] of [range], on one of its [steps] when it has them. */
internal fun sliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val f = if (steps > 0) (fraction * (steps + 1)).roundToInt() / (steps + 1).toFloat() else fraction
    return range.start + (range.endInclusive - range.start) * f
}

private val SLIDER_TRACK = 6.dp
private const val DISABLED_ALPHA = 0.5f
