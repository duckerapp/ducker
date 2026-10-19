package com.github.kr328.clash.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerCard as Card

private const val PROJECT_CHANNEL = "https://t.me/ducker_mihomo"
private const val PROJECT_GITHUB = "https://github.com/duckerapp/ducker"
private const val PROJECT_GITLAB = "https://gitlab.com/duckerx/ducker"

/** Empty addresses are deliberate owner-approved placeholders, never copyable payment data. */
internal data class DonationWallet(val currency: String, val network: String, val address: String = "")

internal val DonationWallets = listOf(
    DonationWallet("USDT", "TRC20"),
    DonationWallet("USDT", "ERC20"),
    DonationWallet("USDT", "TON"),
    DonationWallet("BTC", "Bitcoin"),
    DonationWallet("XMR", "Monero"),
    DonationWallet("TON", "TON"),
)

internal fun LazyListScope.projectSupportItems(onLink: (String) -> Unit, onMail: () -> Unit) {
    item(key = "thanks") {
        Text(
            stringResource(R.string.settings_project_thanks),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "community-heading") { ProjectSection(stringResource(R.string.settings_project_community)) }
    item(key = "telegram-channel") {
        ProjectLinkRow(stringResource(R.string.channel), Icons.AutoMirrored.Filled.Send, stringResource(R.string.channel_hint)) {
            onLink(PROJECT_CHANNEL)
        }
    }
    item(key = "telegram-community") {
        ProjectLinkRow(stringResource(R.string.community), Icons.Default.Forum, stringResource(R.string.community_hint)) {
            onLink(COMMUNITY)
        }
    }
    item(key = "source-heading") { ProjectSection(stringResource(R.string.settings_project_source)) }
    item(key = "github") { ProjectLinkRow("GitHub", Icons.Default.Code) { onLink(PROJECT_GITHUB) } }
    item(key = "gitlab") { ProjectLinkRow("GitLab", Icons.Default.Code) { onLink(PROJECT_GITLAB) } }
    item(key = "wallet-heading") { ProjectSection(stringResource(R.string.settings_project_crypto)) }
    DonationWallets.forEach { wallet ->
        item(key = "wallet-${wallet.currency}-${wallet.network}") { WalletCard(wallet) }
    }
    item(key = "mail") {
        Button(onClick = onMail, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.extraLarge) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.settings_project_mail), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ProjectSection(title: String) {
    Text(
        title, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProjectLinkRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WalletCard(wallet: DonationWallet) {
    val context = LocalContext.current
    val copied = stringResource(R.string.settings_wallet_copied)
    val copyLabel = stringResource(R.string.settings_wallet_copy, wallet.currency, wallet.network)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${wallet.currency} · ${wallet.network}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (wallet.address.isEmpty()) {
                    Text(stringResource(R.string.settings_wallet_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        Text(wallet.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            IconButton(
                enabled = wallet.address.isNotEmpty(),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("${wallet.currency} · ${wallet.network}", wallet.address))
                        // Android 13+ already confirms clipboard copies itself.
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyLabel, modifier = Modifier.size(22.dp))
            }
        }
    }
}
