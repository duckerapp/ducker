package com.github.kr328.clash.ui.components

import kotlin.math.roundToInt
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.getFocusedRect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Фокус на телевизоре (этап 6): вкладки дока, страницы вкладок и места, куда пульт входит в
 * страницу впервые. Есть только на телевизоре — по нему раскладка и узнаёт, что её водят пультом.
 */
class TvFocus {
    val tabs = List(TABS) { FocusRequester() }
    val pages = List(TABS) { FocusRequester() }
    val entries = List(TABS) { FocusRequester() }

    /** Шестерёнка под доком: настройки закрылись — фокус обратно на неё. */
    val gear = FocusRequester()

    /**
     * The screen over the tabs that has the remote now, see [BackLayer]. Focus lost on it (the element under
     * the frame disappeared) returns to it: otherwise the next key put the frame on the tab under the screen.
     */
    var cover: FocusRequester? = null

    /** Что сбросить на странице перед входом: список уехал — точки входа в нём может не быть. */
    var reset: suspend (page: Int) -> Unit = {}

    /**
     * Пульт «стоит на вкладке дока», а фокус остался в странице, где был (D-161). Так влево со
     * страницы и «назад» ведут в док, а вправо или ОК возвращают ровно туда же: случайно вышел —
     * узел заново искать не надо. Ушёл по доку на другую вкладку — это уже настоящий переход, и
     * вернувшись, человек начинает страницу сначала.
     */
    var parked by mutableStateOf(false)

    /** Вход в страницу — всегда с начала: прокрутка сброшена, фокус на точке входа (D-158). */
    suspend fun enter(page: Int) {
        parked = false
        reset(page)
        pages[page].requestFocus()
    }

    private var steered = 0L

    /** Человек ведёт фокус по доку: клавиша в нём или «назад». */
    fun steer() {
        steered = SystemClock.uptimeMillis()
    }

    /**
     * Листать страницу вкладке, на которую встал фокус, — только если его туда вёл человек. Элемент
     * под фокусом исчез или выключился — Compose сам ставит фокус на первую вкладку, и страница
     * перелистывалась на «Главную» (D-158).
     */
    val steering: Boolean get() = SystemClock.uptimeMillis() - steered < STEER_MS
}

private const val STEER_MS = 500L

/**
 * Телевизор ли это. Всё, что сделано под пульт, работает только на нём: телефон с клавиатурой
 * или мышью ведёт себя как раньше, до этапа 6 (D-161).
 */
val LocalTv = staticCompositionLocalOf { false }

/** Телевизор, и его сейчас ведут пультом, а не указкой. Вне телевизора — всегда нет. */
@Composable
internal fun tvKeys(): Boolean = LocalTv.current && LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** The remote's focus of the tab layout; `null` off a TV. */
val LocalTvFocus = staticCompositionLocalOf<TvFocus?> { null }

/** Точка входа в страницу: её даёт вкладка, экран вешает [tabEntry] на нужный элемент. */
val LocalTabEntry = staticCompositionLocalOf<FocusRequester?> { null }

/** Сюда встаёт фокус, когда пульт впервые входит в страницу из дока. Вне телевизора — ничего. */
fun Modifier.tabEntry(): Modifier = composed {
    LocalTabEntry.current?.let { Modifier.focusRequester(it) } ?: Modifier
}

/**
 * Комната для фокуса. Вкладка ([entry] есть): пульт входит в неё через [requester] всегда на
 * [entry], а не туда, где стоял в прошлый раз, — вернувшись во вкладку, человек начинает её
 * сначала (D-158). Поэтому и `focusRestorer()` внутри вкладок нет: он перехватывал бы вход и вёл
 * на старое место. Экран поверх вкладок ([entry] нет) место помнит: из журнала назад в
 * настройки — на «Журнал».
 * Стрелками из комнаты не выйти: за краем страницы — соседние вкладки, под экраном поверх
 * вкладок — невидимая лента. Кроме [onLeft]: влево со страницы — её вкладка в доке, as soon as the element has
 * nothing to its left in its own row ([LeaveLeftNode]).
 */
internal fun Modifier.focusRoom(
    requester: FocusRequester,
    entry: FocusRequester? = null,
    onLeft: (() -> Unit)? = null,
): Modifier = this then FocusNudgeElement
    .then(if (onLeft != null) LeaveLeftElement(onLeft) else Modifier)
    .focusProperties {
        onExit = {
            val direction = requestedFocusDirection

            if (direction in Arrows) cancelFocusChange()
            if (direction == FocusDirection.Left) onLeft?.invoke()
        }
    }
    .focusGroup()
    .then(if (entry != null) Modifier.focusProperties { onEnter = { entry.requestFocus() } } else Modifier.focusRestorer())
    .focusRequester(requester)
    .focusGroup()

/**
 * Кнопка, которая выключается на время работы, с пульта остаётся включённой и просто молчит:
 * выключенная теряет фокус, и пульт оставался ни на чём (D-158). Нажимать — только при [active].
 */
@Composable
internal fun keepFocus(active: Boolean): Boolean = active || tvKeys()

/**
 * Поле ввода под пульт (F-188): в клавишном режиме строка с полем — одна кнопка, ввод открывается
 * по ОК. Иначе фокус проходил через поле по пути дальше, клавиатура выезжала от одного наведения,
 * а рамка обводила только поле без значков. Уходит фокус из поля — строка снова кнопка.
 * Первое — на строку, второе — на само поле. Касаниями поле как было. [corner] — скругление
 * строки, если она не пилюля: рамка обводит строку целиком и пока идёт ввод, а не одно поле в ней.
 */
