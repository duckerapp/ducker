package com.github.kr328.clash.ui.proxy

import com.github.kr328.clash.ui.components.tvScrollToStart
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.tvKeys
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import com.github.kr328.clash.ui.components.tabEntry
import com.github.kr328.clash.ui.components.keepFocus
import com.github.kr328.clash.ui.components.logoModel
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.traversalIndex
import com.github.kr328.clash.ui.components.DuckerIcons
import com.github.kr328.clash.ui.components.overscrollFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.R
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.PullToRefresh
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.FloatingSearch
import com.github.kr328.clash.ui.components.ProxySortToggle
import com.github.kr328.clash.ui.components.ProxyCheckButton
import com.github.kr328.clash.ui.components.SearchField
import com.github.kr328.clash.ui.components.Skeleton
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.DuckerCard as Card
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
) {
    val columns = if (LocalDensity.current.fontScale > 1.15f) 1 else 2
    val scope = rememberCoroutineScope()
    var returnToken by remember { mutableStateOf<Any?>(null) }
    var groupScroll by remember { mutableStateOf<Job?>(null) }
    // Пульт (этап 6): строка поиска висит над списком, и список целиком охватывает её — стрелка
    // вниз его не находила. Вниз из поиска — на первую группу, а если она уехала — в список.
    val listFocus = remember { FocusRequester() }
    val firstGroup = remember { FocusRequester() }

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
    val bottomPaddingPx = with(density) { (16.dp + contentPadding.calculateBottomPadding()).roundToPx() }
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
            ?.let { it.offset + info.beforeContentPadding + (overscroll?.offset ?: 0f).toInt() }
            ?: Int.MIN_VALUE
        val hidden = -searchHeight - 1f

        return slot to (hidden + (searchTop.roundToPx() - hidden) * reveal.value).toInt()
    }

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        top = contentPadding.calculateTopPadding(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().nestedScroll(search)) {
            TvLazyColumn(
                state = listState,
                overscrollEffect = overscroll,
                modifier = Modifier.fillMaxSize().screenInsets().focusRequester(listFocus)
                    .onSizeChanged { viewportHeight = it.height },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Отступы списка, а не самого экрана: тогда карточки уходят под плавающую
                // панель вкладок и видны в просветах по её бокам (D-74).
                contentPadding = PaddingValues(
                    start = start,
                    end = end,
                    top = 16.dp + contentPadding.calculateTopPadding(),
                    bottom = 16.dp + contentPadding.calculateBottomPadding(),
                ),
            ) {
                item {
                    ScreenHeader(stringResource(R.string.proxies_tab)) {
                        // Global — в шапке, а не отдельным рядом над списком (D-136).
                        if (state.running && state.modeSwitch) GlobalChip(state.mode, onMode)

                        // With the VPN off too: the tab loads the profile into the core without the
                        // tunnel, and the core sorts that list by delay as well (D-319).
                        ProxySortToggle(state.sort, onSort)
                    }
                }

                if (searchable) {
                    item(key = SEARCH_KEY) {
                        Spacer(Modifier.fillMaxWidth().height(with(density) { searchHeight.toDp() }))
                    }
                }

                // Заглушки, а не бесконечная полоска (D-92): так же, как на главной, и ничего не
                // крутится на невидимой странице — лента держит её собранной рядом с главной (D-91).
                if (!state.loaded) {
                    item { ProxySkeleton() }
                }

                if (state.loaded && state.groups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.ducker_no_proxies),
                            message = stringResource(
                                if (state.running) R.string.proxies_empty_direct else R.string.proxies_empty_stopped
                            ),
                        )
                    }
                }

                if (state.loaded && groups.isEmpty() && state.groups.isNotEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.proxy_search_empty),
                            message = stringResource(R.string.proxy_search_empty_hint),
                        )
                    }
                }

                groups.forEachIndexed { index, group ->
                    // Свёрнуто по умолчанию, кроме первой группы: у провайдера их бывает пять,
                    // а узлов в каждой под десяток — развёрнутый список не помещается на экран.
                    // При поиске группы раскрыты: свёрнутый список прятал бы найденное.
                    val open = query.isNotBlank() || ((index == 0) != (group.name in flipped))

                    item(key = "header-${group.name}") {
                        GroupHeader(
                            group = group,
                            open = open,
                            checking = state.checking == group.name,
                            checkEnabled = state.checking == null,
                            canCheck = state.canCheck,
                            onExpand = {
                                val beforeLayout = listState.layoutInfo
                                groupScroll?.cancel()
                                if (group.name in flipped) flipped -= group.name else flipped += group.name

                                if (query.isBlank() && (open || returnToken != null)) {
                                    // Keep scroll space while native item fades remove the rows.
                                    val token = Any()
                                    returnToken = token
                                    groupScroll = scope.launch {
                                        try {
                                            withTimeoutOrNull(REVEAL_WAIT_MS) {
                                                snapshotFlow { listState.layoutInfo }.first { it !== beforeLayout }
                                            }
                                            listState.returnAfterCollapse(bottomPaddingPx)
                                            withFrameNanos {}
                                        } finally {
                                            if (returnToken === token) returnToken = null
                                        }
                                    }
                                } else if (!open && group.proxies.isNotEmpty()) {
                                    groupScroll = scope.launch {
                                        listState.reveal(
                                            header = "header-${group.name}",
                                            firstRow = "${group.name}-${group.proxies.first().name}",
                                            rows = (group.proxies.size + columns - 1) / columns,
                                        )
                                    }
                                }
                            },
                            onHealthCheck = { onHealthCheck(group.name) },
                            // Пульт входит на «Прокси» на шапку первой группы: дальше вправо — замер,
                            // вниз — узлы (этап 6).
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = ItemMove)
                                .then(if (tv) Modifier.onFocusChanged { if (it.hasFocus) column[0] = 0 } else Modifier)
                                .then(if (index == 0) Modifier.tabEntry().focusRequester(firstGroup) else Modifier),
                        )
                    }

                    items(
                        if (open) group.proxies.chunked(columns) else emptyList(),
                        key = { "${group.name}-${it.first().name}" },
                    ) { proxies ->
                        ProxyRow(
                            group = group, proxies = proxies, columns = columns, column = column,
                            onSelect = onSelect, onFavorite = onFavorite,
                            modifier = Modifier.animateItem(
                                fadeInSpec = ItemFadeIn, fadeOutSpec = ItemFadeOut, placementSpec = ItemMove,
                            ),
                        )
                    }
                }
                if (returnToken != null) {
                    item(key = RETURN_RESERVE_KEY) {
                        Spacer(Modifier.height(with(density) { viewportHeight.toDp() }))
                    }
                }
            }

            if (searchable) {
                SearchField(
                    value = query,
                    onValue = onQuery,
                    hint = stringResource(R.string.proxy_search),
                    shadowLift = {
                        with(density) {
                            val (slot, floating) = searchPlace()
                            ((floating - slot.toFloat()) / 12.dp.toPx()).coerceIn(0f, 1f)
                        }
                    },
                    modifier = Modifier.screenInsets()
                        .padding(start = start, end = end)
                        .focusProperties {
                            if (!tv) return@focusProperties

                            val first = groups.firstOrNull()?.let { "header-${it.name}" }
                            down = if (listState.layoutInfo.visibleItemsInfo.any { it.key == first }) firstGroup else listFocus
                        }
                        .onFocusChanged { if (it.hasFocus && keys) scope.launch { listState.tvScrollToStart() } }
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

private const val RETURN_RESERVE_KEY = "proxy-scroll-return-reserve"

private suspend fun LazyListState.returnAfterCollapse(bottom: Int) {
    val info = layoutInfo
    // The last item is only a reserve. Use the real content end to find the legal offset.
    val tail = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 2 } ?: return
    val gap = (info.viewportEndOffset - bottom - tail.offset - tail.size).coerceAtLeast(0).toFloat()
    if (gap <= 0f || (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0)) return

    scroll {
        var remaining = gap
        var previous = 0f
        animate(0f, 1f, animationSpec = tween(REVEAL_MS)) { fraction, _ ->
            // Only consume real travel when a compact page reaches its first item.
            val toStart = if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset
                else layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.let { -it }
            if (toStart != null) remaining = minOf(remaining, toStart.coerceAtLeast(0).toFloat())
            val portion = if (previous < 1f) ((fraction - previous) / (1f - previous)).coerceIn(0f, 1f) else 0f
            remaining = (remaining + scrollBy(-remaining * portion)).coerceAtLeast(0f)
            previous = fraction
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
                onSelect = { onSelect(group.name, proxy.name) },
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

    AssistChip(
        enabled = mode != null,
        onClick = { onMode(if (global) TunnelState.Mode.Rule else TunnelState.Mode.Global) },
        label = {
            AnimatedContent(
                targetState = global,
                transitionSpec = {
                    fadeIn(tween(MODE_IN_MS, delayMillis = MODE_OUT_MS)) togetherWith fadeOut(tween(MODE_OUT_MS))
                },
                label = "mode",
            ) {
                Text(stringResource(if (it) R.string.mode_global else R.string.mode_rule))
            }
        },
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

    Card(modifier.fillMaxWidth().focusGroup()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .focusRequester(head)
                .focusProperties { if (tv && canCheck) right = ping }
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (open) R.string.ducker_collapse_group else R.string.ducker_expand_group),
                    onClick = onExpand,
                ).padding(12.dp),
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
                Icon(Icons.Default.Language, contentDescription = null,
                    modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(group.now, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    // У групп, которые выбирают сами, узлы не нажимаются — говорим об
                    // этом здесь, иначе выглядит как сломанное нажатие.
                    text = if (group.selectable) {
                        stringResource(R.string.ducker_nodes_count, group.proxies.size)
                    } else {
                        stringResource(R.string.group_auto, group.proxies.size)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Замер — кружком рядом со стрелкой, а не кнопкой во всю ширину (D-120): та
            // занимала строку в каждой раскрытой группе. Замерять нечем, пока ядро не работает.
            if (canCheck) {
                ProxyCheckButton(
                    checking = checking,
                    enabled = checkEnabled,
                    onClick = onHealthCheck,
                    modifier = Modifier.focusRequester(ping).focusProperties { if (tv) left = head },
                )
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Раскрытие групп (D-120): проявление, растворение и сдвиг соседей. */
/**
 * Раскрытую группу подтягиваем в видимую часть. У нижней группы узлы появлялись под панелью
 * вкладок и за краем экрана: у верхних соседи ниже съезжают, а здесь не двигалось ничего, и
 * раскрытие выглядело как «не сработало». Сдвиг — ровно до последнего узла, но не дальше
 * заголовка: он остаётся на экране.
 */
private suspend fun LazyListState.reveal(header: String, firstRow: String, rows: Int) {
    val layout = withTimeoutOrNull(REVEAL_WAIT_MS) {
        snapshotFlow { layoutInfo }.first { info -> info.visibleItemsInfo.any { it.key == firstRow } }
    } ?: return

    val head = layout.visibleItemsInfo.firstOrNull { it.key == header } ?: return
    val row = layout.visibleItemsInfo.first { it.key == firstRow }

    // Отступы элементов считаются от конца верхнего поля списка; нижнее поле — под панелью вкладок.
    val bottom = row.offset + rows * row.size + (rows - 1) * layout.mainAxisItemSpacing
    val visibleEnd = layout.viewportEndOffset - layout.afterContentPadding
    val shift = minOf(bottom - visibleEnd, head.offset)

    if (shift > 0) animateScrollBy(shift.toFloat(), tween(REVEAL_MS))
}

private const val REVEAL_WAIT_MS = 300L
private const val REVEAL_MS = 320

private val ItemFadeIn = tween<Float>(220)
private val ItemFadeOut = tween<Float>(120)
private val ItemMove = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)


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

    Card(
        modifier = modifier,
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.medium)
            .then(if (tv) Modifier.onFocusChanged { if (it.hasFocus) onFocused() } else Modifier)
            .focusRequester(card)
            .focusProperties {
                if (!tv) return@focusProperties

                right = star
                if (previous != null) left = previous
            }
            // В группе, которая выбирает сама, узлы не нажимаются — но с пульта фокус берут, иначе
            // он проваливался сквозь них на звёздочки (D-158).
            .selectable(
                selected = selected,
                enabled = keepFocus(enabled),
                role = Role.RadioButton,
                onClick = { if (enabled) onSelect() },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = proxy.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
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
                    imageVector = if (proxy.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.favorite),
                    modifier = Modifier.size(20.dp)
                        .focusRequester(star)
                        .focusProperties {
                            if (!tv) return@focusProperties

                            left = card
                            if (next != null) right = next
                        }
                        .onPreviewKeyEvent {
                            if (!tv || !wrap || it.key != Key.DirectionRight || it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            // Ряд ниже может быть ещё не собран — туда ведёт обычный поиск вниз,
                            // а вход в ряд ставит на его левую карточку.
                            onWrap()
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onFavorite),
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
                color = if (failed) MaterialTheme.colorScheme.error else LocalContentColor.current,
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

