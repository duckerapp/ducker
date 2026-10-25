package com.github.kr328.clash.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
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
 */
@Composable
fun BackLayer(
    visible: Boolean,
    onBack: () -> Unit,
    enabled: Boolean = true,
    wide: Boolean = false,
    frameContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val width = LocalWindowInfo.current.containerSize.width.toFloat()

    // Ход жеста: 0 — экран на месте, 1 — палец дотянут до конца. От него — уменьшение,
    // углы и тень: по ним видно, что экран стал карточкой.
    val progress = remember { Animatable(0f) }
    // Сдвиг карточки, пиксели: едет за пальцем один в один, как лента на вкладках.
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
                offset.snapTo((it.touchX - start).coerceIn(-width, width))
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
    val input = LocalInputModeManager.current
    val tv = LocalTv.current

    LaunchedEffect(visible && enabled) {
        if (tv && visible && enabled && input.inputMode == InputMode.Keyboard) focus.requestFocus()
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
                // Пустое место экрана — тоже экран: касание не должно проваливаться
                // к вкладкам под ним и листать их.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }
                .then(if (enabled) Modifier else Modifier.clearAndSetSemantics {})
                .then(if (tv) Modifier.focusRoom(focus) else Modifier),
        ) {
            if (frameContent) ScreenFrame(wide, content = content) else content()
        }
    }
}

private const val ENTER_MS = 220
private const val EXIT_MS = 180
private const val CANCEL_MS = 200

/** Насколько экран уменьшается к концу жеста: ровно чтобы читался как карточка. */
private const val SHRINK = 0.08f
private const val SCRIM = 0.2f
private val ELEVATION = 16.dp
private val CORNER = 32.dp

/** Отпущенная карточка уезжает на ширину экрана с запасом — чтобы и тень ушла за край. */
private const val LEAVE_DISTANCE = 1.15f
private const val LEAVE_MS = 240

/**
 * Система отдаёт ход почти линейно от пройденного пальцем: 320 пикселей из 1080 —
 * это 0,28. Без кривой обычный жест уменьшал экран на 3%, и отклика не было видно.
 * С быстрым стартом экран отзывается сразу.
 */
private fun eased(progress: Float): Float = 1f - (1f - progress) * (1f - progress)
