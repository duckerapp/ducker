package com.github.kr328.clash.service.remote

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
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

    fun setLogObserver(observer: ILogObserver?)
}