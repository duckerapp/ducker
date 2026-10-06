package com.github.kr328.clash.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Смена темы накатом волны от кнопки (D-66).
 *
 * Перекрасить палитру можно и мгновенно, но экран тогда моргает целиком, и непонятно,
 * что именно сработало. Здесь последний кадр старой темы замирает поверх новой и
 * стирается растущим кругом из точки нажатия: волна идёт от угла, где стоит кнопка.
 */
fun interface ThemeSwitch {
    /** @param from центр волны в координатах корня окна. */
    fun toggle(from: Offset)
}

val LocalThemeSwitch = staticCompositionLocalOf<ThemeSwitch?> { null }

@Composable
fun ThemeReveal(onToggle: () -> Unit, content: @Composable () -> Unit) {
    // Экран рисуется через слой, чтобы с него можно было снять последний кадр. Сам слой
    // поверх положить нельзя: вложенные в него RenderNode'ы карточек перерисовались бы
    // новой темой, и «снимок» вышел бы наполовину перекрашенным. Снимок в битмап
    // замораживает всё, и стоит он на этом экране 5–9 мс (замер на Redmi).
    //
    // Через слой — только на кадр снимка (D-91). Пока экран всегда шёл через него,
    // любая мелочь перезаписывала этот слой целиком, система теряла, какой кусок
    // экрана поменялся, и перерисовывала всё: тик часов стоил видеочипу 9–12 мс.
    val layer = rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val radius = remember { Animatable(0f) }
    val toggle by rememberUpdatedState(onToggle)

    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Size.Zero) }

    val switch = remember {
        ThemeSwitch { from ->
            // Второе нажатие посреди наката оставило бы поверх экрана кадр, которому
            // уже нечего накрывать.
            if (frozen != null) return@ThemeSwitch

            scope.launch {
                origin = from

                radius.snapTo(0f)

                // Слой пишется в ближайшей отрисовке. Первый кадр её запускает, ко второму
                // она уже прошла.
                capturing = true
                withFrameNanos {}
                withFrameNanos {}

                frozen = layer.toImageBitmap()

                capturing = false

                toggle()

                radius.animateTo(bounds.farthestFrom(from), tween(520, easing = FastOutSlowInEasing))

                frozen = null
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { bounds = Size(it.width.toFloat(), it.height.toFloat()) }) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                if (capturing) {
                    layer.record { this@drawWithContent.drawContent() }

                    drawLayer(layer)
                } else {
                    drawContent()
                }
            },
        ) {
            CompositionLocalProvider(LocalThemeSwitch provides switch, content = content)
        }

        // Старый кадр поверх нового с вырезанным кругом: видно не перекраску, а накат.
        // Круг вырезаем `BlendMode.Clear` в отдельном слое — клип по пути с чётно-нечётной
        // заливкой аппаратный холст молча игнорирует и дыры не делает.
        frozen?.let { frame ->
            Canvas(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawImage(frame)

                drawCircle(Color.Black, radius.value, origin, blendMode = BlendMode.Clear)
            }
        }
    }
}

/** Радиус, при котором круг из точки накрывает весь экран, — до самого дальнего угла. */
private fun Size.farthestFrom(point: Offset): Float = maxOf(
    hypot(point.x, point.y),
    hypot(width - point.x, point.y),
    hypot(point.x, height - point.y),
    hypot(width - point.x, height - point.y),
)
