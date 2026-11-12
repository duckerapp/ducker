package com.github.kr328.clash.service.store

import com.github.kr328.clash.service.util.ProviderInfo
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A template switch rebuilds without a download only from a provider's own config (configuration templates, T3). */
class LocalSourceTest {
    private fun profile(info: ProviderInfo, config: String = "proxies: []", source: String? = null) =
        Files.createTempDirectory("profile").toFile().apply {
            deleteOnExit()
            resolve("config.yaml").writeText(config)
            source?.let { resolve(SOURCE_FILE).writeText(it) }
            ProviderInfo.write(this, info)
        }

    @Test
    fun `the kept download serves both directions`() {
        val dir = profile(ProviderInfo(template = "ducker-ru"), source = "provider")

        assertEquals(dir.resolve(SOURCE_FILE), localSource(dir, ""))
        assertEquals(dir.resolve(SOURCE_FILE), localSource(dir, "ducker-ru"))
    }

    @Test
    fun `the provider's own config serves a switch into a template`() {
        val dir = profile(ProviderInfo())

        assertEquals(dir.resolve("config.yaml"), localSource(dir, "ducker-ru"))
        assertNull(localSource(dir, ""))
    }

    @Test
    fun `a generated config is never taken for a source`() {
        assertNull(localSource(profile(ProviderInfo(usedTemplate = true)), "ducker-ru"))
        assertNull(localSource(profile(ProviderInfo(template = "ducker-ru")), ""))
        assertNull(localSource(profile(ProviderInfo(template = "ducker-ru")), "ducker-ru"))
        assertNull(localSource(profile(ProviderInfo(), config = ""), "ducker-ru"))
    }

    @Test
    fun `the provider's lock holds only over its own app list`() {
        assertEquals(true, ProviderInfo(overrideUser = true).appsLocked)
        assertEquals(false, ProviderInfo(overrideUser = true, template = "ducker-ru").appsLocked)
    }
}
