package com.github.kr328.clash.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.StopNotice
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.proxy.NodeState
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Одна видимая группа профиля.
 *
 * @param options имена участников. Ровно два — показываем переключатель, больше —
 * строку с переходом на выбор (D-24): группа в mihomo это выбор из списка,
 * а не «включено/выключено».
 */
@Immutable
data class GroupState(
    val name: String,
    val now: String,
    val options: List<String>,
    val delay: Int,
)

@Immutable
data class HomeState(
    val running: Boolean = false,
    val mode: TunnelState.Mode? = null,
    val profile: Profile? = null,
    val groups: List<GroupState> = emptyList(),
    /** Данные ещё не читались ни разу — не показываем ни нулей, ни пустых карточек. */
    val loaded: Boolean = false,
    /**
     * Отвечает ли узел. `null` — ещё не проверяли.
     *
     * Туннель может быть поднят, а связи не быть: оператор блокирует адрес узла,
     * и каждый дозвон кончается таймаутом. Без этой проверки главная писала бы
     * «Подключено», пока не работает ничего (D-58).
     */
    val linkOk: Boolean? = null,
    val linkError: String = "",
    val checking: Boolean = false,
    /**
     * Нажатие на кнопку уже отправлено, а служба ещё не ответила. Пока так, кнопка
     * не принимает нажатий: частые нажатия подряд гоняли службу по кругу, а экран
     * успевал показать и «нет связи», и чужой замер.
     */
    val busy: Boolean = false,
    /** Включена системная блокировка без VPN, и часть приложений из-за неё без сети (F-110). */
    val lockdownOffline: Boolean = false,
    /** «Переключиться на самый быстрый»: группа перемеряется, кнопка ждёт. */
    val switching: Boolean = false,
    /** Самый быстрый искали, а не ответил ни один узел: дело в сети, а не в узле. */
    val noneAnswered: Boolean = false,
    /**
     * VPN остановился не по команде, и туннель с тех пор не поднимали (D-227). Причину служба
     * кладёт на диск: приложение могли открыть и после того, как включить не вышло с плитки.
     */
    val stopFailure: Failure? = null,
)

/**
 * Цифры, которые меняются каждую секунду, — теперь это только время работы: карточка
 * скорости убрана (D-95).
 *
 * Отдельно от [HomeState] они лежат ради процессора (D-77). Пока они были в общем
 * состоянии, каждая секунда пересобирала всю главную: шапку, объявление, обе карточки,
 * кнопку. Теперь секундное обновление трогает ровно то место, которое его показывает, —
 * а остальной экран не знает, что что-то произошло.
 */
@Immutable
data class HomeMetrics(
    val uptimeMs: Long = 0,
)

/**
 * Данные главной. Всё, что нужно экрану, собирается здесь и приходит одним состоянием,
 * чтобы сам экран оставался разметкой без запросов к службе.
 */
