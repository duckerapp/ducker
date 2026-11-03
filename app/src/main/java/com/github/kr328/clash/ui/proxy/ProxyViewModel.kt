package com.github.kr328.clash.ui.proxy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.BUILTIN_PROXY_TYPES
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.settings.SettingsStore
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Узел в списке. Свой тип, а не модель ядра: при выключенном туннеле мы знаем только
 * имена из файла профиля — ни задержек, ни живого состояния.
 */
data class NodeState(
    val name: String,
    val type: String = "",
    val delay: Int = 0,
    /** Поднят звёздочкой наверх списка. */
    val favorite: Boolean = false,
    /** When the core last tested the node, ms since the epoch; 0 — it has not. */
    val tested: Long = 0,
)

data class ProxyGroupState(
    val name: String,
    val now: String,
    val icon: String,
    val proxies: List<NodeState>,
    /**
     * Можно ли выбрать узел руками. У групп вроде `fallback` и `url-test` выбор делает
     * само ядро: нажатие на узел там молча ничего не меняло — ядро отклоняло запрос,
     * а человек видел, что «кнопка не работает».
     */
    val selectable: Boolean = true,
)

data class ProxyState(
    val groups: List<ProxyGroupState> = emptyList(),
    val mode: TunnelState.Mode? = null,
    /** Показывать ли переключатель Global (D-113, D-136). */
    val modeSwitch: Boolean = false,
    val checking: String? = null,
    /** Порядок узлов внутри групп: как в профиле или от быстрых к медленным. */
    val sort: ProxySort = ProxySort.Default,
    val running: Boolean = false,
    /**
     * Ядро знает про узлы и может их померить. При поднятом туннеле это всегда так,
     * при выключенном — если профиль удалось загрузить для замера (D-63).
     */
    val canCheck: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * What the core knows now laid over the list while a check is still running (D-282): [results]
 * are node name to its last result as [DelayStore.ofCore] gives it. A node shows a delay or "нет
 * ответа" the moment the core has tested it; one the core has not tested keeps what it showed. The
 * order stays as it is: sorted by delay, the cards would otherwise move every second.
 */
internal fun overlay(groups: List<ProxyGroupState>, results: Map<String, Int>): List<ProxyGroupState> =
    groups.map { group ->
        group.copy(proxies = group.proxies.map { node ->
            val result = results[node.name] ?: DelayStore.UNKNOWN

            if (result == DelayStore.UNKNOWN) node else node.copy(delay = result)
        })
    }

class ProxyViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProxyState())
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    private var favorites: Set<String> = emptySet()

    /**
     * Группы в том порядке, в каком их отдало ядро или файл профиля. Избранное
     * поднимается наверх поверх этого порядка, а не вместо него: иначе снятая
     * звёздочка оставляла узел на прежнем месте — возвращать его было уже некуда.
     */
    private var baseGroups: List<ProxyGroupState> = emptyList()

    /**
     * Сколько раз подряд пробовали померить группу. Не флаг «мерили»: первый замер
     * сразу после смены состояния туннеля часто уходит в пустоту, и одной попытки мало.
     */
    private val attempts = mutableMapOf<String, Int>()

    /** Состояние туннеля на прошлом обновлении: по его смене замеры устаревают. */
    private var lastRunning: Boolean? = null

    /** Когда туннель последний раз менял состояние: сразу после этого мерить бесполезно. */
    private var switchedAt = 0L

    /** The last change of network the tab has seen, as the service recorded it (D-299). */
    private var networkChangedAt = 0L

    /** Отложенный или идущий автозамер: второй поверх него не запускаем. */
    private var autoJob: Job? = null

    /**
     * The subscription and the hash of the text last written by [refresh]: an unchanged list is not
     * written again. A hash, not the text: 1 000 nodes in 30 groups are 2.7 MB of it (F-391).
     */
    private var saved: Pair<UUID, Int>? = null

    /**
     * One at a time and in order: two refreshes close together would otherwise write their lists in
     * either order. Off the main thread: those 2.7 MB took 70 ms to build on the emulator (F-391).
     */
    private val disk = Dispatchers.IO.limitedParallelism(1)

    /**
     * The list the tab showed last, at once after the process was killed (D-391); [refresh] replaces it
     * with the live one. A node chosen from it goes the usual way: with the VPN up to the core, which
     * refuses one the config no longer has; with it down into the stored choice, applied when the
     * config is loaded.
     */
    fun restore(context: Context) {
        if (_state.value.loaded) return

        viewModelScope.launch {
            val (stars, last) = withContext(Dispatchers.IO) {
                SettingsStore(context).favorites to ServiceStore(context).activeProfile?.let { ProxyListStore(context).load(it) }
            }

            // The live list came first.
            if (last == null || _state.value.loaded) return@launch

            favorites = stars
            baseGroups = last.groups

            _state.update {
                it.copy(
                    groups = withFavorites(baseGroups),
                    mode = last.mode,
                    modeSwitch = last.modeSwitch,
                    running = Remote.broadcasts.clashRunning,
                    canCheck = last.canCheck,
                    loaded = true,
                )
            }
        }
    }

    /**
     * The person came to the tab, or back to the app with it on screen: a new round of the tab's
     * own checks, so a "нет ответа" that has stood for a while is asked again once (D-318).
     */
    fun open(context: Context) {
        attempts.clear()

        refresh(context)
    }

    fun refresh(context: Context) {
        viewModelScope.launch {
            // Read before the list: a switch to another subscription meanwhile would file it under that one.
            val profile = withContext(Dispatchers.IO) { ServiceStore(context).activeProfile }

            // Признак работы берём у службы: `queryTunnelState` при выключенном туннеле
            // отвечает режимом Direct, а не ошибкой, и по нему «работает» не отличить.
            favorites = withContext(Dispatchers.IO) { SettingsStore(context).favorites }

            val running = Remote.broadcasts.clashRunning

            if (lastRunning != null && lastRunning != running) {
                attempts.clear()

                switchedAt = System.currentTimeMillis()
            }

            // Экран открыли, когда туннель уже поднимался — смены состояния мы не видели,
            // и окно «сеть устаканивается» пропускалось (D-110). Отсчитываем его от подъёма.
            if (lastRunning == null && running) {
                switchedAt = withContext(Dispatchers.IO) { ServiceStore(context).tunnelStartedAt }
            }

            lastRunning = running

            // The network under the tunnel became another one: what the core holds are results
            // of the previous network, and every group is due one check of the tab's own (D-299).
            val changedAt = if (running) {
                withContext(Dispatchers.IO) { ServiceStore(context).networkChangedAt }
            } else 0L

            if (changedAt > networkChangedAt) {
                attempts.clear()

                networkChangedAt = changedAt
            }

            val mode = if (running) {
                runCatching { withClash { queryTunnelState().mode } }.getOrNull()
            } else null

            // Переключатель Global/Rules виден всегда, пока провайдер не спрятал его заголовком
            // `ducker-global: off` (D-143). От DIRECT первым в группе GLOBAL, которую ядро
            // собирает само (F-125), защищает служба.
            val modeSwitch = running &&
                runCatching { withProfile { queryActive() } }.getOrNull()?.globalHidden != true

            // При выключенном туннеле просим службу поднять конфиг в ядре без туннеля:
            // тогда видны и задержки, и узел можно выбрать осознанно, а не вслепую.
            val preview = !running &&
                runCatching { withClash { loadForPreview() } }.getOrDefault(false)

            // Список у ядра: там задержки и живой выбор. Если ядро поднять не удалось —
            // читаем группы прямо из файла профиля, без задержек.
            val live = if (running || preview) liveGroups(_state.value.sort) else storedGroups()

            // Свежие значения кладём в хранилище, а показываем свежие поверх прошлых:
            // экран не должен моргать прочерками, пока идёт новый замер (D-72).
            val store = DelayStore(context)

            withContext(Dispatchers.IO) {
                store.merge(live.flatMap { it.proxies }.associate { it.name to it.delay })
            }

            val known = withContext(Dispatchers.IO) { store.all() }

            baseGroups = live.map { group ->
                group.copy(proxies = group.proxies.map {
                    it.copy(delay = DelayStore.shown(it.delay, known[it.name]))
                })
            }

            autoCheck(context, live, known, canCheck = running || preview)

            _state.update {
                it.copy(
                    groups = withFavorites(baseGroups),
                    mode = mode,
                    modeSwitch = modeSwitch,
                    running = running,
                    canCheck = running || preview,
                    loaded = true,
                )
            }

            // For the next start after the process is gone (D-391). Not an empty list, which a service
            // that did not answer gives too, nor one sorted by delay: the sort is not kept, and the live
            // list after a start would reorder the cards.
            if (profile != null && baseGroups.isNotEmpty() && _state.value.sort == ProxySort.Default) {
                val groups = baseGroups
                val shown = _state.value

                withContext(disk) {
                    val text = encodeList(groups, shown)

                    if (saved != profile to text.hashCode()) {
                        saved = profile to text.hashCode()

                        ProxyListStore(context).save(profile, text)
                    }
                }
            }
        }
    }

    private suspend fun liveGroups(sort: ProxySort): List<ProxyGroupState> {
        // В режиме Direct ядро отдаёт пустой список групп (F-29) — это не ошибка,
        // а состояние: выбирать нечего, весь трафик идёт напрямую.
        val names = runCatching {
            withClash { queryProxyGroupNames(false) }
        }.getOrDefault(emptyList())

        // The groups in parallel, a few at a time (D-398): one after another, 1 000 nodes in 30 groups took
        // 200–250 ms on the emulator, each call bound by its own nodes (F-391). Not all at once: the replies
        // share the app's Binder buffer of about 1 MB, and one that did not fit is asked again alone below.
        val fetched = coroutineScope {
            names.map { name ->
                async { runCatching { withClash(groupCalls) { queryProxyGroup(name, sort) } }.getOrNull() }
            }.awaitAll()
        }

        return names.zip(fetched).mapNotNull { (name, found) ->
            val group = found ?: runCatching {
                withClash { queryProxyGroup(name, sort) }
            }.getOrNull() ?: return@mapNotNull null

            ProxyGroupState(
                name = name,
                now = group.now,
                icon = group.icon,
                // В GLOBAL ядро кладёт первыми DIRECT и REJECT (F-153): новичок жмёт на них, и
                // интернет пропадает, а замер их не меряет (D-143). Выбранный остаётся виден.
                proxies = group.proxies
                    .filterNot { name == "GLOBAL" && it.type in BUILTIN_PROXY_TYPES && it.name != group.now }
                    .map { NodeState(it.name, it.type, DelayStore.ofCore(it.delay, it.tested), tested = it.tested) },
                // Живое ядро зовёт тип группы «Selector», файл профиля — «select».
                selectable = group.type.equals("selector", ignoreCase = true),
            )
        }
    }

    private suspend fun storedGroups(): List<ProxyGroupState> {
        val groups = runCatching { withClash { queryProfileGroups() } }.getOrDefault(emptyList())

        return groups.map { group ->
            ProxyGroupState(
                name = group.name,
                now = group.now,
                icon = group.icon,
                proxies = group.proxies.map { NodeState(name = it) },
                selectable = group.type.equals("select", ignoreCase = true),
            )
        }
    }

    /** Сортировка по задержке имеет смысл только при работающем ядре: замеров иначе нет. */
    fun setSort(context: Context, sort: ProxySort) {
        _state.update { it.copy(sort = sort) }

        refresh(context)
    }

    /**
     * Звёздочка: узел уезжает наверх своей группы и остаётся там между запусками.
     * Порядок внутри избранного — тот же, что задаёт сортировка.
     */
    fun toggleFavorite(context: Context, name: String) {
        viewModelScope.launch {
            favorites = withContext(Dispatchers.IO) {
                val store = SettingsStore(context)
                val updated = if (name in store.favorites) store.favorites - name
                else store.favorites + name

                store.favorites = updated

                updated
            }

            _state.update { it.copy(groups = withFavorites(baseGroups)) }
        }
    }

    /**
     * Замер идёт сам, без кнопки: человеку нужны цифры, а не повод их запросить.
     *
     * Меряем, когда по группе есть неизвестные узлы или её замеры сняты до смены сети.
     * Попыток не больше трёх подряд: первая после подъёма туннеля часто уходит в
     * пустоту — ядро ещё встаёт. Прежние значения при этом остаются на экране.
     *
     * [groups] carry the core's own values, [known] is the store: which of the attempts are
     * worth making is decided by [DelayStore.recheck].
     */
    private fun autoCheck(
        context: Context,
        groups: List<ProxyGroupState>,
        known: Map<String, Int>,
        canCheck: Boolean,
    ) {
        val now = System.currentTimeMillis()

        val pending = groups.filter { group ->
            val tried = attempts[group.name] ?: 0

            tried < MAX_ATTEMPTS && DelayStore.recheck(
                tried,
                group.proxies.map { it.delay to known[it.name] },
                outdated = group.proxies.any { DelayStore.outdated(it.tested, networkChangedAt) },
                // REJECT never answers: its group would be checked on every opening. After the
                // round's first check its misses are fresh, and any of them is worth the second.
                silent = group.proxies.any {
                    it.type !in BUILTIN_PROXY_TYPES && if (tried == 0) {
                        DelayStore.silent(it.delay, known[it.name], it.tested, now, switchedAt + SETTLE)
                    } else {
                        it.delay == DelayStore.NO_ANSWER
                    }
                },
            )
        }

        if (pending.isEmpty() || _state.value.checking != null || autoJob?.isActive == true) return

        // Not the state: on the first reading it does not say yet that the core can measure,
        // and with the tunnel down the tab's own check did not start until the second.
        if (!canCheck) return

        // Туннель только что переключили — сеть ещё не устоялась, и замер вернёт
        // пустоту по всем узлам. Меряем, когда окно пройдёт: следующего обновления
        // может и не быть, пока человек не нажмёт что-нибудь. The same after a change of
        // network, with a longer window.
        val left = maxOf(switchedAt + SETTLE, networkChangedAt + NETWORK_SETTLE) - System.currentTimeMillis()

        if (left > 0) {
            autoJob = viewModelScope.launch {
                delay(left)

                refresh(context)
            }

            return
        }

        pending.forEach { attempts[it.name] = (attempts[it.name] ?: 0) + 1 }

        // Повторную попытку не гоним следом за неудачной, как и на главной: итог замера
        // сам зовёт обновление, и вторая попытка уходила через долю секунды. Два промаха
        // подряд за секунду — и узел получал «нет ответа», хотя туннель просто вставал.
        val retry = pending.any { (attempts[it.name] ?: 0) > 1 }

        autoJob = viewModelScope.launch {
            if (retry) delay(RETRY)

            val startedWith = Remote.broadcasts.clashRunning

            _state.update { it.copy(checking = pending.first().name) }

            pending.forEach { group -> checkLive(group.name) }

            val names = pending.map { it.name }.toSet()

            val measured = runCatching { liveGroups(_state.value.sort) }.getOrDefault(emptyList())

            // Туннель переключили прямо во время замера — его результат относится
            // к другой дороге, и записывать его нельзя.
            if (Remote.broadcasts.clashRunning == startedWith) {
                withContext(Dispatchers.IO) {
                    DelayStore(context).merge(
                        measured.filter { it.name in names }
                            .flatMap { it.proxies }
                            .associate { it.name to it.delay },
                    )
                }
            }

            _state.update { it.copy(checking = null) }

            refresh(context)
        }
    }

    /**
     * Избранные наверх, остальные — как пришли. Сортировка устойчивая, поэтому внутри
     * обеих частей сохраняется исходный порядок, и снятая звёздочка возвращает узел
     * на его место.
     */
    private fun withFavorites(groups: List<ProxyGroupState>): List<ProxyGroupState> =
        groups.map { group ->
            group.copy(
                proxies = group.proxies
                    .map { it.copy(favorite = it.name in favorites) }
                    .sortedByDescending { it.favorite },
            )
        }

    fun select(context: Context, group: String, name: String) {
        viewModelScope.launch {
            runCatching {
                withClash {
                    if (_state.value.running) {
                        patchSelector(group, name)
                    } else {
                        // Запоминаем: служба применит выбор при запуске туннеля.
                        patchStoredSelector(group, name)

                        // И сразу говорим ядру, если оно поднято ради замера, — иначе
                        // список показывал бы прежний выбор до перезапуска.
                        if (_state.value.canCheck) patchSelector(group, name)
                    }
                }
            }

            refresh(context)
        }
    }

    /**
     * Проверка задержки идёт по группе целиком: ядро опрашивает её участников и
     * складывает результат в их историю, откуда его и берёт следующий запрос.
     */
    fun healthCheck(context: Context, group: String) {
        viewModelScope.launch {
            measure(context, group)

            refresh(context)
        }
    }

    /**
     * «Переключиться на самый быстрый» из карточки «нет связи» на главной: перемерить группу
     * и выбрать узел с наименьшей задержкой. DIRECT и REJECT не в счёт: «связь есть» мимо
     * VPN — это не починка. Группы вроде «⚡ AUTO» в счёт: их задержка — задержка их узла.
     *
     * @return выбранный узел с задержкой в мс; `null` — не ответил ни один.
     */
    suspend fun fastest(context: Context, group: String): NodeState? {
        val best = measure(context, group)
            .filter { it.type !in BUILTIN_PROXY_TYPES && DelayStore.ofCore(it.delay) > 0 }
            .minByOrNull { it.delay }

        if (best != null) runCatching { withClash { patchSelector(group, best.name) } }

        refresh(context)

        return best
    }

    /** Замер группы с записью итога; отдаёт её узлы с тем, что намерило ядро. */
    private suspend fun measure(context: Context, group: String): List<NodeState> {
        // The tab does not follow a check by hand with its own, unless nobody answered at all (D-282).
        attempts[group] = 1

        _state.update { it.copy(checking = group) }

        checkLive(group)

        // The result is stored as it is: a node that did not answer is marked at once (D-282).
        val measured = runCatching { liveGroups(_state.value.sort) }.getOrDefault(emptyList())
            .firstOrNull { it.name == group }?.proxies.orEmpty()

        withContext(Dispatchers.IO) {
            DelayStore(context).merge(measured.associate { it.name to it.delay })
        }

        _state.update { it.copy(checking = null) }

        return measured
    }

    /**
     * The check of a group, with the result of every node shown the moment the core has tested it —
     * a delay or "нет ответа" — instead of after the last one (D-282): on a long list where most
     * nodes are silent the check takes about a minute (F-270). The core asks the nodes in the order
     * of the profile, ten at a time (`adapter/provider/healthcheck.go`), so the top of the list is
     * done within seconds.
     *
     * The nodes still to be asked are not marked: a check often joins one the core is already
     * running, and which of the results belong to that one cannot be told (F-276).
     */
    private suspend fun checkLive(group: String) = coroutineScope {
        val watch = launch {
            while (true) {
                delay(WATCH)

                val results = runCatching { withClash { queryProxyGroup(group, ProxySort.Default) } }
                    .getOrNull()?.proxies.orEmpty().associate { it.name to DelayStore.ofCore(it.delay, it.tested) }

                baseGroups = overlay(baseGroups, results)

                _state.update { it.copy(groups = withFavorites(baseGroups)) }
            }
        }

        runCatching { withClash { healthCheck(group) } }

        watch.cancel()
    }

    /**
     * Режим меняется сессионным оверрайдом, как это делает CMFA: профиль провайдера
     * при этом не переписывается (инвариант 1), и после перезапуска всё вернётся
     * к тому, что задал профиль.
     */
    fun setMode(context: Context, mode: TunnelState.Mode) {
        // Чип отмечается сразу, не дожидаясь службы: список групп следом перечитывается
        // целиком, и ждать его ради отметки незачем (D-113).
        _state.update { it.copy(mode = mode) }

        viewModelScope.launch {
            runCatching { withClash { setMode(mode) } }

            refresh(context)
        }
    }

    private companion object {
        /** How many group calls of [liveGroups] are in the service at once. */
        val groupCalls = Dispatchers.IO.limitedParallelism(4)

        /** Больше трёх попыток подряд — значит узлы действительно не отвечают. */
        const val MAX_ATTEMPTS = 3

        /** Сколько ждать после переключения туннеля, прежде чем мерить. */
        const val SETTLE = 2500L

        /**
         * How long after a change of network before the tab checks by itself. On the phone a
         * request through the node was back to its usual time 5–8 s after Wi-Fi gave way to the
         * mobile network, and in under 2 s on the way back (F-285); a check made earlier marks
         * working nodes as silent.
         */
        const val NETWORK_SETTLE = 10_000L

        /** Пауза перед повторным автозамером после неудачного — как на главной. */
        const val RETRY = 5000L

        /** How often the list picks up new delays while a check is running. */
        const val WATCH = 1000L
    }
}
