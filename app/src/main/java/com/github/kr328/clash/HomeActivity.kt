package com.github.kr328.clash

import com.github.kr328.clash.ui.components.Symbols
import android.content.ClipData
import android.content.ClipboardManager
import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import android.content.Intent
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.github.kr328.clash.ui.components.BackLayer
import com.github.kr328.clash.ui.components.ScreenFrame
import com.github.kr328.clash.ui.components.TvFocus
import androidx.compose.ui.focus.focusRequester
import com.github.kr328.clash.ui.home.ThemeButton
import com.github.kr328.clash.ui.home.BrandTitle
import com.github.kr328.clash.ui.components.TvTool
import com.github.kr328.clash.ui.components.tvCanvas
import com.github.kr328.clash.ui.components.focusRoom
import com.github.kr328.clash.ui.components.pageBoundary
import com.github.kr328.clash.ui.components.TvPagerScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import com.github.kr328.clash.ui.subscriptions.AddSubscriptionButton
import com.github.kr328.clash.util.publishShortcuts
import androidx.annotation.RequiresApi
import android.graphics.drawable.Icon
import android.content.ComponentName
import android.app.StatusBarManager
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalFocusManager
import com.github.kr328.clash.ui.components.TABS
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.ui.home.HomeScreen
import com.github.kr328.clash.ui.home.NodePicker
import com.github.kr328.clash.ui.rules.RulesScreen
import com.github.kr328.clash.ui.config.ConfigScreen
import com.github.kr328.clash.ui.config.ConfigViewModel
import com.github.kr328.clash.ui.config.lowWindow
import com.github.kr328.clash.ui.home.HomeViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import com.github.kr328.clash.ui.proxy.ProxyScreen
import com.github.kr328.clash.ui.settings.SettingsScreen
import com.github.kr328.clash.ui.settings.SettingsStore
import com.github.kr328.clash.ui.settings.SettingsViewModel
import com.github.kr328.clash.ui.apps.AppsScreen
import com.github.kr328.clash.ui.logs.LogsScreen
import com.github.kr328.clash.ui.logs.LogsViewModel
import com.github.kr328.clash.ui.apps.AppsViewModel
import com.github.kr328.clash.ui.probe.ProbeScreen
import com.github.kr328.clash.ui.probe.ProbeViewModel
import com.github.kr328.clash.ui.proxy.ProxyViewModel
import com.github.kr328.clash.ui.subscriptions.AddSubscriptionDialog
import com.github.kr328.clash.ui.subscriptions.InstallLink
import com.github.kr328.clash.ui.subscriptions.SendToTvSheet
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.subscriptions.SubscriptionAddressesSheet
import com.github.kr328.clash.ui.subscriptions.sendable
import com.github.kr328.clash.ui.subscriptions.DeleteSubscriptionDialog
import com.github.kr328.clash.ui.subscriptions.EditSubscriptionDialog
import com.github.kr328.clash.ui.components.DuckerScaffold
import com.github.kr328.clash.ui.subscriptions.SubscriptionsScreen
import com.github.kr328.clash.ui.subscriptions.SubscriptionsViewModel
import com.github.kr328.clash.ui.subscriptions.UpdateEditedDialog
import com.github.kr328.clash.ui.subscriptions.local
import com.github.kr328.clash.ui.theme.DuckerTheme
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.common.util.providerLink
import com.github.kr328.clash.service.StopNotice
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendBroadcastSelf
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.ui.home.BackgroundStep
import com.github.kr328.clash.ui.home.BackgroundSheet
import com.github.kr328.clash.ui.home.BackgroundNotice
import com.github.kr328.clash.ui.home.VpnSheet
import com.github.kr328.clash.ui.settings.MAIL
import com.github.kr328.clash.ui.update.UpdateSheet
import com.github.kr328.clash.ui.update.UpdatedSheet
import com.github.kr328.clash.update.Updater
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.Lifecycle
import com.github.kr328.clash.util.requestBackgroundWork
import com.github.kr328.clash.util.openVendor
import com.github.kr328.clash.util.backgroundAllowed
import com.github.kr328.clash.util.Vendor
import com.github.kr328.clash.store.TipsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Главный экран приложения (этап 3).
 *
 * Не наследует BaseActivity из CMFA: та завязана на их слой Design с разметкой в XML,
 * а мы этот слой заменяем (D-2, D-3). Запуск службы и разрешение на VPN переиспользуются
 * как есть — это сетевой слой, его мы не трогаем.
 */
/**
 * Наследуемся от `AppCompatActivity`, а не от `ComponentActivity`: до Android 13 выбор языка
 * приложения работает только через подмену контекста, которую делает AppCompat (D-49).
 */
/** Сколько ждём остановки службы перед перезапуском после правки списка приложений. */
private const val RESTART_TIMEOUT = 5000L

class HomeActivity : AppCompatActivity(), Broadcasts.Observer {
    // Через `viewModels()`, а не `lazy { … }`: созданная руками вью-модель не привязана
    // к жизненному циклу, поэтому `onCleared()` у неё не вызывается никогда. Её корутины
    // продолжают работать после закрытия экрана — опрос трафика раз в секунду остаётся
    // висеть, а подписка на журнал не снимается. При смене языка или поворота экрана
    // каждая новая активити заводила ещё одну копию поверх старой.
    private val viewModel: HomeViewModel by viewModels()
    private val proxyViewModel: ProxyViewModel by viewModels()
    private val subscriptionsViewModel: SubscriptionsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val probeViewModel: ProbeViewModel by viewModels()
    private val appsViewModel: AppsViewModel by viewModels()
    private val logsViewModel: LogsViewModel by viewModels()
    private val configViewModel: ConfigViewModel by viewModels()

    /** Кнопка «Подключиться» нажата без подписки — открыть вкладку подписок с окном добавления. */
    private val needSubscription = mutableStateOf(false)

    /** Открыли уведомлением о новой версии — показать панель обновления. */
    private val updateRequest = mutableStateOf(false)

    /** Запрос разрешения VPN ждёт, пока панель перед ним не отпустят (`askVpn`). */
    private val vpnRequest = mutableStateOf<Intent?>(null)

    private val tips by lazy { TipsStore(this) }

    /** Возвраты на экран — по ним панель «работа в фоне» узнаёт ответ системного окна. */
    private val resumes = mutableIntStateOf(0)

    /** Returns of the app to the foreground: «Прокси» reads the core again by them (D-299). */
    private val starts = mutableIntStateOf(0)

    private val qrScanner = registerForActivityResult(QrScanActivity.Scan) { text ->
        subscriptionsViewModel.scanned(text)
    }

