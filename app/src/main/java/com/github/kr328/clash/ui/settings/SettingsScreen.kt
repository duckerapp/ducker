package com.github.kr328.clash.ui.settings

import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.Symbols
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.github.kr328.clash.ui.components.BackLayer
import com.github.kr328.clash.ui.components.DuckerSheet
import java.util.Locale

import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import com.github.kr328.clash.update.UpdateState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.github.kr328.clash.R
import com.github.kr328.clash.util.Shortcut
import com.github.kr328.clash.util.shortcuts
import com.github.kr328.clash.util.Vendor
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.ScreenFrame
import com.github.kr328.clash.ui.components.ScreenHeader
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
    /** Android has a screen to turn «Постоянный VPN» on: Google TV does not (F-346). */
    val alwaysOnSettings: Boolean = true,
    val hideRecents: Boolean = false,
    /** На рабочем столе — «Калькулятор» (D-147). */
    val disguise: Boolean = false,
    /** Показывать скорость в уведомлении — будит процесс раз в секунду. */
    val trafficNotification: Boolean = false,
    /** Android не усыпляет ducker: он в исключениях экономии батареи (D-106). */
    val backgroundAllowed: Boolean = true,
    /** Получать выпуски на несколько дней раньше остальных. */
    val betaUpdates: Boolean = false,
)

/** Community support remains reachable from diagnostics as well as project support. */
internal const val COMMUNITY = "https://t.me/ducker_community"

