package com.github.kr328.clash.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.derivedStateOf
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.UiComposable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
    /**
     * Keep the window and its composition between opens (D-521); the caller keeps the sheet composed. A closed sheet
     * hides its window: creating it and composing the body made each open's first frame long (F-513).
     */
    keep: Boolean = false,
    titleAction: (@Composable () -> Unit)? = null,
    /**
     * The glass style: the screen under the sheet ([SheetGlass.screen]), which the sheet then shows through itself
     * as a pane of glass, its controls as glass on it. Null (outside the app's scaffold) — a frosted sheet.
     */
    backdrop: GlassBackdrop? = LocalSheetGlass.current?.screen,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Пока подписка загружается, панель не закрывается ни свайпом, ни «назад», ни касанием
    // мимо: иначе человек не узнает, чем кончилось.
    val working by rememberUpdatedState(busy)
    val shown by rememberUpdatedState(open)
    val dismiss by rememberUpdatedState(onDismiss)
    val closed by rememberUpdatedState(onClosed)
    val ask by rememberUpdatedState(onAsk)
    val tv = LocalTv.current
    var glassVisible by remember { mutableStateOf(open) }
    val sheetShape = remember(tv) { if (tv) TvSheetShape else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) }

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
        val tuning = LocalGlassTuning.current
        val sheet = remember { Any() }
        if (tuning != null && glassVisible) {
            DisposableEffect(tuning, sheet) {
                tuning.sheets.add(sheet)
                onDispose { tuning.sheets.remove(sheet) }
            }
        }
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
                // On a TV the keyboard lies over the sheet; the sheet lifts the field itself (D-377).
                @Suppress("DEPRECATION")
                setSoftInputMode(
                    if (tv) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                )
                @Suppress("DEPRECATION")
                statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isNavigationBarContrastEnforced = false

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
        var exitVelocity by remember { mutableStateOf<Float?>(null) }
        val gestureDensity by rememberUpdatedState(LocalDensity.current.density)

        fun move(to: Float, spec: FiniteAnimationSpec<Float>, then: () -> Unit = {}) {
            motion?.cancel()
            motion = scope.launch {
                animate(offset.coerceAtMost(height), to, animationSpec = spec) { value, _ ->
                    offset = value
                }
                then()
            }
        }

        // A leaving sheet follows neither a finger nor a scroll: Compose's scroll to the focused field reaches it as
        // one, and it cancelled the slide-out, leaving the closed sheet on screen with Back unable to close it.
        fun drag(by: Float) {
            if (!shown) return

            motion?.cancel()
            exitVelocity = null
            offset = (offset + by).coerceIn(0f, height)
        }

        fun settle(velocity: Float) {
            if (!shown) return

            val down = sheetShouldClose(offset, height, velocity / gestureDensity)
            exitVelocity = if (down) velocity else null
            if (!(down && close())) {
                exitVelocity = null
                move(0f, sheetReleaseSpec(-offset, velocity, SETTLE_MS))
            }
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
            if (tv) return 0f

            val now = keyboard(ime)
            val full = maxOf(keyboard(imeFrom), keyboard(imeTo))
            val gap = room - natural

            if (full <= gap || now >= full) return 0f

            return (minOf(now, gap) - gap * now / full).coerceIn(0f, now)
        }

        // On a TV the keyboard lies over the sheet, and the sheet does not grow by its height: Gboard there jumps in
        // without animating its inset, and the sheet's top jumped to the screen's top with it (F-362). The sheet stays,
        // and only when the field being typed into would end under the keyboard it slides up by that much (D-377).
        val typing = remember { TvTyping() }
        val keyboardTop = if (tv) tvKeyboardTop(typing.active) else null
        val lift = remember { Animatable(0f) }

        if (tv) LaunchedEffect(keyboardTop, typing.active) {
            val field = typing.field?.takeIf { it.isAttached }
            val target = if (keyboardTop == null || field == null) 0f else {
                val bottom = field.boundsInWindow().bottom + lift.value

                (bottom + with(density) { LIFT_GAP.toPx() } - keyboardTop).coerceIn(0f, (room - height).coerceAtLeast(0f))
            }

            lift.animateTo(target, tween(LIFT_MS, easing = FastOutSlowInEasing))
        }

        val focus = LocalFocusManager.current

        // The glass style: the screen under the sheet blurs as it comes out.
        val glass = !tv && LocalGlassStyle.current.enabled
        val rim = glassRim(MaterialTheme.colorScheme.background)
        // Over another glass sheet, frosted: its glass would show the screen under both, a hole through the lower sheet.
        // Settled at each open, so it does not change while shown.
        var nested by remember { mutableStateOf(false) }
        val pane = if (glass && !nested) backdrop else null
        val sheetGlass = if (glass) LocalSheetGlass.current else null
        val glassOwner = remember { Any() }

        // A hidden window draws nothing and takes no touch, key, focus or accessibility: the fullscreen
        // transparent window must never stay visible over the app once the sheet has gone.
        fun finish() {
            glassVisible = false
            sheetGlass?.want(glassOwner, false)
            if (keep) {
                focus.clearFocus(force = true)
                window?.decorView?.visibility = View.GONE
            }
            closed()
        }

        // Выезд и отъезд — от того места, где панель сейчас: закрытие жестом продолжается
        // с того места, где палец её отпустил.
        LaunchedEffect(open, height > 0f) {
            when {
                // A kept window hidden before its first layout is never measured again: show it so it can be.
                height == 0f -> if (!open) finish() else if (keep) window?.decorView?.visibility = View.VISIBLE
                open -> {
                    glassVisible = true
                    if (keep) window?.decorView?.visibility = View.VISIBLE
                    exitVelocity = null
                    nested = sheetGlass?.othersOpen(glassOwner) == true
                    if (glass && backdrop != null && !nested && sheetGlass != null) {
                        sheetGlass.want(glassOwner, true)
                        // Drawn live for each pane, the screen cost every frame of the slide; a few frames' wait at most.
                        withTimeoutOrNull(STILL_WAIT_MS) { snapshotFlow { sheetGlass.ready }.first { it } }
                    }
                    move(0f, tween(ENTER_MS, easing = FastOutSlowInEasing))
                }
                else -> {
                    // A leaving sheet no longer counts as one under a new sheet (an editor giving way to its addresses).
                    sheetGlass?.want(glassOwner, false)
                    val spec = exitVelocity?.let { sheetReleaseSpec(height - offset.coerceAtMost(height), it, EXIT_MS) }
                        ?: tween(EXIT_MS, easing = FastOutLinearInEasing)
                    exitVelocity = null
                    move(height, spec) { finish() }
                }
            }
        }

        // Пульт (этап 6): пальца нет, а к полю в фокусе Compose прокручивает тем же путём, что и
        // жест (F-190). Панель принимала это за свайп, бросала выезд и оставалась за нижним краем.
        val keys by rememberUpdatedState(tvKeys())
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

        if (glass && sheetGlass != null) {
            LaunchedEffect(sheetGlass, glassOwner) {
                snapshotFlow {
                    if (glassVisible && height > 0f) (1f - offset / height).coerceIn(0f, 1f) else 0f
                }.collect { sheetGlass.update(glassOwner, it) }
            }
            DisposableEffect(sheetGlass, glassOwner) { onDispose { sheetGlass.remove(glassOwner) } }
        }

        // Затемнение — отдельным слоем под панелью: касание панели до него не доходит.
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    val shown = if (height > 0f) (1f - offset / height).coerceIn(0f, 1f) else 0f
                    // Over a blurred screen a light dim is enough.
                    drawRect(Color.Black, alpha = (if (glass) GLASS_SCRIM else SCRIM) * shown)
                }
                .pointerInput(Unit) { detectTapGestures { close() } },
        )

        // A glass sheet over the screen is a pane of the pane glass, and so is everything on it (F-552).
        GlassPanes(on = pane != null) {
            Box(
                // Панель — своё окно, и рамка выделения пультом (этап 6) у неё своя.
                Modifier.fillMaxSize()
                    .then(if (tv) Modifier.okOnce().focusRing().ringHold(!open || !settled) else Modifier)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .then(if (tv) Modifier.padding(top = TV_TOP) else Modifier)
                    .onSizeChanged { room = it.height.toFloat() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()
                        .then(
                            if (heightFraction != null) Modifier.layout { measurable, constraints ->
                                val fullWindow = constraints.maxHeight + status.getTop(this)
                                val target = (fullWindow * heightFraction + if (tv) 0f else keyboard(ime)).roundToInt()
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
                        .offset { IntOffset(0, (offset.coerceAtMost(height) + lag() - lift.value).roundToInt()) }
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
                        // On glass the shadow showed through the pane as a dark inner frame; the light rim marks the edge instead.
                        .then(if (glass) Modifier else Modifier.shadow(8.dp, sheetShape, clip = false))
                        .clip(sheetShape)
                        .then(
                            // A pane of glass over the screen as blurred as the screen around it, its rim catching light as
                            // Home's cards do (the owner, 17.11).
                            if (pane != null) Modifier.liquidGlass(
                                pane,
                                base = MaterialTheme.colorScheme.background,
                                tint = MaterialTheme.colorScheme.surface.copy(alpha = GlassMaterial.Surface.tint),
                                shape = sheetShape,
                                blurBoost = SHEET_BLUR,
                                // The light catches the top and fades down the sides: cut off at the screen's bottom edge
                                // it read as a stray stripe (the owner, 18.11).
                                rimFade = SHEET_RIM_FADE,
                            )
                            else if (glass) Modifier
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), sheetShape)
                                .drawWithContent {
                                    drawContent()
                                    // Light catches the glass's top edge and fades down its sides, as on the glass sheet.
                                    drawRoundRect(
                                        rimFadeBrush(rim, SHEET_RIM_FADE.toPx(), 28.dp.toPx()),
                                        size = Size(size.width, size.height + 28.dp.toPx()),
                                        cornerRadius = CornerRadius(28.dp.toPx()),
                                        style = Stroke(1.dp.toPx()),
                                    )
                                }
                            else Modifier.background(MaterialTheme.colorScheme.background, sheetShape),
                        ),
                ) {
                    // Ручка — знак, что панель тянется. Пульту тянуть нечем: на телевизоре на её месте поле.
                    if (tv) {
                        Spacer(Modifier.height(24.dp))
                    } else {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                                .padding(vertical = 22.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                        )
                    }

                    // Lookahead hands the height spring below the final height at once, so a block with its
                    // own size animation — the slider under “Свой” (D-311) — and the panel move together on
                    // the same spring. Chasing such a block frame by frame, the panel lagged behind it, and
                    // the rows under it jerked up and back.
                    SheetBody(tv) {
                        Column(
                            // Прокрутка — на случай низкого экрана с открытой клавиатурой.
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (tv) Modifier.focusProperties { if (start != null) onEnter = { start.requestFocus() } }.focusGroup()
                                    else Modifier,
                                )
                                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                .navigationBarsPadding()
                                .then(if (tv) Modifier else Modifier.imePadding())
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
                                // On a TV the sheet stands on the bottom edge as on the phone (D-380), and the safe area a TV
                                // may cut from the frame is kept inside the scroll, as the phone keeps the navigation bar's:
                                // scrolled, the rows run to the edge, and at the end the last one stands above the area.
                                // Outside the scroll it cut the rows at an empty band.
                                .padding(
                                    start = 20.dp, end = 20.dp,
                                    bottom = (if (compact) 12.dp else 20.dp) + if (tv) TV_TOP else 0.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
                        ) {
                            // Цвет текста — под фон панели: окно диалога берёт чёрный по умолчанию, и в
                            // тёмной теме заголовок и строки-переходы пропадали на фоне.
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                                LocalTvTyping provides typing,
                                LocalGlassExtraBlur provides if (pane != null) SHEET_BLUR else 0.dp,
                                LocalFrosted provides (glass && pane == null),
                            ) {
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

                                // A sheet opened from a tab would hand its cards the tab's backdrop, which lies under the
                                // sheet: inside it everything is Air, unless the sheet is itself glass over the screen, whose
                                // controls are then glass over the same screen.
                                CompositionLocalProvider(LocalGlassBackdrop provides pane) { content() }
                            }
                        }
                    }
                }
            }
        }
        if (tuning != null && glassVisible && tuning.sheets.lastOrNull() === sheet) {
            GlassTuningPanel(tuning)
        }
    }
}

