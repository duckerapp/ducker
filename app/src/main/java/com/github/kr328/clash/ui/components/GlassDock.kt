package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.sin
import com.github.kr328.clash.R

/**
 * Every number of the glass dock, tuned here only. Fitted frame by frame to the owner's two iOS videos of the App
 * Store's tab bar (18.11; evidence `private/qa/glass-session6-20261118/ios-dock/`), the switch's lens (D-542) the model.
 */
internal object DockGlass {
    /** The whole dock grows by this share under a finger (iOS: +4.6 % at the peak, +4.2 % held), about its middle. */
    const val BREATH = 0.045f

    /** iOS's: the peak 170 ms after the touch, ~10 % past; let go, back past its size by ~10 % 200 ms later. */
    val BREATH_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f)

    /**
     * The dock's own glass, whatever Appearance's sliders say (the owner, 18.11): those sliders tune the cards. Over the
     * app's defaults: less blur (iOS's dock is nearly clear), a gentler rim bend, no dome or inner top shadow (`depth`
     * drives both), no grain (`noise`; iOS has none).
     */
    const val BLUR = 25
    const val TINT = 50
    const val REFRACTION = 35
    const val DISPERSION = 30
    const val DEPTH = 0
    const val HIGHLIGHT = 50
    const val NOISE = 0
    const val VIBRANCY = 40

    /**
     * Its tint eases under a finger rather than its colour changing (iOS, dark: 29 → 40 of 255; the owner, 18.11: «не
     * цвет, а тонировку»), by [TINT_EASE]; around the lens it eases a little more, by [TINT_NEAR], over [TINT_REACH] of a
     * tab each way, so the lens carries a clearer patch along with it.
     */
    const val TINT_EASE = 0.3f
    const val TINT_NEAR = 0.25f
    const val TINT_REACH = 1.2f
    const val TINT_IN_MS = 150
    const val TINT_OUT_MS = 400

    /**
     * The chosen tab's veil at rest: the screen under it blurred more than the pane's and a little darker, its colours
     * kept (iOS over pictures); black over the pane's tint on a dark and on a light screen. At 0.45 it read as a black
     * blot (the owner's video of ours, 18.11). [VEIL_OPACITY]: the whole veil shows the pane through it (the owner,
     * 18.11: 35 % clearer, and no light at its edges — a backing, not a pane).
     */
    const val VEIL_SHADE_DARK = 0.22f
    const val VEIL_SHADE_LIGHT = 0.06f
    val VEIL_BLUR = 12.dp
    const val VEIL_OPACITY = 0.65f

    /**
     * A tab's width: the widest label plus this, so labels stand about as far apart as the first one from the dock's
     * end. Spread over the whole width, the tabs stood ~60 dp apart and the lens grew into a big flat oval (the owner,
     * 18.11). The veil fills its tab, so it lies as far from the dock's ends as from its top and bottom; its shape comes
     * out ~1.45 : 1, as iOS's.
     */
    val TAB_GAP = 36.dp

    /** The tabs' icons, a little larger than Air's 20 dp (the owner, 18.11); the labels unchanged. */
    val ICON = 23.dp

    /**
     * The lens, held: a capsule over the dock's edges all round and past its end over the first and last tab, ~1.08 ×
     * the dock's height and 1.39 × as wide as high (iOS's App Store: ~1.06-1.08 and 1.5). Its straight top and bottom
     * draw the dock's edges in flat, ending at the rim in rounded steps; nearly round (1.22 high, 1.23 wide), it bowed
     * them into a barrel (the owner's sketch, 18.11). The width kept from that, iOS's less 15 % (the owner, 18.11).
     */
    const val LENS_HEIGHT = 1.08f
    const val LENS_ASPECT = 1.39f

    /**
     * A touch: the veil gone in 50 ms as the lens swells (120 ms, [follow]). Landed: the lens shrinks back in 160 ms,
     * slowly at first, still glass, then into the veil, which returns over 220, most of it early. iOS's short tap is one
     * bubble, ~150 ms up past its held size and ~150 ms down, glass to the end; ours, 130 ms down from the start at
     * full speed and its bend gone first, seemed to die before it had come (the owner, 18.11, `IMG_0050.mp4`).
     */
    const val VEIL_OUT_MS = 50
    const val LENS_OUT_MS = 160
    const val VEIL_IN_MS = 220

    /**
     * At this speed, in tabs a second, the lens stretches fully along its way ([STRETCH]), squashes across ([SQUASH])
     * and magnifies more ([MOVING_ZOOM], in [LensLook.drawFrom]): iOS's lens on a quick tap.
     */
    const val SPEED_FULL = 8f

    /**
     * The lens's shape follows its speed through this spring: stopping, it squashes past round and gathers itself back
     * («хочет расплыться, но потом собирается», the owner on iOS's, 18.11); following the speed at once, it went flat
     * the moment the page stopped. [SLOT] turns the speed, pixels a second, into tabs a second outside a draw.
     */
    val JELLY_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.4f, stiffness = 380f)
    val SLOT = 84.dp
    const val STRETCH = 0.2f
    const val SQUASH = 0.35f
    const val MOVING_ZOOM = 0f

    /**
     * A touch: the lens swells on past its size, wider and a little flatter, and settles back. iOS's App Store, held:
     * 1.17 × as wide at ~270 ms from the touch, back by ~450 ms later (`ios-dock/appear/`); it grew to its size and stood.
     */
    const val SWELL_WIDE = 0.17f
    const val SWELL_FLAT = 0.05f
    const val SWELL_IN_MS = 270
    const val SWELL_OUT_MS = 450

    /**
     * Past the first or the last tab: how far the lens gives (F-528; iOS's goes ~13 pt past the dock's end under a finger,
     * so 16 dp, not 8), and how much it draws in there, narrower and a
     * little taller, as against a wall; stretched there, it did not read as iOS's (the owner, 18.11: «втягиваться, без
     * фанатизма»).
     */
    val EDGE_GIVE = 16.dp
    const val EDGE_STRETCH = -0.12f

    /** The labels change colour at the lens's sides over this much: a hard edge read as doubled letters. */
    val LABEL_FADE = 14.dp

    /**
     * The lens's look: the switch's (D-542), the accepted reference — a convex glass over the dock that makes what lies
     * under it a little smaller and draws in what lies round it, so the dock's top and bottom edges come into it along
     * an arc with the wallpaper beyond them, and a label at its rim is squeezed and bends round it (the owner, 18.11:
     * «не увеличивать, а немного втягивать в себя»). Magnifying instead (F-545-F-548) gave a fish-eye whatever its
     * profile. A soft light rim all round, the dark ring faint, colours split a little; no shadow, inner arc, blur or
     * frost (the owner, 18.11). Fitted on a numpy copy over our own dock (`ios-dock/ours-drawin.png`). Its middle a
     * little larger than life (`drawFrom` −0.08, ~1.08), the rim drawing in a little harder (push 0.35): at the switch's
     * +0.12 the labels under it came out ~0.87 of their size, «мелкотня» (the owner, 18.11), where iOS's are ~1.15
     * (App Store) and ~1.2 (Clash Lite); the arc of the dock's edges stays, drawn by the rim (`ios-dock/ours-zoom.png`).
     * The rim's draw-in kept to a narrow band (power 6, push 0.5; the middle ~1.12): reaching deep into the wide lens
     * at the switch's power 1.5 it pulled the top and bottom in and squashed the labels to ~0.75 of their height (the
     * owner, 18.11: «содержимое как-то ужимается»; `ios-dock/ours-squash.png`: device, copy, and the band kept narrow).
     * The magnification flat, ~1.15, out to the rim's band (`zoomEdge` 0.12): eased in over 0.35 r it was ~1.18 in the
     * middle and ~1.0 by 0.75 r, a swollen middle (the owner, 18.11: «центр линзы дает заметное увеличение»), where
     * iOS's is ~1.19 to its band, icon and label alike (`ios-dock/ios2/mag.png`). The rim's band (0.35 r) squeezes much
     * of what lies round it into itself, steepest at the rim, its colours parting with that push (`disperse`): iOS's rim
     * takes in much text, rainbow, its middle clean («контур вокруг линзы прям принимает на себя много текста»);
     * colours parted by the whole shift fringed the middle too (`ios-dock/ours-rim.png`).
     */
    val Look = LensLook.Switch.copy(
        drawFrom = -0.13f, zoomEdge = 0.12f, push = 0.8f, pushPower = 6f, pushBand = 0.35f, pushEndFrom = 0.1f, pushEndTo = 0.6f,
        chroma = 0.08f, disperse = 0.35f, ring = 0.15f, rimShade = 0f, shineDark = 0.35f, shineLight = 0.8f, shineEnds = 0.7f,
        shadowDark = 0f, shadowLight = 0f, clearBlur = 0.dp, frostBlur = 0.dp,
    )
}

