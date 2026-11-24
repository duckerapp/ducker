package com.github.kr328.clash.ui.features

import com.github.kr328.clash.ui.components.accentMarkInk
import com.github.kr328.clash.ui.components.accentMark
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.GlassMaterial
import com.github.kr328.clash.ui.components.glass
import com.github.kr328.clash.ui.components.glassPress
import com.github.kr328.clash.ui.components.glassPressOwnTouch
import com.github.kr328.clash.ui.components.rememberGlassPress
import com.github.kr328.clash.ui.components.paneIndication
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.NetworkKind
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.airShadow
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.subscriptions.ProfileReorder
import com.github.kr328.clash.ui.subscriptions.reorderable
import com.github.kr328.clash.ui.components.DuckerCard as Card
import androidx.compose.ui.text.style.TextAlign
import com.github.kr328.clash.ui.settings.SettingsChevron
import com.github.kr328.clash.ui.settings.SettingsGroupShape
import com.github.kr328.clash.ui.settings.SettingsIconGap
import com.github.kr328.clash.ui.settings.SettingsIconSize
import com.github.kr328.clash.ui.settings.SettingsNavigationDivider
import com.github.kr328.clash.ui.settings.SettingsNavigationRow
import com.github.kr328.clash.ui.settings.SettingsRowHeight
import com.github.kr328.clash.ui.settings.SettingsRowInset
import com.github.kr328.clash.ui.settings.SettingsSectionHeading
import com.github.kr328.clash.ui.settings.SettingsSurface
import com.github.kr328.clash.ui.settings.SettingsSwitchRow
import com.github.kr328.clash.ui.settings.SettingsTextGap
import com.github.kr328.clash.ui.settings.settingsSupportingStyle
import com.github.kr328.clash.ui.settings.settingsTitleStyle

/**
 * «Мобильная сеть» (D-423, D-424), built from the settings' parts so it reads as part of the app: the switch as a
 * settings switch card, the two networks as a settings group, the nodes as the cards «Подписки» drags — by a long
 * press, or by TalkBack's actions.
 */
