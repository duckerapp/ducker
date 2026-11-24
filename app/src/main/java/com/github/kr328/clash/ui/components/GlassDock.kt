package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.sin
import com.github.kr328.clash.R

/**
 * Every number of the glass dock, tuned here only. Fitted frame by frame to the owner's two iOS videos of the App
 * Store's tab bar (18.11; evidence `private/qa/glass-session6-20261118/ios-dock/`), the switch's lens (D-542) the model.
 */
/**
 * The phone dock as wide as its tabs need, on glass and Air alike: English's tab ([DockGlass.TAB_MIN]), or the widest
 * label (or «+ Добавить») with [labelRoom] beside it, Air's a little more; a narrow screen still squeezes it. Air's dock spread over the screen's
 * width, up to 420 dp, and read massive beside the glass one (the owner, 23.11).
 */
@Composable
internal fun dockWidth(labelRoom: Dp = DockGlass.LABEL_ROOM): Dp {
    val labelStyle = dockLabelStyle()
    val labels = DockTabItems.map { stringResource(it.first) } + stringResource(R.string.add)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, labelStyle, density, labelRoom) {
        val widest = labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size.width }
        with(density) { maxOf(DockGlass.TAB_MIN, maxOf(widest.toDp(), DockGlass.ICON) + labelRoom) * TABS + DOCK_PAD * 2 }
    }
}

internal object DockGlass {
    /** The whole dock grows by this share under a finger (iOS: +4.6 % at the peak, +4.2 % held), about its middle. */
    const val BREATH = 0.1f