@Composable
internal fun keyField(corner: Dp? = null): Pair<Modifier, Modifier> {
    if (!LocalTv.current) return Modifier to Modifier

    val keys = tvKeys()
    val field = remember { FocusRequester() }
    val pill = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val row = if (keys) {
        Modifier.ringAnchor(corner = corner) { Rect(Offset.Zero, Size(it.width.toFloat(), it.height.toFloat())) }
            .focusRequester(pill)
            .clickable(interactionSource = null, indication = null) {
                editing = true
                field.requestFocus()
            }
    } else Modifier

    return row to Modifier.focusRequester(field)
        .focusProperties { canFocus = !keys || editing }
        // Ввод идёт, пока поле в фокусе, откуда бы он ни пришёл: пальцем на телефоне тоже.
        // Иначе первая клавиша настоящей клавиатуры переводила бы в клавишный режим и
        // выбивала фокус из поля посреди набора.
        .onFocusChanged { editing = it.isFocused }
        // Вверх и вниз поле забирает себе под курсор, и фокус из него не уходил. С клавишами
        // они заканчивают ввод: фокус — на строку, и уже с неё шаг, как с обычной кнопки.
        // Пока открыта клавиатура, стрелки у неё, а закрывает её «назад».
        .onPreviewKeyEvent {
            val direction = when (it.key) {
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> null
            }

            if (!keys || direction == null || it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

            pill.requestFocus()
            focusManager.moveFocus(direction)
            true
        }
}

/**
 * Граница страницы для «показать элемент» (`bringIntoView`): внутри страницы списки прокручиваются
 * к фокусу как обычно, а в ленту страниц запрос не идёт — её на телевизоре ведёт только док (F-192).
 * Прокрутку страниц лента не забирает по той же причине — [TvPagerScroll].
 */
internal fun Modifier.pageBoundary(): Modifier = this then PageBoundaryElement

/**
 * Лента страниц на телевизоре не забирает прокрутку своих страниц. По умолчанию, пока она не
 * доехала до страницы, она съедает прокрутку содержимого и едет сама: колонка главной докручивалась
 * к кнопке питания, отдавала ленте по 12 пикселей за кадр, и та уезжала мимо вкладки (F-192).
 */
internal val TvPagerScroll = object : NestedScrollConnection {}

private data object PageBoundaryElement : ModifierNodeElement<PageBoundaryNode>() {
    override fun create() = PageBoundaryNode()

    override fun update(node: PageBoundaryNode) = Unit
}

private class PageBoundaryNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = Unit
}

/** Направления, которыми фокус двигают клавиши. Программный перевод фокуса идёт как `Enter`. */
private val Arrows = listOf(
    FocusDirection.Left,
    FocusDirection.Right,
    FocusDirection.Up,
    FocusDirection.Down,
    FocusDirection.Next,
    FocusDirection.Previous,
)

/**
 * Рамка выделения пультом: белый контур вокруг элемента с ровным зазором и лёгкий неон наружу.
 * Зазор — чтобы рамка читалась и на белом: вплотную к белой кнопке питания или вкладке дока
 * белый контур с ней сливался, и с дивана фокуса не было видно (D-161). Контур и неон рисуются
 * «разницей» с тем, что под ними: на тёмном они светлые, на белой карточке тёмные — в обеих темах.
 *
 * Одна на весь экран и поверх него, а не у каждой кнопки: готовые кнопки, карточки и
 * переключатели Material рисуют нажатие сами и общий стиль из темы не берут. Касание фокус
 * не ставит, поэтому на телефоне рамки не было бы видно, — но ставится она только на телевизоре.
 *
 * Сообщать о переходе фокуса Compose не умеет (прежний способ устарел и перестанет работать),
 * положение спрашивают сами — при отрисовке, а не заранее: иначе рамка брала место элемента до
 * шага прокрутки в этом кадре и отставала на кадр. Фокус двигают клавиши, поэтому после каждого
 * нажатия рамка перерисовывается каждый кадр — не меньше [FOLLOW_NS] и дальше, пока элемент едет
 * (прокрутка к нему, въезд экрана).
 * «Назад» до клавиш не доходит (его забирает системный жест), а фокус переводит — поэтому
 * о входе и выходе фокуса рамке сообщают и сами комнаты ([focusRoom]) и вкладки дока.
 *
 * С элемента на элемент рамка переезжает, а не перескакивает, — той же пружиной, которой список
 * подъезжает к фокусу ([TvSpring]). У края списка новый элемент ещё едет на своё место, и рамка,
 * прыгнув на него сразу, «доезжала» следом; с одной пружиной на двоих отставание рамки от элемента
 * тает ровно так же, как путь элемента, — рамка стоит, а список едет под ней. Пока элемент под
 * рамкой просто едет, она держится за него кадр в кадр. Элемент въезжает или уезжает в своей
 * анимации — рамку на это время прячут, см. [ringHold].
 *
 * Форму элемента рамка не знает и угадывает по размеру: квадрат обводит кругом, низкое —
 * пилюлей (кнопки), остальное скругляет на [RING_CORNER] (у карточек 18–24). Где угадка не
 * сходится, скругление даёт сам элемент — [ringCorner].
 */
fun Modifier.focusRing(onLost: (() -> Unit)? = null): Modifier = this then FocusRingElement(onLost)

private data class FocusRingElement(val onLost: (() -> Unit)?) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(onLost)

    override fun update(node: FocusRingNode) {
        node.onLost = onLost
    }
}