@Composable
fun MobileScreen(
    state: MobilePage,
    onTurn: (Boolean) -> Unit,
    onProvider: (Boolean) -> Unit,
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
    val pickProfile = { profilesOpen = true; profilesShown = true }

    // As the settings' pages: no rubber edge (D-137 keeps it for long lists).
    TvLazyColumn(
        state = list,
        overscrollEffect = null,
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // The settings pages' top, rows and type (the owner, 24.11).
        contentPadding = PaddingValues(top = 10.dp + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item { ScreenHeader(stringResource(R.string.mobile), onBack = onBack) }

        item {
            // On with no subscription to switch to: the choice comes first, the switch follows it.
            val turn: (Boolean) -> Unit = { on -> if (on && state.profileName == null) pickProfile() else onTurn(on) }
            SettingsSurface(compact = true) {
                SettingsSwitchRow(
                    stringResource(R.string.mobile_switch), stringResource(R.string.mobile_switch_hint), Symbols.CellWifi,
                    checked = settings.on, onChange = turn, compact = true, enabled = state.loaded,
                )
            }
        }

        item {
            SettingsSurface(compact = true) {
                NetworkRow(
                    icon = Symbols.Wifi,
                    title = stringResource(R.string.mobile_wifi),
                    hint = state.wifiName?.let { stringResource(R.string.mobile_wifi_hint, it.asName()) }
                        ?: stringResource(R.string.mobile_wifi_none),
                    now = settings.now == NetworkKind.Wifi,
                    onClick = null,
                )

                SettingsNavigationDivider(compact = true)

                NetworkRow(
                    icon = Symbols.SignalCellularAlt,
                    title = stringResource(R.string.mobile),
                    hint = state.profileName?.let { stringResource(R.string.mobile_profile, it.asName()) }
                        ?: stringResource(R.string.mobile_profile_none),
                    now = settings.now == NetworkKind.Mobile,
                    onClick = if (state.loaded) pickProfile else null,
                )
            }
        }

        // Only when the chosen subscription brings a provider's list (D-534): an edit turns it off, a tap brings it back.
        if (settings.preset) item {
            SettingsSurface(compact = true) {
                SettingsSwitchRow(
                    stringResource(R.string.mobile_provider),
                    stringResource(if (settings.provider) R.string.mobile_provider_on else R.string.mobile_provider_off),
                    Symbols.Checklist, checked = settings.provider, onChange = onProvider, compact = true, enabled = state.loaded,
                )
            }
        }

        item {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(SettingsTextGap)) {
                SettingsSectionHeading(stringResource(R.string.mobile_nodes), compact = true)
                Text(
                    stringResource(if (settings.nodes.isEmpty()) R.string.mobile_nodes_none else R.string.mobile_nodes_hint),
                    modifier = Modifier.padding(horizontal = SettingsRowInset),
                    style = settingsSupportingStyle(),
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
                // No fade: a fading item is drawn cut to its bounds, and its Air shadow would come in with a jump.
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
            SettingsSurface(compact = true) {
                SettingsNavigationRow(
                    stringResource(R.string.mobile_nodes_pick), Symbols.Checklist, { nodesOpen = true; nodesShown = true },
                    compact = true, enabled = state.profileName != null,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = SettingsRowInset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.mobile_rule_pick),
                    style = settingsSupportingStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.mobile_rule_time),
                    style = settingsSupportingStyle(),
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

/** A row of the networks' group, measured as a settings group's row. «Сейчас» stands where a row's value does. */
@Composable
private fun NetworkRow(icon: ImageVector, title: String, hint: String, now: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = SettingsRowHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = SettingsRowInset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsIconGap),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(SettingsIconSize), tint = scheme.onSurfaceVariant)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsTextGap)) {
            // «Сейчас» as a filled bubble right beside the network's name: at the row's end it read as nobody's.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = settingsTitleStyle(), modifier = Modifier.weight(1f, fill = false))

                if (now) {
                    Text(
                        stringResource(R.string.mobile_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentMarkInk(),
                        maxLines = 1,
                        modifier = Modifier.accentMark(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Text(hint, style = settingsSupportingStyle(), color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (onClick != null) {
            SettingsChevron(Symbols.KeyboardArrowRight)
        } else {
            // The row without an arrow keeps its text where the other row's is.
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** A node of the list: its place, its name, and the handle a long press drags it by — as a subscription's row. */
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

    // The pane glass: the row swells under a finger, let go at the lift, as a subscription's row.
    val press = rememberGlassPress()

    Box(modifier.fillMaxWidth().zIndex(if (raised) 1f else 0f)) {
        Card(
            shape = SettingsGroupShape,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                translationY = reorder.offset(key)
                scaleX = 1f + LIFT_SCALE * lift
                scaleY = scaleX
            },
            press = press,
            surfaceModifier = Modifier.semantics { customActions = moves }
                // Outside the reordering, which takes the whole gesture once the row is lifted.
                .glassPressOwnTouch(press)
                .reorderable(reorder, key, scale = { 1f + LIFT_SCALE * lift }, onLift = onLift, onStep = onStep),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = SettingsRowHeight).padding(start = SettingsRowInset, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsIconGap),
            ) {
                // In the icons' column, so the names stand on the labels' line.
                Text(
                    number.toString(),
                    style = settingsTitleStyle(),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = SettingsIconSize),
                )

                Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(SettingsTextGap)) {
                    Text(
                        name.asName(),
                        style = settingsTitleStyle().named(),
                        color = if (missing) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // The provider renamed or removed it: it is skipped until it comes back.
                    if (missing) {
                        Text(stringResource(R.string.mobile_node_missing), style = settingsSupportingStyle(), color = scheme.error)
                    }
                }

                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Symbols.DragIndicator, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(SettingsIconSize))
                }
            }
        }
    }
}

/**
 * The subscription for the mobile network: the node sheet as it is, «Готово» included, with one tick at most — a tick
 * moves the choice, an untick leaves none. Closed any way the choice is kept, as the nodes' ticks are.
 */
@Composable
private fun ProfileSheet(
    open: Boolean,
    state: MobilePage,
    onPick: (String) -> Unit,
    onClosed: () -> Unit,
) {
    var picked by remember { mutableStateOf(state.settings.profile) }
    val shortWindow = LocalConfiguration.current.screenHeightDp < 480
    val done = { onPick(picked) }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.mobile),
        hint = if (state.profiles.isEmpty()) stringResource(R.string.mobile_no_profiles) else null,
        busy = false,
        onDismiss = done,
        onClosed = onClosed,
        scrollable = false,
        compact = true,
        heightFraction = if (shortWindow) 1f else 0.80f,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.profiles, key = { it.uuid }) { profile ->
                NodeChoice(
                    name = profile.name,
                    checked = profile.uuid == picked,
                    favorite = profile.favorite,
                    note = if (profile.active) stringResource(R.string.ducker_active) else null,
                ) { on ->
                    picked = if (on) profile.uuid else ""
                }
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

/**
 * The subscription's nodes to tick, as Home's node sheet lays them out: a card each, 10 dp apart, a ticked one filled
 * as the chosen node is there. A tick puts the node at the end of the list, an untick takes it off; the order is
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
        // A list entry the subscription no longer has stays here, so it can be unticked too.
        val shown = members.orEmpty() + picked.filter { it !in members.orEmpty() }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it }) { name ->
                val checked = name in picked

                NodeChoice(name = name, checked = checked) { on -> if (on) picked.add(name) else picked.remove(name) }
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

/**
 * A node card of the sheet, as Home's node sheet draws one; filled while ticked. A subscription carries the marks
 * «Подписки» gives it: the star of a favourite, «Активна» under the active one's name.
 */
@Composable
private fun NodeChoice(name: String, checked: Boolean, favorite: Boolean = false, note: String? = null, onCheck: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val content = if (checked) scheme.onPrimary else scheme.onSurface
    val fill = if (checked) scheme.primary else scheme.surface
    // On a glass sheet as Home's node sheet's rows: a control of glass, the ticked one keeping its fill; swelling.
    val backdrop = LocalGlassBackdrop.current
    val glass = backdrop != null && !checked
    val press = rememberGlassPress()

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .glassPress(press, own = true)
            .then(if (glass) Modifier.glass(backdrop, GlassMaterial.Control, shape, press = press) else Modifier.airShadow(shape, fill))
            .clip(shape)
            .then(if (glass) Modifier else Modifier.background(fill))
            .toggleable(value = checked, interactionSource = null, indication = paneIndication(), role = Role.Checkbox, onValueChange = onCheck)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name.asName(),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge.named(),
                    fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (favorite) {
                    Icon(Symbols.Star, contentDescription = stringResource(R.string.favorite_marked), tint = content, modifier = Modifier.size(14.dp))
                }
            }

            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.7f))
        }

        if (checked) Icon(Symbols.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/** List keys of the nodes: one drag group, apart from the page's other items. */
private const val NODE = "node:"

private const val LIFT_MS = 150
private const val LIFT_SCALE = 0.03f
