package com.github.kr328.clash.ui.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.github.kr328.clash.ui.components.DuckerSwitch
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.GlassMaterial
import com.github.kr328.clash.ui.components.chosenFill
import com.github.kr328.clash.ui.components.glass
import com.github.kr328.clash.ui.components.glassPress
import com.github.kr328.clash.ui.components.glassPressOwnTouch
import com.github.kr328.clash.ui.components.paneIndication
import com.github.kr328.clash.ui.components.rememberGlassPress
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalResources
import com.github.kr328.clash.ui.components.BACKDROP_PICTURES

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.kr328.clash.ui.components.glassSupported
import com.github.kr328.clash.ui.components.navigationRoom
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import com.github.kr328.clash.update.UpdateState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.ui.components.airEdge
import com.github.kr328.clash.ui.components.glassPanesOn
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
import com.github.kr328.clash.util.labelWith
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
    /** The look, Air or glass, and the glass's settings, see [SettingsStore.style]. */
    val style: String = SettingsStore.STYLE_AIR,
    val glassBackdrop: String = SettingsStore.BACKDROPS.first(),
    val glassBlurBoost: Int = 0,
    /** The own picture: its file's time (0 — none yet), whether it is dark, and whether its top is. */
    val glassImage: Long = 0L,
    val glassImageDark: Boolean = false,
    val glassImageTopDark: Boolean = false,
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
    /** The lock on the subscription link and the config (stage 15). */
    val lockSensitive: Boolean = false,
    /** The phone has a screen lock for that lock to check; without it the row leads to Android's settings. */
    val deviceSecure: Boolean = true,
    /** На рабочем столе — «Калькулятор» (D-147). */
    val disguise: Boolean = false,
    /** Показывать скорость в уведомлении — будит процесс раз в секунду. */
    val trafficNotification: Boolean = false,
    /** Android не усыпляет ducker: он в исключениях экономии батареи (D-106). */
    val backgroundAllowed: Boolean = true,
    /** MIUI's autostart for ducker; `null` — not a Xiaomi, or unknown (F-418). */
    val autostart: Boolean? = null,
    /** Получать выпуски на несколько дней раньше остальных. */
    val betaUpdates: Boolean = false,
)

