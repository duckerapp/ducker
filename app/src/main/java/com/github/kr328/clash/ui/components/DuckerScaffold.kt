package com.github.kr328.clash.ui.components

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.github.kr328.clash.R
import kotlinx.coroutines.delay

/**
 * Панель вкладок висит в воздухе поверх содержимого, а не занимает полосу внизу (D-74).
 *
 * Положение страницы приходит двумя лямбдами, а не числом, и читается отложенно (D-76):
 * [position] — в фазе отрисовки, [selected] — внутри самой панели. Прочитать их в теле
 * экрана значило бы пересобирать всё дерево на каждый кадр жеста.
 *
 * Вкладкам приходит отступ снизу под панель: те, что рисуют список, кладут его
 * в `contentPadding` и продолжают прокручиваться под ней; остальные просто отступают.
 * Экранам поверх вкладок — только системные отступы: пока они открыты, панель уезжает
 * вниз за край (D-96), и место под неё не нужно.
 *
 * Клавиатура панель не поднимает: та остаётся внизу, под клавиатурой (D-146). Над
 * клавиатурой она закрывала половину найденного поиском на «Прокси».
 */
@Composable
fun DuckerScaffold(
    selected: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    /**
     * Показана ли панель. Пока открыт экран поверх вкладок, она уезжает вниз: в настройках
     * вкладки ни к чему, а назад ведут стрелка в шапке и жест (D-96).
     */
    dockShown: Boolean,
    /**
     * Круглая кнопка над панелью, по центру (D-97). Насколько она раскрыта — [actionShown]
     * от 0 до 1: читается в отрисовке, так что кнопка растёт и сжимается ровно за пальцем.
     */
    actionShown: () -> Float = { 0f },
    /** Как кнопка выглядит. Касаний сама не ловит: их ловит поле шире неё, [onAction]. */
    action: @Composable () -> Unit = {},
    /** Нажатие на кнопку; `null` — сейчас не нажимается и касания проходят к списку. */
    onAction: (() -> Unit)? = null,
    /** Подпись кнопки для TalkBack. */
    actionLabel: String? = null,
    /**
     * Надпись единственной кнопки вместо вкладок, пока вкладкам нечего показывать (D-98):
     * подписок нет — панель становится одной кнопкой «Добавить подписку».
     */
    single: String? = null,
    onSingle: () -> Unit = {},
    /**
     * Телевизор: док слева и фокус с пульта (этап 6), см. [TvScaffold]. [tvTitle] и [tvTools] —
     * марка над доком и кнопки под ним: на телефоне они в шапке главной.
     */
    tv: TvFocus? = null,
    tvTitle: @Composable RowScope.() -> Unit = {},
    tvTools: @Composable RowScope.() -> Unit = {},
    content: @Composable (tabs: PaddingValues, overlays: PaddingValues) -> Unit,
) {
    if (tv != null) {
        CompositionLocalProvider(LocalRippleConfiguration provides TvRipple) {
            TvScaffold(
                tv, selected, position, onSelect, dockShown, actionShown, action, onAction, actionLabel, single, onSingle,
                tvTitle, tvTools, content,
            )
        }

        return
    }

    var dockHeight by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    // Уезжает с разгоном, возвращается с торможением: так уходят и приходят на экран
    // по Material. Читается только в слое панели — экран под ней не пересобирается.
    val hidden by animateFloatAsState(
        targetValue = if (dockShown) 0f else 1f,
        animationSpec = if (dockShown) tween(260, easing = LinearOutSlowInEasing)
        else tween(200, easing = FastOutLinearInEasing),
        label = "dock",
    )
    // Системные полосы без клавиатуры: по ним стоит панель (D-146).
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val below = bars.getBottom(density)

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Box(Modifier.fillMaxSize()) {
            // Под панелью резервируем её измеренную высоту: при крупном шрифте она
            // выше, и последний пункт списка не должен уезжать под неё.
            val reserved = with(density) { dockHeight.toDp() }

            content(
                PaddingValues(
                    start = insets.calculateStartPadding(direction),
                    end = insets.calculateEndPadding(direction),
                    top = insets.calculateTopPadding(),
                    // Открыта клавиатура — панель под ней, и отступ нужен только под клавиатуру.
                    bottom = maxOf(
                        insets.calculateBottomPadding(),
                        bars.asPaddingValues().calculateBottomPadding() + reserved,
                    ),
                ),
                insets,
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        bars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer {
                        // Ниже края целиком, вместе с отступом и тенью.
                        translationY = hidden * (size.height + below + 40.dp.toPx())
                    }
                    .then(if (dockShown) Modifier else Modifier.clearAndSetSemantics { }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Место под кнопку есть всегда — иначе панель прыгала бы, когда та
                // появляется. Пустое место касаний не ловит: они проходят к списку.
                Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.matchParentSize()
                            .clearAndSetSemantics { }
                            .graphicsLayer {
                                val shown = actionShown()
                                val scale = popOut(shown)

                                scaleX = scale
                                scaleY = scale
                                // Раскрывается с доворотом: плюс приходит из четверти оборота.
                                rotationZ = (1f - shown) * -90f
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        action()
                    }

                    // Касания ловит не сам кружок, а поле шире него и вне слоя с масштабом
                    // (D-115). У кнопки Material поле равно кружку, и палец, попавший на пару
                    // миллиметров мимо края, не срабатывал. А попадание через слой с масштабом
                    // Compose считает с ошибками (F-124). Волна — по центру, ровно в кружок.
                    if (onAction != null) {
                        Box(
                            Modifier.requiredSize(ACTION_TOUCH)
                                .semantics { if (actionLabel != null) contentDescription = actionLabel }
                                .clickable(
                                    interactionSource = null,
                                    indication = ripple(
                                        bounded = false,
                                        radius = ACTION_SIZE / 2,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    role = Role.Button,
                                    onClick = onAction,
                                ),
                        )
                    }
                }

                FloatingDock(
                    selected = selected,
                    position = position,
                    onSelect = onSelect,
                    single = single,
                    onSingle = onSingle,
                    modifier = Modifier.onSizeChanged { dockHeight = it.height },
                )
            }
        }
    }
}