private class FocusRingNode(var onLost: (() -> Unit)?) :
    DelegatingNode(), DrawModifierNode, KeyInputModifierNode, TraversableNode, LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    override val traverseKey: Any get() = RingKey
    // Фокус в окне появился или пропал совсем — это тоже повод сверить рамку. Пропал, потому что
    // элемент под ним исчез или выключился, — пусть раскладка вернёт его ([onLost], D-158): иначе
    // следующая клавиша ставила фокус в верхний левый угол, на «Главную» в доке.
    private val target: FocusTargetModifierNode = delegate(
        FocusTargetModifierNode(Focusability.Never) { previous, current ->
            follow()
            if (previous.hasFocus && !current.hasFocus) onLost?.let { lost ->
                coroutineScope.launch {
                    withFrameNanos { }
                    if (target.getFocusedRect() == null) lost()
                }
            }
        },
    )
    /** Кто рисует рамку вместо элемента в фокусе: док ведёт её вместе со своей подсветкой. */
    var anchor: RingAnchorNode? = null
    /** Скругление элемента в фокусе, если он его назвал. */
    var corner: RingCornerNode? = null
    private var holds = 0
    // Размытие рисует сама Android: у Compose своей размытой линии нет. Кисть — одна на узел.
    private val blur = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // Фильтр размытия — под плотность экрана, один раз: отрисовка идёт и на каждом кадре прокрутки.
    private var filterFor = 0f
    private var neonFilter: BlurMaskFilter? = null
    private var following: Job? = null
    private var size = IntSize.Zero
    // Время кадра, который рисуется: его знает опрос, у отрисовки своих часов нет.
    private var frame = 0L
    // Следующий скачок места — переход фокуса: было нажатие или фокус сменил группу.
    private var armed = false
    // Куда рамка показывала, на сколько цель сдвинулась за прошлый кадр и где рамка была
    // нарисована; отставание от цели в начале переезда, по сторонам; когда переезд начался и
    // когда рамка появилась.
    private var aimed: Rect? = null
    private var aimStep = Offset.Zero
    private var drawn: Rect? = null
    private var glide: Rect? = null
    private var glideAt = 0L
    // Скругление, с которым рамка нарисована, и его отставание в переезде: с пилюли на круг
    // рамка перетекает, а не щёлкает в круг последним кадром.
    private var round = 0f
    private var glideRound = 0f
    private var shownAt = 0L

    // The element under the frame also moves without a key: a card that appears above the power button pushes it
    // down, and the frame stayed where the button had been. After each frame of the window it checks its place —
    // after, because Compose lays out in its own drawing; the check asks for no frame of its own.
    private val check = Runnable {
        checking = false
        if (isAttached && size != IntSize.Zero && following?.isActive != true && aim() != aimed) follow(arm = false)
    }
    private val moved = ViewTreeObserver.OnPreDrawListener {
        if (!checking) {
            checking = true
            view?.post(check)
        }
        true
    }
    private var checking = false
    private var view: android.view.View? = null

    override fun onAttach() {
        view = currentValueOf(LocalView).also { it.viewTreeObserver.addOnPreDrawListener(moved) }
        follow()
    }

    override fun onDetach() {
        view?.let {
            it.viewTreeObserver.removeOnPreDrawListener(moved)
            it.removeCallbacks(check)
        }
        view = null
        checking = false
    }

    // Окно сменило размер (выехала или ушла клавиатура) — всё съехало, и рамка вместе с ним.
    override fun onRemeasured(size: IntSize) {
        if (size != this.size) {
            this.size = size
            follow()
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        // Переезд рамки взводит только нажатие: отпускание приходит уже посреди прокрутки.
        follow(arm = event.type == KeyEventType.KeyDown)

        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    /** Спрятать рамку ([by] = 1) или вернуть (−1), см. [ringHold]. */
    fun hold(by: Int) {
        holds += by
        follow()
    }

    fun follow(arm: Boolean = true) {
        if (!isAttached) return

        if (arm) armed = true
        following?.cancel()
        following = coroutineScope.launch {
            var start = 0L
            var moved = 0L
            var seen: Rect? = null

            do {
                val now = withFrameNanos { it }
                if (start == 0L) start = now

                frame = now

                val rect = aim()

                if (rect != seen) {
                    seen = rect
                    moved = now
                }

                invalidateDraw()
            } while (
                now - start < FOLLOW_NS || glide != null || now - shownAt < FADE_NS ||
                (now - moved < STILL_NS && now - start < FOLLOW_MAX_NS)
            )

            armed = false
        }
    }

    /** Скругление элемента на месте [r]: названное им самим или угаданное по размеру. */
    private fun Density.cornerOf(r: Rect): Float {
        val half = min(r.width, r.height) / 2
        // Место дока — не элемент в фокусе: скругление того сюда не годится.
        val named = anchor?.let { it.corner } ?: corner?.takeIf { anchor == null && it.isAttached }?.corner

        return when {
            named != null -> named.toPx()
            abs(r.width - r.height) < 1.dp.toPx() -> half
            r.height <= RING_PILL.toPx() -> half
            else -> RING_CORNER.toPx()
        }.coerceAtMost(half)
    }

    /** Что обводить: место, которое дал док, или элемент в фокусе; в своих координатах. */
    private fun aim(): Rect? =
        if (holds > 0) null else anchor?.rectIn(requireLayoutCoordinates()) ?: target.getFocusedRect()

    override fun ContentDrawScope.draw() {
        drawContent()

        // Место считается здесь, при отрисовке, а не в опросе: раскладка этого кадра уже прошла.
        val aim = aim()

        if (aim == null) {
            aimed = null
            aimStep = Offset.Zero
            drawn = null
            glide = null
            return
        }

        val now = frame
        val before = aimed
        val last = drawn

        // Фокус перешёл на другой элемент — рамка переезжает оттуда, где нарисована. Отставание
        // считается от цели и тает само, поэтому цель может ехать и во время переезда. Переход —
        // это скачок: место сменилось рывком или сменился размер. Плавный ход той же цели
        // (прокрутка) переезд не перезапускает, иначе рамка отставала бы от неё заново.
        val step = if (before != null) aim.topLeft - before.topLeft else Offset.Zero

        if (armed && before != null && last != null) {
            val resized = abs(aim.width - before.width) > 0.5f || abs(aim.height - before.height) > 0.5f

            if (resized || (step - aimStep).getDistance() > RING_JUMP.toPx()) {
                glide = Rect(last.left - aim.left, last.top - aim.top, last.right - aim.right, last.bottom - aim.bottom)
                glideRound = round - cornerOf(aim)
                glideAt = now
                armed = false
            }
        }

        aimStep = step

        // Рамки не было (фокус пришёл в окно, её прятали) — она проявляется на месте, без переезда.
        if (last == null) shownAt = now

        aimed = aim

        var r: Rect = aim
        var own = cornerOf(aim)

        glide?.let { lag ->
            val left = springLeft((now - glideAt) / 1e9f)

            if (left < GLIDE_DONE) {
                glide = null
            } else {
                r = Rect(aim.left + lag.left * left, aim.top + lag.top * left, aim.right + lag.right * left, aim.bottom + lag.bottom * left)
                own = (own + glideRound * left).coerceIn(0f, min(r.width, r.height) / 2)
            }
        }

        drawn = r
        round = own

        val alpha = ((now - shownAt).toFloat() / FADE_NS).coerceIn(0f, 1f)
        val gap = RING_GAP.toPx()
        val stroke = RING_STROKE.toPx()

        if (density != filterFor) {
            filterFor = density
            neonFilter = BlurMaskFilter(NEON_BLUR.toPx(), BlurMaskFilter.Blur.OUTER)
        }

        // Размытие — только наружу (`OUTER`), от внешнего края контура: на элемент оно не заходит.
        drawIntoCanvas {
            val outer = r.inflate(gap + stroke)
            val radius = own + gap + stroke

            blur.style = android.graphics.Paint.Style.FILL
            blur.maskFilter = neonFilter
            blur.color = Color.White.copy(alpha = NEON_ALPHA * alpha).toArgb()
            blur.blendMode = android.graphics.BlendMode.DIFFERENCE
            it.nativeCanvas.drawRoundRect(outer.left, outer.top, outer.right, outer.bottom, radius, radius, blur)
        }

        val line = r.inflate(gap + stroke / 2)

        drawRoundRect(
            color = Color.White,
            topLeft = line.topLeft,
            size = line.size,
            cornerRadius = CornerRadius(own + gap + stroke / 2),
            alpha = alpha,
            style = Stroke(stroke),
            blendMode = BlendMode.Difference,
        )
    }
}

private object RingKey

/**
 * Рамка — не вокруг элемента в фокусе, а вокруг [rect] этого места, пока фокус внутри. Док так ведёт
 * рамку вместе со своей белой подсветкой: вкладка получает фокус сразу, а подсветка едет за лентой
 * страниц, и рамка с подсветкой ходили порознь (D-160). [rect] — в своих координатах, по размеру места.
 * [forced] — рамка здесь, хотя фокус не внутри: пульт «припаркован» в доке, см. [TvFocus.parked].
 * [corner] — скругление места, если по размеру его не угадать.
 */
internal fun Modifier.ringAnchor(forced: Boolean = false, corner: Dp? = null, rect: Density.(IntSize) -> Rect): Modifier =
    this then RingAnchorElement(forced, corner, rect)

private data class RingAnchorElement(
    val forced: Boolean,
    val corner: Dp?,
    val rect: Density.(IntSize) -> Rect,
) : ModifierNodeElement<RingAnchorNode>() {
    override fun create() = RingAnchorNode(forced, corner, rect)

    override fun update(node: RingAnchorNode) {
        node.rect = rect
        node.corner = corner

        if (node.forced != forced) {
            node.forced = forced
            node.sync()
        }
    }
}

internal class RingAnchorNode(
    var forced: Boolean,
    var corner: Dp?,
    var rect: Density.(IntSize) -> Rect,
) : Modifier.Node(), FocusEventModifierNode {
    private var focused = false

    override fun onAttach() {
        if (forced) sync()
    }

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.hasFocus
        sync()
    }

    fun sync() {
        traverseAncestors(RingKey) {
            val ring = it as FocusRingNode

            if (focused || forced) ring.anchor = this else if (ring.anchor === this) ring.anchor = null
            ring.follow()
            false
        }
    }

    /** Место в координатах рамки. */
    fun rectIn(ring: LayoutCoordinates): Rect? {
        if (!isAttached) return null

        val own = requireLayoutCoordinates()
        val local = requireDensity().rect(own.size)

        return Rect(ring.localPositionOf(own, local.topLeft), local.size)
    }
}