    /**
     * iOS's: the peak 170 ms after the touch, ~10 % past; let go, back past its size by ~10 % 200 ms later. It breathes
     * while the lens stands, not only the finger: on a short tap, the finger's ~60 ms, it rose and dropped back with the
     * lens still at its peak (the owner, 18.11: «док бар начинает увеличиваться … и потом резко падает обратно»); and
     * back more softly.
     */
    val BREATH_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f)
    val BREATH_BACK: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 260f)

    /**
     * The dock's own glass, whatever Appearance's sliders say (the owner, 18.11): those sliders tune the cards. Over the
     * app's defaults: less blur (iOS's dock is nearly clear), a gentler rim bend, no dome or inner top shadow (`depth`
     * drives both), no grain (`noise`; iOS has none).
     */
    // Tuned by the owner (22.11, the panel's export).
    const val BLUR = 0
    // 45 until the owner's export of 23.11 (F-585).
    const val TINT = 40
    const val REFRACTION = 66
    const val DISPERSION = 50
    const val DEPTH = 70
    const val HIGHLIGHT = 50
    const val NOISE = 0
    const val VIBRANCY = 50

    /**
     * Its tint eases under a finger rather than its colour changing (iOS, dark: 29 → 40 of 255; the owner, 18.11: «не
     * цвет, а тонировку»), by [TINT_EASE]; around the lens it eases a little more, by [TINT_NEAR], over [TINT_REACH] of a
     * tab each way, so the lens carries a clearer patch along with it.
     */
    const val TINT_EASE = 0.5f
    const val TINT_NEAR = 0f
    // On a light screen much less: there the white tint is what makes the dock a pane, and easing it as on a dark one
    // opened a dark hole round the lens and closed it again (the owner, 18.11: «резкие моменты»); iOS's stays white.
    const val TINT_EASE_LIGHT = 0.08f
    const val TINT_NEAR_LIGHT = 0.06f
    const val TINT_REACH = 0f
    const val TINT_IN_MS = 150
    const val TINT_OUT_MS = 400

    /**
     * The chosen tab's veil at rest: the screen under it blurred more than the pane's and a little darker, its colours
     * kept (iOS over pictures); black over the pane's tint on a dark and on a light screen. At 0.45 it read as a black
     * blot (the owner's video of ours, 18.11). [VEIL_OPACITY]: the whole veil shows the pane through it (the owner,
     * 18.11: 35 % clearer, and no light at its edges — a backing, not a pane).
     */
    const val VEIL_SHADE_DARK = 0.25f
    // Light: a pale grey a little under the white pane, as iOS's; at 0.06 over the tint drawn again it came out whiter.
    const val VEIL_SHADE_LIGHT = 0.14f
    val VEIL_BLUR = 0.dp
    const val VEIL_OPACITY = 1f

    /**
     * A tab's width: the widest label plus this, so labels stand about as far apart as the first one from the dock's
     * end. Spread over the whole width, the tabs stood ~60 dp apart and the lens grew into a big flat oval (the owner,
     * 18.11). The veil fills its tab, so it lies as far from the dock's ends as from its top and bottom; its shape comes
     * out ~1.45 : 1, as iOS's.
     */
    val TAB_GAP = 36.dp

    /**
     * A tab's width at least: English's, the widest label («Profiles», ~46 dp) and [TAB_GAP]. A longer label takes only
     * [LABEL_ROOM] beside it: by the widest label alone «Подписки» (~62 dp) made the Russian dock 300 dp, Air's width
     * (the owner, 23.11: «на англ все норм а на ру нет»).
     */
    val TAB_MIN = 46.dp + TAB_GAP
    val LABEL_ROOM = 20.dp

    /** The tabs' icons, a little larger than Air's 20 dp (the owner, 18.11); the labels unchanged. */
    val ICON = 23.dp

    /**
     * The lens, held: a capsule over the dock's edges all round and past its end over the first and last tab, ~1.08 ×
     * the dock's height and 1.39 × as wide as high (iOS's App Store: ~1.06-1.08 and 1.5). Its straight top and bottom
     * draw the dock's edges in flat, ending at the rim in rounded steps; nearly round (1.22 high, 1.23 wide), it bowed
     * them into a barrel (the owner's sketch, 18.11). The width kept from that, iOS's less 15 % (the owner, 18.11).
     */
    const val LENS_HEIGHT = 1.06f
    const val LENS_ASPECT = 1.35f

    /**
     * A touch: the veil, swelling with the lens in its shape, gone in 100 ms on the way (iOS's: it grows into the lens and
     * fades out as it goes; ours went in a frame and left an empty ring, the owner, 18.11); the lens swells in 180 ms ([follow]). Landed: the lens shrinks back in 260 ms,
     * slowly at first, still glass, and softly into the veil, which returns over 220, most of it early. iOS's short tap
     * is one bubble, ~150 ms up past its held size and ~150 ms down, glass to the end; ours, 130 ms down from the start
     * at full speed and its bend gone first, seemed to die before it had come (the owner, 18.11, `IMG_0050.mp4`); at
     * 120 + 160 ms, ending at full speed, still abrupt («плавнее и длительней»). Shrinking, the lens fills with the veil
     * as it goes ([veilGathered]): back on its own 220 ms from the start, the veil was there before the lens had gone,
     * and the lens, slow then fast (0.5, 0, 0.3, 1 over 260 ms), fell into it in a frame (the owner, 18.11: «подложка
     * появляется, а не из линзы она как бы образуется»).
     */
    const val VEIL_OUT_MS = 100
    const val LENS_IN_MS = 180
    const val LENS_OUT_MS = 300
    val LENS_OUT_EASING: Easing = CubicBezierEasing(0.3f, 0f, 0.3f, 1f)
    const val VEIL_IN_MS = 220

    /**
     * Drawn in at an end ([EDGE_STRETCH]), the lens grows this much taller for what it loses in width. No stretch by
     * its speed: once it ran (it never had, its speed unseen), a tap's flight stretched it hard; over three tabs there is
     * no room to speed up (the owner, 18.11: «давай не будем заморачиваться с этим растягиванием»).
     */
    const val SQUASH = 0.35f

    /**
     * A touch: the lens swells on past its size, wider and a little flatter, and settles back. iOS's App Store, held:
     * 1.17 × as wide at ~270 ms from the touch, back by ~450 ms later (`ios-dock/appear/`); it grew to its size and stood.
     */
    const val SWELL_WIDE = 0.28f
    /** And magnifies more at its peak (iOS's tap: the icon 1.08 × its held size), the rim then drawing out what lies at it. */
    const val SWELL_ZOOM = 0.06f
    const val SWELL_FLAT = 0.2f
    const val SWELL_IN_MS = 270
    const val SWELL_OUT_MS = 450

    /**
     * Past the first or the last tab: how far the lens gives (F-528; iOS's goes ~13 pt past the dock's end under a finger,
     * so 16 dp, not 8), and how much it draws in there, narrower and a
     * little taller, as against a wall; stretched there, it did not read as iOS's (the owner, 18.11: «втягиваться, без
     * фанатизма»).
     */
    val EDGE_GIVE = 16.dp

    /**
     * The dock follows the finger like jelly pulled: about its middle it moves towards the finger, stretches along the
     * pull and narrows across, all of it, text and icons too, rubber-banded over the finger's way and chased on a soft
     * spring. iOS's (`IMG_0054.mp4`, the finger up to the screen's top; `ios-dock/ios5/`): moved up ~7 % of its height,
     * ~6 % taller, ~4-5 % narrower, its labels ~5 % narrower with it, the left tabs ~28 px in; past an end ~2 % across
     * (`IMG_0052.mp4`). Stretched from its far side, unsprung and jerking, it looked wrong live; only moved, it was not
     * iOS's (the owner, 18.11). The lens is drawn in it and goes with it.
     */
    val PULL_GIVE_Y = 150.dp
    val PULL_GIVE_X = 40.dp
    val PULL_MOVE_Y = 4.5.dp
    val PULL_MOVE_X = 3.dp
    const val PULL_ALONG_Y = 0.06f
    const val PULL_ACROSS_Y = 0.045f
    const val PULL_ALONG_X = 0.025f
    const val PULL_ACROSS_X = 0.015f
    // Underdamped both ways, a jelly's bubble (the owner, 18.11: «как будто бульканье»).
    val PULL_FOLLOW: SpringSpec<Float> = spring(dampingRatio = 0.42f, stiffness = 320f)
    val PULL_BACK: SpringSpec<Float> = spring(dampingRatio = 0.38f, stiffness = 260f)
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
     * colours parted by the whole shift fringed the middle too (`ios-dock/ours-rim.png`). Its ends take in more still
     * (push 1.2 over 0.45 r, along ×1.1; across ×0.7 keeps the top and bottom bands as they were): on iOS's the text it
     * comes to fills its edges thick («прям жирно становится внутри»; `ios-dock/ours-rim2.png`, now above, new below).
     * On a light screen the rim light 0.15, not 0.8 (a thick white ring), and soft colours along the rim's inside
     * (`irisLight`), as iOS's there (`ios-dock/light-rim.png`, before above); the dark screen's look unchanged.
     * Softer at the rim (the owner, 19.11, F-556): the labels mirrored into the top and bottom read as sharp bright specks
     * — the mirror's band 0.1 r, not 0.04, softened over a grid 0.07 r apart and laid at 0.6; along the ends the dock's
     * edge, drawn in, read as a second, rainbow frame inside the lens's own — spread more along the rim (smear 1.2, not
     * 0.6), its colours parting less (chroma 0.06, disperse 0.2); on a dark screen 3 % lighter, not 7 %: under a finger
     * the lens read milky. Fitted on the numpy copy over a dock like today's (`glass-session8-20261119/lens/`).
     */
    val Look = LensLook(
        drawFrom = -0.09f, zoomEdge = 0.02f, push = 0.34f, pushPower = 6f, pushBand = 0.52f, pushAlong = 1.06f, pushAcross = 1.52f, pushEndFrom = 0.13f, pushEndTo = 0.46f, chroma = 0.07f, disperse = 0.08f, smear = 0.83f, bandSide = 0.5f, bandEnd = 0.12f, mirror = 0.4f, mirrorSoft = 0.03f, mirrorChroma = 0.43f, mirrorMix = 0.7f, ring = 0f, rimShade = 0f, lighten = 0f, shineDark = 0.15f, shineLight = 0.15f, shineEnds = 0f, irisDark = 0f, irisLight = 0.12f, shadowDark = 0.06f, shadowLight = 0f, clearBlur = 0.dp, frostBlur = 0.dp,
    )

    /**
     * On a light screen the colours split half as much: the dark it draws in against the white pane split into a rainbow
     * all round, and the rim's colours at 0.3 were too much (the owner, 18.11: «радуга очень агрессивная … все в радуге»).
     */
    // Half the dark screen's colours, as before; its lightness as before too: there the lens is not milky.
    // The light theme's, as before the dark one was tuned (the owner, 22.11).
    val LookLight = LensLook(
        drawFrom = -0.13f, zoomEdge = 0.12f, push = 1.2f, pushPower = 6f, pushBand = 0.45f, pushAlong = 1.1f, pushAcross = 0.7f, pushEndFrom = 0.1f, pushEndTo = 0.6f, chroma = 0.03f, disperse = 0.1f, smear = 1.2f, bandSide = 0.1f, bandEnd = 0.22f, mirror = 0.9f, mirrorSoft = 0.07f, mirrorChroma = 0.15f, mirrorMix = 0.6f, ring = 0.15f, rimShade = 0f, lighten = 0.07f, shineDark = 0.35f, shineLight = 0.15f, shineEnds = 0.7f, irisDark = 0f, irisLight = 0.12f, shadowDark = 0f, shadowLight = 0f, clearBlur = 0.dp, frostBlur = 0.dp,
    )

    /**
     * Its body's own light line, the cards' (`DOCK_LINE_SHADER`), over its own glass instead of its thin rim light: the
     * owner's choice (22.11, the panel's export).
     */
    const val OWN_LINE = true
    const val LINE = 0.3f
    val LINE_WIDTH = 2.59.dp
    val LINE_SPREAD = 0.dp
    const val LINE_GLOW = 0f
    const val LINE_FALL = 2f
    const val LINE_DIRECT = 1f

    /** The share of the lens, shrinking, over which the veil takes over from its filling. */
    const val HANDOVER = 0.3f
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
    val tuning = LocalGlassTuning.current
    val style = tuning.optics(GlassTuningGroup.Dock, appStyle.dockDefaults())
    // «Док 2» (review app): the body in the cards' pane glass, tuned by its own group from the cards' settings; the chosen
    // tab's veil and the lens keep the dock's own (the owner, 22.11: the dock's body follows its own rules).
    val dockPane = tuning?.enabled == true && tuning.dockPane
    val bodyStyle = if (dockPane) tuning.optics(GlassTuningGroup.DockPane, appStyle.asDock()) else style
    fun body(key: String, fallback: Float, byTheme: Boolean = false) =
        tuning?.value(GlassTuningGroup.Dock, key, fallback, byTheme && !dark) ?: fallback
    fun veilValue(key: String, fallback: Float, byTheme: Boolean = false) =
        tuning?.value(GlassTuningGroup.Veil, key, fallback, byTheme && !dark) ?: fallback
    fun lensValue(key: String, fallback: Float) =
        tuning?.value(GlassTuningGroup.DockLens, key, fallback, !dark) ?: fallback
    val lensTheme = tuning.lens(GlassTuningGroup.DockLens, if (dark) DockGlass.Look else DockGlass.LookLight, !dark)
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
    // The finger off its touch, pixels: across (past the first or last tab) and up or down. The dock stretches after
    // it ([DockGlass.PULL_GIVE_Y]).
    val pullX = remember { Animatable(0f) }
    val pullY = remember { Animatable(0f) }
    // A touch's swell past the lens's size, 0..1 ([DockGlass.SWELL_WIDE]).
    val swell = remember { Animatable(0f) }
    val padPx = with(LocalDensity.current) { DOCK_PAD.toPx() }
    val pullGiveXPx = with(LocalDensity.current) { DockGlass.PULL_GIVE_X.toPx() }
    val pullGiveYPx = with(LocalDensity.current) { DockGlass.PULL_GIVE_Y.toPx() }
    val pullMoveXPx = with(LocalDensity.current) { DockGlass.PULL_MOVE_X.toPx() }
    val pullMoveYPx = with(LocalDensity.current) { DockGlass.PULL_MOVE_Y.toPx() }

    LaunchedEffect(touch.count, touch.down, motion, drag.dragging) {
        if (!motion) {
            // Without Android's animations a tap lifts no lens; a drag still shows it under the finger.
            veil.snapTo(if (drag.dragging) 0f else 1f)
            held.snapTo(if (drag.dragging) 1f else 0f)
            touch.seen = touch.count
            return@LaunchedEffect
        }
        launch { veil.animateTo(0f, tween(DockGlass.VEIL_OUT_MS, easing = LinearEasing)) }
        var back: Job? = null
        held.follow(touch, motion, out = DockGlass.LENS_OUT_MS, outEasing = DockGlass.LENS_OUT_EASING, inMs = DockGlass.LENS_IN_MS) {
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

    // Under a finger, or while the lens stands for a touch already gone.
    val lifted by remember { derivedStateOf { touch.down || held.targetValue > 0.5f } }
    LaunchedEffect(lifted, motion) {
        if (!motion) {
            breath.snapTo(0f)
            ease.snapTo(0f)
            return@LaunchedEffect
        }
        val to = if (lifted) 1f else 0f
        launch { breath.animateTo(to, if (lifted) DockGlass.BREATH_SPRING else DockGlass.BREATH_BACK) }
        ease.animateTo(to, tween(if (lifted) DockGlass.TINT_IN_MS else DockGlass.TINT_OUT_MS))
    }

    fun zoom() = 1f + body("breath", DockGlass.BREATH) * breath.value

    // The finger's way, rubber-banded to −1..1.
    fun pulledX() = pullX.value / (abs(pullX.value) + pullGiveXPx)
    fun pulledY() = pullY.value / (abs(pullY.value) + pullGiveYPx)
    // The dock's scale: its breath, stretched along the pull and narrowed across.
    fun dockScale(): ScaleFactor {
        val z = zoom()
        val x = abs(pulledX())
        val y = abs(pulledY())
        return ScaleFactor(
            z * (1f + DockGlass.PULL_ALONG_X * x) * (1f - DockGlass.PULL_ACROSS_Y * y),
            z * (1f + DockGlass.PULL_ALONG_Y * y) * (1f - DockGlass.PULL_ACROSS_X * x),
        )
    }

    // Shrinking, the lens fills with the veil's shade as it goes, evenly from rim to rim ([veilGathered], drawn under
    // the lens's glass over all it shows), and the veil itself waits: under the lens its edge, drawn in by the rim,
    // showed a smaller blot growing out to the rim (the owner, 18.11: «наоборот растёт к линзе»).
    fun shrinking() = held.targetValue < 0.5f && held.value >= LENS_SHOWN
    // Over the lens's last third the lens itself fades out over the dock as the veil comes in under it: swapped at the
    // end, the veil (blurred more, tinted otherwise) stepped in at once — unseen dark on dark, plain on a light screen —
    // and the lens's resampled text and icons, softer, turned sharp in a frame (the owner, 18.11).
    fun handover(): Float = if (shrinking()) 1f - (held.value / DockGlass.HANDOVER).coerceIn(0f, 1f) else 0f
    fun veilShown(): Float = if (shrinking()) handover() else veil.value
    fun veilGathered(): Float = if (shrinking()) (1f - held.value).pow(1.3f) else 0f

    // The geometry, in the row of tabs [row] wide and [h] high.
    fun slot(row: Float) = dockSlotWidth(row)
    fun middle(row: Float) = drag.center(dockPillOffset(row, position(), rtl = rtl) + slot(row) / 2f)
    fun veilRect(row: Float, h: Float): Rect {
        val width = lerp(slot(row), row, face.merge)
        val left = lerp(middle(row) - slot(row) / 2f, 0f, face.merge)
        return Rect(left, 0f, left + width, h)
    }
    fun stretch() = DockGlass.EDGE_STRETCH * edge.value
    fun lensRect(row: Float, h: Float): Rect {
        val s = held.value
        val st = stretch()
        // From the veil to the lens: the lens sized by the dock's height, [h] and its padding.
        val lensHalfHeight = (h / 2f + padPx) * lensValue("height", DockGlass.LENS_HEIGHT)
        val sw = swell.value
        val halfWidth = lerp(slot(row) / 2f, lensHalfHeight * lensValue("aspect", DockGlass.LENS_ASPECT) * (1f + lensValue("swellWide", DockGlass.SWELL_WIDE) * sw), s) * (1f + st)
        val halfHeight = lerp(h / 2f, lensHalfHeight * (1f - lensValue("swellFlat", DockGlass.SWELL_FLAT) * sw), s) * (1f - DockGlass.SQUASH * st)
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
    val dockWidth = dockWidth()
    val paneTint = scheme.surface.copy(alpha = (GlassMaterial.Surface.tint * (bodyStyle.tint / 50f).coerceIn(0f, 2f)).coerceIn(0f, 0.92f))
    val veilShade = Color.Black.copy(alpha = veilValue("shade", if (dark) DockGlass.VEIL_SHADE_DARK else DockGlass.VEIL_SHADE_LIGHT, byTheme = true))

    /** The pane's tint over [whole], the dock's size: eased under a finger, and a little more around the lens. */
    fun DrawScope.drawPaneTint(whole: Size) {
        val base = paneTint.alpha * (1f - body("tintEase", if (dark) DockGlass.TINT_EASE else DockGlass.TINT_EASE_LIGHT, byTheme = true) * ease.value)
        val s = held.value.coerceIn(0f, 1f)
        val corner = CornerRadius(whole.height / 2f)
        if (s < LENS_SHOWN) {
            drawRoundRect(paneTint.copy(alpha = base), size = whole, cornerRadius = corner)
            return
        }
        val pad = DOCK_PAD.toPx()
        val row = whole.width - 2 * pad
        val c = (pad + middle(row)) / whole.width
        val reach = slot(row) * body("tintReach", DockGlass.TINT_REACH) / whole.width
        val low = base * (1f - body("tintNear", if (dark) DockGlass.TINT_NEAR else DockGlass.TINT_NEAR_LIGHT, byTheme = true) * s)
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
                    val scale = dockScale()
                    scaleX = scale.scaleX
                    scaleY = scale.scaleY
                    translationX = pullMoveXPx * pulledX()
                    translationY = pullMoveYPx * pulledY()
                }
                .onGloballyPositioned { dockAt = it.positionInRoot() }
                .drawWithContent {
                    drawContent()

                    val s = held.value
                    if (lens == null || s < LENS_SHOWN) return@drawWithContent
                    val pad = DOCK_PAD.toPx()
                    val row = size.width - 2 * pad
                    val h = size.height - 2 * pad
                    val rect = lensRect(row, h).translate(pad, pad)
                    val whole = size
                    val scale = dockScale()
                    // Its bend, colours and mirror with its size, there from its first frames as it swells and to its last
                    // as it shrinks (iOS's): the shader bends by lift².
                    val lift = sqrt(s)
                    // Its lightness and light line go with its last third too: whole to its last frame, they blinked
                    // out with it as the veil came back (the owner, 18.11: «мигание … при отпускании пальца»).
                    val fade = (s / 0.3f).coerceAtMost(1f)
                    val theme = lensTheme
                    val look = if (fade >= 1f) theme else theme.run {
                        copy(
                            lighten = lighten * fade, shineDark = shineDark * fade, shineLight = shineLight * fade,
                            ring = ring * fade, irisLight = irisLight * fade,
                        )
                    }
                    lensLayer.alpha = 1f - handover()
                    lens.draw(
                        this, lensLayer, rect.center, rect.width / 2f, rect.height / 2f, lift, 0f, dark, look,
                        drawFrom = lensTheme.drawFrom - lensValue("swellZoom", DockGlass.SWELL_ZOOM) * swell.value,
                    ) {
                        drawRect(screen, Offset(-whole.width, -whole.height), Size(whole.width * 3, whole.height * 3))
                        // The screen as it lies under the grown dock: [dockAt] is its grown corner (see liquidGlass's zoom).
                        scale(1f / scale.scaleX, 1f / scale.scaleY, pivot = Offset.Zero) {
                            translate(backdrop.origin.x - dockAt.x, backdrop.origin.y - dockAt.y) { drawLayer(backdrop.layer) }
                        }
                        // The dock itself under the lens, its pane and veil as they are: drawn in, its edges come in along an
                        // arc with the wallpaper beyond (the owner, 18.11). The sharp wallpaper alone, magnified, was a fish-eye.
                        drawLayer(dockLayer)
                        val gathered = veilGathered() * veilValue("opacity", DockGlass.VEIL_OPACITY)
                        if (gathered > 0f) {
                            val all = Size(whole.width * 3, whole.height * 3)
                            val from = Offset(-whole.width, -whole.height)
                            // Over the pane already tinted: the veil's shade alone, as the veil adds it (its tint again made
                            // it lighter than the veil that takes over, a blink).
                            drawRect(veilShade, from, all, alpha = gathered)
                        }
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
                        zoom = ::dockScale,
                        veil = { drawPaneTint(size) },
                        dockOptics = !dockPane,
                        paneGroup = GlassTuningGroup.DockPane,
                        glassStyle = bodyStyle,
                    ),
                shape = shape,
                color = Color.Transparent,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .padding(DOCK_PAD)
                        .onSizeChanged { rowWidth = it.width.toFloat() }
                        .then(if (alone) Modifier else Modifier.fingerOn(touch).dockDrag(drag, scope, edge, pullX, pullY, position, onSelect, rtl)),
                ) {
                    // The veil: its own glass, darker and blurred more than the pane, so the chosen label reads on any screen.
                    Box(
                        Modifier.matchParentSize()
                            .layout { measurable, constraints ->
                                // In the lens's shape as it swells or shrinks: it grows into the lens and back out of it.
                                val rect = chosen(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                                val placeable = measurable.measure(
                                    Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
                            }
                            .graphicsLayer {
                                alpha = veilShown() * veilValue("opacity", DockGlass.VEIL_OPACITY)
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
                                if (veilShown() < 0.003f) return@drawWithContent
                                drawContent()
                                val drop = face.splash.value
                                if (drop > 0f && drop < 1f) drawSplash(Rect(Offset.Zero, size), CornerRadius(size.height / 2f), drop)
                            }
                            .liquidGlass(
                                backdrop,
                                base = screen,
                                tint = paneTint,
                                shape = shape,
                                blurBoost = veilValue("blur", DockGlass.VEIL_BLUR.value).dp,
                                bendBoost = 0f,
                                rimBoost = 0f,
                                veil = {
                                    drawRect(paneTint)
                                    drawRect(veilShade)
                                },
                                zoom = ::dockScale,
                                dockOptics = true,
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
    pullX: Animatable<Float, *>,
    pullY: Animatable<Float, *>,
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
            var grab = 0f
            var grabAt = 0f

            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dy = change.position.y - down.position.y
                // Chased on a spring that keeps its speed: the sway lags and gives like jelly.
                scope.launch { pullY.animateTo(dy, DockGlass.PULL_FOLLOW) }

                if (!dragging && abs(change.position.x - down.position.x) > slop) {
                    dragging = true
                    val from = drag.center(tabMiddle(width, position(), rtl))
                    drag.begin(from)
                    // The lens sets off from where it is and comes under the finger over a tab's way: sent at once to the
                    // finger — past the slop, off the tab's middle — it jumped as it tore away (the owner, 18.11).
                    grab = from - change.position.x
                    grabAt = change.position.x
                }

                if (dragging) {
                    // Taken: the tab under the finger must not also take it as a tap.
                    change.consume()

                    val raw = change.position.x + grab * (1f - (abs(change.position.x - grabAt) / slot).coerceAtMost(1f))
                    val x = clamp(raw)
                    val over = raw - x
                    val give = DockGlass.EDGE_GIVE.toPx()

                    drag.move(x + over * give / (abs(over) + give))
                    scope.launch { pullX.animateTo(over, DockGlass.PULL_FOLLOW) }
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
            // Let go at the edge, the lens wobbles back into shape, and the dock back into its place.
            scope.launch { edge.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f)) }
            scope.launch { pullX.animateTo(0f, DockGlass.PULL_BACK) }
            scope.launch { pullY.animateTo(0f, DockGlass.PULL_BACK) }
        }
    }
}

// Below this the lens is not drawn: the veil shows again from the same frame, or the dock blinked with neither.
private const val LENS_SHOWN = 0.01f
