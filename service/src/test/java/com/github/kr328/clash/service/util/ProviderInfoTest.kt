package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Прочерк как «не задано» (D-68): пустое значение заголовка панель сохранить не даёт,
 * и убрать свой заголовок провайдер может только так.
 */
class ProviderInfoTest {
    @Test
    fun `прочерк значит не задано`() {
        val info = ProviderInfo.of(
            mapOf(
                "profile-title" to "-",
                "ducker-brand-name" to " - ",
                "ducker-brand-logo" to "-",
                "ducker-buy-url" to "-",
                "support-url" to "-",
                "ducker-override-user" to "off",
            )
        )

        assertEquals("", info.profileTitle)
        assertEquals("", info.brandName)
        assertEquals("", info.brandLogo)
        assertEquals("", info.buyUrl)
        assertEquals("", info.supportUrl)
        assertFalse(info.overrideUser)
    }

    @Test
    fun `обычные значения не трогаем`() {
        val info = ProviderInfo.of(
            mapOf(
                "profile-title" to "Утка в инвизе",
                "support-url" to "https://t.me/support",
                // Прочерк внутри строки — часть текста, а не признак пустоты.
                "ducker-brand-name" to "Duck-VPN",
            )
        )

        assertEquals("Утка в инвизе", info.profileTitle)
        assertEquals("https://t.me/support", info.supportUrl)
        assertEquals("Duck-VPN", info.brandName)
    }

    @Test
    fun `переключатель Global прячется только по off`() {
        assertTrue(ProviderInfo.of(mapOf("Ducker-Global" to " Off ")).globalHidden)
        assertFalse(ProviderInfo.of(mapOf("ducker-global" to "on")).globalHidden)
        assertFalse(ProviderInfo.of(emptyMap()).globalHidden)
    }

    @Test
    fun `локальные порты закрываются только по off`() {
        assertTrue(ProviderInfo.of(mapOf("Ducker-Local-Proxy" to " Off ")).localProxyOff)
        assertFalse(ProviderInfo.of(mapOf("ducker-local-proxy" to "on")).localProxyOff)
        assertFalse(ProviderInfo.of(emptyMap()).localProxyOff)
    }
}