/** Community support remains reachable from diagnostics as well as project support. */
internal const val COMMUNITY = "https://t.me/ducker_chat"

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
    onStyle: (String) -> Unit,
    onGlassBackdrop: (String) -> Unit,
    onGlassBlurBoost: (Int) -> Unit,
    onPickGlassImage: () -> Unit,
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
    /** The lock switched; off asks the lock itself first. Without a screen lock — Android's security settings. */
    onLockSensitive: (Boolean) -> Unit,
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
    // A layer of its own above the section, so that Back from it returns to «О приложении» it was opened from.
    var supportOpen by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    var languageMounted by rememberSaveable { mutableStateOf(false) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val openDetails: (SettingsPage) -> Unit = { page ->
        detailsPage = page.name
        detailsOpen = true
    }
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
            val compact = !tv && (page == SettingsPage.Root || page == SettingsPage.Connection || page == SettingsPage.Privacy)
            TvLazyColumn(
                state = list,
                overscrollEffect = null,
                modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp)
                    .then(if (LocalTv.current) Modifier.focusRestorer() else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 12.dp),
                contentPadding = PaddingValues(
                    top = (if (compact) 10.dp else 16.dp) + tvEdge(),
                    bottom = 16.dp + tvEdge() + navigationRoom(),
                ),
            ) {
                item(key = "header") {
                    ScreenHeader(stringResource(page.title), onBack = back)
                }
                when (page) {
                    SettingsPage.Root -> {
                        val updateRows: @Composable () -> Unit = {
                            SettingsNavigationRow(
                                title = stringResource(R.string.app_update_check),
                                subtitle = when {
                                    update.checking -> stringResource(R.string.app_update_checking)
                                    update.release != null -> stringResource(R.string.app_update_available, update.release.version)
                                    update.checked == false -> stringResource(R.string.app_update_check_failed)
                                    update.checked == true -> stringResource(R.string.app_update_latest)
                                    compact -> null
                                    else -> stringResource(R.string.app_update_check_hint)
                                },
                                icon = Symbols.Update,
                                busy = update.checking, onClick = onUpdate, action = true, compact = compact,
                            )
                            SettingsNavigationDivider(compact)
                            SettingsSwitchRow(
                                title = stringResource(if (compact) R.string.settings_auto_update_title else R.string.app_update_auto),
                                subtitle = stringResource(if (compact) R.string.settings_auto_update_short else R.string.app_update_auto_hint),
                                icon = Symbols.SystemUpdate,
                                checked = update.auto == true,
                                onChange = onAutoUpdate,
                                compact = compact,
                            )
                            SettingsNavigationDivider(compact)
                            SettingsSwitchRow(
                                title = stringResource(R.string.app_update_beta),
                                subtitle = if (compact) null else stringResource(R.string.app_update_beta_hint),
                                icon = Symbols.Science,
                                checked = state.betaUpdates,
                                onChange = onBetaUpdates,
                                compact = compact,
                            )
                        }
                        item(key = "interface") {
                            SettingsNavigationGroup(stringResource(R.string.settings_category_interface), compact) {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.language), value = language,
                                    icon = Symbols.Translate, compact = compact,
                                    onClick = { languageMounted = true; languageOpen = true },
                                )
                                SettingsNavigationDivider(compact)
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_appearance), value = theme,
                                    icon = Symbols.DarkMode, compact = compact,
                                    onClick = { navigate(SettingsPage.Appearance) },
                                )
                            }
                        }
                        item(key = "vpn") {
                            SettingsNavigationGroup("VPN", compact) {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.section_connection),
                                    subtitle = if (compact) null else stringResource(R.string.settings_connection_summary),
                                    icon = Symbols.Link, compact = compact,
                                    onClick = { navigate(SettingsPage.Connection) },
                                )
                                SettingsNavigationDivider(compact)
                                SettingsNavigationRow(
                                    title = stringResource(R.string.section_privacy),
                                    subtitle = if (compact) null else stringResource(R.string.settings_privacy_summary),
                                    icon = Symbols.ShieldLock, compact = compact,
                                    onClick = { navigate(SettingsPage.Privacy) },
                                )
                            }
                        }
                        if (compact) item(key = "updates") {
                            SettingsNavigationGroup(stringResource(R.string.settings_category_updates), compact = true) {
                                updateRows()
                            }
                        }
                        item(key = "information") {
                            SettingsNavigationGroup(stringResource(R.string.settings_category_information), compact) {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_help),
                                    subtitle = if (compact) null else stringResource(R.string.settings_help_summary),
                                    icon = Symbols.SupportAgent, compact = compact,
                                    onClick = { navigate(SettingsPage.Help) },
                                )
                                SettingsNavigationDivider(compact)
                                if (!compact) {
                                    updateRows()
                                    SettingsNavigationDivider()
                                }
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_project),
                                    subtitle = if (compact) null else stringResource(R.string.settings_project_summary),
                                    icon = Symbols.Favorite, compact = compact,
                                    onClick = { supportOpen = true },
                                )
                                SettingsNavigationDivider(compact)
                                SettingsNavigationRow(
                                    title = stringResource(R.string.settings_about),
                                    subtitle = if (compact) null else stringResource(R.string.version_app, state.appVersion),
                                    value = state.appVersion.takeIf { compact && it.isNotEmpty() },
                                    icon = Symbols.Info, compact = compact,
                                    onClick = { navigate(SettingsPage.About) },
                                )
                            }
                        }
                    }
                    SettingsPage.Appearance -> {
                        // Ducker Glass is dark only: no theme to choose while it is on.
                        if (tv || !glassSupported || state.style != SettingsStore.STYLE_GLASS) item {
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

                        // Ducker Glass lies on its pictures: pure black has nothing to do there.
                        if (tv || !glassSupported || state.style != SettingsStore.STYLE_GLASS) item {
                            SwitchRow(
                                title = stringResource(R.string.oled),
                                subtitle = stringResource(R.string.oled_hint),
                                checked = state.oled,
                                onChange = onOled,
                                icon = Symbols.Contrast,
                            )
                        }

                        // The wallpaper gives its colour from Android 8.1.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) item {
                            SwitchRow(
                                title = stringResource(R.string.dynamic_color),
                                subtitle = stringResource(R.string.dynamic_color_hint),
                                checked = state.dynamicColor,
                                onChange = onDynamicColor,
                                icon = Symbols.Palette,
                            )
                        }

                        // Only where the glass is drawn: phones with Android 13+.
                        if (!tv && glassSupported) {
                            item {
                                ChipsCard(
                                    title = stringResource(R.string.style),
                                    icon = Symbols.Palette,
                                    options = listOf(
                                        SettingsStore.STYLE_AIR to stringResource(R.string.style_air),
                                        SettingsStore.STYLE_GLASS to stringResource(R.string.style_glass),
                                    ),
                                    selected = state.style,
                                    onSelect = onStyle,
                                    badges = mapOf(SettingsStore.STYLE_GLASS to "BETA"),
                                )
                            }
                        }

                        if (!tv && glassSupported && state.style == SettingsStore.STYLE_GLASS) {
                            item {
                                BackdropCard(selected = state.glassBackdrop, onSelect = onGlassBackdrop)
                            }

                            // The own picture is not offered for now (the owner, 23.11), see SettingsStore.glassBackdrop.
                            if (state.glassBackdrop == SettingsStore.BACKDROP_IMAGE) {
                                item {
                                    LinkRow(
                                        title = stringResource(R.string.glass_image_pick),
                                        subtitle = stringResource(R.string.glass_image_pick_hint),
                                        onClick = onPickGlassImage,
                                        icon = Symbols.PhotoLibrary,
                                    )
                                }
                            }

                            // The glass is the owner's tuning; only its blur can be raised, in three steps (the owner, 23.11).
                            item {
                                ChipsCard(
                                    title = stringResource(R.string.glass_blur),
                                    icon = Symbols.Contrast,
                                    options = (0..SettingsStore.GLASS_BLUR_BOOST_MAX step 10).map {
                                        it.toString() to if (it == 0) stringResource(R.string.glass_blur_standard) else "+$it %"
                                    },
                                    selected = state.glassBlurBoost.toString(),
                                    onSelect = { onGlassBlurBoost(it.toInt()) },
                                )
                            }
                        }
                    }
                    SettingsPage.Connection -> {
                        settingsRows("connection-vpn", compact = compact, rows = buildList {
                            // TV has no background permission prompt (F-346).
                            if (!tv) add {
                                BackgroundRow(state.backgroundAllowed, state.autostart, onAllowBackground, onVendor)
                            }
                            add {
                                LinkRow(
                                    title = stringResource(R.string.access_control),
                                    subtitle = stringResource(R.string.access_control_hint),
                                    onClick = onAccessControl, icon = Symbols.Apps, compact = compact,
                                )
                            }
                            add {
                                SwitchRow(
                                    title = stringResource(R.string.auto_restart),
                                    subtitle = stringResource(if (compact) R.string.settings_auto_restart_short else R.string.auto_restart_hint),
                                    checked = state.autoRestart,
                                    onChange = onAutoRestart, icon = Symbols.RestartAlt, compact = compact,
                                )
                            }
                            // Google TV does not expose Android's Always-on VPN screen (F-346).
                            if (state.alwaysOnSettings) add {
                                val subtitle = when {
                                    state.alwaysOn -> R.string.always_on_enabled
                                    compact -> R.string.settings_always_on_short
                                    else -> R.string.always_on_hint
                                }
                                LinkRow(
                                    title = stringResource(R.string.always_on),
                                    subtitle = stringResource(subtitle),
                                    onClick = onAlwaysOn, icon = Symbols.VpnLock, compact = compact,
                                )
                            }
                        })
                        if (!tv) settingsRows("connection-notifications", R.string.settings_group_notifications, compact = true, rows = buildList {
                            // The system can offer to place the tile starting on Android 13 (D-87).
                            if (onAddTile != null) add {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.add_tile),
                                    onClick = onAddTile, icon = Symbols.ToggleOn, compact = true,
                                )
                            }
                            add {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.traffic_notification),
                                    subtitle = stringResource(R.string.settings_traffic_short),
                                    checked = state.trafficNotification,
                                    onChange = onTrafficNotification, icon = Symbols.Notifications, compact = true,
                                )
                            }
                        })
                        if (!compact) item { SectionTitle(stringResource(R.string.section_advanced)) }
                        settingsRows("connection-advanced", R.string.section_advanced, compact, rows = listOf({
                            ExpandableCard(
                                title = stringResource(R.string.advanced),
                                subtitle = stringResource(R.string.advanced_hint),
                                icon = Symbols.Tune, compact = compact,
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

                                // A VPN sets the system proxy from Android 10.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) InnerSwitch(
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
                        }))
                        if (compact && state.alwaysOnSettings) item(key = "connection-details") {
                            SettingsDetailsLink(R.string.settings_connection_help) { openDetails(SettingsPage.Connection) }
                        }
                    }
                    SettingsPage.Privacy -> {
                        settingsRows("privacy-visibility", R.string.settings_privacy_protection, compact, rows = buildList {
                            // The TV cannot initiate a screen-lock check, but can turn off an existing setting.
                            if (!tv || state.lockSensitive) add {
                                val subtitle = when {
                                    !state.deviceSecure -> R.string.lock_sensitive_no_screen_lock
                                    compact -> R.string.settings_lock_short
                                    else -> R.string.lock_sensitive_hint
                                }
                                SwitchRow(
                                    title = stringResource(R.string.lock_sensitive),
                                    subtitle = stringResource(subtitle),
                                    checked = state.lockSensitive && state.deviceSecure,
                                    onChange = onLockSensitive, icon = Symbols.Lock, compact = compact,
                                )
                            }
                            add {
                                SwitchRow(
                                    title = stringResource(R.string.hide_recents),
                                    subtitle = if (compact) null else stringResource(R.string.hide_recents_hint),
                                    checked = state.hideRecents,
                                    onChange = onHideRecents, icon = Symbols.VisibilityOff, compact = compact,
                                )
                            }
                            // The TV launcher can keep the original icon. Show this on TV only to turn it off.
                            if (!tv || state.disguise) add {
                                SwitchRow(
                                    title = stringResource(R.string.disguise),
                                    subtitle = stringResource(if (compact) R.string.settings_disguise_short else R.string.disguise_hint),
                                    checked = state.disguise,
                                    onChange = onDisguise, icon = Symbols.Calculate, compact = compact,
                                )
                            }
                        })
                        settingsRows("privacy-external", R.string.settings_privacy_access, compact, rows = listOf({
                            SwitchRow(
                                title = stringResource(R.string.external_control),
                                subtitle = stringResource(if (compact) R.string.settings_external_short else R.string.external_control_hint),
                                checked = state.allowExternalControl,
                                onChange = onExternalControl, icon = Symbols.SmartToy, compact = compact,
                            )
                        }))
                        settingsRows("privacy-device", R.string.settings_privacy_device, compact, rows = listOf({
                            HwidCard(hwid, sendHwid, onSendHwid, compact)
                        }))
                        if (compact) item(key = "privacy-details") {
                            SettingsDetailsLink(R.string.settings_privacy_help) { openDetails(SettingsPage.Privacy) }
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

                        // Not on a TV: a web panel is heavy for a TV's memory, hard to drive with a remote, and Back did
                        // not lead out of it.
                        if (!tv) item {
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
                        aboutItems(state.appVersion, state.coreVersion, onLink = onLink, onSupport = { supportOpen = true })
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (sectionOpen || supportOpen) Modifier.clearAndSetSemantics {} else Modifier,
            ),
        ) {
            ScreenFrame { pageContent(SettingsPage.Root, onBack) }
        }
        // Keep the root mounted beneath the section so predictive Back reveals
        // its saved position, using the same motion as entry into Settings.
        BackLayer(
            visible = sectionOpen,
            enabled = active && !languageMounted && !supportOpen && detailsPage == null,
            onBack = { sectionOpen = false },
            // The Settings layer around it draws the fade.
            fade = false,
        ) {
            pageContent(SettingsPage.valueOf(destination)) { sectionOpen = false }
        }
        BackLayer(visible = supportOpen, enabled = active, onBack = { supportOpen = false }, fade = false) {
            pageContent(SettingsPage.Project) { supportOpen = false }
        }
    }

    if (languageMounted) {
        SettingsLanguageSheet(
            open = languageOpen, selected = state.language, options = languages,
            onSelect = { pendingLanguage = it; languageOpen = false },
            onDismiss = { pendingLanguage = null; languageOpen = false },
            onClosed = {
                languageMounted = false
                // Apply after dismissal: the words change under the closed sheet, not in it as it slides away (D-508).
                pendingLanguage?.let { value -> pendingLanguage = null; onLanguage(value) }
            },
        )
    }

    detailsPage?.let { page ->
        SettingsDetailsSheet(
            page = SettingsPage.valueOf(page),
            open = detailsOpen,
            onDismiss = { detailsOpen = false },
            onClosed = { detailsPage = null },
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
private fun HwidCard(hwid: String, sendHwid: Boolean, onSendHwid: (Boolean) -> Unit, compact: Boolean) {
    val hwidPress = remember { MutableInteractionSource() }
    val press = rememberGlassPress()
    val content: @Composable ColumnScope.() -> Unit = {
        if (compact) {
            SettingsSwitchRow(
                title = stringResource(R.string.hwid_title),
                subtitle = if (sendHwid) stringResource(R.string.settings_hwid_short) else null,
                icon = Symbols.Fingerprint,
                checked = sendHwid,
                onChange = onSendHwid,
                compact = true,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .toggleable(value = sendHwid, interactionSource = hwidPress, indication = paneIndication(), role = Role.Switch, onValueChange = onSendHwid)
                    .glassPressOwnTouch(press),
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

                DuckerSwitch(checked = sendHwid, onDrag = onSendHwid, pressed = hwidPress)
            }
        }

        if (sendHwid) {
            // Never compose or expose the identifier until requested. Leaving the page hides it too (D-292).
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(shown) {
                if (shown) {
                    delay(HWID_SHOWN_MS)
                    shown = false
                }
            }

            if (compact) {
                SettingsNavigationDivider(compact = true)
                SettingsRow(
                    title = stringResource(if (shown) R.string.hwid_hide else R.string.hwid_show),
                    compact = true,
                    modifier = Modifier.clickable(role = Role.Button) { shown = !shown },
                    trailing = { SettingsChevron(if (shown) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown) },
                )
                if (shown) {
                    SelectionContainer(Modifier.padding(start = SettingsRowInset + SettingsIconSize + SettingsIconGap,
                        end = SettingsRowInset, bottom = 12.dp)) {
                        Text(hwid, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                val style = MaterialTheme.typography.bodyMedium
                val measurer = rememberTextMeasurer()
                val size = remember(hwid, style) { measurer.measure(hwid, style).size }
                val show = stringResource(R.string.hwid_show)

                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = null,
                            indication = paneIndication(),
                            onClickLabel = if (shown) stringResource(R.string.hwid_hide) else null,
                            role = Role.Button,
                        ) { shown = !shown }
                        .glassPressOwnTouch(press)
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
            }
        } else {
            Text(
                text = stringResource(R.string.hwid_disabled_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = if (compact) Modifier.padding(start = SettingsRowInset + SettingsIconSize + SettingsIconGap,
                    end = SettingsRowInset, bottom = 12.dp) else Modifier,
            )
        }
    }
    if (compact) {
        Column(content = content)
    } else {
        Card(modifier = Modifier.fillMaxWidth(), press = press) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
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
private fun BackgroundRow(allowed: Boolean, autostart: Boolean?, onAllow: () -> Unit, onVendor: (Shortcut?) -> Unit) {
    val vendor = Vendor.current
    var stepsOpen by rememberSaveable { mutableStateOf(false) }
    val foldable = allowed && vendor != null
    val fold = if (foldable) {
        Modifier.clickable(
            interactionSource = null,
            indication = LocalIndication.current,
            onClickLabel = stringResource(R.string.background_vendor_steps, stringResource(vendor.names)),
            role = Role.Button,
        ) { stepsOpen = !stepsOpen }
    } else Modifier

    SettingsRow(
        title = stringResource(R.string.background_title),
        subtitle = if (allowed) null else stringResource(R.string.background_restricted),
        value = if (allowed) stringResource(R.string.settings_background_allowed) else null,
        icon = Symbols.BatteryChargingFull,
        modifier = fold,
        compact = true,
        trailing = when {
            foldable -> ({ SettingsChevron(if (stepsOpen) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown) })
            allowed -> ({ SettingsChevron(Symbols.Check) })
            else -> null
        },
    )
    if (!allowed || vendor != null) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            if (!allowed) {
                Pill(
                    text = stringResource(R.string.background_allow),
                    onClick = onAllow,
                    modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                    selected = true,
                )
            }

            if (vendor != null) {
                AnimatedVisibility(visible = !foldable || stepsOpen) {
                    Column(Modifier.padding(top = if (allowed) 2.dp else 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.background_vendor, stringResource(vendor.names)) +
                                " " + stringResource(vendor.steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        vendor.shortcuts.ifEmpty { listOf(null) }.forEach { shortcut ->
                            Pill(
                                text = stringResource(shortcut.labelWith(autostart)),
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
    compact: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val press = rememberGlassPress()
    if (compact) {
        SettingsRow(
            title = title, subtitle = subtitle, icon = icon, compact = true,
            modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
            trailing = { SettingsChevron(if (expanded) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown) },
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth(), resizing = true, press = press) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = null, indication = paneIndication()) { expanded = !expanded }
                    .glassPressOwnTouch(press)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIcon(icon)

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.syllables())
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
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().then(if (LocalTv.current) Modifier else Modifier.heightIn(min = 48.dp))
            .toggleable(value = checked, interactionSource = press, indication = LocalIndication.current, role = Role.Switch, onValueChange = onChange),
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

        DuckerSwitch(checked = checked, onDrag = onChange, pressed = press)
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
    /** A small mark beside a choice's label, by its value: «BETA» on Ducker Glass. */
    badges: Map<String, String> = emptyMap(),
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIcon(icon)
                Text(title, style = MaterialTheme.typography.titleSmall.syllables())
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val labelStyle = MaterialTheme.typography.labelLarge
                val measurer = rememberTextMeasurer()
                val badgeStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
                val widest = options.maxOf { (value, label) ->
                    measurer.measure(label, labelStyle).size.width +
                        (badges[value]?.let { with(density) { measurer.measure(it, badgeStyle).size.width + 18.dp.roundToPx() } } ?: 0)
                }
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
                        // On a glass card the choices are tinted as the glass's controls, the chosen one as its chosen.
                        val rest = if (LocalGlassBackdrop.current != null) scheme.surfaceVariant.copy(alpha = GlassMaterial.Control.tint)
                        else scheme.surfaceVariant
                        val press = rememberGlassPress()
                        val shape = MaterialTheme.shapes.extraLarge
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .then(
                                    if (press != null) Modifier.glassPress(press, own = true)
                                        .glass(LocalGlassBackdrop.current, GlassMaterial.Control, shape, press = press)
                                        .clip(shape)
                                        .background(if (chosen) chosenFill() else androidx.compose.ui.graphics.Color.Transparent)
                                    else Modifier.clip(shape).background(if (chosen) chosenFill() else rest),
                                )
                                .selectable(selected = chosen, interactionSource = null, indication = paneIndication(), role = Role.RadioButton, onClick = { onSelect(value) })
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val ink = if (chosen) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                            val badge = badges[value]
                            if (badge == null) {
                                Text(label, modifier = Modifier.fillMaxWidth(), style = labelStyle, textAlign = TextAlign.Center, color = ink)
                            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label, style = labelStyle, color = ink, maxLines = 1)
                                Text(
                                    badge,
                                    style = badgeStyle,
                                    color = ink.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    modifier = Modifier.border(1.dp, ink.copy(alpha = 0.35f), CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The glass style's backdrop: every choice as a small round sample of itself, the own picture as its thumbnail. Nine
 * text pills over two screens of scrolling showed none of them (F-388).
 */
@Composable
private fun BackdropCard(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        SettingsStore.BACKDROP_LIQUID to stringResource(R.string.glass_backdrop_liquid),
        SettingsStore.BACKDROP_FERN to stringResource(R.string.glass_backdrop_fern),
    )
    val scheme = MaterialTheme.colorScheme

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIcon(Symbols.Wallpaper)
                Text(stringResource(R.string.glass_backdrop), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    options.firstOrNull { it.first == selected }?.second.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) {
                options.forEach { (value, label) ->
                    val chosen = selected == value
                    val press = rememberGlassPress()
                    Box(
                        Modifier.size(SWATCH + 8.dp)
                            .glassPress(press, own = true)
                            .border(2.dp, if (chosen) scheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .border(1.dp, scheme.outlineVariant, CircleShape)
                            .selectable(selected = chosen, interactionSource = null, indication = paneIndication(), role = Role.RadioButton, onClick = { onSelect(value) })
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        PictureSample(BACKDROP_PICTURES.getValue(value))
                    }
                }
            }
        }
    }
}

/** A built-in picture's thumbnail. */
@Composable
private fun PictureSample(@DrawableRes picture: Int) {
    val resources = LocalResources.current
    val thumbnail by produceState<ImageBitmap?>(null, picture) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeResource(resources, picture, BitmapFactory.Options().apply { inSampleSize = 16 })?.asImageBitmap()
            }.getOrNull()
        }
    }

    thumbnail?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

private val SWATCH = 48.dp

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
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector,
    compact: Boolean = false,
) {
    if (compact) {
        SettingsSwitchRow(title, subtitle, icon, checked, onChange, compact = true)
        return
    }

    val press = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, interactionSource = press, indication = paneIndication(), role = Role.Switch, onValueChange = onChange),
        ownClick = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.syllables())
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DuckerSwitch(checked = checked, onDrag = onChange, pressed = press)
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, icon: ImageVector, compact: Boolean = false) {
    if (compact) {
        SettingsNavigationRow(title, icon, onClick, subtitle = subtitle, compact = true)
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = null, indication = paneIndication(), role = Role.Button, onClick = onClick),
        ownClick = true,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.syllables())
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

private val SettingsGroupShape = RoundedCornerShape(16.dp)
private val SettingsRowInset = 14.dp
private val SettingsIconSize = 19.dp
private val SettingsIconGap = 11.dp

private fun LazyListScope.settingsRows(
    key: String,
    @StringRes title: Int? = null,
    compact: Boolean,
    rows: List<@Composable () -> Unit>,
) {
    if (compact) {
        item(key = key) {
            SettingsNavigationGroup(title?.let { stringResource(it) } ?: "VPN", compact = true) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) SettingsNavigationDivider(compact = true)
                    row()
                }
            }
        }
    } else {
        rows.forEachIndexed { index, row -> item(key = "$key-$index") { row() } }
    }
}

@Composable
internal fun SettingsNavigationGroup(
    title: String,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = if (compact) 6.dp else 8.dp
    val shape = if (compact) SettingsGroupShape else MaterialTheme.shapes.large
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        Text(
            title,
            modifier = Modifier.padding(start = if (compact) 12.dp else 4.dp, top = if (compact) 2.dp else 4.dp)
                .semantics { heading() },
            style = if (compact) MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (compact && !glassPanesOn()) {
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface,
                border = airEdge(),
            ) {
                Column(content = content)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                surfaceModifier = Modifier.clip(shape),
                content = content,
            )
        }
    }
}

@Composable
internal fun SettingsNavigationDivider(compact: Boolean = false) {
    HorizontalDivider(
        Modifier.padding(start = if (compact) SettingsRowInset + SettingsIconSize + SettingsIconGap else 16.dp,
            end = if (compact) SettingsRowInset else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (compact) 0.75f else 0.45f),
    )
}

@Composable
private fun SettingsChevron(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    busy: Boolean = false,
    /** An action runs in place and only shows a progress indicator while it runs. */
    action: Boolean = false,
    compact: Boolean = false,
) {
    SettingsRow(
        title = title, subtitle = subtitle, value = value, icon = icon, compact = compact,
        modifier = Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        trailing = when {
            busy -> ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
            // Reserve the progress width so that the text doesn't reflow when it appears.
            action -> ({ Spacer(Modifier.size(20.dp)) })
            compact -> ({ SettingsChevron(Symbols.KeyboardArrowRight) })
            else -> ({ Icon(Symbols.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp)) })
        },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val typography = MaterialTheme.typography
    val titleStyle = if (compact) typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 21.sp)
    else typography.bodyLarge.syllables()
    val valueStyle = if (compact) typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp) else typography.bodyMedium
    val supportingStyle = if (compact) valueStyle else typography.bodySmall
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val inset = if (compact) SettingsRowInset else 16.dp
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 50.dp else 61.dp).then(modifier)
            .padding(horizontal = inset, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) SettingsIconGap else 12.dp),
    ) {
        if (compact) {
            if (icon == null) Spacer(Modifier.width(SettingsIconSize))
            else Icon(icon, contentDescription = null, modifier = Modifier.size(SettingsIconSize), tint = secondary)
        } else if (icon != null) {
            SettingsIcon(icon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
            if (value == null) {
                Text(title, style = titleStyle)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val measurer = rememberTextMeasurer()
                    val fits = with(LocalDensity.current) {
                        measurer.measure(title, titleStyle).size.width +
                            measurer.measure(value, valueStyle).size.width + 12.dp.roundToPx() <= maxWidth.roundToPx()
                    }
                    if (fits) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(title, Modifier.weight(1f), style = titleStyle)
                            Text(value, style = valueStyle, color = secondary)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = titleStyle)
                            Text(value, style = valueStyle, color = secondary)
                        }
                    }
                }
            }
            if (subtitle != null) Text(subtitle, style = supportingStyle, color = secondary)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    SettingsRow(
        title = title, subtitle = subtitle, icon = icon, compact = compact,
        modifier = Modifier.toggleable(value = checked, interactionSource = press,
            indication = LocalIndication.current, role = Role.Switch, onValueChange = onChange),
        trailing = {
            if (compact && !glassPanesOn()) {
                // Only the track is scaled. The full row remains the switch's touch and accessibility target.
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    DuckerSwitch(checked, modifier = Modifier.requiredWidth(52.dp).graphicsLayer {
                        scaleX = 0.82f
                        scaleY = 0.82f
                    })
                }
            } else {
                DuckerSwitch(checked = checked, onDrag = onChange, pressed = press)
            }
        },
    )
}

