package com.github.kr328.clash.ui.components

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.clipPath

import androidx.compose.ui.geometry.RoundRect

import androidx.compose.ui.graphics.Path


import android.os.Build

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.tappableElement
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.github.kr328.clash.R
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
     * The TV's round button under its dock (D-97). How far it is open, 0 to 1, is read in drawing, so the
     * button grows and shrinks right behind the page. A phone has no button (D-390).
     */
    actionShown: () -> Float = { 0f },
    /** How the TV's button looks. It takes no touches itself: [onAction] does. */
    action: @Composable () -> Unit = {},
    /**
     * The action of «Подписки»; `null` while it is not available. On a TV it is the round button's tap; on a
     * phone the selected third tab turns into «+ Добавить» while it is set, and a tap on that tab calls it (D-390).
     */
    onAction: (() -> Unit)? = null,
    /** What [onAction] does, for TalkBack. */
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
    /** Home's picture, whose connect wave bends the whole screen, see [screenWaveEffect]. */
    wave: GlassBackdrop? = null,
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

    // Across the dock: its height at the bottom, its width at the side.
    var dockDepth by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // A low window — a phone on its side: the dock stands at the start edge, as on a TV, and the screens keep the
    // whole height. At the bottom it took 93 of 360 dp of a phone on its side.
    val rail = lowWindow()

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

    // The glass style: the dock shows the screen under it through glass. Null — Air.
    val backdrop = rememberGlassBackdrop()
    val sheetGlass = LocalSheetGlass.current
    // Under an open glass sheet the screen stands still: drawn once for the sheet's panes, see SheetStillPictures.
    if (backdrop != null && sheetGlass != null) {
        SheetStillPictures(backdrop, sheetGlass, extra = sheetBlur())
        DisposableEffect(sheetGlass, backdrop) {
            sheetGlass.screen = backdrop
            onDispose { if (sheetGlass.screen === backdrop) sheetGlass.screen = null }
        }
    }

    val waveShader = remember(wave != null) { if (wave != null) heroWaveShader() else null }
    var waveAt by remember { mutableStateOf(Offset.Zero) }
    val tuning = LocalGlassTuning.current

    val sheetBlur = sheetBlur()
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Box(
            Modifier.fillMaxSize().then(
                // The glass style: under a bottom sheet the screen blurs as the sheet comes out; Home's connect wave
                // bends it all.
                if (LocalGlassStyle.current.enabled) Modifier.onGloballyPositioned { waveAt = it.positionInRoot() }.graphicsLayer {
                    val shown = sheetGlass?.shown ?: 0f
                    renderEffect = when {
                        shown > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> sheetBlurEffect(sheetBlur.toPx(), shown)
                        wave != null && waveShader != null -> screenWaveEffect(waveShader, wave, waveAt, tuning)
                        else -> null
                    }
                } else Modifier,
            ),
        ) {
            // Под панелью резервируем её измеренную высоту: при крупном шрифте она
            // выше, и последний пункт списка не должен уезжать под неё.
            val reserved = with(density) { dockDepth.toDp() }
            val rtl = direction == LayoutDirection.Rtl
            val side = if (rtl) bars.getRight(density, direction) else bars.getLeft(density, direction)

            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            content(
                if (rail) {
                    PaddingValues(
                        start = with(density) { side.toDp() } + RAIL_EDGE + reserved,
                        end = insets.calculateEndPadding(direction),
                        top = insets.calculateTopPadding(),
                        bottom = insets.calculateBottomPadding(),
                    )
                } else {
                    PaddingValues(
                        start = insets.calculateStartPadding(direction),
                        end = insets.calculateEndPadding(direction),
                        top = insets.calculateTopPadding(),
                        // Открыта клавиатура — панель под ней, и отступ нужен только под клавиатуру.
                        bottom = maxOf(
                            insets.calculateBottomPadding(),
                            bars.asPaddingValues().calculateBottomPadding() + reserved,
                        ),
                    )
                },
                insets,
            )
            }

            NavigationFade()

            Box(
                modifier = Modifier.align(if (rail) Alignment.CenterStart else Alignment.BottomCenter)
                    .windowInsetsPadding(
                        bars.only(
                            WindowInsetsSides.Horizontal +
                                if (rail) WindowInsetsSides.Vertical else WindowInsetsSides.Bottom,
                        ),
                    )
                    .padding(horizontal = if (rail) RAIL_EDGE else 30.dp, vertical = 12.dp)
                    .graphicsLayer {
                        // Ниже края целиком, вместе с отступом и тенью; сбоку — за свой край.
                        if (rail) {
                            translationX = hidden * (size.width + side + 40.dp.toPx()) * if (rtl) 1f else -1f
                        } else {
                            translationY = hidden * (size.height + below + 40.dp.toPx())
                        }
                    }
                    .then(if (dockShown) Modifier else Modifier.clearAndSetSemantics { }),
            ) {
                FloatingDock(
                    selected = selected,
                    position = position,
                    onSelect = onSelect,
                    onAction = onAction,
                    actionLabel = actionLabel,
                    single = single,
                    onSingle = onSingle,
                    vertical = rail,
                    modifier = Modifier.onSizeChanged { dockDepth = if (rail) it.width else it.height },
                    backdrop = backdrop,
                )
            }
        }
    }
}

