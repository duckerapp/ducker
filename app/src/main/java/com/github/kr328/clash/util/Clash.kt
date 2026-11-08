package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.service.TunService
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendBroadcastSelf

fun Context.startClashService(): Intent? {
    val vpnRequest = VpnService.prepare(this)
    if (vpnRequest != null)
        return vpnRequest

    startForegroundServiceCompat(TunService::class.intent)

    return null
}

fun Context.stopClashService() {
    // Asked off: no restore brings the tunnel back. With the service not up yet the request reaches nobody, and the
    // mark left by an update made the next start restore it seconds after a stop (F-423).
    ServiceStore(this).tunnelStartedAt = 0

    sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
}