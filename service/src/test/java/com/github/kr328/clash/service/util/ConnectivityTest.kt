package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectivityTest {
    @Test
    fun `Private DNS shows the provider's domain, not the person's id`() {
        assertEquals("…nextdns.io", dnsProviderOf("abc123.dns.nextdns.io"))
        assertEquals("…cloudflare-dns.com", dnsProviderOf("1dot1dot1dot1.cloudflare-dns.com"))
        assertEquals("dns.google", dnsProviderOf("dns.google"))
        assertEquals("…adguard-dns.com", dnsProviderOf("d1a2b3.d.adguard-dns.com."))
    }
}
