package com.github.kr328.clash.ui.settings

import android.net.Uri
import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
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
import com.github.kr328.clash.ui.components.SensitiveLock
import com.github.kr328.clash.update.Updater
import com.github.kr328.clash.util.backgroundAllowed
import com.github.kr328.clash.util.miuiAutostart
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
        _state.update { it.copy(backgroundAllowed = context.backgroundAllowed(), autostart = context.miuiAutostart()) }
    }

    /** A screen lock is set in Android's settings: read again when the person comes back from there. */
    fun refreshLock(context: Context) {
        _state.update { it.copy(deviceSecure = SensitiveLock.available(context)) }
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
                // Seed the lock before a covered screen can open while the rest loads.
                _state.update {
                    it.copy(
                        theme = ours.theme, oled = ours.oled, dynamicColor = ours.dynamicColor,
                        lockSensitive = ours.lockSensitive,
                        style = ours.style,
                        glassBackdrop = ours.glassBackdrop, glassBlur = ours.glassBlur,
                        glassImage = glassImageTime(context), glassImageDark = ours.glassImageDark,
                        glassImageTopDark = ours.glassImageTopDark ?: false,
                        glassRefraction = ours.glassRefraction, glassDispersion = ours.glassDispersion,
                        glassDepth = ours.glassDepth, glassHighlight = ours.glassHighlight,
                        glassNoise = ours.glassNoise, glassVibrancy = ours.glassVibrancy,
                        glassTint = ours.glassTint,
                    )
                }
            }
        }

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val ours = SettingsStore(context)
                val service = ServiceStore(context)

                // A picture chosen before its top was measured, its darkness judged by the old threshold: measured again
                // here, before the state below is read, as that state replaces the whole of the current one.
                if (ours.glassBackdrop == SettingsStore.BACKDROP_IMAGE && ours.glassImageTopDark == null) remeasureGlassImage(context)

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
                    style = ours.style,
                    glassBackdrop = ours.glassBackdrop,
                    glassBlur = ours.glassBlur,
                    glassImage = glassImageTime(context),
                    glassImageDark = ours.glassImageDark,
                    glassImageTopDark = ours.glassImageTopDark ?: false,
                    glassRefraction = ours.glassRefraction,
                    glassDispersion = ours.glassDispersion,
                    glassDepth = ours.glassDepth,
                    glassHighlight = ours.glassHighlight,
                    glassNoise = ours.glassNoise,
                    glassVibrancy = ours.glassVibrancy,
                    glassTint = ours.glassTint,
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
                    lockSensitive = ours.lockSensitive,
                    deviceSecure = SensitiveLock.available(context),
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
                    autostart = context.miuiAutostart(),
                )
            }

            // The mihomo release our core branch stands on, the same as in the User-Agent; the owner asked for
            // the version without the build hash (D-525). The override is asked of the service, where the
            // native library is already loaded (F-5).
            val core = "mihomo ${SubscriptionHeaders.MIHOMO_RELEASE}"

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
        // The screen is not recreated by a new language (D-508), so nothing reads it back.
        _state.update { it.copy(language = tag) }

        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    fun setOled(context: Context, value: Boolean) = edit(context) { it.oled = value }

    fun setDynamicColor(context: Context, value: Boolean) = edit(context) { it.dynamicColor = value }

    /**
     * The look and the glass's settings: the state first, so the screen behind follows the finger as with the theme,
     * then the store, on this thread so that a slider's last value is the one kept.
     */
    fun setStyle(context: Context, value: String) {
        _state.update { it.copy(style = value) }
        SettingsStore(context).style = value
    }

    fun setGlassBackdrop(context: Context, value: String) {
        val store = SettingsStore(context)
        if (value == SettingsStore.BACKDROP_NONE) {
            runCatching { File(context.filesDir, SettingsStore.GLASS_IMAGE_FILE).delete() }
            store.apply {
                glassBackdrop = value
                glassImageDark = false
                glassImageTopDark = null
            }
            _state.update { it.copy(glassImage = 0L, glassImageDark = false, glassImageTopDark = false).withGlass(store) }
        } else {
            store.glassBackdrop = value
            // The blur and the tuning default differently over an own picture.
            _state.update { it.withGlass(store) }
        }
    }

    /**
     * Experiment: the picked picture becomes Home's backdrop. Copied in, fitted to the screen's long side, and
     * measured: a dark one takes the app dark, as the black backdrops do.
     */
    fun setGlassImage(context: Context, uri: Uri) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

        viewModelScope.launch {
            val tone = withContext(Dispatchers.IO) {
                runCatching {
                    val screen = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val long = maxOf(info.size.width, info.size.height)
                        if (long > screen) {
                            val scale = screen.toFloat() / long
                            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }

                    File(context.filesDir, SettingsStore.GLASS_IMAGE_FILE).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }

                    pictureTone(bitmap, context)
                }.getOrNull()
            } ?: return@launch

            val store = SettingsStore(context).apply {
                glassImageDark = tone.first
                glassImageTopDark = tone.second
                glassBackdrop = SettingsStore.BACKDROP_IMAGE
            }
            _state.update {
                it.copy(glassImage = glassImageTime(context), glassImageDark = tone.first, glassImageTopDark = tone.second)
                    .withGlass(store)
            }
        }
    }

    /** The own picture already in place, measured again from its file; on a background thread. */
    private fun remeasureGlassImage(context: Context) {
        val tone = runCatching {
            BitmapFactory.decodeFile(File(context.filesDir, SettingsStore.GLASS_IMAGE_FILE).path)?.let { pictureTone(it, context) }
        }.getOrNull() ?: return

        SettingsStore(context).apply {
            glassImageDark = tone.first
            glassImageTopDark = tone.second
        }
    }

    /**
     * Whether [bitmap] is dark on average (the theme over it) and at its top as the screen shows it, under the status
     * bar and the headers. The picture fills the window cropped to its middle (`glassImage`), so the top is measured
     * there.
     */
    private fun pictureTone(bitmap: Bitmap, context: Context): Pair<Boolean, Boolean> {
        fun dark(part: Bitmap): Boolean {
            val small = Bitmap.createScaledBitmap(part, 24, 24, true)
            val pixels = IntArray(24 * 24).also { small.getPixels(it, 0, 24, 0, 0, 24, 24) }
            return pixels.map { Color.luminance(it) }.average() < DARK_LUMINANCE
        }

        val metrics = context.resources.displayMetrics
        // The glass is a phone's style, and a phone holds it upright.
        val width = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val height = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val scale = maxOf(width / bitmap.width, height / bitmap.height)
        val shownWidth = (width / scale).toInt().coerceIn(1, bitmap.width)
        val shownHeight = (height / scale).toInt().coerceIn(1, bitmap.height)
        val left = (bitmap.width - shownWidth) / 2
        val top = (bitmap.height - shownHeight) / 2
        val band = Bitmap.createBitmap(bitmap, left, top, shownWidth, (shownHeight * TOP_BAND).toInt().coerceAtLeast(1))

        return dark(bitmap) to dark(band)
    }

    /** The blur and the tuning as the store has them now: their defaults follow the backdrop. */
    private fun SettingsState.withGlass(store: SettingsStore) = copy(
        glassBackdrop = store.glassBackdrop,
        glassBlur = store.glassBlur,
        glassRefraction = store.glassRefraction,
        glassDispersion = store.glassDispersion,
        glassDepth = store.glassDepth,
        glassHighlight = store.glassHighlight,
        glassNoise = store.glassNoise,
        glassVibrancy = store.glassVibrancy,
        glassTint = store.glassTint,
    )

    private fun glassImageTime(context: Context): Long =
        File(context.filesDir, SettingsStore.GLASS_IMAGE_FILE).lastModified()

    fun setGlassBlur(context: Context, value: Int) {
        _state.update { it.copy(glassBlur = value) }
        SettingsStore(context).glassBlur = value
    }

    fun setGlassRefraction(context: Context, value: Int) {
        _state.update { it.copy(glassRefraction = value) }
        SettingsStore(context).glassRefraction = value
    }

    fun setGlassDispersion(context: Context, value: Int) {
        _state.update { it.copy(glassDispersion = value) }
        SettingsStore(context).glassDispersion = value
    }

    fun setGlassDepth(context: Context, value: Int) {
        _state.update { it.copy(glassDepth = value) }
        SettingsStore(context).glassDepth = value
    }

    fun setGlassHighlight(context: Context, value: Int) {
        _state.update { it.copy(glassHighlight = value) }
        SettingsStore(context).glassHighlight = value
    }

    fun setGlassNoise(context: Context, value: Int) {
        _state.update { it.copy(glassNoise = value) }
        SettingsStore(context).glassNoise = value
    }

    fun setGlassVibrancy(context: Context, value: Int) {
        _state.update { it.copy(glassVibrancy = value) }
        SettingsStore(context).glassVibrancy = value
    }

    fun setGlassTint(context: Context, value: Int) {
        _state.update { it.copy(glassTint = value) }
        SettingsStore(context).glassTint = value
    }

    fun resetGlassTuning(context: Context) {
        val store = SettingsStore(context).apply { resetGlassTuning() }
        _state.update { it.withGlass(store) }
    }

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

    fun setLockSensitive(context: Context, value: Boolean) {
        _state.update { it.copy(lockSensitive = value) }
        edit(context) { it.lockSensitive = value }
    }

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
private fun alwaysOnVpn(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
    runCatching { VpnService().isAlwaysOn }.getOrDefault(false)
/**
 * Below this average relative luminance a picture counts as dark. 0.18 is where white and black letters have the same
 * contrast; the old 0.45 (about sRGB 180, a light grey) took snow and clouds for dark (F-388).
 */
private const val DARK_LUMINANCE = 0.2

/** The part of the screen's height under the status bar and the tabs' headers. */
private const val TOP_BAND = 0.12f
