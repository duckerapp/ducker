package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.model.AccessControlMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Главное, что здесь проверяется: галочка человека не отменяет список провайдера.
 * Если это сломается, у людей при включённом VPN отвалятся банк и госуслуги, а причину
 * они не найдут — в интерфейсе-то галочка снята.
 */
class PackageFilterTest {
    private val bank = "ru.bank"
    private val gov = "ru.gov"
    private val browser = "org.mozilla.firefox"

    private val profile = ProfileNetwork(excludePackage = listOf(bank, gov))

    private fun inTunnel(
        packageName: String,
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork = this.profile,
    ) = PackageFilter.inTunnel(packageName, PackageFilter.decide(mode, chosen, profile))

    @Test
    fun `без профиля и без выбора в туннель идут все`() {
        assertTrue(inTunnel(browser, AccessControlMode.AcceptAll, emptySet(), ProfileNetwork()))
    }

    @Test
    fun `профильный список выводит приложение мимо туннеля в любом режиме`() {
        for (mode in AccessControlMode.entries) {
            assertFalse(inTunnel(bank, mode, emptySet()), "режим $mode")
        }
    }

    @Test
    fun `человек не может вернуть в туннель то, что вывел провайдер`() {
        // Даже отметив банк в режиме «только выбранные», человек не пустит его в туннель.
        assertFalse(inTunnel(bank, AccessControlMode.AcceptSelected, setOf(bank, browser)))
        assertTrue(inTunnel(browser, AccessControlMode.AcceptSelected, setOf(bank, browser)))
    }

    @Test
    fun `выбор человека складывается с профильным`() {
        val chosen = setOf(browser)
        val decision = PackageFilter.decide(AccessControlMode.DenySelected, chosen, profile)

        assertTrue(decision.allowed == null)
        assertTrue(decision.denied.containsAll(listOf(bank, gov, browser)))
    }

    @Test
    fun `include-package пускает в туннель только перечисленных`() {
        val only = ProfileNetwork(includePackage = listOf(browser), excludePackage = listOf(bank))

        assertTrue(inTunnel(browser, AccessControlMode.AcceptAll, emptySet(), only))
        assertFalse(inTunnel("com.other", AccessControlMode.AcceptAll, emptySet(), only))
        // Пересечение с выбором человека: он может сузить список, но не расширить.
        assertFalse(inTunnel(browser, AccessControlMode.AcceptSelected, setOf("com.other"), only))
    }

    @Test
    fun `через VPN без отметок в туннель идут все, кроме списка провайдера`() {
        assertTrue(inTunnel(browser, AccessControlMode.AcceptSelected, emptySet()))
        assertFalse(inTunnel(bank, AccessControlMode.AcceptSelected, emptySet()))

        val only = ProfileNetwork(includePackage = listOf(browser))
        assertTrue(inTunnel(browser, AccessControlMode.AcceptSelected, emptySet(), only))
        assertFalse(inTunnel("com.other", AccessControlMode.AcceptSelected, emptySet(), only))

        // Галочка при этом не заблокирована: с неё начинается выбор.
        assertFalse(PackageFilter.lockedByProfile(browser, AccessControlMode.AcceptSelected, emptySet(), profile))
    }

    @Test
    fun `заблокированным считается тот, чью судьбу галочка не меняет`() {
        assertTrue(PackageFilter.lockedByProfile(bank, AccessControlMode.DenySelected, emptySet(), profile))
        assertFalse(PackageFilter.lockedByProfile(browser, AccessControlMode.DenySelected, emptySet(), profile))

        // В режиме «все» галочки не значат ничего, поэтому заблокировано всё: экран
        // в этом режиме список и не показывает.
        assertTrue(PackageFilter.lockedByProfile(browser, AccessControlMode.AcceptAll, emptySet(), profile))
    }

    @Test
    fun `ответ для списка совпадает с поштучным при любых отметках и списках провайдера`() {
        val universe = listOf(bank, gov, browser, "com.other")

        fun subsets(): List<Set<String>> = (0 until (1 shl universe.size)).map { bits ->
            universe.filterIndexedTo(HashSet()) { i, _ -> bits and (1 shl i) != 0 }
        }

        for (mode in AccessControlMode.entries) for (include in subsets()) for (exclude in subsets()) {
            val lists = ProfileNetwork(includePackage = include.toList(), excludePackage = exclude.toList())
            val batch = PackageFilter.lockedByProfile(universe, mode, lists)

            for (chosen in subsets()) for (app in universe) {
                assertTrue(
                    (app in batch) == PackageFilter.lockedByProfile(app, mode, chosen, lists),
                    "$app, режим $mode, include $include, exclude $exclude, отмечены $chosen",
                )
            }
        }
    }
}
