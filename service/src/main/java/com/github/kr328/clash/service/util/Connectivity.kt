package com.github.kr328.clash.service.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.provider.Settings

fun ConnectivityManager.resolveDns(network: Network?): List<String> {
    val properties = getLinkProperties(network) ?: return listOf()
    return properties.dnsServers.map { it.asSocketAddressText(53) }
}

/**
 * Android's Private DNS for the log (D-528): with a provider's hostname set, a blocked DoT leaves every app without DNS
 * while the tunnel and its nodes look fine. The mode is a hidden setting, so it may be unreadable; the state comes from
 * [properties] of the network. Of the hostname only the provider's domain shows: services like NextDNS put the
 * person's own id in front of it.
 */
fun privateDnsOf(context: Context, properties: LinkProperties?): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "unsupported"
    val mode = runCatching { Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "default" }
        .getOrDefault("unreadable")
    val server = properties?.privateDnsServerName
    val state = when {
        server != null -> "strict ${dnsProviderOf(server)}"
        properties?.isPrivateDnsActive == true -> "active"
        else -> "inactive"
    }
    return "$mode, $state"
}

/** The last two labels: `abc123.dns.nextdns.io` → `…nextdns.io`. */
fun dnsProviderOf(host: String): String {
    val domain = host.trimEnd('.').split('.').takeLast(2).joinToString(".")
    return if (domain == host.trimEnd('.')) domain else "…$domain"
}