/** Кнопка над панелью (D-97). */
val ACTION_SIZE = 56.dp

/** Поле нажатия кнопки над панелью — шире самого кружка (D-115). */
private val ACTION_TOUCH = 72.dp

/**
 * Рост с лёгким перелётом в конце: кнопка не просто вырастает, а раскрывается, чуть
 * пружиня. В 0 и 1 — ровно 0 и 1, поэтому в покое размер точный.
 */
private fun popOut(t: Float): Float {
    val c = 1.2f
    val u = t - 1f

    return (1f + (c + 1f) * u * u * u + c * u * u).coerceAtLeast(0f)
}

/**
 * Ряд вкладок рисуется дважды: снизу в тусклом цвете, сверху в цвете «на подсветке»,
 * и верхняя копия обрезается ровно по подсветке (D-76).
 *
 * Простое переключение цвета по выбранной вкладке выглядело бы так: подсветка уже
 * наехала на соседнюю вкладку, а её подпись ещё серая — серое на чёрном, полжеста
 * не прочитать. Здесь цвет меняется там же, где проходит край подсветки, и обе копии
 * статичны: за кадр меняются только прямоугольник заливки и прямоугольник обрезки,
 * то есть одна фаза отрисовки.
 */
@Composable
private fun FloatingDock(
    selected: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    single: String?,
    onSingle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val pill = MaterialTheme.colorScheme.primaryContainer
    val onPill = MaterialTheme.colorScheme.onPrimaryContainer

    val alone = single != null

    // 0 — три вкладки, 1 — одна кнопка во всю панель (D-98). Подсветка вкладки
    // расползается на всю ширину, вкладки гаснут, проявляется надпись; обратно — в обратном
    // порядке, и из кнопки снова вырастают вкладки. Читается только в отрисовке.
    val merge by animateFloatAsState(
        targetValue = if (alone) 1f else 0f,
        animationSpec = tween(MERGE_MS, easing = FastOutSlowInEasing),
        label = "merge",
    )

    // Надпись держим и после того, как кнопка не нужна: пока панель разворачивается
    // обратно во вкладки, она гаснет, а не пропадает скачком.
    val lastLabel = remember { mutableStateOf("") }
    SideEffect { if (single != null) lastLabel.value = single }

    // Блик по кнопке — раз, когда она появилась, и потом раз в несколько секунд. Не
    // бесконечная пульсация: та гоняла бы экран без остановки, как полоска загрузки (D-91).
    val shine = remember { Animatable(0f) }

    LaunchedEffect(alone) {
        shine.snapTo(0f)

        if (!alone) return@LaunchedEffect

        delay(MERGE_MS.toLong())

        while (true) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(SHINE_MS, easing = FastOutSlowInEasing))
            delay(SHINE_PAUSE_MS)
        }
    }

    val current = selected()

    fun DrawScope.pillLeft() = lerp(
        dockPillOffset(width = size.width, position = position(), rtl = layoutDirection == LayoutDirection.Rtl),
        0f,
        merge,
    )

    fun DrawScope.pillWidth() = lerp(dockSlotWidth(size.width), size.width, merge)

    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 10.dp,
    ) {
        Box(Modifier.fillMaxWidth().padding(5.dp)) {
            // Подсветка — своим слоем под обеими копиями вкладок: при слиянии она растёт,
            // а вкладки гаснут, и гасить её вместе с ними нельзя.
            Box(
                Modifier.matchParentSize().drawBehind {
                    drawRoundRect(
                        color = pill,
                        topLeft = Offset(pillLeft(), 0f),
                        size = Size(pillWidth(), size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            )

            DockTabs(
                selected = current,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onSelect = if (alone) null else onSelect,
                modifier = Modifier
                    .then(if (alone) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer { alpha = 1f - (merge * 2f).coerceIn(0f, 1f) },
            )

            DockTabs(
                selected = current,
                content = onPill,
                onSelect = null,
                // Нажатия и озвучка живут в нижней копии: верхняя только перекрашивает,
                // иначе TalkBack читал бы каждую вкладку дважды.
                modifier = Modifier.clearAndSetSemantics { }
                    .graphicsLayer { alpha = 1f - (merge * 2f).coerceIn(0f, 1f) }
                    .drawWithContent {
                        val left = pillLeft()

                        clipRect(left, 0f, left + pillWidth(), size.height) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )

            if (lastLabel.value.isNotEmpty()) {
                Row(
                    modifier = Modifier.matchParentSize()
                        .graphicsLayer { alpha = ((merge - 0.5f) * 2f).coerceIn(0f, 1f) }
                        .clip(shape)
                        .then(
                            if (alone) Modifier.clickable(role = Role.Button, onClick = onSingle)
                            else Modifier.clearAndSetSemantics { },
                        )
                        .drawWithContent {
                            drawContent()

                            val t = shine.value

                            if (t > 0f && t < 1f) {
                                val band = size.width * 0.4f
                                val x = -band + (size.width + band * 2f) * t

                                drawRect(
                                    Brush.linearGradient(
                                        colors = listOf(Color.Transparent, onPill.copy(alpha = 0.22f), Color.Transparent),
                                        start = Offset(x - band / 2f, 0f),
                                        end = Offset(x + band / 2f, size.height),
                                    ),
                                )
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = onPill, modifier = Modifier.size(22.dp))

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = single ?: lastLabel.value,
                        color = onPill,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Слияние вкладок в одну кнопку и обратно (D-98). */
private const val MERGE_MS = 420

/** Проход блика по кнопке и пауза между проходами. */
private const val SHINE_MS = 900
private const val SHINE_PAUSE_MS = 4000L

/** Вкладки дока — на телефоне и на телевизоре. */
internal val DockTabItems = listOf(
    R.string.home_tab to DuckerIcons.Home,
    R.string.proxies_tab to DuckerIcons.Proxies,
    R.string.subscriptions_tab to DuckerIcons.Subscriptions,
)

/**
 * Один ряд вкладок. Обе копии обязаны совпадать по метрике до пикселя, иначе обрезанная
 * поедет относительно нижней, — поэтому жирность подписи одна на все вкладки, а выбор
 * несут подсветка и цвет.
 */
@Composable
private fun DockTabs(
    selected: Int,
    content: Color,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (onSelect != null) Modifier.selectableGroup().focusGroup() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockTabItems.forEachIndexed { index, (label, icon) ->
            val active = selected == index

            Column(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .then(
                        if (onSelect != null) {
                            Modifier.selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = content,
                )

                FitLabel(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = content,
                )
            }
        }
    }
}