@Composable
private fun SettingsDetailsLink(@StringRes title: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(title), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun SettingsDetailsSheet(page: SettingsPage, open: Boolean, onDismiss: () -> Unit, onClosed: () -> Unit) {
    val connection = page == SettingsPage.Connection
    DuckerSheet(
        open = open,
        title = stringResource(if (connection) R.string.settings_connection_help_title else R.string.settings_privacy_help),
        busy = false, onDismiss = onDismiss, onClosed = onClosed,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (connection) {
                SettingsExplanation(R.string.auto_restart, R.string.auto_restart_hint)
                SettingsExplanation(R.string.always_on, R.string.always_on_hint)
            } else {
                SettingsExplanation(R.string.lock_sensitive, R.string.lock_sensitive_hint)
                SettingsExplanation(R.string.disguise, R.string.disguise_hint)
                SettingsExplanation(R.string.external_control, R.string.external_control_hint)
                SettingsExplanation(R.string.hwid_title, R.string.hwid_explained)
            }
        }
    }
}

@Composable
private fun SettingsExplanation(@StringRes title: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// The system's language, not the app's (which LocalConfiguration carries): read again each time the sheet opens.
@SuppressLint("NonObservableLocale")
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

/**
 * A row's title under an enlarged font: a word too long for the row is hyphenated by its syllables instead of being
 * broken anywhere — «Confidentialit / é» at 2.0 on a narrow phone in French (D-408). At the normal size the titles fit.
 */
@Composable
private fun TextStyle.syllables(): TextStyle =
    if (LocalDensity.current.fontScale > 1f) copy(hyphens = Hyphens.Auto) else this
