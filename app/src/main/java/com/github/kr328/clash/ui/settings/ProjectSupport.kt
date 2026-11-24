package com.github.kr328.clash.ui.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Shape
import com.github.kr328.clash.ui.components.GlassCardButton
import com.github.kr328.clash.ui.components.GlassMaterial
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.glass
import com.github.kr328.clash.ui.components.glassPanesOn
import com.github.kr328.clash.ui.components.Symbols
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.smoothResource
import com.github.kr328.clash.ui.components.DuckerCard as Card

private const val PROJECT_NEWS = "https://t.me/ducker_news"
private const val PROJECT_GITHUB = "https://github.com/duckerapp/ducker"
private const val PROJECT_GITLAB = "https://gitlab.com/duckerx/ducker"
private const val PROJECT_CORE = "https://github.com/duckerapp/mihomo"
private const val PROJECT_LICENSE = "https://github.com/duckerapp/ducker/blob/main/LICENSE"
private const val AUTHOR = "duckwright"
private const val AUTHOR_TELEGRAM = "https://t.me/duckwright"
private const val AUTHOR_GITHUB = "https://github.com/duckwright"
private const val CRYPTOBOT = "https://t.me/send?start=IVviDBljozrs"

/** [label] goes into «only on the X network»; [title] adds the token standard people know it by. */
internal enum class DonationNetwork(val title: String, val label: String, val address: String) {
    Tron("Tron · TRC-20", "Tron", "TBDXcuLPSDebgB75g1sUtzKwNHBtHoAfpS"),
    Ton("TON", "TON", "UQBp9oJjEqvRrKoWfR3y1K-9I9DqZSVE3KabY8yAH44klxu6"),
    Solana("Solana", "Solana", "aCPg6Fjo8fuFkYqJg4HwGDW1TJwxWkpbgTz1ZLHjg6a"),
    Bnb("BNB Chain · BEP-20", "BNB Chain", "0xf60DB7297ed73B83073dABCd35fdBC8713a0F0D9"),
    Ethereum("Ethereum · ERC-20", "Ethereum", "0xf60DB7297ed73B83073dABCd35fdBC8713a0F0D9"),
    Bitcoin("Bitcoin", "Bitcoin", "bc1p70e6hugpakd2mplxd9gr4q8zkujw74snuqfyjtq6f5g7vmvnmweswsche8"),
    Litecoin("Litecoin", "Litecoin", "ltc1pe9tz6du20nydl7re8tqccq0h5xmjxs0utss49zm7tdvy99n4sk4q8rz9zk"),
    BitcoinCash("Bitcoin Cash", "Bitcoin Cash", "qq0eeefy5ulgf7lk76hptjyn443ywletp5w3eltqjv"),
    Zcash("Zcash", "Zcash", "t1VcMcq3kgpPC7rKbwN7AGDNeTdcvHjjwxr"),
}

/** The owner's wallets (D-523); USDT networks run from the cheapest transfer to the dearest (D-524). */
internal val DonationCoins: List<Pair<String, List<DonationNetwork>>> = listOf(
    "USDT" to listOf(DonationNetwork.Tron, DonationNetwork.Ton, DonationNetwork.Solana, DonationNetwork.Bnb, DonationNetwork.Ethereum),
    "USDC" to listOf(DonationNetwork.Solana, DonationNetwork.Ethereum),
    "BTC" to listOf(DonationNetwork.Bitcoin),
    "ETH" to listOf(DonationNetwork.Ethereum),
    "TON" to listOf(DonationNetwork.Ton),
    "SOL" to listOf(DonationNetwork.Solana),
    "TRX" to listOf(DonationNetwork.Tron),
    "BNB" to listOf(DonationNetwork.Bnb),
    "LTC" to listOf(DonationNetwork.Litecoin),
    "BCH" to listOf(DonationNetwork.BitcoinCash),
    "ZEC" to listOf(DonationNetwork.Zcash),
)

