package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
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
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.parseCIDR
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicBoolean

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val self: TunService
        get() = this

    private var reason: String? = null

    /** С каким режимом блокировки собран текущий туннель. */
    private var lockdownApplied = false

    /** The subscription's `tun` values the interface was built with; [openLock] guards both. */
    private var builtWith: ProfileNetwork? = null
    private val openLock = Any()

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        // До первой загрузки конфига: истёкший «Подробный журнал» в неё уже не попадёт (D-224).
        runCatching { VerboseLog.check(self) }

        val close = install(CloseModule(self))
        val tun = install(TunModule(self))
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
        install(MobileModule(self))

        try {
            synchronized(openLock) { tun.open() }

            tunOpened.set(true)

            // В самописец: «постоянный VPN» и блокировка без VPN меняют маршруты (F-110).
            Log.i("Tunnel up: always-on=$isAlwaysOn, lockdown=$isLockdownEnabled")

            if (config.ready) recheck()

            while (isActive) {
                val quit = select<Boolean> {
                    close.onEvent {
                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    // Ветку с setUnderlyingNetworks сняли вместе с подъёмом minSdk до 29
                    // (D-13): она работала только на 22..28. Событие всё равно вычитываем —
                    // иначе очередь модуля некому разбирать.
                    network.onEvent {
                        false
                    }
                    // Блокировку включили или выключили — пересобираем туннель на месте:
                    // ядро само подменит старый интерфейс новым.
                    lockdown.onEvent {
                        val now = isLockdownEnabled

                        if (now != lockdownApplied) {
                            synchronized(openLock) { tun.open(now) }

                            // Главная и плитка перечитают состояние, в том числе
                            // предупреждение о приложениях без сети.
                            sendClashStarted()
                        }

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

                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (StatusProvider.serviceRunning)
            return stopSelf()

        StatusProvider.serviceRunning = true

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
        sendClashStarted()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
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
     * Возвращает, осталось ли хоть одно приложение мимо туннеля.
     */
    private fun applyPackageFilter(builder: Builder, store: ServiceStore, profile: ProfileNetwork): Boolean {
        val decision = PackageFilter.decide(store.accessControlMode, store.accessControlPackages, profile)

        val allowed = decision.allowed

        if (allowed != null) {
            (allowed + packageName).forEach { runCatching { builder.addAllowedApplication(it) } }

            return true
        }

        // В списке провайдера сотни пакетов, а стоит из них горстка: считаем только те,
        // что система приняла.
        return (decision.denied - packageName).count {
            runCatching { builder.addDisallowedApplication(it) }.isSuccess
        } > 0
    }

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

            if (now.tunValues() == builtWith) return

            Log.i("Another subscription's tun values: rebuilding the interface")

            open(lockdownApplied)
        }

        // Home and the tile read the state again, the warning about apps without network included.
        sendClashStarted()
    }

    private fun TunModule.open(lockdown: Boolean = false) {
        val store = ServiceStore(self)

        // Раздел `tun:` профиля ядро на Android не применяет — интерфейс создаём мы,
        // поэтому списки пакетов и признак отключённого DNS читаем сами (F-69).
        val profile = store.activeProfile
            ?.let { Clash.queryProfileNetwork(self.importedDir.resolve(it.toString()).absolutePath) }
            ?: ProfileNetwork()

        // В режиме «Блокировать соединения без VPN» система режет всё, что идёт мимо
        // туннеля (F-110): исключённые из маршрутов адреса — и домашняя сеть в первую
        // очередь — стали бы недоступны. Поэтому в этом режиме маршрутов не вырезаем,
        // а локальные адреса ядро само отправит напрямую: правило для них в шаблоне
        // есть, а сокеты ядра блокировка не трогает.
        lockdownApplied = lockdown
        builtWith = profile.tunValues()

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
            store.lockdownOffline = applyPackageFilter(this, store, profile) && lockdown

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
            setMetered(false)

            // System Proxy
            if (store.systemProxy) {
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

            TunModule.TunDevice(
                fd = establish()?.detachFd()
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

        attach(device)

        // Про блокировку система отвечает только тому, у кого туннель уже поднят: до
        // establish() ответ всегда «нет». Поэтому поднимаемся как обычно и, если режим
        // не совпал, сразу пересобираемся — ядро подменит интерфейс на месте.
        if (isLockdownEnabled != lockdown) open(!lockdown)
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
