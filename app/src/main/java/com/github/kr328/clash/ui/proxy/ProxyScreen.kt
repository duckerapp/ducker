package com.github.kr328.clash.ui.proxy

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import com.github.kr328.clash.ui.components.chosenGlassTint
import com.github.kr328.clash.ui.components.glassPress
import com.github.kr328.clash.ui.components.liquidGlass
import com.github.kr328.clash.ui.components.rememberGlassPress
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.isolated
import com.github.kr328.clash.ui.theme.failureOn
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.GlassPanes
import com.github.kr328.clash.ui.components.PaneIconButton
import com.github.kr328.clash.ui.components.glassPanesOn
import com.github.kr328.clash.ui.components.paneIndication
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.tvScrollToStart
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.tvKeys
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.components.ownLeft
import com.github.kr328.clash.ui.components.ringCorner
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import com.github.kr328.clash.ui.components.tabEntry
import com.github.kr328.clash.ui.components.keepFocus
import com.github.kr328.clash.ui.components.logoModel
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import androidx.compose.animation.core.animate
import kotlin.math.floor
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.traversalIndex
import com.github.kr328.clash.ui.components.overscrollFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.R
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.PullToRefresh
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.FloatingSearch
import com.github.kr328.clash.ui.components.glassSourceOver
import com.github.kr328.clash.ui.components.rememberGlassBackdrop
import com.github.kr328.clash.ui.components.ProxySortToggle
import com.github.kr328.clash.ui.components.ProxyCheckButton
import com.github.kr328.clash.ui.components.SearchField
import com.github.kr328.clash.ui.components.Skeleton
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.DuckerCard as Card
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Вкладка «Прокси» (этап 4): режим работы, группы, выбор узла и проверка задержки.
 *
 * Список один, а не вкладка на группу: групп у провайдера обычно три-пять, и прокрутка
 * читается быстрее, чем переключение вкладок. Узлов при этом может быть много, поэтому
 * список ленивый.
 */
