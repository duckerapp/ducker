package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.model.AccessControlMode

/**
 * Кто идёт через туннель: списки профиля плюс выбор человека.
 *
 * Живёт отдельно от `TunService`, потому что тот же ответ нужен экрану «Приложения»:
 * человек должен видеть, что часть приложений уже выведена конфигом провайдера, и что
 * общие отметки их не меняют. Считать это в двух местах разными формулами — верный способ
 * показать одно, а сделать другое.
 *
 * Правило сложения: галочка человека список провайдера не отменяет. Он нужен, чтобы банк и
 * госуслуги видели сеть без VPN, и терять его из-за галочки нельзя. The config's own rows change only one by one and
 * on purpose, per subscription ([Changes], D-472), unless the provider locks them with `ducker-override-user: on`.
 */
object PackageFilter {
    /**
     * [allowed] непустой — в туннель попадают только эти приложения; null — все, кроме
     * [denied]. Ровно эти два случая умеет `VpnService.Builder`.
     */
    data class Decision(val allowed: Set<String>?, val denied: Set<String>)

    /**
     * What the person changed of a subscription's config apps (D-472, D-473): the rows [reversed] in «Приложения»,
     * and the apps [ruled] by «Мои правила» — those go into the tunnel, where a rule works, for as long as the rule is
     * there. Empty under `ducker-override-user: on`. The rule is not copied into [reversed]: two records drifted apart
     * when the provider's lock came and went.
     */
    data class Changes(val reversed: Set<String> = emptySet(), val ruled: Set<String> = emptySet())

    fun decide(
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork,
        changes: Changes = Changes(),
    ): Decision =
        // «Через VPN» без единой отметки — «ещё не выбрал», а не «ни одного приложения»:
        // иначе касание по режиму оставляло в туннеле один ducker (D-139).
        (if (mode == AccessControlMode.AcceptSelected && chosen.isEmpty()) literal(AccessControlMode.DenySelected, chosen, profile)
        else literal(mode, chosen, profile))
            .withChanges(profile, changes)

    /** The config's own rows: its `include-package` and `exclude-package`. */
    fun listed(profile: ProfileNetwork): Set<String> = profile.includePackage.toSet() + profile.excludePackage

    /** Where the provider alone puts a [listed] app: an excluded one past the tunnel, an included one into it. */
    fun providerInTunnel(packageName: String, profile: ProfileNetwork): Boolean =
        packageName !in profile.excludePackage && packageName in profile.includePackage

    private fun Decision.withChanges(profile: ProfileNetwork, changes: Changes): Decision {
        val listed = listed(profile)
        val flipped = changes.reversed.intersect(listed)
        val ruled = changes.ruled.intersect(listed)

        if (flipped.isEmpty() && ruled.isEmpty()) return this

        // A rule wins over a reversal that took its app out.
        val into = flipped.filterTo(HashSet()) { !providerInTunnel(it, profile) } + ruled
        val out = flipped - into

        return if (allowed != null) Decision(allowed + into - out, denied)
        else Decision(null, denied - into + out)
    }

    private fun literal(
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork,
    ): Decision {
        val include = profile.includePackage.toSet()
        val exclude = profile.excludePackage.toSet()

        val allowed = when {
            // The config's rows follow the provider and the person's reversals, not the marks (D-471): with a list
            // of its own the marks used to narrow it, now each of its rows is reversed by itself.
            include.isNotEmpty() -> include - exclude

            mode == AccessControlMode.AcceptSelected -> chosen - exclude

            else -> null
        }

        if (allowed != null) return Decision(allowed, emptySet())

        return Decision(
            allowed = null,
            denied = exclude + if (mode == AccessControlMode.DenySelected) chosen else emptySet(),
        )
    }

    fun inTunnel(packageName: String, decision: Decision): Boolean =
        decision.allowed?.contains(packageName) ?: (packageName !in decision.denied)

    /**
     * Решает ли судьбу приложения конфиг, а не галочка.
     *
     * Проверяем прямо: считаем исход с галочкой и без неё. Если он один и тот же, менять
     * галочку бессмысленно — значит, её нужно показать выключенной, а не обманывать
     * человека кликабельным переключателем, который ничего не делает.
     *
     * Режим — буквальный: у «Через VPN» без отметок приложение в туннеле и с галочкой, и без,
     * но галочка там не пустая — с неё начинается выбор.
     */
    fun lockedByProfile(
        packageName: String,
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork,
    ): Boolean {
        val on = inTunnel(packageName, literal(mode, chosen + packageName, profile))
        val off = inTunnel(packageName, literal(mode, chosen - packageName, profile))

        return on == off
    }

    /**
     * [lockedByProfile] сразу для списка — им пользуется экран «Приложения». Исход для приложения
     * зависит только от его собственной галочки, а не от чужих, поэтому на весь список хватает
     * двух решений: «отмечены все» и «не отмечен никто». Поштучный расчёт дважды пересобирал
     * множества провайдера на каждое приложение при каждом нажатии (аудит, perf 3.2).
     */
    fun lockedByProfile(
        packages: Collection<String>,
        mode: AccessControlMode,
        profile: ProfileNetwork,
    ): Set<String> {
        val on = literal(mode, packages.toSet(), profile)
        val off = literal(mode, emptySet(), profile)

        return packages.filterTo(HashSet()) { inTunnel(it, on) == inTunnel(it, off) }
    }
}
