package com.github.kr328.clash.ui.features

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.NetworkKind
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.subscriptions.ProfileReorder
import com.github.kr328.clash.ui.subscriptions.reorderable
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * «Мобильная сеть» (D-423, D-424): the switch, Wi-Fi as it is and the subscription for the mobile network, then its
 * nodes in the order they are tried — dragged by a long press as subscriptions are, moved by TalkBack's actions.
 */
@Composable
fun MobileScreen(
    state: MobilePage,
    onTurn: (Boolean) -> Unit,
    onProfile: (String) -> Unit,
    onNodes: (List<String>) -> Unit,
    onMove: (from: String, to: String) -> Unit,
    onMoved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val moved by rememberUpdatedState(onMoved)
    val reorder = remember(list) {
        ProfileReorder(
            list = list,
            scope = scope,
            sameGroup = { a, b -> a is String && b is String && a.startsWith(NODE) && b.startsWith(NODE) },
            onMove = { from, to -> move(from.removePrefix(NODE), to.removePrefix(NODE)) },
            onDrop = { moved() },
            putDown = LIFT_MS,
        )
    }

    // Each sheet stays composed while it slides away: [*Open] drives it, [*Shown] keeps it.
    var profilesOpen by remember { mutableStateOf(false) }
    var profilesShown by remember { mutableStateOf(false) }
    var nodesOpen by remember { mutableStateOf(false) }
    var nodesShown by remember { mutableStateOf(false) }

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item { ScreenHeader(stringResource(R.string.mobile), onBack = onBack) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(value = settings.on, enabled = state.loaded, role = Role.Switch, onValueChange = onTurn)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.mobile_switch), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.mobile_switch_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(checked = settings.on, onCheckedChange = null, enabled = state.loaded)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                NetworkRow(
                    icon = Symbols.Wifi,
                    filled = false,
                    title = stringResource(R.string.mobile_wifi),
                    hint = state.wifiName?.let { stringResource(R.string.mobile_wifi_hint, it.asName()) }
                        ?: stringResource(R.string.mobile_wifi_none),
                    now = settings.now == NetworkKind.Wifi,
                    onClick = null,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 70.dp))

                NetworkRow(
                    icon = Symbols.SignalCellularAlt,
                    filled = true,
                    title = stringResource(R.string.mobile),
                    hint = state.profileName?.let { stringResource(R.string.mobile_profile, it.asName()) }
                        ?: stringResource(R.string.mobile_profile_none),
                    now = settings.now == NetworkKind.Mobile,
                    onClick = if (state.loaded) ({ profilesOpen = true; profilesShown = true }) else null,
                )
            }
        }

        item {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.mobile_nodes), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (settings.nodes.isEmpty()) R.string.mobile_nodes_none else R.string.mobile_nodes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val nodes = settings.nodes

        items(nodes, key = { NODE + it }) { name ->
            val key = NODE + name
            val index = nodes.indexOf(name)
            val up = stringResource(R.string.move_up)
            val down = stringResource(R.string.move_down)

            NodeRow(
                number = index + 1,
                name = name,
                missing = state.members?.let { name !in it } == true,
                reorder = reorder,
                key = key,
                onLift = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onStep = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                // TalkBack does not drag: the same moves as actions.
                moves = listOfNotNull(
                    nodes.getOrNull(index - 1)?.let { above ->
                        CustomAccessibilityAction(up) { onMove(name, above); onMoved(); true }
                    },
                    nodes.getOrNull(index + 1)?.let { below ->
                        CustomAccessibilityAction(down) { onMove(name, below); onMoved(); true }
                    },
                ),
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = if (reorder.active && reorder.dragged != key && reorder.settling != key) {
                        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                    } else null,
                ),
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
                    .clickable(enabled = state.profileName != null, role = Role.Button) { nodesOpen = true; nodesShown = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val color = if (state.profileName != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                    Icon(Symbols.Checklist, contentDescription = null, tint = color)
                    Text(stringResource(R.string.mobile_nodes_pick), style = MaterialTheme.typography.bodyLarge, color = color)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.mobile_rule_pick),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.mobile_rule_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (profilesShown) {
        ProfileSheet(
            open = profilesOpen,
            state = state,
            onPick = {
                onProfile(it)
                profilesOpen = false
            },
            onDismiss = { profilesOpen = false },
            onClosed = { profilesShown = false },
        )
    }

    if (nodesShown) {
        NodesSheet(
            open = nodesOpen,
            state = state,
            onDone = {
                onNodes(it)
                nodesOpen = false
            },
            onClosed = { nodesShown = false },
        )
    }
}

@Composable
private fun NetworkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    title: String,
    hint: String,
    now: Boolean,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (filled) scheme.primaryContainer else scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (filled) scheme.onPrimaryContainer else scheme.onSurface)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)

                if (now) {
                    Text(
                        stringResource(R.string.mobile_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(scheme.primary)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }

            Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (onClick != null) Icon(Symbols.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

/** A node of the list: its number, its name, and the handle a long press drags it by. */
@Composable
private fun NodeRow(
    number: Int,
    name: String,
    missing: Boolean,
    reorder: ProfileReorder,
    key: String,
    onLift: () -> Unit,
    onStep: () -> Unit,
    moves: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val lifted = reorder.dragged == key
    val up = lifted || (reorder.settling == key && reorder.arrived != key)
    val lift by animateFloatAsState(if (up) 1f else 0f, tween(LIFT_MS), label = "lift")
    val raised = lifted || reorder.settling == key || lift > 0f

    Box(modifier.fillMaxWidth().zIndex(if (raised) 1f else 0f)) {
        Card(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                translationY = reorder.offset(key)
                scaleX = 1f + LIFT_SCALE * lift
                scaleY = scaleX
            },
            surfaceModifier = Modifier.semantics { customActions = moves }
                .reorderable(reorder, key, scale = { 1f + LIFT_SCALE * lift }, onLift = onLift, onStep = onStep),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimary)
                }

                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(
                        name.asName(),
                        style = MaterialTheme.typography.bodyLarge.named(),
                        color = if (missing) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // The provider renamed or removed it: it is skipped until it comes back.
                    if (missing) {
                        Text(stringResource(R.string.mobile_node_missing), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                }

                Icon(
                    Symbols.DragIndicator,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** The subscription for the mobile network: a tap picks it and closes the sheet. */
@Composable
private fun ProfileSheet(
    open: Boolean,
    state: MobilePage,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.mobile_pick_profile),
        hint = if (state.profiles.isEmpty()) stringResource(R.string.mobile_no_profiles) else null,
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        state.profiles.forEach { profile ->
            val selected = profile.uuid == state.settings.profile

            ChoiceRow(
                name = profile.name,
                checked = selected,
                modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onPick(profile.uuid) },
            )
        }
    }
}

/**
 * The subscription's nodes to tick. A tick puts the node at the end of the list, an untick takes it off; the order is
 * changed on the page. Closed any way — «Готово», back or a swipe — the ticks are kept: nothing is lost by a gesture.
 */
@Composable
private fun NodesSheet(
    open: Boolean,
    state: MobilePage,
    onDone: (List<String>) -> Unit,
    onClosed: () -> Unit,
) {
    val picked = remember { mutableStateListOf<String>().apply { addAll(state.settings.nodes) } }
    val members = state.members
    val shortWindow = LocalConfiguration.current.screenHeightDp < 480
    val done = { onDone(picked.toList()) }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.mobile_pick_nodes),
        hint = when {
            members == null -> null
            members.isEmpty() -> stringResource(R.string.mobile_pick_nodes_empty)
            else -> stringResource(R.string.mobile_pick_nodes_hint, state.profileName.orEmpty().asName())
        },
        busy = false,
        onDismiss = done,
        onClosed = onClosed,
        scrollable = false,
        compact = true,
        heightFraction = if (shortWindow) 1f else 0.80f,
    ) {
        // A list entry the subscription no longer has stays tickable, so it can be taken off here too.
        val shown = members.orEmpty() + picked.filter { it !in members.orEmpty() }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(shown, key = { it }) { name ->
                val checked = name in picked

                ChoiceRow(
                    name = name,
                    checked = checked,
                    modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) { on ->
                        if (on) picked.add(name) else picked.remove(name)
                    },
                )
            }
        }

        SheetButton(
            text = stringResource(R.string.done),
            busyText = stringResource(R.string.done),
            busy = false,
            enabled = true,
            onClick = done,
        )
    }
}

@Composable
private fun ChoiceRow(name: String, checked: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            if (checked) Symbols.CheckCircle else Symbols.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
        )
        Text(
            name.asName(),
            style = MaterialTheme.typography.bodyLarge.named(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** List keys of the nodes: one drag group, apart from the page's other items. */
private const val NODE = "node:"

private const val LIFT_MS = 150
private const val LIFT_SCALE = 0.03f
