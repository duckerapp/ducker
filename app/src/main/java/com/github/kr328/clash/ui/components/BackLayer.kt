package com.github.kr328.clash.ui.components

import com.github.kr328.clash.ui.theme.LocalBackdropTone
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Экран поверх вкладок: настройки и то, что открывается из них (D-91).
 *
 * Лежит слоем над лентой вкладок, а не вместо неё. Раньше лента на время настроек
 * уходила из композиции и при возврате собиралась заново — три страницы в один кадр,
 * да ещё под затухающими настройками. Теперь она всё время собрана внизу, закрытие
 * ничего не пересоздаёт, а жест «назад» показывает то, что лежит под экраном: экран
 * становится карточкой и едет за пальцем, под ним видна главная, а отпущенный — уезжает
 * за край экрана (D-95).
 *
 * [enabled] — ловить ли «назад»: у настроек его забирает открытый поверх экран. Заодно
 * закрытый экран прячется от TalkBack, как вкладки под настройками: человек его не видит.
 * [wide] — без предела ширины, см. [ScreenFrame].
 *
 * [frameContent] is false when content owns a separate [ScreenFrame] for each page.
 * A shared frame above nested layers would paint a stationary status-bar gradient
 * over the moving page during predictive Back.
 *
 * [fade] draws [NavigationFade] over the screen's bottom; false for a layer inside one that already draws it,
 * and for a screen with its own controls over the bar.
 */
