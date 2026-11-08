package com.github.kr328.clash.service

import com.github.kr328.clash.service.TunGuard.Companion.crashedSince
import com.github.kr328.clash.service.TunGuard.Companion.leftDownUnnoticed
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a start of the service process tells the person the VPN went off: after two crashes in a row Android
 * dropped the guard and nothing said so (verified on Android 14).
 */
class TunGuardTest {
    @get:Rule val folder = TemporaryFolder()

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

    /** A file of the files directory, last written at [time]. */
    private fun written(path: String, time: Long) {
        val file = File(folder.root, path)
        file.parentFile.mkdirs()
        file.writeText("synthetic record\n")
        assertTrue(file.setLastModified(time))
    }

    @Test
    fun `a death with no record is the system's`() {
        assertFalse(crashedSince(folder.root, startedAt = 3_000_000L))
    }

    @Test
    fun `records older than the tunnel belong to an earlier death`() {
        written("clash/stderr.log", 2_000_000L)
        written("crashes/background/flight.log", 2_000_000L)
        written("crashes/background/flight.1.log", 1_000_000L)

        assertFalse(crashedSince(folder.root, startedAt = 3_000_000L))
    }

    @Test
    fun `a newer panic of the core is our crash`() {
        written("clash/stderr.log", 4_000_000L)

        assertTrue(crashedSince(folder.root, startedAt = 3_000_000L))
    }

    @Test
    fun `a newer crash of the tunnel's process is ours, of another process not`() {
        written("crashes/main/flight.log", 4_000_000L)
        written("crashes/dashboard/flight.log", 4_000_000L)

        assertFalse(crashedSince(folder.root, startedAt = 3_000_000L))

        written("crashes/background/flight.1.log", 4_000_000L)

        assertTrue(crashedSince(folder.root, startedAt = 3_000_000L))
    }
}
