package com.github.kr328.clash.service.clash.module

import android.content.Intent
import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.util.PackageFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An app installed while the tunnel is up (D-454): what counts as an install, what is joined, what rebuilds. */
class AppInstallModuleTest {
    private val bank = "ru.bank"
    private val gov = "ru.gov"
    private val game = "com.game"

    private val excluded = PackageFilter.decide(
        AccessControlMode.AcceptAll, emptySet(), ProfileNetwork(excludePackage = listOf(bank, gov))
    )

    @Test
    fun `a new package counts, an update and a removal do not`() {
        assertEquals(bank, installedPackage(Intent.ACTION_PACKAGE_ADDED, replacing = false, bank))
        assertNull(installedPackage(Intent.ACTION_PACKAGE_ADDED, replacing = true, bank))
        assertNull(installedPackage(Intent.ACTION_PACKAGE_REMOVED, replacing = false, bank))
        assertNull(installedPackage(Intent.ACTION_PACKAGE_ADDED, replacing = false, null))
    }

    @Test
    fun `installs in a row are joined, a later one is the next batch`() = runBlocking {
        val heard = Channel<String?>(Channel.UNLIMITED)

        // An update comes first: it opens no window by itself.
        heard.trySend(null)
        heard.trySend(bank)
        heard.trySend(gov)
        launch {
            delay(300)
            heard.send(game)
        }

        assertEquals(setOf(bank, gov), heard.joined(100) { it })
        assertEquals(setOf(game), heard.joined(100) { it })
    }

    @Test
    fun `only a name the filter lists rebuilds the interface`() {
        assertEquals(listOf(bank), setOf(bank).namedBy(excluded))
        assertEquals(emptyList(), setOf(game).namedBy(excluded))
        // Two at once, one of them named: one rebuild, for that one.
        assertEquals(listOf(gov), setOf(game, gov).namedBy(excluded))
    }

    @Test
    fun `a person's own choice counts as the provider's list does`() {
        val without = PackageFilter.decide(AccessControlMode.DenySelected, setOf(game), ProfileNetwork())
        val only = PackageFilter.decide(AccessControlMode.AcceptSelected, setOf(game), ProfileNetwork())

        assertEquals(listOf(game), setOf(game, bank).namedBy(without))
        // «Только выбранные»: the interface lets in what it was built with, a chosen app installed later is left out.
        assertEquals(listOf(game), setOf(game, bank).namedBy(only))
    }
}