@Composable
fun ProxyScreen(
    state: ProxyState,
    onSelect: (group: String, name: String) -> Unit,
    onFavorite: (name: String) -> Unit,
    onHealthCheck: (group: String) -> Unit,
    onMode: (TunnelState.Mode) -> Unit,
    onSort: (ProxySort) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    /**
     * Группы, у которых раскрытие отличается от умолчания. Список, а не карта состояний:
     * так он переживает и уничтожение страницы, и смерть процесса, а умолчание
     * («раскрыта только первая») остаётся одним правилом ниже.
     */
    flipped: MutableList<String>,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** Идёт то, что запустило «потянуть, чтобы обновить» (D-92). */
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /**
     * Whether the page is on screen, read where it is used (F-590). Off it a running check does not spin its circle: the
     * tab is composed beside home, and the circle drew a frame 60 times a second on any tab for as long as a check ran
     * (F-393).
     */
    shown: () -> Boolean = { true },
) {
    val columns = if (LocalDensity.current.fontScale > 1.15f) 1 else 2
    val scope = rememberCoroutineScope()
    // The groups unrolling or rolling up now ([Fold]), their jobs, and the rows' measured height.
    val folds = remember { mutableStateMapOf<String, Fold>() }
    val foldJobs = remember { mutableMapOf<String, Job>() }
    val rowMeasure = remember { RowMeasure() }
    // Пульт (этап 6): строка поиска висит над списком, и список целиком охватывает её — стрелка
    // вниз его не находила. Вниз из поиска — на первую группу, а если она уехала — в список.
    val listFocus = remember { FocusRequester() }
    val firstGroup = remember { FocusRequester() }
    // «Готово» of the search on a TV takes the frame to the first node found (D-377).
    val firstRow = remember { FocusRequester() }
    // The search hangs over the list, outside it: the remote looked for a neighbour inside the list first, Up from
    // the first group went to the header's sort and Down from there back to the group, so the search never took the
    // frame (D-377). Both are sent to it, and Up from it goes to the header.
    val searchFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }

    // Поиск идёт по узлам, а не по группам: человек помнит имя города, а не то,
    // в какой группе оно лежит. Группа без совпадений просто исчезает из списка.
    // Ищется и протокол — тот, что написан на карточке: «vless», «hysteria2».
    val groups = remember(state.groups, query) {
        val needle = query.trim()

        if (needle.isEmpty()) state.groups
        else state.groups.mapNotNull { group ->
            group.proxies.filter {
                it.name.contains(needle, ignoreCase = true) || it.type.contains(needle, ignoreCase = true)
            }
                .takeIf { it.isNotEmpty() }
                ?.let { group.copy(proxies = it) }
        }
    }

    val searchable = state.loaded && state.groups.isNotEmpty()
    val start = 16.dp + contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val end = 16.dp + contentPadding.calculateEndPadding(LocalLayoutDirection.current)

    // Строка поиска всплывает над списком: в длинном списке за ней приходилось листать в
    // самый верх. В списке на её месте пусто, а сама строка рисуется поверх — одно поле, а не
    // две копии, которые делили бы фокус и клавиатуру. Пока её место в списке видно, строка
    // стоит на нём; ушло за край — выезжает при прокрутке вверх и уезжает при прокрутке вниз.
    val density = LocalDensity.current
    var viewportHeight by remember { mutableIntStateOf(0) }
    // 16 dp to the dock, as on «Подписки», whose last card carries its own 12 dp gap: the dock floats 12 dp above
    // the room the scaffold keeps for it (`DuckerScaffold`), and 16 alone left 4 dp.
    val bottomPadding = 28.dp + contentPadding.calculateBottomPadding()

    /**
     * Unrolls a group's [total] rows under its header, or rolls them up into it ([closing]): one motion of [Fold.shown]
     * that every row and everything below follow in the same layout pass. Opening, the list scrolls with it as far as
     * the rows go below the screen, up to the header. Rolling up at the list's end, the list itself brings the items
     * above down as the content shrinks. Tapped again midway, the same fold turns back from where it is.
     */
    /**
     * How far the list must come down as [name]'s [shown] rows roll up ([first]), and how far it can ([second]): its end
     * would rise above the screen's, the items beyond the screen counted at their measured heights (the headers are
     * alike, and so are the rows); no further than the list is scrolled.
     */
    fun backAfterFold(name: String, shown: Float): Pair<Float, Float> {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return 0f to 0f
        val header = info.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith("header-") == true }?.size
        val row = rowMeasure.height
        if (shown <= 0f || header == null || row <= 0) return 0f to 0f
        // The groups' items in list order with their heights as they are now (as the list below lays them out).
        var below = 0f
        var past = false
        groups.forEachIndexed { index, group ->
            val fold = if (query.isBlank()) folds[group.name] else null
            val open = query.isNotBlank() || ((index == 0) != (group.name in flipped))
            val rows = fold?.rows ?: if (open) (group.proxies.size + columns - 1) / columns else 0
            if (past) below += header
            if (last.key == "header-${group.name}") past = true
            for (i in 0 until rows) {
                if (past) below += fold?.let { (it.shown - i * row).coerceIn(0f, row.toFloat()) } ?: row.toFloat()
                if (last.key == "${group.name}-$i") past = true
            }
        }
        val end = info.viewportEndOffset - info.afterContentPadding
        val short = end - (last.offset + last.size + below - shown)
        if (short <= 0f) return 0f to 0f
        val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
        return short to scrolled
    }

    fun foldGroup(name: String, total: Int, closing: Boolean) {
        if (total == 0) return
        val estimate = rowMeasure.height.takeIf { it > 0 } ?: with(density) { ROW_ESTIMATE.roundToPx() }
        val fold = folds[name] ?: run {
            // The rows a screen can show, and two more: a big group's hundreds stay out of the list meanwhile.
            val cap = minOf(total, viewportHeight / estimate + 2)
            Fold(if (closing) (cap * estimate).toFloat() else 0f, cap).also { folds[name] = it }
        }
        foldJobs.remove(name)?.cancel()
        foldJobs[name] = scope.launch {
            // The motion starts a frame after the tap: in that frame the list takes the new rows or lets them go
            // (composed, laid out and drawn for the first time when opening, 15–20 ms), and a time-based motion begun in
            // it leapt ahead by that frame's length. The rows are measured in it too.
            withFrameNanos {}
            withFrameNanos {}
            val height = rowMeasure.height.takeIf { it > 0 } ?: estimate
            val target = if (closing) 0f else (fold.rows * height).toFloat()
            val spec = tween<Float>(FOLD_MS, easing = FastOutSlowInEasing)
            val (back, room) = if (closing) backAfterFold(name, fold.shown) else 0f to 0f
            if (back > 0f && room > 0f) {
                // The list's end would rise above the screen's as the rows roll up: the list comes down with them in
                // the same frames, at the rate that keeps its end at the screen's until it reaches its top, instead of
                // jumping back once the content ran short (the title and the search fell 130 px in three frames, the
                // owner, 24.11). In a job of its own: a finger takes the scroll from it.
                val from = fold.shown
                launch {
                    listState.scroll {
                        var done = 0f
                        animate(from, 0f, animationSpec = spec) { value, _ ->
                            fold.shown = value
                            val step = floor(minOf(room, back * (1f - value / from)) - done)
                            if (step > 0f) done -= scrollBy(-step)
                        }
                    }
                }.join()
            } else if (!closing) {
                val unroll = launch { animate(fold.shown, target, animationSpec = spec) { value, _ -> fold.shown = value } }
                launch { listState.follow(fold, "header-$name", unroll) }
                unroll.join()
            }
            // Without a scroll alongside, or after a finger took it, the rows finish on their own.
            if (fold.shown != target) animate(fold.shown, target, animationSpec = spec) { value, _ -> fold.shown = value }
            folds.remove(name)
            foldJobs.remove(name)
        }
    }
    val search = remember(density) { FloatingSearch(with(density) { 8.dp.toPx() }) }
    var searchHeight by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    // Анимация читается только при раскладке и отрисовке строки: смена «показать/спрятать»
    // не пересобирает весь экран.
    val typed by rememberUpdatedState(query.isNotEmpty())
    val reveal = remember { Animatable(0f) }
    // С пульта строка не выезжает (D-158): только путала бы, пока ходишь по узлам. Стоит на своём
    // месте в начале списка; фокус пришёл на неё — список сам доезжает до начала.
    val keys by rememberUpdatedState(tvKeys())
    val tv = LocalTv.current
    // Столбец, по которому пульт идёт вниз и вверх (D-161): из правого — по правому, из левого —
    // по левому. С шапки группы и с ряда на ряд (вправо с последней звёздочки) — в левый.
    val column = remember { intArrayOf(0) }

    LaunchedEffect(search) {
        snapshotFlow { !keys && (search.shown || typed) }.collectLatest {
            reveal.animateTo(if (it) 1f else 0f, tween(SEARCH_MS))
        }
    }
    val searchTop = 12.dp + contentPadding.calculateTopPadding()

    val overscroll = overscrollFor(listState)

    /** Место строки в списке (за верхним краем — нет места) и где она была бы, выехав сверху. */
    fun Density.searchPlace(): Pair<Int, Int> {
        val info = listState.layoutInfo
        // Пружина на краю сдвигает список слоем, мимо раскладки, — строка едет вместе с ним.
        val slot = info.visibleItemsInfo.firstOrNull { it.key == SEARCH_KEY }
            ?.let { it.offset + ITEM_GAP.roundToPx() + info.beforeContentPadding + (overscroll?.offset ?: 0f).toInt() }
            ?: Int.MIN_VALUE
        val hidden = -searchHeight - 1f

        return slot to (hidden + (searchTop.roundToPx() - hidden) * reveal.value).toInt()
    }

    // The pane glass, as on Home (F-550).
    GlassPanes {
        PullToRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            top = contentPadding.calculateTopPadding(),
            modifier = modifier.fillMaxSize(),
        ) {
            // The glass style: the floating search shows the list under it on the screen's backdrop: with the backdrop
            // alone its glass stood still as the cards moved beneath (the owner, 19.11).
            val tabBackdrop = LocalGlassBackdrop.current
            val listBackdrop = rememberGlassBackdrop()
            // Live only while it floats off its place: there cards pass under it. At its place, or hidden, it shows the
            // backdrop's still picture, cheaper while it rests (F-552).
            val floating by remember(density) {
                derivedStateOf { reveal.value > 0f && with(density) { searchPlace().let { (slot, floating) -> floating > slot } } }
            }

            Box(Modifier.fillMaxSize().nestedScroll(search)) {
                TvLazyColumn(
                    state = listState,
                    overscrollEffect = overscroll,
                    modifier = Modifier.fillMaxSize().glassSourceOver(listBackdrop, tabBackdrop).screenInsets().focusRequester(listFocus)
                        .onSizeChanged { viewportHeight = it.height },
                    // The 12 dp between the items lies inside each but the first ([ITEM_GAP]): a folding row takes its
                    // gap with it to nothing.
                    // Отступы списка, а не самого экрана: тогда карточки уходят под плавающую
                    // панель вкладок и видны в просветах по её бокам (D-74).
                    contentPadding = PaddingValues(
                        start = start,
                        end = end,
                        top = 16.dp + contentPadding.calculateTopPadding(),
                        bottom = bottomPadding,
                    ),
                ) {
                    item {
                        ScreenHeader(
                            stringResource(R.string.proxies_tab),
                            modifier = if (tv && searchable) {
                                Modifier.focusRequester(headerFocus).focusProperties { down = searchFocus }
                            } else Modifier,
                        ) {
                            // Global — в шапке, а не отдельным рядом над списком (D-136).
                            if (state.running && state.modeSwitch) GlobalChip(state.mode, onMode)

                            // With the VPN off too: the tab loads the profile into the core without the
                            // tunnel, and the core sorts that list by delay as well (D-319).
                            // Plain text in the glass style too: a text toggle gets no pane (the owner, 17.11).
                            ProxySortToggle(state.sort, onSort)
                        }
                    }

                    if (searchable) {
                        item(key = SEARCH_KEY) {
                            Spacer(Modifier.padding(top = ITEM_GAP).fillMaxWidth().height(with(density) { searchHeight.toDp() }))
                        }
                    }

                    // Заглушки, а не бесконечная полоска (D-92): так же, как на главной, и ничего не
                    // крутится на невидимой странице — лента держит её собранной рядом с главной (D-91).
                    if (!state.loaded) {
                        item { Box(Modifier.padding(top = ITEM_GAP)) { ProxySkeleton() } }
                    }

                    if (state.loaded && state.groups.isEmpty()) {
                        item {
                            Box(Modifier.padding(top = ITEM_GAP)) {
                                EmptyState(
                                    icon = Symbols.Language,
                                    title = stringResource(R.string.ducker_no_proxies),
                                    message = stringResource(
                                        if (state.running) R.string.proxies_empty_direct else R.string.proxies_empty_stopped
                                    ),
                                )
                            }
                        }
                    }

                    if (state.loaded && groups.isEmpty() && state.groups.isNotEmpty()) {
                        item {
                            Box(Modifier.padding(top = ITEM_GAP)) {
                                EmptyState(
                                    icon = Symbols.Language,
                                    title = stringResource(R.string.proxy_search_empty),
                                    message = stringResource(R.string.proxy_search_empty_hint),
                                )
                            }
                        }
                    }

                    groups.forEachIndexed { index, group ->
                        // Свёрнуто по умолчанию, кроме первой группы: у провайдера их бывает пять,
                        // а узлов в каждой под десяток — развёрнутый список не помещается на экран.
                        // При поиске группы раскрыты: свёрнутый список прятал бы найденное.
                        val open = query.isNotBlank() || ((index == 0) != (group.name in flipped))
                        val rows = group.proxies.chunked(columns)

                        item(key = "header-${group.name}") {
                            GroupHeader(
                                group = group,
                                open = open,
                                checking = shown() && state.checking == group.name,
                                checkEnabled = state.checking == null,
                                canCheck = state.canCheck,
                                onExpand = {
                                    if (group.name in flipped) flipped -= group.name else flipped += group.name
                                    if (query.isBlank()) foldGroup(group.name, rows.size, closing = open)
                                },
                                onHealthCheck = { onHealthCheck(group.name) },
                                // Пульт входит на «Прокси» на шапку первой группы: дальше вправо — замер,
                                // вниз — узлы (этап 6).
                                modifier = Modifier.padding(top = ITEM_GAP)
                                    .then(if (tv) Modifier.onFocusChanged { if (it.hasFocus) column[0] = 0 } else Modifier)
                                    .then(if (index == 0) Modifier.tabEntry().focusRequester(firstGroup) else Modifier)
                                    .then(
                                        if (index == 0 && tv) Modifier.focusProperties {
                                            onExit = { if (requestedFocusDirection == FocusDirection.Up) searchFocus.requestFocus() }
                                        } else Modifier,
                                    ),
                            )
                        }

                        // Rows are keyed by position: a starred node moving up changes the cards in place
                        // rather than crossfading every row below it (D-359). While the group unrolls or rolls up,
                        // only the rows that can be seen stay in the list ([Fold.rows]).
                        val fold = if (query.isBlank()) folds[group.name] else null
                        itemsIndexed(
                            when {
                                fold != null -> rows.take(fold.rows)
                                open -> rows
                                else -> emptyList()
                            },
                            key = { i, _ -> "${group.name}-$i" },
                        ) { row, proxies ->
                            ProxyRow(
                                group = group, proxies = proxies, columns = columns, column = column,
                                onSelect = onSelect, onFavorite = onFavorite,
                                modifier = Modifier.foldRow(if (query.isBlank()) folds[group.name] else null, row, rowMeasure)
                                    .padding(top = ITEM_GAP)
                                    .then(if (index == 0 && row == 0) Modifier.focusRequester(firstRow) else Modifier),
                            )
                        }
                    }
                }

                if (searchable) {
                    SearchField(
                        value = query,
                        onValue = onQuery,
                        hint = stringResource(R.string.proxy_search),
                        next = firstRow,
                        glass = if (floating || tabBackdrop == null) listBackdrop else tabBackdrop,
                        shadowLift = {
                            with(density) {
                                val (slot, floating) = searchPlace()
                                ((floating - slot.toFloat()) / 12.dp.toPx()).coerceIn(0f, 1f)
                            }
                        },
                        modifier = Modifier.screenInsets()
                            .padding(start = start, end = end)
                            .focusRequester(searchFocus)
                            .focusProperties {
                                if (!tv) return@focusProperties

                                up = headerFocus
                                val first = groups.firstOrNull()?.let { "header-${it.name}" }
                                down = if (listState.layoutInfo.visibleItemsInfo.any { it.key == first }) firstGroup else listFocus
                            }
                            .onFocusChanged {
                                if (!it.hasFocus || !keys) return@onFocusChanged
                                // The first node found is the left one, as from a group's header.
                                column[0] = 0
                                scope.launch { listState.tvScrollToStart() }
                            }
                            .onSizeChanged { searchHeight = it.height.toFloat() }
                            .offset {
                                val (slot, floating) = searchPlace()

                                IntOffset(0, maxOf(slot, floating))
                            }
                            // Поверх списка, но озвучивается раньше него, а не после всех узлов.
                            .semantics { traversalIndex = -1f },
                    )
                }
            }
        }
    }
}


