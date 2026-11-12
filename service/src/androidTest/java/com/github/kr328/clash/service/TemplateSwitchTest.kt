package com.github.kr328.clash.service

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.ConfigTemplateStore
import com.github.kr328.clash.service.store.SOURCE_FILE
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URL
import java.util.UUID

/**
 * Configuration templates through the service's update path: [ProfileProcessor] in the test package, against the
 * fixture server on the host. Run by hand on one named emulator, with the server serving a copy of the fixtures
 * whose `providers-mixed.yaml` names `10.0.2.2` instead of `127.0.0.1`:
 *
 *     python3 -I <copy>/serve.py
 *     ./gradlew :service:assembleMetaDebugAndroidTest -Pabi=x86_64
 *     adb -s emulator-5556 install -r service/build/outputs/apk/androidTest/meta/debug/service-meta-debug-androidTest.apk
 *     adb -s emulator-5556 shell am instrument -w -e class com.github.kr328.clash.service.TemplateSwitchTest \
 *         com.github.kr328.clash.service.test/androidx.test.runner.AndroidJUnitRunner
 */
class TemplateSwitchTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = InstrumentationRegistry.getArguments().getString("base") ?: "http://10.0.2.2:18766"
    private val requested by lazy { ConfigTemplateStore(context) }

    @Before
    fun init() {
        Global.init(context.applicationContext as Application)
    }

    private fun dir(uuid: UUID) = context.importedDir.resolve(uuid.toString())
    private fun info(uuid: UUID) = ProviderInfo.read(dir(uuid))
    private fun config(uuid: UUID) = dir(uuid).resolve("config.yaml").readBytes()
    private fun served(file: String) = URL("$base/sub/$file").readBytes()

    private suspend fun add(file: String, template: String = ""): UUID {
        val uuid = ProfileManager(context).create(Profile.Type.Url, file, "$base/sub/$file")

        if (template.isNotEmpty()) ProfileProcessor.setTemplate(context, uuid, template, null)
        ProfileProcessor.apply(context, uuid)

        return uuid
    }

    private suspend fun relink(uuid: UUID, source: String) {
        ImportedDao().update(ImportedDao().queryByUUID(uuid)!!.copy(source = source))
    }

    private suspend fun refused(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: Exception) {
            return e.message.orEmpty()
        }

        fail("expected a refusal")
        error("unreachable")
    }

    private fun assertTemplate(uuid: UUID, source: ByteArray) {
        assertEquals("ducker-ru", info(uuid).template)
        assertEquals("ducker-ru", requested.read(uuid))
        assertArrayEquals(source, dir(uuid).resolve(SOURCE_FILE).readBytes())
        assertTrue(String(config(uuid)).contains("Основной профиль"))
    }

    private fun assertOwn(uuid: UUID, config: ByteArray) {
        assertEquals("", info(uuid).template)
        assertEquals("", requested.read(uuid))
        assertFalse(dir(uuid).resolve(SOURCE_FILE).exists())
        assertArrayEquals(config, config(uuid))
    }

    /** S1: added in template mode; an update keeps the choice; `source.yaml` is the provider's bytes. */
    @Test
    fun addedInTemplateMode(): Unit = runBlocking {
        val uuid = add("full-proxies.yaml", "ducker-ru")
        val source = served("full-proxies.yaml")

        assertTemplate(uuid, source)
        // yaml.v3 writes the flag escaped (`"\U0001F1E9\U0001F1EA Alpha"`), which the core reads back as it was.
        assertTrue(String(config(uuid)).contains("Alpha"))
        assertFalse(String(config(uuid)).contains("Provider choice"))

        ProfileProcessor.update(context, uuid, null)

        assertTemplate(uuid, source)
        assertEquals("", info(uuid).updateFailure)

        ProfileProcessor.delete(context, uuid)
    }

    /** S2: both ways from what is kept, with the link dead: no download. */
    @Test
    fun switchesBothWaysWithoutDownload(): Unit = runBlocking {
        val uuid = add("full-proxies.yaml")
        val source = served("full-proxies.yaml")

        assertOwn(uuid, source)

        relink(uuid, "$base/fault/500")

        ProfileProcessor.setTemplate(context, uuid, "ducker-ru", null)
        assertTemplate(uuid, source)

        ProfileProcessor.setTemplate(context, uuid, "", null)
        assertOwn(uuid, source)

        ProfileProcessor.setTemplate(context, uuid, "ducker-ru", null)
        assertTemplate(uuid, source)

        ProfileProcessor.delete(context, uuid)
    }

    /** S3: a config that is our own fallback is no source: the switch downloads, and a failed one changes nothing. */
    @Test
    fun withoutAKeptSourceTheSwitchDownloads(): Unit = runBlocking {
        val uuid = add("links.txt")
        val before = config(uuid)

        assertTrue(info(uuid).usedTemplate)

        relink(uuid, "$base/fault/500")
        refused { ProfileProcessor.setTemplate(context, uuid, "ducker-ru", null) }

        assertEquals("", info(uuid).template)
        assertTrue(info(uuid).usedTemplate)
        assertEquals("", requested.read(uuid))
        assertArrayEquals(before, config(uuid))

        relink(uuid, "$base/sub/links.txt")
        ProfileProcessor.setTemplate(context, uuid, "ducker-ru", null)

        assertTemplate(uuid, served("links.txt"))
        assertFalse(info(uuid).usedTemplate)

        ProfileProcessor.delete(context, uuid)
    }

    /** S4: a refused preparation, on a switch or on an update, keeps the working profile and the choice. */
    @Test
    fun refusalsKeepTheProfile(): Unit = runBlocking {
        val own = add("collision.yaml")
        val ownConfig = config(own)

        val reason = refused { ProfileProcessor.setTemplate(context, own, "ducker-ru", null) }

        assertTrue(reason, reason.contains("Основной профиль"))
        assertOwn(own, ownConfig)

        val templated = add("full-proxies.yaml", "ducker-ru")
        val config = config(templated)

        for (fault in listOf("sub/collision.yaml", "fault/500", "fault/empty", "fault/garbage")) {
            relink(templated, "$base/$fault")
            refused { ProfileProcessor.update(context, templated, null) }

            assertArrayEquals(fault, config, config(templated))
            assertTemplate(templated, served("full-proxies.yaml"))
        }

        refused { ProfileProcessor.setTemplate(context, templated, "no-such-template", null) }
        assertEquals("ducker-ru", requested.read(templated))

        ProfileProcessor.delete(context, own)
        ProfileProcessor.delete(context, templated)
    }

    /** S9 and C2 on a device: an HTTP provider fetched with its own header; static, inline and provider nodes. */
    @Test
    fun providerNodesComeWithTheirOwnHeader(): Unit = runBlocking {
        val uuid = add("providers-mixed.yaml", "ducker-ru")
        val text = String(config(uuid))

        assertTemplate(uuid, served("providers-mixed.yaml"))
        assertTrue(text.contains("Static") && text.contains("inline-src") && text.contains("http-src"))
        // The server answers 401 without the provider's header, and a failed provider download is skipped.
        // The core keeps provider files under `providers/` (`config/process.go`).
        assertTrue(dir(uuid).resolve("providers/proxy_providers/http-src.yaml").readText().contains("keep A1"))

        ProfileProcessor.delete(context, uuid)
    }

    /** S8: a delete during a switch, and a subscription given up before its first download, leave no choice. */
    @Test
    fun deletedSubscriptionsLeaveNoChoice(): Unit = runBlocking {
        val uuid = add("full-proxies.yaml")
        val switch = async { runCatching { ProfileProcessor.setTemplate(context, uuid, "ducker-ru", null) } }

        delay(300)
        ProfileProcessor.delete(context, uuid)
        switch.await()

        assertFalse(ImportedDao().exists(uuid))
        assertFalse(dir(uuid).exists())
        assertEquals("", requested.read(uuid))

        val pending = ProfileManager(context).create(Profile.Type.Url, "pending", "$base/sub/full-proxies.yaml")

        ProfileProcessor.setTemplate(context, pending, "ducker-ru", null)
        assertEquals("ducker-ru", requested.read(pending))

        ProfileProcessor.release(context, pending)
        assertEquals("", requested.read(pending))

        val file = ProfileManager(context).create(Profile.Type.File, "file")

        refused { ProfileProcessor.setTemplate(context, file, "ducker-ru", null) }
        ProfileProcessor.release(context, file)
    }
}
