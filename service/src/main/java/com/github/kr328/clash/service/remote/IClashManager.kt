package com.github.kr328.clash.service.remote

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.model.LogSlice
import com.github.kr328.clash.service.model.ShareState
import com.github.kr328.clash.service.model.MobileState
import com.github.kr328.kaidl.BinderInterface
import java.util.UUID

@BinderInterface
interface IClashManager {
    fun queryTunnelState(): TunnelState

    /**
     * Версия собранного ядра — её спрашивает поддержка провайдера.
     *
     * Через службу, а не напрямую: значение знает нативная библиотека, а она живёт
     * в процессе службы, и тянуть её в интерфейс незачем (FACTS.md, F-5).
     */
    fun queryCoreVersion(): String
    fun queryTrafficTotal(): Long

    /**
     * Скорость за последнюю секунду: верхние 32 бита — отдача, нижние — приём.
     *
     * В ядре она была всегда, но наверх не отдавалась: ею пользовалось только
     * уведомление службы (FACTS.md, F-28). Главной она нужна, чтобы показывать
     * скорость без захода в другие экраны.
     */
    fun queryTrafficNow(): Long
    fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String>

    /**
     * Группы активного профиля, прочитанные из файла. Работает при выключенном туннеле:
     * ядро только разбирает YAML и ничего не запускает.
     */
    suspend fun queryProfileGroups(): List<ProfileGroup>

    /**
     * Списки `tun.include-package` / `tun.exclude-package` подписки [uuid], без него — активной.
     * Нужны экрану «Приложения»: он показывает, кого уже вывел конфиг провайдера, и не даёт
     * снять это галочкой; и своим правилам — правило на такое приложение не сработает (D-135).
     */
    suspend fun queryProfileNetwork(uuid: UUID?): ProfileNetwork

    /**
     * Загрузить активный профиль в ядро, не поднимая туннель, — чтобы можно было
     * померить узлы до подключения. Возвращает false, если профиля нет или загрузка
     * не удалась.
     */
    suspend fun loadForPreview(): Boolean

    /**
     * Поднять уровень журнала у уже работающего ядра, не дожидаясь перезагрузки конфига.
     */
    fun setLogLevel(level: LogMessage.Level)

    /**
     * Запомнить выбор узла, пока ядро не работает. Служба применит его при запуске —
     * тем же механизмом, которым восстанавливает выбор после перезапуска.
     */
    fun patchStoredSelector(group: String, name: String)
    fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup
    fun queryProviders(): ProviderList

    fun patchSelector(group: String, name: String): Boolean

    /** Один замер узла для диагностики (D-57). Серию собирает интерфейс. */
    fun probeProxy(name: String): ProbeResult

    suspend fun healthCheck(group: String)

    /** Checks only the node [group] uses now: Home's own number after a start (Q-102). */
    suspend fun healthCheckChosen(group: String)
    suspend fun updateProvider(type: Provider.Type, name: String)

    /**
     * Режим туннеля с экрана «Прокси». Действует сразу и переживает перезагрузки конфига
     * до конца сеанса, но сам конфиг не перезагружает.
     */
    suspend fun setMode(mode: TunnelState.Mode)

    /**
     * Проверить конфиг из редактора (D-124): `config.yaml` в каталоге [path] разбирается,
     * как при загрузке, но не применяется. Пусто — ошибок нет, иначе текст ошибки ядра.
     */
    fun validateConfig(path: String): String

    fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride
    fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride)
    fun clearOverride(slot: Clash.OverrideSlot)

    /**
     * The core log for the “Журнал” screen (D-290): the lines numbered [after] and later, with the
     * number to ask from next time. The service holds the last lines itself — always while
     * “Подробный журнал” is on, otherwise only while somebody asks. A reader names the [epoch] of
     * its previous reply; another life of the service process answers with all it has.
     */
    suspend fun queryLogs(epoch: Long, after: Long): LogSlice

    /** “Очистить”: the service's copy too, or an evicted interface would read the lines again. */
    fun clearLogs()

    /** «Подключения» (stage 15): the core's open connections, read about once a second while the page is open. */
    fun queryConnections(): List<Connection>

    /** «Сбросить все» on «Подключения»: every connection is closed, apps open new ones by the current rules. */
    fun closeAllConnections()

    /** «Раздача по Wi-Fi» (D-419–D-421) for its card on «Возможности». */
    fun queryShare(): ShareState

    /** Its switch: works only while the VPN runs; returns what came of it. */
    suspend fun setShare(on: Boolean): ShareState

    /** The «Пароль» switch for a Wi-Fi that is not the phone's own, remembered (D-421). */
    suspend fun setSharePassword(on: Boolean): ShareState

    /** «Мобильная сеть» (D-423, D-424) for its page: the settings, the network now, the subscription Wi-Fi has. */
    suspend fun queryMobile(): MobileState

    /** Saves the page's settings; on the mobile network a new choice applies at once. */
    suspend fun setMobile(on: Boolean, profile: String, nodes: List<String>): MobileState

    /** Members of [profile]'s main group to pick from: the core's while it runs that subscription, else its file's. */
    suspend fun queryMobileNodes(profile: String): List<String>
}