/**
 * Скругление элемента — рамке, когда по размеру его не угадать (низкая плашка со слабым
 * скруглением сошла бы за кнопку-пилюлю), см. [focusRing]. Ставится перед тем, что берёт фокус.
 */
internal fun Modifier.ringCorner(corner: Dp): Modifier = this then RingCornerElement(corner)

private data class RingCornerElement(val corner: Dp) : ModifierNodeElement<RingCornerNode>() {
    override fun create() = RingCornerNode(corner)

    override fun update(node: RingCornerNode) {
        node.corner = corner
    }
}

internal class RingCornerNode(var corner: Dp) : Modifier.Node(), FocusEventModifierNode {
    override fun onFocusEvent(focusState: FocusState) {
        traverseAncestors(RingKey) {
            val ring = it as FocusRingNode

            if (focusState.isFocused) ring.corner = this else if (ring.corner === this) ring.corner = null
            false
        }
    }
}

/**
 * Пока [hold], рамки нет. Панель раскрывается или уезжает в своей анимации, а фокус переходит
 * сразу: рамка вставала на узел, которого ещё не видно, или оставалась висеть на месте поля,
 * пока окно с ним уезжало (D-161). Отпустили — рамка проявляется там, где фокус.
 */
internal fun Modifier.ringHold(hold: Boolean): Modifier = this then RingHoldElement(hold)

private data class RingHoldElement(val hold: Boolean) : ModifierNodeElement<RingHoldNode>() {
    override fun create() = RingHoldNode(hold)

    override fun update(node: RingHoldNode) = node.set(hold)
}

private class RingHoldNode(private var hold: Boolean) : Modifier.Node() {
    private var held: FocusRingNode? = null

    override fun onAttach() = apply()

    override fun onDetach() = release()

    fun set(hold: Boolean) {
        this.hold = hold
        if (isAttached) apply()
    }