@Composable
fun BackLayer(
    visible: Boolean,
    onBack: () -> Unit,
    enabled: Boolean = true,
    wide: Boolean = false,
    frameContent: Boolean = true,
    fade: Boolean = true,
    /**
     * The glass style: Home's picture under the screen instead of the plain background, its cards glass over it (D-540).
     * A layer inside a glass one (Settings' pages) is glass too.
     */
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    /** The picture's tone at the top, for the header drawn on it, see [BackdropTone]. */
    backdropTone: Boolean? = LocalBackdropTone.current,
    /**
     * Comes in from the side the reading goes to and leaves back there, as a page turned over the one under it:
     * Settings' sections (the owner, 24.11). Otherwise it grows in from the middle.
     */
    slide: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The card's corners: from the screen's own from the gesture's start, so they meet the display's (the owner,
    // 24.11), to at least [CORNER] at its end. Android tells the display's from 12; before it, a modest guess.
    val view = LocalView.current
    val density = LocalDensity.current
    val screenCorner = remember(view, density) {
        val corner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
        } else null
        maxOf(corner ?: 0f, with(density) { FALLBACK_CORNER.toPx() })
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Not `LocalWindowInfo.containerSize`: it can lag a turn of the phone behind (D-478).
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    // Ход жеста: 0 — экран на месте, 1 — палец дотянут до конца. От него — уменьшение,
    // углы и тень: по ним видно, что экран стал карточкой.
    val progress = remember { Animatable(0f) }
    // Сдвиг карточки, пиксели: догоняет палец пружиной [BackFollow].
    val offset = remember { Animatable(0f) }
    // Карточка уже уезжает — второй жест поверх неё не ловим.
    var leaving by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = visible && enabled && !leaving) { events ->
        var start = Float.NaN
        var fromLeft = true

        try {
            events.collect {
                if (start.isNaN()) start = it.touchX

                fromLeft = it.swipeEdge == BackEventCompat.EDGE_LEFT

                progress.snapTo(it.progress)

                // Undispatched: the newest chase takes the offset at once, and the leave below takes it from the last.
                val target = (it.touchX - start).coerceIn(-width, width)
                scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.animateTo(target, BackFollow) }
            }

            // Отпустил — карточка уезжает за край туда, куда её тянули, и только потом
            // экран закрывается: гаснуть ему уже некому, он за краем.
            leaving = true

            val direction = when {
                offset.value > 0f -> 1f
                offset.value < 0f -> -1f
                fromLeft -> 1f
                else -> -1f
            }

            offset.animateTo(direction * width * LEAVE_DISTANCE, tween(LEAVE_MS, easing = LinearOutSlowInEasing))

            onBack()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, tween(CANCEL_MS)) }
            scope.launch { offset.animateTo(0f, tween(CANCEL_MS)) }

            throw e
        }
    }

    // Пульт (этап 6): экран открылся или снова стал верхним — фокус в него, туда, где стоял.
    // Только с клавишами: на касании фокус встал бы в поле поиска и поднял клавиатуру.
    val focus = remember { FocusRequester() }
    val room = remember { RoomFocus() }
    val input = LocalInputModeManager.current
    val tv = LocalTv.current

    // A screen that already has the focus keeps it where it is. Settings and the page open in them become top
    // together when a screen over them closes: the outer layer's request already restored the row, and the page's
    // second request entered it anew, on its back arrow.
    LaunchedEffect(visible && enabled) {
        if (tv && visible && enabled && input.inputMode == InputMode.Keyboard && !room.focused) focus.requestFocus()
    }

    // The top screen takes back focus that got lost on it, see [TvFocus.cover].
    val tvFocus = LocalTvFocus.current

    DisposableEffect(tvFocus, visible && enabled) {
        if (visible && enabled) tvFocus?.cover = focus

        onDispose { if (tvFocus != null && tvFocus.cover === focus) tvFocus.cover = null }
    }

    // Открыли снова — от прошлого жеста не должно остаться сдвинутой карточки.
    LaunchedEffect(visible) {
        if (visible) {
            progress.snapTo(0f)
            offset.snapTo(0f)
            leaving = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        // A sliding page moves itself, below; the screen under it only dims.
        enter = if (slide) EnterTransition.None else fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), initialScale = 0.96f),
        // Ушёл жестом — карточка уже за краем, гасить нечего. Обычное закрытие ещё и
        // уменьшает от центра и подтягивало бы уехавшую карточку обратно к экрану.
        exit = if (leaving || slide) ExitTransition.None
        else fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.94f),
    ) {
        // Opened again after leaving by the gesture: the card's last offset, shrink and corners are cleared a frame late
        // (the effect above), and a page sliding in from them jumped (the owner, 24.11). Until it is in, they don't
        // count. Not while it leaves: that runs with the page settled in.
        val stale = { leaving && transition.currentState != EnterExitState.Visible && transition.targetState == EnterExitState.Visible }
        val pull = { if (stale()) 0f else progress.value }
        val shift = { if (stale()) 0f else offset.value }

        // How far a sliding page has come in: the screen under it dims as it is covered.
        val covered = if (slide) transition.animateFloat(
            transitionSpec = { tween(if (targetState == EnterExitState.Visible) SLIDE_IN_MS else SLIDE_OUT_MS, easing = LinearEasing) },
            label = "covered",
        ) { if (it == EnterExitState.Visible) 1f else 0f } else null

        // Под уменьшенным экраном — главная того же цвета, и без затемнения и тени край
        // экрана не читался: казалось, что настройки просто съехали вбок.
        Box(
            Modifier.fillMaxSize().drawBehind {
                // Затемнение тает по мере того, как карточка уходит с экрана.
                val gone = if (width > 0f) (abs(shift()) / width).coerceIn(0f, 1f) else 0f
                val dim = maxOf(SCRIM * eased(pull()), SLIDE_SCRIM * (covered?.value ?: 0f))

                drawRect(Color.Black, alpha = dim * (1f - gone))
            },
        )

        Box(
            Modifier.fillMaxSize()
                .then(
                    if (slide) Modifier.animateEnterExit(
                        enter = slideInHorizontally(tween(SLIDE_IN_MS, easing = SlideInEasing)) { if (rtl) -it else it },
                        exit = if (leaving) ExitTransition.None
                        else slideOutHorizontally(tween(SLIDE_OUT_MS, easing = SlideOutEasing)) { if (rtl) -it else it },
                    ) else Modifier,
                )
                .graphicsLayer {
                    // Читается здесь, а не в композиции: каждый кадр жеста трогает
                    // только этот слой.
                    val p = eased(pull())
                    val scale = 1f - SHRINK * p

                    scaleX = scale
                    scaleY = scale
                    translationX = shift()
                    shape = RoundedCornerShape(lerp(screenCorner, maxOf(screenCorner, CORNER.toPx()), p))
                    clip = p > 0f
                    shadowElevation = ELEVATION.toPx() * p
                }
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (backdrop != null) {
                        Modifier.backdropPicture(backdrop) {
                            progress.value
                            offset.value
                            transition.playTimeNanos
                        }
                    } else {
                        Modifier
                    },
                )
                // Пустое место экрана — тоже экран: касание не должно проваливаться
                // к вкладкам под ним и листать их.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }
                .then(if (enabled) Modifier else Modifier.clearAndSetSemantics {})
                .then(if (tv) Modifier.onFocusChanged { room.focused = it.hasFocus }.focusRoom(focus) else Modifier),
        ) {
            CompositionLocalProvider(
                LocalGlassBackdrop provides backdrop,
                LocalBackdropTone provides if (backdrop != null) backdropTone else null,
            ) {
                // The tabs' pane glass on a screen over Home's picture, so the two read as one glass (D-544).
                GlassPanes(on = backdrop != null) {
                    // On a plain background the panes are tuned apart ([GlassTuningGroup.PlainPane]), from the cards'.
                    // One call either way, as in GlassPanes: the content keeps its state when the background changes.
                    val tuning = LocalGlassTuning.current
                    val plain = backdrop != null && screenWallMode(tuning) != WallMode.PICTURE
                    CompositionLocalProvider(
                        LocalPaneGroup provides if (plain) GlassTuningGroup.PlainPane else LocalPaneGroup.current,
                        LocalGlassStyle provides
                            if (plain) tuning.optics(GlassTuningGroup.PlainPane, LocalGlassStyle.current.asPlain()) else LocalGlassStyle.current,
                    ) {
                        if (frameContent) ScreenFrame(wide, content = content) else content()
                    }
                }
            }

            if (fade) NavigationFade()
        }
    }
}