/** Почта проекта — там же, для тех, кому Telegram не подходит. */
const val MAIL = "duckermihomo@proton.me"

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
    onMail: () -> Unit,
    /** Новая версия приложения: что известно и идёт ли проверка. */
    update: UpdateState,
    /** Есть что ставить — открыть панель обновления, иначе проверить. */
    onUpdate: () -> Unit,
    /** Turning it on goes through Android's “install from this source” screen first. */
    onAutoUpdate: (Boolean) -> Unit,
    onBetaUpdates: (Boolean) -> Unit,
    onAllowBackground: () -> Unit,
    /** Экран оболочки; `null` — страница приложения. */
    onVendor: (Shortcut?) -> Unit,
    hwid: String,
    sendHwid: Boolean,
    onSendHwid: (Boolean) -> Unit,
    onBack: () -> Unit,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tv = LocalTv.current
    var destination by rememberSaveable { mutableStateOf(SettingsPage.Appearance.name) }
    var sectionOpen by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    var languageMounted by rememberSaveable { mutableStateOf(false) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val languages = settingsLanguages()
    val theme = when (state.theme) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
    val language = languages.firstOrNull { it.first == state.language }?.second
        ?: stringResource(R.string.language_system)
    val navigate: (SettingsPage) -> Unit = {
        destination = it.name
        sectionOpen = true
    }

    val pageContent: @Composable (SettingsPage, () -> Unit) -> Unit = { page, back ->
        pages.SaveableStateProvider(page.name) {
            val list = rememberLazyListState()
            TvLazyColumn(
                state = list,
                overscrollEffect = null,
                modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp)
                    .then(if (LocalTv.current) Modifier.focusRestorer() else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp + tvEdge()),
            ) {
                item(key = "header") {
                    ScreenHeader(stringResource(page.title), onBack = back)
                }
                when (page) {
                    SettingsPage.Root -> {
                        item(key = "interface") {
                            SettingsNavigationGroup(stringResource(R.string.settings_category_interface)) {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.language), value = language,
                                    icon = Symbols.Translate,
                                    onClick = { languageMounted = true; languageOpen = true },
                                )
                                SettingsNavigationDivider()
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_appearance), value = theme,
                                    icon = Symbols.DarkMode,
                                    onClick = { navigate(SettingsPage.Appearance) },
                                )
                            }
                        }
                        item(key = "vpn") {
                            SettingsNavigationGroup("VPN") {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.section_connection),
                                    subtitle = stringResource(R.string.settings_connection_summary),
                                    icon = Symbols.Link,
                                    onClick = { navigate(SettingsPage.Connection) },
                                )
                                SettingsNavigationDivider()
                                SettingsNavigationRow(
                                    title = stringResource(R.string.section_privacy),
                                    subtitle = stringResource(R.string.settings_privacy_summary),
                                    icon = Symbols.ShieldLock,
                                    onClick = { navigate(SettingsPage.Privacy) },
                                )
                            }
                        }
                        item(key = "information") {
                            SettingsNavigationGroup(stringResource(R.string.settings_category_information)) {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_help),
                                    subtitle = stringResource(R.string.settings_help_summary),
                                    icon = Symbols.SupportAgent,
                                    onClick = { navigate(SettingsPage.Help) },
                                )
                                SettingsNavigationDivider()
                                SettingsNavigationRow(
                                    title = stringResource(R.string.app_update_check),
                                    subtitle = when {
                                        update.checking -> stringResource(R.string.app_update_checking)
                                        update.release != null -> stringResource(R.string.app_update_available, update.release.version)
                                        update.checked == false -> stringResource(R.string.app_update_check_failed)
                                        update.checked == true -> stringResource(R.string.app_update_latest)
                                        else -> stringResource(R.string.app_update_check_hint)
                                    },
                                    icon = Symbols.Update,
                                    busy = update.checking, onClick = onUpdate, action = true,
                                )
                                if (update.autoAvailable) {
                                    SettingsNavigationDivider()
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.app_update_auto),
                                        subtitle = stringResource(R.string.app_update_auto_hint),
                                        icon = Symbols.SystemUpdate,
                                        checked = update.auto == true,
                                        onChange = onAutoUpdate,
                                    )
                                }
                                SettingsNavigationDivider()
                                SettingsSwitchRow(
                                    title = stringResource(R.string.app_update_beta),
                                    subtitle = stringResource(R.string.app_update_beta_hint),
                                    icon = Symbols.Science,
                                    checked = state.betaUpdates,
                                    onChange = onBetaUpdates,
                                )
                                SettingsNavigationDivider()
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_project),
                                    subtitle = stringResource(R.string.settings_project_summary),
                                    icon = Symbols.Favorite,
                                    onClick = { navigate(SettingsPage.Project) },
                                )
                                SettingsNavigationDivider()
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_about),
                                    subtitle = stringResource(R.string.version_app, state.appVersion),
                                    icon = Symbols.Info,
                                    onClick = { navigate(SettingsPage.About) },
                                )
                            }
                        }
                    }
                    SettingsPage.Appearance -> {
                        item {
                            ChipsCard(
                                title = stringResource(R.string.theme),
                                icon = Symbols.DarkMode,
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
                            SwitchRow(
                                title = stringResource(R.string.oled),
                                subtitle = stringResource(R.string.oled_hint),
                                checked = state.oled,
                                onChange = onOled,
                                icon = Symbols.Contrast,
                            )
                        }

                        item {
                            SwitchRow(
                                title = stringResource(R.string.dynamic_color),
                                subtitle = stringResource(R.string.dynamic_color_hint),
                                checked = state.dynamicColor,
                                onChange = onDynamicColor,
                                icon = Symbols.Palette,
                            )
                        }
                    }
                    SettingsPage.Connection -> {
                        // Not on a TV, as Home's prompt (F-346).
                        if (!tv) {
                            item {
                                BackgroundCard(state.backgroundAllowed, onAllowBackground, onVendor)
                            }
                        }

                        item {
                            LinkRow(
                                title = stringResource(R.string.access_control),
                                subtitle = stringResource(R.string.access_control_hint),
                                onClick = onAccessControl,
                                icon = Symbols.Apps,
                            )
                        }

                        // Плитка в быстрых настройках есть давно, но найти её в редакторе шторки умеют
                        // немногие. С Android 13 система может предложить добавить её сама (D-87). A TV has no
                        // quick-settings shade.
                        if (onAddTile != null && !tv) {
                            item {
                                LinkRow(
                                    title = stringResource(R.string.add_tile),
                                    subtitle = stringResource(R.string.add_tile_hint),
                                    onClick = onAddTile,
                                    icon = Symbols.ToggleOn,
                                )
                            }
                        }

                        item {
                            SwitchRow(
                                title = stringResource(R.string.auto_restart),
                                subtitle = stringResource(R.string.auto_restart_hint),
                                checked = state.autoRestart,
                                onChange = onAutoRestart,
                                icon = Symbols.RestartAlt,
                            )
                        }

                        // Автозапуск ждёт своей очереди среди событий загрузки — на слабом телефоне полторы
                        // минуты (D-128). Сразу поднимает только «Постоянный VPN» Android (этап 10). Google TV has
                        // no screen for it (F-346).
                        if (state.alwaysOnSettings) {
                            item {
                                LinkRow(
                                    title = stringResource(R.string.always_on),
                                    subtitle = stringResource(if (state.alwaysOn) R.string.always_on_enabled else R.string.always_on_hint),
                                    onClick = onAlwaysOn,
                                    icon = Symbols.VpnLock,
                                )
                            }
                        }

                        // Not on a TV: it has no notification shade to show the speed in.
                        if (!tv) item {
                            SwitchRow(
                                title = stringResource(R.string.traffic_notification),
                                subtitle = stringResource(R.string.traffic_notification_hint),
                                checked = state.trafficNotification,
                                onChange = onTrafficNotification,
                                icon = Symbols.Notifications,
                            )
                        }
                        item { SectionTitle(stringResource(R.string.section_advanced)) }

                        item {
                            ExpandableCard(
                                title = stringResource(R.string.advanced),
                                subtitle = stringResource(R.string.advanced_hint),
                                icon = Symbols.Tune,
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
                                        modifier = Modifier.selectableGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        for (value in listOf("", "system", "gvisor", "mixed", "mips")) {
                                            Pill(
                                                // Что именно задал провайдер — видно прямо на чипе (D-122).
                                                text = value.ifEmpty {
                                                    if (state.profileStack.isEmpty()) stringResource(R.string.stack_from_profile)
                                                    else stringResource(R.string.stack_from_profile_value, state.profileStack)
                                                },
                                                onClick = { onStack(value) },
                                                selected = state.stack == value,
                                                onCard = true,
                                                role = Role.RadioButton,
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
                    }
                    SettingsPage.Privacy -> {
                        item {
                            SwitchRow(
                                title = stringResource(R.string.hide_recents),
                                subtitle = stringResource(R.string.hide_recents_hint),
                                checked = state.hideRecents,
                                onChange = onHideRecents,
                                icon = Symbols.VisibilityOff,
                            )
                        }

                        // On a TV only while it is on, so it can be turned off: the TV's launcher went on showing the
                        // app's own picture under the new name, and a TV is not the device people check for a VPN.
                        if (!tv || state.disguise) item {
                            SwitchRow(
                                title = stringResource(R.string.disguise),
                                subtitle = stringResource(R.string.disguise_hint),
                                checked = state.disguise,
                                onChange = onDisguise,
                                icon = Symbols.Calculate,
                            )
                        }

                        item {
                            SwitchRow(
                                title = stringResource(R.string.external_control),
                                subtitle = stringResource(R.string.external_control_hint),
                                checked = state.allowExternalControl,
                                onChange = onExternalControl,
                                icon = Symbols.SmartToy,
                            )
                        }

                        item {
                            HwidCard(hwid, sendHwid, onSendHwid)
                        }
                    }
                    SettingsPage.Help -> {
                        // «Подробный журнал» — переключателем на самом экране журнала, второй здесь не нужен.
                        item {
                            LinkRow(
                                title = stringResource(R.string.logs),
                                subtitle = stringResource(R.string.logs_hint),
                                onClick = onLogs,
                                icon = Symbols.BugReport,
                            )
                        }

                        item {
                            LinkRow(
                                title = stringResource(R.string.probe),
                                subtitle = stringResource(R.string.probe_hint),
                                onClick = onProbe,
                                icon = Symbols.Speed,
                            )
                        }

                        item {
                            LinkRow(
                                title = stringResource(R.string.dashboard),
                                subtitle = stringResource(R.string.dashboard_hint),
                                onClick = onDashboard,
                                icon = Symbols.Monitoring,
                            )
                        }
                        item {
                            LinkRow(
                                title = stringResource(R.string.community),
                                subtitle = stringResource(R.string.community_hint),
                                onClick = { onLink(COMMUNITY) },
                                icon = Symbols.Forum,
                            )
                        }

                        item {
                            LinkRow(
                                title = stringResource(R.string.mail),
                                subtitle = MAIL,
                                onClick = onMail,
                                icon = Symbols.Email,
                            )
                        }
                    }
                    SettingsPage.Project -> {
                        projectSupportItems(onLink = onLink, onMail = onMail)
                    }
                    SettingsPage.About -> {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // The tile as on the launcher (D-291): the mark of the other theme, so that it
                                    // stands out from the card instead of blending into it.
                                    Image(
                                        painter = painterResource(
                                            if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.drawable.ducker_mark_light
                                            else R.drawable.ducker_mark_dark
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(percent = 22)),
                                    )
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
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (sectionOpen) Modifier.clearAndSetSemantics {} else Modifier,
            ),
        ) {
            ScreenFrame { pageContent(SettingsPage.Root, onBack) }
        }
        // Keep the root mounted beneath the section so predictive Back reveals
        // its saved position, using the same motion as entry into Settings.
        BackLayer(
            visible = sectionOpen,
            enabled = active && !languageMounted,
            onBack = { sectionOpen = false },
        ) {
            pageContent(SettingsPage.valueOf(destination)) { sectionOpen = false }
        }
    }

    if (languageMounted) {
        SettingsLanguageSheet(
            open = languageOpen, selected = state.language, options = languages,
            onSelect = { pendingLanguage = it; languageOpen = false },
            onDismiss = { pendingLanguage = null; languageOpen = false },
            onClosed = {
                languageMounted = false
                // Apply after dismissal: a locale change may recreate the Activity.
                pendingLanguage?.let { value -> pendingLanguage = null; onLanguage(value) }
            },
        )
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
                SettingsIcon(Symbols.Fingerprint)
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
                // Hidden until tapped, and hidden again seven seconds later (D-292). While hidden,
                // the text is measured and never composed: the mosaic takes its size, and a
                // screenshot or TalkBack get nothing. Not saved on purpose: leaving settings, or
                // scrolling the card away, hides it at once.
                var shown by remember { mutableStateOf(false) }

                LaunchedEffect(shown) {
                    if (shown) {
                        delay(HWID_SHOWN_MS)

                        shown = false
                    }
                }

                val style = MaterialTheme.typography.bodyMedium
                val measurer = rememberTextMeasurer()
                val size = remember(hwid, style) { measurer.measure(hwid, style).size }
                val show = stringResource(R.string.hwid_show)

                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable(
                            onClickLabel = if (shown) stringResource(R.string.hwid_hide) else null,
                            role = Role.Button,
                        ) { shown = !shown }
                        .padding(vertical = 6.dp),
                ) {
                    Crossfade(shown, label = "hwid") { open ->
                        if (open) {
                            SelectionContainer {
                                Text(text = hwid, style = style)
                            }
                        } else {
                            Mosaic(
                                with(LocalDensity.current) {
                                    Modifier.size(size.width.toDp(), size.height.toDp())
                                }.semantics { contentDescription = show },
                            )
                        }
                    }
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

    Card(modifier = Modifier.fillMaxWidth(), surfaceModifier = fold, resizing = true) {
        // Отступы — у самих строк, а не общим spacedBy: общий оставался бы, пока шаги
        // сворачиваются, и в последнем кадре карточка прыгала на 12 dp (D-140).
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIcon(Symbols.BatteryChargingFull)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.background_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (allowed) R.string.background_allowed else R.string.background_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (allowed) {
                    Icon(Symbols.CheckCircle, contentDescription = null)
                }
            }

            if (!allowed) {
                Pill(
                    text = stringResource(R.string.background_allow),
                    onClick = onAllow,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
                    selected = true,
                )
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
                            Pill(
                                text = stringResource(shortcut?.label ?: R.string.background_app_settings),
                                onClick = { onVendor(shortcut) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                onCard = true,
                            )
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

    Card(modifier = Modifier.fillMaxWidth(), resizing = true) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIcon(icon)

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (expanded) Symbols.KeyboardArrowUp
                    else Symbols.KeyboardArrowDown,
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

/** Theme choices wrap vertically when large text needs more room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsCard(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIcon(icon)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val labelStyle = MaterialTheme.typography.labelLarge
                val measurer = rememberTextMeasurer()
                val widest = options.maxOf { measurer.measure(it.second, labelStyle).size.width }
                val columns = with(density) {
                    if ((widest + 12.dp.roundToPx()) * options.size +
                        8.dp.roundToPx() * (options.size - 1) <= maxWidth.roundToPx()) options.size else 1
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = columns,
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    options.forEach { (value, label) ->
                        val chosen = selected == value
                        val scheme = MaterialTheme.colorScheme
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(if (chosen) scheme.primaryContainer else scheme.surfaceVariant)
                                .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(value) })
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label, modifier = Modifier.fillMaxWidth(), style = labelStyle,
                                textAlign = TextAlign.Center,
                                color = if (chosen) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            )
                        }
                    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIcon(icon)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Symbols.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** How long the device identifier stays on screen after a tap (D-292). */
private const val HWID_SHOWN_MS = 7_000L

private const val MOSAIC_ROWS = 3
private val MOSAIC_TONES = floatArrayOf(0.10f, 0.18f, 0.28f, 0.42f)

/**
 * A mosaic in place of a hidden text, the size of that text. Drawn by us: `Modifier.blur` does
 * nothing below Android 12, and a blur would still need the text under it.
 */
@Composable
private fun Mosaic(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val cell = size.height / MOSAIC_ROWS
        val columns = (size.width / cell).roundToInt().coerceAtLeast(1)
        val width = size.width / columns
        val gap = 0.5.dp.toPx()
        val corner = CornerRadius(1.5.dp.toPx())
        // The same seed at every draw, or the tiles would flicker.
        val random = kotlin.random.Random(7)

        for (row in 0 until MOSAIC_ROWS) for (column in 0 until columns) {
            drawRoundRect(
                color = ink.copy(alpha = MOSAIC_TONES[random.nextInt(MOSAIC_TONES.size)]),
                topLeft = Offset(column * width + gap, row * cell + gap),
                size = Size(width - 2 * gap, cell - 2 * gap),
                cornerRadius = corner,
            )
        }
    }
}

private enum class SettingsPage(@StringRes val title: Int) {
    Root(R.string.settings),
    Appearance(R.string.settings_appearance),
    Connection(R.string.section_connection),
    Privacy(R.string.section_privacy),
    Help(R.string.settings_help),
    Project(R.string.settings_project),
    About(R.string.settings_about),
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
}

@Composable
private fun SettingsNavigationGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title, modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            surfaceModifier = Modifier.clip(MaterialTheme.shapes.large),
            content = content,
        )
    }
}

@Composable
private fun SettingsNavigationDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    busy: Boolean = false,
    /** The row does something rather than open a page: no arrow, only the progress while it runs. */
    action: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 61.dp)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (value == null) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val measurer = rememberTextMeasurer()
                    val titleStyle = MaterialTheme.typography.bodyLarge
                    val valueStyle = MaterialTheme.typography.bodyMedium
                    val density = LocalDensity.current
                    val fits = with(density) {
                        measurer.measure(title, titleStyle).size.width +
                            measurer.measure(value, valueStyle).size.width + 12.dp.roundToPx() <= maxWidth.roundToPx()
                    }
                    if (fits) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(title, Modifier.weight(1f), style = titleStyle)
                            Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, style = titleStyle)
                            Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            // The progress takes this place, so the text does not move when it appears.
            action -> Spacer(Modifier.size(20.dp))
            else -> Icon(Symbols.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** A row of a settings group, like [SettingsNavigationRow], that holds a switch. */
@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 61.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun settingsLanguages(): List<Pair<String, String>> = listOf(
    "" to stringResource(R.string.language_system),
    "ru" to stringResource(R.string.language_ru),
    "en" to stringResource(R.string.language_en),
    "zh-CN" to "简体中文", "fa" to "فارسی", "ar" to "العربية", "es" to "Español",
    "fr" to "Français", "id" to "Bahasa Indonesia", "pt-BR" to "Português (Brasil)",
    "tr" to "Türkçe", "vi" to "Tiếng Việt",
)

@Composable
private fun SettingsLanguageSheet(
    open: Boolean,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    val systemName = systemLocale.getDisplayLanguage(systemLocale)
    DuckerSheet(
        open = open, title = stringResource(R.string.language), busy = false,
        heightFraction = 0.78f, onDismiss = onDismiss, onClosed = onClosed,
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                LanguageChoice(
                    label = stringResource(R.string.settings_language_system),
                    hint = stringResource(R.string.settings_system_language, systemName),
                    selected = selected.isEmpty(), onSelect = { onSelect("") },
                )
            }
            Card(Modifier.fillMaxWidth()) {
                options.filter { it.first.isNotEmpty() }.forEach { (value, label) ->
                    LanguageChoice(label, selected = selected == value, onSelect = { onSelect(value) })
                }
            }
            Text(
                stringResource(R.string.settings_language_immediate),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onSelect: () -> Unit, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Symbols.Check, contentDescription = null, modifier = Modifier.size(24.dp))
        else Spacer(Modifier.size(24.dp))
    }
}
