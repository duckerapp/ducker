package com.github.kr328.clash.service.clash.module

import android.net.ConnectivityManager
import android.net.VpnService
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.parseInetSocketAddress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.SecureRandom

class TunModule(private val vpn: VpnService) : Module<Unit>(vpn) {
    data class TunDevice(
        val fd: Int,
        var stack: String,
        val congestion: String,
        val gateway: String,
        val portal: String,
        val dns: String,
    )

    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val close = Channel<Unit>(Channel.CONFLATED)

    private fun queryUid(
        protocol: Int,
        source: InetSocketAddress,
        target: InetSocketAddress,
    ): Int {
        return runCatching { connectivity.getConnectionOwnerUid(protocol, source, target) }
            .getOrElse { -1 }
    }

    override suspend fun run() {
        try {
            return close.receive()
        } finally {
            withContext(NonCancellable) {
                requestStop()
            }
        }
    }

    fun listenHttp(): InetSocketAddress? {
        val r = { 1 + random.nextInt(199) }
        val listenAt = "127.${r()}.${r()}.${r()}:0"
        val address = Clash.startHttp(listenAt)

        return address?.let(::parseInetSocketAddress)
    }

    fun attach(device: TunDevice) {
        // Ядро не собрало туннель — служба останавливается с этой причиной, а не числится
        // включённой без туннеля (D-226). Подробности ядро пишет в журнал строкой «TUN:».
        check(
            Clash.startTun(
                fd = device.fd,
                stack = device.stack,
                congestion = device.congestion,
                gateway = device.gateway,
                portal = device.portal,
                dns = device.dns,
                markSocket = vpn::protect,
                querySocketUid = this::queryUid
            )
        ) { START_FAILED }
    }

    suspend fun close() {
        close.send(Unit)
    }

    companion object {
        /** Причина остановки службы; по ней главная объясняет, почему VPN не включился. */
        const val START_FAILED = "Tunnel start failed"

        private val random = SecureRandom()

        fun requestStop() {
            Clash.stopHttp()
            Clash.stopTun()
        }
    }
}