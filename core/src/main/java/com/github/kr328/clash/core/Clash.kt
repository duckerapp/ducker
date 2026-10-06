package com.github.kr328.clash.core

import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

object Clash {
    enum class OverrideSlot {
        Persist, Session
    }

    private val ConfigurationOverrideJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun reset() {
        Bridge.nativeReset()
    }

    fun forceGc() {
        Bridge.nativeForceGc()
    }

    fun suspendCore(suspended: Boolean) {
        Bridge.nativeSuspend(suspended)
    }

    fun queryTunnelState(): TunnelState {
        val json = Bridge.nativeQueryTunnelState()

        return Json.decodeFromString(TunnelState.serializer(), json)
    }

    /** Сменить режим на лету, без перезагрузки конфига. `false` — ядро такого режима не знает. */
    fun setTunnelMode(mode: TunnelState.Mode): Boolean {
        return Bridge.nativeSetTunnelMode(mode.name.lowercase()) == 0
    }

    fun queryTrafficNow(): Traffic {
        return Bridge.nativeQueryTrafficNow()
    }

    fun queryTrafficTotal(): Traffic {
        return Bridge.nativeQueryTrafficTotal()
    }

    fun notifyDnsChanged(dns: List<String>) {
        Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
    }

    fun notifyTimeZoneChanged(name: String, offset: Int) {
        Bridge.nativeNotifyTimeZoneChanged(name, offset)
    }

    fun notifyInstalledAppsChanged(uids: List<Pair<Int, String>>) {
        val uidList = uids.joinToString(separator = ",") { "${it.first}:${it.second}" }

        Bridge.nativeNotifyInstalledAppChanged(uidList)
    }

    fun startTun(
        fd: Int,
        stack: String,
        congestion: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int
    ): Boolean =
        Bridge.nativeStartTun(fd, stack, congestion, gateway, portal, dns, SafeTunInterface(markSocket, querySocketUid)) == 0

    fun stopTun() {
        Bridge.nativeStopTun()
    }

    fun startHttp(listenAt: String): String? {
        return Bridge.nativeStartHttp(listenAt)
    }

    fun stopHttp() {
        Bridge.nativeStopHttp()
    }

    /**
     * Сетевые настройки из файла профиля, которые применяет уже Kotlin-сторона:
     * списки пакетов для `VpnService` и признак отключённого DNS.
     */
    fun queryProfileNetwork(path: String): ProfileNetwork {
        return try {
            Json.Default.decodeFromString(
                ProfileNetwork.serializer(),
                Bridge.nativeQueryProfileNetwork(path)
            )
        } catch (e: Exception) {
            ProfileNetwork()
        }
    }

