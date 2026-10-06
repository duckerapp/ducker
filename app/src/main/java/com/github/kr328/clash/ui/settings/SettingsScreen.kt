package com.github.kr328.clash.ui.settings

import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.github.kr328.clash.R
import com.github.kr328.clash.util.Shortcut
import com.github.kr328.clash.util.shortcuts
import com.github.kr328.clash.util.Vendor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.CheckCircle
import com.github.kr328.clash.ui.components.BrandAvatar
import com.github.kr328.clash.ui.components.IconBadge
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.DuckerCard as Card

data class SettingsState(
    val theme: String = SettingsStore.THEME_SYSTEM,
    /** Пустая строка — язык системы. */
    val language: String = "",
    val oled: Boolean = false,
    val dynamicColor: Boolean = false,
    val allowExternalControl: Boolean = false,
    val ipv6: Boolean = false,
    /** Уровень журнала ядра поднят до debug — иначе разбирать нечего. */
    val debugLog: Boolean = false,
    val appVersion: String = "",
    val coreVersion: String = "",

    // Расширенное: то, что обычному человеку трогать незачем, но иногда надо.
    /** `VpnService.allowBypass()` — приложение может осознанно уйти мимо туннеля. */
    val allowBypass: Boolean = false,
    /** HTTP-прокси в свойствах VPN-сети. */
    val systemProxy: Boolean = false,
    /** Ловить DNS-запросы мимо ядра (`any:53` вместо адреса туннеля). */
    val dnsHijacking: Boolean = true,
    /** Не пускать в туннель адреса домашней сети. */
    val bypassPrivate: Boolean = true,
    /** Выбор человека: пусто — как в профиле, иначе он главнее профиля (D-86). */
    val stack: String = "",
    /** Что задал провайдер (`tun.stack`); пусто — ничего (D-122). */
    val profileStack: String = "",
    /** Поднимать туннель после перезагрузки и обновления. */
    val autoRestart: Boolean = false,
    /** Android держит ducker «Постоянным VPN» (этап 10). */
    val alwaysOn: Boolean = false,
    val hideRecents: Boolean = false,
    /** На рабочем столе — «Калькулятор» (D-147). */
    val disguise: Boolean = false,
    /** Показывать скорость в уведомлении — будит процесс раз в секунду. */
    val trafficNotification: Boolean = false,
    /** Android не усыпляет ducker: он в исключениях экономии батареи (D-106). */
    val backgroundAllowed: Boolean = true,
)

/** Канал с выпусками и чат сообщества — строки в разделе «Помощь». */
private const val CHANNEL = "https://t.me/ducker_mihomo"
private const val COMMUNITY = "https://t.me/ducker_community"

