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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.ui.components.airEdge
import com.github.kr328.clash.ui.components.glassPanesOn
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
    val language = languages.firstOrNull { it.first == state.language && it.first.isNotEmpty() }?.second
        ?: stringResource(if (tv) R.string.language_system else R.string.settings_language_system)
    // Every opening starts at the page's top, as it was never opened: no scroll or unfolded part from the last time
    // (the owner, 24.11). A page still on screen keeps its state until it leaves.
    val navigate: (SettingsPage) -> Unit = {
        pages.removeState(it.name)
        destination = it.name
        sectionOpen = true
    }
    val openSupport = {
        pages.removeState(SettingsPage.Project.name)
        supportOpen = true
    }

    val pageContent: @Composable (SettingsPage, () -> Unit) -> Unit = { page, back ->
        pages.SaveableStateProvider(page.name) {
            val list = rememberLazyListState()
            val compact = !tv
            TvLazyColumn(
                state = list,
                overscrollEffect = null,
                modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp)
                    .then(if (LocalTv.current) Modifier.focusRestorer() else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (compact) SettingsGroupGap else 12.dp),
                contentPadding = PaddingValues(
                    top = (if (compact) 10.dp else 16.dp) + tvEdge(),
                    bottom = SettingsGroupGap + tvEdge() + navigationRoom(),
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
                                    // Always a second line, so the row keeps its height while a check starts.
                                    compact -> stringResource(R.string.settings_update_tap)
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
                                    onClick = openSupport,
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
                        // Ducker Glass is dark only and does not offer pure black.
                        val themeChoices = tv || !glassSupported || state.style != SettingsStore.STYLE_GLASS
                        // Only where the glass is drawn: phones with Android 13+. First, so that switching it changes
                        // only what lies below, never what is under the finger.
                        if (!tv && glassSupported) {
                            item(key = "appearance-style") {
                                ChipsCard(
                                    title = stringResource(R.string.style),
                                    icon = Symbols.Brush,
                                    options = listOf(
                                        SettingsStore.STYLE_AIR to stringResource(R.string.style_air),
                                        // The short name: «BETA» beside it says the rest (the owner, 24.11).
                                        SettingsStore.STYLE_GLASS to "Glass",
                                    ),
                                    selected = state.style,
                                    onSelect = onStyle,
                                    badges = mapOf(SettingsStore.STYLE_GLASS to "BETA"),
                                    compact = true,
                                    large = true,
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
                                    SettingsSurface(compact = true) {
                                        SettingsNavigationRow(
                                            title = stringResource(R.string.glass_image_pick),
                                            subtitle = stringResource(R.string.glass_image_pick_hint),
                                            onClick = onPickGlassImage,
                                            icon = Symbols.PhotoLibrary,
                                            compact = true,
                                        )
                                    }
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
                                    compact = true,
                                )
                            }
                        }

                        settingsRows("appearance-theme", compact = compact, rows = buildList {
                            if (themeChoices) {
                                add {
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
                                        compact = compact,
                                        grouped = compact,
                                    )
                                }
                                add {
                                    SwitchRow(
                                        title = stringResource(R.string.oled),
                                        subtitle = stringResource(if (compact) R.string.settings_oled_short else R.string.oled_hint),
                                        checked = state.oled,
                                        onChange = onOled, icon = Symbols.Contrast, compact = compact,
                                    )
                                }
                            }
                            // The wallpaper gives its colour from Android 8.1.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) add {
                                SwitchRow(
                                    title = stringResource(R.string.dynamic_color),
                                    subtitle = stringResource(if (compact) R.string.settings_dynamic_color_short else R.string.dynamic_color_hint),
                                    checked = state.dynamicColor,
                                    onChange = onDynamicColor, icon = Symbols.Palette, compact = compact,
                                )
                            }
                        })
                    }
                    SettingsPage.Connection -> {
                        // The apps that go through the VPN: what this page is opened for most.
                        settingsRows("connection-apps", compact = compact, rows = listOf({
                            LinkRow(
                                title = stringResource(R.string.access_control),
                                subtitle = stringResource(R.string.access_control_hint),
                                onClick = onAccessControl, icon = Symbols.Apps, compact = compact,
                            )
                        }))
                        settingsRows(
                            "connection-vpn", R.string.settings_group_vpn_work, compact,
                            footer = if (state.alwaysOnSettings) ({
                                SettingsFooter(R.string.settings_connection_help) { openDetails(SettingsPage.Connection) }
                            }) else null,
                            rows = buildList {
                                // TV has no background permission prompt (F-346).
                                if (!tv) add {
                                    BackgroundRow(state.backgroundAllowed, state.autostart, onAllowBackground, onVendor)
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
                            },
                        )
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

                                StackChoice(state.stack, state.profileStack, onStack)

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
                                RiskyLabel()

                                // A VPN sets the system proxy from Android 10.
                                val proxy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                if (proxy) InnerSwitch(
                                    title = stringResource(R.string.system_proxy),
                                    subtitle = stringResource(R.string.system_proxy_hint),
                                    checked = state.systemProxy,
                                    onChange = onSystemProxy,
                                    divided = false,
                                )
                                InnerSwitch(
                                    title = stringResource(R.string.allow_bypass),
                                    subtitle = stringResource(R.string.allow_bypass_hint),
                                    checked = state.allowBypass,
                                    onChange = onAllowBypass,
                                    divided = proxy,
                                )
                            }
                        }))
                    }
                    SettingsPage.Privacy -> {
                        settingsRows("privacy-phone", R.string.settings_privacy_protection, compact, rows = buildList {
                            // The TV cannot initiate a screen-lock check, but can turn off an existing setting.
                            if (!tv || state.lockSensitive) add {
                                val subtitle = when {
                                    !state.deviceSecure && compact -> R.string.settings_lock_needs_screen
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
                                    title = stringResource(if (compact) R.string.settings_disguise_title else R.string.disguise),
                                    subtitle = stringResource(if (compact) R.string.settings_disguise_short else R.string.disguise_hint),
                                    checked = state.disguise,
                                    onChange = onDisguise, icon = Symbols.Calculate, compact = compact,
                                )
                            }
                        })
                        settingsRows(
                            "privacy-access", R.string.settings_privacy_access, compact,
                            footer = { SettingsFooter(R.string.settings_privacy_more) { openDetails(SettingsPage.Privacy) } },
                            rows = listOf({
                                SwitchRow(
                                    title = stringResource(if (compact) R.string.settings_external_title else R.string.external_control),
                                    subtitle = stringResource(if (compact) R.string.settings_external_short else R.string.external_control_hint),
                                    checked = state.allowExternalControl,
                                    onChange = onExternalControl, icon = Symbols.SmartToy, compact = compact,
                                )
                            }, {
                                HwidCard(hwid, sendHwid, onSendHwid, compact)
                            }),
                        )
                    }
                    SettingsPage.Help -> {
                        settingsRows("help-diagnostics", R.string.settings_category_diagnostics, compact, rows = buildList {
                            add {
                                LinkRow(
                                    title = stringResource(R.string.logs),
                                    subtitle = stringResource(if (compact) R.string.settings_logs_short else R.string.logs_hint),
                                    onClick = onLogs, icon = Symbols.BugReport, compact = compact,
                                )
                            }
                            add {
                                LinkRow(
                                    title = stringResource(R.string.probe),
                                    subtitle = stringResource(if (compact) R.string.settings_probe_short else R.string.probe_hint),
                                    onClick = onProbe, icon = Symbols.Speed, compact = compact,
                                )
                            }
                            // The web panel is designed for touch and does not handle remote Back on TV.
                            if (!tv) add {
                                SettingsNavigationRow(
                                    title = stringResource(R.string.dashboard),
                                    subtitle = stringResource(R.string.dashboard_hint),
                                    onClick = onDashboard, icon = Symbols.Monitoring, compact = true,
                                )
                            }
                        })
                        settingsRows("help-support", R.string.support, compact, rows = listOf({
                            LinkRow(
                                title = stringResource(R.string.community),
                                subtitle = stringResource(if (compact) R.string.settings_community_short else R.string.community_hint),
                                onClick = { onLink(COMMUNITY) }, icon = Symbols.Forum,
                                compact = compact, destinationIcon = Symbols.OpenInNew,
                            )
                        }, {
                            LinkRow(
                                title = stringResource(R.string.mail),
                                subtitle = MAIL, onClick = onMail, icon = Symbols.Email,
                                compact = compact, destinationIcon = Symbols.OpenInNew,
                            )
                        }))
                    }
                    SettingsPage.Project -> {
                        projectSupportItems(compact = compact, onLink = onLink, onMail = onMail)
                    }
                    SettingsPage.About -> {
                        aboutItems(state.appVersion, state.coreVersion, compact = compact, onLink = onLink, onSupport = openSupport)
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
            // A section turns in from the side like a page; on TV it keeps the plain fade.
            slide = !tv,
        ) {
            pageContent(SettingsPage.valueOf(destination)) { sectionOpen = false }
        }
        BackLayer(visible = supportOpen, enabled = active, onBack = { supportOpen = false }, fade = false, slide = !tv) {
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
                title = stringResource(R.string.settings_hwid_title),
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
                    trailing = { DisclosureChevron(shown) },
                )
                // Composed only while open or closing, so the identifier still exists only on request.
                SettingsDisclosure(visible = shown) {
                    SelectionContainer(Modifier.fillMaxWidth().padding(start = SettingsLabelInset,
                        end = SettingsRowInset, bottom = SettingsBodyInset)) {
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
                modifier = if (compact) Modifier.padding(start = SettingsLabelInset,
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

/** Android permission and the device maker's own instructions are independent (D-106, D-107). */
@Composable
private fun BackgroundRow(allowed: Boolean, autostart: Boolean?, onAllow: () -> Unit, onVendor: (Shortcut?) -> Unit) {
    val vendor = Vendor.current
    var stepsOpen by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = stringResource(R.string.background_title),
        subtitle = if (allowed) null else stringResource(R.string.background_restricted),
        value = if (allowed) stringResource(R.string.settings_background_allowed) else null,
        icon = Symbols.BatteryChargingFull,
        compact = true,
        trailing = if (allowed) ({ SettingsChevron(Symbols.Check) }) else null,
    )
    if (!allowed) {
        SettingsNavigationDivider(compact = true)
        SettingsNavigationRow(
            title = stringResource(R.string.background_allow),
            icon = null, onClick = onAllow, compact = true, destinationIcon = Symbols.OpenInNew,
        )
    }
    if (vendor != null) {
        SettingsNavigationDivider(compact = true)
        SettingsRow(
            title = stringResource(R.string.settings_background_steps, stringResource(vendor.names)),
            icon = Symbols.Smartphone,
            compact = true,
            modifier = Modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.background_vendor_steps, stringResource(vendor.names)),
            ) { stepsOpen = !stepsOpen },
            trailing = { DisclosureChevron(stepsOpen) },
        )
        SettingsDisclosure(visible = stepsOpen) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(vendor.steps),
                    modifier = Modifier.padding(start = SettingsLabelInset, end = SettingsRowInset, bottom = SettingsBodyInset),
                    style = settingsSupportingStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vendor.shortcuts.ifEmpty { listOf(null) }.forEach { shortcut ->
                    SettingsNavigationDivider(compact = true)
                    SettingsNavigationRow(
                        title = stringResource(if (shortcut == null) R.string.settings_background_settings
                            else shortcut.labelWith(autostart)),
                        icon = null, onClick = { onVendor(shortcut) }, compact = true, destinationIcon = Symbols.OpenInNew,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDisclosure(visible: Boolean, content: @Composable () -> Unit) {
    // Keep the full width throughout the transition, clipping only the height below the header.
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = expandVertically(tween(DISCLOSURE_OPEN_MS, easing = LinearOutSlowInEasing), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(DISCLOSURE_CLOSE_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
    ) { content() }
}

private const val DISCLOSURE_OPEN_MS = 200
private const val DISCLOSURE_CLOSE_MS = 160

/** The disclosure arrow: turns over as the part below opens or closes, on the same timing. */
@Composable
private fun DisclosureChevron(open: Boolean) {
    val turn by animateFloatAsState(
        if (open) 180f else 0f,
        tween(if (open) DISCLOSURE_OPEN_MS else DISCLOSURE_CLOSE_MS, easing = FastOutSlowInEasing),
        label = "chevron",
    )
    Icon(
        Symbols.KeyboardArrowDown, contentDescription = null,
        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    if (compact) {
        val reveal = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        Column(Modifier.bringIntoViewRequester(reveal)) {
            SettingsRow(
                title = title, subtitle = subtitle, icon = icon, compact = true,
                modifier = Modifier.clickable(role = Role.Button) {
                    expanded = !expanded
                    // The block ends the page: once it has opened, bring what opened into view.
                    if (expanded) scope.launch {
                        delay(DISCLOSURE_OPEN_MS.toLong())
                        reveal.bringIntoView()
                    }
                },
                trailing = { DisclosureChevron(expanded) },
            )
            // The rows draw their own dividers and insets, as the group's other rows do.
            SettingsDisclosure(visible = expanded) {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
        return
    }

    val press = rememberGlassPress()
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
    /** On a phone: a divider above, as between the group's rows; not under a label of its own. */
    divided: Boolean = true,
) {
    if (!LocalTv.current) {
        if (divided) SettingsNavigationDivider(compact = true)
        SettingsSwitchRow(title, subtitle, icon = null, checked = checked, onChange = onChange, compact = true)
        return
    }
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth()
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

        SettingsSwitch(checked, onChange, press, compact = false)
    }
}

/** The core's network stack; what the provider set is shown on its own choice (D-122). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackChoice(stack: String, profileStack: String, onStack: (String) -> Unit) {
    val compact = !LocalTv.current
    if (compact) SettingsNavigationDivider(compact = true)
    Column(
        if (compact) Modifier.fillMaxWidth().padding(start = SettingsLabelInset, end = SettingsRowInset, top = 10.dp, bottom = SettingsBodyInset)
        else Modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) SettingsTextGap else 6.dp),
    ) {
        Text(
            text = stringResource(R.string.stack_mode),
            style = if (compact) settingsTitleStyle() else MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.stack_mode_hint),
            style = if (compact) settingsSupportingStyle() else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Четыре чипа в одну строку на узком экране не влезают, поэтому
        // FlowRow: перенесётся, а не обрежется.
        FlowRow(
            modifier = Modifier.selectableGroup().then(if (compact) Modifier.padding(top = 8.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 0.dp),
        ) {
            for (value in listOf("", "system", "gvisor", "mixed", "mips")) {
                Pill(
                    text = value.ifEmpty {
                        if (profileStack.isEmpty()) stringResource(R.string.stack_from_profile)
                        else stringResource(R.string.stack_from_profile_value, profileStack)
                    },
                    onClick = { onStack(value) },
                    selected = stack == value,
                    onCard = true,
                    role = Role.RadioButton,
                )
            }
        }
    }
}

/** The label over the switches that change the traffic's own route. */
@Composable
private fun RiskyLabel() {
    val compact = !LocalTv.current
    if (compact) SettingsNavigationDivider(compact = true) else HorizontalDivider()
    Text(
        text = stringResource(R.string.advanced_risky),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (compact) Modifier.padding(start = SettingsLabelInset, end = SettingsRowInset, top = 14.dp) else Modifier,
    )
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
    compact: Boolean = false,
    grouped: Boolean = false,
    /** The style's choice: a larger track, always one row, see [SegmentTrack]. */
    large: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(
            if (compact) Modifier else Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp),
        ) {
            if (compact) {
                SettingsRow(title, compact = true, icon = icon)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsIcon(icon)
                    Text(title, style = MaterialTheme.typography.titleSmall.syllables())
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().then(
                if (compact) Modifier.padding(start = SettingsRowInset, end = SettingsRowInset, bottom = 8.dp)
                else Modifier,
            )) {
                val density = LocalDensity.current
                val labelStyle = MaterialTheme.typography.labelLarge
                val measurer = rememberTextMeasurer()
                val badgeStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
                val widest = options.maxOf { (value, label) ->
                    measurer.measure(label, labelStyle).size.width +
                        (badges[value]?.let { with(density) { measurer.measure(it, badgeStyle).size.width + 18.dp.roundToPx() } } ?: 0)
                }
                val columns = with(density) {
                    val room = maxWidth.roundToPx()
                    if (compact) {
                        // A track's segment: its share of the track inside the pill's inset, less 8 dp each side.
                        if (widest + 16.dp.roundToPx() <= (room - 2 * SegmentPillInset.roundToPx()) / options.size) options.size else 1
                    } else if ((widest + 12.dp.roundToPx()) * options.size + 8.dp.roundToPx() * (options.size - 1) <= room) options.size else 1
                }
                if (compact && large) {
                    SegmentTrack(
                        options, selected, onSelect, badges, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        badgeStyle, large = true,
                    )
                } else if (compact && columns == options.size) {
                    SegmentTrack(options, selected, onSelect, badges, labelStyle, badgeStyle)
                } else FlowRow(
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
                            modifier = Modifier.weight(1f).heightIn(min = SettingsMinTouch)
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
                            } else FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = labelStyle, color = ink)
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
    if (grouped) content() else SettingsSurface(compact) { content() }
}

/**
 * A few choices as one track, the chosen one a pill that rides to it: lighter than a separate pill per choice
 * (the owner, 24.11). The row is a whole touch target tall; the track is drawn 4 dp in from its top and bottom.
 */
@Composable
private fun SegmentTrack(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    badges: Map<String, String>,
    labelStyle: TextStyle,
    badgeStyle: TextStyle,
    /**
     * Taller, and the pill moves at once: the style's choice redraws the whole app, which would cut the ride short
     * (the owner, 24.11).
     */
    large: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val ride by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "segment")
    val position = if (large) index.toFloat() else ride
    val track = if (LocalGlassBackdrop.current != null) scheme.surfaceVariant.copy(alpha = GlassMaterial.Control.tint)
    else scheme.surfaceVariant
    val pill = chosenFill()
    Row(
        Modifier.fillMaxWidth().height(if (large) SegmentLargeHeight else SettingsMinTouch).selectableGroup().drawBehind {
            val inset = SegmentTrackInset.toPx()
            val pad = (if (large) SegmentLargePillInset else SegmentPillInset).toPx()
            val height = size.height - 2 * inset
            drawRoundRect(track, Offset(0f, inset), Size(size.width, height), CornerRadius(height / 2))
            val segment = (size.width - 2 * pad) / options.size
            val pillHeight = height - 2 * pad
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - pad - segment * (position + 1)
            else pad + segment * position
            drawRoundRect(pill, Offset(left, inset + pad), Size(segment, pillHeight), CornerRadius(pillHeight / 2))
        },
    ) {
        options.forEachIndexed { i, (value, label) ->
            val chosen = i == index
            val target = if (chosen) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            val fading by animateColorAsState(target, tween(180), label = "ink")
            val ink = if (large) target else fading
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = chosen, interactionSource = null, indication = null, role = Role.RadioButton) {
                        if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(value)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val badge = badges[value]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = labelStyle, color = ink, maxLines = 1)
                    if (badge != null) Text(
                        badge, style = badgeStyle, color = ink.copy(alpha = 0.8f), maxLines = 1,
                        modifier = Modifier.border(1.dp, ink.copy(alpha = 0.35f), CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private val SegmentTrackInset = 4.dp
private val SegmentPillInset = 3.dp
/** The style's track: 48 dp drawn, 8 dp taller than the theme's. */
private val SegmentLargeHeight = 56.dp
private val SegmentLargePillInset = 4.dp

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

    SettingsSurface(compact = true) {
        SettingsRow(
            stringResource(R.string.glass_backdrop), compact = true, icon = Symbols.Wallpaper,
            value = options.firstOrNull { it.first == selected }?.second,
        )
        Column(Modifier.padding(start = SettingsRowInset, end = SettingsRowInset, bottom = SettingsBodyInset)) {
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
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector,
    compact: Boolean = false,
    destinationIcon: ImageVector = Symbols.KeyboardArrowRight,
) {
    if (compact) {
        SettingsNavigationRow(title, icon, onClick, subtitle = subtitle, compact = true, destinationIcon = destinationIcon)
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
internal val SettingsGroupGap = 16.dp
internal val SettingsRowInset = 14.dp
internal val SettingsBodyInset = 12.dp
internal val SettingsTextGap = 2.dp
internal val SettingsMinTouch = 48.dp
internal val SettingsIconSize = 19.dp
private val SettingsIconGap = 11.dp
private val SettingsLabelInset = SettingsRowInset + SettingsIconSize + SettingsIconGap

@Composable
internal fun settingsTitleStyle() =
    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 21.sp).syllables()

@Composable
internal fun settingsSupportingStyle() =
    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp)

private fun LazyListScope.settingsRows(
    key: String,
    @StringRes title: Int? = null,
    compact: Boolean,
    /** A phone's explanation link right under the group, see [SettingsFooter]. */
    footer: (@Composable () -> Unit)? = null,
    rows: List<@Composable () -> Unit>,
) {
    if (compact) {
        item(key = key) {
            Column {
                SettingsNavigationGroup(title?.let { stringResource(it) }, compact = true) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) SettingsNavigationDivider(compact = true)
                        row()
                    }
                }
                footer?.invoke()
            }
        }
    } else {
        rows.forEachIndexed { index, row -> item(key = "$key-$index") { row() } }
    }
}

@Composable
internal fun SettingsSectionHeading(title: String, compact: Boolean) {
    Text(
        title,
        modifier = Modifier.padding(start = if (compact) SettingsRowInset else 4.dp, top = if (compact) 2.dp else 4.dp)
            .semantics { heading() },
        style = if (compact) settingsSupportingStyle().copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SettingsNavigationGroup(
    title: String?,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title == null) {
        SettingsSurface(compact, content)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            SettingsSectionHeading(title, compact)
            SettingsSurface(compact, content)
        }
    }
}

@Composable
internal fun SettingsSurface(compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = if (compact) SettingsGroupShape else MaterialTheme.shapes.large
    if (compact && !glassPanesOn()) {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface,
            border = airEdge(),
        ) { Column(content = content) }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = shape,
            surfaceModifier = Modifier.clip(shape), content = content,
        )
    }
}

@Composable
internal fun SettingsNavigationDivider(compact: Boolean = false, reserveIcon: Boolean = true) {
    val inset = if (compact) SettingsRowInset else 16.dp
    HorizontalDivider(
        Modifier.padding(start = if (compact && reserveIcon) SettingsLabelInset else inset, end = inset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (compact) 0.75f else 0.45f),
    )
}

@Composable
private fun SettingsChevron(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsNavigationRow(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    busy: Boolean = false,
    /** An action runs in place and only shows a progress indicator while it runs. */
    action: Boolean = false,
    compact: Boolean = false,
    destinationIcon: ImageVector = Symbols.KeyboardArrowRight,
) {
    SettingsRow(
        title = title, subtitle = subtitle, value = value, icon = icon, compact = compact,
        modifier = Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        trailing = when {
            busy -> ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
            // Reserve the progress width so that the text doesn't reflow when it appears.
            action -> ({ Spacer(Modifier.size(20.dp)) })
            compact -> ({ SettingsChevron(destinationIcon) })
            else -> ({ Icon(destinationIcon, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (destinationIcon == Symbols.OpenInNew) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.material3.LocalContentColor.current) })
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
    reserveIcon: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val typography = MaterialTheme.typography
    val titleStyle = if (compact) settingsTitleStyle()
    else typography.bodyLarge.syllables()
    val valueStyle = if (compact) settingsSupportingStyle() else typography.bodyMedium
    val supportingStyle = if (compact) valueStyle else typography.bodySmall
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val inset = if (compact) SettingsRowInset else 16.dp
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 50.dp else 61.dp).then(modifier)
            .padding(horizontal = inset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) SettingsIconGap else 12.dp),
    ) {
        if (compact) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(SettingsIconSize), tint = secondary)
            else if (reserveIcon) Spacer(Modifier.width(SettingsIconSize))
        } else if (icon != null) {
            SettingsIcon(icon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) SettingsTextGap else 3.dp)) {
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
                        Column(verticalArrangement = Arrangement.spacedBy(SettingsTextGap)) {
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
    icon: ImageVector?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    SettingsRow(
        title = title, subtitle = subtitle, icon = icon, compact = compact,
        modifier = Modifier.toggleable(value = checked, interactionSource = press,
            indication = LocalIndication.current, role = Role.Switch, onValueChange = onChange),
        trailing = { SettingsSwitch(checked, onChange, press, compact) },
    )
}

@Composable
private fun SettingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, press: MutableInteractionSource, compact: Boolean) {
    if (compact && !glassPanesOn()) {
        // The row handles input. Scale just the visible track.
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            DuckerSwitch(checked, modifier = Modifier.requiredWidth(52.dp).graphicsLayer {
                scaleX = 0.82f
                scaleY = 0.82f
            })
        }
    } else {
        DuckerSwitch(checked = checked, onDrag = onChange, pressed = press)
    }
}

/**
 * A group's footer that opens the longer explanation: its icon on the rows' icon line and its text on their labels'
 * line, the same room above and below the text, so the next group does not start after a gap (the owner, 24.11).
 */
@Composable
private fun SettingsFooter(@StringRes text: Int, onClick: () -> Unit) {
    val style = settingsSupportingStyle()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SettingsRowInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(SettingsIconGap),
    ) {
        Box(Modifier.width(SettingsIconSize).height(with(LocalDensity.current) { style.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
            Icon(Symbols.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = secondary)
        }
        Text(stringResource(text), Modifier.weight(1f), style = style, color = secondary)
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
        // The page's order and the rows' own names, so that each paragraph is found by its row.
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (connection) {
                SettingsExplanation(R.string.auto_restart, R.string.auto_restart_hint)
                SettingsExplanation(R.string.always_on, R.string.always_on_hint)
            } else {
                SettingsExplanation(R.string.lock_sensitive, R.string.lock_sensitive_hint)
                SettingsExplanation(R.string.hide_recents, R.string.hide_recents_hint)
                SettingsExplanation(R.string.settings_disguise_title, R.string.disguise_hint)
                SettingsExplanation(R.string.settings_external_title, R.string.external_control_hint)
                SettingsExplanation(R.string.settings_hwid_title, R.string.hwid_explained)
            }
        }
    }
}

@Composable
private fun SettingsExplanation(@StringRes title: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = settingsTitleStyle(), modifier = Modifier.semantics { heading() })
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
    val compact = !LocalTv.current
    val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    val systemName = systemLocale.getDisplayLanguage(systemLocale)
    DuckerSheet(
        open = open, title = stringResource(R.string.language), busy = false,
        heightFraction = 0.78f, onDismiss = onDismiss, onClosed = onClosed,
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SettingsBodyInset)) {
            SettingsSurface(compact) {
                LanguageChoice(
                    label = stringResource(R.string.settings_language_system),
                    hint = stringResource(R.string.settings_system_language, systemName),
                    selected = selected.isEmpty(), compact = compact, onSelect = { onSelect("") },
                )
            }
            SettingsSurface(compact) {
                options.filter { it.first.isNotEmpty() }.forEachIndexed { index, (value, label) ->
                    if (compact && index > 0) SettingsNavigationDivider(compact = true, reserveIcon = false)
                    LanguageChoice(label, selected = selected == value, compact = compact, onSelect = { onSelect(value) })
                }
            }
            Text(
                stringResource(R.string.settings_language_immediate),
                modifier = Modifier.padding(horizontal = if (compact) SettingsRowInset else 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, compact: Boolean, onSelect: () -> Unit, hint: String? = null) {
    if (compact) {
        SettingsRow(
            title = label, subtitle = hint, compact = true, reserveIcon = false,
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            trailing = { if (selected) SettingsChevron(Symbols.Check) else Spacer(Modifier.size(16.dp)) },
        )
        return
    }


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
