package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Every number of the glass dock, tuned here only. Fitted frame by frame to the owner's two iOS videos of the App
 * Store's tab bar (18.11; evidence `private/qa/glass-session6-20261118/ios-dock/`), the switch's lens (D-542) the model.
 */
internal object DockGlass {
    /** The whole dock grows by this share under a finger (iOS: +4.6 % at the peak, +4.2 % held), about its middle. */
    const val BREATH = 0.045f

    /** iOS's: the peak 170 ms after the touch, ~10 % past; let go, back past its size by ~10 % 200 ms later. */
    val BREATH_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f)

    /** The pane lightens under a finger, white over it (iOS, dark: 29 → 40 of 255), on a dark and on a light screen. */
    const val LIGHT_DARK = 0.05f
    const val LIGHT_LIGHT = 0.12f
    const val LIGHT_IN_MS = 150
    const val LIGHT_OUT_MS = 400

    /**
     * The chosen tab's veil at rest: black over the pane's own tint (iOS, dark: 15 against the pane's 29), on a dark and
     * on a light screen, and blurred more than the pane, so its icon and label read whatever lies under it.
     */
    const val VEIL_SHADE_DARK = 0.45f
    const val VEIL_SHADE_LIGHT = 0.07f
    val VEIL_BLUR = 8.dp

    /** The lens, held: 1.30 × the veil's width and 1.26 × its height, a little over the dock's edges (iOS). */
    const val LENS_WIDTH = 1.3f
    const val LENS_HEIGHT = 1.26f

    /**
     * A touch: the veil gone in 50 ms as the lens swells (120 ms, [follow]). Landed: the lens shrinks back in 130 ms,
     * the veil returning over 220, most of it early (iOS: 100-130 ms and ~80 ms; a little slower to be seen).
     */
    const val VEIL_OUT_MS = 50
    const val LENS_OUT_MS = 130
    const val VEIL_IN_MS = 220

    /**
     * At this speed, in tabs a second, the lens stretches fully along its way ([STRETCH]), squashes across ([SQUASH])
     * and magnifies more ([MOVING_ZOOM], in [LensLook.drawFrom]): iOS's lens on a quick tap.
     */
    const val SPEED_FULL = 8f
    const val STRETCH = 0.3f
    const val SQUASH = 0.35f
    const val MOVING_ZOOM = 0.1f

    /** Past the first or the last tab: how far the lens gives, and how much it stretches there (F-528). */
    val EDGE_GIVE = 8.dp
    const val EDGE_STRETCH = 0.2f

    /** The labels change colour at the lens's sides over this much: a hard edge read as doubled letters. */
    val LABEL_FADE = 14.dp

    /**
     * The lens's look: the switch's, but magnifying as iOS's (1.15 × held); its ends draw in the neighbours' labels rather
     * than mirror a band (0.08 r, the switch's 0.22), its top and bottom bands thinner (push 0.25); colours split a
     * little more, a fainter shadow. Fitted on a numpy copy of the shader beside iOS's held lens.
     */
    val Look = LensLook.Switch.copy(
        drawFrom = -0.13f, push = 0.25f, pushAlong = 1f, bandEnd = 0.08f, chroma = 0.12f,
        shadowDark = 0.2f, shadowLight = 0.1f, frostBlur = 2.dp,
    )
}

/**
 * The phone's dock in the glass style, after iOS's tab bar (the owner's videos, 18.11, measured frame by frame; numbers
 * in [DockGlass]): a pane over the screen; the chosen tab under a darker veil blurred more than the pane, its icon and
 * label in the chosen colour. Under a finger the veil clears into a lens a third bigger (the switch's lens, D-542),
 * which the finger leads along the dock and a tap sends along with the page, magnifying and drawing in what lies round
 * it, everything under it in the chosen colour, stretched by its speed; let go, it settles back into the veil as the
 * page lands. The dock breathes with the finger: it grows and lightens a little, and settles back past its size.
 * Kept from before (F-528-F-531): one owner of the lens's place ([DockDrag]), page and lens in step, rubber at the
 * ends, no haptic tick.
 */