/** «О приложении» as the project's card (D-524). */
internal fun LazyListScope.aboutItems(
    appVersion: String, coreVersion: String, compact: Boolean, onLink: (String) -> Unit, onSupport: () -> Unit,
) {
    item(key = "about-card") { AboutCard(appVersion, coreVersion, compact, onSupport) }
    item(key = "about-community") {
        SettingsNavigationGroup(stringResource(R.string.settings_project_community), compact) {
            ProjectLinkRow(stringResource(R.string.channel), Symbols.Campaign, stringResource(R.string.channel_hint), compact) { onLink(PROJECT_NEWS) }
            SettingsNavigationDivider(compact)
            ProjectLinkRow(stringResource(R.string.community), Symbols.Forum, stringResource(if (compact) R.string.settings_community_short else R.string.community_hint), compact) { onLink(COMMUNITY) }
        }
    }
    item(key = "about-source") {
        SettingsNavigationGroup(stringResource(R.string.settings_project_source), compact) {
            ProjectLinkRow("GitHub", Symbols.Code, "duckerapp/ducker", compact) { onLink(PROJECT_GITHUB) }
            SettingsNavigationDivider(compact)
            ProjectLinkRow("GitLab", Symbols.Code, "duckerx/ducker", compact) { onLink(PROJECT_GITLAB) }
            SettingsNavigationDivider(compact)
            ProjectLinkRow(stringResource(R.string.about_core), Symbols.Lan, stringResource(R.string.about_core_hint), compact) { onLink(PROJECT_CORE) }
            SettingsNavigationDivider(compact)
            ProjectLinkRow(stringResource(R.string.about_license), Symbols.Article, stringResource(R.string.about_license_hint), compact) { onLink(PROJECT_LICENSE) }
        }
    }
    item(key = "about-author") {
        SettingsNavigationGroup(stringResource(R.string.about_author), compact) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = if (compact) SettingsRowInset else 16.dp, vertical = 10.dp),
                // The pills go under the name together when a large font leaves no room beside it.
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(AUTHOR, Modifier.padding(end = 8.dp), style = if (compact) settingsTitleStyle() else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Telegram", onClick = { onLink(AUTHOR_TELEGRAM) }, onCard = true)
                    Pill("GitHub", onClick = { onLink(AUTHOR_GITHUB) }, onCard = true)
                }
            }
        }
    }
}

