package com.github.kr328.clash.ui.components

import androidx.compose.runtime.derivedStateOf
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R

/**
 * Панель снизу (D-99, D-100, D-109): общий каркас, поле и кнопка — у добавления и правки
 * подписки и у просьбы о работе в фоне, чтобы все панели выглядели и вели себя одинаково.
 *
 * [open] — показывать ли панель; когда он становится `false`, панель уезжает вниз
 * и только потом зовёт [onClosed]: сразу убрать её из композиции — значит оборвать
 * на полпути и анимацию закрытия.
 *
 * Своя, а не `ModalBottomSheet` Material (D-127): у того затемнение фона не шло за пальцем, а
 * гасло и зажигалось целиком, стоило сдвинуть панель на 56dp, — у этого порога всё мигало, и
 * отпущенная за ним панель захлопывалась. Здесь панель едет за пальцем один в один,
 * затемнение тает вместе с ней; закрывается, если утянута больше чем на треть или смахнута
 * вниз, иначе встаёт на место без пружины; в тупике — ничего, без растяжения.
 *
 * [onAsk] — закрыть значит бросить введённое: «назад», свайп и касание мимо зовут его, а
 * панель остаётся на месте (D-135).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DuckerSheet(
    open: Boolean,
    title: String,
    busy: Boolean,
    hint: String? = null,
    onAsk: (() -> Unit)? = null,
    /** Куда пульт встаёт при открытии, если не на первое: у необратимого действия — на отказ. */
    start: FocusRequester? = null,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    /** A lazy body manages its own scrolling while the sheet's footer stays visible. */
    scrollable: Boolean = true,
    /** Leave room for a lazy body and a fixed footer in a short window. */
    compact: Boolean = false,
    /** A fraction of the full window; keyboard insets expand it only as far as space allows. */
    heightFraction: Float? = null,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Пока подписка загружается, панель не закрывается ни свайпом, ни «назад», ни касанием
    // мимо: иначе человек не узнает, чем кончилось.
    val working by rememberUpdatedState(busy)
    val dismiss by rememberUpdatedState(onDismiss)
    val closed by rememberUpdatedState(onClosed)
    val ask by rememberUpdatedState(onAsk)
    val sheetShape = remember { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) }

    /** Жест закрытия: занята — нельзя, есть что бросить — спросить, иначе закрыть. Закрылась ли. */
    fun close(): Boolean {
        if (working) return false

        ask?.let {
            it()

            return false
        }

        dismiss()

        return true
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Окно — во весь экран и без своего: системное затемнение включается разом, а
        // выезд и затемнение здесь свои.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Полосы системы — прозрачные, значки — как у приложения: окно диалога заливало их
        // своими цветами, сверху чёрным, снизу белым.
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f

        SideEffect {
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                setWindowAnimations(0)
                @Suppress("DEPRECATION")
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                @Suppress("DEPRECATION")
                statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                navigationBarColor = android.graphics.Color.TRANSPARENT
                isNavigationBarContrastEnforced = false

                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }

        val scope = rememberCoroutineScope()
        // Высота панели и её сдвиг вниз от раскрытого положения, пиксели. Пока высота не
        // измерена, панель стоит за нижним краем.
        var height by remember { mutableFloatStateOf(0f) }
        var offset by remember { mutableFloatStateOf(Float.MAX_VALUE) }
        var motion by remember { mutableStateOf<Job?>(null) }

        fun move(to: Float, duration: Int, easing: Easing, then: () -> Unit = {}) {
            motion?.cancel()
            motion = scope.launch {
                animate(offset.coerceAtMost(height), to, animationSpec = tween(duration, easing = easing)) { value, _ ->
                    offset = value
                }
                then()
            }
        }

        fun drag(by: Float) {
            motion?.cancel()
            offset = (offset + by).coerceIn(0f, height)
        }

        fun settle(velocity: Float) {
            val down = velocity > FLING || (velocity > -FLING && offset > height * CLOSE_PART)

            if (!(down && close())) move(0f, SETTLE_MS, LinearOutSlowInEasing)
        }

        val density = LocalDensity.current
        val ime = WindowInsets.ime
        val imeFrom = WindowInsets.imeAnimationSource
        val imeTo = WindowInsets.imeAnimationTarget
        val bars = WindowInsets.navigationBars
        val status = WindowInsets.statusBars
        // Место под панель и её высота без клавиатуры, пиксели.
        var room by remember { mutableFloatStateOf(0f) }
        var natural by remember { mutableFloatStateOf(0f) }

        fun Density.keyboard(insets: WindowInsets) =
            (insets.getBottom(this) - bars.getBottom(this)).coerceAtLeast(0).toFloat()

        /**
         * Насколько панель отстаёт от клавиатуры. Панель выше места над клавиатурой упиралась в
         * верх за первые кадры выезда, на самой скорости, и вставала как вкопанная (D-135). Теперь
         * подъём растянут на весь выезд — та же кривая, что у клавиатуры, в меньшем размахе, — и
         * к концу выезда отставания нет: панель там же, где стояла бы.
         */
        fun Density.lag(): Float {
            val now = keyboard(ime)
            val full = maxOf(keyboard(imeFrom), keyboard(imeTo))
            val gap = room - natural

            if (full <= gap || now >= full) return 0f

            return (minOf(now, gap) - gap * now / full).coerceIn(0f, now)
        }

        // Выезд и отъезд — от того места, где панель сейчас: закрытие жестом продолжается
        // с того места, где палец её отпустил.
        LaunchedEffect(open, height > 0f) {
            when {
                height == 0f -> if (!open) closed()
                open -> move(0f, ENTER_MS, FastOutSlowInEasing)
                else -> move(height, EXIT_MS, FastOutLinearInEasing) { closed() }
            }
        }

        // Пульт (этап 6): пальца нет, а к полю в фокусе Compose прокручивает тем же путём, что и
        // жест (F-190). Панель принимала это за свайп, бросала выезд и оставалась за нижним краем.
        val keys by rememberUpdatedState(tvKeys())
        val tv = LocalTv.current
        // Панель встала на место: пока она выезжает или уезжает, рамки пульта нет — иначе рамка
        // оставалась висеть на месте поля, а панель с ним уже уехала (D-161).
        val settled by remember { derivedStateOf { offset <= 0f } }

        // Содержимое листается само; упёрлось вверху — дальше едет панель, сдвинута —
        // сначала встаёт на место. Остаток жеста съедается: в тупике ничего не тянется.
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (keys || source != NestedScrollSource.UserInput || available.y >= 0f || offset <= 0f) return Offset.Zero

                    val used = maxOf(available.y, -offset)
                    drag(used)

                    return Offset(0f, used)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (keys || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y > 0f) drag(available.y)

                    return available
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (keys || offset <= 0f) return Velocity.Zero

                    settle(available.y)

                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
            }
        }

        // Затемнение — отдельным слоем под панелью: касание панели до него не доходит.
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    val shown = if (height > 0f) (1f - offset / height).coerceIn(0f, 1f) else 0f
                    drawRect(Color.Black, alpha = SCRIM * shown)
                }
                .pointerInput(Unit) { detectTapGestures { close() } },
        )

        Box(
            // Панель — своё окно, и рамка выделения пультом (этап 6) у неё своя.
            Modifier.fillMaxSize()
                .then(if (tv) Modifier.focusRing().ringHold(!open || !settled) else Modifier)
                .windowInsetsPadding(WindowInsets.statusBars)
                .onSizeChanged { room = it.height.toFloat() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()
                    .then(
                        if (heightFraction != null) Modifier.layout { measurable, constraints ->
                            val fullWindow = constraints.maxHeight + status.getTop(this)
                            val target = (fullWindow * heightFraction + keyboard(ime)).roundToInt()
                                .coerceIn(0, constraints.maxHeight)
                            val placeable = measurable.measure(constraints.copy(minHeight = target, maxHeight = target))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        } else Modifier,
                    )
                    .onSizeChanged {
                        height = it.height.toFloat()

                        // Пока панель не упёрлась в верх, её высота — содержимое плюс клавиатура.
                        val now = with(density) { keyboard(ime) }
                        if (now == 0f || height < room) natural = height - now
                    }
                    .offset { IntOffset(0, (offset.coerceAtMost(height) + lag()).roundToInt()) }
                    // Уезжающая панель не нажимается ни пальцем, ни пультом (аудит 9в.11): за время
                    // отъезда кнопка успевала сработать второй раз.
                    .pointerInput(open) {
                        if (!open) awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                    .onPreviewKeyEvent { !open }
                    .nestedScroll(connection)
                    .draggable(
                        state = rememberDraggableState { drag(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { settle(it) },
                    )
                    // Its height animates: a platform outline shadow avoids rebuilding
                    // a full-sheet blur mask for each new height.
                    .shadow(8.dp, sheetShape, clip = false)
                    .clip(sheetShape)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // Ручка — знак, что панель тянется.
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(vertical = 22.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )

                // Lookahead hands the height spring below the final height at once, so a block with its
                // own size animation — the slider under “Свой” (D-311) — and the panel move together on
                // the same spring. Chasing such a block frame by frame, the panel lagged behind it, and
                // the rows under it jerked up and back.
                LookaheadScope {
                    Column(
                        // Прокрутка — на случай низкого экрана с открытой клавиатурой.
                        modifier = Modifier.fillMaxWidth()
                            .then(
                                if (tv) Modifier.focusProperties { if (start != null) onEnter = { start.requestFocus() } }.focusGroup()
                                else Modifier,
                            )
                            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .navigationBarsPadding()
                            .imePadding()
                            // Высота меняется плавно: подсказка при загрузке, строка ошибки, лишняя
                            // строка в поле — без этого панель прыгала вверх рывком (D-101). Стоит
                            // внутри отступа клавиатуры: за клавиатурой панель и так едет кадр в кадр,
                            // пружина там только отставала бы.
                            // Held at the bottom while the height rides, like the sheet itself: the rows below
                            // a change stay still, and only the sheet's top moves. Held at the top, the save
                            // button hopped whenever the spring and a block's own animation drifted apart (D-314).
                            .then(
                                // A fixed-height body already follows the IME insets. A second size
                                // spring makes its rows chase the sheet when the keyboard closes.
                                if (heightFraction == null) Modifier.animateContentSize(
                                    spring(stiffness = Spring.StiffnessMediumLow), alignment = Alignment.BottomStart,
                                ) else Modifier,
                            )
                            .padding(start = 20.dp, end = 20.dp, bottom = if (compact) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
                    ) {
                        // Цвет текста — под фон панели: окно диалога берёт чёрный по умолчанию, и в
                        // тёмной теме заголовок и строки-переходы пропадали на фоне.
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (titleAction == null) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Box(
                                            Modifier.height(with(density) { MaterialTheme.typography.headlineSmall.lineHeight.toDp() }),
                                            contentAlignment = Alignment.Center,
                                        ) { titleAction() }
                                    }
                                }

                                if (hint != null) {
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            content()
                        }
                    }
                }
            }
        }
    }
}

