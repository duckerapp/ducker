package com.github.kr328.clash.service

import com.github.kr328.clash.core.model.ProfileNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The interface is rebuilt after a load only when the subscription's `tun` values differ (audit 06.11.2026, item 4). */
class TunValuesTest {
    private val built = ProfileNetwork(excludePackage = listOf("ru.bank"), stack = "system").tunValues()

    @Test
    fun `the same values under another mode keep the interface`() {
        assertEquals(built, ProfileNetwork(excludePackage = listOf("ru.bank"), stack = "system", mode = "global").tunValues())
    }

    @Test
    fun `every value the interface is built from rebuilds it`() {
        val base = ProfileNetwork(excludePackage = listOf("ru.bank"), stack = "system")

        listOf(
            base.copy(excludePackage = emptyList()),
            base.copy(includePackage = listOf("org.telegram.messenger")),
            base.copy(routeExcludeAddress = listOf("10.0.0.0/8")),
            base.copy(stack = "gvisor"),
            base.copy(congestionController = "bbr"),
            base.copy(dnsDisabled = true),
        ).forEach { assertNotEquals(built, it.tunValues(), "$it") }
    }
}