/**
 * Свои настройки (этап 5).
 *
 * Здесь только то, что человек трогает: вид, режим работы VPN, IPv6 и обход приложений.
 * Тонкая настройка ядра (DNS, оверрайды, метаданные) остаётся на экранах CMFA — там она
 * уже сделана, и повторять её ради одинакового вида незачем.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onOled: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onExternalControl: (Boolean) -> Unit,
    onIpv6: (Boolean) -> Unit,
    onAccessControl: () -> Unit,
    /** `null` — система не умеет предлагать плитку (до Android 13), строки нет. */
    onAddTile: (() -> Unit)?,
    onAllowBypass: (Boolean) -> Unit,
    onSystemProxy: (Boolean) -> Unit,
    onDnsHijacking: (Boolean) -> Unit,
    onBypassPrivate: (Boolean) -> Unit,
    onStack: (String) -> Unit,
    onAutoRestart: (Boolean) -> Unit,
    /** Настройки VPN в Android — там включают «Постоянный VPN». */
    onAlwaysOn: () -> Unit,
    onTrafficNotification: (Boolean) -> Unit,
    onHideRecents: (Boolean) -> Unit,
    onDisguise: (Boolean) -> Unit,
    onLogs: () -> Unit,
    onProbe: () -> Unit,
    onDashboard: () -> Unit,
    onLink: (String) -> Unit,
    onAllowBackground: () -> Unit,
    /** Экран оболочки; `null` — страница приложения. */
    onVendor: (Shortcut?) -> Unit,
    hwid: String,
    sendHwid: Boolean,
    onSendHwid: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        // Пульт (этап 6): список помнит, где стоял фокус, — Compose помнит по уровню на список.
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp)
            .then(if (LocalTv.current) Modifier.focusRestorer() else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            ScreenHeader(stringResource(R.string.settings), onBack = onBack)
        }

        // Порядок — по тому, что человеку нужно чаще (D-122): сначала то, от чего каждый день
        // зависит работа VPN, потом вид и приватность; сетевые тонкости свёрнуты «для
        // знающих», журнал и диагностика — отдельным разделом в конце.
        item { SectionTitle(stringResource(R.string.section_connection)) }

        item {
            BackgroundCard(state.backgroundAllowed, onAllowBackground, onVendor)
        }

        item {
            LinkRow(
                title = stringResource(R.string.access_control),
                subtitle = stringResource(R.string.access_control_hint),
                onClick = onAccessControl,
                icon = Icons.Default.Apps,
            )
        }

        // Плитка в быстрых настройках есть давно, но найти её в редакторе шторки умеют
        // немногие. С Android 13 система может предложить добавить её сама (D-87).
        if (onAddTile != null) {
            item {
                LinkRow(
                    title = stringResource(R.string.add_tile),
                    subtitle = stringResource(R.string.add_tile_hint),
                    onClick = onAddTile,
                    icon = Icons.Default.ToggleOn,
                )
            }
        }

        item {
            SwitchRow(
                title = stringResource(R.string.auto_restart),
                subtitle = stringResource(R.string.auto_restart_hint),
                checked = state.autoRestart,
                onChange = onAutoRestart,
                icon = Icons.Default.RestartAlt,
            )
        }

        // Автозапуск ждёт своей очереди среди событий загрузки — на слабом телефоне полторы
        // минуты (D-128). Сразу поднимает только «Постоянный VPN» Android (этап 10).
        item {
            LinkRow(
                title = stringResource(R.string.always_on),
                subtitle = stringResource(if (state.alwaysOn) R.string.always_on_enabled else R.string.always_on_hint),
                onClick = onAlwaysOn,
                icon = Icons.Default.VpnLock,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.traffic_notification),
                subtitle = stringResource(R.string.traffic_notification_hint),
                checked = state.trafficNotification,
                onChange = onTrafficNotification,
                icon = Icons.Default.Notifications,
            )
        }

        item { SectionTitle(stringResource(R.string.section_look)) }

        item {
            ChipsCard(
                title = stringResource(R.string.theme),
                icon = Icons.Default.DarkMode,
                options = listOf(
                    SettingsStore.THEME_SYSTEM to stringResource(R.string.theme_system),
                    SettingsStore.THEME_LIGHT to stringResource(R.string.theme_light),
                    SettingsStore.THEME_DARK to stringResource(R.string.theme_dark),
                ),
                selected = state.theme,
                onSelect = onTheme,
            )
        }

        item {
            ChipsCard(
                title = stringResource(R.string.language),
                icon = Icons.Default.Translate,
                // Только те языки, на которые переведены наши экраны (D-15).
                options = listOf(
                    "" to stringResource(R.string.language_system),
                    "ru" to stringResource(R.string.language_ru),
                    "en" to stringResource(R.string.language_en),
                ),
                selected = state.language,
                onSelect = onLanguage,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.oled),
                subtitle = stringResource(R.string.oled_hint),
                checked = state.oled,
                onChange = onOled,
                icon = Icons.Default.Contrast,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.dynamic_color),
                subtitle = stringResource(R.string.dynamic_color_hint),
                checked = state.dynamicColor,
                onChange = onDynamicColor,
                icon = Icons.Default.Palette,
            )
        }

        item { SectionTitle(stringResource(R.string.section_privacy)) }

        item {
            SwitchRow(
                title = stringResource(R.string.external_control),
                subtitle = stringResource(R.string.external_control_hint),
                checked = state.allowExternalControl,
                onChange = onExternalControl,
                icon = Icons.Default.ToggleOn,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.hide_recents),
                subtitle = stringResource(R.string.hide_recents_hint),
                checked = state.hideRecents,
                onChange = onHideRecents,
                icon = Icons.Default.VisibilityOff,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.disguise),
                subtitle = stringResource(R.string.disguise_hint),
                checked = state.disguise,
                onChange = onDisguise,
                icon = Icons.Default.Calculate,
            )
        }

        item {
            HwidCard(hwid, sendHwid, onSendHwid)
        }

        item { SectionTitle(stringResource(R.string.section_advanced)) }

        item {
            ExpandableCard(
                title = stringResource(R.string.advanced),
                subtitle = stringResource(R.string.advanced_hint),
                icon = Icons.Default.Tune,
            ) {
                InnerSwitch(
                    title = stringResource(R.string.ipv6),
                    subtitle = stringResource(R.string.ipv6_hint),
                    checked = state.ipv6,
                    onChange = onIpv6,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.stack_mode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.stack_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Четыре чипа в одну строку на узком экране не влезают, поэтому
                    // FlowRow: перенесётся, а не обрежется.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (value in listOf("", "system", "gvisor", "mixed", "mips")) {
                            FilterChip(
                                selected = state.stack == value,
                                onClick = { onStack(value) },
                                label = {
                                    Text(
                                        // Что именно задал провайдер — видно прямо на чипе (D-122).
                                        text = value.ifEmpty {
                                            if (state.profileStack.isEmpty()) stringResource(R.string.stack_from_profile)
                                            else stringResource(R.string.stack_from_profile_value, state.profileStack)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                shape = MaterialTheme.shapes.extraLarge,
                            )
                        }
                    }
                }

                InnerSwitch(
                    title = stringResource(R.string.dns_hijack),
                    subtitle = stringResource(R.string.dns_hijack_hint),
                    checked = state.dnsHijacking,
                    onChange = onDnsHijacking,
                )
                InnerSwitch(
                    title = stringResource(R.string.bypass_private),
                    subtitle = stringResource(R.string.bypass_private_hint),
                    checked = state.bypassPrivate,
                    onChange = onBypassPrivate,
                )

                // Ниже — то, что меняет саму дорогу трафика. Отделено намеренно:
                // случайный клик здесь стоит дороже, чем на остальных переключателях.
                HorizontalDivider()

                Text(
                    text = stringResource(R.string.advanced_risky),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                InnerSwitch(
                    title = stringResource(R.string.system_proxy),
                    subtitle = stringResource(R.string.system_proxy_hint),
                    checked = state.systemProxy,
                    onChange = onSystemProxy,
                )
                InnerSwitch(
                    title = stringResource(R.string.allow_bypass),
                    subtitle = stringResource(R.string.allow_bypass_hint),
                    checked = state.allowBypass,
                    onChange = onAllowBypass,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_help)) }

        // «Подробный журнал» — переключателем на самом экране журнала, второй здесь не нужен.
        item {
            LinkRow(
                title = stringResource(R.string.logs),
                subtitle = stringResource(R.string.logs_hint),
                onClick = onLogs,
                icon = Icons.Default.BugReport,
            )
        }

        item {
            LinkRow(
                title = stringResource(R.string.probe),
                subtitle = stringResource(R.string.probe_hint),
                onClick = onProbe,
                icon = Icons.Default.Speed,
            )
        }

        item {
            LinkRow(
                title = stringResource(R.string.dashboard),
                subtitle = stringResource(R.string.dashboard_hint),
                onClick = onDashboard,
                icon = Icons.Default.Insights,
            )
        }

        item {
            LinkRow(
                title = stringResource(R.string.channel),
                subtitle = stringResource(R.string.channel_hint),
                onClick = { onLink(CHANNEL) },
                icon = Icons.AutoMirrored.Filled.Send,
            )
        }

        item {
            LinkRow(
                title = stringResource(R.string.community),
                subtitle = stringResource(R.string.community_hint),
                onClick = { onLink(COMMUNITY) },
                icon = Icons.Default.Forum,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandAvatar(null, Modifier.size(44.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.version_app, state.appVersion),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.version_core, state.coreVersion),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Идентификатор устройства (D-25, D-36). Именно его просит провайдер в поддержке, и именно
 * он решает, придёт профиль или отказ (F-52).
 *
 * Жил на вкладке подписок, под карточками, — и выключатель там задевали случайно, а
 * выключенный он оставляет без профиля у панели с лимитом устройств. Теперь — в самом низу
 * настроек (D-103): найти можно, задеть мимоходом нельзя.
 */
@Composable
private fun HwidCard(hwid: String, sendHwid: Boolean, onSendHwid: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .toggleable(value = sendHwid, role = Role.Switch, onValueChange = onSendHwid),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(Icons.Default.Fingerprint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hwid_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.hwid_explained),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Switch(checked = sendHwid, onCheckedChange = null)
            }

            if (sendHwid) {
                SelectionContainer {
                    Text(text = hwid, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Прямо говорим, чем это кончится: у панели с лимитом устройств
                // вместо профиля приходит отказ (D-36, F-52).
                Text(
                    text = stringResource(R.string.hwid_disabled_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Работа в фоне (D-106, D-107). Без исключения из экономии батареи Android усыпляет службу,
 * и VPN отваливается при выключенном экране. Оболочки Xiaomi, Huawei, vivo, OPPO, Samsung,
 * Transsion сверх того закрывают приложения сами (F-116, F-121) — им нужны шаги в их
 * собственных настройках, и показываем только шаги своей оболочки.
 *
 * Когда Android уже разрешил, хватает двух строк с галочкой (этап 13). Шаги оболочки
 * приложение проверить не может, поэтому они не пропадают, а раскрываются касанием по карточке.
 */
@Composable
private fun BackgroundCard(allowed: Boolean, onAllow: () -> Unit, onVendor: (Shortcut?) -> Unit) {
    val vendor = Vendor.current
    var stepsOpen by rememberSaveable { mutableStateOf(false) }
    val foldable = allowed && vendor != null

    val fold = if (foldable) {
        Modifier.clip(MaterialTheme.shapes.large).clickable(
            onClickLabel = stringResource(R.string.background_vendor_steps, stringResource(vendor.names)),
            role = Role.Button,
        ) { stepsOpen = !stepsOpen }
    } else Modifier

    Card(modifier = Modifier.fillMaxWidth().then(fold)) {
        // Отступы — у самих строк, а не общим spacedBy: общий оставался бы, пока шаги
        // сворачиваются, и в последнем кадре карточка прыгала на 12 dp (D-140).
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(Icons.Default.BatteryChargingFull)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.background_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (allowed) R.string.background_allowed else R.string.background_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (allowed) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
            }

            if (!allowed) {
                Button(onClick = onAllow, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.background_allow))
                }
            }

            if (vendor != null) {
                AnimatedVisibility(visible = !foldable || stepsOpen) {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.background_vendor, stringResource(vendor.names)) +
                                " " + stringResource(vendor.steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        vendor.shortcuts.ifEmpty { listOf(null) }.forEach { shortcut ->
                            OutlinedButton(onClick = { onVendor(shortcut) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(shortcut?.label ?: R.string.background_app_settings))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Сворачиваемая карточка: снаружи одна строка, внутри — настройки, которые нужны редко.
 * Так же ведут себя группы на вкладке «Прокси», и человек уже знает этот жест.
 */
@Composable
private fun ExpandableCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(icon)

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

/** Переключатель внутри развёрнутой карточки — без своей подложки, она уже есть. */
@Composable
private fun InnerSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Выбор из нескольких значений чипами — тема и язык. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsCard(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector,
) {
    Card(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .clickable(role = Role.Button, onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
