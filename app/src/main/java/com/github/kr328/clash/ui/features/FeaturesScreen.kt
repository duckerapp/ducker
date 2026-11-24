package com.github.kr328.clash.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.ShareState
import com.github.kr328.clash.ui.components.paneIndication
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.DuckerCard as Card
import com.github.kr328.clash.ui.settings.SettingsGroupGap
import com.github.kr328.clash.ui.settings.SettingsNavigationDivider
import com.github.kr328.clash.ui.settings.SettingsNavigationGroup
import com.github.kr328.clash.ui.settings.SettingsNavigationRow

/**
 * «Возможности» (stage 15): the tools that are not needed every day, behind one pill on Home so that Home stays a
 * button and a node: «Мобильная сеть» above them (D-423; not on a TV, which has no mobile network), «Подключения» and
 * «Раздача по Wi-Fi» (D-465), each a row opening its own page.
 */
@Composable
fun FeaturesScreen(
    share: ShareState,
    mobile: MobilePage,
    onMobile: () -> Unit,
    onConnections: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val tv = LocalTv.current

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        // On a phone the settings pages' spacing and rows, so the two read as one (the owner, 24.11).
        verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else SettingsGroupGap),
        contentPadding = PaddingValues(top = (if (tv) 16.dp else 10.dp) + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item { ScreenHeader(stringResource(R.string.features), onBack = onBack) }

        // The card's own short line where the page would explain the feature (the owner, 24.11).
        val shareHint = shareStatus(share).let { if (it == R.string.share_hint) R.string.features_share_short else it }

        if (!tv) {
            item {
                val settings = mobile.settings
                val name = mobile.profileName?.asName()
                val mobileHint = when {
                    !settings.on -> stringResource(R.string.mobile_card_off)
                    name == null -> stringResource(R.string.mobile_card_no_profile)
                    settings.nodes.isEmpty() -> name
                    else -> pluralStringResource(R.plurals.mobile_card_nodes, settings.nodes.size, name, settings.nodes.size)
                }
                SettingsNavigationGroup(null, compact = true) {
                    SettingsNavigationRow(stringResource(R.string.mobile), Symbols.CellWifi, onMobile, subtitle = mobileHint, compact = true)
                    SettingsNavigationDivider(compact = true)
                    SettingsNavigationRow(
                        stringResource(R.string.connections), Symbols.Lan, onConnections,
                        subtitle = stringResource(R.string.connections_hint), compact = true,
                    )
                    SettingsNavigationDivider(compact = true)
                    SettingsNavigationRow(stringResource(R.string.share), Symbols.WifiTethering, onShare, subtitle = stringResource(shareHint), compact = true)
                }
            }
        } else {
            item {
                ToolRow(Symbols.Lan, stringResource(R.string.connections), stringResource(R.string.connections_hint), onConnections)
            }

            item {
                ToolRow(Symbols.WifiTethering, stringResource(R.string.share), stringResource(shareHint), onShare)
            }
        }
    }
}

/** As the settings' rows have it: a plain icon, no tile — the page is part of the app, not a launcher of its own. */
@Composable
private fun ToolIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = null, indication = paneIndication(), role = Role.Button, onClick = onClick),
        ownClick = true,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolIcon(icon)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Icon(Symbols.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