/**
 * The content fades out down to the three-button bar, which sits on no scrim since Theme.kt turned it off.
 * Nothing with gestures: that bar has nothing tappable.
 *
 * Telegram's tab fade: the veil builds over 60 dp in even steps to 82 %, so the content still shows faintly
 * under the buttons; ours ended opaque, and its ramp showed a hard line where it got dense. Telegram starts
 * it at its tabs' top; ours starts at the dock's lower edge, 12 dp over the bar — the dock is taller, and
 * the veil reached over its sides — and every screen starts it there, so they all look alike.
 */
@Composable
internal fun BoxScope.NavigationFade() {
    val density = LocalDensity.current

    if (WindowInsets.tappableElement.getBottom(density) == 0) return

    val background = MaterialTheme.colorScheme.background
    val brush = remember(background, density) {
        Brush.verticalGradient(
            0f to background.copy(alpha = 0f),
            1f / 3 to background.copy(alpha = 0.34f),
            2f / 3 to background.copy(alpha = 0.62f),
            1f to background.copy(alpha = 0.82f),
            endY = with(density) { FADE_RAMP.toPx() },
        )
    }

    Box(
        Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + 12.dp)
            .background(brush),
    )
}

private val FADE_RAMP = 60.dp

/**
 * Room at the end of an overlay screen's list: those screens run under the three-button bar now and fade out
 * there ([NavigationFade]). Nothing while the keyboard is up, which already lifts the screen.
 */
@Composable
internal fun navigationRoom(): Dp =
    WindowInsets.navigationBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()

/** The TV's round button under its dock (D-97). */
val ACTION_SIZE = 56.dp

/** The dock's padding around its tabs. */
internal val DOCK_PAD = 4.dp

/**
 * The side dock: its gap to the start edge, its width, a tab's height and the highlight's corner. Narrow and tall:
 * 108 dp wide looked a blob beside the cards.
 */