@Composable
private fun ProxyRow(
    group: ProxyGroupState,
    proxies: List<NodeState>,
    columns: Int,
    column: IntArray,
    onSelect: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = LocalTv.current
    // Пульт (D-158) идёт по группе строго: карточка, её звёздочка, следующая
    // карточка — и так до конца группы, с ряда на ряд. Вниз и вверх — на карточку
    // того же столбца в соседнем ряду, а нет такой — на последнюю (D-161).
    // Звёздочку стрелки сами не находят: она внутри карточки, а внутрь
    // нажимаемого Compose не водит (F-187).
    val cards = remember(proxies.size) { List(proxies.size) { FocusRequester() } }
    val stars = remember(proxies.size) { List(proxies.size) { FocusRequester() } }
    val more = proxies.last() !== group.proxies.last()
    // The name, not the group: a group is a new object whenever one of its nodes changes, and every card
    // on screen recomposed with a new tap handler during a check (F-393).
    val name = group.name

    Row(
        modifier = modifier.fillMaxWidth()
            // Высоту больше не подгоняем: имя занимает ровно две строки,
            // а тип и задержка рисуются всегда, поэтому карточки и так
            // одинаковые. С IntrinsicSize.Min при minLines нижняя строка
            // обрезалась — задержка просто пропадала.
            .selectableGroup()
            .then(
                if (tv) {
                    Modifier.focusProperties {
                        onEnter = { cards[column[0].coerceAtMost(cards.lastIndex)].requestFocus() }
                    }.focusGroup()
                } else Modifier.focusGroup(),
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        proxies.forEachIndexed { i, proxy ->
            ProxyOption(
                proxy = proxy,
                selected = proxy.name == group.now,
                enabled = group.selectable,
                onSelect = { onSelect(name, proxy.name) },
                onFavorite = { onFavorite(proxy.name) },
                card = cards[i],
                star = stars[i],
                previous = stars.getOrNull(i - 1),
                next = cards.getOrNull(i + 1),
                // Вправо с последней звёздочки ряда — на следующий ряд группы.
                wrap = more && i == proxies.lastIndex,
                onFocused = { column[0] = i },
                onWrap = { column[0] = 0 },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (proxies.size < columns) Spacer(Modifier.weight(1f))
    }
}

private const val SEARCH_KEY = "search"
private const val SEARCH_MS = 180

/**
 * Global/Rules в шапке (D-136, D-143): всегда залит и показывает текущий режим. Нажатие
 * переключает режим, надпись сменяется плавно — без заливки «выключено» было непонятно, что
 * сейчас включено.
 */
@Composable
private fun GlobalChip(mode: TunnelState.Mode?, onMode: (TunnelState.Mode) -> Unit) {
    val global = mode == TunnelState.Mode.Global
    val label: @Composable () -> Unit = {
        AnimatedContent(
            targetState = global,
            transitionSpec = {
                fadeIn(tween(MODE_IN_MS, delayMillis = MODE_OUT_MS)) togetherWith fadeOut(tween(MODE_OUT_MS))
            },
            label = "mode",
        ) {
            Text(stringResource(if (it) R.string.mode_global else R.string.mode_rule))
        }
    }

    // On the pane glass in the chosen card's glass, as the bell (F-579): the solid accent pill stood out of the glass
    // (the owner, 23.11). It swells under a finger, without a ripple.
    val backdrop = LocalGlassBackdrop.current
    if (backdrop != null && glassPanesOn()) {
        val scheme = MaterialTheme.colorScheme
        val press = rememberGlassPress()
        Box(
            Modifier.minimumInteractiveComponentSize()
                .height(32.dp)
                .graphicsLayer { alpha = if (mode != null) 1f else 0.38f }
                .glassPress(press?.takeIf { mode != null }, own = true)
                .liquidGlass(backdrop, base = scheme.background, tint = chosenGlassTint(), shape = CircleShape, press = press, fixedTint = true)
                .clip(CircleShape)
                .clickable(enabled = mode != null, role = Role.Button, interactionSource = null, indication = null) {
                    onMode(if (global) TunnelState.Mode.Rule else TunnelState.Mode.Global)
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides scheme.onPrimaryContainer,
                LocalTextStyle provides MaterialTheme.typography.labelLarge,
            ) { label() }
        }
        return
    }

    AssistChip(
        enabled = mode != null,
        onClick = { onMode(if (global) TunnelState.Mode.Rule else TunnelState.Mode.Global) },
        label = label,
        shape = MaterialTheme.shapes.extraLarge,
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            labelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

private const val MODE_OUT_MS = 90
private const val MODE_IN_MS = 150

@Composable
private fun GroupHeader(
    group: ProxyGroupState,
    open: Boolean,
    checking: Boolean,
    checkEnabled: Boolean,
    canCheck: Boolean,
    onExpand: () -> Unit,
    onHealthCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Пульт (этап 6): кружок замера лежит внутри нажимаемой шапки, а внутрь нажимаемого стрелки
    // не заходят (F-187), — вправо на него и обратно ведём явно.
    val head = remember { FocusRequester() }
    val ping = remember { FocusRequester() }
    val tv = LocalTv.current

    val expandLabel = stringResource(if (open) R.string.ducker_collapse_group else R.string.ducker_expand_group)

    // The header is the card's own click, so under the pane glass the card swells for it but not for the check's.
    Card(
        modifier.fillMaxWidth().focusGroup(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            // The card's 24 dp: by its size the frame took the usual 20 and stood off the card's corners.
            .then(if (tv) Modifier.ringCorner(24.dp) else Modifier)
            .focusRequester(head)
            .focusProperties { if (tv && canCheck) end = ping }
            .clickable(
                interactionSource = null,
                indication = paneIndication(),
                role = Role.Button,
                onClickLabel = expandLabel,
                onClick = onExpand,
            ),
        ownClick = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Без подложки: картинку группы задаёт провайдер, её поля и форму мы не
            // знаем, и любая плашка под ней вылезает по краям. Не загрузилась — глобус,
            // а не пустое место.
            var failed by remember(group.icon) { mutableStateOf(false) }
            val icon = remember(group.icon) { logoModel(group.icon) }

            if (icon != null && !failed) {
                AsyncImage(model = icon, contentDescription = null,
                    modifier = Modifier.size(34.dp), onError = { failed = true })
            } else {
                Icon(Symbols.Language, contentDescription = null,
                    modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val title = MaterialTheme.typography.titleMedium

                // A name on two lines with the style's leading (1.5) read as two separate lines; tighter here,
                // while one line keeps its old height and place. The old line is the font size times the style's
                // ratio, as text lays it out: under a larger font `lineHeight.toDp()` scales 24 sp less than 16.
                val line = with(LocalDensity.current) {
                    (title.fontSize.toPx() * title.lineHeight.value / title.fontSize.value).toDp()
                }

                Text(
                    group.name.isolated(),
                    modifier = Modifier.heightIn(min = line).wrapContentHeight(),
                    style = title.copy(lineHeight = 1.25.em),
                    fontWeight = FontWeight.Medium,
                )
                Text(group.now.isolated(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // У групп, которые выбирают сами, узлы не нажимаются — говорим об
                // этом здесь, иначе выглядит как сломанное нажатие. A plain group's node count said nothing useful
                // (the owner, 19.11).
                if (!group.selectable) {
                    Text(
                        text = stringResource(R.string.group_auto, group.proxies.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The check and the arrow closer, 8 dp between their glass circles: the name took two lines. Air's too (the owner,
            // 23.11).
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Замер — кружком рядом со стрелкой, а не кнопкой во всю ширину (D-120): та
            // занимала строку в каждой раскрытой группе. Замерять нечем, пока ядро не работает.
            if (canCheck) {
                ProxyCheckButton(
                    checking = checking,
                    enabled = checkEnabled,
                    onClick = onHealthCheck,
                    // Its own Left: the header holds it, and the page sent Left to the dock.
                    modifier = Modifier.focusRequester(ping).then(if (tv) Modifier.ownLeft(head) else Modifier),
                )
            }
            // On the pane glass a round glass button beside the check, as Home's buttons on its cards; else the bare arrow
            // of the header's own click.
            val arrow = if (open) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown
            if (glassPanesOn()) {
                PaneIconButton(onClick = onExpand) { Icon(arrow, contentDescription = expandLabel) }
            } else {
                Icon(arrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            }
        }
    }
}

/**
 * Раскрытие групп (D-120), with [ProxyScreen]'s `foldGroup`: a group's rows unroll under its header and roll up into it,
 * the rows below following in the same layout pass (the owner, 24.11: the rows faded in and out where they stood while
 * the headers below slid over them on springs of their own, items from beyond the screen came in at other times, and the
 * list then scrolled after them by itself; F-591). [shown] — the pixels of the rows that show, one row after another
 * from the top; [rows] — how many rows the list keeps meanwhile.
 */
private class Fold(shown: Float, val rows: Int) {
    var shown by mutableFloatStateOf(shown)
}

/** The rows' height with their gap, as last measured: all rows are alike (two lines of name, the type and the delay). */
private class RowMeasure {
    var height = 0
}

/**
 * A row of a folding group shows only its share of [Fold.shown], its bottom first, sliding out from under the item above:
 * the row's height follows it, so all below moves with it. Without a fold, the row as it is.
 */
private fun Modifier.foldRow(fold: Fold?, row: Int, measure: RowMeasure): Modifier =
    (if (fold == null) this else drawWithContent {
        // A row not out yet is not drawn at all: cut to nothing, its glass panes still rendered their layers, and a
        // fold on glass took twice the GPU of a scroll.
        if (size.height <= 0f) return@drawWithContent
        // Only the top is cut, where the row comes out from under the one above: its shadow keeps its room at the sides.
        val room = 16.dp.toPx()
        clipRect(left = -room, top = 0f, right = size.width + room, bottom = size.height + room) {
            this@drawWithContent.drawContent()
        }
    }).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val full = placeable.height
        measure.height = full
        val shown = if (fold == null) full else (fold.shown - row * full).coerceIn(0f, full.toFloat()).roundToInt()
        layout(placeable.width, shown) { placeable.place(0, shown - full) }
    }

/**
 * While [unroll] runs, the list scrolls as far as the group's rows grow below the screen, but never takes its [header]
 * above the top. A finger on the list stops it; the rows go on unrolling.
 */
private suspend fun LazyListState.follow(fold: Fold, header: String, unroll: Job) {
    scroll {
        var last = fold.shown
        while (unroll.isActive) {
            withFrameNanos {}
            val now = fold.shown
            val grown = now - last
            if (grown <= 0f) continue
            val info = layoutInfo
            val head = info.visibleItemsInfo.firstOrNull { it.key == header } ?: break
            val end = info.viewportEndOffset - info.afterContentPadding
            val over = head.offset + head.size + now - end
            // Whole pixels: the fractions left the items a pixel to and fro for a few frames as the scroll ended.
            val step = floor(minOf(grown, over, head.offset.toFloat()))
            last = now
            if (step > 0f) scrollBy(step)
        }
    }
}

/** A group's unroll and roll-up. */
private const val FOLD_MS = 320

/** A row's height before any was measured: a card of two lines of name, the type and the delay, and the gap. */
private val ROW_ESTIMATE = 96.dp

/** The space above each item of «Прокси»'s list but the first, inside the item. */
private val ITEM_GAP = 12.dp

// One message at a time: OK pressed again replaces it instead of queuing the same text for seconds.
private var groupToast: Toast? = null

private fun groupPicks(context: Context) {
    groupToast?.cancel()
    groupToast = Toast.makeText(context.applicationContext, R.string.group_picks_itself, Toast.LENGTH_SHORT).apply { show() }
}

private val FavoriteCornerShape = GenericShape { size, direction ->
    val end = if (direction == LayoutDirection.Ltr) size.width else 0f
    moveTo(end, 0f)
    lineTo(size.width - end, 0f)
    arcTo(
        rect = Rect(end - size.width, -size.height, end + size.width, size.height),
        startAngleDegrees = if (direction == LayoutDirection.Ltr) 180f else 0f,
        sweepAngleDegrees = if (direction == LayoutDirection.Ltr) -90f else 90f,
        forceMoveTo = false,
    )
    close()
}


@Composable
private fun ProxyOption(
    proxy: NodeState,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    /** Пульт: сама карточка и её звёздочка, звёздочка слева и карточка справа в том же ряду. */
    card: FocusRequester,
    star: FocusRequester,
    previous: FocusRequester?,
    next: FocusRequester?,
    /** Вправо со звёздочки — на следующий ряд: карточка последняя в ряду, но не в группе. */
    wrap: Boolean,
    /** Фокус на карточке или её звёздочке; переход на следующий ряд вот-вот случится. */
    onFocused: () -> Unit,
    onWrap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val tv = LocalTv.current
    val context = LocalContext.current
    // Along the row: to the left in a right-to-left language.
    val onward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight

    Card(
        modifier = modifier,
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.medium)
            .then(if (tv) Modifier.onFocusChanged { if (it.hasFocus) onFocused() } else Modifier)
            .focusRequester(card)
            .focusProperties {
                if (!tv) return@focusProperties

                // Start and end: the star and the neighbours change sides in a right-to-left language.
                end = star
                if (previous != null) start = previous
            }
            // В группе, которая выбирает сама, узлы не нажимаются — но с пульта фокус берут, иначе
            // он проваливался сквозь них на звёздочки (D-158). OK on one says why nothing happens:
            // silence read as a broken button.
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = paneIndication(),
                enabled = keepFocus(enabled),
                role = Role.RadioButton,
                onClick = { if (enabled) onSelect() else groupPicks(context) },
            ),
        shape = MaterialTheme.shapes.medium,
        // Swells for a touch on the card, not on its star.
        ownClick = true,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = proxy.name.asName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.named(),
                    // The filled surface carries selection; keep the name lighter than screen headings.
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    // Ровно две строки всегда: у провайдеров имена разной длины, и без
                    // этого соседние карточки выходят разной высоты. Длинное имя
                    // помещается целиком, короткое оставляет пустую строку.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Галочки нет: выбранный узел и так выделен цветом и рамкой, а для
                // озвучки выбор объявляет сама карточка (role = RadioButton).
                // Звёздочка стоит в правом верхнем углу, на одной линии с названием,
                // и не ездит вслед за его длиной.
                Icon(
                    imageVector = if (proxy.favorite) Symbols.Star else Symbols.StarOutline,
                    contentDescription = stringResource(if (proxy.favorite) R.string.unfavorite else R.string.favorite),
                    modifier = Modifier.size(20.dp)
                        // Anchor the sector to the card's top/end while retaining the 20 dp icon slot.
                        // The real clipped target also keeps surrounding star taps off the card.
                        .then(if (tv) Modifier else Modifier.layout { measurable, _ ->
                            val icon = 20.dp.roundToPx()
                            val target = icon + 24.dp.roundToPx() + 12.dp.roundToPx()
                            val placeable = measurable.measure(Constraints.fixed(target, target))
                            layout(icon, icon) {
                                placeable.placeRelative(-24.dp.roundToPx(), -12.dp.roundToPx())
                            }
                        })
                        .focusRequester(star)
                        // Left to its own card, as the subscription row's «⋯»: the first column's card holds the
                        // star, and the page sent Left to the dock.
                        .then(if (tv) Modifier.ownLeft(card) else Modifier)
                        .focusProperties {
                            if (!tv) return@focusProperties

                            if (next != null) end = next
                        }
                        .onPreviewKeyEvent {
                            if (!tv || !wrap || it.key != onward || it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            // Ряд ниже может быть ещё не собран — туда ведёт обычный поиск вниз,
                            // а вход в ряд ставит на его левую карточку.
                            onWrap()
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        .clip(if (tv) MaterialTheme.shapes.small else FavoriteCornerShape)
                        .toggleable(value = proxy.favorite, role = Role.Checkbox, onValueChange = { onFavorite() })
                        .then(if (tv) Modifier else Modifier.padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 24.dp)),
                    tint = if (proxy.favorite) LocalContentColor.current
                    else LocalContentColor.current.copy(alpha = 0.4f),
                )
            }
            // Строки рисуем всегда, даже когда значения нет: иначе карточки без замера
            // выходят ниже соседних, и список выглядит рваным.
            Text(
                text = proxy.type.ifEmpty { stringResource(R.string.value_unknown) },
                style = MaterialTheme.typography.labelSmall,
                // Выбранная карточка заливается чёрным, поэтому вложенные цвета берём
                // от неё самой: иначе серый текст пропадает на чёрном.
                color = LocalContentColor.current.copy(alpha = 0.6f),
            )

            // «Нет ответа» — свойство самого узла, а не группы: значение приходит из
            // хранилища замеров и означает, что замер был и не удался (D-72).
            val failed = proxy.delay == DelayStore.NO_ANSWER

            Text(
                text = when {
                    proxy.delay >= DelayStore.SLOW -> stringResource(R.string.delay_slow, proxy.delay / 1000)
                    proxy.delay > 0 -> stringResource(R.string.delay_ms, proxy.delay)
                    failed -> stringResource(R.string.delay_failed)
                    else -> stringResource(R.string.value_unknown)
                },
                style = MaterialTheme.typography.labelLarge,
                // A selected card is filled in the accent: the red of its own letters' side, not the screen's.
                color = if (failed) failureOn(LocalContentColor.current) else LocalContentColor.current,
            )
        }
    }
}

/** Заглушка списка узлов: поиск, карточка группы и пара рядов узлов (D-92). */
@Composable
private fun ProxySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(height = 48.dp)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(height = 40.dp, width = 40.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(height = 18.dp, width = 170.dp)
                        Skeleton(height = 12.dp, width = 110.dp)
                    }
                }

                Skeleton(height = 36.dp)
            }
        }

        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Skeleton(height = 16.dp, width = 110.dp)
                            Skeleton(height = 12.dp, width = 50.dp)
                            Skeleton(height = 18.dp, width = 60.dp)
                        }
                    }
                }
            }
        }
    }
}