private const val ENTER_MS = 280
private const val STILL_WAIT_MS = 120L
private const val EXIT_MS = 220
private const val LIFT_MS = 240

/**
 * The sheet's body; on a TV it scrolls to the focus by the app's rule (D-161). A sheet stands at the window's root,
 * outside the TV layout that provides that rule, and got Compose's own for a TV: the focus pinned at 30 % of the body —
 * a fraction of a pixel the scroll never reached, so the rows stepped a pixel back and forth for good (F-367).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetBody(tv: Boolean, content: @Composable @UiComposable LookaheadScope.() -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides if (tv) TvBringIntoView else LocalBringIntoViewSpec.current) {
        LookaheadScope(content)
    }
}

/**
 * A sheet on a TV: rounded at the top, its outline running on [LIFT_ROOM] below its bottom edge. The keyboard lifts the
 * sheet off that edge (D-377), and a hole opened under it; now its background and shadow reach the screen's edge.
 */
private val TvSheetShape = object : Shape {
    private val top = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        top.createOutline(size.copy(height = size.height + with(density) { LIFT_ROOM.toPx() }), layoutDirection, density)
}

/** No lift goes higher than a TV's canvas, 540 dp high (`tvCanvas`). */
private val LIFT_ROOM = 540.dp

/** Between the field being typed into and a TV keyboard's top. */
private val LIFT_GAP = 16.dp
private const val SETTLE_MS = 200
private const val SCRIM = 0.32f
private const val GLASS_SCRIM = 0.12f

