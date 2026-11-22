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
import androidx.compose.animation.AnimatedVisibility
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
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
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
        enter = fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), initialScale = 0.96f),
        // Ушёл жестом — карточка уже за краем, гасить нечего. Обычное закрытие ещё и
        // уменьшает от центра и подтягивало бы уехавшую карточку обратно к экрану.
        exit = if (leaving) ExitTransition.None
        else fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.94f),
    ) {
        // Под уменьшенным экраном — главная того же цвета, и без затемнения и тени край
        // экрана не читался: казалось, что настройки просто съехали вбок.
        Box(
            Modifier.fillMaxSize().drawBehind {
                // Затемнение тает по мере того, как карточка уходит с экрана.
                val gone = if (width > 0f) (abs(offset.value) / width).coerceIn(0f, 1f) else 0f

                drawRect(Color.Black, alpha = SCRIM * eased(progress.value) * (1f - gone))
            },
        )

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // Читается здесь, а не в композиции: каждый кадр жеста трогает
                    // только этот слой.
                    val p = eased(progress.value)
                    val scale = 1f - SHRINK * p

                    scaleX = scale
                    scaleY = scale
                    translationX = offset.value
                    shape = RoundedCornerShape(CORNER * p)
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
                    if (frameContent) ScreenFrame(wide, content = content) else content()
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
            place.matrix.reset()
            here.transformFrom(here.findRootCoordinates(), place.matrix)
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
 * Home's picture as the screens over the tabs show it, blurred, tinted and saturated by the tuning panel's
 * [GlassTuningGroup.Wall] (the review app; elsewhere [wall] itself). Recorded by [screenWallSource], so their glass
 * shows it too.
 */
@Composable
internal fun rememberScreenWall(wall: GlassBackdrop?): GlassBackdrop? {
    val tuning = LocalGlassTuning.current
    if (wall == null || tuning == null || !tuning.enabled) return wall
    val screens = rememberGlassBackdrop(still = true) ?: return wall

    // It draws [wall]'s layer, so shows its changes at once; its still pictures are made anew with them.
    LaunchedEffect(wall, screens) {
        snapshotFlow { wall.origin to wall.version }.collect { (origin, _) ->
            screens.origin = origin
            screens.version++
        }
    }

    return screens
}

/** Records [screens] from [wall] (see [rememberScreenWall]) and draws nothing itself. */
@Composable
internal fun Modifier.screenWallSource(screens: GlassBackdrop?, wall: GlassBackdrop?): Modifier {
    val tuning = LocalGlassTuning.current
    if (screens == null || wall == null || screens === wall || tuning == null) return this
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val picture = rememberGraphicsLayer()

    return drawBehind {
        val group = GlassTuningGroup.Wall
        val blur = tuning.value(group, "blur", 0f, light).dp.toPx()
        val saturation = tuning.value(group, "saturation", 1f, light)
        val tint = tuning.value(group, "tint", 0f, light)
        val tone = tuning.value(group, "tone", if (light) 1f else 0f, light)

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

/** Whether the focus is inside the layer; read by its focus request, not drawn — no state. */
private class RoomFocus {
    var focused = false
}

private const val ENTER_MS = 220
private const val EXIT_MS = 180
private const val CANCEL_MS = 200

/** Насколько экран уменьшается к концу жеста: ровно чтобы читался как карточка. */
private const val SHRINK = 0.08f
private const val SCRIM = 0.2f
private val ELEVATION = 16.dp
private val CORNER = 32.dp

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
