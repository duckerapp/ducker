package com.github.kr328.clash.service

import com.github.kr328.clash.service.TunGuard.Companion.leftDownUnnoticed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a start of the service process tells the person the VPN went off: after two crashes in a row Android
 * dropped the guard and nothing said so (verified on Android 14).
 */
class TunGuardTest {
    private val boot = 1_000_000L
    private val update = 2_000_000L

    @Test
    fun `a tunnel that died after the last boot and update is told`() {
        assertTrue(leftDownUnnoticed(startedAt = 3_000_000L, bootedAt = boot, updatedAt = update, running = false))
    }

    @Test
    fun `a running tunnel or none at all is not`() {
        assertFalse(leftDownUnnoticed(startedAt = 3_000_000L, bootedAt = boot, updatedAt = update, running = true))
        assertFalse(leftDownUnnoticed(startedAt = 0L, bootedAt = boot, updatedAt = update, running = false))
    }

    @Test
    fun `a tunnel cut by a reboot or an update is left to their receivers`() {
        assertFalse(leftDownUnnoticed(startedAt = 500_000L, bootedAt = boot, updatedAt = 0L, running = false))
        assertFalse(leftDownUnnoticed(startedAt = 1_500_000L, bootedAt = boot, updatedAt = update, running = false))
    }
}