@Composable
internal fun GlassDock(
    selected: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    onAction: (() -> Unit)?,
    actionLabel: String?,
    single: String?,
    onSingle: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val screen = scheme.background
    val dark = screen.luminance() < 0.5f
    val style = LocalGlassStyle.current
    val shape = RoundedCornerShape(percent = 50)
    val face = rememberDockFace(single, onAction, position, glass = true)
    val alone = single != null
    val current = selected()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val motion = scope.coroutineContext.motionScale > 0f
    val drag = remember(scope) { DockDrag(scope) }
    val touch = remember { LensTouch() }
    val lens = remember { lensShader() }
    val lensLayer = rememberGraphicsLayer()
    // What the lens shows of the dock: the pane, the veil and the labels.
    val dockLayer = rememberGraphicsLayer()
    // The labels in the chosen colour, whole: under the lens everything is chosen, the neighbours it draws in too.
    val chosenLayer = rememberGraphicsLayer()
    var dockAt by remember { mutableStateOf(Offset.Zero) }
    val positionNow by rememberUpdatedState(position)

    // 0 — the veil, 1 — the lens, swollen under a finger.
    val held = remember { Animatable(0f) }
    // The veil's presence: it clears as the lens swells and comes back as it settles.
    val veil = remember { Animatable(1f) }
    val frost = remember { Animatable(0f) }
    // The dock's breath: 0 at rest, 1 grown and lightened under a finger.
    val breath = remember { Animatable(0f) }
    val light = remember { Animatable(0f) }
    // How far the finger has pulled the lens past the first or the last tab, 0..1; it wobbles through zero on release.
    val edge = remember { Animatable(0f) }

    LaunchedEffect(touch.count, touch.down, motion, drag.dragging) {
        if (!motion) {
            // Without Android's animations a tap lifts no lens; a drag still shows it under the finger.
            veil.snapTo(if (drag.dragging) 0f else 1f)
            frost.snapTo(0f)
            held.snapTo(if (drag.dragging) 1f else 0f)
            touch.seen = touch.count
            return@LaunchedEffect
        }
        launch { veil.animateTo(0f, tween(DockGlass.VEIL_OUT_MS)) }
        launch { frost.snapTo(0f) }
        var back: Job? = null
        held.follow(touch, motion, out = DockGlass.LENS_OUT_MS) {
            // A tap's click comes a frame or two after the finger lifts, and only then does the page set off: the lens
            // must not take the page still at rest for its landing.
            repeat(2) { withFrameNanos { } }
            snapshotFlow { drag.resting && positionNow().let { abs(it - it.roundToInt()) < LANDED } }.first { it }
            launch { frost.animateTo(1f, tween(DockGlass.LENS_OUT_MS)) }
            back = launch { veil.animateTo(1f, tween(DockGlass.VEIL_IN_MS, easing = LinearOutSlowInEasing)) }
        }
        if (touch.down) return@LaunchedEffect
        back?.join() ?: veil.animateTo(1f, tween(DockGlass.VEIL_IN_MS, easing = LinearOutSlowInEasing))
        frost.snapTo(0f)
    }

    LaunchedEffect(touch.down, motion) {
        if (!motion) {
            breath.snapTo(0f)
            light.snapTo(0f)
            return@LaunchedEffect
        }
        val to = if (touch.down) 1f else 0f
        launch { breath.animateTo(to, DockGlass.BREATH_SPRING) }
        light.animateTo(to, tween(if (touch.down) DockGlass.LIGHT_IN_MS else DockGlass.LIGHT_OUT_MS))
    }

    fun zoom() = 1f + DockGlass.BREATH * breath.value

    // The geometry, in the row of tabs [row] wide and [h] high.
    fun slot(row: Float) = dockSlotWidth(row)
    fun middle(row: Float) = drag.center(dockPillOffset(row, position(), rtl = rtl) + slot(row) / 2f)
    fun veilRect(row: Float, h: Float): Rect {
        val width = lerp(slot(row), row, face.merge)
        val left = lerp(middle(row) - slot(row) / 2f, 0f, face.merge)
        return Rect(left, 0f, left + width, h)
    }
    // How fast the lens moves, 0..1 of [DockGlass.SPEED_FULL].
    fun speed(row: Float) = (drag.speed / slot(row) / DockGlass.SPEED_FULL).coerceIn(0f, 1f)
    fun stretch(row: Float) = (DockGlass.STRETCH * speed(row) + DockGlass.EDGE_STRETCH * edge.value).coerceAtLeast(-0.15f)
    fun lensRect(row: Float, h: Float): Rect {
        val s = held.value
        val st = stretch(row)
        val halfWidth = slot(row) / 2f * lerp(1f, DockGlass.LENS_WIDTH, s) * (1f + st)
        val halfHeight = h / 2f * lerp(1f, DockGlass.LENS_HEIGHT, s) * (1f - DockGlass.SQUASH * st)
        val c = middle(row)
        return Rect(c - halfWidth, h / 2f - halfHeight, c + halfWidth, h / 2f + halfHeight)
    }
    // Where the labels are in the chosen colour: under the veil at rest, under the lens while it shows.
    fun chosen(row: Float, h: Float): Rect = lerp(veilRect(row, h), lensRect(row, h), held.value.coerceIn(0f, 1f))

    fun DrawScope.chosenMask(): Brush {
        val rect = chosen(size.width, size.height)
        val left = rect.left / size.width
        val right = rect.right / size.width
        val fade = lerp(1f, DockGlass.LABEL_FADE.toPx(), held.value.coerceIn(0f, 1f)) / size.width
        return Brush.horizontalGradient(
            0f to Color.Transparent,
            left.coerceIn(0f, 1f) to Color.Transparent,
            (left + fade).coerceIn(0f, 1f) to Color.Black,
            (right - fade).coerceIn(0f, 1f) to Color.Black,
            right.coerceIn(0f, 1f) to Color.Transparent,
            1f to Color.Transparent,
        )
    }

    val chosenColor = scheme.primary
    val paneTint = scheme.surface.copy(alpha = (GlassMaterial.Surface.tint * (style.tint / 50f).coerceIn(0f, 2f)).coerceIn(0f, 0.92f))
    val veilShade = Color.Black.copy(alpha = if (dark) DockGlass.VEIL_SHADE_DARK else DockGlass.VEIL_SHADE_LIGHT)
    val lightColor = Color.White.copy(alpha = if (dark) DockGlass.LIGHT_DARK else DockGlass.LIGHT_LIGHT)

    Box(
        modifier
            .graphicsLayer {
                val z = zoom()
                scaleX = z
                scaleY = z
            }
            .onGloballyPositioned { dockAt = it.positionInRoot() }
            .drawWithContent {
                drawContent()

                val s = held.value
                if (lens == null || s < 0.01f) return@drawWithContent
                val pad = DOCK_PAD.toPx()
                val row = size.width - 2 * pad
                val rect = lensRect(row, size.height - 2 * pad).translate(pad, pad)
                val whole = size
                val z = zoom()
                lens.draw(
                    this, lensLayer, rect.center, rect.width / 2f, rect.height / 2f, s, frost.value, dark, DockGlass.Look,
                    drawFrom = DockGlass.Look.drawFrom - DockGlass.MOVING_ZOOM * speed(row),
                ) {
                    drawRect(screen, Offset(-whole.width, -whole.height), Size(whole.width * 3, whole.height * 3))
                    // The screen as it lies under the grown dock: [dockAt] is its grown corner (see liquidGlass's zoom).
                    scale(1f / z, 1f / z, pivot = Offset.Zero) {
                        translate(backdrop.origin.x - dockAt.x, backdrop.origin.y - dockAt.y) { drawLayer(backdrop.layer) }
                    }
                    drawLayer(dockLayer)
                    translate(pad, pad) { drawLayer(chosenLayer) }
                }
            },
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).airShadow(shape)
                .drawWithContent {
                    dockLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(dockLayer)
                }
                .liquidGlass(
                    backdrop,
                    base = screen,
                    tint = scheme.surface.copy(alpha = scheme.surface.alpha * GlassMaterial.Surface.tint),
                    shape = shape,
                    zoom = ::zoom,
                ),
            shape = shape,
            color = Color.Transparent,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .drawBehind { if (light.value > 0f) drawRect(lightColor.copy(alpha = lightColor.alpha * light.value)) }
                    .padding(DOCK_PAD)
                    .then(if (alone) Modifier else Modifier.fingerOn(touch).dockDrag(drag, scope, edge, position, onSelect, rtl)),
            ) {
                // The veil: its own glass, darker and blurred more than the pane, so the chosen label reads on any screen.
                Box(
                    Modifier.matchParentSize()
                        .layout { measurable, constraints ->
                            val rect = veilRect(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                            val placeable = measurable.measure(
                                Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), 0) }
                        }
                        .graphicsLayer {
                            alpha = veil.value
                            // The capsule swells a little as «+ Добавить»'s drop lands, and settles back.
                            val drop = face.splash.value
                            if (drop > 0f && drop < 1f) {
                                val swell = 2f * size.height * SPLASH_SWELL * sin(PI.toFloat() * minOf(1f, drop * 2f))
                                scaleX = 1f + swell / size.width
                                scaleY = 1f + swell / size.height
                            } else {
                                scaleX = 1f
                                scaleY = 1f
                            }
                        }
                        .drawWithContent {
                            if (veil.value < 0.003f) return@drawWithContent
                            drawContent()
                            val drop = face.splash.value
                            if (drop > 0f && drop < 1f) drawSplash(Rect(Offset.Zero, size), CornerRadius(size.height / 2f), drop)
                        }
                        .liquidGlass(
                            backdrop,
                            base = screen,
                            tint = paneTint,
                            shape = shape,
                            blurBoost = DockGlass.VEIL_BLUR,
                            bendBoost = 0f,
                            rimBoost = 0f,
                            veil = {
                                drawRect(paneTint)
                                drawRect(veilShade)
                            },
                            zoom = ::zoom,
                        ),
                )

                DockTabs(
                    selected = current,
                    content = scheme.onSurfaceVariant,
                    onSelect = if (alone) null else { index ->
                        val action = onAction
                        val adds = index == ACTION_TAB && face.added && action != null

                        // Where the page is, not the tab it last rested on: a tab tapped while the page still travelled
                        // to another was taken for the current one, and the lens dropped mid-way (the owner, 17.11).
                        if (!adds && position() != index.toFloat()) drag.aim(index) else drag.cancel()
                        if (adds) action() else onSelect(index)
                    },
                    turn = face.turn,
                    actionLabel = if (face.added) actionLabel else null,
                    vertical = false,
                    modifier = Modifier
                        .then(if (alone) Modifier.clearAndSetSemantics { } else Modifier)
                        .graphicsLayer {
                            alpha = 1f - (face.merge * 2f).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(chosenMask(), blendMode = BlendMode.DstOut)
                        },
                )

                DockTabs(
                    selected = current,
                    content = chosenColor,
                    onSelect = null,
                    turn = face.turn,
                    actionLabel = null,
                    vertical = false,
                    // Presses and speech live in the copy below: this one only recolours.
                    modifier = Modifier.clearAndSetSemantics { }
                        .graphicsLayer {
                            alpha = 1f - (face.merge * 2f).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            chosenLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(chosenLayer)
                            drawRect(chosenMask(), blendMode = BlendMode.DstIn)
                        },
                )

                SingleFace(face, single, onSingle, vertical = false, shape = shape, color = chosenColor)
            }
        }
    }
}

