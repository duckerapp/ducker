package com.github.kr328.clash.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.github.kr328.clash.ui.components.machine
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * «Возможности» (stage 15): the tools that are not needed every day, behind one pill on Home so that Home stays a
 * button and a node: «Мобильная сеть» above them (D-423; not on a TV, which has no mobile network), «Подключения» and
 * «Раздача по Wi-Fi».
 */
@Composable
fun FeaturesScreen(
    share: ShareState,
    mobile: MobilePage,
    onMobile: () -> Unit,
    onConnections: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSharePassword: (Boolean) -> Unit,
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

        item { ShareCard(share, onShare, onSharePassword) }
    }
}

/**
 * «Раздача по Wi-Fi» (D-419–D-421): the switch, and while it is on what to enter on the second device, a tap copying
 * each. The password's switch shows only on a Wi-Fi that is not the phone's own: there it never asks one.
 */
@Composable
private fun ShareCard(state: ShareState, onTurn: (Boolean) -> Unit, onPassword: (Boolean) -> Unit) {
    val usable = state.running && !state.closed

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .toggleable(value = state.on, enabled = usable, role = Role.Switch, onValueChange = onTurn)
                .padding(16.dp),
        ) {
            // The status changes its length with the state; under the row it moves neither the icon nor the switch (D-464).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolIcon(Symbols.WifiTethering)

                Text(stringResource(R.string.share), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))

                Switch(checked = state.on, onCheckedChange = null, enabled = usable)
            }

            Text(
                text = stringResource(shareStatus(state)),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.stopped != null && !state.on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp),
            )
        }

        if (!state.on) return@Card

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(Modifier.padding(vertical = 8.dp)) {
            CopyRow(stringResource(R.string.share_address), state.address)
            CopyRow(stringResource(R.string.share_port), state.port.toString())

            if (!state.own) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(value = state.password, role = Role.Switch, onValueChange = onPassword)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.share_password), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(if (state.password) R.string.share_password_hint else R.string.share_password_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.password) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }

                    Switch(checked = state.password, onCheckedChange = null)
                }
            }

            if (state.locked) {
                CopyRow(stringResource(R.string.share_login), state.login)
                CopyRow(stringResource(R.string.share_secret), state.secret)
            }

            Text(
                text = stringResource(R.string.share_how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun shareStatus(state: ShareState): Int = when {
    !state.running -> R.string.share_needs_vpn
    state.closed -> R.string.share_closed
    state.on -> if (state.own) R.string.share_own else R.string.share_wifi
    state.stopped == ShareState.Stop.NetworkChanged -> R.string.share_network_changed
    state.stopped == ShareState.Stop.NoNetwork -> R.string.share_no_network
    state.stopped == ShareState.Stop.Failed -> R.string.share_failed
    else -> R.string.share_hint
}

/** A line to enter on the second device; a tap copies its value. */
@Composable
private fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    val copied = stringResource(R.string.share_copied)

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.share_copy)) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
                // Android 13+ already confirms clipboard copies itself.
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge.machine(), color = MaterialTheme.colorScheme.primary)
        Icon(Symbols.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
