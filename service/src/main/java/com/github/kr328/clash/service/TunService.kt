package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.*
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.util.Cidr
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.PackageFilter
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.store.ConfigAppsStore
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.parseCIDR
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.measureTime

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val self: TunService
        get() = this

    private var reason: String? = null

    /** С каким режимом блокировки собран текущий туннель. */
    private var lockdownApplied = false

    // Android 10+: an older system does not tell the app whether it is always-on or locked down (D-482).
    private val lockdownOn: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLockdownEnabled
    private val alwaysOn: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlwaysOn

    /** The subscription's `tun` values the interface was built with; [openLock] guards both. */
    private var builtWith: ProfileNetwork? = null
    /** What the person changed of that subscription's config apps (D-473), as the interface was built with it. */
    private var builtChanges = PackageFilter.Changes()
    private val openLock = Any()

    /** `elapsedRealtime` of [onCreate]: the connect's timing line counts from it (D-454). */
    private var createdAt = 0L

    /**
     * Apps the filter names that were installed while this tunnel is up, once for each install; [openLock] guards it.
     * Android works out the tunnel's uid ranges anew only when `establish()` is given another list of names than the
     * last time: the same names are a seamless handover that keeps the old ranges, so the new app stayed on the wrong
     * side (F-445). Naming these once more makes the list differ.
     */
    private val installedLate = ArrayList<String>()

    /**
     * Set once this instance is asked off or its runtime ends. Android keeps a VPN service bound until its interface is
     * gone, so a start in that time is delivered here, and the instance it starts again is never destroyed: no tunnel,
     * every screen saying on, stops unheard (F-492). Such a start brings up the next instance instead ([restartWanted]).
     */
    @Volatile
    private var stopping = false
    private var restartWanted = false

    /**
     * Heard from [onCreate] on: the runtime's own receiver came up only after the previous runtime's cleanup and the
     * core's reset, and a stop before it was lost while every screen already showed the VPN on (F-492).
     */
    private val stops = Channel<Unit>(Channel.CONFLATED)
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("User request close")

            stopping = true
            // Asked off, whoever asked (the notification's button too): a toggle sent while this instance is still
            // going down sees it off, and no restore brings it back (F-423).
            ServiceStore(self).tunnelStartedAt = 0
            stops.trySend(Unit)
        }
    }

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        // До первой загрузки конфига: истёкший «Подробный журнал» в неё уже не попадёт (D-224).
        runCatching { VerboseLog.check(self) }

        val tun = install(TunModule(self))
        // Before the config starts loading: a socket its downloads open before this stays unprotected, and once the
        // interface is up its packets go into the tunnel (D-431). Every stop releases it: TunModule's finally, onDestroy.
        tun.protectEarly()
        val config = install(ConfigurationModule(self))

        // Первая проверка групп с автовыбором идёт при загрузке конфига, а конфиг и туннель
        // встают одновременно: кто первым — дело случая. Проверка до туннеля или в первые
        // мгновения после него записывает узлы мёртвыми, и автовыбор держит первый попавшийся
        // до пяти минут. Перемеряем один раз, когда готово и то и другое: запускает тот,
        // кто закончил вторым (D-110). Флаги ставятся до проверки чужого — хотя бы одна
        // сторона увидит другую; дважды не запустится.
        val rechecked = AtomicBoolean(false)
        val tunOpened = AtomicBoolean(false)
        val recheck = {
            if (rechecked.compareAndSet(false, true)) Clash.healthCheckAuto()
        }

        config.onLoaded = {
            if (tunOpened.get()) {
                recheck()
                tun.rebuildIfChanged()
            }
        }
        val network = install(NetworkObserveModule(self))
        val lockdown = install(LockdownModule(self))

        if (store.dynamicNotification)
            install(DynamicNotificationModule(self))
        else
            install(StaticNotificationModule(self))

        install(AppListCacheModule(self))
        install(TimeZoneModule(self))
        install(SuspendModule(self))
        install(ShareModule(self))
        install(MobileModule(self) { config.ready })

        try {
            // Asked off by now: the interface is not built at all. A stop during the build takes it down at the loop.
            if (stopping) return@clashRuntime

            synchronized(openLock) { tun.open(resetMs = resetMs) }

            // After the first build: it has every app installed before it, only a later install matters (D-454).
            val installs = install(AppInstallModule(self))

            tunOpened.set(true)

            // В самописец: «постоянный VPN» и блокировка без VPN меняют маршруты (F-110).
            Log.i("Tunnel up: always-on=$alwaysOn, lockdown=$lockdownOn")

            if (config.ready) recheck()

            while (isActive) {
                val quit = select<Boolean> {
                    stops.onReceive {
                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    // Android 8–9 as CMFA did it (D-482); from 10 the system follows the default network itself.
                    // The event is read on every version, or nothing drains the module's queue.
                    network.onEvent {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) setUnderlyingNetworks(arrayOf(it))

                        false
                    }
                    // Блокировку включили или выключили — пересобираем туннель на месте:
                    // ядро само подменит старый интерфейс новым.
                    lockdown.onEvent {
                        val now = lockdownOn

                        if (now != lockdownApplied) {
                            synchronized(openLock) { tun.open(now) }

                            // Главная и плитка перечитают состояние, в том числе
                            // предупреждение о приложениях без сети.
                            sendClashStarted()
                        }

                        false
                    }
                    // An app the filter names was installed with the tunnel up: the interface was built without
                    // it, so it is on the wrong side until rebuilt (D-454). Open connections break once.
                    installs.onEvent { installed ->
                        val now = lockdownOn

                        val rebuilt = synchronized(openLock) {
                            val named = installed.namedBy(
                                PackageFilter.decide(
                                    store.accessControlMode, store.accessControlPackages, builtWith ?: ProfileNetwork(),
                                    builtChanges,
                                )
                            )

                            when {
                                named.isEmpty() -> false

                                // While Android swaps the old network for the new one, no rule sends the apps
                                // into a tunnel for tens of milliseconds, and what they send then leaves with the
                                // phone's own address (F-446). Its lockdown drops those packets instead, so only
                                // under it is the rebuild done; otherwise the app waits for the next connect.
                                !now -> {
                                    Log.i("An app the filter names was installed: no lockdown, it waits for the next connect")

                                    false
                                }

                                else -> {
                                    Log.i("An app the filter names was installed: rebuilding the interface")

                                    installedLate += named

                                    tun.open(now)

                                    true
                                }
                            }
                        }

                        if (rebuilt) sendClashStarted()

                        false
                    }
                }

                if (quit) break
            }
        } catch (e: Exception) {
            Log.e("Create clash runtime: ${e.message}", e)

            reason = e.message
        } finally {
            withContext(NonCancellable) {
                tun.close()

                stopping = true
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (StatusProvider.serviceRunning)
            return stopSelf()

        StatusProvider.serviceRunning = true
        createdAt = SystemClock.elapsedRealtime()

        registerReceiverCompat(
            stopReceiver, IntentFilter(Intents.ACTION_CLASH_REQUEST_STOP), Permissions.RECEIVE_SELF_BROADCASTS,
        )

        // Причина прошлой остановки к новому подъёму не относится (D-227).
        StopNotice.clear(this)

        FlightRecorder.attachCore()

        // Отсюда считается время работы туннеля на главном экране.
        ServiceStore(this).tunnelStartedAt = System.currentTimeMillis()

        StaticNotificationModule.createNotificationChannel(this)
        StaticNotificationModule.notifyLoadingNotification(this)

        // Страж поднимет туннель, если процесс умрёт на ходу (D-90). Стартуем после
        // уведомления: служба уже на переднем плане, и фоновый запуск разрешён.
        startService(TunGuard::class.intent)

        runtime.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The receiver and this both run on the main thread, so a start after a stop always sees it. stopSelf() takes
        // back what the start asked of Android, and onDestroy brings up the next instance. A runtime that ends by
        // itself sets [stopping] on its own thread: a start just before that joins this instance and hears its stop
        // with the reason, which is right — starting again would only fail the same way.
        if (stopping) {
            restartWanted = true

            stopSelf()
        } else {
            sendClashStarted()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // Not registered when onCreate found another instance running.
        runCatching { unregisterReceiver(stopReceiver) }

        TunModule.requestStop()

        StatusProvider.serviceRunning = false

        ServiceStore(this).apply {
            tunnelStartedAt = 0
            lockdownOffline = false
        }

        // Остановка штатная — поднимать после неё нечего.
        stopService(TunGuard::class.intent)

        // До сообщения об остановке: главная по нему перечитывает причину с диска (D-227).
        reason?.let { StopNotice.show(this, it) }

        sendClashStopped(reason)

        // The runtime's cleanup resets the core, which waits while a config is still loading — up to the 20 s
        // of a provider download. On the main thread that froze `:background`: an ANR, and a start tapped
        // meanwhile could not reach startForeground in time. The next start waits for this runtime anyway
        // (`globalLock` in ClashRuntime), so the wait here can be short.
        cancelAndJoinBlocking(STOP_WAIT_MS)

        Log.i("TunService destroyed: ${reason ?: "successfully"}")

        // Allowed from the background: the prepared VPN app may start its service (F-113).
        if (restartWanted) {
            Log.i("Started while going down: starting again")

            startForegroundServiceCompat(TunService::class.intent)
        }

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runtime.requestGc()
    }


    /**
     * Какие приложения идут в туннель.
     *
     * Android разрешает либо белый список, либо чёрный, но не оба сразу, поэтому порядок
     * такой: если профиль задал `include-package`, это белый список, иначе — чёрный из
     * `exclude-package`. Выбор человека в настройках складывается с профильным, а не
     * отменяет его: список провайдера нужен, чтобы банковские и госприложения видели сеть
     * без VPN, и молча его терять нельзя.
     *
     * Returns what the system took of the list.
     */
    private fun applyPackageFilter(
        builder: Builder, store: ServiceStore, profile: ProfileNetwork, changes: PackageFilter.Changes,
    ): Filtered {
        val decision = PackageFilter.decide(store.accessControlMode, store.accessControlPackages, profile, changes)

        val allowed = decision.allowed

        if (allowed != null) {
            val asked = allowed + packageName
            val accepted = asked.count { runCatching { builder.addAllowedApplication(it) }.isSuccess }

            installedLate.forEach { if (it in asked) runCatching { builder.addAllowedApplication(it) } }

            return Filtered(accepted, asked.size, outside = true)
        }

        // В списке провайдера сотни пакетов, а стоит из них горстка: считаем только те,
        // что система приняла.
        // The Builder takes a name that is not installed as well (F-445), so the count is every name asked.
        val asked = decision.denied - packageName
        val accepted = asked.count { runCatching { builder.addDisallowedApplication(it) }.isSuccess }

        installedLate.forEach { if (it in asked) runCatching { builder.addDisallowedApplication(it) } }

        return Filtered(accepted, asked.size, outside = accepted > 0)
    }

    /** How many of the [asked] names the system [accepted]; [outside] — some app is left past the tunnel. */
    private class Filtered(val accepted: Int, val asked: Int, val outside: Boolean)

    /**
     * The interface takes the subscription's `tun` values — app lists, excluded routes, stack, DNS (F-69) — and a
     * load of another subscription, picked by the person or by «Мобильная сеть», kept the first one's. Rebuilt in
     * place as for the lockdown switch; called before the load is announced, so what waits for it runs on the new one.
     * The core closes the old interface before it starts on the new one (`startTun`), so a rebuild that fails leaves
     * no tunnel: its exception goes out of `onLoaded` to ConfigurationModule, which stops the service with the reason.
     */
    private fun TunModule.rebuildIfChanged() {
        synchronized(openLock) {
            val active = ServiceStore(self).activeProfile ?: return
            val now = Clash.queryProfileNetwork(self.importedDir.resolve(active.toString()).absolutePath)

            // The person's changes follow their rules, and a provider's lock that came or went (D-473).
            if (now.tunValues() == builtWith && ConfigAppsStore(self).changes(active) == builtChanges) return

            Log.i("Another subscription's tun values: rebuilding the interface")

            open(lockdownApplied)
        }

        // Home and the tile read the state again, the warning about apps without network included.
        sendClashStarted()
    }

    /** [resetMs] — the core's reset before a connect; null for a rebuild of a running tunnel. */
    private fun TunModule.open(lockdown: Boolean = false, resetMs: Long? = null) {
        val started = SystemClock.elapsedRealtime()
        val store = ServiceStore(self)

        // Раздел `tun:` профиля ядро на Android не применяет — интерфейс создаём мы,
        // поэтому списки пакетов и признак отключённого DNS читаем сами (F-69).
        val profile: ProfileNetwork
        val profileMs = measureTime {
            profile = store.activeProfile
                ?.let { Clash.queryProfileNetwork(self.importedDir.resolve(it.toString()).absolutePath) }
                ?: ProfileNetwork()
        }.inWholeMilliseconds

        // В режиме «Блокировать соединения без VPN» система режет всё, что идёт мимо
        // туннеля (F-110): исключённые из маршрутов адреса — и домашняя сеть в первую
        // очередь — стали бы недоступны. Поэтому в этом режиме маршрутов не вырезаем,
        // а локальные адреса ядро само отправит напрямую: правило для них в шаблоне
        // есть, а сокеты ядра блокировка не трогает.
        lockdownApplied = lockdown
        builtWith = profile.tunValues()
        builtChanges = store.activeProfile?.let { ConfigAppsStore(self).changes(it) } ?: PackageFilter.Changes()

        val filtered: Filtered
        val filterMs: Long
        val establishMs: Long

        val device = with(Builder()) {
            // Interface address
            addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
            if (store.allowIpv6) {
                addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
            }

            // Route
            // Исключения собираем из двух источников: приватные сети (настройка
            // приложения) и `tun.route-exclude-address` из профиля (D-56). Встроенный
            // список приложения задан уже как маршруты-дополнения, поэтому обратно в
            // исключения его переводит та же арифметика.
            val excludedV4 = if (lockdown) emptyList() else buildList {
                if (store.bypassPrivateNetwork) {
                    addAll(
                        Cidr.complementV4(
                            resources.getStringArray(R.array.bypass_private_route).toList()
                        ).map { "${it.address}/${it.prefix}" }
                    )
                }

                addAll(profile.routeExcludeAddress)
            }

            if (excludedV4.isNotEmpty()) {
                Cidr.complementV4(excludedV4).forEach { addRoute(it.address, it.prefix) }

                if (store.allowIpv6) {
                    if (store.bypassPrivateNetwork) {
                        resources.getStringArray(R.array.bypass_private_route6)
                            .map(::parseCIDR)
                            .forEach { addRoute(it.ip, it.prefix) }
                    } else {
                        addRoute(NET_ANY6, 0)
                    }
                }

                // Route of virtual DNS
                addRoute(TUN_DNS, 32)
                if (store.allowIpv6) {
                    addRoute(TUN_DNS6, 128)
                }
            } else {
                addRoute(NET_ANY, 0)
                if (store.allowIpv6) {
                    addRoute(NET_ANY6, 0)
                }
            }

            // Access Control
            filterMs = measureTime { filtered = applyPackageFilter(this, store, profile, builtChanges) }.inWholeMilliseconds
            store.lockdownOffline = filtered.outside && lockdown

            // Blocking
            setBlocking(false)

            // Mtu
            setMtu(TUN_MTU)

            // Session Name
            // Это имя система показывает в своём уведомлении «VPN включён»,
            // поэтому здесь название приложения, а не ядра.
            setSession("ducker")

            // Virtual Dns Server
            // Если профиль отключил DNS ядра, перехватывать запросы нельзя: обслуживать
            // их будет некому. Пусть приложения спрашивают системный DNS через туннель.
            if (!profile.dnsDisabled) {
                addDnsServer(TUN_DNS)
                if (store.allowIpv6) {
                    addDnsServer(TUN_DNS6)
                }
            }

            // Open MainActivity
            setConfigureIntent(
                PendingIntent.getActivity(
                    self,
                    R.id.nf_vpn_status,
                    Intent().setComponent(Components.MAIN_ACTIVITY),
                    pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                )
            )

            // Metered
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)

            // System Proxy: a VPN can set one from Android 10.
            if (store.systemProxy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listenHttp()?.let {
                    setHttpProxy(
                        ProxyInfo.buildDirectProxy(
                            it.address.hostAddress,
                            it.port,
                            HTTP_PROXY_BLACK_LIST + if (store.bypassPrivateNetwork) HTTP_PROXY_LOCAL_LIST else emptyList()
                        )
                    )
                }
            }

            if (store.allowBypass) {
                allowBypass()
            }

            val descriptor: ParcelFileDescriptor?
            establishMs = measureTime { descriptor = establish() }.inWholeMilliseconds

            TunModule.TunDevice(
                fd = descriptor?.detachFd()
                    ?: throw NullPointerException(Failure.VPN_REJECTED),
                // Пока человек не выбрал стек, решает `tun.stack` провайдера (D-56);
                // выбрал — его выбор главнее, иначе настройка была бы декоративной (D-86).
                stack = store.tunStackMode.ifEmpty { profile.stack },
                // Действует только на стеке mips; значение провайдера идёт как есть (D-235).
                congestion = profile.congestionController,
                gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" + if (store.allowIpv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
                portal = TUN_PORTAL + if (store.allowIpv6) ",$TUN_PORTAL6" else "",
                // Перехват DNS имеет смысл, только если запросы есть кому обслуживать.
                // При `dns.enable: false` в профиле резолвера у ядра нет, и перехваченные
                // запросы просто умирали бы: имена перестали бы разрешаться совсем.
                // Пустая строка означает «не перехватывать» (native/tun/tun.go:45).
                dns = when {
                    profile.dnsDisabled -> ""
                    store.dnsHijacking -> NET_ANY
                    else -> TUN_DNS + if (store.allowIpv6) ",$TUN_DNS6" else ""
                },
            )
        }

        val coreMs = measureTime { attach(device) }.inWholeMilliseconds

        // Where a connect's time goes (D-454). The total of a connect counts from the service's creation, so what the
        // parts leave out shows as the difference. Numbers only: no addresses, no package names (invariant 4).
        val total = SystemClock.elapsedRealtime() - if (resetMs != null) createdAt else started

        Log.i(
            (if (resetMs != null) "Connect $total ms: reset $resetMs, " else "Rebuild $total ms: ") +
                    "profile $profileMs, apps $filterMs (accepted ${filtered.accepted} of ${filtered.asked}), " +
                    "establish $establishMs, core $coreMs"
        )

        // Про блокировку система отвечает только тому, у кого туннель уже поднят: до
        // establish() ответ всегда «нет». Поэтому поднимаемся как обычно и, если режим
        // не совпал, сразу пересобираемся — ядро подменит интерфейс на месте.
        if (lockdownOn != lockdown) open(!lockdown)
    }

    companion object {
        private const val STOP_WAIT_MS = 3_000L
        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
        private const val TUN_DNS = TUN_PORTAL
        private const val TUN_DNS6 = TUN_PORTAL6
        private const val NET_ANY = "0.0.0.0"
        private const val NET_ANY6 = "::"

        private val HTTP_PROXY_LOCAL_LIST: List<String> = listOf(
            "localhost",
            "*.local",
            "127.*",
            "10.*",
            "172.16.*",
            "172.17.*",
            "172.18.*",
            "172.19.*",
            "172.2*",
            "172.30.*",
            "172.31.*",
            "192.168.*"
        )
        private val HTTP_PROXY_BLACK_LIST: List<String> = listOf(
            "*zhihu.com",
            "*zhimg.com",
            "*jd.com",
            "100ime-iat-api.xfyun.cn",
            "*360buyimg.com",
        )
    }
}

/** What of a subscription the interface is built from: all but `mode`, which the core applies by itself. */
internal fun ProfileNetwork.tunValues() = copy(mode = "")