    /**
     * Группы из файла профиля. Ядро при этом ничего не запускает — ни слушателей,
     * ни провайдеров, — поэтому вызов работает и при выключенном туннеле.
     */
    fun queryProfileGroups(path: String): List<ProfileGroup> {
        return try {
            Json.Default.decodeFromString(
                ListSerializer(ProfileGroup.serializer()),
                Bridge.nativeQueryProfileGroups(path)
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
        val names = Json.Default.decodeFromString(
            JsonArray.serializer(),
            Bridge.nativeQueryGroupNames(excludeNotSelectable)
        )

        return names.map {
            require(it.jsonPrimitive.isString)

            it.jsonPrimitive.content
        }
    }

    fun queryGroup(name: String, sort: ProxySort): ProxyGroup {
        return Bridge.nativeQueryGroup(name, sort.name)
            ?.let { Json.Default.decodeFromString(ProxyGroup.serializer(), it) }
            ?: ProxyGroup("Unknown", emptyList(), "", "")
    }

    fun healthCheck(name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeHealthCheck(this, name)
        }
    }

    /**
     * Один замер конкретного узла. Блокирующий — ждать всё равно приходится.
     * Серию собирает вызывающая сторона (D-57).
     */
    /**
     * Уровень журнала у работающего ядра. В отличие от оверрайда, применяется сразу,
     * без переподключения, — переключатель «подробный журнал» должен срабатывать на месте.
     */
    fun setLogLevel(level: LogMessage.Level): Boolean =
        Bridge.nativeSetLogLevel(level.name.lowercase()) == 0

    fun probeProxy(name: String, url: String = "", timeout: Int = 0): ProbeResult {
        return try {
            Json.Default.decodeFromString(
                ProbeResult.serializer(),
                Bridge.nativeProbeProxy(name, url, timeout)
            )
        } catch (e: Exception) {
            ProbeResult(error = e.message ?: "probe failed")
        }
    }

    /**
     * Закрыть все установленные соединения.
     *
     * Нужно при смене сети: сокеты, открытые через пропавший интерфейс, сами об этом не
     * узнают и висят до своего таймаута — это десятки секунд, в которые приложения кажутся
     * зависшими. После закрытия они переподключаются сразу по новой сети (D-59).
     */
    fun closeAllConnections() {
        Bridge.nativeCloseAllConnections()
    }

    /**
     * Закрыть все сессии мультиплекса (D-105). Сессия — одно соединение до сервера, по
     * которому идут потоки многих приложений; [closeAllConnections] её не видит. После
     * смены сети она либо уже мертва, либо умрёт вместе с прежней сетью.
     */
    fun resetMux() {
        Bridge.nativeResetMux()
    }

    /**
     * Проверить пингом сессии мультиплекса, молчавшие дольше 35 секунд (D-105, D-110): экран
     * включился, и человек вот-вот откроет приложение. Мёртвые закрываются, живые остаются.
     * Не ждёт ответа.
     */
    fun probeMux() {
        Bridge.nativeProbeMux()
    }

    fun healthCheckAll() {
        Bridge.nativeHealthCheckAll()
    }

    /**
     * Перемерить группы с автовыбором (D-110): их первая проверка при загрузке конфига
     * могла уйти до подъёма туннеля и записать все узлы мёртвыми. Не ждёт ответа.
     */
    fun healthCheckAuto() {
        Bridge.nativeHealthCheckAuto()
    }

    fun patchSelector(selector: String, name: String): Boolean {
        return Bridge.nativePatchSelector(selector, name)
    }

    fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        reportStatus: (FetchStatus) -> Unit
    ): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeFetchAndValid(
                SafeFetchCallback(this, reportStatus),
                path.absolutePath,
                url,
                force
            )
        }
    }

    fun load(path: File): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeLoad(this, path.absolutePath)
        }
    }

    fun queryProviders(): List<Provider> {
        val providers =
            Json.Default.decodeFromString(JsonArray.serializer(), Bridge.nativeQueryProviders())

        return List(providers.size) {
            Json.Default.decodeFromJsonElement(Provider.serializer(), providers[it])
        }
    }

    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply {
            Bridge.nativeUpdateProvider(this, type.toString(), name)
        }
    }

    fun queryOverride(slot: OverrideSlot): ConfigurationOverride {
        return try {
            ConfigurationOverrideJson.decodeFromString(
                ConfigurationOverride.serializer(),
                Bridge.nativeReadOverride(slot.ordinal)
            )
        } catch (e: Exception) {
            ConfigurationOverride()
        }
    }

    fun patchOverride(slot: OverrideSlot, configuration: ConfigurationOverride) {
        Bridge.nativeWriteOverride(
            slot.ordinal,
            ConfigurationOverrideJson.encodeToString(
                ConfigurationOverride.serializer(),
                configuration
            )
        )
    }

    fun clearOverride(slot: OverrideSlot) {
        Bridge.nativeClearOverride(slot.ordinal)
    }

    fun subscribeLogcat(): ReceiveChannel<LogMessage> {
        return Channel<LogMessage>(32).apply {
            Bridge.nativeSubscribeLogcat(LogcatChannel(this))
        }
    }

    /**
     * События ядра для самописца службы (D-111): свой отбор, без оглядки на уровень журнала
     * профиля, — ошибки, предупреждения, info без строк о соединениях, мультиплекс.
     */
    fun subscribeRecorder(): ReceiveChannel<LogMessage> {
        return Channel<LogMessage>(256).apply {
            Bridge.nativeSubscribeRecorder(LogcatChannel(this))
        }
    }

    fun setAgeSecretKey(key: String?) {
        Bridge.nativeSetAgeSecretKey(key)
    }

    /**
     * Свои правила человека для следующих загрузок профиля (D-123): JSON-список
     * `{type, value, target}`, пусто — правил нет. Встают перед правилами провайдера.
     */
    fun setUserRules(rules: String) {
        Bridge.nativeSetUserRules(rules)
    }

    /** Разобрать `config.yaml` в каталоге [path], не применяя (D-124). Пусто — ошибок нет. */
    fun validateConfig(path: String): String {
        return Bridge.nativeValidateConfig(path)
    }

    /**
     * Задаёт заголовки, которые ядро добавит к запросу подписки: идентификатор
     * устройства, сведения о нём и User-Agent (D-30, D-31, D-38).
     *
     * Заголовки попадают только в запрос подписки. Провайдеры правил качаются
     * с чужих адресов и получают лишь User-Agent — инвариант 3 в PLAN.md.
     */
    /**
     * Разбирает набор ссылок протоколов и подставляет узлы во встроенный шаблон (D-19).
     *
     * Возвращает готовый конфиг либо кидает [ClashException] с текстом, который можно
     * показать человеку: по ссылке пришло что-то, что не разобралось.
     */
    fun convertAndApplyTemplate(
        content: String,
        template: String,
        directHost: String = "",
    ): String {
        val result = Json.Default.decodeFromString(
            ConvertResult.serializer(),
            Bridge.nativeConvertAndApplyTemplate(content, template, directHost),
        )

        if (result.error.isNotEmpty()) {
            throw ClashException(result.error)
        }

        return result.yaml
    }

    fun setSubscriptionHeaders(headers: Map<String, String>) {
        Bridge.nativeSetSubscriptionHeaders(
            if (headers.isEmpty()) null
            else Json.Default.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                headers,
            )
        )
    }

    fun genX25519KeyPair(): AgeKeyPair {
        return parseAgeKeyPair(checkNotNull(Bridge.nativeGenX25519KeyPair()))
    }

    fun genHybridKeyPair(): AgeKeyPair {
        return parseAgeKeyPair(checkNotNull(Bridge.nativeGenHybridKeyPair()))
    }

    fun veritySecretKeys(vararg secretKeys: String): Boolean {
        return Bridge.nativeVeritySecretKeys(secretKeys.firstOrNull() ?: "")
    }

    fun toPublicKeys(vararg secretKeys: String): List<String> {
        return Bridge.nativeToPublicKeys(secretKeys.firstOrNull() ?: "")
            ?.let { Json.Default.decodeFromString(ListSerializer(String.serializer()), it) }
            ?: emptyList()
    }

    fun verityPublicKeys(vararg publicKeys: String): Boolean {
        return Bridge.nativeVerityPublicKeys(publicKeys.firstOrNull() ?: "")
    }

    private fun parseAgeKeyPair(value: String): AgeKeyPair {
        return Json.Default.decodeFromString(AgeKeyPair.serializer(), value)
    }
}
