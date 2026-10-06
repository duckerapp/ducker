package com.github.kr328.clash.ui.subscriptions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Перетаскивание подписок долгим нажатием (этап 13, пакет 5). Своё, без библиотеки: подписок
 * единицы, а нужно немногое — поднять, вести за пальцем, поменять местами с соседом, чей центр
 * пересекли, у края списка — листать.
 *
 * Где карточка на экране — это место, где её подняли, плюс путь пальца. Поэтому ни обмен местами,
 * ни прокрутка не уводят её из-под пальца: меняется только то, насколько она смещена от своего
 * места в списке. Ключи — ключи элементов списка, у подписок это номер строкой.
 */
@Stable
internal class ProfileReorder(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val isProfile: (Any) -> Boolean,
    private val onMove: (from: String, to: String) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Поднятая карточка; `null` — ничего не тащат. */
    var dragged by mutableStateOf<Any?>(null)
        private set

    /** Отпущенная карточка, которая доезжает на своё место. */
    var settling by mutableStateOf<Any?>(null)
        private set

    /** Идёт перестановка — соседи переезжают плавно, а не скачком. */
    val active: Boolean
        get() = dragged != null || settling != null

    private var start = 0
    private var moved by mutableFloatStateOf(0f)
    private val settle = Animatable(0f)

    private fun item(key: Any) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Насколько карточку сдвинуть от её места в списке. */
    fun offset(key: Any): Float = when (key) {
        dragged -> start + moved - (item(key)?.offset ?: start)
        settling -> settle.value
        else -> 0f
    }

    fun begin(key: Any) {
        start = item(key)?.offset ?: return
        moved = 0f
        dragged = key

        scope.launch { autoscroll(key) }
    }

    /** Палец сдвинулся; `true` — карточка встала на новое место. */
    fun drag(dy: Float): Boolean {
        moved += dy

        return swap()
    }

    fun end() {
        val key = dragged ?: return
        val rest = offset(key)

        dragged = null
        settling = key

        scope.launch {
            settle.snapTo(rest)
            settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))

            if (settling == key) settling = null
        }

        onDrop()
    }

    private fun swap(): Boolean {
        val key = dragged ?: return false
        val info = item(key) ?: return false
        val middle = start + moved + info.size / 2
        val items = list.layoutInfo.visibleItemsInfo
        val next = items.firstOrNull { it.index == info.index + 1 }?.takeIf { isProfile(it.key) }
        val prev = items.firstOrNull { it.index == info.index - 1 }?.takeIf { isProfile(it.key) }

        val target = when {
            next != null && middle > next.offset + next.size / 2 -> next
            prev != null && middle < prev.offset + prev.size / 2 -> prev
            else -> return false
        }

        onMove(key as String, target.key as String)

        return true
    }

    /** Карточку подвели к краю списка — список едет, тем быстрее, чем дальше за край. */
    private suspend fun autoscroll(key: Any) {
        while (dragged == key) {
            withFrameNanos {}

            val info = item(key) ?: continue
            val layout = list.layoutInfo
            val top = start + moved
            val first = layout.viewportStartOffset + layout.beforeContentPadding
            val last = layout.viewportEndOffset - layout.afterContentPadding

            val over = when {
                top < first -> top - first
                top + info.size > last -> top + info.size - last
                else -> 0f
            }

            if (over != 0f && list.scrollBy((over * EDGE_SPEED).coerceIn(-MAX_STEP, MAX_STEP)) != 0f) swap()
        }
    }

    private companion object {
        /** Какая доля захода за край проезжает за кадр. */
        const val EDGE_SPEED = 0.15f

        /** Больше этого за кадр, пикселей, список не едет. */
        const val MAX_STEP = 24f
    }
}

/**
 * Долгое нажатие поднимает карточку, дальше она едет за пальцем. Стоит внутри касания карточки и
 * съедает весь жест после подъёма, включая отпускание: иначе подписка, которую просто подержали,
 * стала бы активной. [scale] — во сколько карточка сейчас увеличена: путь пальца внутри неё
 * меньше настоящего во столько же раз.
 */
internal fun Modifier.reorderable(
    reorder: ProfileReorder,
    key: Any,
    scale: () -> Float,
    onLift: () -> Unit,
    onStep: () -> Unit,
) = pointerInput(reorder, key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

        onLift()
        reorder.begin(key)

        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == press.id } ?: break
                val dy = change.positionChange().y

                change.consume()

                if (!change.pressed) break
                if (reorder.drag(dy * scale())) onStep()
            }
        } finally {
            reorder.end()
        }
    }
}
