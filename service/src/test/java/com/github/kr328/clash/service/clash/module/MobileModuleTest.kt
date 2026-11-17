package com.github.kr328.clash.service.clash.module

import com.github.kr328.clash.service.model.NetworkKind
import com.github.kr328.clash.service.util.Failure
import kotlinx.coroutines.runBlocking
import java.util.UUID
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

    @Test
    fun `a silent list is checked again after 30 s, then twice as long, at most every 5 min`() {
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 300_000L, 300_000L), (0..5).map(::recheckIn))
        assertEquals(RECHECK_MAX_MS, recheckIn(Int.MAX_VALUE))
    }

    @Test
    fun `the node in use is checked every minute on screen, every five in the pocket, a miss confirmed in seconds`() {
        assertEquals(60_000L, watchIn(0, screenOn = true))
        assertEquals(300_000L, watchIn(0, screenOn = false))
        assertEquals(10_000L, watchIn(1, screenOn = true))
        assertEquals(10_000L, watchIn(1, screenOn = false))
        // Two misses in a row move on, one does not: a single lost check on a noisy mobile network is not a dead node.
        assertEquals(2, WATCH_MISSES)
    }

    private val s = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val t = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val lte = listOf("LTE-1", "LTE-2")
    private val blank = MobileSetup(false, null, emptyList(), false)
    private val preset = MobileSetup(true, s, lte, true)

    @Test
    fun `a preset sets up only a page nobody set up, and never takes another subscription's place`() {
        assertEquals(preset, presetOutcome(blank, s, lte, override = false, free = true, setUp = false))
        // The person chose «Не выбрана» themselves.
        assertNull(presetOutcome(blank, s, lte, override = false, free = true, setUp = true))
        // A second subscription with a preset after the first.
        assertNull(presetOutcome(preset, t, listOf("X"), override = false, free = false, setUp = false))
        // The override brings the provider's own settings back, not over another subscription the person chose.
        assertNull(presetOutcome(MobileSetup(true, t, emptyList(), false), s, lte, override = true, free = false, setUp = true))
        assertEquals(preset, presetOutcome(blank, s, lte, override = true, free = true, setUp = true))
        assertEquals(preset, presetOutcome(MobileSetup(false, s, listOf("NL-1"), false), s, lte, override = true, free = false, setUp = true))
        assertNull(presetOutcome(blank, s, emptyList(), override = true, free = true, setUp = false))
    }

    @Test
    fun `with the provider's list on, updates bring its list, its withdrawal turns switching off and its return on`() {
        assertEquals(preset.copy(nodes = listOf("LTE-3")), presetOutcome(preset, s, listOf("LTE-3"), override = false, free = false, setUp = true))
        assertNull(presetOutcome(preset, s, lte, override = false, free = false, setUp = true))
        // Switching turned off for a while stays off through an update.
        assertNull(presetOutcome(preset.copy(on = false), s, lte, override = false, free = false, setUp = true))
        val withdrawn = presetOutcome(preset, s, emptyList(), override = false, free = false, setUp = true)
        assertEquals(MobileSetup(false, s, emptyList(), true), withdrawn)
        assertNull(presetOutcome(withdrawn!!.copy(on = true), s, emptyList(), override = false, free = false, setUp = true))
        assertEquals(preset, presetOutcome(withdrawn, s, lte, override = false, free = false, setUp = true))
        // Edited by the person: updates leave the list alone.
        assertNull(presetOutcome(preset.copy(nodes = listOf("LTE-2"), provider = false), s, lte, override = false, free = false, setUp = true))
    }
}