/**
 * Home's picture where it lies in the window, as under the tabs: the glass of the cards on it shows the picture at their
 * place in the window, so a picture moved with the screen would not meet it. Drawn back through the whole way from the
 * window to here, the screen's scale too, and anew each frame [moves] reads: placed by its position alone, a frame late,
 * the picture grew and shrank with a screen opening or closing over the same picture, which read as a jerk (the owner,
 * 22.11).
 */
@Composable
private fun Modifier.backdropPicture(backdrop: GlassBackdrop, moves: () -> Unit): Modifier {
    val place = remember { Placement() }
    return onPlaced { place.coordinates = it }.drawBehind {
        moves()
        val here = place.coordinates
        if (here != null && here.isAttached) {
            // Here to the window, then inverted: the inverse Compose keeps for a layer was found stale on a screen opened
            // again after it left by Back, the picture drawn a screen and a bit aside — no picture at all.
            here.findRootCoordinates().transformFrom(here, place.matrix)
            place.matrix.invert()
        }
        withTransform({
            if (here != null && here.isAttached) transform(place.matrix)
            translate(backdrop.origin.x, backdrop.origin.y)
        }) { drawLayer(backdrop.layer) }
    }
}

/** Where a screen's picture is drawn from; read in its draw, not drawn by — no state. */
private class Placement {
    var coordinates: LayoutCoordinates? = null
    val matrix = Matrix()
}

/**
 * What the screens over the tabs lie on ([screenWallMode]): a plain colour (iOS's by default, the owner's choice,
 * 22.11) or Home's picture blurred, tinted and saturated ([GlassTuningGroup.Wall]). Recorded by [screenWallSource], so
 * their glass shows it too.
 */
@Composable
internal fun rememberScreenWall(wall: GlassBackdrop?): GlassBackdrop? {
    if (wall == null) return null
    val screens = rememberGlassBackdrop(still = StillFor.Screens) ?: return wall

    // On the picture it draws [wall]'s layer, so shows its changes at once; its still pictures are made anew with them.
    // The plain background shows none of the picture: those changes are not its own.
    val picture = screenWallMode(LocalGlassTuning.current) == WallMode.PICTURE
    LaunchedEffect(wall, screens, picture) {
        snapshotFlow { wall.origin to wall.version }.collect { (origin, _) ->
            screens.origin = origin
            if (picture) screens.version++
        }
    }

    return screens
}