private const val ENTER_MS = 280
private const val EXIT_MS = 220
private const val SETTLE_MS = 200
private const val SCRIM = 0.32f

/** Утянута больше чем на треть — закрывается. */
private const val CLOSE_PART = 0.33f

/** Смахнули быстрее этого, пикселей в секунду, — закрывается, как бы мало ни утянули. */
private const val FLING = 1500f

/**
 * Вопрос перед тем, как бросить несохранённое: правку конфига (D-124), новое правило (D-135)
 * или черновик правки (D-228). Красная кнопка — [leave], [stay] («Остаться») — текстом.
 */
@Composable
internal fun LeaveSheet(
    title: String,
    hint: String,
    leave: String,
    onLeave: () -> Unit,
    onStay: () -> Unit,
    stay: String = stringResource(R.string.config_stay),
) {
    var open by remember { mutableStateOf(true) }
    var leaving by remember { mutableStateOf(false) }
    val safe = remember { FocusRequester() }

    DuckerSheet(
        open = open,
        title = title,
        busy = false,
        start = safe,
        onDismiss = { open = false },
        onClosed = { if (leaving) onLeave() else onStay() },
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetButton(
            text = leave,
            busyText = "",
            busy = false,
            enabled = true,
            onClick = {
                leaving = true
                open = false
            },
            danger = true,
        )

        TextButton(onClick = { open = false }, modifier = Modifier.fillMaxWidth().focusRequester(safe)) {
            Text(stay)
        }
    }
}

