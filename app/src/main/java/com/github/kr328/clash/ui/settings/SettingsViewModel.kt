package com.github.kr328.clash.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.RestartReceiver
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.SubscriptionHeaders
import com.github.kr328.clash.update.Updater
import com.github.kr328.clash.util.backgroundAllowed
import com.github.kr328.clash.util.disguised
import com.github.kr328.clash.util.setDisguised
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** Внешний вид уже прочитан из своего хранилища — см. [refresh]. */
    private var seeded = false

    /**
     * Только флаг работы в фоне: человек возвращается из системного окна или настроек
     * батареи, и карточка должна показать новое состояние сразу (D-106).
     */
    fun refreshBackground(context: Context) {
        _state.update { it.copy(backgroundAllowed = context.backgroundAllowed()) }
    }

    /** «Постоянный VPN» включают в настройках Android — перечитываем, когда человек вернулся оттуда. */
    fun refreshAlwaysOn() {
        _state.update { it.copy(alwaysOn = alwaysOnVpn()) }
    }

    fun refresh(context: Context) {
        // Внешний вид — из своего хранилища и сразу (аудит, perf 5.1): остальное ниже ждёт
        // процесс службы, а при выключенном VPN он поднимается дольше, чем держится заставка, —
        // и экран собирался в теме системы, а потом перекрашивался. Только при первом чтении:
        // дальше состояние новее диска (см. setTheme).
        if (!seeded) {
            seeded = true

            SettingsStore(context).let { ours ->
                _state.update { it.copy(theme = ours.theme, oled = ours.oled, dynamicColor = ours.dynamicColor) }
            }
        }

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val ours = SettingsStore(context)
                val service = ServiceStore(context)

                val appVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: ""

                val pm = context.packageManager

                SettingsState(
                    theme = ours.theme,
                    // The whole tag: "zh-CN" and "pt-BR" are chosen with their region, and Android
                    // keeps Indonesian as "in" — the tag reads "id" again (D-320).
                    language = AppCompatDelegate.getApplicationLocales()
                        .takeIf { !it.isEmpty }?.get(0)?.toLanguageTag().orEmpty(),
                    oled = ours.oled,
                    dynamicColor = ours.dynamicColor,
                    allowExternalControl = ours.allowExternalControl,
                    ipv6 = service.allowIpv6,
                    appVersion = appVersion,
                    allowBypass = service.allowBypass,
                    systemProxy = service.systemProxy,
                    dnsHijacking = service.dnsHijacking,
                    bypassPrivate = service.bypassPrivateNetwork,
                    stack = service.tunStackMode,
                    trafficNotification = service.dynamicNotification,
                    hideRecents = ours.hideFromRecents,
                    betaUpdates = ours.betaUpdates,
                    disguise = context.disguised,
                    // Автозапуск — это состояние компонента, отдельного хранилища у него
                    // нет: включённый приёмник и есть настройка.
                    // Приёмник включён по умолчанию (D-129): выключен только явно.
                    autoRestart = pm.getComponentEnabledSetting(RestartReceiver::class.componentName) !=
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    alwaysOn = alwaysOnVpn(),
                    alwaysOnSettings = pm.resolveActivity(Intent(Settings.ACTION_VPN_SETTINGS), 0) != null,
                    backgroundAllowed = context.backgroundAllowed(),
                )
            }

            // Версию ядра и оверрайд спрашиваем у службы: там нативная библиотека уже
            // загружена (F-5). Впереди — выпуск mihomo, на котором стоит наша ветка ядра,
            // тот же, что в User-Agent.
            val core = runCatching { withClash { queryCoreVersion() } }.getOrDefault("")
                .let { if (it.isEmpty()) it else "mihomo ${SubscriptionHeaders.MIHOMO_RELEASE} ($it)" }

            val debugLog = runCatching {
                withClash { queryOverride(Clash.OverrideSlot.Persist) }.logLevel
            }.getOrNull() == LogMessage.Level.Debug

            // Что задал провайдер — чтобы показать рядом с выбором стека (D-122).
            val profileStack = runCatching { withClash { queryProfileNetwork(null) }.stack }.getOrDefault("")

            _state.value = loaded.copy(coreVersion = core, debugLog = debugLog, profileStack = profileStack)
        }
    }

    /**
     * Палитра меняется прямо под пальцем, поэтому состояние правим сразу, а на диск
     * пишем следом: `edit` дожидается перечитывания настроек вместе с опросом службы,
     * и накат волны (D-66) успел бы пройти по старой теме.
     */
    fun setTheme(context: Context, value: String) {
        _state.update { it.copy(theme = value) }

        edit(context) { it.theme = value }
    }

    /**
     * Язык хранит сама система (Android 13+) или AppCompat (раньше), своего хранилища не
     * заводим. Пустая метка возвращает язык системы.
     */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    fun setOled(context: Context, value: Boolean) = edit(context) { it.oled = value }

    fun setDynamicColor(context: Context, value: Boolean) = edit(context) { it.dynamicColor = value }

    fun setAllowExternalControl(context: Context, value: Boolean) {
        SettingsStore(context).allowExternalControl = value
        _state.update { it.copy(allowExternalControl = value) }
    }

    /**
     * IPv6 применяется при следующем запуске туннеля: настройка читается службой,
     * когда она поднимает TUN.
     */
    fun setAllowBypass(context: Context, value: Boolean) = service(context) { it.allowBypass = value }

    fun setSystemProxy(context: Context, value: Boolean) = service(context) { it.systemProxy = value }

    fun setDnsHijacking(context: Context, value: Boolean) = service(context) { it.dnsHijacking = value }

    fun setBypassPrivate(context: Context, value: Boolean) =
        service(context) { it.bypassPrivateNetwork = value }

    fun setStack(context: Context, value: String) = service(context) { it.tunStackMode = value }

    fun setTrafficNotification(context: Context, value: Boolean) =
        service(context) { it.dynamicNotification = value }

    fun setHideRecents(context: Context, value: Boolean) = edit(context) { it.hideFromRecents = value }

    /** Канал сменился — что предлагать, решается заново, а следом идёт свежая проверка. */
    fun setBetaUpdates(context: Context, value: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SettingsStore(context).betaUpdates = value }

            refresh(context)

            Updater.check()
        }
    }

    fun setDisguise(context: Context, value: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { context.setDisguised(value) }

            refresh(context)
        }
    }

    /** Приёмник включён — туннель поднимется после перезагрузки и обновления. */
    fun setAutoRestart(context: Context, value: Boolean) =
        component(context, RestartReceiver::class.componentName, enabled = value)

    /** Выключенный алиас убирает значок из списка приложений. */
    private fun component(context: Context, component: ComponentName, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.packageManager.setComponentEnabledSetting(
                    component,
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }

            refresh(context)
        }
    }

    private fun service(context: Context, block: (ServiceStore) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block(ServiceStore(context)) }

            refresh(context)
        }
    }

    fun setIpv6(context: Context, value: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ServiceStore(context).allowIpv6 = value }

            refresh(context)
        }
    }

    /**
     * Уровень журнала живёт в постоянном оверрайде ядра, а не в своих настройках: его же
     * правит экран оверрайдов CMFA, и два хранилища разошлись бы. Выключение возвращает
     * не «info», а пустое значение — тогда уровень снова задаёт профиль (инвариант 1).
     */
    fun setDebugLog(context: Context, value: Boolean) {
        viewModelScope.launch {
            runCatching {
                withClash {
                    val override = queryOverride(Clash.OverrideSlot.Persist)

                    override.logLevel = if (value) LogMessage.Level.Debug else null

                    patchOverride(Clash.OverrideSlot.Persist, override)

                    // Оверрайд применится при следующей загрузке конфига, а человек ждёт
                    // результата сейчас. Выключение возвращает уровень профиля — его
                    // ядро подставит само при перезагрузке, а пока ставим спокойный info.
                    setLogLevel(if (value) LogMessage.Level.Debug else LogMessage.Level.Info)
                }
            }

            refresh(context)
        }
    }

    private fun edit(context: Context, block: (SettingsStore) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block(SettingsStore(context)) }

            refresh(context)
        }
    }
}

/**
 * Android держит ducker «Постоянным VPN» (этап 10). Спросить может только само VPN-приложение,
 * пока его туннель поднят: система узнаёт владельца VPN по UID, а он у интерфейса и службы общий.
 * Экземпляр `VpnService` — только ради вызова, службой он не становится. Служба сама узнать не
 * успевает: включение при работающем туннеле его не перезапускает. Настройку Android
 * `always_on_vpn_app` на Android 16 приложению не прочесть. Туннель выключен — не знаем,
 * строка остаётся подсказкой.
 */
private fun alwaysOnVpn(): Boolean = runCatching { VpnService().isAlwaysOn }.getOrDefault(false)