    private fun apply() {
        if (!hold) release()
        else if (held == null) traverseAncestors(RingKey) {
            held = (it as FocusRingNode).also { ring -> ring.hold(1) }
            false
        }
    }

    private fun release() {
        held?.hold(-1)
        held = null
    }
}

/** Фокус вошёл в группу или вышел из неё — пусть рамка сверится. */
private data object FocusNudgeElement : ModifierNodeElement<FocusNudgeNode>() {
    override fun create() = FocusNudgeNode()

    override fun update(node: FocusNudgeNode) = Unit
}

private class FocusNudgeNode : Modifier.Node(), FocusEventModifierNode, TraversableNode {
    override val traverseKey: Any get() = RoomKey

    /** Focus is inside: [LeaveLeftNode] leaves a nested room's keys to it. */
    var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.hasFocus
        traverseAncestors(RingKey) { (it as FocusRingNode).follow(); false }
    }
}

private object RoomKey

/**
 * OK held down on a remote is one press with repeats and acts once. Held on the computer's keyboard in the
 * emulator — and on some remotes passed through HDMI-CEC — it arrives as a stream of separate presses, and each
 * acted: the VPN went on and off until the key was let go. A press of OK that follows the previous release within
 * [REPEAT_GAP_MS] is such a repeat — no hand lets go and presses again that fast — and is dropped with its release.
 * Set on the root of each window: the tab layout and every sheet.
 */
internal fun Modifier.okOnce(): Modifier = this then OkOnceElement

private data object OkOnceElement : ModifierNodeElement<OkOnceNode>() {
    override fun create() = OkOnceNode()

    override fun update(node: OkOnceNode) = Unit
}

private class OkOnceNode : Modifier.Node(), KeyInputModifierNode {
    private var releasedAt = 0L
    private var dropping = false

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return false

        val native = event.nativeKeyEvent

        return when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) dropping = releasedAt > 0 && native.eventTime - releasedAt < REPEAT_GAP_MS
                dropping
            }

            KeyEventType.KeyUp -> {
                releasedAt = native.eventTime
                dropping.also { dropping = false }
            }

            else -> false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}

private const val REPEAT_GAP_MS = 40L

private data class LeaveLeftElement(val onLeft: () -> Unit) : ModifierNodeElement<LeaveLeftNode>() {
    override fun create() = LeaveLeftNode(onLeft)

    override fun update(node: LeaveLeftNode) {
        node.onLeft = onLeft
    }
}

/**
 * Left goes to the dock unless the focused element has something to its left in its own row (F-346). Compose's
 * search takes a candidate from another row when the row has none: the power button's Left landed on «Обновить»
 * below it, and so did the node row's, which starts a few pixels right of the card under it. Compose cannot be
 * asked what its search would pick, so the room reads the semantics tree, which lists the focusable elements with
 * their bounds; `RootForTest` is the public way to it. Without it Left goes as before.
 */
private class LeaveLeftNode(var onLeft: () -> Unit) :
    Modifier.Node(), KeyInputModifierNode, CompositionLocalConsumerModifierNode {
    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.DirectionLeft || event.type != KeyEventType.KeyDown) return false

        var nested = false
        traverseDescendants(RoomKey) {
            nested = (it as FocusNudgeNode).focused
            if (nested) TraverseDescendantsAction.CancelTraversal else TraverseDescendantsAction.ContinueTraversal
        }
        if (nested) return false

        val owner = (currentValueOf(LocalView) as? RootForTest)?.semanticsOwner ?: return false
        val room = requireLayoutCoordinates().boundsInRoot()
        val focusable = owner.getAllSemanticsNodes(mergingEnabled = false).filter {
            SemanticsProperties.Focused in it.config && SemanticsProperties.Disabled !in it.config
        }
        val from = focusable.firstOrNull { it.config[SemanticsProperties.Focused] }?.boundsInRoot ?: return false
        // Compose's own test for a candidate to the left, kept to the element's row and to what is on the page.
        val beside = focusable.any {
            val r = it.boundsInRoot

            !r.isEmpty && r.overlaps(room) && r.left < from.left && r.right < from.right &&
                r.top < from.bottom && r.bottom > from.top
        }

        if (beside) return false

        onLeft()
        return true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}

private const val FOLLOW_NS = 700_000_000L
/** Элемент не двигался столько — доехал, следить дальше незачем. */
private const val STILL_NS = 200_000_000L
/** Предел слежения: что-то едет непрерывно — рамка всё равно отпускает кадры. */
private const val FOLLOW_MAX_NS = 3_000_000_000L
/** Проявление рамки. */
private const val FADE_NS = 120_000_000f
/** Отставание рамки меньше этой доли пути — переезд закончен. */
private const val GLIDE_DONE = 0.004f

/**
 * Пружина без колебаний, одна на прокрутку к фокусу и на переезд рамки (жёсткость 600 — доезд
 * за пятую долю секунды: мягче штатной 1500, которая и читалась рывком).
 */
private const val TV_SPRING = 600f
private val TvSpring = spring<Float>(stiffness = TV_SPRING)

/** Какая доля пути осталась такой пружине через [seconds] после старта из покоя. */
internal fun springLeft(seconds: Float): Float {
    val wt = sqrt(TV_SPRING) * seconds.coerceAtLeast(0f)

    return (1f + wt) * exp(-wt)
}
private val RING_GAP = 3.dp
private val RING_STROKE = 3.dp
private val RING_CORNER = 20.dp
/** Место сменилось за кадр больше чем на столько сверх прежнего хода — фокус перешёл, а не едет. */
private val RING_JUMP = 24.dp
/** Ниже этого элемент считается кнопкой-пилюлей. */
private val RING_PILL = 56.dp
private val NEON_BLUR = 8.dp
private const val NEON_ALPHA = 0.45f

/**
 * На телевизоре фокус показывает рамка, и своя подложка фокуса у кнопок Material лишняя:
 * кнопка питания от неё серела и светилась не так, как остальные. Нажатие — как было.
 */
