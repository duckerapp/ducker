package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.floor

/**
 * One choice of a few, first made for «Вручную · Свой · Провайдер» (D-286): a white track and a black pill that
 * rides to the chosen segment. [widest] sizes the segments, so a label that changes while one looks keeps its
 * place; by default the labels themselves. The glass style (D-541): under a finger the pill swells into the dock's
 * clear lens, which a drag carries between the segments and lets go on the nearest; a tap's lens rides to the
 * tapped segment and settles once it lands.
 */
@Composable
fun PillSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    widest: List<String> = labels,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val scope = rememberCoroutineScope()
    val motion = scope.coroutineContext.motionScale > 0f
    val positionState = remember { Animatable(selected.toFloat()) }
    val position by positionState.asState()
    LaunchedEffect(selected) { positionState.animateTo(selected.toFloat(), PILL_SPRING) }
    val pill = chosenFill()
    // Пульт (этап 6) входит в переключатель на выбранный сегмент, а не на ближайший к центру.
    val segments = remember(labels.size) { List(labels.size) { FocusRequester() } }
    val tv = LocalTv.current
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)

    // Left and width of every segment, from the last layout; the pill rides between them.
    var spans by remember { mutableStateOf(FloatArray(0)) }

    // The glass style's lens: 0 — the pill, 1 — a clear lens under a finger.
    val glass = (LocalGlassBackdrop.current != null || LocalFrosted.current) && !tv
    // The dock's own lens (the owner, 19.11: as the config's bars); its size here stays the pills' own.
    val bubble = remember(glass) { if (glass) lensShader() else null }
    val tuning = LocalGlassTuning.current
    val dark = scheme.background.luminance() < 0.5f
    val lens = remember { Animatable(0f) }
    // How far past the first or the last segment the finger is, pixels: the lens gives a little and stretches there.
    val edge = remember { Animatable(0f) }
    val touch = remember { LensTouch() }
    val segmentsLayer = if (bubble != null) rememberGraphicsLayer() else null
    val bubbleLayer = if (bubble != null) rememberGraphicsLayer() else null
    val under = rememberPaneUnder()
    // Where its corner lies in the window and how much its swell scales it, as [GlassToolbar]'s.
    val at = remember { floatArrayOf(0f, 0f, 1f, 1f) }
    val selectedNow by rememberUpdatedState(selected)
    if (bubble != null) LaunchedEffect(touch.count, touch.down, motion) {
        // Swollen until the pill has landed where the finger sent it.
        lens.follow(touch, motion) {
            snapshotFlow { !positionState.isRunning && positionState.value == selectedNow.toFloat() }.first { it }
        }
    }
    val presence = { if (bubble != null) lens.value else 0f }
    // The pane glass: the whole control swells while a finger is on it, as the dock breathes under its lens.
    val press = rememberGlassPress()

    Layout(
        modifier = modifier.fillMaxWidth()
            .height(52.dp)
            .glassPress(press?.takeIf { enabled }, own = true, follow = false)
            // Dimmed together with its shadow, which would show through the faded surface.
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
                outsets = DuckerShadowOutsets
            }
            .then(
                if (bubble != null) Modifier.onGloballyPositioned {
                    val p = it.positionInRoot()
                    at[0] = p.x
                    at[1] = p.y
                    if (it.size.width > 0 && it.size.height > 0) {
                        at[2] = (it.localToRoot(Offset(it.size.width.toFloat(), 0f)).x - p.x) / it.size.width
                        at[3] = (it.localToRoot(Offset(0f, it.size.height.toFloat())).y - p.y) / it.size.height
                    }
                }
                    .drawWithContent {
                        val shown = lens.value
                        if (shown < 0.01f) {
                            drawContent()
                            return@drawWithContent
                        }
                        // The lens shows the segments under it: the track, the faded pill, the labels.
                        segmentsLayer!!.record { this@drawWithContent.drawContent() }
                        drawLayer(segmentsLayer)

                        val pad = PILL_PAD.toPx()
                        val (left, width) = pillSpan(spans, positionState.value)
                        // Past the ends, as the dock's lens: it follows less and less, as rubber does, and stretches,
                        // mostly at the very end; elsewhere stretched by its speed.
                        val over = edge.value
                        val give = PILL_EDGE_GIVE.toPx()
                        val bent = over * give / (abs(over) + give)
                        val reach = (abs(over) / (width / 2f)).coerceAtMost(1f)
                        // The config editor's bars' lens one to one (the owner, 23.11: theirs is the reference): its size,
                        // its look and its stretch, see [GlassToolbar]; tuned in a group of its own.
                        val group = GlassTuningGroup.PillLens
                        fun value(key: String, fallback: Float) = tuning?.value(group, key, fallback, !dark) ?: fallback
                        val lensHalfHeight = size.height / 2f * value("height", TOOLBAR_LENS_HEIGHT)
                        val dockHalfWidth = lensHalfHeight * value("aspect", TOOLBAR_LENS_ASPECT)
                        val wide = maxOf(width / 2f * (1f + value("widen", TOOLBAR_WIDEN)), dockHalfWidth)
                        val stretch = ((abs(positionState.velocity) * width / 5000f).coerceAtMost(0.25f) * dockHalfWidth / wide + PILL_EDGE_STRETCH * reach * reach * reach)
                            .coerceAtLeast(-0.15f)
                        val origin = Offset(at[0], at[1])
                        drawDockLens(
                            lens = bubble,
                            layer = bubbleLayer!!,
                            center = Offset(pad + left + width / 2f + bent, size.height / 2f),
                            halfWidth = lerp(width / 2f, wide, shown) * (1f + stretch),
                            halfHeight = lerp(size.height / 2f - pad, lensHalfHeight, shown) * (1f - 0.35f * stretch),
                            shown = shown,
                            dark = dark,
                            theme = tuning.lens(group, if (dark) TOOLBAR_LOOK else TOOLBAR_LOOK_LIGHT, !dark),
                        ) {
                            // What lies under the control, as the swelling control's own place sees it.
                            scale(1f / at[2], 1f / at[3], pivot = Offset.Zero) { under(origin) }
                            drawLayer(segmentsLayer)
                        }
                    }
                else Modifier,
            )
            .sheetSurface(CircleShape, press)
            .padding(PILL_PAD)
            .then(if (bubble != null && enabled) Modifier.pillDrag(touch, { spans }, positionState, edge, motion, scope, selected, onSelect) else Modifier)
            // Пилюля рисуется, а не компонуется: едет каждый кадр, и перекладывать
            // ради этого сегменты незачем.
            .drawBehind {
                val (left, width) = pillSpan(spans, position)
                drawRoundRect(
                    // Under the lens the pill clears, as the dock's highlight under its bubble.
                    color = pill.copy(alpha = pill.alpha * (1f - presence())),
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .selectableGroup()
            .then(
                if (tv) {
                    Modifier.focusProperties { onEnter = { segments[selected].requestFocus() } }
                        .focusGroup()
                } else Modifier,
            ),
        content = {
            labels.forEachIndexed { index, text ->
                val on = index == selected

                Box(
                    modifier = Modifier
                        .focusRequester(segments[index])
                        .clip(CircleShape)
                        .selectable(
                            selected = on,
                            interactionSource = null,
                            indication = if (press != null) null else LocalIndication.current,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Unseen, it only gives the segment its width: the longest label with even room
                    // at both ends.
                    Text(
                        text = widest.getOrElse(index) { text },
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 14.dp).alpha(0f).clearAndSetSemantics {},
                    )

                    // The label twice: in the backing's ink, and in the pill's over it, drawn only where
                    // the pill is — readable in every frame while the pill rides under it. Recoloring
                    // by choice blacked the label out halfway; “difference” of white, used before, gave
                    // the pill's complement, pink on a green-tinted pill (D-312).
                    val label: @Composable (Color) -> Unit = { color ->
                        FitLabel(
                            text = text,
                            style = style,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = color,
                        )
                    }

                    label(scheme.onSurface)

                    Box(
                        modifier = Modifier.matchParentSize()
                            .clearAndSetSemantics {}
                            .then(if (bubble != null) Modifier.graphicsLayer { alpha = 1f - lens.value } else Modifier)
                            .drawWithContent {
                                val (left, width) = pillSpan(spans, position)
                                val from = left - spans.getOrElse(index * 2) { 0f }
                                clipRect(left = from, right = from + width) { this@drawWithContent.drawContent() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        label(scheme.onPrimaryContainer)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Every segment is as wide as its label, and what is left over is shared equally: the same
        // room around every label. Too narrow a screen shares the width by the labels' widths, and
        // the labels shrink.
        val total = constraints.maxWidth
        val wanted = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val free = total - wanted.sum()
        val widths = wanted.map { if (free >= 0) it + free / wanted.size else it * total / wanted.sum() }
            .toMutableList()
        widths[widths.lastIndex] += total - widths.sum()

        val placeables = measurables.mapIndexed { i, it -> it.measure(Constraints.fixed(widths[i], constraints.maxHeight)) }
        val next = FloatArray(widths.size * 2)
        // The first segment at the layout's start: on the right in a right-to-left language.
        val rtl = layoutDirection == LayoutDirection.Rtl
        var x = 0
        widths.forEachIndexed { i, width ->
            next[i * 2] = (if (rtl) total - x - width else x).toFloat()
            next[i * 2 + 1] = width.toFloat()
            x += width
        }
        if (!next.contentEquals(spans)) spans = next

        layout(total, constraints.maxHeight) {
            placeables.forEachIndexed { i, it -> it.place(next[i * 2].toInt(), 0) }
        }
    }
}

/**
 * The glass pill's drag: a finger moved past the slop takes the pill (and the touch from the segment under it and from
 * the list's scroll: on the pills it only goes sideways, the owner, 18.11), which follows it between the segments with a
 * tick at each, giving at the ends ([edge]); let go, the nearest is chosen.
 */
private fun Modifier.pillDrag(
    touch: LensTouch,
    spans: () -> FloatArray,
    position: Animatable<Float, *>,
    edge: Animatable<Float, *>,
    motion: Boolean,
    scope: CoroutineScope,
    selected: Int,
    onSelect: (Int) -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val selectedNow by rememberUpdatedState(selected)
    val selectNow by rememberUpdatedState(onSelect)
    fingerOn(touch).pointerInput(motion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var moving = false
            var near = selectedNow
            val from = segmentMiddle(spans(), positionAt(spans(), down.position.x).roundToInt())
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!moving && (change.position - down.position).getDistance() > slop) moving = true
                    if (!moving) continue

                    change.consume()
                    // The finger's way from the middle of its segment: its place in a wide segment read as a pull past
                    // the end, and the lens jerked the other way first (the owner, 19.11).
                    val x = from + change.position.x - down.position.x
                    val to = positionAt(spans(), x)
                    scope.launch { if (motion) position.animateTo(to, FOLLOW_SPRING) else position.snapTo(to) }
                    val ends = endMiddles(spans())
                    scope.launch { edge.snapTo(if (motion) x - x.coerceIn(ends.first, ends.second) else 0f) }
                    val now = to.roundToInt()
                    if (now != near) {
                        near = now
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            } finally {
                // Let go at the end, the lens wobbles back into shape, as the dock's.
                scope.launch { edge.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f)) }
            }
            if (!moving) return@awaitEachGesture

            val count = spans().size / 2
            val picked = near.coerceIn(0, maxOf(count - 1, 0))
            if (picked != selectedNow) selectNow(picked)
            else scope.launch { position.animateTo(picked.toFloat(), PILL_SPRING) }
        }
    }
}

/** The middle of segment [i], clamped to the segments there are. */
private fun segmentMiddle(spans: FloatArray, i: Int): Float {
    val count = spans.size / 2
    if (count == 0) return 0f
    val k = i.coerceIn(0, count - 1)
    return spans[k * 2] + spans[k * 2 + 1] / 2f
}

/**
 * The dock's lens at [center], [shown] 0…1: its shader and look, its bend growing with it, its lightness and light line
 * with its last third, as the dock's (`GlassDock`).
 */
internal fun DrawScope.drawDockLens(
    lens: GlassLens,
    layer: GraphicsLayer,
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    shown: Float,
    dark: Boolean,
    theme: LensLook,
    under: DrawScope.() -> Unit,
) {
    val fade = (shown / 0.3f).coerceAtMost(1f)
    val look = if (fade >= 1f) theme else theme.run {
        copy(lighten = lighten * fade, shineDark = shineDark * fade, shineLight = shineLight * fade, ring = ring * fade, irisLight = irisLight * fade)
    }
    lens.draw(this, layer, center, halfWidth, halfHeight, kotlin.math.sqrt(shown), 0f, dark, look, under = under)
}

/** The segments' and the config bars' lens on the light theme, as before the dark one was tuned. */
internal val SEGMENT_LOOK_LIGHT = LensLook(
        drawFrom = -0.13f, zoomEdge = 0.12f, push = 1.2f, pushPower = 6f, pushBand = 0.45f, pushAlong = 1.1f, pushAcross = 0.7f, pushEndFrom = 0.1f, pushEndTo = 0.6f, chroma = 0.03f, disperse = 0.1f, smear = 1.2f, bandSide = 0.18f, bandEnd = 0.22f, mirror = 0.9f, mirrorSoft = 0.14f, mirrorChroma = 0.15f, mirrorMix = 0.3f, ring = 0.15f, rimShade = 0f, lighten = 0.07f, shineDark = 0.35f, shineLight = 0.15f, shineEnds = 0.7f, irisDark = 0f, irisLight = 0.12f, shadowDark = 0f, shadowLight = 0f, clearBlur = 0.dp, frostBlur = 0.dp,
    )

/**
 * The config editor's bars' lens ([GlassToolbar]), and the segments' one to one ([PillSegments]): the owner's reference
 * (23.11, the panel's export).
 */
internal val TOOLBAR_LOOK = LensLook(
        drawFrom = -0.2f, zoomEdge = 0.34f, push = 1.58f, pushPower = 6f, pushBand = 0.5f, pushAlong = 0.35f, pushAcross = 1.37f, pushEndFrom = 0f, pushEndTo = 0.71f, chroma = 0.06f, disperse = 0.1f, smear = 0.66f, bandSide = 0.5f, bandEnd = 0.07f, mirror = 0.73f, mirrorSoft = 0.02f, mirrorChroma = 0.37f, mirrorMix = 0.26f, ring = 0f, rimShade = 0f, lighten = 0.05f, shineDark = 0.11f, shineLight = 0.15f, shineEnds = 0.21f, irisDark = 0f, irisLight = 0.12f, shadowDark = 0.16f, shadowLight = 0f, clearBlur = 0.dp, frostBlur = 0.dp,
    )
internal val TOOLBAR_LOOK_LIGHT = SEGMENT_LOOK_LIGHT
internal const val TOOLBAR_LENS_HEIGHT = 1.2f
internal const val TOOLBAR_LENS_ASPECT = 1.3f
internal const val TOOLBAR_WIDEN = 0.12f

/** The middles of the segments at either end, the smaller first: in a right-to-left layout the first is on the right. */
private fun endMiddles(spans: FloatArray): Pair<Float, Float> {
    val count = spans.size / 2
    if (count == 0) return 0f to 0f
    val a = spans[0] + spans[1] / 2f
    val b = spans[(count - 1) * 2] + spans[(count - 1) * 2 + 1] / 2f
    return minOf(a, b) to maxOf(a, b)
}

/** The inverse of [pillSpan]: where between the segments a pill centred at [x] stands; past the ends, the end. */
internal fun positionAt(spans: FloatArray, x: Float): Float {
    val count = spans.size / 2
    if (count < 2) return 0f
    fun middle(i: Int) = spans[i * 2] + spans[i * 2 + 1] / 2f
    for (i in 0 until count - 1) {
        val a = middle(i)
        val b = middle(i + 1)
        if (x >= minOf(a, b) && x <= maxOf(a, b)) return i + (x - a) / (b - a)
    }
    return if (abs(x - middle(0)) < abs(x - middle(count - 1))) 0f else (count - 1).toFloat()
}

private val PILL_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
// The pill under a finger: close behind it, a little give.
private val FOLLOW_SPRING = spring<Float>(dampingRatio = 0.85f, stiffness = 1400f)
private val PILL_PAD = 4.dp
// How far the lens gives past the end segments, and how much it stretches there: the dock's.
private val PILL_EDGE_GIVE = 8.dp
private const val PILL_EDGE_STRETCH = 0.2f

/** The pill between the segments at [position]: left and width, from [spans] of [PillSegments]. */
internal fun pillSpan(spans: FloatArray, position: Float): Pair<Float, Float> {
    val count = spans.size / 2
    if (count < 2) return (spans.getOrElse(0) { 0f }) to (spans.getOrElse(1) { 0f })

    val from = floor(position).toInt().coerceIn(0, count - 2)
    val part = position - from
    return lerp(spans[from * 2], spans[from * 2 + 2], part) to lerp(spans[from * 2 + 1], spans[from * 2 + 3], part)
}

/**
 * A small pill in place of Material's chips and buttons: white with the cards' shadow on the screen's grey,
 * grey without a shadow on a white card ([onCard]), black while [selected]. [role] says what it is: a choice
 * among others ([Role.RadioButton]), an on/off ([Role.Switch]) or an action ([Role.Button]). [fit] — the label
 * shrinks instead of being cut, for pills that share a row by their labels (D-114).
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onCard: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    role: Role = Role.Button,
    fit: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val rest = if (onCard) scheme.surfaceVariant else scheme.surface
    // The glass style: on the tabs every pill is the shared frosted button, on a card or off it.
    val backdrop = LocalGlassBackdrop.current
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(PILL_FADE_MS), label = "pillSelection")
    val style = MaterialTheme.typography.labelLarge
    // The pane glass: it swells under a finger as Home's buttons do, without a ripple.
    val press = rememberGlassPress()
    val indication = if (press != null) null else LocalIndication.current
    val action = when (role) {
        Role.Button -> Modifier.clickable(interactionSource = null, indication = indication, enabled = enabled, role = role, onClick = onClick)
        Role.Switch, Role.Checkbox -> Modifier.toggleable(
            value = selected, interactionSource = null, indication = indication, enabled = enabled, role = role, onValueChange = { onClick() },
        )
        else -> Modifier.selectable(selected = selected, interactionSource = null, indication = indication, enabled = enabled, role = role, onClick = onClick)
    }

    Box(
        modifier = modifier.minimumInteractiveComponentSize()
            // Before the shadow, which would otherwise show through the faded pill.
            .graphicsLayer {
                alpha = if (enabled) 1f else DISABLED_ALPHA
                outsets = DuckerShadowOutsets
            }
            .glassPress(press.takeIf { enabled }, own = true)
            .then(
                when {
                    backdrop != null -> Modifier.glass(backdrop, GlassMaterial.Control, CircleShape, press = press)
                    onCard -> Modifier
                    else -> Modifier.airShadow(CircleShape, rest)
                },
            )
            .clip(CircleShape)
            .then(action),
        propagateMinConstraints = true,
    ) {
        val content: @Composable (Color, Modifier) -> Unit = { ink, layer ->
            Row(
                modifier = layer.heightIn(min = 36.dp)
                    .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))

                if (fit) FitLabel(text, style, color = ink)
                else Text(text, color = ink, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        content(
            scheme.onSurface,
            Modifier.drawWithContent {
                val edge = size.width * selection
                val left = if (layoutDirection == LayoutDirection.Rtl) 0f else edge
                val right = if (layoutDirection == LayoutDirection.Rtl) size.width - edge else size.width
                clipRect(left = left, right = right) { this@drawWithContent.drawContent() }
            }.background(if (backdrop != null) Color.Transparent else rest),
        )
        // Reveal fill and its ink together, as in PillSegments: opposing color fades cross
        // at zero contrast. The second label is visual only and never sizes the pill.
        content(
            scheme.onPrimaryContainer,
            Modifier.matchParentSize().clearAndSetSemantics {}
                .drawWithContent {
                    val edge = size.width * selection
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - edge else 0f
                    clipRect(left = left, right = left + edge) { this@drawWithContent.drawContent() }
                }
                .background(chosenFill()),
        )
    }
}

private const val PILL_FADE_MS = 150
private const val DISABLED_ALPHA = 0.4f

/** An action of a [GlassToolbar]: an icon or a label. [fill] — the main one, in the accent fill; [busy] — a ring for it. */
internal class ToolbarItem(
    val description: String,
    val icon: ImageVector? = null,
    val label: String? = null,
    val enabled: Boolean = true,
    val fill: Boolean = false,
    val busy: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Actions in one capsule of the pane glass, after the dock (the owner, 19.11: the config's tools «как докбар»): equal
 * segments; under a finger the dock's lens swells over the touched one and a drag carries it along with a tick at each;
 * lifted on the bar, the action under it runs, as iOS's toolbars; nothing stays chosen. Only where [glassPanesOn].
 */
@Composable
internal fun GlassToolbar(items: List<ToolbarItem>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = scope.coroutineContext.motionScale > 0f
    val position = remember { Animatable(0f) }
    val edge = remember { Animatable(0f) }
    val lens = remember { Animatable(0f) }
    val touch = remember { LensTouch() }
    var spans by remember { mutableStateOf(FloatArray(0)) }
    // The dock's own lens (the owner, 19.11: the segments' bubble here was «пустышка»), its look and its size.
    val lensGlass = remember { lensShader() }
    val tuning = LocalGlassTuning.current
    val itemsLayer = rememberGraphicsLayer()
    val lensLayer = rememberGraphicsLayer()
    // Where the bar's corner lies in the window and how much its breath scales it: the lens draws the screen under it.
    val at = remember { floatArrayOf(0f, 0f, 1f, 1f) }
    val base = scheme.background
    val dark = base.luminance() < 0.5f
    val press = rememberGlassPress()
    val backdrop = LocalGlassBackdrop.current
    val itemsNow by rememberUpdatedState(items)
    val accent = scheme.primaryContainer
    if (lensGlass != null) LaunchedEffect(touch.count, touch.down, motion) { lens.follow(touch, motion) }

    Layout(
        modifier = modifier.height(height)
            // The whole bar breathes while a finger is on it, as the dock under its lens.
            .glassPress(press, own = true, follow = false)
            .onGloballyPositioned {
                val p = it.positionInRoot()
                at[0] = p.x
                at[1] = p.y
                if (it.size.width > 0 && it.size.height > 0) {
                    at[2] = (it.localToRoot(Offset(it.size.width.toFloat(), 0f)).x - p.x) / it.size.width
                    at[3] = (it.localToRoot(Offset(0f, it.size.height.toFloat())).y - p.y) / it.size.height
                }
            }
            .drawWithContent {
                val s = lens.value
                if (lensGlass == null || backdrop == null || s < 0.01f) {
                    drawContent()
                    return@drawWithContent
                }
                // What the lens draws in: the bar itself, its pane, fill and icons.
                itemsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(itemsLayer)

                val pad = PILL_PAD.toPx()
                val (left, width) = pillSpan(spans, position.value)
                val over = edge.value
                val give = PILL_EDGE_GIVE.toPx()
                val bent = over * give / (abs(over) + give)
                val reach = (abs(over) / (width / 2f)).coerceAtMost(1f)
                // As the dock's: from the segment to a capsule [DockGlass.LENS_HEIGHT] of the bar's height, the dock's
                // aspect, and never narrower than a wide segment (the save bar's).
                val group = GlassTuningGroup.Toolbar
                fun value(key: String, fallback: Float) = tuning?.value(group, key, fallback, !dark) ?: fallback
                val lensHalfHeight = size.height / 2f * value("height", TOOLBAR_LENS_HEIGHT)
                val dockHalfWidth = lensHalfHeight * value("aspect", TOOLBAR_LENS_ASPECT)
                val wide = maxOf(width / 2f * (1f + value("widen", TOOLBAR_WIDEN)), dockHalfWidth)
                // The stretch as many pixels as a dock-sized lens's: a share of a wide lens's width leapt ahead as the
                // finger set off (the owner, 19.11: the save bar's lens jerked the way it was pulled).
                val stretch = ((abs(position.velocity) * width / 5000f).coerceAtMost(0.25f) * dockHalfWidth / wide + PILL_EDGE_STRETCH * reach * reach * reach)
                    .coerceAtLeast(-0.15f)
                val halfWidth = lerp(width / 2f, wide, s) * (1f + stretch)
                val halfHeight = lerp(size.height / 2f - pad, lensHalfHeight, s) * (1f - 0.35f * stretch)
                val whole = size
                drawDockLens(lensGlass, lensLayer, Offset(pad + left + width / 2f + bent, size.height / 2f), halfWidth, halfHeight, s, dark, tuning.lens(group, if (dark) TOOLBAR_LOOK else TOOLBAR_LOOK_LIGHT, !dark)) {
                    drawRect(base, Offset(-whole.width, -whole.height), Size(whole.width * 3, whole.height * 3))
                    // The screen as it lies under the breathing bar: [at] is its grown corner.
                    scale(1f / at[2], 1f / at[3], pivot = Offset.Zero) {
                        translate(backdrop.origin.x - at[0], backdrop.origin.y - at[1]) { drawLayer(backdrop.layer) }
                    }
                    drawLayer(itemsLayer)
                }
            }
            .then(if (backdrop != null) Modifier.glass(backdrop, GlassMaterial.Surface, CircleShape, press = press) else Modifier)
            .clip(CircleShape)
            .padding(PILL_PAD)
            .fingerOn(touch)
            .pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    val slop = viewConfiguration.touchSlop
                    fun at(x: Float) = positionAt(spans, x).roundToInt().coerceIn(0, (spans.size / 2 - 1).coerceAtLeast(0))
                    var near = at(down.position.x)
                    val from = segmentMiddle(spans, near)
                    scope.launch { position.snapTo(near.toFloat()) }
                    var moving = false
                    var last = down.position
                    var lifted = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            last = change.position
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            if (!moving && (change.position - down.position).getDistance() > slop) moving = true
                            if (!moving) continue
                            // From the middle of the segment it started on, as the pills'.
                            val x = from + change.position.x - down.position.x
                            val to = positionAt(spans, x)
                            scope.launch { if (motion) position.animateTo(to, FOLLOW_SPRING) else position.snapTo(to) }
                            val ends = spans.let { s ->
                                val n = s.size / 2
                                if (n == 0) 0f to 0f else {
                                    val a = s[0] + s[1] / 2f
                                    val b = s[(n - 1) * 2] + s[(n - 1) * 2 + 1] / 2f
                                    minOf(a, b) to maxOf(a, b)
                                }
                            }
                            scope.launch { edge.snapTo(if (motion) x - x.coerceIn(ends.first, ends.second) else 0f) }
                            val now = at(x)
                            if (now != near) {
                                near = now
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    } finally {
                        scope.launch { edge.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f)) }
                    }
                    scope.launch { position.animateTo(near.toFloat(), PILL_SPRING) }
                    // Lifted off the bar (a finger slid well away from it), nothing runs.
                    val reach = slop * 3
                    val onBar = last.y > -reach && last.y < size.height + reach
                    val item = itemsNow.getOrNull(near)
                    if (lifted && onBar && item != null && item.enabled && !item.busy) item.onClick()
                }
            }
            // The main action's fill. Kept under the lens, which shows it with its label: cleared, the label in the
            // fill's ink was lost on the dark glass (the owner, 19.11: «если зажать на save, текст не видно»).
            .drawBehind {
                itemsNow.forEachIndexed { i, item ->
                    if (!item.fill || spans.size < (i + 1) * 2) return@forEachIndexed
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(spans[i * 2], 0f),
                        size = Size(spans[i * 2 + 1], size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            },
        content = {
            items.forEach { item ->
                val ink = (if (item.fill) scheme.onPrimaryContainer else scheme.onSurface)
                    .let { if (item.enabled) it else it.copy(alpha = 0.3f) }
                Box(
                    // The bar takes the touches; TalkBack reaches every action as a button of its own.
                    modifier = Modifier.clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = item.description
                        if (!item.enabled) disabled()
                        onClick { if (item.enabled) item.onClick(); item.enabled }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        item.busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ink)
                        item.icon != null -> Icon(item.icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                        item.label != null -> FitLabel(item.label, MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp), color = ink)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Equal segments, for symmetry (the owner, 19.11).
        val total = constraints.maxWidth
        val count = measurables.size.coerceAtLeast(1)
        val widths = MutableList(measurables.size) { total / count }
        if (widths.isNotEmpty()) widths[widths.lastIndex] += total - widths.sum()
        val placeables = measurables.mapIndexed { i, it -> it.measure(Constraints.fixed(widths[i], constraints.maxHeight)) }
        val next = FloatArray(widths.size * 2)
        val rtl = layoutDirection == LayoutDirection.Rtl
        var x = 0
        widths.forEachIndexed { i, width ->
            next[i * 2] = (if (rtl) total - x - width else x).toFloat()
            next[i * 2 + 1] = width.toFloat()
            x += width
        }
        if (!next.contentEquals(spans)) spans = next
        layout(total, constraints.maxHeight) {
            placeables.forEachIndexed { i, it -> it.place(next[i * 2].toInt(), 0) }
        }
    }
}