/** Поле — как поиск узлов: белое, скруглённое, вместо поля с рамкой Material (D-99). */
@Composable
internal fun SheetField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    error: Boolean = false,
    maxLines: Int = 1,
    readOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme

    // Пульт (этап 6): ввод по ОК, как у поиска, — иначе панель открывалась сразу с клавиатурой.
    // Скругление — рамке: по высоте поле сошло бы за кнопку-пилюлю.
    val (row, input) = keyField(corner = 20.dp)
    val flat = airEdge()
    val edge = if (error) BorderStroke(1.dp, scheme.error) else flat

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (flat == null) Modifier.dropShadow(shape, DuckerCardShadow) else Modifier)
            .clip(shape)
            .background(scheme.surface)
            .then(edge?.let { Modifier.border(it, shape) } ?: Modifier)
            .then(row)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Подсказка — внутри поля, как у поиска узлов: TalkBack читает её названием поля.
        BasicTextField(
            value = value,
            onValueChange = onValue,
            enabled = enabled,
            readOnly = readOnly,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).then(input).padding(vertical = 14.dp),
            singleLine = maxLines == 1,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.onSurface),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    field()
                }
            },
        )

        if (value.isNotEmpty() && enabled) {
            IconButton(onClick = { onValue("") }) {
                Icon(Symbols.Close, contentDescription = stringResource(R.string.clear), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Главная кнопка панели — чёрная пилюля, как кнопка на панели вкладок. Пока идёт загрузка,
 * кнопка не сереет, а показывает кольцо и [busyText]: провайдер может отдавать конфиг
 * и правила десятки секунд, и серое окно выглядело зависшим.
 */
@Composable
internal fun SheetButton(
    text: String,
    busyText: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /** Необратимое действие — кнопка красная (удаление подписки, D-118). */
    danger: Boolean = false,
    /**
     * Доля сделанного, 0..1, когда она известна (загрузка подписки, D-212): кнопка светлеет, как
     * поле, и заливается своим цветом от начала к концу — вместо кольца. `null` — кольцо.
     */
    progress: Float? = null,
    /** Before the label, while not busy: «＋» of «Добавить правило». */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (danger) scheme.error else scheme.primaryContainer
    val content = if (danger) scheme.onError else scheme.onPrimaryContainer
    val filling = busy && progress != null

    // Доля приходит рывками — по файлу, а первая, под конфиг, сразу большим куском. Мягкая
    // пружина без отскока тянет заливку за ней ровно; последний шаг, до полной, — быстро.
    val filled by animateFloatAsState(
        targetValue = if (filling) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = if (progress != null && progress >= 1f) tween(FILL_UP_MS) else spring(stiffness = FILL_STIFFNESS),
        label = "sheetButtonFill",
    )
    val back by animateColorAsState(if (filling) scheme.surface else container, tween(FILL_FADE_MS), label = "sheetButtonBack")
    val front by animateColorAsState(if (filling) container else content, tween(FILL_FADE_MS), label = "sheetButtonFront")
    val edge = airEdge(if (filling) scheme.surface else container)

    Box(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            // Нечего отправлять — кнопка приглушена целиком, вместе с фоном.
            // Before the shadow, which would otherwise show through the faded button.
            .graphicsLayer {
                alpha = if (enabled || busy) 1f else 0.4f
                outsets = DuckerShadowOutsets
            }
            .then(if (edge == null) Modifier.dropShadow(CircleShape, DuckerCardShadow) else Modifier)
            .clip(CircleShape)
            .background(back)
            .then(
                if (filling) {
                    (edge?.let { Modifier.border(it, CircleShape) } ?: Modifier)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(filled, 0f..1f) }
                } else Modifier
            )
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SheetButtonLabel(text, busyText, busy, ring = !filling, color = front, icon = icon)

        // Залитая часть — та же надпись другим цветом, обрезанная по доле: буквы на границе
        // заливки двухцветные и читаются по обе её стороны.
        if (filling) {
            Box(
                modifier = Modifier.matchParentSize().drawWithContent {
                    val edge = size.width * filled

                    clipRect(
                        left = if (layoutDirection == LayoutDirection.Rtl) size.width - edge else 0f,
                        right = if (layoutDirection == LayoutDirection.Rtl) size.width else edge,
                    ) {
                        drawRect(container)
                        this@drawWithContent.drawContent()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                SheetButtonLabel(text, busyText, busy, ring = false, color = content)
            }
        }
    }
}

@Composable
private fun SheetButtonLabel(text: String, busyText: String, busy: Boolean, ring: Boolean, color: Color, icon: ImageVector? = null) {
    // Надпись сменяется растворением, а не подменой: кнопка — то, куда человек
    // смотрит в момент нажатия.
    AnimatedContent(
        targetState = busy,
        modifier = Modifier.padding(horizontal = 20.dp),
        // Без обрезки по ширине: иначе новая надпись проявляется по буквам, как печатается.
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform(clip = false))
        },
        label = "sheetButton",
    ) { working ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (working && ring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp,
                )

                Spacer(Modifier.width(12.dp))
            }

            if (!working && icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }

            Text(
                text = if (working) busyText else text,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Мягкость пружины заливки: шаг в пятую часть кнопки она проходит секунды за две. */
private const val FILL_STIFFNESS = 12f

/** Доливка до конца, когда загрузка удалась. */
private const val FILL_UP_MS = 250

/** Смена цветов кнопки при входе в заливку и выходе из неё. */
private const val FILL_FADE_MS = 200