/** «Поддержать проект»: only the ways to give (D-524). */
internal fun LazyListScope.projectSupportItems(compact: Boolean, onLink: (String) -> Unit, onMail: () -> Unit) {
    item(key = "thanks") {
        Text(
            stringResource(R.string.settings_project_thanks),
            modifier = Modifier.padding(horizontal = if (compact) SettingsRowInset else 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "cryptobot") { CryptoBotCard(compact) { onLink(CRYPTOBOT) } }
    item(key = "wallet") {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            SettingsSectionHeading(stringResource(R.string.donate_wallet), compact)
            WalletCard(compact)
        }
    }
    item(key = "mail") {
        if (compact) {
            SettingsSurface(compact = true) {
                SettingsNavigationRow(
                    stringResource(R.string.settings_project_mail), Symbols.Email, onMail,
                    compact = true, destinationIcon = Symbols.OpenInNew,
                )
            }
        } else {
            SupportButton(onClick = onMail, outlined = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Symbols.Email, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.settings_project_mail), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(appVersion: String, coreVersion: String, compact: Boolean, onSupport: () -> Unit) {
    val markSize = if (compact) 56.dp else 72.dp
    SettingsSurface(compact) {
        Column(
            Modifier.fillMaxWidth().then(
                if (compact) Modifier.padding(horizontal = SettingsRowInset, vertical = SettingsBodyInset)
                else Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) SettingsBodyInset else 14.dp),
        ) {
            // The tile as on the launcher (D-291): the mark of the other theme, so that it
            // stands out from the card instead of blending into it.
            Image(
                bitmap = smoothResource(
                    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.drawable.ducker_mark_light
                    else R.drawable.ducker_mark_dark,
                    markSize,
                ),
                contentDescription = null,
                modifier = Modifier.size(markSize).clip(RoundedCornerShape(percent = 22)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ducker", style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold)
                Text(
                    stringResource(R.string.about_tagline), textAlign = TextAlign.Center,
                    style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VersionChip(stringResource(R.string.version_app, appVersion))
                if (coreVersion.isNotEmpty()) VersionChip(stringResource(R.string.version_core, coreVersion))
            }
            SupportButton(onClick = onSupport, compact = compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Symbols.Favorite, contentDescription = null, modifier = Modifier.size(if (compact) SettingsIconSize else 20.dp))
                    Text(stringResource(R.string.settings_project), style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * A wide button: on the pane glass the glass buttons' look (Home's), whose solid fill stood out of the glass (the owner,
 * 23.11); elsewhere Material's as before, [outlined] or filled.
 */
@Composable
private fun SupportButton(
    onClick: () -> Unit, outlined: Boolean = false, compact: Boolean = false, content: @Composable RowScope.() -> Unit,
) {
    val height = when {
        compact -> SettingsMinTouch
        outlined -> 56.dp
        else -> 52.dp
    }
    val modifier = Modifier.fillMaxWidth().heightIn(min = height)
    when {
        glassPanesOn() -> GlassCardButton(onClick = onClick, modifier = modifier, padding = 16.dp, content = content)
        outlined -> OutlinedButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.extraLarge, content = content)
        else -> Button(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.extraLarge, content = content)
    }
}

/** A grey plate under [shape]; on the pane glass a pane of the buttons' glass, as the cards' icons. */
@Composable
private fun Modifier.plate(shape: Shape): Modifier =
    if (glassPanesOn()) glass(LocalGlassBackdrop.current, GlassMaterial.Control, shape).clip(shape)
    else clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)

@Composable
private fun VersionChip(text: String) {
    Text(
        text,
        Modifier.plate(RoundedCornerShape(percent = 50)).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun CryptoBotCard(compact: Boolean, onOpen: () -> Unit) {
    if (compact) {
        SettingsSurface(compact = true) {
            SettingsNavigationRow(
                title = stringResource(R.string.donate_cryptobot_open),
                subtitle = stringResource(R.string.donate_cryptobot_hint),
                icon = Symbols.SmartToy, onClick = onOpen, compact = true, destinationIcon = Symbols.OpenInNew,
            )
        }
        return
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(44.dp).plate(MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { Icon(Symbols.SmartToy, contentDescription = null, modifier = Modifier.size(24.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.donate_cryptobot), Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.donate_cryptobot_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SupportButton(onClick = onOpen) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.donate_cryptobot_open), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Icon(Symbols.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletCard(compact: Boolean) {
    val context = LocalContext.current
    val copied = stringResource(R.string.settings_wallet_copied)
    var coinIndex by rememberSaveable { mutableIntStateOf(0) }
    var networkIndex by rememberSaveable { mutableIntStateOf(0) }
    val (coin, networks) = DonationCoins[coinIndex]
    val network = networks[networkIndex.coerceIn(networks.indices)]

    SettingsSurface(compact) {
        Column(
            if (compact) Modifier.padding(horizontal = SettingsRowInset, vertical = SettingsBodyInset)
            else Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsBodyInset),
        ) {
            WalletLabel(stringResource(R.string.donate_coin))
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DonationCoins.forEachIndexed { index, (symbol, _) ->
                    Pill(
                        symbol, onClick = { coinIndex = index; networkIndex = 0 },
                        selected = index == coinIndex, onCard = true, role = Role.RadioButton,
                    )
                }
            }
            if (networks.size > 1) {
                WalletLabel(stringResource(R.string.donate_network))
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    networks.forEachIndexed { index, it ->
                        Pill(
                            it.title, onClick = { networkIndex = index },
                            selected = it == network, onCard = true, role = Role.RadioButton,
                        )
                    }
                }
            }
            SelectionContainer {
                Text(
                    network.address,
                    Modifier.fillMaxWidth().plate(if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium)
                        .padding(horizontal = SettingsRowInset, vertical = SettingsBodyInset),
                    style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Symbols.Info, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.donate_only_network, coin, network.label),
                    style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SupportButton(
                compact = compact,
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("$coin · ${network.label}", network.address))
                        // Android 13+ already confirms clipboard copies itself.
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Symbols.ContentCopy, contentDescription = null, modifier = Modifier.size(if (compact) SettingsIconSize else 20.dp))
                    Text(stringResource(R.string.donate_copy), style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun WalletLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A row of a settings group that opens a page outside the app. */
@Composable
private fun ProjectLinkRow(title: String, icon: ImageVector, subtitle: String, compact: Boolean, onClick: () -> Unit) {
    SettingsNavigationRow(
        title, icon, onClick, subtitle = subtitle, compact = compact, destinationIcon = Symbols.OpenInNew,
    )
}
