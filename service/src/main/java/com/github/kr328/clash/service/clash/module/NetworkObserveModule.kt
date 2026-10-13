package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.net.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.asSocketAddressText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NetworkObserveModule(service: Service) : Module<Network>(service) {
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val store = ServiceStore(service)
    private val networks: Channel<Network> = Channel(Channel.UNLIMITED)
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    private data class NetworkInfo(
        @Volatile var losingMs: Long = 0,
        @Volatile var dnsList: List<InetAddress> = emptyList()
    ) {
        fun isAvailable(): Boolean = losingMs < System.currentTimeMillis()
    }

    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var curDnsList = emptyList<String>()

    /**
     * Какая сеть сейчас основная. По ней и определяется настоящая смена сети: колбэки
     * вроде `onLinkPropertiesChanged` прилетают и без неё — например при обновлении
     * списка DNS или адреса IPv6, — а закрывать соединения на каждый чих нельзя.
     */
    @Volatile
    private var bestNetwork: Network? = null

    /** Когда подписались на сети, `elapsedRealtime`: от него отсчитываем первую раздачу. */
    @Volatile
    private var registeredAt = 0L

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("NetworkObserve onAvailable network=$network")
            networkInfos[network] = NetworkInfo()

            notifyNetworkChange()
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Log.i("NetworkObserve onLosing network=$network")
            networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive
            notifyDnsChange()
            notifyNetworkChange()

            networks.trySend(network)
        }

        override fun onLost(network: Network) {
            Log.i("NetworkObserve onLost network=$network")
            networkInfos.remove(network)
            notifyDnsChange()
            notifyNetworkChange()

            networks.trySend(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            // Целиком LinkProperties — около килобайта на строку, и меняется часто: самописец
            // забивался, а с ним адреса телефона (аудит, perf 2.5). Хватает интерфейса и DNS.
            Log.i("NetworkObserve onLinkPropertiesChanged network=$network ${linkProperties.interfaceName} dns=${linkProperties.dnsServers}")
            networkInfos[network]?.dnsList = linkProperties.dnsServers
            notifyDnsChange()
            notifyNetworkChange()

            networks.trySend(network)
        }

        override fun onUnavailable() {
            Log.i("NetworkObserve onUnavailable")
        }
    }

    private fun register(): Boolean {
        Log.i("NetworkObserve start register")
        return try {
            registeredAt = SystemClock.elapsedRealtime()
            connectivity.registerNetworkCallback(request, callback)

            true
        } catch (e: Exception) {
            Log.w("NetworkObserve register failed", e)

            false
        }
    }

    private fun unregister(): Boolean {
        Log.i("NetworkObserve start unregister")
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w("NetworkObserve unregister failed", e)
        }

        return false
    }

    private fun networkToInt(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = connectivity.getNetworkCapabilities(entry.key)
        // calculate priority based on transport type, available state
        // lower value means higher priority
        // wifi > ethernet > usb tethering > bluetooth tethering > cellular > satellite > other
        return when {
            capabilities == null -> 100
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> 5
            // TRANSPORT_LOWPAN / TRANSPORT_THREAD / TRANSPORT_WIFI_AWARE are not for general internet access, which will not set as default route.
            else -> 20
        } + (if (entry.value.isAvailable()) 0 else 10)
    }

    /**
     * Соединения, открытые через пропавший интерфейс, сами об этом не узнают и висят до
     * своего таймаута — десятки секунд, в которые приложения кажутся зависшими. Закрываем
     * их сразу, и приложения переподключаются по новой сети (D-59).
     */
    private fun notifyNetworkChange() {
        val best = networkInfos.asSequence().minByOrNull { networkToInt(it) }?.key

        if (best == bestNetwork) return

        val previous = bestNetwork

        bestNetwork = best

        // Сразу после подписки Android по очереди присылает все уже подключённые сети —
        // «лучшая» за миллисекунды меняется с мобильной на Wi-Fi. Это знакомство, а не смена
        // сети: сброс мультиплекса (D-105) рвал бы проверки узлов, начатые в ту же секунду (D-109).
        if (SystemClock.elapsedRealtime() - registeredAt < SETTLE_MS) return

        // Another network carries the tunnel from now on, also when it comes after none at all:
        // delay checks made before this moment say nothing about it (D-299).
        if (best != null) store.networkChangedAt = System.currentTimeMillis()

        // При первом появлении сети закрывать нечего: соединений ещё нет.
        if (previous == null) return

        Log.i("NetworkObserve best network $previous -> $best, closing connections")

        // Сессии мультиплекса — отдельно: это не соединения туннеля, и без сброса новые
        // потоки уходили бы в канал через прежнюю сеть (D-105).
        Clash.resetMux()
        Clash.closeAllConnections()
    }

    private fun notifyDnsChange() {
        val dnsList = (networkInfos.asSequence().minByOrNull { networkToInt(it) }?.value?.dnsList
            ?: emptyList()).map { x -> x.asSocketAddressText(53) }
        val prevDnsList = curDnsList
        if (dnsList.isNotEmpty() && prevDnsList != dnsList) {
            Log.i("notifyDnsChange $prevDnsList -> $dnsList")
            curDnsList = dnsList
            Clash.notifyDnsChanged(dnsList)
        }
    }

    override suspend fun run() {
        register()

        try {
            while (true) {
                val quit = select {
                    networks.onReceive {
                        enqueueEvent(it)

                        false
                    }
                }
                if (quit) {
                    return
                }
            }
        } finally {
            withContext(NonCancellable) {
                unregister()

                Log.i("NetworkObserve dns = []")
                Clash.notifyDnsChanged(emptyList())
            }
        }
    }
}

/** Первая раздача сетей после подписки приходит за миллисекунды; секунды хватает с запасом. */
private const val SETTLE_MS = 1000L
