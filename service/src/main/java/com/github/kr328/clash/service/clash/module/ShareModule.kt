package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.model.ShareState
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * «Раздача по Wi-Fi» (D-419–D-421): the core's own port on the phone's address in one network, while the VPN runs.
 *
 * The address is the phone's own network first — its hotspot, USB or Bluetooth tethering, named by the system's
 * tethering broadcast — else Wi-Fi or Ethernet. Only a private IPv4 address, and only that one: the mobile network
 * often gives the phone a public address, and the port must never be open there. It goes off when that network goes
 * away or is replaced, or the phone's address in it changes (D-420): Android names a Wi-Fi network only to apps with
 * location access, so a router's reconnect counts too, and a port is never carried into a café unnoticed.
 */
class ShareModule(service: Service) : Module<Unit>(service) {
    private val store = ServiceStore(service)
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val lock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Wi-Fi and Ethernet networks with the phone's private IPv4 address there, «192.168.1.5/24». */
    private val networks = ConcurrentHashMap<Network, String>()

    /** The interfaces the phone shares its connection through: hotspot, USB, Bluetooth. */
    @Volatile
    private var tethered: List<String> = emptyList()

    /** Where the port is open; guarded by [lock]. */
    private var open: Spot? = null

    @Volatile
    var state = ShareState(running = true, closed = providerClosed, password = store.sharePassword)
        private set

    private data class Spot(val key: String, val network: String, val own: Boolean)

    override suspend fun run() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                val address = properties.linkAddresses.firstOrNull { it.address.isPrivateV4() }

                if (address != null)
                    networks[network] = "${address.address.hostAddress}/${address.prefixLength}"
                else
                    networks.remove(network)

                wake.trySend(Unit)
            }

            override fun onLost(network: Network) {
                networks.remove(network)

                wake.trySend(Unit)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                tethered = intent?.getStringArrayListExtra(EXTRA_ACTIVE_TETHER).orEmpty()

                wake.trySend(Unit)
            }
        }

        // A Wi-Fi without internet is still a network to share on; the VPN itself is left out by the request's defaults.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // A failure here must not stop the VPN: without it the sharing only finds no network.
        val watching = runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onFailure { Log.w("Sharing: network callback failed", it) }
            .isSuccess

        // Sent by the system's tethering service, which is not always the system's own process; Android lets no app
        // send it (a protected broadcast), so the receiver may stay open to it. Sticky: the current state comes at once.
        ContextCompat.registerReceiver(service, receiver, IntentFilter(ACTION_TETHER_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)

        active = this

        try {
            while (true) {
                wake.receive()

                lock.withLock { check() }
            }
        } finally {
            withContext(NonCancellable) {
                active = null

                if (watching) runCatching { connectivity.unregisterNetworkCallback(callback) }
                service.unregisterReceiver(receiver)

                lock.withLock { shut(null) }
            }
        }
    }

    /** The switch on the card. */
    suspend fun turn(on: Boolean): ShareState = lock.withLock {
        if (!on || providerClosed) shut(null)
        else spots().firstOrNull()?.let(::start) ?: shut(ShareState.Stop.NoNetwork)

        state
    }

    /** The «Пароль» switch for a Wi-Fi that is not the phone's own (D-421), remembered. */
    suspend fun password(on: Boolean): ShareState = lock.withLock {
        store.sharePassword = on

        val spot = open

        // Opened again on the same port, so whoever came in without the password just turned on is let go.
        if (spot != null && !spot.own) start(spot) else state = state.copy(password = on)

        state
    }

    /** «Новый пароль» (D-464): reopened on the same port, so whoever came in with the old one is let go. */
    suspend fun renew(): ShareState = lock.withLock {
        store.shareSecret = ""

        open?.let(::start)

        state
    }

    /** Something changed: the network the port is open on, or the subscription's provider. */
    private fun check() {
        val spot = open

        when {
            spot == null -> state = base().copy(stopped = state.stopped)
            providerClosed -> shut(null)
            spot !in spots() -> {
                Log.i("Sharing: its network changed, off")

                shut(ShareState.Stop.NetworkChanged)
            }
        }
    }

    private fun start(spot: Spot) {
        val locked = !spot.own && store.sharePassword
        val secret = secret()
        val user = if (locked) LOGIN else ""
        val pass = if (locked) secret else ""

        // The port is kept, so the second device is set up once; taken by another app — another one.
        var port = Clash.startShare(spot.network, store.sharePort, user, pass)
        if (port <= 0 && store.sharePort != 0) port = Clash.startShare(spot.network, 0, user, pass)

        if (port <= 0) {
            open = null
            state = base().copy(stopped = ShareState.Stop.Failed)

            return
        }

        store.sharePort = port
        open = spot
        state = base().copy(
            on = true,
            address = spot.network.substringBefore('/'),
            port = port,
            own = spot.own,
            login = LOGIN,
            secret = secret,
        )

        Log.i("Sharing on ${state.address}:$port, own network: ${spot.own}, password: $locked")
    }

    private fun shut(stopped: ShareState.Stop?) {
        if (open != null) Clash.stopShare()

        open = null
        state = base().copy(stopped = stopped)
    }

    private fun base() = ShareState(running = true, closed = providerClosed, password = store.sharePassword)

    /** The phone's own networks first: they are what people share through, and they need no password. */
    private fun spots(): List<Spot> =
        tethered.mapNotNull { name -> ownAddress(name)?.let { Spot("tether:$name", it, own = true) } } +
            networks.map { (network, address) -> Spot("network:$network", address, own = false) }

    private fun ownAddress(name: String): String? =
        runCatching { NetworkInterface.getByName(name) }.getOrNull()
            ?.interfaceAddresses
            ?.firstOrNull { it.address.isPrivateV4() }
            ?.let { "${it.address.hostAddress}/${it.networkPrefixLength}" }

    /** Made once and kept until «Новый пароль»: no 0, o, 1, l or i to misread on a TV. */
    private fun secret(): String = store.shareSecret.ifEmpty {
        val random = SecureRandom()

        String(CharArray(SECRET_LENGTH) { SECRET_LETTERS[random.nextInt(SECRET_LETTERS.length)] })
            .also { store.shareSecret = it }
    }

    companion object {
        /** The running module, while the VPN is up; the binder reaches the sharing through it. */
        @Volatile
        var active: ShareModule? = null
            private set

        /** The active subscription's `ducker-local-proxy: off` (D-218), written at every load of its config. */
        @Volatile
        var providerClosed = false
            set(value) {
                field = value

                active?.wake?.trySend(Unit)
            }

        const val LOGIN = "ducker"

        private const val SECRET_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789"
        private const val SECRET_LENGTH = 8

        // TetheringManager's, a system API: the same strings since Android 2.2.
        private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        private const val EXTRA_ACTIVE_TETHER = "tetherArray"
    }
}

/** 10/8, 172.16/12, 192.168/16: a public address — some offices give one even on Wi-Fi — would open the port to all. */
private fun InetAddress.isPrivateV4() = this is Inet4Address && isSiteLocalAddress