private val RAIL_EDGE = 12.dp
private val RAIL_WIDTH = 88.dp
private val RAIL_TAB = 72.dp
private val RAIL_CORNER = 20.dp

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
    onAction: (() -> Unit)?,
    actionLabel: String?,
    single: String?,
    onSingle: () -> Unit,
    /** Standing at the side ([lowWindow]): the tabs go top to bottom, and so does the highlight. */
    vertical: Boolean,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
) {
    // The glass style's phone dock is its own, after iOS's (GlassDock); the side dock keeps this one on glass too.
    if (backdrop != null && !vertical && glassSupported) {
        GlassDock(selected, position, onSelect, onAction, actionLabel, single, onSingle, backdrop, modifier)
        return
    }

    // Upright, the dock and its highlight round their corners as the TV's side dock does: as pills, nearly square
    // tabs became circles, and the dock's round ends cut the highlight's.
    val shape = if (vertical) RoundedCornerShape(RAIL_CORNER + DOCK_PAD) else RoundedCornerShape(percent = 50)
    val pill = MaterialTheme.colorScheme.primaryContainer.let { if (backdrop != null) it.copy(alpha = 0.8f) else it }
    val onPill = MaterialTheme.colorScheme.onPrimaryContainer
    val face = rememberDockFace(single, onAction, position, glass = backdrop != null)
    val alone = single != null

    // Brighter on the light theme's black highlight than on the dark theme's white one, as in the mockup.
    val tabShineColor = onPill.copy(alpha = if (pill.luminance() < 0.5f) 0.38f else 0.2f)

    val current = selected()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val screen = MaterialTheme.colorScheme.background

    // The highlight along the dock: across its width at the bottom, down its height at the side.
    fun DrawScope.pill(): Rect {
        val length = if (vertical) size.height else size.width
        val start = lerp(dockPillOffset(length, position(), rtl = rtl && !vertical), 0f, face.merge)
        val extent = lerp(dockSlotWidth(length), length, face.merge)

        return if (vertical) Rect(Offset(0f, start), Size(size.width, extent))
        else Rect(Offset(start, 0f), Size(extent, size.height))
    }

    val tuning = LocalGlassTuning.current
    CompositionLocalProvider(LocalGlassStyle provides tuning.optics(GlassTuningGroup.Dock, LocalGlassStyle.current)) {
    Surface(
        modifier = modifier.then(if (vertical) Modifier.width(RAIL_WIDTH) else Modifier.widthIn(max = 420.dp)).airShadow(shape)
            .liquidGlass(
                backdrop,
                base = screen,
                tint = MaterialTheme.colorScheme.surface.let { it.copy(alpha = it.alpha * GlassMaterial.Surface.tint) },
                shape = shape,
                dockOptics = true,
            ),
        shape = shape,
        color = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxWidth().padding(DOCK_PAD)) {
            // Подсветка — своим слоем под обеими копиями вкладок: при слиянии она растёт,
            // а вкладки гаснут, и гасить её вместе с ними нельзя.
            Box(
                Modifier.matchParentSize().drawBehind {
                    val drop = face.splash.value
                    val dropping = drop > 0f && drop < 1f
                    // The capsule swells a little as the drop lands, and settles back.
                    val rect = pill().let { if (dropping) it.inflate(it.height * SPLASH_SWELL * sin(PI.toFloat() * minOf(1f, drop * 2f))) else it }
                    val corner = CornerRadius(if (vertical) RAIL_CORNER.toPx() else rect.minDimension / 2f)

                    drawRoundRect(color = pill, topLeft = rect.topLeft, size = rect.size, cornerRadius = corner)

                    // On a dark theme the highlight is light itself and needs no rim.
                    if (backdrop != null && screen.luminance() > 0.5f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.25f),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = corner,
                            style = Stroke(1.dp.toPx()),
                        )
                    }

                    if (dropping) drawSplash(rect, corner, drop)

                    val t = face.tabShine.value

                    if (t > 0f && t < 1f) {
                        // The band is transparent outside itself, so the highlight's own shape clips it.
                        val band = rect.width * 0.6f
                        val x = rect.left - band + (rect.width + band * 2f) * t

                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, tabShineColor, Color.Transparent),
                                start = Offset(x - band / 2f, rect.top),
                                // A 15° slant, as in the mockup.
                                end = Offset(x + band / 2f, rect.top + band * 0.27f),
                            ),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = corner,
                        )
                    }
                },
            )

            DockTabs(
                selected = current,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onSelect = if (alone) null else { index ->
                    val action = onAction
                    if (index == ACTION_TAB && face.added && action != null) action() else onSelect(index)
                },
                turn = face.turn,
                actionLabel = if (face.added) actionLabel else null,
                vertical = vertical,
                modifier = Modifier
                    .then(if (alone) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer { alpha = 1f - (face.merge * 2f).coerceIn(0f, 1f) },
            )

            DockTabs(
                selected = current,
                content = onPill,
                onSelect = null,
                turn = face.turn,
                actionLabel = null,
                vertical = vertical,
                // Нажатия и озвучка живут в нижней копии: верхняя только перекрашивает,
                // иначе TalkBack читал бы каждую вкладку дважды.
                modifier = Modifier.clearAndSetSemantics { }
                    .graphicsLayer { alpha = 1f - (face.merge * 2f).coerceIn(0f, 1f) }
                    .drawWithContent {
                        val rect = pill()

                        clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )

            SingleFace(face, single, onSingle, vertical, shape, onPill)
        }
    }
    }
}

/**
 * What both phone docks show besides the tabs: the merge into one button while there is nothing to show (D-98), and
 * «Подписки» turned into «+ Добавить» (D-390), with their shines. [merge] is read in drawing only.
 */