/**
 * Вопрос перед тем, как бросить несохранённое: правку конфига (D-124), новое правило (D-135)
 * или черновик правки (D-228). Красная кнопка — [leave], [stay] («Остаться») — текстом.
 * [danger] off — leaving loses nothing, and the button is the ordinary black one.
 */
@Composable
internal fun LeaveSheet(
    title: String,
    hint: String,
    leave: String,
    onLeave: () -> Unit,
    onStay: () -> Unit,
    stay: String = stringResource(R.string.config_stay),
    danger: Boolean = true,
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
            danger = danger,
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
    /** On a TV, where «Готово» takes the frame: the sheet's next step (D-377). */
    next: FocusRequester? = null,
    /** A link, a domain, a search in a config: left to right in every language ([machine]). */
    machine: Boolean = false,
    /** A name: in its own direction, as a [named] name in a list. */
    named: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme

    // Пульт (этап 6): ввод по ОК, как у поиска, — иначе панель открывалась сразу с клавиатурой.
    // Скругление — рамке: по высоте поле сошло бы за кнопку-пилюлю.
    val (row, input) = keyField(corner = 20.dp)
    // On a TV the link fields take no new lines either: Gboard's «Готово» put one into them (F-362).
    val done = tvDone(next)
    val flat = airEdge()
    val edge = if (error) BorderStroke(1.dp, scheme.error) else flat
    val pane = LocalGlassBackdrop.current?.takeIf { flat == null }

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (pane != null) Modifier.glass(pane, GlassMaterial.Control, shape).clip(shape)
                else Modifier.then(if (flat == null) Modifier.cardShadow(shape) else Modifier).clip(shape).background(scheme.surface),
            )
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
            keyboardOptions = if (done != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
            keyboardActions = done ?: KeyboardActions.Default,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)
                .let { if (machine) it.machine() else if (named) it.named() else it },
            cursorBrush = SolidColor(scheme.onSurface),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        // Smaller rather than cut, as a button's label: «sberbank.ru или» under the largest font.
                        FitLabel(placeholder, MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }

                    field()
                }
            },
        )

        if (value.isNotEmpty() && enabled) {
            // On the pane glass a small glass circle, as far from the field's end as from its top.
            PaneIconButton(
                onClick = { onValue("") },
                modifier = if (glassPanesOn()) Modifier.padding(end = 6.dp) else Modifier,
                glassSize = GLASS_CLEAR_BUTTON,
            ) {
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
    // On a glass sheet a plain button is glass on it, as its other controls; a filling one keeps its fill. A red one is
    // glass too, with a red cast and a red label, on a frosted sheet as well: a solid red fill stood out of the glass
    // (the owner, 18.11).
    val backdrop = LocalGlassBackdrop.current?.takeIf { progress == null }
    val redGlass = danger && progress == null && (backdrop != null || LocalFrosted.current)
    val pane = backdrop?.takeIf { !danger }
    val container = if (danger) scheme.error else scheme.primaryContainer
    val content = when {
        redGlass -> scheme.error
        danger -> scheme.onError
        pane != null -> scheme.onSurface
        else -> scheme.onPrimaryContainer
    }
    val red = scheme.error.copy(alpha = RED_GLASS)
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
    // The pane glass: a glass button swells under a finger as Home's do, without a ripple.
    val press = if (pane != null || (redGlass && backdrop != null)) rememberGlassPress() else null

    Box(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            // Нечего отправлять — кнопка приглушена целиком, вместе с фоном.
            // Before the shadow, which would otherwise show through the faded button.
            .graphicsLayer {
                alpha = if (enabled || busy) 1f else 0.4f
                outsets = DuckerShadowOutsets
            }
            .glassPress(press?.takeIf { enabled && !busy }, own = true)
            .then(if (edge == null && pane == null && !redGlass) Modifier.cardShadow(CircleShape) else Modifier)
            .clip(CircleShape)
            .then(
                when {
                    pane != null -> Modifier.glass(pane, GlassMaterial.Control, CircleShape, press = press)
                    redGlass && backdrop != null -> Modifier.glass(backdrop, GlassMaterial.Control, CircleShape, color = red, press = press)
                    redGlass -> Modifier.background(red.copy(alpha = red.alpha * GlassMaterial.Control.tint))
                        .border(1.dp, glassRim(scheme.background), CircleShape)
                    else -> Modifier.background(back)
                },
            )
            .then(
                if (filling) {
                    (edge?.let { Modifier.border(it, CircleShape) } ?: Modifier)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(filled, 0f..1f) }
                } else Modifier
            )
            .clickable(
                interactionSource = null,
                indication = if (press != null) null else LocalIndication.current,
                enabled = enabled && !busy, role = Role.Button, onClick = onClick,
            ),
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

/** How red a red button's glass is, before the controls' tint. */
private const val RED_GLASS = 0.5f