/**
 * The finger leads the lens along the dock past the touch slop: past the first or the last tab it follows less and
 * less, as rubber does, and stretches toward the finger ([edge]); let go, the tab under it is chosen and the lens
 * handed to the page. A tap is the tabs' own; after either, the lens rides on the page until it lands.
 */
private fun Modifier.dockDrag(
    drag: DockDrag,
    scope: CoroutineScope,
    edge: Animatable<Float, *>,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    rtl: Boolean,
): Modifier = pointerInput(drag, rtl) {
    val slop = viewConfiguration.touchSlop
    // The lens's ride after the last gesture: a new touch takes the dock over from it.
    var settle: Job? = null

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val width = size.width.toFloat()
        val slot = dockSlotWidth(width)

        fun clamp(x: Float) = x.coerceIn(slot / 2f, width - slot / 2f)
        fun tabAt(x: Float): Int {
            val raw = (clamp(x) / slot).toInt().coerceIn(0, TABS - 1)
            return if (rtl) TABS - 1 - raw else raw
        }

        settle?.cancel()
        var released = false
        try {
            var dragging = false
            var tab = 0

            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                if (!dragging && abs(change.position.x - down.position.x) > slop) {
                    dragging = true
                    drag.begin(drag.center(dockPillOffset(width = width, position = position(), rtl = rtl) + slot / 2f))
                }

                if (dragging) {
                    // Taken: the tab under the finger must not also take it as a tap.
                    change.consume()

                    val raw = change.position.x
                    val x = clamp(raw)
                    val over = raw - x
                    val give = DockGlass.EDGE_GIVE.toPx()

                    drag.move(x + over * give / (abs(over) + give))
                    // Heavy: hardly any stretch near the tab, most of it at the dock's very edge (the owner, 17.11).
                    val reach = (abs(over) / (slot / 2f)).coerceAtMost(1f)
                    scope.launch { edge.snapTo(reach * reach * reach) }
                    tab = tabAt(x)
                }
            }

            if (dragging) {
                onSelect(tab)
                drag.release(dockPillOffset(width, position(), rtl = rtl) + slot / 2f)
            }

            released = true
        } finally {
            if (!released) drag.cancel()
            // Let go at the edge, the lens wobbles back into shape.
            scope.launch { edge.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f)) }
            // A tap's lens rides on the page until it lands (the owner, 17.11).
            settle = scope.launch { drag.ride(position, slot) }
        }
    }
}

/** Within this much of a page the page has landed: a few pixels of the dock. */
private const val LANDED = 0.004f
