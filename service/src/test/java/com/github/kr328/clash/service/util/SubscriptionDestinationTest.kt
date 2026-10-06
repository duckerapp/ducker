package com.github.kr328.clash.service.util

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionDestinationTest {
    // Списка хостингов нет (D-192): подписка на любом домене по https получает данные устройства.
    @Test fun anyHttpsHostReceivesDeviceData() {
        for (url in listOf("https://provider.invalid/sub", "https://raw.githubusercontent.com/sub", "https://test.pages.dev/sub", "https://user@provider.invalid/sub")) {
            assertTrue(deviceHeadersAllowed(url), url)
        }
    }
    @Test fun requiresHttpsBeforeCollectingDeviceData() {
        assertTrue(deviceHeadersAllowed("HTTPS://provider.invalid/sub"))
        for (url in listOf("http://provider.invalid/sub", "content://files/sub", "https:///sub", "https://", "https://good.invalid\\@bad.invalid/sub")) {
            assertFalse(deviceHeadersAllowed(url))
        }
    }
}