internal val TvRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0f, hoveredAlpha = 0.08f, pressedAlpha = 0.1f),
)

/**
 * Прокрутка к элементу в фокусе. На телевизоре Compose сам держит такой элемент на трети высоты
 * списка (F-193): список дёргался на каждое нажатие, фон ехал даже между карточкой и её
 * звёздочкой, а рамка «доезжала» следом (D-161). Здесь список стоит, пока элемент в средней
 * части экрана, и мягко подъезжает, только когда фокус подходит к краю, — так следующий ряд
 * виден заранее.
 */
@OptIn(ExperimentalFoundationApi::class)
internal val TvBringIntoView = object : BringIntoViewSpec {
    // ponytail: в foundation 1.12 свойство помечено устаревшим, и читает его только обычная
    // прокрутка (`verticalScroll`): ленивые списки берут штатную пружину (F-193), им — [TvLazyColumn].
    // Перестанут читать совсем — колонка главной поедет резче; тогда и ей свою прокрутку.
    @Suppress("OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = TvSpring

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        tvScrollDistance(offset, size, containerSize)
}

/** Список, который к фокусу подъезжает сам ([softScroll]): штатной прокрутке делать нечего. */
@OptIn(ExperimentalFoundationApi::class)
private val HeldBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Ленивый список под пульт. На телевизоре к элементу в фокусе он подъезжает сам — той же мягкой
 * пружиной, что и рамка ([TvSpring]): штатную анимацию ленивым спискам не сменить, она жёсткая и
 * читалась рывком (F-193). На телефоне это обычный `LazyColumn` с теми же параметрами.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    if (!LocalTv.current) {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            overscrollEffect = overscrollEffect,
            content = content,
        )

        return
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides HeldBringIntoView) {
        LazyColumn(
            modifier = modifier.then(SoftScrollElement(state)),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            overscrollEffect = overscrollEffect,
            content = content,
        )
    }
}

/**
 * Список мягко доезжает до начала: фокус ушёл на то, что стоит над списком (строка поиска).
 * Той же пружиной, что и подъезд к фокусу, — штатная `animateScrollToItem` едет своей, а после
 * неё элементы с анимацией перестановки подскакивали на следующем же переходе фокуса (F-194).
 */
internal suspend fun LazyListState.tvScrollToStart() {
    val distance = startDistance()

    if (distance != null) animateScrollBy(-distance.toFloat(), TvSpring)
    else animateScrollToItem(0)
}

/**
 * How far the list is from its start while its first item is on screen, else `null`. Read from that item's place,
 * not `firstVisibleItemIndex`: a screen header that has slid under the list's top padding is still on screen, cut,
 * but the list already counts the next item as the first visible one, and the header stayed cut.
 */
private fun LazyListState.startDistance(): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull()?.takeIf { it.index == 0 }?.let { (-it.offset).coerceAtLeast(0) }

private data class SoftScrollElement(val state: ScrollableState) : ModifierNodeElement<SoftScrollNode>() {
    override fun create() = SoftScrollNode(state)

    override fun update(node: SoftScrollNode) {
        node.state = state
    }
}

/**
 * Подъезд списка к фокусу. Список сам не прокручивается ([HeldBringIntoView]) и передаёт запрос
 * «показать элемент» выше — сюда, с местом элемента в своих координатах. Пружина та же, что у
 * рамки, и стартует из покоя — поэтому рамка у края списка стоит, а список едет под ней. Цель
 * сверяется каждый кадр: новый запрос посреди пути меняет её, а скорость остаётся — быстрые
 * нажатия не дёргают список; и начало списка, показавшись по дороге, продлевает путь до него.
 */
private class SoftScrollNode(var state: ScrollableState) : Modifier.Node(), BringIntoViewModifierNode {
    private var job: Job? = null
    // Сколько осталось проехать по последнему запросу.
    private var wanted: () -> Float = { 0f }

    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        wanted = {
            val rect = if (childCoordinates.isAttached) boundsProvider() else null

            if (rect == null) 0f
            else tvScrollDistance(rect.top, rect.height, childCoordinates.size.height.toFloat(), toStart())
        }

        if (job?.isActive != true) job = coroutineScope.launch { run() }
    }

    /** Сколько осталось до начала списка; ноль — уже там или неизвестно (первый элемент не виден). */
    private fun toStart(): Float = (state as? LazyListState)?.startDistance()?.toFloat() ?: 0f

    private suspend fun run() {
        val w = sqrt(TV_SPRING)
        // Скорость остатка, пикселей в секунду.
        var speed = 0f
        var last = 0L

        state.scroll {
            while (true) {
                // Сначала кадр, потом место: раскладка прошлого шага к этому времени уже прошла.
                val dt = withFrameNanos { now -> (if (last == 0L) 0f else (now - last) / 1e9f).also { last = now } }
                val left = wanted()

                if (abs(left) < 0.5f && abs(speed) < 30f) break

                // Шаг пружины без колебаний за время кадра: точное решение, а не приближение.
                val c = speed + w * left
                val e = exp(-w * dt)
                val step = left - (left + c * dt) * e

                speed = (speed - c * w * dt) * e

                // Край списка: дальше ехать некуда.
                if (abs(scrollBy(step) - step) > 0.5f) break
            }
        }
    }
}

/**
 * На сколько прокрутить, чтобы элемент ([offset] от начала, длиной [size]) встал в среднюю часть
 * списка длиной [containerSize]: от краёв остаётся запас [TV_SCROLL_EDGE], у крупного элемента —
 * сколько есть, поровну с обеих сторон. Ноль — элемент уже там.
 */
internal fun tvScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
    val end = offset + size

    // Элемент выше списка: закрывает его целиком — список стоит, иначе показывается ближний край.
    if (size >= containerSize) return if (offset > 0f) offset else if (end < containerSize) end - containerSize else 0f

    val edge = min(containerSize * TV_SCROLL_EDGE, (containerSize - size) / 2)

    return when {
        offset < edge -> offset - edge
        end > containerSize - edge -> end - (containerSize - edge)
        else -> 0f
    }
}

