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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item { ScreenHeader(stringResource(R.string.features), onBack = onBack) }

        if (!tv) {
            item {
                val settings = mobile.settings
                val name = mobile.profileName?.asName()

                ToolRow(
                    icon = Symbols.CellWifi,
                    title = stringResource(R.string.mobile),
                    hint = when {
                        !settings.on -> stringResource(R.string.mobile_card_off)
                        name == null -> stringResource(R.string.mobile_card_no_profile)
                        settings.nodes.isEmpty() -> name
                        else -> pluralStringResource(R.plurals.mobile_card_nodes, settings.nodes.size, name, settings.nodes.size)
                    },
                    onClick = onMobile,
                )
            }
        }

        item {
            ToolRow(
                icon = Symbols.Lan,
                title = stringResource(R.string.connections),
                hint = stringResource(R.string.connections_hint),
                onClick = onConnections,
            )
        }

        item {
            ToolRow(
                icon = Symbols.WifiTethering,
                title = stringResource(R.string.share),
                hint = stringResource(shareStatus(share)),
                onClick = onShare,
            )
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
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick),
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