class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(HomeMetrics())
    val metrics: StateFlow<HomeMetrics> = _metrics.asStateFlow()

    /** Когда последний раз проверяли связь: чаще раза в минуту сами не лезем. */
    private var lastCheck = 0L
    /** Узел, к которому относится последний вывод о связи. */
    private var lastCheckedNode = ""

    /**
     * Узел, выбранный на главной, пока служба его не применила (D-116): имя и задержка.
     * До [pendingUntil] обновление, пришедшее раньше выбора, не возвращает прежний узел —
     * иначе плашка мигнула бы старым названием.
     */
    private var pendingNode: Pair<String, Int>? = null
    private var pendingUntil = 0L
    private var polling: Job? = null

    /**
     * Когда поднялся туннель. Читается один раз на сеанс: значение лежит в настройках
     * службы, а это чужой процесс, и каждое чтение — вызов через провайдер. Раз в секунду
     * такой вызов делать незачем, время работы считается вычитанием (D-77).
     */
    private var startedAt = 0L

    /**
     * Сколько раз пробовали замерить группу при нынешнем состоянии туннеля.
     * Не флаг «уже мерили»: первый замер сразу после подъёма часто уходит в пустоту —
     * туннель ещё встаёт, ответа нет, и задержка навсегда оставалась прочерком.
     */
    private val checkAttempts = mutableMapOf<String, Int>()

    /** Состояние туннеля на прошлом обновлении: по его смене замеры устаревают. */
    private var lastRunning: Boolean? = null

    /** Каким состоянием кнопка ждёт ответа службы и с какого момента. */
    private var requested: Boolean? = null
    private var requestedAt = 0L

    /** Когда туннель последний раз менял состояние. */
    private var switchedAt = 0L

    /** Промахи проверки связи подряд: одна неудача ничего не значит. */
    private var linkMisses = 0

    /** Основная группа и узел, уже записанные в [DelayStore.main]: не писать на диск одно и то же. */
    private var lastMain: Pair<String, String>? = null

    /** One refresh at a time, see [RefreshGate]. Created on first use: it needs a context. */
    private var gate: RefreshGate? = null

    /** Опрос скорости идёт, только пока экран виден и туннель поднят. */
    fun refresh(context: Context) {
        val app = context.applicationContext

        (gate ?: RefreshGate(viewModelScope) { answered -> load(app, answered) }.also { gate = it }).request()
    }

    private suspend fun load(context: Context, answered: () -> Unit) {
        coroutineScope {
            val running = Remote.broadcasts.clashRunning

            // Туннель подняли или опустили — прежние замеры относятся к другой дороге,
            // и задержку надо снять заново, иначе в строке узла висит прочерк.
            val switched = lastRunning != null && lastRunning != running

            if (switched) {
                checkAttempts.clear()

                switchedAt = System.currentTimeMillis()

                linkMisses = 0

                // Первая проверка связи — сразу после окна «сеть устаканивается», а не через
                // минуту после прошлой: переподключились за минуту — и «Подключено» висело
                // без проверки, даже когда узел мёртв.
                lastCheck = 0
            }

            // Экран открыли, когда туннель уже поднимался — из шторки, после обновления
            // приложения (D-90). Смены состояния мы не видели, и окно «сеть
            // устаканивается» пропускалось: замер уходил в ещё не готовый туннель и
            // записывал «нет ответа». Отсчитываем его от подъёма туннеля (D-110).
            if (lastRunning == null && running) {
                switchedAt = withContext(Dispatchers.IO) { ServiceStore(context).tunnelStartedAt }

                settleThenRefresh(context)
            }

            lastRunning = running


            // Ядру нужно время, чтобы отдать группы после подъёма или остановки.
            // Один повторный запрос — и строка узла показывает настоящее значение.
            if (switched) {
                viewModelScope.launch {
                    delay(1500)

                    refresh(context)
                }
            }

            // Подписка, режим и прошлые замеры друг от друга не зависят, а каждый
            // запрос — переход в чужой процесс. Подряд они складывались в паузу, которую
            // видно на запуске: заставка висит, пока не приедет последний (D-77).
            val profileAsync = async {
                runCatching { withProfile { queryActive() } }.getOrNull().also { answered() }
            }

            val modeAsync = async {
                if (running) runCatching { withClash { queryTunnelState().mode } }.getOrNull()
                else null
            }

            // Задержка узла берётся из общего хранилища замеров (D-72): под кнопкой
            // должно стоять последнее известное значение, а не прочерк на время замера.
            // The last delay the node answered with: one unanswered check of the list is not
            // "нет ответа" here — that is said by this screen's own check of the connection (D-282).
            val knownAsync = async(Dispatchers.IO) { DelayStore(context).answers() }

            val lockdownAsync = async(Dispatchers.IO) { running && ServiceStore(context).lockdownOffline }

            val stopAsync = async(Dispatchers.IO) { if (running) "" else ServiceStore(context).stopReason }

            // Первый показ при выключенном туннеле — без ядра (аудит, perf 5.2): загрузку профиля
            // в ядро на слабом телефоне ждали дольше, чем держится заставка, и за ней мелькали
            // заглушки. Узел под кнопкой — последний показанный; ядро ниже его уточнит.
            if (!running && !_state.value.loaded) {
                val profile = profileAsync.await()

                val last = profile?.let {
                    withContext(Dispatchers.IO) {
                        val store = DelayStore(context)

                        store.switchTo(it.uuid.toString())

                        store.main?.let { (group, node) ->
                            GroupState(group, node, emptyList(), store.answers()[node] ?: DelayStore.UNKNOWN)
                        }
                    }
                }

                if (last != null) {
                    val stop = stopAsync.await().takeIf { reason -> reason.isNotEmpty() }?.let(Failure::of)

                    _state.update {
                        if (it.loaded) it
                        else it.copy(running = false, profile = profile, groups = listOf(last), loaded = true, stopFailure = stop)
                    }
                }
            }

            // Home shows no subscription and one has become active — the first one added: it goes
            // on screen now, with the power button. Its node comes after the core has loaded the
            // config below, which with the built-in template takes seconds: the rule sets are
            // downloaded first (D-229). All that time home said «Подписка не выбрана».
            if (_state.value.loaded && _state.value.profile == null) {
                profileAsync.await()?.let { added ->
                    _state.update { if (it.profile == null) it.copy(profile = added) else it }
                }
            }

            // При выключенном туннеле поднимаем конфиг в ядре без туннеля (D-63):
            // тогда на главной видно выбранный узел и его задержку ещё до подключения.
            val preview = !running &&
                runCatching { withClash { loadForPreview() } }.getOrDefault(false)

            val live = if (running || preview) loadGroups() else emptyList()

            val profile = profileAsync.await()

            val mode = modeAsync.await()

            // Подписку сменили — прежние замеры относятся к чужим узлам (D-81).
            profile?.let {
                withContext(Dispatchers.IO) { DelayStore(context).switchTo(it.uuid.toString()) }
            }

            val known = knownAsync.await()

            val groups = live.map { it.copy(delay = DelayStore.shown(it.delay, known[it.now])) }

            // Запоминаем, что показали, — для первого показа в следующий запуск (см. выше).
            groups.firstOrNull()?.let { it.name to it.now }?.takeIf { it != lastMain }?.let {
                lastMain = it

                withContext(Dispatchers.IO) { DelayStore(context).main = it }
            }

            val pending = pendingNode?.takeIf { System.currentTimeMillis() < pendingUntil }
            val confirmed = pending == null || groups.firstOrNull()?.now == pending.first

            if (confirmed) pendingNode = null

            val shown = if (confirmed) groups else groups.mapIndexed { i, group ->
                if (i == 0) group.copy(now = pending!!.first, delay = pending.second) else group
            }

            // Узел могли сменить на вкладке «Прокси» — тогда прежний вывод о связи
            // относится уже не к нему, и держать карточку «узел не отвечает» нельзя.
            val nodeChanged = groups.firstOrNull()?.now.orEmpty() != lastCheckedNode

            if (nodeChanged) lastCheck = 0

            // Задержка на главной не должна висеть прочерком: ядро отдаёт «неизвестно»,
            // пока по группе не было ни одного замера (F-93). Меряем сами, до трёх раз
            // на группу за сеанс туннеля.
            groups.firstOrNull()
                ?.takeIf { it.delay <= DelayStore.UNKNOWN }
                ?.takeIf { System.currentTimeMillis() - switchedAt > SETTLE }
                ?.takeIf { (checkAttempts[it.name] ?: 0) < MAX_CHECK_ATTEMPTS }
                ?.let { group ->
                    val attempt = (checkAttempts[group.name] ?: 0) + 1

                    checkAttempts[group.name] = attempt

                    viewModelScope.launch {
                        // Повторную попытку не гоним следом за неудачной: узел мог
                        // не ответить потому, что туннель в этот момент вставал.
                        // Три попытки подряд сгорали за секунду, и дальше оставалось
                        // только «нет ответа».
                        if (attempt > 1) delay(RETRY)

                        runCatching { withClash { healthCheck(group.name) } }

                        refresh(context)
                    }
                }

            // Служба ответила тем, чего ждали, — кнопка свободна в том же кадре, где
            // пошла волна (D-93). Раньше это ловил только секундный опрос, и кнопка ещё
            // до секунды крутила кольцо ожидания поверх уже новой карточки.
            val answered = requested != null && requested == running

            if (answered) requested = null

            _state.update {
                it.copy(
                    busy = if (answered) false else it.busy,
                    running = running,
                    mode = mode,
                    profile = profile,
                    // Сразу после остановки туннеля ядро ещё не отдаёт группы, и строка
                    // узла на секунду срывалась на заглушку. Пока подписка на месте,
                    // держим прежний выбор: он всё равно не менялся.
                    groups = shown.ifEmpty { if (profile != null) it.groups else emptyList() },
                    loaded = true,
                    // Туннель опустили или сменили узел — прежний вывод о связи
                    // больше ничего не значит.
                    linkOk = if (running && !nodeChanged) it.linkOk else null,
                    linkError = if (running && !nodeChanged) it.linkError else "",
                    noneAnswered = running && !nodeChanged && it.noneAnswered,
                    lockdownOffline = lockdownAsync.await(),
                    stopFailure = stopAsync.await().takeIf { reason -> reason.isNotEmpty() }?.let(Failure::of),
                )
            }

            // Опрос идёт только при поднятом туннеле, и без обнуления на карточках
            // остались бы цифры прошлого сеанса.
            if (!running) _metrics.value = HomeMetrics()
        }
    }

    /**
     * Окно после подъёма туннеля ещё не прошло — обновиться, когда пройдёт: замер сейчас
     * пропущен, а следующего обновления может и не случиться, пока человек не нажмёт что-то.
     */
    private fun settleThenRefresh(context: Context) {
        val left = SETTLE - (System.currentTimeMillis() - switchedAt)

        if (left <= 0) return

        viewModelScope.launch {
            delay(left)

            refresh(context)
        }
    }

    /**
     * Один замер через группу, которой идёт основной трафик. Замер по **группе**, а не по
     * узлу: ядро само разрешит её в текущий выбор, и проверять будем именно то, чем
     * пользуется человек.
     */
    fun checkLink() {
        val group = _state.value.groups.firstOrNull()?.name ?: return

        if (_state.value.checking) return

        // Сразу после переключения проверять нечего: туннель ещё встаёт, и отказ
        // здесь означал бы только это. Секундный опрос может позвать проверку раньше
        // первого обновления экрана — тогда отсчёт от подъёма туннеля (D-110).
        if (System.currentTimeMillis() - maxOf(switchedAt, startedAt) < SETTLE) return

        lastCheck = System.currentTimeMillis()
        lastCheckedNode = _state.value.groups.firstOrNull()?.now.orEmpty()

        viewModelScope.launch {
            val startedWith = Remote.broadcasts.clashRunning

            _state.update { it.copy(checking = true) }

            val result = runCatching {
                withContext(Dispatchers.IO) { withClash { probeProxy(group) } }
            }.getOrNull()

            // Туннель переключили во время проверки — её вывод уже не про него.
            if (Remote.broadcasts.clashRunning != startedWith) {
                _state.update { it.copy(checking = false) }

                return@launch
            }

            val ok = result?.ok

            linkMisses = if (ok == false) linkMisses + 1 else 0

            // Первый промах перепроверяем через несколько секунд, а не через минуту: иначе
            // карточка «нет связи» при мёртвом узле появлялась только на второй минуте.
            if (ok == false && linkMisses < MISSES_BEFORE_FAILURE) {
                lastCheck = System.currentTimeMillis() - CHECK_INTERVAL + RETRY
            }

            _state.update {
                it.copy(
                    checking = false,
                    // Карточка «нет связи» двигает весь экран, поэтому показываем её
                    // только когда две проверки подряд сказали одно и то же.
                    linkOk = if (ok == false && linkMisses < MISSES_BEFORE_FAILURE) it.linkOk else ok,
                    linkError = result?.error.orEmpty(),
                    noneAnswered = ok != true && it.noneAnswered,
                )
            }
        }
    }

    /**
     * «Переключиться на самый быстрый» из карточки «нет связи». Замер и выбор делает вкладка
     * «Прокси» ([pick]): узлы, задержки и выбор живут у неё, главная только ждёт итога и
     * проверяет связь заново, уже через новый узел.
     */
    fun switchToFastest(pick: suspend () -> NodeState?) {
        if (_state.value.switching) return

        _state.update { it.copy(switching = true, noneAnswered = false) }

        viewModelScope.launch {
            val best = pick()

            if (best != null) nodeChanged(best.name, best.delay)

            _state.update { it.copy(switching = false, noneAnswered = best == null) }
        }
    }

    /** Карточку «VPN не включился» закрыли крестиком: причина прочитана (D-227). */
    fun dismissStop(context: Context) {
        _state.update { it.copy(stopFailure = null) }

        viewModelScope.launch(Dispatchers.IO) { StopNotice.clear(context) }
    }

    /** Кнопку нажали: до ответа службы она больше нажатий не принимает. */
    fun markToggling(target: Boolean) {
        requested = target
        requestedAt = System.currentTimeMillis()

        _state.update { it.copy(busy = true) }
    }

    /** Переключение не состоялось: нет подписки, отказ провайдера, отказ в разрешении. */
    fun cancelToggling() {
        requested = null

        _state.update { it.copy(busy = false) }
    }

    /** Экран ушёл с глаз — опрашивать службу раз в секунду незачем (D-77). */
    fun stopTrafficPolling() {
        polling?.cancel()
        polling = null
    }

    fun startTrafficPolling(context: Context) {
        // Вью-модель переживает пересоздание активити, а LaunchedEffect после него
        // сработает заново: без этой проверки опросов станет два.
        if (polling?.isActive == true) return

        polling = viewModelScope.launch {
            while (isActive) {
                // Служба ответила ожидаемым состоянием — кнопка снова живая. Ответа нет
                // дольше разумного (отказ в разрешении, ошибка запуска) — тоже отпускаем.
                // Проверяем здесь, а не в `refresh`: его зовут не на каждое изменение,
                // и кнопка оставалась серой дольше, чем нужно.
                if (requested != null) {
                    val settled = requested == Remote.broadcasts.clashRunning
                    val waited = System.currentTimeMillis() - requestedAt > TOGGLE_TIMEOUT

                    if (settled || waited) {
                        requested = null

                        _state.update { it.copy(busy = false) }

                        refresh(context)
                    }
                }

                if (_state.value.running) {
                    if (startedAt == 0L) {
                        startedAt = withContext(Dispatchers.IO) { ServiceStore(context).tunnelStartedAt }
                    }

                    // Трафик у службы больше не спрашиваем: он был нужен только карточке
                    // скорости (D-95), а это обращение в чужой процесс каждую секунду.
                    _metrics.value = HomeMetrics(
                        // Ровно до секунды: доли всё равно не показываются, а с ними
                        // значение менялось бы на каждом такте, даже когда часы стоят.
                        uptimeMs = if (startedAt > 0) {
                            (System.currentTimeMillis() - startedAt) / 1000 * 1000
                        } else 0,
                    )
                } else {
                    startedAt = 0L

                    // Туннель опустили — история уже не про него.
                    _metrics.value = HomeMetrics()
                }

                val state = _state.value

                if (state.running && state.groups.isNotEmpty() &&
                    System.currentTimeMillis() - lastCheck > CHECK_INTERVAL
                ) {
                    checkLink()
                }

                delay(1000)
            }
        }
    }

    /**
     * Узел сменили с главной (D-116) — прежний вывод «не отвечает» относится уже не к нему.
     * [name] — выбранный узел, [delay] — его задержка; у группы в плашке окажется её узел.
     * Обнуляем и время проверки, чтобы следующая случилась сразу, а не через минуту:
     * человек как раз ждёт, помогло ли переключение. Сам выбор делает вкладка «Прокси»,
     * а главная обновится вслед за её списком.
     */
    fun nodeChanged(name: String, delay: Int) {
        lastCheck = 0

        // Промахи прежнего узла новому не в счёт: иначе первая же неудача сразу после
        // переключения, пока рвутся старые соединения, возвращала карточку.
        linkMisses = 0

        _state.update { it.copy(linkOk = null, linkError = "", noneAnswered = false) }

        // Название — сразу, пока плашка ещё под сворачивающейся панелью: к концу анимации
        // в ней уже новый узел, и подмены на глазах нет. Группы автовыбора в профиле
        // обычно скрыты, и их узел знает только ядро — один быстрый запрос, задолго до
        // конца сворачивания.
        viewModelScope.launch {
            val (now, shown) = resolveNode(name)?.takeIf { it.second > 0 || delay <= 0 } ?: (name to delay)

            pendingNode = now to shown
            pendingUntil = System.currentTimeMillis() + PENDING_MS

            _state.update { state ->
                state.copy(
                    groups = state.groups.mapIndexed { i, group ->
                        if (i == 0) group.copy(now = now, delay = shown) else group
                    },
                )
            }
        }
    }

    private suspend fun loadGroups(): List<GroupState> {
        // Скрытые группы ядро уже отфильтровало (F-29), свой фильтр не нужен.
        val names = runCatching {
            withClash { queryProxyGroupNames(true) }
        }.getOrDefault(emptyList())

        // Группы спрашиваем разом, а не по очереди: у профиля их бывает несколько,
        // и каждая — отдельный заход в службу.
        return coroutineScope {
            names.map { name ->
                async {
                    val group = runCatching {
                        withClash { queryProxyGroup(name, ProxySort.Default) }
                    }.getOrNull() ?: return@async null

                    val current = group.proxies.firstOrNull { it.name == group.now }
                    val own = current?.delay ?: 0

                    val node = if (current?.isGroup == true) resolveNode(group.now) else null

                    GroupState(
                        name = name,
                        now = node?.first ?: group.now,
                        options = group.proxies.map { it.name },
                        delay = node?.second ?: own,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Выбор группы может указывать не на узел, а на другую группу: у подписок без своего
     * конфига «Основной профиль» выбирает «⚡ AUTO», а узел держит уже тот (D-81).
     *
     * Под кнопкой на главной должно стоять имя узла: «⚡ AUTO» человеку не говорит
     * ни о стране, ни о том, куда он подключён. Спускаемся до настоящего узла и берём
     * его задержку. Глубже трёх шагов не ходим: вложенность больше — это ошибка конфига,
     * а не случай, ради которого стоит ходить в службу.
     *
     * @return имя узла и его задержка либо `null`, если [name] и есть узел.
     */
    private suspend fun resolveNode(name: String, depth: Int = 0): Pair<String, Int>? {
        if (depth >= MAX_GROUP_DEPTH) return null

        val group = runCatching {
            withClash { queryProxyGroup(name, ProxySort.Default) }
        }.getOrNull()?.takeIf { it.proxies.isNotEmpty() } ?: return null

        val current = group.proxies.firstOrNull { it.name == group.now }
        val delay = current?.delay ?: 0

        // Вглубь — только если выбрана группа: на запрос обычного узла как группы ядро отвечает
        // предупреждением, и при каждом обновлении главной ими забивался самописец (F-162).
        val deeper = if (current?.isGroup == true) resolveNode(group.now, depth + 1) else null

        return deeper ?: (group.now to delay)
    }

    private companion object {
        /** Сами проверяем не чаще раза в минуту: это настоящий запрос в сеть. */
        const val CHECK_INTERVAL = 60_000L

        /** Больше трёх попыток подряд — значит узел действительно не отвечает. */
        const val MAX_CHECK_ATTEMPTS = 3

        /** Сколько ждать после переключения туннеля, прежде чем что-то проверять. */
        const val SETTLE = 2500L

        /** Пауза перед повторным замером задержки после неудачного. */
        const val RETRY = 5000L

        /** Насколько глубоко разрешаем цепочку «группа выбирает группу». */
        const val MAX_GROUP_DEPTH = 3

        /** Сколько ждать, пока служба применит выбор с главной (D-116). */
        const val PENDING_MS = 3000L

        /** Сколько неудач подряд считается настоящей потерей связи. */
        const val MISSES_BEFORE_FAILURE = 2

        /** Дольше этого службу не ждём: кнопка не должна залипать навсегда. */
        const val TOGGLE_TIMEOUT = 15_000L
    }
}
