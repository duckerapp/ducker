package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Край списка — пружиной, а не системным растяжением (D-137).
 *
 * Растяжение Android тянет и искажает весь экран, а на коротком списке быстрый бросок за два
 * кадра упирается в край и целиком уходит в растяжение; на кадре удара картинка ещё и замирает.
 * Здесь содержимое просто сдвигается слоем: палец тянет его за край всё туже, а бросок, который
 * упёрся в край, уводит его по инерции, и пружина без колебаний возвращает на место.
 */
class BounceOverscroll(private val scope: CoroutineScope) : OverscrollEffect {
    /** Сдвиг содержимого за край, пк: плюс — к концу оси (вниз), минус — к началу. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var vertical = true

    /** Длина списка по оси: сдвиг пальцем стремится к её доле [MAX_PULL], но не достигает. */
    private var length = 1f

    private var settling: Job? = null

    /** Прошлый шаг броска: по нему считаем скорость в момент удара о край. */
    private var flingStepAt = 0L

    /** Пружина уже запущена ударом о край — конец броска её не перезапускает. */
    private var bounced = false

    override val isInProgress: Boolean get() = offset != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // Шаг броска, а не пальца. Упёрся в край — пружина стартует в этом же кадре: конец броска
        // доходит до applyToFling кадром позже, и тот кадр стоял бы неподвижно.
        if (source != NestedScrollSource.UserInput) {
            val now = System.nanoTime()
            val scrolled = performScroll(delta)
            val rest = (delta - scrolled).along()

            if (abs(rest) > 0.5f && !bounced && flingStepAt > 0L) {
                val velocity = delta.along() / ((now - flingStepAt) / 1e9f)

                if (abs(velocity) > MIN_VELOCITY) {
                    // Остаток шага — сразу сдвигом: кадр удара проходит полный шаг, а не обрубок.
                    bounced = true
                    offset += rest
                    settle(velocity.coerceIn(-MAX_VELOCITY, MAX_VELOCITY), since = now)
                }
            }

            flingStepAt = now

            return scrolled
        }

        if (delta.x != 0f) vertical = false else if (delta.y != 0f) vertical = true

        val d = delta.along()

        // Палец поймал содержимое на возврате — дальше ведёт он, а не пружина.
        settling?.cancel()

        // Палец пошёл обратно, пока содержимое за краем, — сначала возвращаем его на место.
        var back = 0f

        if (offset != 0f && d.sign != offset.sign) {
            val next = if (offset > 0) (offset + d).coerceAtLeast(0f) else (offset + d).coerceAtMost(0f)

            back = next - offset
            offset = next
        }

        val scrolled = performScroll(axis(d - back)).along()
        val rest = d - back - scrolled

        // Остаток тянет за край, и чем дальше, тем туже.
        if (rest != 0f) {
            val slack = (1f - abs(offset) / (length * MAX_PULL)).coerceIn(0f, 1f)

            offset += rest * slack * slack * PULL
        }

        return axis(back + scrolled)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (velocity.x != 0f) vertical = false else if (velocity.y != 0f) vertical = true

        val v = velocity.along()

        flingStepAt = 0L
        bounced = false

        // Отпустили за краем: содержимое плавно возвращается, бросок внутрь отдаём списку, пока
        // пружина его возвращает. The release's speed outward is dropped: thrown on, it carried
        // the content further out before the return, a jerk after a hard pull (D-315).
        if (offset != 0f && (v == 0f || v.sign == offset.sign)) {
            settle(0f)
            // Still a release for the nested-scroll parents (the pull of PullToRefresh, a sheet's
            // drag): skipped, they never heard the finger go up.
            performFling(Velocity.Zero)

            return
        }

        if (offset != 0f) settle(0f)

        val left = v - performFling(velocity).along()

        // Бросок упёрся в край на первом же шаге — скорости удара нет, берём остаток броска.
        if (!bounced && abs(left) > MIN_VELOCITY) settle(left.coerceIn(-MAX_VELOCITY, MAX_VELOCITY))
    }

    /**
     * Пружина к нулю, в две фазы. Наружу — жёсткая: скорость броска гаснет за два-три кадра, но с
     * той же скорости, без ступеньки. Назад — мягкая, как отпущенная резинка.
     *
     * Время идёт от [since] — от удара о край — или с кадра до первого своего: иначе первый кадр
     * пружины повторяет стартовое положение, и картинка на кадр замирает.
     */
    private fun settle(velocity: Float, since: Long = 0L) {
        settling?.cancel()
        settling = scope.launch {
            var start = since

            if (velocity != 0f && (offset == 0f || velocity.sign == offset.sign)) {
                val out = springToZero(velocity, Spring.StiffnessMedium)

                // До вершины: там скорость меняет знак.
                play(out, start) { played -> out.getVelocityVectorFromNanos(played).value.sign != velocity.sign }
                start = 0L
            }

            play(springToZero(0f, Spring.StiffnessMediumLow), start) { false }

            offset = 0f
        }
    }

    private fun springToZero(velocity: Float, stiffness: Float) = TargetBasedAnimation(
        animationSpec = spring(stiffness = stiffness),
        typeConverter = Float.VectorConverter,
        initialValue = offset,
        targetValue = 0f,
        initialVelocityVector = AnimationVector1D(velocity),
    )

    private suspend fun play(
        spring: TargetBasedAnimation<Float, AnimationVector1D>,
        since: Long,
        stop: (played: Long) -> Boolean,
    ) {
        var start = since

        do {
            val done = withFrameNanos { now ->
                if (start <= 0L || start > now) start = now - FRAME_NANOS

                val played = now - start

                offset = spring.getValueFromNanos(played)

                spring.isFinishedFromNanos(played) || stop(played)
            }
        } while (!done)
    }

    /** Сдвиг — слоем: раскладка и перерисовка содержимого на каждом кадре не нужны. */
    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)

            length = (if (vertical) placeable.height else placeable.width).toFloat().coerceAtLeast(1f)

            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    if (vertical) translationY = offset else translationX = offset
                }
            }
        }
    }

    private fun Offset.along() = if (vertical) y else x
    private fun Velocity.along() = if (vertical) y else x
    private fun axis(value: Float) = if (vertical) Offset(0f, value) else Offset(value, 0f)

    private companion object {
        const val MAX_PULL = 0.1f

        /**
         * Какую долю хода пальца содержимое проходит у самого края. Вместе с [MAX_PULL]: палец
         * через весь экран уводит содержимое на ~160 пк (было вдвое больше — много пустоты).
         */
        const val PULL = 0.25f
        const val MIN_VELOCITY = 50f

        /** Скорость больше этой уводила бы содержимое за край дальше 200 пк. */
        const val MAX_VELOCITY = 20000f

        /** Один кадр на 120 Гц. */
        const val FRAME_NANOS = 8_333_333L
    }
}