/**
 * The phone's dock in the glass style, after iOS's tab bar (the owner's videos, 18.11, measured frame by frame; numbers
 * in [DockGlass]): a pane over the screen; the chosen tab under a darker veil blurred more than the pane, its icon and
 * label in the chosen colour. Under a finger the veil clears into a lens a third bigger (the switch's lens, D-542),
 * which the finger leads along the dock and a tap sends flying ahead of the page, magnifying and drawing in what lies
 * round it, everything under it in the chosen colour, stretched by its speed; landed, it settles back into the veil.
 * The dock breathes with the finger: it grows a little, its tint eases, and it settles back past its size. Its glass is
 * its own ([DockGlass.BLUR] and the rest), not Appearance's sliders. Kept from before (F-528-F-531): one owner of the
 * lens's place ([DockDrag]), rubber at the ends, no haptic tick.
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
    val appStyle = LocalGlassStyle.current
    val style = remember(appStyle) {
        appStyle.copy(
            blur = DockGlass.BLUR, tint = DockGlass.TINT, refraction = DockGlass.REFRACTION, dispersion = DockGlass.DISPERSION,
            depth = DockGlass.DEPTH, highlight = DockGlass.HIGHLIGHT, noise = DockGlass.NOISE, vibrancy = DockGlass.VIBRANCY,
        )
    }
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
    // What the lens shows of the dock: the pane, its veil and labels, its rim — the edge it bends into an arc.
    val dockLayer = rememberGraphicsLayer()
    // The labels in the chosen colour, whole: under the lens everything is chosen, the neighbours it draws in too.
    val chosenLayer = rememberGraphicsLayer()
    var dockAt by remember { mutableStateOf(Offset.Zero) }
    var rowWidth by remember { mutableFloatStateOf(0f) }

    // 0 — the veil, 1 — the lens, swollen under a finger.
    val held = remember { Animatable(0f) }
    // The veil's presence: it clears as the lens swells and comes back as it settles.
    val veil = remember { Animatable(1f) }
    // The dock's breath: 0 at rest, 1 grown under a finger; [ease]: its tint eased.
    val breath = remember { Animatable(0f) }
    val ease = remember { Animatable(0f) }
    // How far the finger has pulled the lens past the first or the last tab, 0..1; it wobbles through zero on release.
    val edge = remember { Animatable(0f) }
    // The lens's speed, 0..1 of [DockGlass.SPEED_FULL], through [DockGlass.JELLY_SPRING]: below 0 it squashes.
    val jelly = remember { Animatable(0f) }
    // A touch's swell past the lens's size, 0..1 ([DockGlass.SWELL_WIDE]).
    val swell = remember { Animatable(0f) }
    val slotPx = with(LocalDensity.current) { DockGlass.SLOT.toPx() }
    val padPx = with(LocalDensity.current) { DOCK_PAD.toPx() }

    LaunchedEffect(motion, slotPx) {
        if (!motion) return@LaunchedEffect
        snapshotFlow { (drag.speed / slotPx / DockGlass.SPEED_FULL).coerceIn(0f, 1f) }
            .collectLatest { jelly.animateTo(it, DockGlass.JELLY_SPRING) }
    }

    LaunchedEffect(touch.count, touch.down, motion, drag.dragging) {
        if (!motion) {
            // Without Android's animations a tap lifts no lens; a drag still shows it under the finger.
            veil.snapTo(if (drag.dragging) 0f else 1f)
            held.snapTo(if (drag.dragging) 1f else 0f)
            touch.seen = touch.count
            return@LaunchedEffect
        }
        launch { veil.animateTo(0f, tween(DockGlass.VEIL_OUT_MS)) }
        var back: Job? = null
        held.follow(touch, motion, out = DockGlass.LENS_OUT_MS, outEasing = FastOutLinearInEasing) {
            // A tap's click comes a frame or two after the finger lifts, and only then is the lens sent off: it must not
            // settle before it has gone. It settles as it lands, the page still on its way (iOS).
            repeat(2) { withFrameNanos { } }
            snapshotFlow { drag.resting }.first { it }
            back = launch { veil.animateTo(1f, tween(DockGlass.VEIL_IN_MS, easing = LinearOutSlowInEasing)) }
        }
        if (touch.down) return@LaunchedEffect
        back?.join() ?: veil.animateTo(1f, tween(DockGlass.VEIL_IN_MS, easing = LinearOutSlowInEasing))
    }

    LaunchedEffect(touch.count, motion) {
        if (!motion || touch.count == 0) {
            swell.snapTo(0f)
            return@LaunchedEffect
        }
        swell.animateTo(1f, tween(DockGlass.SWELL_IN_MS, easing = LinearOutSlowInEasing))
        swell.animateTo(0f, tween(DockGlass.SWELL_OUT_MS, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(touch.down, motion) {
        if (!motion) {
            breath.snapTo(0f)
            ease.snapTo(0f)
            return@LaunchedEffect
        }
        val to = if (touch.down) 1f else 0f
        launch { breath.animateTo(to, DockGlass.BREATH_SPRING) }
        ease.animateTo(to, tween(if (touch.down) DockGlass.TINT_IN_MS else DockGlass.TINT_OUT_MS))
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
    // How fast the lens moves, 0..1 of [DockGlass.SPEED_FULL], springy; below 0 as it gathers itself after a stop.
    fun speed() = jelly.value.coerceIn(-0.5f, 1f)
    fun stretch() = (DockGlass.STRETCH * speed() + DockGlass.EDGE_STRETCH * edge.value).coerceAtLeast(-0.15f)
    fun lensRect(row: Float, h: Float): Rect {
        val s = held.value
        val st = stretch()
        // From the veil to the lens: the lens sized by the dock's height, [h] and its padding.
        val lensHalfHeight = (h / 2f + padPx) * DockGlass.LENS_HEIGHT
        val sw = swell.value
        val halfWidth = lerp(slot(row) / 2f, lensHalfHeight * DockGlass.LENS_ASPECT * (1f + DockGlass.SWELL_WIDE * sw), s) * (1f + st)
        val halfHeight = lerp(h / 2f, lensHalfHeight * (1f - DockGlass.SWELL_FLAT * sw), s) * (1f - DockGlass.SQUASH * st)
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
    // The dock as wide as its tabs need: the widest label (or «+ Добавить») and [DockGlass.TAB_GAP] each; a narrow
    // screen still squeezes it.
    val labelStyle = dockLabelStyle()
    val labels = DockTabItems.map { stringResource(it.first) } + stringResource(R.string.add)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val dockWidth = remember(labels, labelStyle, density) {
        val widest = labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size.width }
        with(density) { (maxOf(widest.toDp(), DockGlass.ICON) + DockGlass.TAB_GAP) * TABS + DOCK_PAD * 2 }
    }
    val paneTint = scheme.surface.copy(alpha = (GlassMaterial.Surface.tint * (style.tint / 50f).coerceIn(0f, 2f)).coerceIn(0f, 0.92f))
    val veilShade = Color.Black.copy(alpha = if (dark) DockGlass.VEIL_SHADE_DARK else DockGlass.VEIL_SHADE_LIGHT)

    /** The pane's tint over [whole], the dock's size: eased under a finger, and a little more around the lens. */
    fun DrawScope.drawPaneTint(whole: Size) {
        val base = paneTint.alpha * (1f - DockGlass.TINT_EASE * ease.value)
        val s = held.value.coerceIn(0f, 1f)
        val corner = CornerRadius(whole.height / 2f)
        if (s < 0.01f) {
            drawRoundRect(paneTint.copy(alpha = base), size = whole, cornerRadius = corner)
            return
        }
        val pad = DOCK_PAD.toPx()
        val row = whole.width - 2 * pad
        val c = (pad + middle(row)) / whole.width
        val reach = slot(row) * DockGlass.TINT_REACH / whole.width
        val low = base * (1f - DockGlass.TINT_NEAR * s)
        fun tint(alpha: Float) = paneTint.copy(alpha = alpha)
        val brush = Brush.horizontalGradient(
            0f to tint(base),
            (c - reach).coerceIn(0f, 1f) to tint(base),
            (c - reach / 2f).coerceIn(0f, 1f) to tint(low + (base - low) * 0.25f),
            c.coerceIn(0f, 1f) to tint(low),
            (c + reach / 2f).coerceIn(0f, 1f) to tint(low + (base - low) * 0.25f),
            (c + reach).coerceIn(0f, 1f) to tint(base),
            1f to tint(base),
        )
        drawRoundRect(brush, size = whole, cornerRadius = corner)
    }

    // Appearance's sliders tune the cards; the dock keeps its own glass (the owner, 18.11).
    CompositionLocalProvider(LocalGlassStyle provides style) {
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
                    val h = size.height - 2 * pad
                    val rect = lensRect(row, h).translate(pad, pad)
                    val whole = size
                    val z = zoom()
                    // Its bend, colours and mirror with its size, there from its first frames as it swells and to its last
                    // as it shrinks (iOS's): the shader bends by lift².
                    val lift = sqrt(s)
                    lens.draw(
                        this, lensLayer, rect.center, rect.width / 2f, rect.height / 2f, lift, 0f, dark, DockGlass.Look,
                        drawFrom = DockGlass.Look.drawFrom - DockGlass.MOVING_ZOOM * abs(speed()),
                    ) {
                        drawRect(screen, Offset(-whole.width, -whole.height), Size(whole.width * 3, whole.height * 3))
                        // The screen as it lies under the grown dock: [dockAt] is its grown corner (see liquidGlass's zoom).
                        scale(1f / z, 1f / z, pivot = Offset.Zero) {
                            translate(backdrop.origin.x - dockAt.x, backdrop.origin.y - dockAt.y) { drawLayer(backdrop.layer) }
                        }
                        // The dock itself under the lens, its pane and veil as they are: drawn in, its edges come in along an
                        // arc with the wallpaper beyond (the owner, 18.11). The sharp wallpaper alone, magnified, was a fish-eye.
                        drawLayer(dockLayer)
                        translate(pad, pad) { drawLayer(chosenLayer) }
                    }
                },
        ) {
            Surface(
                modifier = Modifier.width(dockWidth).airShadow(shape)
                    .drawWithContent {
                        dockLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(dockLayer)
                    }
                    .liquidGlass(
                        backdrop,
                        base = screen,
                        tint = paneTint,
                        shape = shape,
                        zoom = ::zoom,
                        veil = { drawPaneTint(size) },
                    ),
                shape = shape,
                color = Color.Transparent,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .padding(DOCK_PAD)
                        .onSizeChanged { rowWidth = it.width.toFloat() }
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
                                alpha = veil.value * DockGlass.VEIL_OPACITY
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
                            val row = rowWidth
                            if (!adds && position() != index.toFloat() && row > 0f) {
                                drag.fly(middle(row), tabMiddle(row, index.toFloat(), rtl), pagerMiddle(row, position, rtl))
                            } else {
                                drag.cancel()
                            }
                            if (adds) action() else onSelect(index)
                        },
                        turn = face.turn,
                        actionLabel = if (face.added) actionLabel else null,
                        vertical = false,
                        iconSize = DockGlass.ICON,
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
                        iconSize = DockGlass.ICON,
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
}

