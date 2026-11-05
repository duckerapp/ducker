package com.github.kr328.clash.service.clash.module

import com.github.kr328.clash.service.model.NetworkKind
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
