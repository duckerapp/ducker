package com.github.kr328.clash.service

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * The settings exchange between two real processes (D-263). The tests run in the main process of
 * the test package and stand for the UI; the provider lives in the `:background` process of the
 * same package, as in the app.
 *
 * Needs a device, so it is run by hand, on one named emulator:
 *
 *     ./gradlew :service:assembleMetaDebugAndroidTest -Pabi=x86_64
 *     adb -s emulator-5556 install -r service/build/outputs/apk/androidTest/meta/debug/service-meta-debug-androidTest.apk
 *     adb -s emulator-5556 shell am instrument -w com.github.kr328.clash.service.test/androidx.test.runner.AndroidJUnitRunner
 */
class SettingsExchangeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.dataDir, "shared_prefs/service.xml")

    @Before
    fun emptyFile() {
        Global.init(context.applicationContext as Application)

        killOwner()

        file.delete()
        File(file.path + ".bak").delete()
    }

    @Test
    fun readsTheFileOfAnOlderBuild() {
        file.parentFile!!.mkdirs()
        file.writeText(
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="active_profile">7b3f6f0e-2c1a-4b63-9d0c-3f1f0f6a9a11</string>
                <string name="access_control_mode">DenySelected</string>
                <set name="access_control_packages">
                    <string>com.android.chrome</string>
                    <string>org.telegram.messenger</string>
                </set>
                <boolean name="allow_ipv6" value="true" />
                <boolean name="send_hwid" value="false" />
                <long name="tunnel_started_at" value="1760000000000" />
                <string name="tun_stack_mode">gvisor</string>
            </map>
            """.trimIndent()
        )

        val store = ServiceStore(context)

        assertEquals(UUID.fromString("7b3f6f0e-2c1a-4b63-9d0c-3f1f0f6a9a11"), store.activeProfile)
        assertEquals(AccessControlMode.DenySelected, store.accessControlMode)
        assertEquals(setOf("com.android.chrome", "org.telegram.messenger"), store.accessControlPackages)
        assertEquals(true, store.allowIpv6)
        assertEquals(false, store.sendHwid)
        assertEquals(1760000000000L, store.tunnelStartedAt)
        assertEquals("gvisor", store.tunStackMode)

        // Not in the file: the defaults.
        assertEquals(true, store.bypassPrivateNetwork)
        assertEquals("", store.stopReason)
    }

    @Test
    fun keepsEveryTypeAndTellsAStoredValueFromAMissingOne() {
        val store = PreferenceProvider.createStoreProvider(context)

        assertEquals(7, store.getInt("int", 7))
        assertEquals(7L, store.getLong("long", 7))
        assertEquals("none", store.getString("string", "none"))
        assertEquals(setOf("none"), store.getStringSet("set", setOf("none")))
        assertEquals(true, store.getBoolean("boolean", true))

        // Values that look like "nothing" are values all the same.
        store.setInt("int", 0)
        store.setLong("long", 0)
        store.setString("string", "")
        store.setStringSet("set", emptySet())
        store.setBoolean("boolean", false)

        assertEquals(0, store.getInt("int", 7))
        assertEquals(0L, store.getLong("long", 7))
        assertEquals("", store.getString("string", "none"))
        assertEquals(emptySet<String>(), store.getStringSet("set", setOf("none")))
        assertEquals(false, store.getBoolean("boolean", true))

        store.setInt("int", Int.MIN_VALUE)
        store.setLong("long", Long.MAX_VALUE)
        store.setString("string", "утка 🦆 <&>\n")
        store.setStringSet("set", setOf("a", "б", ""))
        store.setBoolean("boolean", true)

        assertEquals(Int.MIN_VALUE, store.getInt("int", 7))
        assertEquals(Long.MAX_VALUE, store.getLong("long", 7))
        assertEquals("утка 🦆 <&>\n", store.getString("string", "none"))
        assertEquals(setOf("a", "б", ""), store.getStringSet("set", setOf("none")))
        assertEquals(true, store.getBoolean("boolean", false))
    }

    @Test
    fun readsAndWritesSurviveTheDeathOfTheOwnerProcess() {
        val store = PreferenceProvider.createStoreProvider(context)
        val apps = (1..300).map { "com.example.app$it" }.toSet()

        store.setStringSet("apps", apps)
        store.setBoolean("flag", true)

        // The provider writes with apply(), which reaches the disk a moment later.
        awaitOnDisk("com.example.app300")
        awaitOnDisk("name=\"flag\" value=\"true\"")

        repeat(20) { round ->
            // Straight after the kill this process still holds the connection to the dead one.
            assertTrue("The owner process was not running", killOwner())

            assertEquals(apps, store.getStringSet("apps", emptySet()))
            assertEquals(true, store.getBoolean("flag", false))

            assertTrue("The owner process was not running", killOwner())

            store.setLong("round", round.toLong())

            assertEquals(round.toLong(), store.getLong("round", -1))
            awaitOnDisk("name=\"round\" value=\"$round\"")
        }
    }

    @Test
    fun aValueOfAnotherTypeIsAnErrorAndNotTheDefault() {
        val store = PreferenceProvider.createStoreProvider(context)

        store.setString("key", "text")

        assertThrows(Exception::class.java) { store.getInt("key", 7) }

        // The provider is still there for the next caller.
        assertEquals("text", store.getString("key", "none"))
    }

    private fun killOwner(): Boolean {
        val owners = context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
            .filter { it.processName.endsWith(":background") }

        owners.forEach { Process.killProcess(it.pid) }

        return owners.isNotEmpty()
    }

    private fun awaitOnDisk(text: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000

        // The file is replaced while it is written: a failed read is a reason to look again.
        while (runCatching { text in file.readText() }.getOrDefault(false).not()) {
            assertTrue("$text did not reach ${file.name}", SystemClock.elapsedRealtime() < deadline)

            Thread.sleep(20)
        }
    }
}