/** The middle of the tab at [position] in a row [row] wide. */
private fun tabMiddle(row: Float, position: Float, rtl: Boolean) = dockPillOffset(row, position, rtl = rtl) + dockSlotWidth(row) / 2f

/** Where the page is, as the lens's middle in a row [row] wide. */
private fun pagerMiddle(row: Float, position: () -> Float, rtl: Boolean): () -> Float = { tabMiddle(row, position(), rtl) }

/**
 * The finger leads the lens along the dock past the touch slop: past the first or the last tab it follows less and
 * less, as rubber does, and stretches toward the finger ([edge]); let go, the tab under it is chosen and the lens
 * flies there. A tap is the tabs' own.
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

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val width = size.width.toFloat()
        val slot = dockSlotWidth(width)

        fun clamp(x: Float) = x.coerceIn(slot / 2f, width - slot / 2f)
        fun tabAt(x: Float): Int {
            val raw = (clamp(x) / slot).toInt().coerceIn(0, TABS - 1)
            return if (rtl) TABS - 1 - raw else raw
        }

        var released = false
        try {
            var dragging = false
            var tab = 0

            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                if (!dragging && abs(change.position.x - down.position.x) > slop) {
                    dragging = true
                    drag.begin(drag.center(tabMiddle(width, position(), rtl)))
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
                drag.fly(0f, tabMiddle(width, tab.toFloat(), rtl), pagerMiddle(width, position, rtl))
            }

            released = true
        } finally {
            if (!released) drag.cancel()
            // Let go at the edge, the lens wobbles back into shape.
            scope.launch { edge.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f)) }
        }
    }
}