/** Records [screens] from [wall] (see [rememberScreenWall]) and draws nothing itself. */
@Composable
internal fun Modifier.screenWallSource(screens: GlassBackdrop?, wall: GlassBackdrop?): Modifier {
    val tuning = LocalGlassTuning.current?.takeIf { it.enabled }
    if (screens == null || wall == null || screens === wall) return this
    val plain = MaterialTheme.colorScheme.background
    val light = plain.luminance() > 0.5f
    val picture = rememberGraphicsLayer()
    // The plain background as last recorded: drawn again with the window around it (a screen coming over the tabs), it
    // counted as a new one, and the screens' panes drew their glass live through their first frames (F-582).
    val recorded = remember { arrayOfNulls<Color>(1) }

    return drawBehind {
        val mode = screenWallMode(tuning)
        if (mode != WallMode.PICTURE) {
            val color = if (mode == WallMode.IOS) (if (light) IOS_GROUPED_LIGHT else Color.Black) else plain
            if (recorded[0] == color) return@drawBehind
            recorded[0] = color
            screens.layer.record { drawRect(color) }
            // Not a read of this draw: it would invalidate itself and draw again forever.
            Snapshot.withoutReadObservation { screens.version++ }
            return@drawBehind
        }
        recorded[0] = null
        val group = GlassTuningGroup.Wall
        fun value(key: String, fallback: Float) = tuning?.value(group, key, fallback, light) ?: fallback
        val blur = value("blur", wallBlur(!light)).dp.toPx()
        val saturation = value("saturation", 1f)
        val tint = value("tint", wallTint(!light))
        val tone = value("tone", if (light) 1f else 0f)

        picture.renderEffect = if (blur > 0f) BlurEffect(blur, blur, TileMode.Clamp) else null
        picture.colorFilter = if (saturation != 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) else null
        picture.record { drawLayer(wall.layer) }
        screens.layer.record {
            drawLayer(picture)
            if (tint > 0f) drawRect(lerp(Color.Black, Color.White, tone), alpha = tint)
        }
        // Not a read of this draw: it would invalidate itself and draw again forever.
        Snapshot.withoutReadObservation { screens.version++ }
    }
}

/** What the screens over the tabs lie on: the tuning panel's choice in the review app, else [WallMode.DEFAULT]. */
internal fun screenWallMode(tuning: GlassTuning?): Int = if (tuning?.enabled == true) tuning.wallMode else WallMode.DEFAULT

/** iOS's `systemGroupedBackground` in the light (its Settings); black in the dark. */
private val IOS_GROUPED_LIGHT = Color(0xFFF2F2F7)

/** Whether the focus is inside the layer; read by its focus request, not drawn — no state. */
private class RoomFocus {
    var focused = false
}

private const val ENTER_MS = 220
private const val EXIT_MS = 180
private const val CANCEL_MS = 200

/** How much the screen shrinks by the gesture's end: 0.08 before, a quarter more on the owner's word (24.11). */
private const val SHRINK = 0.10f
private const val SCRIM = 0.2f
private val ELEVATION = 16.dp
private val CORNER = 32.dp
/** The card's least corners from the gesture's start: where Android does not tell the display's (before 12), or it is square. */
private val FALLBACK_CORNER = 16.dp

/** A section page sliding in over Settings and out again: Material's emphasized curves, as long both ways (the owner). */
private const val SLIDE_IN_MS = 240
private const val SLIDE_OUT_MS = 240
private val SlideInEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val SlideOutEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
/** How dark the screen under a sliding page gets once covered; below the gesture's [SCRIM]. */
private const val SLIDE_SCRIM = 0.12f

/**
 * How a card or the tabs follow the finger of a back gesture: by a spring, as the system already eases the gesture's
 * progress, so the move and the shrink go together. One to one, the card leapt: by a curved edge (the OnePlus 12) the
 * gesture starts at x 6 px and the finger is reported at 90 px 30 ms later, while the progress grew smoothly from 0 —
 * a jump and a gap beside the card on a slow pull (F-457).
 */
internal val BackFollow: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium)

/** Отпущенная карточка уезжает на ширину экрана с запасом — чтобы и тень ушла за край. */
private const val LEAVE_DISTANCE = 1.15f
private const val LEAVE_MS = 240

/**
 * Система отдаёт ход почти линейно от пройденного пальцем: 320 пикселей из 1080 —
 * это 0,28. Без кривой обычный жест уменьшал экран на 3%, и отклика не было видно.
 * С быстрым стартом экран отзывается сразу.
 */
private fun eased(progress: Float): Float = 1f - (1f - progress) * (1f - progress)