    /** «Файл» в окне добавления подписки: любой тип — у `.yaml` на телефоне часто нет своего. */
    private val pickSubscriptionFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) subscriptionsViewModel.addFile(this, uri)
    }

    /** Журнал сохраняется туда, куда укажет человек: своей папки у приложения нет. */
    private val saveLog = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch {
            val text = logsViewModel.report(this@HomeActivity)

            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    // Поток может не открыться (папка удалена, нет прав) — это отказ,
                    // а не успех: иначе человек увидит «сохранено» и пустое место.
                    val stream = contentResolver.openOutputStream(uri)
                        ?: error("не удалось открыть файл")

                    stream.use { it.write(text.toByteArray()) }
                }
            }.isSuccess

            Toast.makeText(
                this@HomeActivity,
                if (saved) R.string.logs_saved else R.string.logs_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Разрешение на уведомления. Без него на Android 13+ служба работает молча: не видно
     * ни скорости, ни самого факта подключения, и вернуться в приложение из шторки нельзя.
     * Просили его только экраны CMFA, поэтому на своём интерфейсе уведомления пропали.
     */
    /**
     * «Сохранить как» → файлом (D-124). Файл с телефона — окно открывается в его папке:
     * положить правленый рядом, под своим именем.
     */
    private val exportConfig = registerForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/yaml") {
            override fun createIntent(context: Context, input: String): Intent =
                super.createIntent(context, input).apply {
                    configViewModel.state.value.file?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                }
        }
    ) { uri ->
        if (uri != null) configViewModel.export(this, uri)
    }

    private val notificationPermission = registerForActivityResult(RequestPermission()) { }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startClashService()
        } else {
            // В разрешении отказали — кнопку надо отпустить, иначе она останется
            // не нажимаемой до истечения таймаута.
            viewModel.cancelToggling()
        }
    }

    // A TV with a larger canvas than Android TV's 960×540 gets that canvas (see [tvCanvas]).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(tvCanvas(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка задаётся один раз, а применять её приходится каждой новой задаче.
        applyRecents(SettingsStore(this).hideFromRecents)

        // Ярлыки на значке (D-87). Вне главного потока: это вызов в системную службу,
        // а первому кадру он ни к чему.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { publishShortcuts() } }

        // Телевизор (этап 6, D-12): тот же APK, но док слева и управление пультом.
        val tv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        // Whether there is a subscription, known before the service answers: its folder of
        // downloaded ones. The first frame is drawn by it — the welcome or home — instead of home
        // giving way to the welcome a moment later. Not a flag in the settings: those go into
        // Android's backup and the subscriptions do not (`backup_rules.xml`), so on a restored
        // phone the flag would say there are some.
        val hadSubscriptions = !importedDir.list().isNullOrEmpty()

        setContent {
            val settingsState by settingsViewModel.state.collectAsState()

            val dark = when (settingsState.theme) {
                SettingsStore.THEME_LIGHT -> false
                SettingsStore.THEME_DARK -> true
                // Телевизор смотрят в полутьме — там тёмная, что бы ни сообщала система, пока
                // проверки на настоящих телевизорах не покажут иного.
                else -> tv || isSystemInDarkTheme()
            }

            DuckerTheme(
                dark = dark,
                oled = settingsState.oled,
                dynamicColor = settingsState.dynamicColor,
                // Тему переключает кнопка в шапке, и новая палитра накатывает волной
                // от неё же (D-66).
                onToggleDark = {
                    settingsViewModel.setTheme(
                        this@HomeActivity,
                        if (dark) SettingsStore.THEME_LIGHT else SettingsStore.THEME_DARK,
                    )
                },
                tv = tv,
            ) {
                val pagerState = rememberPagerState { TABS }
                val scope = rememberCoroutineScope()
                val focusManager = LocalFocusManager.current
                val tvFocus = remember { if (tv) TvFocus() else null }

                // Состояние вкладки «Прокси» живёт здесь, а не внутри экрана: страницу
                // уничтожает и заход в настройки, и уход на дальнюю вкладку, а поиск,
                // раскрытые группы и прокрутка должны это пережить (D-76).
                var proxyQuery by rememberSaveable { mutableStateOf("") }
                // Своим сейвером: штатный хранит только то, что кладётся в Bundle,
                // а список состояния туда не кладётся — приложение падало на запуске.
                val proxyFlipped = rememberSaveable(
                    saver = listSaver<MutableList<String>, String>(
                        save = { it.toList() },
                        restore = { it.toMutableStateList() },
                    ),
                ) { mutableStateListOf<String>() }
                val proxyList = rememberLazyListState()

                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showProbe by rememberSaveable { mutableStateOf(false) }
                val subscriptionsState by subscriptionsViewModel.state.collectAsState()
                // Панель правки, как и добавления, живёт в композиции, пока уезжает вниз (D-100).
                var editShown by remember { mutableStateOf(false) }
                // Свои правила подписки (D-123) и её конфиг (D-124): открываются из её правки.
                // Читаются сразу, а экран въезжает, когда панель уехала: вместе с ней, под
                // уезжающей панелью, он въезжал двумя встречными движениями, а если ждать
                // панель, чтобы только потом начать читать, — открывался дольше.
                // Кадром позже, чем уходит панель (D-128): на Redmi Note 9S закрытие её окна и
                // сборка экрана в одном кадре давали рывок в 190 мс.
                var editGone by remember { mutableStateOf(true) }

                LaunchedEffect(editShown) {
                    if (editShown) {
                        editGone = false
                    } else {
                        withFrameNanos { }
                        editGone = true
                    }
                }

                val showRules = subscriptionsState.rulesFor != null && editGone
                val configState by configViewModel.state.collectAsState()
                val showConfig = configState.open && editGone
                var showApps by rememberSaveable { mutableStateOf(false) }
                var showLogs by rememberSaveable { mutableStateOf(false) }

                // Признаки «экран открыт» переживают выгрузку процесса, а данные экранов — нет.
                // Грузились они только по нажатию, и после возврата «Приложения» вечно показывали
                // загрузку, журнал молчал, а в замере нечего было выбрать (аудит, 9б.1). После
                // поворота экрана модели живы: список приложений с несохранёнными отметками не
                // перечитываем, остальные вызовы повтор переносят.
                LaunchedEffect(Unit) {
                    if (showApps && appsViewModel.state.value.loading) appsViewModel.load(this@HomeActivity)
                    if (showLogs) logsViewModel.start()
                    if (showProbe) probeViewModel.refresh()
                }

                // «Подробный журнал» выключается сам через сутки (D-224), а настройки читаются один
                // раз, при создании окна: тумблер сверяем, открывая журнал и возвращаясь к нему.
                LaunchedEffect(showLogs, resumes.intValue) {
                    if (showLogs) settingsViewModel.refresh(this@HomeActivity)
                }

                // Закрытие экранов поверх вкладок — одно на жест «назад» и стрелку в шапке.
                val closeSettings = { showSettings = false }
                val closeApps = {
                    showApps = false

                    applyAppFilter()
                }
                val closeLogs = {
                    showLogs = false

                    logsViewModel.stop()
                }
                val closeProbe = { showProbe = false }

                // С дальних вкладок «назад» — это свайп на соседнюю вкладку: лента едет
                // ровно за пальцем, отпустил — доезжает (D-91). С главной — выход из
                // приложения, его рисует система. Экраны поверх вкладок ловят «назад»
                // сами, в `BackLayer`.
                PredictiveBackHandler(
                    // По осевшей странице, а не по текущей: текущая переключается, как только
                    // лента под пальцем уехала за середину, обработчик выключался, и система
                    // обрывала жест прямо под пальцем.
                    // На телевизоре «назад» ведёт по доку, см. ниже.
                    enabled = !tv && !showSettings && !showProbe && !showApps && !showLogs && !showRules && !showConfig &&
                        pagerState.settledPage != 0,
                ) { events ->
                    val from = pagerState.settledPage

                    try {
                        var start = Float.NaN
                        var moved = 0f

                        pagerState.scroll {
                            events.collect { event ->
                                if (start.isNaN()) start = event.touchX

                                val target = abs(event.touchX - start)
                                    .coerceAtMost(pagerState.layoutInfo.pageSize.toFloat())

                                moved -= scrollBy(-(target - moved))
                            }
                        }

                        pagerState.animateScrollToPage(from - 1)
                    } catch (e: CancellationException) {
                        scope.launch { pagerState.animateScrollToPage(from) }

                        throw e
                    }
                }

                val state by viewModel.state.collectAsState()
                // Без `by`: значение читает не экран, а те строки внизу, которые его
                // показывают. Иначе секундный тик пересобирал бы всю страницу (D-77).
                val metrics = viewModel.metrics.collectAsState()
                val proxyState by proxyViewModel.state.collectAsState()
                val probeState by probeViewModel.state.collectAsState()
                val appsState by appsViewModel.state.collectAsState()
                val logsState by logsViewModel.state.collectAsState()

                // Обновление приложения: панель живёт в композиции, пока уезжает вниз.
                val updateState by Updater.state.collectAsState()
                var updateOpen by remember { mutableStateOf(false) }
                var updateShown by remember { mutableStateOf(false) }
                val openUpdate = {
                    updateOpen = true
                    updateShown = true
                }

                // «Установка из этого источника» ещё не разрешена — сначала экран Android с этим
                // тумблером, и уже потом установщик. Иначе тот сам уводит в настройки, а после
                // возврата считает, что человек отказался: «Обновить» пришлось бы жать дважды.
                // Turning the automatic update on waits for it too: without it Android would ask.
                var afterInstallPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
                val withInstallPermission: (() -> Unit) -> Unit = { action ->
                    // Экрана с тумблером у системы нет — пусть спрашивает установщик.
                    if (packageManager.canRequestPackageInstalls() || !askInstallPermission()) {
                        action()
                    } else {
                        afterInstallPermission = action
                    }
                }

                LaunchedEffect(resumes.intValue) {
                    val action = afterInstallPermission ?: return@LaunchedEffect

                    afterInstallPermission = null

                    if (packageManager.canRequestPackageInstalls()) action()
                }

                // “What's new” in the version the app has just updated itself to.
                var updatedOpen by remember { mutableStateOf(false) }
                var updatedShown by remember { mutableStateOf(false) }

                // Из уведомления: что именно вышло, приложение после запуска узнаёт не сразу.
                LaunchedEffect(updateRequest.value, updateState.release) {
                    if (updateRequest.value && updateState.release != null) {
                        updateRequest.value = false
                        openUpdate()
                    }
                }

                // Android просит подтвердить установку. Его окно может открыть только окно
                // приложения — поэтому отсюда, а не из приёмника, и когда приложение на экране.
                LaunchedEffect(updateState.confirm, resumes.intValue) {
                    val confirm = updateState.confirm ?: return@LaunchedEffect

                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        Updater.confirmShown()
                        runCatching { startActivity(confirm) }
                    }
                }

                // Работа в фоне (D-109). Панель живёт в композиции и после закрытия, пока
                // уезжает вниз, — как панели подписок.
                var backgroundOpen by remember { mutableStateOf(false) }
                var backgroundShown by remember { mutableStateOf(false) }
                var backgroundStep by remember { mutableStateOf(BackgroundStep.Allow) }
                // Ушли в системное окно — по возвращении решаем, что дальше.
                var awaitingSystem by remember { mutableStateOf(false) }
                var shellSeen by remember { mutableStateOf(tips.backgroundHintSeen) }
                var snoozedUntil by remember { mutableLongStateOf(tips.backgroundSnoozedUntil) }

                val openBackground = { step: BackgroundStep ->
                    backgroundStep = step
                    backgroundOpen = true
                    backgroundShown = true
                }
                val markShellSeen = {
                    tips.backgroundHintSeen = true
                    shellSeen = true
                }

                // Первое подключение удалось — самое время объяснить, зачем работа в фоне, и
                // только потом показать системное окно. Один раз; дальше — карточка на
                // главной и «Работа в фоне» в настройках. Not on a TV: it is never off the charger, where Doze acts,
                // its request screens are stubs, and a TV's brand would pick a phone vendor's steps (F-346).
                LaunchedEffect(state.running) {
                    if (tv || !state.running || tips.backgroundAsked || backgroundAllowed()) return@LaunchedEffect

                    delay(BACKGROUND_ASK_DELAY)

                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        tips.backgroundAsked = true
                        openBackground(BackgroundStep.Allow)
                    }
                }

                // Вернулись из системного окна: разрешили — на телефоне с «убийцей» фона
                // показываем шаги его оболочки, иначе закрываем; отказали — закрываем, и
                // остаётся карточка на главной.
                LaunchedEffect(resumes.intValue) {
                    if (!awaitingSystem) return@LaunchedEffect
                    awaitingSystem = false

                    if (backgroundAllowed() && Vendor.current != null && !shellSeen) {
                        backgroundStep = BackgroundStep.Vendor
                    } else {
                        backgroundOpen = false
                    }
                }

                val backgroundNotice = when {
                    tv -> null

                    !settingsState.backgroundAllowed && tips.backgroundAsked &&
                        System.currentTimeMillis() >= snoozedUntil -> BackgroundNotice.Restricted(
                        onAllow = { openBackground(BackgroundStep.Allow) },
                        onLater = {
                            snoozedUntil = System.currentTimeMillis() + BACKGROUND_SNOOZE
                            tips.backgroundSnoozedUntil = snoozedUntil
                        },
                    )

                    settingsState.backgroundAllowed && !shellSeen -> Vendor.current?.let { vendor ->
                        BackgroundNotice.Shell(vendor) { openBackground(BackgroundStep.Vendor) }
                    }

                    else -> null
                }

                LaunchedEffect(needSubscription.value) {
                    if (!needSubscription.value) return@LaunchedEffect

                    showSettings = false
                    showApps = false
                    showLogs = false

                    // Без анимации: поверх сразу открывается окно добавления,
                    // анимировать под ним нечего.
                    pagerState.scrollToPage(2)

                    subscriptionsViewModel.refresh(this@HomeActivity)
                    subscriptionsViewModel.showAdd(true)

                    needSubscription.value = false
                }

                // Данные вкладки обновляем, только когда листание закончилось.
                // `currentPage` переключается на середине жеста: незавершённый свайп
                // запускал бы замер задержек, которого никто не просил (D-76).
                LaunchedEffect(pagerState.settledPage) {
                    when (pagerState.settledPage) {
                        1 -> proxyViewModel.open(this@HomeActivity)
                        2 -> subscriptionsViewModel.refresh(this@HomeActivity)
                    }
                }

                // The app came back with «Прокси» on screen: the page did not change, and the tab
                // would go on showing what it read before the app was left — on the walk home,
                // the delays of the mobile network (D-299). The first start is the effect above.
                LaunchedEffect(starts.intValue) {
                    if (starts.intValue > 1 && pagerState.settledPage == 1) {
                        proxyViewModel.open(this@HomeActivity)
                    }
                }

                // Выбор узла подтверждается службой не сразу, поэтому главную обновляем
                // и при возврате на неё, и когда подтверждение доходит уже после перехода.
                //
                // Only on a change: the first pass repeated what `onStart` has just asked for.
                LaunchedEffect(Unit) {
                    snapshotFlow {
                        Triple(pagerState.settledPage == 0 && !showSettings, proxyState.groups, proxyState.mode)
                    }.drop(1).collect { (onHome) ->
                        if (onHome) viewModel.refresh(this@HomeActivity)
                    }
                }

                // Клавиатуру снимаем на начале жеста, а не по остановке: иначе она едет
                // вместе с пальцем, а панель вкладок прыгает вверх вслед за ней.
                // На телевизоре ленту листает сам фокус в доке, и снимать его нельзя.
                if (!tv) LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.isScrollInProgress }
                        .filter { it }
                        .collect { focusManager.clearFocus(force = true) }
                }

                // Подписок нет вовсе: приложению нечего показывать, кроме одного действия (D-98).
                // Решаем по списку подписок, а не по активной: подписки могут быть, но ни одна
                // не выбрана, — это не первый запуск.
                //
                // Until the list arrives the answer is the one from the disk. A subscription that has
                // not been downloaded is not one yet: the first is in the list from the press of
                // «Добавить», long before there is anything to show.
                val none = if (subscriptionsState.loaded) subscriptionsState.profiles.none { it.imported } else !hadSubscriptions

                // The first subscription is in the list a moment before home has it, and home without
                // it is the «Подписка не выбрана» card. The welcome stays until home has it and gives
                // way straight to the power button. A plain holder, not state: it is this line's own
                // previous answer.
                val arriving = state.profile == null &&
                    (subscriptionsState.busy || subscriptionsState.profiles.any { it.active })
                val welcomed = remember { booleanArrayOf(false) }
                val onboarding = (none || welcomed[0] && arriving).also { welcomed[0] = it }

                // На главную — доездом, а не прыжком: подсветка на панели в это время
                // растягивается в кнопку, и обе к концу сходятся у левого края. Прыжком
                // подсветка на миг перескакивала на «Главную» ещё до слияния.
                LaunchedEffect(onboarding) {
                    if (onboarding) pagerState.animateScrollToPage(0)
                }

                // Быстрая смена узла на главной (D-116): узлы, задержки, избранное и сам выбор —
                // у вкладки «Прокси», главная берёт оттуда свою основную группу.
                val mainGroup = state.groups.firstOrNull()?.name
                val picker = remember(proxyState.groups, mainGroup) {
                    proxyState.groups.firstOrNull { it.name == mainGroup && it.selectable }
                        ?.let { NodePicker(group = it.name, now = it.now, nodes = it.proxies) }
                }

                val overlay = when {
                    showConfig -> "config"
                    showRules -> "rules"
                    showLogs -> "logs"
                    showApps -> "apps"
                    showProbe -> "probe"
                    showSettings -> "settings"
                    else -> null
                }

                // Пульт (этап 6). Старт — на вкладке в доке. «Назад» со страницы — в её вкладку,
                // из дока — на «Главную», с неё — выход. Экран поверх закрылся — фокус обратно
                // на страницу, на её начало, как при входе с дока (D-158).
                var pageFocused by remember { mutableStateOf(false) }
                // Вкладка, к которой едет лента на телевизоре.
                var tvTarget by remember { mutableIntStateOf(0) }
                var paging by remember { mutableStateOf<Job?>(null) }
                // Настройки открыты шестерёнкой под доком, а не со страницы.
                var fromGear by remember { mutableStateOf(false) }

                if (tvFocus != null) {
                    // Подписок нет — во всём доке одна кнопка, и она на месте первой вкладки (D-98).
                    // В режиме касаний (мышь у эмулятора, пульт-указка) кнопки фокус не берут —
                    // тогда он встаёт в док с первым же нажатием клавиши.
                    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard

                    LaunchedEffect(onboarding, keys) {
                        if (keys) tvFocus.tabs[if (onboarding) 0 else pagerState.settledPage].requestFocus()
                    }

                    // Со страницы «назад» паркует пульт на её вкладке (D-161): фокус остаётся, где был,
                    // и вправо возвращает на то же место. Из дока — настоящий переход на «Главную».
                    val inPage = pageFocused && !tvFocus.parked

                    BackHandler(enabled = overlay == null && (inPage || pagerState.settledPage != 0)) {
                        if (inPage) {
                            tvFocus.parked = true
                        } else {
                            tvFocus.parked = false
                            tvFocus.steer()
                            tvFocus.tabs[0].requestFocus()
                        }
                    }

                    var covered by remember { mutableStateOf(false) }

                    // Лента доезжает до вкладки дока, даже если её перебили: прокрутка к элементу в
                    // фокусе на прежней странице останавливала её между страницами (D-159).
                    LaunchedEffect(pagerState.settledPage) { tvTarget = pagerState.settledPage }
                    LaunchedEffect(Unit) {
                        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                            if (!scrolling && pagerState.currentPageOffsetFraction != 0f) pagerState.animateScrollToPage(tvTarget)
                        }
                    }

                    LaunchedEffect(overlay == null) {
                        if (overlay != null) {
                            covered = true
                        } else if (covered) {
                            covered = false

                            // Настройки открывали шестерёнкой под доком — на неё и возвращаемся (D-161).
                            if (fromGear) {
                                fromGear = false
                                tvFocus.gear.requestFocus()
                            } else {
                                tvFocus.enter(pagerState.settledPage)
                            }
                        }
                    }

                    // Вход во вкладку — с начала (D-158): «Прокси» — к первой группе, где точка входа.
                    tvFocus.reset = { if (it == 1) proxyList.scrollToItem(0) }

                    // Ждёт подписку с телефона, пока открыта панель «+» (D-162).
                    DisposableEffect(subscriptionsState.adding) {
                        if (subscriptionsState.adding) subscriptionsViewModel.startReceive(this@HomeActivity)

                        onDispose { subscriptionsViewModel.stopReceive() }
                    }

                    // Подписка с телефона загружена (D-163): осталось её включить — пульт встаёт на
                    // кнопку питания. Не раньше, чем главная покажет эту подписку: до того кнопки
                    // может не быть вовсе — у первой подписки на её месте ещё приветствие.
                    val landed = subscriptionsState.toPower?.takeIf { it == state.profile?.uuid }

                    LaunchedEffect(landed) {
                        if (landed == null) return@LaunchedEffect

                        tvTarget = 0
                        paging?.cancel()
                        pagerState.animateScrollToPage(0)
                        tvFocus.enter(0)

                        subscriptionsViewModel.atPower()
                    }
                } else {
                    // Подписка по ссылке со страницы провайдера загружена и выбрана (D-211): на главную,
                    // к её имени и кнопке питания. Как и на телевизоре — когда главная её уже показывает.
                    val landed = subscriptionsState.toPower?.takeIf { it == state.profile?.uuid }

                    LaunchedEffect(landed) {
                        if (landed == null) return@LaunchedEffect

                        pagerState.animateScrollToPage(0)

                        subscriptionsViewModel.atPower()
                    }
                }

                DuckerScaffold(
                    // Подсветка вкладки идёт по осевшей странице: по ней же выбирается
                    // плавающая кнопка, а её появление меняет отступы (D-76). На телевизоре — по
                    // вкладке, к которой лента едет: пока она едет, осевшая ещё прежняя, и возврат
                    // фокуса на неё не считался сменой вкладки (D-159).
                    selected = { if (tv) tvTarget else pagerState.settledPage },
                    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onSelect = { tab ->
                        // Перезапуск ядра после правки списка приложений и остановку
                        // журнала запускает только нажатие: палец, который просто листает
                        // страницы, ронять туннель не должен.
                        val fromOverlay = overlay != null

                        showSettings = false
                        showProbe = false

                        if (showApps) {
                            showApps = false

                            applyAppFilter()
                        }

                        if (showLogs) {
                            showLogs = false

                            logsViewModel.stop()
                        }

                        tvTarget = tab

                        // На телевизоре вкладку выбирает фокус, а не нажатие: лента всегда едет к ней.
                        // Иначе быстрое «вниз-вверх» по доку принималось за повторное нажатие — лента
                        // доезжала до соседней вкладки, а фокус оставался на прежней (D-159). Пока лента
                        // едет, и на телефоне касание вкладки — смена, а не повторное нажатие.
                        if (pagerState.currentPage == tab && !fromOverlay && !tv && paging?.isActive != true) {
                            // Повторное нажатие по своей же вкладке обновляет данные:
                            // страница не меняется, и эффект по `settledPage` промолчит.
                            when (tab) {
                                1 -> proxyViewModel.refresh(this@HomeActivity)
                                2 -> subscriptionsViewModel.refresh(this@HomeActivity)
                            }
                        } else {
                            // Прежнее перелистывание отменяем сами: если лента ещё не тронулась,
                            // перелистывание на её же страницу считает себя сделанным и прежнее не
                            // перебивает — быстрое «вниз-вверх» доезжало до соседней вкладки (D-159).
                            paging?.cancel()
                            paging = scope.launch {
                                // Лента под экраном поверх — листать невидимое незачем,
                                // экран гаснет уже над нужной вкладкой.
                                if (fromOverlay) pagerState.scrollToPage(tab)
                                else pagerState.animateScrollToPage(tab)
                            }
                        }
                    },
                    // Пока открыт экран поверх вкладок, панель уезжает вниз (D-96).
                    dockShown = overlay == null,
                    // Подписок нет — вкладок будто и нет: панель становится одной кнопкой (D-98).
                    single = if (onboarding) stringResource(R.string.add_subscription) else null,
                    onSingle = { subscriptionsViewModel.showAdd(true) },
                    // «Плюс» подписок раскрывается ровно по мере того, как вкладка подписок
                    // въезжает на экран, и сжимается, когда уезжает (D-97).
                    actionShown = {
                        (pagerState.currentPage + pagerState.currentPageOffsetFraction - 1f).coerceIn(0f, 1f)
                    },
                    action = { AddSubscriptionButton() },
                    // Нажимается, только когда вкладка подписок осела перед глазами: пока
                    // кружок сжат, касания проходят к списку.
                    onAction = if (overlay == null && pagerState.settledPage == 2) {
                        { subscriptionsViewModel.showAdd(true) }
                    } else null,
                    actionLabel = stringResource(R.string.add_subscription),
                    tv = tvFocus,
                    // На телевизоре шапка главной — в колонке дока: марка над ним, кнопки под ним (D-161).
                    tvTitle = { BrandTitle(state.profile) },
                    tvTools = {
                        ThemeButton { icon, description, onClick, modifier -> TvTool(icon, description, onClick, modifier) }

                        TvTool(
                            icon = Symbols.Settings,
                            description = stringResource(R.string.settings),
                            onClick = {
                                fromGear = true
                                showSettings = true
                            },
                            modifier = if (tvFocus != null) Modifier.focusRequester(tvFocus.gear) else Modifier,
                        )
                    },
                ) { tabs, overlayInsets ->
                    // На телевизоре док слева (этап 6): лента встаёт справа от него целиком, а
                    // экранам остаются верх и низ — иначе колонка по центру сжалась бы на ширину дока.
                    val direction = LocalLayoutDirection.current
                    val insets = if (tv) {
                        PaddingValues(top = tabs.calculateTopPadding(), bottom = tabs.calculateBottomPadding())
                    } else tabs
                    // A TV's lists take the safe area's top and bottom inside themselves (`tvEdge`) and run to the
                    // screen's edge; these are the paddings left outside them.
                    val homeInsets = if (tv) PaddingValues() else insets
                    val listInsets = if (tv) {
                        PaddingValues(start = overlayInsets.calculateStartPadding(direction), end = overlayInsets.calculateEndPadding(direction))
                    } else overlayInsets

                    // Под экраном поверх вкладок лента не должна ни читаться TalkBack,
                    // ни ловить фокус: человек её не видит.
                    val pagerModifier = Modifier.fillMaxSize()
                        .then(if (overlay != null) Modifier.clearAndSetSemantics {} else Modifier)
                        .then(
                            if (tv) {
                                Modifier.padding(start = tabs.calculateStartPadding(direction), end = tabs.calculateEndPadding(direction))
                                    .onFocusChanged { pageFocused = it.hasFocus }
                            } else Modifier,
                        )

                    val tabPage: @Composable PagerScope.(Int) -> Unit = { page ->
                        // Своим слоем: перемены на одной странице перезаписывают
                        // отрисовку только её, а не всех трёх вместе с панелью.
                        // Пульт входит в страницу из дока и выходит только обратно в свою вкладку.
                        // Страницы стоят друг под другом, и плавающий поиск «Прокси» заезжал
                        // на главную — на телевизоре каждая обрезана по своим краям.
                        Box(
                            Modifier.fillMaxSize().graphicsLayer()
                                .then(
                                    tvFocus?.let {
                                        Modifier.clipToBounds().pageBoundary().focusRoom(it.pages[page], it.entries[page], onLeft = { it.parked = true })
                                    } ?: Modifier,
                                ),
                        ) {
                            ScreenFrame(entry = tvFocus?.entries?.get(page)) {
                                // Главную не видно — её часы стоят. Иначе раз в секунду пересобиралась и
                                // перерисовывалась страница за краем экрана, и телефон рисовал ради неё
                                // кадр на любой вкладке и поверх любого экрана (F-231). Пока ленту
                                // листают, главная может быть на экране — считаем её видимой.
                                val homeVisible = overlay == null && (pagerState.currentPage == 0 || pagerState.isScrollInProgress)

                                when (page) {
                                    1 -> ProxyScreen(
                                        state = proxyState,
                                        query = proxyQuery,
                                        onQuery = { proxyQuery = it },
                                        flipped = proxyFlipped,
                                        listState = proxyList,
                                        onSelect = { group, name ->
                                            proxyViewModel.select(this@HomeActivity, group, name)
                                        },
                                        onFavorite = { proxyViewModel.toggleFavorite(this@HomeActivity, it) },
                                        onHealthCheck = { proxyViewModel.healthCheck(this@HomeActivity, it) },
                                        onMode = { proxyViewModel.setMode(this@HomeActivity, it) },
                                        onSort = { proxyViewModel.setSort(this@HomeActivity, it) },
                                        modifier = Modifier.consumeWindowInsets(insets),
                                        contentPadding = insets,
                                        // Потянуть вниз — перемерить основную группу; если мерить нечем
                                        // (туннель выключен, профиль не загружен) — перечитать список.
                                        refreshing = proxyState.checking != null,
                                        onRefresh = {
                                            val main = proxyState.groups.firstOrNull()

                                            if (proxyState.canCheck && main != null) {
                                                proxyViewModel.healthCheck(this@HomeActivity, main.name)
                                            } else {
                                                proxyViewModel.refresh(this@HomeActivity)
                                            }
                                        },
                                    )

                                    2 -> SubscriptionsScreen(
                                        state = subscriptionsState,
                                        onSelect = { subscriptionsViewModel.setActive(this@HomeActivity, it) },
                                        onUpdate = { subscriptionsViewModel.update(this@HomeActivity, it) },
                                        onEdit = { subscriptionsViewModel.showEdit(it) },
                                        onDelete = { subscriptionsViewModel.askDelete(it) },
                                        onAdd = { subscriptionsViewModel.showAdd(true) },
                                        onDismissError = { subscriptionsViewModel.dismissUpdateError() },
                                        onOpenLink = { open(it) },
                                        onFavorite = { subscriptionsViewModel.toggleFavorite(this@HomeActivity, it) },
                                        onMove = { from, to -> subscriptionsViewModel.move(from, to) },
                                        onMoved = { subscriptionsViewModel.saveOrder(this@HomeActivity) },
                                        onAddresses = { subscriptionsViewModel.showAddresses(it.uuid) },
                                        onRules = { subscriptionsViewModel.openRules(this@HomeActivity, it.uuid) },
                                        onSendToTv = {
                                            subscriptionsViewModel.scanForTv(it.uuid)
                                            qrScanner.launch(true)
                                        },
                                        modifier = Modifier.consumeWindowInsets(insets),
                                        contentPadding = insets,
                                        // Потянуть вниз — обновить подписку, которая сейчас в работе.
                                        refreshing = subscriptionsState.updating != null,
                                        onRefresh = {
                                            val active = subscriptionsState.profiles.firstOrNull { it.active }

                                            if (active != null && !active.local) {
                                                subscriptionsViewModel.update(this@HomeActivity, active.uuid)
                                            } else {
                                                subscriptionsViewModel.refresh(this@HomeActivity)
                                            }
                                        },
                                    )

                                    else -> HomeScreen(
                                        state = state,
                                        header = !tv,
                                        metrics = { if (homeVisible) metrics.value else Snapshot.withoutReadObservation { metrics.value } },
                                        onToggle = { toggle() },
                                        onSettings = { showSettings = true },
                                        onSupport = { open(it) },
                                        // Через вкладку «Подписки»: у неё сторож и итог обновления,
                                        // которые главная теперь показывает на кнопке (D-119).
                                        onRefresh = {
                                            state.profile?.uuid?.let { subscriptionsViewModel.update(this@HomeActivity, it) }
                                        },
                                        updating = state.profile?.uuid?.let { it == subscriptionsState.updating } == true,
                                        updateFailed = subscriptionsState.updateError.isNotEmpty() &&
                                            subscriptionsState.updateErrorFor == state.profile?.uuid,
                                        onCheckLink = { viewModel.checkLink() },
                                        onOpenProxies = {
                                            tvTarget = 1
                                            paging?.cancel()
                                            paging = scope.launch { pagerState.animateScrollToPage(1) }
                                            // Пульт (D-160): фокус — сразу в «Прокси», с их начала, как при
                                            // входе с дока. Иначе он оставался на кнопке закрывшейся панели
                                            // узлов, а с её уходом падал на «Главную» в доке.
                                            tvFocus?.let { scope.launch { it.enter(1) } }
                                        },
                                        onVpnSettings = {
                                            runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                                        },
                                        onDismissStop = { viewModel.dismissStop(this@HomeActivity) },
                                        onCopyError = { copyReport(it, R.string.error_copied) },
                                        update = updateState.release?.version,
                                        onUpdate = openUpdate,
                                        updateProgress = updateState.progress?.let { (it * 100).toInt() },
                                        updateInstalling = updateState.installing,
                                        updated = updateState.updated?.version,
                                        onWhatsNew = if (updateState.updated?.notes?.values?.any { it.isNotBlank() } == true) {
                                            {
                                                updatedOpen = true
                                                updatedShown = true
                                            }
                                        } else {
                                            null
                                        },
                                        onUpdatedSeen = Updater::acknowledged,
                                        onChooseSubscription = {
                                            tvTarget = 2
                                            paging?.cancel()
                                            paging = scope.launch { pagerState.animateScrollToPage(2) }
                                            tvFocus?.let { scope.launch { it.enter(2) } }
                                        },
                                        // Самый быстрый — из той же группы, что и быстрая смена (D-116):
                                        // у групп автовыбора узел выбирает ядро, и кнопке там делать нечего.
                                        onFastest = picker?.let { p ->
                                            {
                                                viewModel.switchToFastest {
                                                    proxyViewModel.fastest(this@HomeActivity, p.group)
                                                }
                                            }
                                        },
                                        onboarding = onboarding,
                                        backgroundNotice = backgroundNotice,
                                        picker = picker,
                                        pickerState = proxyState,
                                        onSortNodes = { proxyViewModel.setSort(this@HomeActivity, it) },
                                        onCheckNodes = { proxyViewModel.healthCheck(this@HomeActivity, it) },
                                        shown = pagerState.settledPage == 0 && overlay == null,
                                        onOpenPicker = { proxyViewModel.refresh(this@HomeActivity) },
                                        onPickNode = { group, name ->
                                            proxyViewModel.select(this@HomeActivity, group, name)
                                        },
                                        onPreviewNode = { name ->
                                            val delay = picker?.nodes?.firstOrNull { it.name == name }?.delay ?: 0

                                            viewModel.nodeChanged(name, delay)
                                        },
                                        modifier = Modifier.padding(homeInsets).consumeWindowInsets(insets),
                                    )
                                }
                            }
                        }
                    }

                    // Вкладки листаются пальцем (D-76), на телевизоре — фокусом в доке, и лента
                    // идёт сверху вниз, как стоят вкладки. Лента собрана всегда, в том числе
                    // под настройками (D-91): закрытие экрана поверх ничего не пересоздаёт.
                    // Три страницы держим собранными: соседняя иначе собирается
                    // на первом же пикселе жеста, а главная не ленивая —
                    // все карточки разом, в самый узкий кадр. Счёт идёт от видимых
                    // страниц: с единицей на крайней вкладке дальняя выпадала
                    // и собиралась заново в начале следующего свайпа (D-112).
                    if (tv) {
                        VerticalPager(
                            state = pagerState,
                            modifier = pagerModifier,
                            key = { it },
                            userScrollEnabled = false,
                            pageNestedScrollConnection = TvPagerScroll,
                            beyondViewportPageCount = TABS - 1,
                            pageContent = tabPage,
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = pagerModifier,
                            key = { it },
                            // Пока подписок нет, листать некуда: вкладкам нечего показывать (D-98).
                            userScrollEnabled = !onboarding,
                            beyondViewportPageCount = TABS - 1,
                            pageContent = tabPage,
                        )
                    }

                    // Экраны поверх вкладок — слоями над лентой, а не вместо неё (D-91).
                    BackLayer(
                        visible = showSettings,
                        onBack = closeSettings,
                        enabled = !showApps && !showLogs && !showProbe,
                        frameContent = false,
                    ) {
                        SettingsScreen(
                            state = settingsState,
                            onTheme = { settingsViewModel.setTheme(this@HomeActivity, it) },
                            onOled = { settingsViewModel.setOled(this@HomeActivity, it) },
                            onDynamicColor = { settingsViewModel.setDynamicColor(this@HomeActivity, it) },
                            onExternalControl = { settingsViewModel.setAllowExternalControl(this@HomeActivity, it) },
                            onIpv6 = { settingsViewModel.setIpv6(this@HomeActivity, it) },
                            onAddTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                { requestAddTile() }
                            } else null,
                            onAccessControl = {
                                showApps = true

                                appsViewModel.load(this@HomeActivity)
                            },
                            onAllowBypass = { settingsViewModel.setAllowBypass(this@HomeActivity, it) },
                            onSystemProxy = { settingsViewModel.setSystemProxy(this@HomeActivity, it) },
                            onDnsHijacking = { settingsViewModel.setDnsHijacking(this@HomeActivity, it) },
                            onBypassPrivate = { settingsViewModel.setBypassPrivate(this@HomeActivity, it) },
                            onStack = { settingsViewModel.setStack(this@HomeActivity, it) },
                            onAutoRestart = { settingsViewModel.setAutoRestart(this@HomeActivity, it) },
                            onAlwaysOn = { runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } },
                            onTrafficNotification = {
                                settingsViewModel.setTrafficNotification(this@HomeActivity, it)
                            },
                            onHideRecents = {
                                settingsViewModel.setHideRecents(this@HomeActivity, it)

                                applyRecents(it)
                            },
                            onDisguise = { settingsViewModel.setDisguise(this@HomeActivity, it) },
                            onLogs = {
                                showLogs = true

                                logsViewModel.start()
                            },
                            onProbe = {
                                showProbe = true
                                probeViewModel.refresh()
                            },
                            onDashboard = { startActivity(DashboardActivity::class.intent) },
                            onLink = { open(it) },
                            onMail = { mail() },
                            update = updateState,
                            onUpdate = {
                                if (updateState.release != null) openUpdate() else Updater.check(manual = true)
                            },
                            onAutoUpdate = { on ->
                                if (on) withInstallPermission { Updater.setAuto(true) } else Updater.setAuto(false)
                            },
                            onBetaUpdates = { settingsViewModel.setBetaUpdates(this@HomeActivity, it) },
                            onAllowBackground = { requestBackgroundWork() },
                            onVendor = { openVendor(it) },
                            hwid = subscriptionsState.hwid,
                            sendHwid = subscriptionsState.sendHwid,
                            onSendHwid = { subscriptionsViewModel.setSendHwid(this@HomeActivity, it) },
                            onLanguage = { settingsViewModel.setLanguage(it) },
                            active = showSettings && !showApps && !showLogs && !showProbe,
                            onBack = closeSettings,
                            modifier = Modifier.padding(listInsets).consumeWindowInsets(overlayInsets),
                        )
                    }

                    BackLayer(
                        visible = showApps,
                        onBack = closeApps,
                    ) {
                        AppsScreen(
                            state = appsState,
                            onMode = { appsViewModel.setMode(it) },
                            onToggle = { appsViewModel.toggle(it) },
                            onQuery = { appsViewModel.setQuery(it) },
                            onShowSystem = { appsViewModel.setShowSystem(it) },
                            onSelectAll = { appsViewModel.selectAll() },
                            onSelectNone = { appsViewModel.selectNone() },
                            onBack = closeApps,
                            modifier = Modifier.padding(listInsets).consumeWindowInsets(overlayInsets),
                        )
                    }

                    BackLayer(
                        visible = showLogs,
                        onBack = closeLogs,
                    ) {
                        LogsScreen(
                            state = logsState,
                            verbose = settingsState.debugLog,
                            onVerbose = { settingsViewModel.setDebugLog(this@HomeActivity, it) },
                            onCopy = {
                                lifecycleScope.launch { copyReport(logsViewModel.report(this@HomeActivity)) }
                            },
                            onShare = { shareLog() },
                            onSave = { saveLog.launch("ducker-log.txt") },
                            onClear = { logsViewModel.clear() },
                            onBack = closeLogs,
                            modifier = Modifier.padding(overlayInsets).consumeWindowInsets(overlayInsets),
                        )
                    }

                    // Конфиг сам обходит клавиатуру (D-124): снизу ему — только полоса навигации,
                    // а не общий отступ с клавиатурой, иначе и кнопки ехали бы вверх вместе с ней.
                    val navigation = WindowInsets.navigationBars.asPaddingValues()
                    val configInsets = remember(overlayInsets, navigation) {
                        object : PaddingValues {
                            override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
                                overlayInsets.calculateLeftPadding(layoutDirection)

                            override fun calculateTopPadding() = overlayInsets.calculateTopPadding()

                            override fun calculateRightPadding(layoutDirection: LayoutDirection) =
                                overlayInsets.calculateRightPadding(layoutDirection)

                            override fun calculateBottomPadding() = navigation.calculateBottomPadding()
                        }
                    }

                    BackLayer(
                        visible = showConfig,
                        onBack = { configViewModel.close() },
                        // Телефон боком — редактору вся ширина (D-145).
                        wide = lowWindow(),
                    ) {
                        ConfigScreen(
                            state = configState,
                            onFormat = { text, apply -> configViewModel.format(this@HomeActivity, text, apply) },
                            onSave = { configViewModel.save(this@HomeActivity, it) },
                            onSaveCopy = {
                                configViewModel.saveCopy(this@HomeActivity, it)
                                // Копия или файл — новая подписка в списке.
                                subscriptionsViewModel.refresh(this@HomeActivity)
                            },
                            onExport = { text, bare ->
                                configViewModel.prepareExport(text, bare)
                                exportConfig.launch(configViewModel.exportName(this@HomeActivity, bare))
                            },
                            onResult = { configViewModel.show(it) },
                            onDirty = { configViewModel.markDirty() },
                            onStash = { configViewModel.stash(it) },
                            onDraft = { configViewModel.answerDraft(it) },
                            onAskLeave = { configViewModel.askLeave(it) },
                            onClose = { configViewModel.close() },
                            modifier = Modifier.padding(configInsets).consumeWindowInsets(configInsets),
                        )
                    }

                    BackLayer(
                        visible = showRules,
                        onBack = { subscriptionsViewModel.closeRules() },
                    ) {
                        RulesScreen(
                            name = subscriptionsState.rulesName,
                            rules = subscriptionsState.rules,
                            bypass = subscriptionsState.rulesBypass,
                            onAdd = { subscriptionsViewModel.addRules(this@HomeActivity, it) },
                            onRemove = { subscriptionsViewModel.removeRule(this@HomeActivity, it) },
                            onBack = { subscriptionsViewModel.closeRules() },
                            modifier = Modifier.padding(listInsets).consumeWindowInsets(overlayInsets),
                        )
                    }

                    BackLayer(
                        visible = showProbe,
                        onBack = closeProbe,
                    ) {
                        ProbeScreen(
                            state = probeState,
                            onSelect = { probeViewModel.select(it) },
                            onStart = { probeViewModel.start() },
                            onStop = { probeViewModel.stop() },
                            onCopy = { copyReport(probeViewModel.report()) },
                            onBack = closeProbe,
                            modifier = Modifier.padding(listInsets).consumeWindowInsets(overlayInsets),
                        )
                    }
                }

                // Панель удаления, как добавления и правки, живёт в композиции, пока уезжает вниз (D-118).
                var deleteShown by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.deleting) {
                    if (subscriptionsState.deleting != null) deleteShown = true
                }

                if (deleteShown) {
                    DeleteSubscriptionDialog(
                        open = subscriptionsState.deleting != null,
                        name = subscriptionsState.deletingName,
                        busy = subscriptionsState.deleteBusy,
                        onConfirm = {
                            subscriptionsState.deleting?.let {
                                subscriptionsViewModel.delete(this@HomeActivity, it)
                            }
                        },
                        onDismiss = { subscriptionsViewModel.askDelete(null) },
                        onClosed = { deleteShown = false },
                    )
                }

                // Свой ли конфиг — запоминается при открытии: пока панель уезжает, правка уже
                // закрыта, и поля ссылки не должны возникать в ней на прощание.
                var editLocal by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.editing) {
                    val editing = subscriptionsState.editing ?: return@LaunchedEffect

                    editShown = true
                    editLocal = subscriptionsState.profiles.firstOrNull { it.uuid == editing }?.local == true
                }

                if (editShown) {
                    EditSubscriptionDialog(
                        open = subscriptionsState.editing != null,
                        name = subscriptionsState.editName,
                        title = subscriptionsState.editTitle,
                        rules = subscriptionsState.editing?.let { subscriptionsState.ruleCounts[it] } ?: 0,
                        onRules = {
                            subscriptionsState.editing?.let { uuid ->
                                subscriptionsViewModel.openRules(this@HomeActivity, uuid)
                                subscriptionsViewModel.showEdit(null)
                            }
                        },
                        onConfig = {
                            subscriptionsState.profiles.firstOrNull { it.uuid == subscriptionsState.editing }?.let { profile ->
                                configViewModel.open(this@HomeActivity, profile)
                                subscriptionsViewModel.showEdit(null)
                            }
                        },
                        onAddresses = {
                            subscriptionsState.editing?.let { uuid ->
                                subscriptionsViewModel.showEdit(null)
                                subscriptionsViewModel.showAddresses(uuid)
                            }
                        },
                        local = editLocal,
                        link = subscriptionsState.editLink,
                        interval = subscriptionsState.editInterval,
                        follow = subscriptionsState.editFollow,
                        providerInterval = subscriptionsState.editProviderInterval,
                        error = subscriptionsState.editError,
                        reach = subscriptionsState.editReach,
                        busy = subscriptionsState.editBusy,
                        backup = subscriptionsState.editBackup,
                        onName = { subscriptionsViewModel.setEditName(it) },
                        onLink = { subscriptionsViewModel.setEditLink(it) },
                        onInterval = { subscriptionsViewModel.setEditInterval(it) },
                        onFollow = { subscriptionsViewModel.followEditProvider() },
                        onConfirm = { subscriptionsViewModel.saveEdit(this@HomeActivity) },
                        onDismiss = { subscriptionsViewModel.showEdit(null) },
                        onClosed = {
                            editShown = false
                        },
                    )
                }

                var addressesShown by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.addressesFor) {
                    if (subscriptionsState.addressesFor != null) addressesShown = true
                }

                // While it slides away the sheet keeps the last list, as «+» and «Изменить» keep their
                // content: emptied at once, the sheet shrank on its way down. Not state — it is read
                // in the same composition that writes it.
                val keptAddresses = remember { arrayOfNulls<Profile>(1) }

                if (addressesShown) {
                    // The profile is looked up every time: an update while the list is open refreshes it.
                    subscriptionsState.profiles.firstOrNull { it.uuid == subscriptionsState.addressesFor }
                        ?.let { keptAddresses[0] = it }
                    val profile = keptAddresses[0]

                    SubscriptionAddressesSheet(
                        open = subscriptionsState.addressesFor != null && profile != null,
                        addresses = profile?.addresses.orEmpty(),
                        triedAt = profile?.triedAt ?: 0,
                        onDismiss = { subscriptionsViewModel.showAddresses(null) },
                        onClosed = { addressesShown = false },
                    )
                }

                var confirmShown by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.confirmUpdate) {
                    if (subscriptionsState.confirmUpdate != null) confirmShown = true
                }

                if (confirmShown) {
                    UpdateEditedDialog(
                        open = subscriptionsState.confirmUpdate != null,
                        onConfirm = {
                            subscriptionsState.confirmUpdate?.let { uuid ->
                                subscriptionsViewModel.dismissConfirmUpdate()
                                subscriptionsViewModel.update(this@HomeActivity, uuid, force = true)
                            }
                        },
                        onDismiss = { subscriptionsViewModel.dismissConfirmUpdate() },
                        onClosed = { confirmShown = false },
                    )
                }

                // Панель добавления живёт в композиции и после закрытия, пока уезжает вниз (D-99).
                var addShown by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.adding) {
                    if (subscriptionsState.adding) addShown = true
                }

                if (addShown) {
                    AddSubscriptionDialog(
                        open = subscriptionsState.adding,
                        value = subscriptionsState.inputShown,
                        longText = subscriptionsState.inputLong,
                        error = subscriptionsState.addError,
                        busy = subscriptionsState.busy,
                        progress = subscriptionsState.addProgress,
                        backup = subscriptionsState.addBackup,
                        onValue = { subscriptionsViewModel.setInput(it) },
                        onPaste = { paste() },
                        onScan = { qrScanner.launch(false) },
                        onFile = { pickSubscriptionFile.launch(arrayOf("*/*")) },
                        onConfirm = { subscriptionsViewModel.add(this@HomeActivity) },
                        onDismiss = {
                            subscriptionsViewModel.cancelAdd(this@HomeActivity)
                            subscriptionsViewModel.showAdd(false)
                        },
                        onClosed = { addShown = false },
                        receive = subscriptionsState.receive,
                        onNewCode = { subscriptionsViewModel.startReceive(this@HomeActivity) },
                    )
                }

                // Сканер узнал код телевизора (D-162): панель живёт в композиции, пока уезжает вниз.
                var sendShown by remember { mutableStateOf(false) }

                LaunchedEffect(subscriptionsState.sendTo) {
                    if (subscriptionsState.sendTo != null) sendShown = true
                }

                if (sendShown) {
                    SendToTvSheet(
                        open = subscriptionsState.sendTo != null,
                        profiles = subscriptionsState.profiles.sendable,
                        chosen = subscriptionsState.sendChoice,
                        error = subscriptionsState.sendError,
                        busy = subscriptionsState.sendBusy,
                        sent = subscriptionsState.sent,
                        onChoose = { subscriptionsViewModel.chooseSend(it) },
                        onConfirm = { subscriptionsViewModel.confirmSend(this@HomeActivity) },
                        onDismiss = { subscriptionsViewModel.closeSend() },
                        onClosed = { sendShown = false },
                    )
                }

                if (backgroundShown) {
                    BackgroundSheet(
                        open = backgroundOpen,
                        step = backgroundStep,
                        vendor = Vendor.current,
                        onAllow = {
                            awaitingSystem = true
                            requestBackgroundWork()
                        },
                        onOpen = {
                            markShellSeen()
                            openVendor(it)
                        },
                        onDismiss = {
                            if (backgroundStep == BackgroundStep.Vendor) markShellSeen()
                            backgroundOpen = false
                        },
                        onClosed = { backgroundShown = false },
                    )
                }

                // Панель перед системным окном VPN: живёт в композиции, пока уезжает вниз.
                val vpnAsk = vpnRequest.value
                var vpnShown by remember { mutableStateOf(false) }

                LaunchedEffect(vpnAsk) {
                    if (vpnAsk != null) vpnShown = true
                }

                if (vpnShown) {
                    VpnSheet(
                        open = vpnAsk != null,
                        onContinue = {
                            tips.vpnExplained = true
                            vpnRequest.value = null
                            vpnAsk?.let { vpnPermission.launch(it) }
                        },
                        onDismiss = {
                            vpnRequest.value = null
                            viewModel.cancelToggling()
                        },
                        onClosed = { vpnShown = false },
                    )
                }

                val updateRelease = updateState.release

                val updatedNow = updateState.updated

                if (updatedShown && updatedNow != null) {
                    UpdatedSheet(
                        open = updatedOpen,
                        updated = updatedNow,
                        onDismiss = { updatedOpen = false },
                        // Read — the card has done its job.
                        onClosed = {
                            updatedShown = false
                            Updater.acknowledged()
                        },
                    )
                }

                if (updateShown && updateRelease != null) {
                    UpdateSheet(
                        open = updateOpen,
                        release = updateRelease,
                        state = updateState,
                        vpn = state.running,
                        permitted = remember(resumes.intValue) { packageManager.canRequestPackageInstalls() },
                        onInstall = { withInstallPermission { Updater.install() } },
                        onAuto = { yes ->
                            if (yes) {
                                withInstallPermission {
                                    Updater.setAuto(true)
                                    Updater.install()
                                }
                            } else {
                                Updater.setAuto(false)
                            }
                        },
                        onDismiss = { updateOpen = false },
                        onClosed = { updateShown = false },
                    )
                }

            }
        }

        // Фон окна не нужен: экран целиком закрашивает Compose. Иначе каждый кадр видеокарта
        // заливала весь экран дважды — на Redmi Note 9S список узлов упирался в неё (D-128).
        // После setContent: AppCompat, собирая свой декор, ставит фон из темы заново.
        window.setBackgroundDrawable(null)

        holdSplash()

        // Главную обновляет onStart, в том же кадре. Отсюда нельзя: состояние туннеля
        // приложение узнаёт у службы только в onStart, и после выгрузки интерфейса здесь
        // читалось «выключено» — карточка открывалась выключенной и следом играла волну.
        settingsViewModel.refresh(this)

        if (savedInstanceState == null) openExternal(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        openExternal(intent)
    }

    /**
     * Что открыли снаружи. Ссылка со страницы провайдера (`ducker://install-config?url=…`, а также
     * `clash://` и `clashmeta://`) — в панель «+», как если бы её вставили руками: имя, выбор и
     * ошибки — те же, а скачивается она только по «Добавить» (D-211). `.yaml` из файлового
     * менеджера — в редактор конфига (D-124). Уведомление о новой версии — в панель обновления.
     */
    private fun openExternal(intent: Intent?) {
        if (intent?.action == Updater.ACTION_SHOW) {
            updateRequest.value = true

            return
        }

        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return

        if (uri.scheme in InstallLink.SCHEMES && uri.host == "install-config") {
            val url = uri.getQueryParameter("url")?.trim().orEmpty()

            if (url.isNotEmpty()) {
                // `mirror` repeats: the provider's backups for a link blocked from the start (stage 21, package 3).
                subscriptionsViewModel.openLink(InstallLink(url, uri.getQueryParameters("mirror")))

                // Тот же вход, что у «Подключиться» без подписки: вкладка «Подписки» и панель «+».
                needSubscription.value = true
            }

            return
        }

        configViewModel.openFile(this, uri)
    }

    override fun onStart() {
        super.onStart()

        Remote.broadcasts.addObserver(this)

        // Человек мог вернуться из настроек Android, где включил блокировку без VPN:
        // служба сверится и при надобности пересоберёт туннель (F-110). Туннель не
        // поднят — сообщение просто никто не примет.
        sendBroadcastSelf(Intent(Intents.ACTION_CHECK_LOCKDOWN))

        // Секундный опрос живёт ровно столько, сколько экран на глазах (D-77): в фоне
        // он раз в секунду дёргал чужой процесс, а показывать его результат некому.
        viewModel.startTrafficPolling(this@HomeActivity)

        viewModel.refresh(this@HomeActivity)

        starts.intValue++

        // Новая версия приложения: что знали в прошлый раз — сразу, проверка — если давно не было.
        Updater.opened()
    }

    override fun onResume() {
        super.onResume()

        // Человек мог вернуться из окна «работа в фоне» или настроек батареи (D-106).
        // Именно onResume: окно системы прозрачное, и после него onStart не зовётся.
        settingsViewModel.refreshBackground(this)
        settingsViewModel.refreshAlwaysOn()
        resumes.intValue++

        // Приложение на экране: о несостоявшемся VPN скажет карточка главной (D-227).
        StopNotice.hide(this)
    }

    override fun onStop() {
        super.onStop()

        Remote.broadcasts.removeObserver(this)

        viewModel.stopTrafficPolling()

        // Телевизор: кода на экране больше нет — порт закрыт (D-162).
        subscriptionsViewModel.expireReceive()

        // Заставку система рисует до запуска приложения и в своей теме: при тёмной теме ducker
        // и светлой системе запуск начинался с белого экрана. С Android 12 система запоминает
        // ночной режим приложения и берёт его для заставки. Здесь, а не по нажатию: это смена
        // конфигурации, и посреди волны темы (D-66) она пересобрала бы экран ещё раз.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                when (settingsViewModel.state.value.theme) {
                    SettingsStore.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                    SettingsStore.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        }
    }

    /**
     * Заставка держится, пока главная не получила первые данные (D-77).
     *
     * Иначе виден стык: заставка гаснет, данных ещё нет, и человек полсекунды смотрит
     * на почти пустой экран. Первая отрисовка откладывается, пока данные не приедут,
     * и всё это время на экране остаётся то, что рисует система: с Android 12 — свой
     * сплэш со значком, раньше — фон окна из темы.
     *
     * Держим не дольше [SPLASH_HOLD]: если служба не отвечает, заглушки честнее, чем
     * бесконечная заставка. `setKeepOnScreenCondition` здесь не годится — он есть
     * только в androidx-совместимости, а платформенный `SplashScreen` такого не умеет.
     */
    private fun holdSplash() {
        val content: View = findViewById(android.R.id.content)

        var ready = false

        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready) return false

                content.viewTreeObserver.removeOnPreDrawListener(this)

                return true
            }
        })

        // Список подписок нужен сразу: по нему решается, главная это или приветствие (D-98).
        // Иначе он приходил бы с опозданием вместе с соседними вкладками (D-91), и обычная
        // панель успевала мелькнуть перед приветствием.
        subscriptionsViewModel.refresh(this)

        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_HOLD) {
                viewModel.state.first { it.loaded }
                subscriptionsViewModel.state.first { it.loaded }
            }

            ready = true

            // Отменённая отрисовка сама не повторится, если композиция к этому моменту
            // успокоилась: просим кадр явно.
            content.invalidate()
        }
    }

    /**
     * Предложить добавить плитку в быстрые настройки (D-87, Android 13+).
     *
     * Система показывает своё окно с названием и значком, а ответ приходит числом:
     * добавили, уже была, отказались. Отказов подряд система может перестать
     * показывать окно вовсе — поэтому предложение только по нажатию, никаких
     * всплывающих просьб при запуске.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        val manager = getSystemService(StatusBarManager::class.java) ?: return

        manager.requestAddTileService(
            ComponentName(this, TileService::class.java),
            getString(R.string.launch_name),
            Icon.createWithResource(this, com.github.kr328.clash.service.R.drawable.ic_logo_service),
            mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.add_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.add_tile_already
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> return@requestAddTileService
                else -> R.string.add_tile_failed
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggle() {
        // Нажатие уже в работе: служба поднимает или опускает туннель, и второе
        // нажатие только гоняет её по кругу.
        if (viewModel.state.value.busy) return

        val running = Remote.broadcasts.clashRunning

        viewModel.markToggling(!running)

        if (running) {
            stopClashService()

            return
        }

        askNotifications()

        lifecycleScope.launch {
            val active = withProfile { queryActive() }

            if (active == null || !active.imported) {
                // Подписки ещё нет: отправляем туда, где её добавляют, а не на экран CMFA.
                needSubscription.value = true

                viewModel.cancelToggling()

                return@launch
            }

            // Подписка упёрлась в лимит устройств — подключение должно быть запрещено,
            // иначе лимит ничего не ограничивает (D-26).
            if (active.hwidBlocked) {
                // Без этого нажатие просто ничего не делает, и человек решит, что
                // сломалась кнопка, а не что провайдер отказал.
                Toast.makeText(
                    this@HomeActivity,
                    if (active.hwidReason == "not_supported") R.string.limit_hwid_required
                    else R.string.limit_max_devices,
                    Toast.LENGTH_LONG,
                ).show()

                viewModel.cancelToggling()

                return@launch
            }

            startClashService()?.let { askVpn(it) }
        }
    }

    /**
     * Первое подключение: системное окно VPN пугает «сможет отслеживать трафик», и человек жмёт
     * «Отмена». Сначала своя панель — что сейчас спросят и зачем, один раз; дальше сразу окно.
     */
    private fun askVpn(request: Intent) {
        if (tips.vpnExplained) vpnPermission.launch(request) else vpnRequest.value = request
    }

    /**
     * Вставка из буфера: самый частый способ добавить подписку — скопировать ссылку
     * из чата с провайдером.
     */
    private fun paste() {
        val clip = getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
            .trim()

        if (clip.isEmpty()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()

            return
        }

        subscriptionsViewModel.setInput(clip)
    }

    private fun refreshAll() {
        viewModel.refresh(this@HomeActivity)

        // Соседние вкладки собраны рядом с главной (D-76), и их обновление перерисовывает
        // их целиком: «Прокси» — 12–16 мс. Сразу после подключения это попадало в кадры
        // волны на кнопке и рвало её (D-91), поэтому — когда волна пройдёт.
        lifecycleScope.launch {
            delay(TABS_REFRESH_DELAY)

            proxyViewModel.refresh(this@HomeActivity)
            subscriptionsViewModel.refresh(this@HomeActivity)
        }
    }

    /**
     * Журнал — файлом в «Поделиться» (D-111). Его просят у человека издалека, и одно
     * касание до Telegram надёжнее, чем «скопируйте» (в сообщение не влезет) или
     * «сохраните и найдите файл».
     */
    private fun shareLog() {
        lifecycleScope.launch {
            val text = logsViewModel.report(this@HomeActivity)

            val uri = runCatching {
                withContext(Dispatchers.IO) {
                    val file = cacheDir.resolve("logs").apply { mkdirs() }.resolve("ducker-log.txt")

                    file.writeText(text)

                    FileProvider.getUriForFile(this@HomeActivity, "$packageName.logs", file)
                }
            }.getOrNull()

            if (uri == null) {
                Toast.makeText(this@HomeActivity, R.string.logs_save_failed, Toast.LENGTH_SHORT).show()

                return@launch
            }

            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "ducker log")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            runCatching { startActivity(Intent.createChooser(send, getString(R.string.logs_share))) }
        }
    }

    private fun copyReport(text: String, done: Int = R.string.probe_copied) {
        val clipboard = getSystemService(ClipboardManager::class.java)

        clipboard?.setPrimaryClip(ClipData.newPlainText("ducker", text))

        Toast.makeText(this, done, Toast.LENGTH_SHORT).show()
    }

    /**
     * Выбор приложений применяется только при создании туннеля, поэтому после изменения
     * ядро надо перезапустить — иначе человек увидит старое поведение и решит, что
     * настройка не работает.
     */
    private fun applyAppFilter() {
        lifecycleScope.launch {
            if (!appsViewModel.save(this@HomeActivity)) return@launch
            if (!Remote.broadcasts.clashRunning) return@launch

            stopClashService()

            // Ждём остановки, но не вечно: если служба почему-то не отчиталась, лучше
            // попробовать поднять туннель заново, чем молча оставить его выключенным.
            withTimeoutOrNull(RESTART_TIMEOUT) {
                while (Remote.broadcasts.clashRunning) {
                    delay(200)
                }
            }

            // Разрешение на VPN могли отозвать в системных настройках, пока приложение
            // работало: тогда служба вернёт запрос, и его надо показать.
            startClashService()?.let { vpnPermission.launch(it) }
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        // Отказ ничего не ломает: туннель поднимется всё равно, просто без уведомления.
        if (!granted) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applyRecents(hide: Boolean) {
        runCatching {
            getSystemService(ActivityManager::class.java)?.appTasks?.forEach {
                it.setExcludeFromRecents(hide)
            }
        }
    }

    private fun open(url: String) {
        val destination = providerLink(url) ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))) }
    }

    /** Экран Android «Установка неизвестных приложений» для ducker. Открылся ли. */
    private fun askInstallPermission(): Boolean = runCatching {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }.isSuccess

    /** Письмо — в почтовое приложение; нет его (телевизор) — адрес в буфер обмена. */
    private fun mail() {
        runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$MAIL"))) }
            .onFailure { copyReport(MAIL, R.string.mail_copied) }
    }

    override fun onServiceRecreated() = refreshAll()
    override fun onStarted() = refreshAll()
    override fun onStopped(cause: String?) {
        // Причину остановки главная читает с диска и показывает карточкой (D-227).
        refreshAll()

        settingsViewModel.refreshAlwaysOn()
    }
    override fun onProfileChanged() = refreshAll()
    override fun onProfileUpdateCompleted(uuid: UUID?) {
        subscriptionsViewModel.reportUpdateFinished(this, uuid)
        viewModel.refresh(this@HomeActivity)
    }

    // Загрузка идёт в фоновой задаче, поэтому о её исходе мы узнаём только отсюда.
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        subscriptionsViewModel.reportUpdateFailed(this, uuid, reason)
        viewModel.refresh(this@HomeActivity)
    }
    override fun onProfileLoaded() {
        refreshAll()

        // «Постоянный VPN» система отвечает, только пока туннель поднят: его могли поднять
        // плиткой или командой при открытых настройках (F-218).
        settingsViewModel.refreshAlwaysOn()
    }

    private companion object {
        /** Дольше этого сплэш не держим: служба может и не ответить. */
        const val SPLASH_HOLD = 500L

        /** Волна на кнопке идёт 520 мс — соседние вкладки обновляем после неё. */
        const val TABS_REFRESH_DELAY = 700L

        /** Окно «работа в фоне» — после волны, а не поверх неё (D-106). */
        const val BACKGROUND_ASK_DELAY = 1500L

        /** «Не сейчас» на карточке прячет её на неделю (D-109). */
        const val BACKGROUND_SNOOZE = 7L * 24 * 60 * 60 * 1000
    }
}