internal class DockFace(
    private val merging: State<Float>,
    /** The one button's words, kept while it turns back into the tabs. */
    val label: String,
    /** The shine over the one button. */
    val shine: Animatable<Float, AnimationVector1D>,
    /** «Подписки» reads «+ Добавить». */
    val added: Boolean,
    val turn: Transition<Boolean>,
    /** The glass style's drop as «+ Добавить» comes: a ring of light from the capsule, 0..1. */
    val splash: Animatable<Float, AnimationVector1D>,
    /** Air's shines over the highlight as «+ Добавить» comes. */
    val tabShine: Animatable<Float, AnimationVector1D>,
) {
    /** 0 — the tabs, 1 — the one button. */
    val merge: Float get() = merging.value
}

@Composable
internal fun rememberDockFace(single: String?, onAction: (() -> Unit)?, position: () -> Float, glass: Boolean): DockFace {
    val alone = single != null

    // 0 — три вкладки, 1 — одна кнопка во всю панель (D-98). Подсветка вкладки
    // расползается на всю ширину, вкладки гаснут, проявляется надпись; обратно — в обратном
    // порядке, и из кнопки снова вырастают вкладки. Читается только в отрисовке.
    val merge = animateFloatAsState(
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
    val motionEnabled = rememberCoroutineScope().coroutineContext.motionScale > 0f

    LaunchedEffect(alone, motionEnabled) {
        shine.snapTo(0f)

        if (!alone || !motionEnabled) return@LaunchedEffect

        delay(MERGE_MS.toLong())

        while (true) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(SHINE_MS, easing = FastOutSlowInEasing))
            delay(SHINE_PAUSE_MS)
        }
    }

    // «Подписки» turns into «+ Добавить» once the pager has rested on it for a moment, so the person first reads
    // where they came, and turns back as soon as the page moves (D-390). Until then a tap on it does nothing,
    // so a quick double tap does not open adding.
    var added by remember { mutableStateOf(false) }

    LaunchedEffect(onAction != null, alone) {
        // Merging into the one button keeps «+ Добавить» as it is: it grows into «+ Добавить подписку».
        if (alone) return@LaunchedEffect

        if (onAction == null) {
            added = false

            return@LaunchedEffect
        }

        // Within a hair of the page rather than exactly on it, in case the pager stops a pixel off.
        snapshotFlow { abs(position() - ACTION_TAB) < 0.01f }.collectLatest { resting ->
            if (!resting) {
                added = false
            } else if (!added) {
                // The glass lens has just landed: the turn follows it at once.
                delay(if (glass) GLASS_ACTION_PAUSE_MS else ACTION_PAUSE_MS)
                added = true
            }
        }
    }

    val turn = updateTransition(added, label = "action")
    val splash = remember { Animatable(0f) }

    // Three shines over the highlight, then still: the call is made once, not repeated (D-91).
    val tabShine = remember { Animatable(0f) }

    LaunchedEffect(added, alone, motionEnabled) {
        tabShine.snapTo(0f)
        splash.snapTo(0f)

        if (!added || alone || !motionEnabled) return@LaunchedEffect

        if (glass) {
            // On glass drops of light instead of the shines: a ring leaves the capsule as it swells and settles, three
            // times, as Air's shines, so the new button is noticed (the owner, 17.11).
            repeat(ACTION_SHINES) { pass ->
                if (pass > 0) delay(ACTION_SHINE_GAP_MS)

                splash.snapTo(0f)
                splash.animateTo(1f, tween(SPLASH_MS, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }

        delay(ACTION_SHINE_DELAY_MS)

        repeat(ACTION_SHINES) { pass ->
            if (pass > 0) delay(ACTION_SHINE_GAP_MS)

            tabShine.snapTo(0f)
            tabShine.animateTo(1f, tween(SHINE_MS, easing = FastOutSlowInEasing))
        }
    }

    return DockFace(merge, lastLabel.value, shine, added, turn, splash, tabShine)
}

/** The glass drop's ring of light leaving the capsule [rect], [drop] 0..1 of the way. */
internal fun DrawScope.drawSplash(rect: Rect, corner: CornerRadius, drop: Float) {
    val outline = Path().apply { addRoundRect(RoundRect(rect, corner)) }

    clipPath(outline) {
        drawCircle(
            color = Color.White.copy(alpha = 0.55f * (1f - drop)),
            radius = lerp(rect.height * 0.25f, rect.width * 0.7f, drop),
            center = rect.center,
            style = Stroke((3.dp.toPx() * (1f - drop)).coerceAtLeast(0.5f)),
        )
    }
}

/** The one button the dock turns into while there is nothing to show (D-98): «+» and [single]'s words, with its shine. */
@Composable
internal fun BoxScope.SingleFace(face: DockFace, single: String?, onSingle: () -> Unit, vertical: Boolean, shape: Shape, color: Color) {
    if (face.label.isEmpty()) return

    val faceModifier = Modifier.matchParentSize()
        .graphicsLayer { alpha = ((face.merge - 0.5f) * 2f).coerceIn(0f, 1f) }
        .clip(shape)
        .then(
            if (single != null) Modifier.clickable(role = Role.Button, onClick = onSingle)
            else Modifier.clearAndSetSemantics { },
        )
        .drawWithContent {
            drawContent()

            val t = face.shine.value

            if (t > 0f && t < 1f) {
                val band = size.width * 0.4f
                val x = -band + (size.width + band * 2f) * t

                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset(x - band / 2f, 0f),
                        end = Offset(x + band / 2f, size.height),
                    ),
                )
            }
        }

    if (vertical) {
        // The side dock is narrow: the «+» over the words, which take two lines.
        Column(
            modifier = faceModifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(Symbols.Add, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))

            Text(
                text = single ?: face.label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    } else {
        Row(
            modifier = faceModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Symbols.Add, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))

            Spacer(Modifier.width(8.dp))

            Text(
                text = single ?: face.label,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

internal val SHEET_BLUR = 18.dp
/** How far below a glass sheet's top corners the light on its rim fades out. */
internal val SHEET_RIM_FADE = 160.dp

/** Слияние вкладок в одну кнопку и обратно (D-98). */
private const val MERGE_MS = 420

/** Проход блика по кнопке и пауза между проходами. */
private const val SHINE_MS = 900
private const val SHINE_PAUSE_MS = 4000L

/** The tab that turns into «+ Добавить»: «Подписки» (D-390). */
internal const val ACTION_TAB = 2

/** How long the pager rests on it before it turns. */
private const val ACTION_PAUSE_MS = 280L

/** On glass the lens's landing already marks the arrival: the turn follows it at once. */
private const val GLASS_ACTION_PAUSE_MS = 60L

/** The glass drop: its length, and how much the capsule swells, of its height. */
private const val SPLASH_MS = 700
internal const val SPLASH_SWELL = 0.06f

/** Its shines: from the start of the turn to the first, between passes, and how many. */
private const val ACTION_SHINE_DELAY_MS = 140L
private const val ACTION_SHINE_GAP_MS = 750L
private const val ACTION_SHINES = 3

/** The curves of the mockup: the standard deceleration and the «+»'s slight overshoot. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val OvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Вкладки дока — на телефоне и на телевизоре. */
internal val DockTabItems = listOf(
    R.string.home_tab to Symbols.Home,
    R.string.proxies_tab to Symbols.Language,
    R.string.subscriptions_tab to Symbols.Article,
)

/** The tabs' labels: one weight for all, so both copies of the row match to the pixel. */
@Composable
internal fun dockLabelStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)

/**
 * Один ряд вкладок. Обе копии обязаны совпадать по метрике до пикселя, иначе обрезанная
 * поедет относительно нижней, — поэтому жирность подписи одна на все вкладки, а выбор
 * несут подсветка и цвет.
 */
@Composable
internal fun DockTabs(
    selected: Int,
    content: Color,
    onSelect: ((Int) -> Unit)?,
    turn: Transition<Boolean>,
    /** What a tap on «Подписки» does once it reads «+ Добавить», for TalkBack. */
    actionLabel: String?,
    /** Top to bottom, in the side dock. */
    vertical: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    val style = dockLabelStyle()
    val selectedWord = stringResource(R.string.tab_selected)

    val group = modifier.fillMaxWidth()
        .then(if (onSelect != null) Modifier.selectableGroup().focusGroup() else Modifier)

    // [slot] — the tab's share of the row, or its row in the column.
    val tab: @Composable (Int, Modifier) -> Unit = { index, slot ->
        val (label, icon) = DockTabItems[index]
        val active = selected == index
        val name = stringResource(label)

        Column(
            modifier = slot
                .clip(if (vertical) RoundedCornerShape(RAIL_CORNER) else RoundedCornerShape(percent = 50))
                .then(
                    if (onSelect != null) {
                        // What `selectable` is, plus a click label: turned into «+ Добавить», the tab keeps
                        // its name and role, and TalkBack says what a tap does (D-390).
                        Modifier.semantics {
                            if (index == ACTION_TAB) {
                                // Compose takes TalkBack's click away from a selected tab, so this one says
                                // its selection in words. Its name is its own: a label at zero alpha is
                                // hidden from TalkBack.
                                contentDescription = name
                                if (active) stateDescription = selectedWord
                            } else {
                                this.selected = active
                            }
                        }
                            .clickable(
                                onClickLabel = if (index == ACTION_TAB) actionLabel else null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            if (index == ACTION_TAB) {
                ActionFace(turn, icon, name, style, content, iconSize)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = content,
                )

                FitLabel(
                    text = name,
                    style = style,
                    color = content,
                )
            }
        }
    }

    if (vertical) {
        Column(group) {
            // Equal rows, so the highlight's slot (a third of the height) is each tab's.
            DockTabItems.indices.forEach { tab(it, Modifier.fillMaxWidth().heightIn(min = RAIL_TAB)) }
        }
    } else {
        Row(group, verticalAlignment = Alignment.CenterVertically) {
            DockTabItems.indices.forEach { tab(it, Modifier.weight(1f)) }
        }
    }
}

/**
 * «Подписки» and the «+ Добавить» it turns into, one over the other (D-390). The icon leaves with a turn and the
 * «+» comes from a quarter turn, as the round «+» did; the new label rises from below. Neither label is in
 * semantics: the tab carries its name itself. Both copies of the tabs run this on one transition, so they stay
 * in step to the pixel.
 */
@Composable
private fun ActionFace(turn: Transition<Boolean>, icon: ImageVector, label: String, style: TextStyle, content: Color, iconSize: Dp) {
    // The mockup's timings: «Подписки» goes in 180 ms; «+ Добавить» comes 60 ms later and goes back in 160 ms.
    val gone by turn.animateFloat({ tween(180, easing = StandardEasing) }, label = "gone") { if (it) 1f else 0f }
    val faded by turn.animateFloat({ tween(140) }, label = "faded") { if (it) 1f else 0f }
    val plus by turn.animateFloat(
        transitionSpec = {
            if (targetState) tween(340, delayMillis = 60, easing = OvershootEasing)
            else tween(160, easing = FastOutLinearInEasing)
        },
        label = "plus",
    ) { if (it) 1f else 0f }
    val rise by turn.animateFloat(
        transitionSpec = { if (targetState) tween(300, delayMillis = 60, easing = StandardEasing) else tween(160) },
        label = "rise",
    ) { if (it) 1f else 0f }
    val shown by turn.animateFloat(
        transitionSpec = { if (targetState) tween(160, delayMillis = 60) else tween(120) },
        label = "shown",
    ) { if (it) 1f else 0f }

    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize).graphicsLayer {
                alpha = 1f - faded
                rotationZ = gone * 90f
                scaleX = lerp(1f, 0.4f, gone)
                scaleY = scaleX
            },
            tint = content,
        )

        Icon(
            imageVector = Symbols.Add,
            contentDescription = null,
            modifier = Modifier.size(iconSize).graphicsLayer {
                alpha = shown
                rotationZ = (1f - plus) * -90f
                scaleX = lerp(0.4f, 1f, plus)
                scaleY = scaleX
            },
            tint = content,
        )
    }

    Box(contentAlignment = Alignment.Center) {
        FitLabel(
            text = label,
            style = style,
            modifier = Modifier.clearAndSetSemantics { }.graphicsLayer {
                alpha = 1f - faded
                translationY = gone * -7.dp.toPx()
            },
            color = content,
        )

        FitLabel(
            text = stringResource(R.string.add),
            style = style,
            modifier = Modifier.clearAndSetSemantics { }.graphicsLayer {
                alpha = shown
                translationY = (1f - rise) * 7.dp.toPx()
            },
            color = content,
        )
    }
}
