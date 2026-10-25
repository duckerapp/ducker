package com.github.kr328.clash.ui.theme

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    // Снимок в битмап замораживает всё. The frame is read back from the window as it is on screen
    // ([snapshot]): the old way, drawing the screen through a layer for one frame and rendering that
    // layer to a bitmap, sometimes returned it drawn only partly — on the emulator 5 switches of 16
    // showed the new theme under a straight line at two thirds of the screen. Nothing is drawn
    // through a layer now, so D-91's whole-screen redraws cannot come back either.
    val window = LocalActivity.current?.window
    val scope = rememberCoroutineScope()
    val radius = remember { Animatable(0f) }
    val toggle by rememberUpdatedState(onToggle)

    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Where the reveal is in the window, pixels: the snapshot is taken from there.
    var area by remember { mutableStateOf(Rect.Zero) }

    val switch = remember {
        // Not a state: a second press in the same frame must already see it.
        var running = false

        ThemeSwitch { from ->
            // Второе нажатие посреди наката оставило бы поверх экрана кадр, которому
            // уже нечего накрывать. Dropped from the press on: let through while the snapshot was
            // being taken, it ran a second switch beside the first, and the theme came back.
            if (running) return@ThemeSwitch

            running = true

            scope.launch {
                try {
                    origin = from

                    radius.snapTo(0f)

                    frozen = window?.let { snapshot(it, area) }

                    toggle()

                    // No picture of the old theme: it just switches, without a wave.
                    if (frozen == null) return@launch

                    // The new palette recomposes the whole screen — the switch's heaviest frame (18 ms of
                    // the UI thread on the emulator, more on a TV). It passes under the whole old frame:
                    // inside the wave it made the circle jump at its start.
                    withFrameNanos {}
                    withFrameNanos {}

                    radius.animateTo(area.size.farthestFrom(from), tween(520, easing = FastOutSlowInEasing))
                } finally {
                    frozen = null
                    running = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { area = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalThemeSwitch provides switch, content = content)

        // Старый кадр поверх нового с вырезанным кругом: видно не перекраску, а накат.
        // Круг вырезаем `BlendMode.Clear` в отдельном слое — клип по пути с чётно-нечётной
        // заливкой аппаратный холст молча игнорирует и дыры не делает. A clip outside the circle it
        // does make, but on the emulator that cost 3–5 ms a frame more than this layer and missed frames.
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

/**
 * What the window shows inside [area] (window pixels) now, read back by the system; `null` if it could not.
 * The copy is of a frame already on screen, whole, and is made off the main thread.
 */
private suspend fun snapshot(window: Window, area: Rect): ImageBitmap? {
    val bounds = android.graphics.Rect(
        area.left.roundToInt(), area.top.roundToInt(), area.right.roundToInt(), area.bottom.roundToInt(),
    )

    if (bounds.isEmpty) return null

    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)

    return suspendCancellableCoroutine { done ->
        PixelCopy.request(
            window, bounds, bitmap,
            { result -> done.resume(if (result == PixelCopy.SUCCESS) bitmap.asImageBitmap() else null) },
            Handler(Looper.getMainLooper()),
        )
    }
}

/** Радиус, при котором круг из точки накрывает весь экран, — до самого дальнего угла. */
private fun Size.farthestFrom(point: Offset): Float = maxOf(
    hypot(point.x, point.y),
    hypot(width - point.x, point.y),
    hypot(point.x, height - point.y),
    hypot(width - point.x, height - point.y),
)
