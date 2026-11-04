package com.github.kr328.clash.ui.home

import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.Symbols
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.FloatingSearch
import com.github.kr328.clash.ui.components.ProxyCheckButton
import com.github.kr328.clash.ui.components.ProxySortToggle
import com.github.kr328.clash.ui.components.SearchField
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.airShadow
import com.github.kr328.clash.ui.proxy.NodeState
import com.github.kr328.clash.ui.proxy.ProxyGroupState
import com.github.kr328.clash.ui.proxy.ProxyState
import kotlinx.coroutines.flow.collectLatest

/** The proxy tab owns ordering, checks and selection; the sheet only presents that state. */
@Composable
internal fun NodePickerSheet(
    open: Boolean,
    state: ProxyState,
    initialGroup: String?,
    onPick: (group: String, name: String) -> Unit,
    onSort: (ProxySort) -> Unit,
    onCheck: (String) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val group = state.groups.firstOrNull { it.name == initialGroup }
        ?: state.groups.firstOrNull()
    val shortWindow = LocalConfiguration.current.screenHeightDp < 480

    DuckerSheet(
        open = open,
        title = stringResource(R.string.all_nodes),
        titleAction = { ProxySortToggle(state.sort, onSort) },
        busy = false, onDismiss = onDismiss, onClosed = onClosed,
        scrollable = false, compact = true,
        heightFraction = if (shortWindow) 1f else 0.80f,
    ) {
        PickerList(
            state = state, group = group, query = query, onQuery = { query = it },
            onPick = onPick, onCheck = onCheck,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        SheetButton(
            text = stringResource(R.string.config_result_close),
            busyText = stringResource(R.string.config_result_close),
            busy = false, enabled = true, onClick = onDismiss,
        )
    }
}

@Composable
private fun PickerList(
    state: ProxyState,
    group: ProxyGroupState?,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (String, String) -> Unit,
    onCheck: (String) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val search = remember(density) { FloatingSearch(with(density) { 8.dp.toPx() }) }
    var searchHeight by remember(density) { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    val typed by rememberUpdatedState(query.isNotEmpty())
    val reveal = remember { Animatable(0f) }
    val nodes = remember(group?.proxies, query) {
        val needle = query.trim()
        val all = group?.proxies.orEmpty()
        if (needle.isEmpty()) all else all.filter { it.name.contains(needle, true) || it.type.contains(needle, true) }
    }
    LaunchedEffect(group?.name, query) { list.scrollToItem(0) }
    LaunchedEffect(search, list) {
        snapshotFlow {
            val atStart = list.firstVisibleItemIndex == 0
            val atTop = atStart && list.firstVisibleItemScrollOffset == 0
            val show = when {
                typed -> true
                atTop -> false
                // While its slot is visible, keep the current mode: direction jitter must not
                // switch between the natural slot and a floating position during a sheet drag.
                atStart -> reveal.targetValue == 1f
                else -> search.shown
            }
            atTop to show
        }.collectLatest { (atTop, show) ->
            if (atTop && !show) reveal.snapTo(0f)
            else reveal.animateTo(if (show) 1f else 0f, tween(180))
        }
    }
    fun Density.searchPlace(): Pair<Int, Int> {
        val info = list.layoutInfo
        val slot = info.visibleItemsInfo.firstOrNull { it.key == PICKER_SEARCH_KEY }
            ?.let { it.offset + info.beforeContentPadding }
            ?: Int.MIN_VALUE
        val hidden = -searchHeight - 1f
        return slot to (hidden + (12.dp.roundToPx() - hidden) * reveal.value).toInt()
    }
    Box(modifier.graphicsLayer { clip = true; shape = PickerViewport }.nestedScroll(search)) {
        val background = MaterialTheme.colorScheme.background
        LazyColumn(
            state = list, overscrollEffect = null,
            modifier = Modifier.fillMaxSize().selectableGroup().drawWithCache {
                val edge = 24.dp.toPx().coerceAtMost(size.height)
                val brush = Brush.verticalGradient(
                    0f to background.copy(alpha = 0f), 1f to background,
                    startY = size.height - edge, endY = size.height,
                )
                onDrawWithContent {
                    drawContent()
                    // Like ScreenFrame's top veil: one cached gradient, no live blur or offscreen buffer.
                    if (list.canScrollForward) {
                        val info = list.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()
                        val remaining = if (last != null && last.index == info.totalItemsCount - 1) {
                            (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).toFloat()
                        } else edge
                        drawRect(
                            brush, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge),
                            alpha = (remaining / edge).coerceIn(0f, 1f),
                        )
                    }
                }
            },
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = PICKER_SEARCH_KEY) { Spacer(Modifier.fillMaxWidth().height(with(density) { searchHeight.toDp() })) }
            if (group != null && !group.selectable) {
                item(key = "automatic") {
                    Text(stringResource(R.string.group_auto, group.proxies.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (nodes.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                        if (!state.loaded) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.proxy_search_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(nodes, key = { "node-${it.name}" }) { node ->
                    PickerNode(
                        node, selected = node.name == group?.now, enabled = group?.selectable == true,
                        onClick = { group?.let { onPick(it.name, node.name) } },
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().onSizeChanged { searchHeight = it.height.toFloat() }
                .offset { val (slot, floating) = searchPlace(); IntOffset(0, maxOf(slot, floating)) }
                .semantics { traversalIndex = -1f },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query, onQuery, stringResource(R.string.proxy_search), modifier = Modifier.weight(1f),
                compact = true,
            )
            ProxyCheckButton(
                checking = group != null && state.checking == group.name,
                enabled = group != null && state.canCheck && state.checking == null,
                onClick = { group?.let { onCheck(it.name) } }, size = 48.dp,
                compact = true,
            )
        }
    }
}

/** Keep the floating row inside the list while allowing card shadows to overhang sideways. */
private object PickerViewport : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val overhang = with(density) { 16.dp.toPx() }
        return Outline.Rectangle(Rect(-overhang, 0f, size.width + overhang, size.height))
    }
}

private const val PICKER_SEARCH_KEY = "picker-search"

@Composable
private fun PickerNode(node: NodeState, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val content = if (selected) scheme.onPrimary else scheme.onSurface
    val fill = if (selected) scheme.primary else scheme.surface
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .airShadow(shape, fill).clip(shape)
            .background(fill)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.favorite) Icon(Symbols.Star, contentDescription = stringResource(R.string.favorite), tint = content, modifier = Modifier.size(18.dp))
        Text(
            node.name.asName(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.named(),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = when {
                node.delay >= DelayStore.SLOW -> stringResource(R.string.delay_slow, node.delay / 1000)
                node.delay > 0 -> stringResource(R.string.delay_ms, node.delay)
                node.delay == DelayStore.NO_ANSWER -> stringResource(R.string.delay_failed)
                else -> stringResource(R.string.value_unknown)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (node.delay == DelayStore.NO_ANSWER) scheme.error else content.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}