/**
 * То же у начала списка, до которого осталось [toStart]: если элемент и после этого остаётся в
 * средней части — едем до самого начала. Иначе список вставал, не доехав: шапке первой группы
 * запаса уже хватало, а заголовок экрана над ней оставался срезанным.
 */
internal fun tvScrollDistance(offset: Float, size: Float, containerSize: Float, toStart: Float): Float {
    val plain = tvScrollDistance(offset, size, containerSize)

    return if (plain <= 0f && toStart > 0f && tvScrollDistance(offset + toStart, size, containerSize) <= 0f) -toStart else plain
}

private const val TV_SCROLL_EDGE = 0.2f

/**
 * Раскладка для телевизора: слева колонка — марка сверху, док по центру, тема и настройки
 * снизу, всё от одного левого края (D-161); экраны — справа от неё. Логика та же, что у
 * [DuckerScaffold]: экранам поверх вкладок колонка уступает место и уезжает за край (D-96),
 * «плюс» подписок (D-97) — под доком, пока подписок нет — одна кнопка вместо вкладок (D-98).
 * Отступы от краёв — по правилам Android TV: телевизор может срезать края кадра.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvScaffold(
    focus: TvFocus,
    selected: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    dockShown: Boolean,
    actionShown: () -> Float,
    action: @Composable () -> Unit,
    onAction: (() -> Unit)?,
    actionLabel: String?,
    single: String?,
    onSingle: () -> Unit,
    title: @Composable RowScope.() -> Unit,
    tools: @Composable RowScope.() -> Unit,
    content: @Composable (tabs: PaddingValues, overlays: PaddingValues) -> Unit,
) {
    var dockWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Один вход на нажатие: `onExit` дока Compose зовёт по нескольку раз за один поиск (восемь в
    // эмуляторе), а повторный вход в уже активную страницу сбрасывал фокус на её первый элемент.
    var entering by remember { mutableStateOf<Job?>(null) }
    val enter: (Int) -> Unit = { page ->
        if (entering?.isActive != true) entering = scope.launch { focus.enter(page) }
    }
    // Под экраном поверх вкладок страницу не трогаем: фокус там его.
    val covered by rememberUpdatedState(!dockShown)

    val hidden by animateFloatAsState(
        targetValue = if (dockShown) 0f else 1f,
        animationSpec = if (dockShown) tween(260, easing = LinearOutSlowInEasing)
        else tween(200, easing = FastOutLinearInEasing),
        label = "dock",
    )

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoView, LocalTvFocus provides focus) {
        // Поверхностью, а не одним фоном: она же задаёт цвет текста, как Scaffold на телефоне.
        Surface(
            Modifier.fillMaxSize()
                .okOnce()
                .focusRing { if (!covered) enter(selected()) else focus.cover?.requestFocus() }
                // Пульт припаркован на вкладке дока, а фокус остался в странице (D-161): клавиши до
                // страницы не доходят. Вправо и ОК возвращают рамку на место, вверх и вниз ведут
                // по колонке уже по-настоящему — оттуда вход в страницу снова с начала.
                .onPreviewKeyEvent {
                    if (!focus.parked) return@onPreviewKeyEvent false
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true

                    when (it.key) {
                        Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> focus.parked = false

                        // Выше первой вкладки идти некуда: остаёмся припаркованными.
                        Key.DirectionUp, Key.DirectionDown -> if (it.key == Key.DirectionDown || selected() > 0) {
                            focus.parked = false
                            focus.steer()
                            focus.tabs[selected()].requestFocus()
                            focusManager.moveFocus(if (it.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down)
                        }
                    }

                    true
                },
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                val reserved = with(density) { dockWidth.toDp() }

                content(
                    PaddingValues(start = TV_EDGE + reserved + TV_GAP, top = TV_TOP, end = TV_EDGE, bottom = TV_TOP),
                    PaddingValues(horizontal = TV_EDGE, vertical = TV_TOP),
                )

                // One column, so that a large display size squeezes only the gaps: as three corners of a box
                // the title, the dock and the tools overlapped on a short screen.
                Column(
                    modifier = Modifier.fillMaxHeight()
                        .padding(start = TV_EDGE, top = TV_TOP, bottom = TV_TOP)
                        .graphicsLayer { translationX = -hidden * (size.width + TV_EDGE.toPx() + 40.dp.toPx()) }
                        .then(if (dockShown) Modifier else Modifier.clearAndSetSemantics { })
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                // Клавиша в доке отменяет вход в страницу, который ещё не случился: иначе он
                                // срабатывал поверх следующих нажатий, и фокус уезжал не туда (D-159).
                                entering?.cancel()
                                focus.steer()
                            }
                            false
                        }
                        // Из колонки — только вправо, в экран, и всегда на его начало (D-158).
                        .focusProperties {
                            onExit = {
                                if (requestedFocusDirection in Arrows) cancelFocusChange()
                                if (requestedFocusDirection == FocusDirection.Right) enter(selected())
                            }
                        }
                        .focusGroup(),
                ) {
                    // Марка не шире дока: правее уже экран.
                    Row(
                        modifier = Modifier.widthIn(max = reserved).heightIn(min = TV_TOOL),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        content = title,
                    )

                    Spacer(Modifier.weight(1f))

                    Column(
                        modifier = Modifier.onSizeChanged { dockWidth = it.width },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SideDock(focus, selected, position, onSelect, enter, single, onSingle)

                        // Место под «плюс» есть всегда, иначе док прыгал бы, когда тот появляется.
                        Box(Modifier.size(ACTION_SIZE), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.matchParentSize()
                                    .clearAndSetSemantics { }
                                    .graphicsLayer {
                                        val shown = actionShown()

                                        scaleX = shown
                                        scaleY = shown
                                        alpha = shown
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                action()
                            }

                            if (onAction != null) {
                                Box(
                                    Modifier.matchParentSize()
                                        .clip(CircleShape)
                                        .semantics { if (actionLabel != null) contentDescription = actionLabel }
                                        .clickable(role = Role.Button, onClick = onAction),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        // Вправо с последней кнопки — в экран, как с вкладки: иначе поиск находил
                        // «плюс», он правее и выше.
                        modifier = Modifier
                            .focusProperties {
                                onExit = {
                                    if (requestedFocusDirection == FocusDirection.Right) {
                                        cancelFocusChange()
                                        enter(selected())
                                    }
                                }
                            }
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        content = tools,
                    )
                }
            }
        }
    }
}

/** Кнопка под доком — тема, настройки: круг в цвет и с тенью дока, чтобы читалась с ним одним целым. */
@Composable
internal fun TvTool(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(TV_TOOL).airShadow(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Док сбоку: вкладки столбиком, подсветка едет за лентой, как на телефоне (D-76). Вкладка
 * выбирается, как только на неё встал фокус: вверх-вниз по доку листают экраны, а ОК на
 * вкладке — вход в экран, как стрелка вправо.
 */
@Composable
private fun SideDock(
    focus: TvFocus,
    selected: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    enter: (Int) -> Unit,
    single: String?,
    onSingle: () -> Unit,
) {
    val pill = MaterialTheme.colorScheme.primaryContainer
    val onPill = MaterialTheme.colorScheme.onPrimaryContainer
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    // Цвет подписи — по вкладке, к которой едет подсветка, а не по осевшей странице: иначе
    // прежняя вкладка держала чёрную подпись на тёмном, пока лента не доедет.
    val current by remember(position) { derivedStateOf { position().roundToInt() } }

    val shape = RoundedCornerShape(TV_TAB_CORNER + TV_DOCK_PAD)

    // The cards' soft shadow: the platform one with an outline stood out heavier than the cards beside it.
    Surface(
        modifier = Modifier.airShadow(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.width(IntrinsicSize.Max)
                .padding(TV_DOCK_PAD)
                // Рамка пульта — вокруг подсветки, а не вокруг вкладки: едут вместе.
                .ringAnchor(forced = focus.parked) { size ->
                    val slot = TV_TAB_HEIGHT.toPx()
                    val top = if (single != null) 0f else slot * position().coerceIn(0f, (TABS - 1).toFloat())

                    Rect(Offset(0f, top), Size(size.width.toFloat(), slot))
                }
                .drawBehind {
                    val slot = TV_TAB_HEIGHT.toPx()
                    val top = if (single != null) 0f
                    else slot * position().coerceIn(0f, (TABS - 1).toFloat())

                    drawRoundRect(
                        color = pill,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, slot),
                        cornerRadius = CornerRadius(TV_TAB_CORNER.toPx()),
                    )
                }
                .selectableGroup(),
        ) {
            if (single != null) {
                TvTab(
                    icon = Symbols.Add,
                    label = single,
                    color = onPill,
                    modifier = Modifier.focusRequester(focus.tabs[0])
                        .clip(RoundedCornerShape(TV_TAB_CORNER))
                        .clickable(role = Role.Button, onClick = onSingle),
                )
            } else {
                DockTabItems.forEachIndexed { index, (label, icon) ->
                    val active = current == index

                    TvTab(
                        icon = icon,
                        label = stringResource(label),
                        color = if (active) onPill else dim,
                        modifier = Modifier.then(FocusNudgeElement)
                            .focusRequester(focus.tabs[index])
                            .onFocusChanged {
                                if (it.isFocused && selected() != index) {
                                    if (focus.steering) onSelect(index) else enter(selected())
                                }
                            }
                            .clip(RoundedCornerShape(TV_TAB_CORNER))
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { enter(index) },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTab(icon: ImageVector, label: String, color: Color, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(TV_TAB_HEIGHT).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = color)

        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Android TV lays its screens out on 960×540 dp at 720p, 1080p and 4K alike, and ours are built for that canvas. A TV
 * that reports a lower density has a larger canvas, and the screens stood small in its middle with wide empty
 * margins: the density is raised until the canvas is 960×540. A higher one — a larger display size — is kept.
 * Off a TV [base] is returned as it is.
 */
internal fun tvCanvas(base: Context): Context {
    if (!base.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return base

    val config = base.resources.configuration
    val metrics = base.resources.displayMetrics
    val dpi = min(metrics.widthPixels * 160 / TV_CANVAS_WIDTH, metrics.heightPixels * 160 / TV_CANVAS_HEIGHT)

    if (dpi <= config.densityDpi) return base

    return base.createConfigurationContext(
        Configuration(config).apply {
            densityDpi = dpi
            screenWidthDp = metrics.widthPixels * 160 / dpi
            screenHeightDp = metrics.heightPixels * 160 / dpi
            smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
        },
    )
}

private const val TV_CANVAS_WIDTH = 960
private const val TV_CANVAS_HEIGHT = 540

/**
 * The safe area's top and bottom on a TV ([TV_TOP]), zero elsewhere. A scrolling screen puts it inside its list:
 * at rest the content stands where it did, and scrolled it runs to the screen's edge — a padding outside the list
 * cut it at an empty band.
 */
@Composable
internal fun tvEdge(): Dp = if (LocalTv.current) TV_TOP else 0.dp

/** Поля от краёв кадра: 48 по бокам и 27 сверху и снизу — безопасная зона Android TV. */
private val TV_EDGE = 48.dp
internal val TV_TOP = 27.dp
private val TV_GAP = 24.dp
private val TV_TAB_HEIGHT = 60.dp
private val TV_TAB_CORNER = 20.dp
/** Поле дока вокруг вкладок: в него встаёт рамка вокруг подсветки — зазор и контур. */
private val TV_DOCK_PAD = 10.dp
private val TV_TOOL = 52.dp
