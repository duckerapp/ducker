package com.github.kr328.clash.ui.features

import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.settings.SettingsGroupShape
import com.github.kr328.clash.ui.settings.SettingsRow
import com.github.kr328.clash.ui.settings.SettingsRowInset
import com.github.kr328.clash.ui.settings.SettingsSurface
import com.github.kr328.clash.ui.settings.settingsSupportingStyle
import com.github.kr328.clash.ui.settings.settingsTitleStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.common.util.formatSpeed
import com.github.kr328.clash.ui.apps.AppIcon
import com.github.kr328.clash.ui.apps.CountBesidePills
import com.github.kr328.clash.ui.components.GlassMaterial
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.glass
import com.github.kr328.clash.ui.components.glassPanesOn
import com.github.kr328.clash.ui.components.paneIndication
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.SearchField
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.formatBytes
import com.github.kr328.clash.ui.components.formatCount
import com.github.kr328.clash.ui.components.isolated
import com.github.kr328.clash.ui.components.machine
import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.smoothResource
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * «Подключения» (stage 15): which app goes where, by which rule and node, live. A tap narrows the list — to the app, then
 * to the site; «Сбросить все» closes them, and apps open new ones by the current rules — narrowed, only those shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionsScreen(
    state: ConnectionsState,
    /** The VPN is on: without it the core carries nothing to show. */
    running: Boolean,
    onPick: (ConnectionRow) -> Unit,
    onClearApp: () -> Unit,
    onClearSite: () -> Unit,
    onSearch: (String) -> Unit,
    onCloseAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val context = LocalContext.current
    // On a phone the settings pages' type and insets (the owner, 24.11); a TV keeps its own.
    val compact = !LocalTv.current

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = (if (compact) 10.dp else 16.dp) + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item { ScreenHeader(stringResource(R.string.connections), onBack = onBack) }

        if (!running) {
            item {
                if (compact) SettingsSurface(compact = true) {
                    SettingsRow(
                        stringResource(R.string.connections_off_title), compact = true,
                        subtitle = stringResource(R.string.connections_off), icon = Symbols.Lan,
                    )
                } else EmptyState(
                    icon = Symbols.Lan,
                    title = stringResource(R.string.connections_off_title),
                    message = stringResource(R.string.connections_off),
                )
            }

            return@TvLazyColumn
        }

        item {
            SearchField(value = state.query, onValue = onSearch, hint = stringResource(R.string.connections_search), glass = LocalGlassBackdrop.current)
        }

        item {
            // The speed on a line of its own, always there: changing every second beside the count, it moved the reset
            // from the line to under it and back, and the list with it.
            CountBesidePills(
                modifier = Modifier.padding(start = if (compact) SettingsRowInset else 4.dp),
                count = {
                    Column {
                        Text(
                            text = pluralStringResource(R.plurals.connections_count, state.count, formatCount(state.count)),
                            style = if (compact) settingsTitleStyle() else MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = context.formatSpeed(state.speed),
                            style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                pills = {
                    Pill(
                        text = stringResource(if (state.narrowed) R.string.connections_reset_shown else R.string.connections_reset),
                        onClick = onCloseAll,
                        icon = Symbols.RestartAlt,
                        enabled = if (state.narrowed) state.rows.any { !it.closed } else state.count > 0,
                    )
                },
            )
        }

        // The filter as pills with ✕: what narrowed the list, and the way back.
        if (state.app != null) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(text = appLabel(state.app).isolated(), onClick = onClearApp, selected = true, icon = Symbols.Close)
                    state.site?.let { Pill(text = it.isolated(), onClick = onClearSite, selected = true, icon = Symbols.Close) }
                }
            }
        } else if (state.rows.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.connections_tap_hint),
                    style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (compact) SettingsRowInset else 4.dp),
                )
            }
        }

        if (state.loaded && state.rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(if (state.query.isBlank()) R.string.connections_empty else R.string.proxy_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                )
            }
        }

        items(state.rows, key = { it.id }) { row ->
            // No fade: a fading item draws in a layer cut to its bounds, and the Air shadow under it came in with a jump at
            // the end, as on «Прокси».
            ConnectionCard(row, compact, onClick = { onPick(row) }, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
        }
    }
}

@Composable
private fun ConnectionCard(row: ConnectionRow, compact: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = if (compact) SettingsGroupShape else MaterialTheme.shapes.large,
        surfaceModifier = Modifier.clip(if (compact) SettingsGroupShape else MaterialTheme.shapes.large)
            .clickable(interactionSource = null, indication = paneIndication(), role = Role.Button, onClick = onClick),
        ownClick = true,
    ) {
        Row(
            // The card stays, its content fades: a closed one keeps its place and its shadow among the open ones.
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp).alpha(if (row.closed) CLOSED_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppMark(row.app)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appLabel(row.app),
                        style = if (compact) settingsTitleStyle() else MaterialTheme.typography.titleSmall,
                        fontWeight = if (compact) null else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The whole line but the speed: with a spacer beside it, a name had only half and was cut.
                        modifier = Modifier.weight(1f),
                    )

                    // Speed while it moves, otherwise how much went: a still connection is not «0 B/s».
                    Text(
                        text = when {
                            row.closed -> stringResource(R.string.connections_closed)
                            row.speed > 0 -> LocalContext.current.formatSpeed(row.speed)
                            else -> formatBytes(row.total)
                        },
                        style = MaterialTheme.typography.labelMedium.machine(),
                        color = if (row.speed > 0) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (row.port == 443 || row.port == 80 || row.port == 0) row.site else "${row.site}:${row.port}",
                        style = (if (compact) settingsSupportingStyle().copy(color = scheme.onSurface) else MaterialTheme.typography.bodyMedium).machine(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (row.udp) {
                        Text(
                            text = "UDP",
                            style = MaterialTheme.typography.labelSmall.machine(),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }

                if (row.route.isNotEmpty()) {
                    Text(
                        text = row.route.isolated(),
                        style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val CLOSED_ALPHA = 0.5f

/** The app's icon; ours for the core's own connections; a plain mark when Android named nobody. */
@Composable
private fun AppMark(app: ConnectionApp) {
    val size = 36.dp

    when {
        app.packageName != null && app.kind == ConnectionApp.Kind.App -> AppIcon(app.packageName, size, Modifier)
        app.kind == ConnectionApp.Kind.Core -> Image(
            bitmap = smoothResource(R.drawable.ducker_mark_dark, size),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)),
        )
        else -> Box(
            // On the pane glass a pane of the buttons' glass, as a rule's badge: a grey fill read as foreign there.
            modifier = Modifier.size(size).then(
                if (glassPanesOn()) Modifier.glass(LocalGlassBackdrop.current, GlassMaterial.Control, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                else Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (app.kind == ConnectionApp.Kind.Shared) Symbols.WifiTethering else Symbols.Lan,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun appLabel(app: ConnectionApp): String = when (app.kind) {
    ConnectionApp.Kind.App -> app.label
    ConnectionApp.Kind.Core -> stringResource(R.string.connections_core)
    ConnectionApp.Kind.Shared -> stringResource(R.string.connections_shared, app.label.isolated())
    ConnectionApp.Kind.Unknown -> stringResource(R.string.connections_unknown_app)
}
