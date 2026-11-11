package com.github.kr328.clash.service

import com.github.kr328.clash.service.store.UserRulesStore
import kotlinx.coroutines.sync.withLock
import com.github.kr328.clash.service.clash.module.configLoadLock
import android.content.Context
import com.github.kr328.clash.common.util.backgroundScope
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.LogSlice
import com.github.kr328.clash.service.model.ShareState
import com.github.kr328.clash.service.clash.module.ShareModule
import com.github.kr328.clash.service.clash.module.MobileModule
import com.github.kr328.clash.service.model.MobileState
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendOverrideChanged
import com.github.kr328.clash.service.util.sendSelectorChanged
import kotlinx.coroutines.*

class ClashManager(private val context: Context) : IClashManager,
    CoroutineScope by backgroundScope("ClashManager") {
    private val store = ServiceStore(context)

    /** Профиль и время его файла, уже загруженные ради замера. */
    private var previewLoaded: Pair<java.util.UUID, Long>? = null

    /**
     * Интерфейс ушёл — профиль, загруженный ради показа узлов ([loadForPreview]), из ядра убираем.
     * Иначе при выключенном VPN процесс службы оставался с загруженным профилем и его таймерами:
     * проверки групп, NTP, обновления провайдеров (аудит, perf 1.3, F-208).
     */
    fun unloadPreview() {
        // Замок занят — конфиг грузит туннель, и ядро уже его.
        if (!configLoadLock.tryLock()) return

        try {
            if (previewLoaded == null || StatusProvider.serviceRunning) return

            Clash.reset()

            previewLoaded = null
        } finally {
            configLoadLock.unlock()
        }
    }

    override fun queryTunnelState(): TunnelState {
        return Clash.queryTunnelState()
    }

    override fun queryCoreVersion(): String {
        return Bridge.nativeCoreVersion().replace("_", "-")
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    override fun queryTrafficNow(): Long {
        return Clash.queryTrafficNow()
    }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override suspend fun queryProfileGroups(): List<ProfileGroup> {
        val active = store.activeProfile ?: return emptyList()

        val selected = SelectionDao().querySelections(active).associate { it.proxy to it.selected }

        return Clash.queryProfileGroups(
            context.importedDir.resolve(active.toString()).absolutePath
        ).map { group ->
            // Ядро про сохранённый выбор не знает: в файле профиля его нет.
            group.copy(now = selected[group.name] ?: group.proxies.firstOrNull().orEmpty())
        }
    }

    override suspend fun queryProfileNetwork(uuid: java.util.UUID?): ProfileNetwork {
        val profile = uuid ?: store.activeProfile ?: return ProfileNetwork()

        return Clash.queryProfileNetwork(
            context.importedDir.resolve(profile.toString()).absolutePath
        )
    }

    /**
     * Замер узлов при выключенном туннеле (D-63).
     *
     * Ядру для этого нужен разобранный конфиг: имена групп и участники живут в нём.
     * Туннель при этом не поднимается, а сессионный оверрайд глушит всё, что слушает
     * порты: от ядра нужны только исходящие соединения до узлов.
     *
     * Загружаем не чаще, чем надо: повторный вызов на том же профиле ничего не делает.
     */
    override suspend fun loadForPreview(): Boolean = configLoadLock.withLock {
        // Туннель уже поднят — конфиг в ядре и так настоящий, трогать нельзя.
        // Признак именно `serviceRunning`: поле с именем профиля после остановки
        // остаётся заполненным, оно только для показа. Проверка — под замком загрузки
        // (D-110): иначе замер мог наложить свои правки поверх конфига туннеля.
        if (StatusProvider.serviceRunning) return@withLock true

        val active = store.activeProfile ?: return@withLock false
        val imported = ImportedDao().queryByUUID(active) ?: return@withLock false

        val dir = context.importedDir.resolve(active.toString())
        val stamp = active to dir.resolve("config.yaml").lastModified()

        // Одной отметки мало: после остановки туннеля ядро выгружает профиль, а отметка
        // остаётся, и список узлов молча становился пустым. Верим ей, только пока ядро
        // и правда отвечает группами.
        if (stamp == previewLoaded && Clash.queryGroupNames(false).isNotEmpty()) return@withLock true

        runCatching {
            val override = Clash.queryOverride(Clash.OverrideSlot.Session)

            override.mixedPort = 0
            override.httpPort = 0
            override.socksPort = 0
            override.redirectPort = 0
            override.tproxyPort = 0
            override.listeners = emptyList()
            override.allowLan = false
            // The profile's DNS stays on: node names resolve through it as they will with the
            // tunnel up. Only its server is left without an address (D-306).
            override.dns.listen = ""

            Clash.patchOverride(Clash.OverrideSlot.Session, override)

            Clash.setAgeSecretKey(imported.ageSecretKey?.takeIf { it.isNotBlank() })
            Clash.setUserRules(UserRulesStore(context).raw(active))
            Clash.load(dir).await()

            // Выбор узла показываем тот же, что применится при запуске.
            SelectionDao().querySelections(active).forEach {
                Clash.patchSelector(it.proxy, it.selected)
            }

            keepGlobalOffDirect(active)

            previewLoaded = stamp

            true
        }.getOrElse {
            Log.w("Load profile for preview: $it", it)

            false
        }
    }

    override fun setLogLevel(level: LogMessage.Level) {
        Clash.setLogLevel(level)
    }

    override fun patchStoredSelector(group: String, name: String) {
        val active = store.activeProfile ?: return

        // With the VPN off the core may hold no config: the main group is then the one saved to bring back.
        if (group == MobileModule.mainGroup() || group == store.mobileBackGroup) MobileModule.manual(context)

        SelectionDao().setSelected(Selection(active, group, name))
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
    }

    /**
     * Раньше режим менялся через оверрайд с рассылкой, и служба заново грузила весь конфиг:
     * секунды, перемер всех узлов, а чип на экране ждал этого. Теперь ядро переключается
     * на лету, а оверрайд без рассылки нужен, чтобы выбор пережил следующую загрузку —
     * например, обновление подписки. Под замком загрузки: иначе она могла прочитать
     * оверрайд до записи и вернуть прежний режим.
     */
    override suspend fun setMode(mode: TunnelState.Mode) = configLoadLock.withLock {
        val override = Clash.queryOverride(Clash.OverrideSlot.Session)

        override.mode = mode

        Clash.patchOverride(Clash.OverrideSlot.Session, override)

        Clash.setTunnelMode(mode)

        store.activeProfile?.let { keepGlobalOffDirect(it) }

        Unit
    }

    override fun validateConfig(path: String): String = Clash.validateConfig(path)

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        // Постоянный оверрайд читают настройки ради тумблера журнала — сверяем его срок (D-224).
        if (slot == Clash.OverrideSlot.Persist) VerboseLog.check(context)

        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        // A node of the main group picked by the person holds against «Мобильная сеть» until the network changes.
        if (group == MobileModule.mainGroup()) MobileModule.manual(context)

        return Clash.patchSelector(group, name).also {
            if (it) {
                context.sendSelectorChanged()
            }

            val current = store.activeProfile ?: return@also

            if (it) {
                SelectionDao().setSelected(Selection(current, group, name))
            } else {
                SelectionDao().removeSelected(current, group)
            }
        }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        Clash.patchOverride(slot, configuration)

        // Журнал включили — отсюда идут его сутки, выключили — будильник снимается (D-224).
        if (slot == Clash.OverrideSlot.Persist) VerboseLog.check(context)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override fun probeProxy(name: String): ProbeResult {
        return Clash.probeProxy(name)
    }

    override suspend fun healthCheck(group: String) {
        return Clash.healthCheck(group).await()
    }

    override suspend fun healthCheckChosen(group: String) {
        return Clash.healthCheckChosen(group).await()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override suspend fun queryLogs(epoch: Long, after: Long): LogSlice {
        return LogTail.read(epoch, after)
    }

    override fun clearLogs() {
        LogTail.clear()
    }

    override fun queryConnections(): List<Connection> = Clash.queryConnections()

    override fun closeAllConnections() {
        Clash.closeAllConnections()
    }

    override fun closeConnections(ids: List<String>) {
        Clash.closeConnections(ids)
    }

    override fun queryShare(): ShareState =
        ShareModule.active?.state ?: ShareState(closed = ShareModule.providerClosed, password = store.sharePassword)

    override suspend fun setShare(on: Boolean): ShareState = ShareModule.active?.turn(on) ?: queryShare()

    override suspend fun setSharePassword(on: Boolean): ShareState {
        ShareModule.active?.let { return it.password(on) }

        store.sharePassword = on

        return queryShare()
    }

    override suspend fun newSharePassword(): ShareState = ShareModule.active?.renew() ?: queryShare()

    override suspend fun queryMobile(): MobileState = MobileModule.state(context)

    override suspend fun setMobile(on: Boolean, profile: String, nodes: List<String>): MobileState {
        val uuid = profile.takeIf { it.isNotEmpty() }?.let(java.util.UUID::fromString)

        // A sheet closed without a change, a node put back where it was: nothing to switch again.
        if (on == store.mobileOn && uuid == store.mobileProfile && nodes == store.mobileNodes) return queryMobile()

        store.mobileOn = on
        store.mobileProfile = uuid
        store.mobileNodes = nodes

        // Off: nothing is brought back or shown, with the VPN off too.
        if (!on) MobileModule.forget(context)

        MobileModule.active?.changed()

        // The picked subscription's files come now, not inside the first switch to it.
        if (on && uuid != null) ProfileProcessor.prefetch(context, uuid)

        return queryMobile()
    }

    override suspend fun queryMobileNodes(profile: String): List<String> {
        val uuid = java.util.UUID.fromString(profile)

        // The core knows the members a file does not name (`use:` providers); it runs only the active subscription.
        if (StatusProvider.serviceRunning && uuid == store.activeProfile) {
            MobileModule.mainGroup()?.let { main -> MobileModule.members(main).let { if (it.isNotEmpty()) return it } }
        }

        return Clash.queryProfileGroups(context.importedDir.resolve(profile).absolutePath)
            .firstOrNull { it.type.equals("select", ignoreCase = true) }
            ?.proxies.orEmpty()
    }
}

/**
 * Группу GLOBAL, которую провайдер не описал, ядро собирает само, и первым в ней — а значит,
 * выбранным — стоит DIRECT (F-125). В режиме Global весь трафик тогда шёл мимо прокси. Если
 * выбран встроенный выход и человек его сам не выбирал, ставим первый настоящий узел (D-136).
 * Выбор не запоминаем: при следующей загрузке он получится тот же. Сбой здесь загрузку
 * профиля не ломает — остаётся выбор ядра.
 */
internal suspend fun keepGlobalOffDirect(profile: java.util.UUID) = runCatching {
    if (Clash.queryTunnelState().mode != TunnelState.Mode.Global) return@runCatching
    if (SelectionDao().querySelections(profile).any { it.proxy == GLOBAL }) return@runCatching

    val global = Clash.queryGroup(GLOBAL, ProxySort.Default)

    if (global.proxies.firstOrNull { it.name == global.now }?.type !in BUILTIN_PROXY_TYPES) return@runCatching

    val first = global.proxies.firstOrNull { it.type !in BUILTIN_PROXY_TYPES } ?: return@runCatching

    Clash.patchSelector(GLOBAL, first.name)
}.onFailure { Log.w("Keep GLOBAL off DIRECT: $it", it) }

private const val GLOBAL = "GLOBAL"

/** Встроенные выходы ядра: имена типов из `constant/adapters.go` mihomo. */
val BUILTIN_PROXY_TYPES = setOf("Direct", "Reject", "RejectDrop", "Compatible", "Pass", "PassRule", "Dns")
