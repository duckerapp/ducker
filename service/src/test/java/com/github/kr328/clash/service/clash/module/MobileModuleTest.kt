package com.github.kr328.clash.service.clash.module

import com.github.kr328.clash.service.model.NetworkKind
import com.github.kr328.clash.service.util.Failure
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Мобильная сеть» (D-423, D-424): which side a network is on, what a settled change does, how long it waits. */
class MobileModuleTest {
    @Test
    fun `a SIM and any metered network are the mobile side, an unmetered Wi-Fi or Ethernet is Wi-Fi`() {
        assertEquals(NetworkKind.Mobile, networkKind(cellular = true, metered = true))
        // Some carriers' unlimited 5G is unmetered, still the SIM.
        assertEquals(NetworkKind.Mobile, networkKind(cellular = true, metered = false))
        // Another phone's hotspot, a dacha's 4G router marked by hand (Q-116).
        assertEquals(NetworkKind.Mobile, networkKind(cellular = false, metered = true))
        assertEquals(NetworkKind.Wifi, networkKind(cellular = false, metered = false))
    }

    @Test
    fun `the mobile side switches only when set up, Wi-Fi brings back only what was saved`() {
        assertEquals(MobileAction.Apply, mobileAction(NetworkKind.Mobile, configured = true, back = false))
        assertEquals(MobileAction.Apply, mobileAction(NetworkKind.Mobile, configured = true, back = true))
        assertEquals(MobileAction.Nothing, mobileAction(NetworkKind.Mobile, configured = false, back = true))
        assertEquals(MobileAction.Restore, mobileAction(NetworkKind.Wifi, configured = false, back = true))
        assertEquals(MobileAction.Nothing, mobileAction(NetworkKind.Wifi, configured = true, back = false))
        assertEquals(MobileAction.Nothing, mobileAction(NetworkKind.None, configured = true, back = true))
    }

    @Test
    fun `a minute in the pocket, seconds with the screen on, at once once held`() {
        assertEquals(POCKET_HOLD_MS, holdLeft(0, screenOn = false))
        assertEquals(POCKET_HOLD_MS - 20_000, holdLeft(20_000, screenOn = false))
        // The phone out of the pocket after 20 s of the new network: no more waiting.
        assertEquals(0, holdLeft(20_000, screenOn = true))
        assertEquals(SCREEN_ON_HOLD_MS - 3_000, holdLeft(3_000, screenOn = true))
        assertEquals(0, holdLeft(POCKET_HOLD_MS + 1, screenOn = false))
    }

    @Test
    fun `a failed switch waits for a screen-on or a change, then is tried again at once`() {
        val held = POCKET_HOLD_MS * 10

        // Long held, screen on: switches now…
        assertEquals(0, mobileWait(NetworkKind.Mobile, NetworkKind.Wifi, false, false, held, screenOn = true))
        // …but not again right after it failed: that was a loop of reloads.
        assertEquals(UNTIL_WAKE, mobileWait(NetworkKind.Mobile, NetworkKind.Wifi, false, failed = true, held, screenOn = true))
        assertEquals(UNTIL_WAKE, mobileWait(NetworkKind.Wifi, NetworkKind.Mobile, true, failed = true, 0, screenOn = true))
        // The wake clears `failed`: the next pass switches without a new hold.
        assertEquals(0, mobileWait(NetworkKind.Mobile, NetworkKind.Wifi, false, false, held, screenOn = true))
        // A failed settings change leaves the kind unsettled (None), so the mobile side is tried again after the wake.
        assertEquals(0, mobileWait(NetworkKind.Mobile, NetworkKind.None, false, false, held, screenOn = false))
    }

    @Test
    fun `the loop rests on a settled kind or no network, the start does not wait, a fresh change holds`() {
        assertEquals(UNTIL_WAKE, mobileWait(NetworkKind.Mobile, NetworkKind.Mobile, true, false, 0, screenOn = true))
        assertEquals(UNTIL_WAKE, mobileWait(NetworkKind.None, NetworkKind.Wifi, true, false, 0, screenOn = true))
        assertEquals(0, mobileWait(NetworkKind.Mobile, NetworkKind.Wifi, starting = true, false, 0, screenOn = false))
        assertEquals(POCKET_HOLD_MS - 1_000, mobileWait(NetworkKind.Mobile, NetworkKind.Wifi, false, false, 1_000, false))
    }

    @Test
    fun `the switch waits for the first config, and goes on without it after the limit`() = runBlocking {
        var polls = 0

        assertTrue(awaitReady(timeoutMs = 5_000, pollMs = 5) { ++polls >= 4 })
        assertEquals(4, polls)

        val started = System.nanoTime()

        assertFalse(awaitReady(timeoutMs = 100, pollMs = 5) { false })
        assertTrue(System.nanoTime() - started >= 100_000_000)
    }

    @Test
    fun `a refused load ends the wait at once, a failed download or another subscription does not`() {
        val target = "a"

        assertEquals(true, loadOutcome(target, target, loaded = true, reason = null))
        assertEquals(false, loadOutcome(target, target, loaded = false, reason = Failure.RELOAD + "bad yaml"))
        assertNull(loadOutcome(target, target, loaded = false, reason = "DOWNLOAD_FAILED: timeout"))
        assertNull(loadOutcome(target, "b", loaded = true, reason = null))
        assertNull(loadOutcome(target, "b", loaded = false, reason = Failure.RELOAD + "bad yaml"))
        assertNull(loadOutcome(target, null, loaded = true, reason = null))
    }
}
