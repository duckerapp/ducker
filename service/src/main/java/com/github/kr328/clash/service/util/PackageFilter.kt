package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.model.AccessControlMode

/**
 * Кто идёт через туннель: списки профиля плюс выбор человека.
 *
 * Живёт отдельно от `TunService`, потому что тот же ответ нужен экрану «Приложения»:
 * человек должен видеть, что часть приложений уже выведена конфигом провайдера, и что
 * снять их галочкой нельзя. Считать это в двух местах разными формулами — верный способ
 * показать одно, а сделать другое.
 *
 * Правило сложения: список провайдера не отменяется никогда. Он нужен, чтобы банк и
 * госуслуги видели сеть без VPN, и терять его из-за галочки нельзя.
 */
object PackageFilter {
    /**
     * [allowed] непустой — в туннель попадают только эти приложения; null — все, кроме
     * [denied]. Ровно эти два случая умеет `VpnService.Builder`.
     */
    data class Decision(val allowed: Set<String>?, val denied: Set<String>)

    fun decide(
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork,
    ): Decision =
        // «Через VPN» без единой отметки — «ещё не выбрал», а не «ни одного приложения»:
        // иначе касание по режиму оставляло в туннеле один ducker (D-139).
        if (mode == AccessControlMode.AcceptSelected && chosen.isEmpty()) literal(AccessControlMode.DenySelected, chosen, profile)
        else literal(mode, chosen, profile)

    private fun literal(
        mode: AccessControlMode,
        chosen: Set<String>,
        profile: ProfileNetwork,
    ): Decision {
        val include = profile.includePackage.toSet()
        val exclude = profile.excludePackage.toSet()

        val allowed = when {
            include.isNotEmpty() && mode == AccessControlMode.AcceptSelected ->
                include.intersect(chosen) - exclude

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
