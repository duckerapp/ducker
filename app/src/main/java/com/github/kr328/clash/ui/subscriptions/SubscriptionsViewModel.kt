package com.github.kr328.clash.ui.subscriptions

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import java.util.concurrent.TimeUnit
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.config.ConfigEdits
import com.github.kr328.clash.ui.config.fileName
import com.github.kr328.clash.ui.config.readFile
import com.github.kr328.clash.log.LogScrubber
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendOverrideChanged
import com.github.kr328.clash.service.store.UserRulesStore
import com.github.kr328.clash.service.model.UserRule
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.SubscriptionHeaders
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.store.AppStore
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.util.PackageFilter
import com.github.kr328.clash.ui.rules.Bypass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class SubscriptionsState(
    val profiles: List<Profile> = emptyList(),
    /** Какая подписка сейчас обновляется — на её карточке показываем это. */
    val updating: UUID? = null,
    val sendHwid: Boolean = true,
    /** Почему не удалось обновить подписку. Пусто — прошлая попытка прошла или её не было. */
    val updateError: String = "",
    /** Какую подписку не удалось обновить: её имя — в карточке ошибки, «Не удалось» — на её кнопке. */
    val updateErrorFor: UUID? = null,
    /** Что именно не удалось — по этому карточка выбирает заголовок и кнопку (D-227). */
    val updateFailure: Failure? = null,
    val hwid: String = "",
    val loaded: Boolean = false,
    /** Открыт ли диалог добавления и что в нём происходит. */
    val adding: Boolean = false,
    val input: String = "",
    val addError: String = "",
    val busy: Boolean = false,
    /** Какую подписку правим. Пусто — окно правки закрыто. */
    val editing: UUID? = null,
    val editName: String = "",
    /** Название от провайдера — подсказка в пустом поле имени: оно вернётся, если стереть своё. */
    val editTitle: String = "",
    val editLink: String = "",
    val editInterval: Long = 0,
    val editError: String = "",
    val editBusy: Boolean = false,
    /** Какую подписку собираемся удалить. Пусто — вопрос не задан. */
    val deleting: UUID? = null,
    /** Имя остаётся и после закрытия: панель ещё уезжает вниз, и заголовок не должен пустеть. */
    val deletingName: String = "",
    val deleteBusy: Boolean = false,
    /** Сколько своих правил у каждой подписки — значок на карточке (D-123). */
    val ruleCounts: Map<UUID, Int> = emptyMap(),
    /** Подписка, чьи правила открыты; `null` — экран правил закрыт. */
    val rulesFor: UUID? = null,
    val rulesName: String = "",
    val rules: List<UserRule> = emptyList(),
    /** Кто при этой подписке идёт мимо туннеля; `null` — ещё не узнали у службы (D-135). */
    val rulesBypass: Bypass? = null,
    /** Обновление подписки с правленым вручную конфигом ждёт подтверждения (D-125). */
    val confirmUpdate: UUID? = null,
    /** Телевизор: что на месте QR в панели «+» (D-162). На телефоне и пока неизвестно — `null`. */
    val receive: Receive? = null,
    /**
     * Эта подписка загружена и выбрана — пора на главную, к кнопке питания: на телевизоре её
     * прислали с телефона (D-163), на телефоне добавили ссылкой со страницы провайдера (D-211).
     */
    val toPower: UUID? = null,
    /** Панель «+» открыта ссылкой со страницы провайдера (D-211): добавленное сразу становится активным. */
    val linked: Boolean = false,
    /** Доля загрузки добавляемой подписки, 0..1, — заливка кнопки «Добавить» (D-212). */
    val addProgress: Float? = null,
    /** Телефон: отсканирован код телевизора — открыта панель отправки (D-162). */
    val sendTo: TvLink? = null,
    val sendChoice: UUID? = null,
    val sendError: String = "",
    val sendBusy: Boolean = false,
    /** Телевизор подписку принял: панель говорит об этом, пока её не закроют. */
    val sent: Boolean = false,
)

/** Приём подписки с телефона (D-162): что панель «+» на телевизоре показывает на месте QR. */
sealed interface Receive {
    /** Ждём телефон: код для QR. */
    data class Waiting(val link: String) : Receive

    /** Телевизор не в домашней сети: ни Wi-Fi, ни кабеля. */
    data object NoNetwork : Receive

    /** Сеанс закрыт без подписки: вышло время, пришло много чужих сообщений или экран уходил. */
    data object Expired : Receive

    /** Подписка получена и загружается. */
    data object Received : Receive
}

/**
 * Третья вкладка: подписки и настройки (этап 5).
 *
 * Идентификатор устройства показывается здесь же (D-25): человеку нужно видеть, что именно
 * уходит провайдеру, и уметь это выключить (D-36).
 */
/** Как часто подписка обновляется сама. Восемь часов — три раза в сутки. */
private val UPDATE_INTERVAL = TimeUnit.HOURS.toMillis(8)

class SubscriptionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionsState())
    val state: StateFlow<SubscriptionsState> = _state.asStateFlow()

    fun refresh(context: Context) {
        viewModelScope.launch {
            val order = withContext(Dispatchers.IO) { AppStore(context).profileOrder }
            val profiles = runCatching { withProfile { queryAll() } }.getOrDefault(emptyList())
                .inOrder(order.split(',')) { it.uuid.toString() }

            val sendHwid = withContext(Dispatchers.IO) { ServiceStore(context).sendHwid }
            val counts = withContext(Dispatchers.IO) {
                val rules = UserRulesStore(context)

                profiles.associate { it.uuid to rules.read(it.uuid).size }
            }

            _state.update {
                it.copy(
                    profiles = profiles,
                    ruleCounts = counts,
                    sendHwid = sendHwid,
                    hwid = SubscriptionHeaders.hwidOf(context),
                    loaded = true,
                )
            }
        }
    }

    fun showAdd(show: Boolean) {
        _state.update {
            it.copy(adding = show, input = if (show) it.input else "", addError = "", linked = show && it.linked)
        }
    }

    fun setInput(value: String) {
        _state.update { it.copy(input = value, addError = "") }
    }

    /**
     * Ссылка со страницы провайдера, `ducker://install-config?url=…` (D-211): то же, что вставить
     * её в поле, — скачивается она, только когда человек нажмёт «Добавить». Панель открывает
     * главный экран.
     */
    fun openLink(url: String) {
        if (_state.value.busy) return

        _state.update { it.copy(input = url, addError = "", linked = true) }
    }

    /**
     * Ссылка на подписку добавляется как обычный профиль по адресу, а набор ссылок
     * протоколов — через конвертацию в службе (D-17, D-19). Отличаем по схеме: всё, что
     * не http(s), скачивать неоткуда.
     */
    fun add(context: Context, fromPhone: Boolean = false) {
        val raw = _state.value.input.trim()

        if (raw.isEmpty()) return

        // Со страницы провайдера подписку добавляют, чтобы включить, — как присланную с телефона.
        val activate = fromPhone || _state.value.linked

        addJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, addError = "", addProgress = 0f) }

            val result = runCatching {
                val isUrl = raw.startsWith("http://", true) || raw.startsWith("https://", true)

                val uuid = withProfile {
                    if (isUrl) {
                        val name = defaultName(raw).ifEmpty { context.getString(R.string.subscription_default_name) }

                        create(Profile.Type.Url, name, raw).also { created ->
                            // Ядро создаёт профиль с interval = 0, то есть без автообновления,
                            // и человек об этом не узнает, пока узлы не протухнут. Ставим сами,
                            // до commit: патч правит ещё не импортированный профиль, поэтому
                            // лишней загрузки не будет.
                            patch(created, name, raw, UPDATE_INTERVAL, null)
                        }
                    } else {
                        // As a file: a long list does not fit into the arguments of a command (F-268).
                        val file = context.cacheDir.resolve(PASTED).apply { writeText(raw) }

                        try {
                            createFromContent(context.getString(R.string.subscription_default_name), file.absolutePath)
                        } finally {
                            file.delete()
                        }
                    }
                }.also { addUuid = it }

                commitNew(uuid, activate)
            }

            if (result.isSuccess) fillUp()

            finishAdd(context, result, fromPhone, toPower = activate)
        }
    }

    /**
     * «Файл» (этап 13, пакет 5). Конфиг mihomo сразу становится своим конфигом — как «В подписки»
     * в редакторе (D-124): ядро разбирает в тексте только ссылки, и целый конфиг через поле оно
     * отвергло бы. Ссылки из файла — в поле, как «Вставить»: дальше человек жмёт «Добавить».
     */
    fun addFile(context: Context, uri: Uri) {
        if (_state.value.busy) return

        addJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, addError = "", addProgress = 0f) }

            val result = runCatching {
                val text = withContext(Dispatchers.IO) { readFile(context, uri) }?.removePrefix("\uFEFF")
                    ?: throw Told(context.getString(R.string.config_file_unreadable))

                if ('\u0000' in text) throw Told(context.getString(R.string.file_not_subscription))

                if (!CONFIG.containsMatchIn(text)) {
                    _state.update { it.copy(input = text.trim()) }

                    return@runCatching false
                }

                val file = withContext(Dispatchers.IO) { fileName(context, uri) }
                val name = file.substringBeforeLast('.').ifEmpty { file }
                    .ifEmpty { context.getString(R.string.subscription_default_name) }
                val uuid = withProfile { create(Profile.Type.File, name) }.also { addUuid = it }

                withContext(Dispatchers.IO) {
                    context.pendingDir.resolve(uuid.toString()).resolve("config.yaml").writeText(text)
                }

                commitNew(uuid)

                true
            }

            // Ссылки легли в поле — окно остаётся открытым, добавит «Добавить».
            if (result.getOrNull() == false) {
                addJob = null
                _state.update { it.copy(busy = false) }

                return@launch
            }

            if (result.isSuccess) fillUp()

            finishAdd(context, result)
        }
    }

    /**
     * Заготовка загружается: не загрузилась — убираем её, иначе в списке остаётся подписка, которая
     * ни разу не загрузилась, и каждая новая попытка добавляет ещё одну (D-100). Первая
     * добавленная подписка становится активной сама: иначе человек добавит её и упрётся в «нет
     * профиля» при попытке подключиться. Присланная с телефона или добавленная ссылкой со страницы
     * провайдера ([activate], D-163, D-211) — тоже: её добавляют, чтобы включить.
     */
    private suspend fun commitNew(uuid: UUID, activate: Boolean = false) {
        // Ядро сообщает ход загрузки (D-212). Доля только растёт: запасной путь через встроенный
        // шаблон начинает счёт заново, а заливке пятиться незачем.
        val report = IFetchObserver { status ->
            addProgress(status)?.let { part ->
                _state.update { it.copy(addProgress = maxOf(it.addProgress ?: 0f, part)) }
            }
        }

        runCatching { withProfile { commit(uuid, report) } }.onFailure { failure ->
            runCatching { withProfile { delete(uuid) } }
            throw failure
        }

        val profiles = withProfile { queryAll() }
        if (activate || profiles.count { it.imported } == 1) {
            profiles.firstOrNull { it.uuid == uuid }?.let { withProfile { setActive(it) } }
        }
    }

    /** Загрузка удалась: кнопка доливается до конца — и только потом панель закрывается (D-212). */
    private suspend fun fillUp() {
        _state.update { it.copy(addProgress = 1f) }

        delay(FILL_UP_MS)
    }

    /** Итог добавления: ошибка — в окно, успех — окно уезжает. */
    private fun CoroutineScope.finishAdd(
        context: Context,
        result: Result<*>,
        fromPhone: Boolean = false,
        toPower: Boolean = false,
    ) {
        // Отменили (cancelAdd): окно уже закрыто, заготовка удалена там же.
        if (!isActive) return

        val uuid = addUuid

        addJob = null
        addUuid = null

        result.onFailure { failure ->
            _state.update {
                it.copy(
                    busy = false,
                    addError = failure.shown(context).ifEmpty { context.getString(R.string.add_failed) },
                    // Код одноразовый: ссылка осталась в поле — повторить можно кнопкой «Добавить».
                    receive = if (fromPhone) Receive.Expired else it.receive,
                )
            }
        }

        result.onSuccess {
            _state.update {
                it.copy(busy = false, adding = false, input = "", linked = false, toPower = uuid.takeIf { toPower })
            }
        }

        refresh(context)
    }

    private var addJob: Job? = null
    private var addUuid: UUID? = null

    /**
     * Окно закрыли, пока подписка грузится: провайдер может молчать дольше минуты, и держать
     * человека в окне без выхода нельзя. Заготовку удаляем сразу — служба докачает, увидит,
     * что её нет, и ничего не добавит (`ProfileProcessor.apply`).
     */
    fun cancelAdd(context: Context) {
        val job = addJob ?: return
        val uuid = addUuid

        addJob = null
        addUuid = null
        job.cancel()

        _state.update { it.copy(busy = false, addError = "") }

        if (uuid != null) viewModelScope.launch {
            runCatching { withProfile { delete(uuid) } }
            refresh(context)
        }
    }

    private var receiving: Job? = null

    /**
     * Телевизор ждёт подписку с телефона (D-162). Порт открыт, пока идёт эта работа: её
     * останавливают закрытие панели «+» ([stopReceive]) и уход приложения с экрана
     * ([expireReceive]). Сеть сменилась — сеанс и код новые. Принятая ссылка добавляется так же,
     * как введённая руками.
     */
    fun startReceive(context: Context) {
        val app = context.applicationContext

        _state.update { it.copy(toPower = null) }

        receiving?.cancel()
        receiving = viewModelScope.launch {
            val link = channelFlow<String?> {
                homeAddress(app).collectLatest { address ->
                    if (address != null) {
                        return@collectLatest send(receive(address) { code ->
                            _state.update { it.copy(receive = Receive.Waiting(code.toString())) }
                        })
                    }

                    // Система сообщает о сети чуть позже запроса: «нет сети» не должно мигать.
                    delay(NO_NETWORK_MS)
                    _state.update { it.copy(receive = Receive.NoNetwork) }
                }
            }.first()

            if (link == null) {
                _state.update { it.copy(receive = Receive.Expired) }

                return@launch
            }

            _state.update { it.copy(receive = Receive.Received) }

            // Идёт своё добавление, руками, — дождаться: два разом не ведём.
            _state.first { !it.busy }
            _state.update { it.copy(input = link, addError = "") }

            add(app, fromPhone = true)
        }
    }

    /** Панель «+» закрылась: порт закрыт, код забыт. */
    fun stopReceive() {
        receiving?.cancel()
        receiving = null

        _state.update { it.copy(receive = null) }
    }

    /** Приложение ушло с экрана: кода никто не видит — и слушать сеть незачем (D-162). */
    fun expireReceive() {
        if (receiving?.isActive != true) return

        receiving?.cancel()

        _state.update { it.copy(receive = Receive.Expired) }
    }

    /** Главная показана: на телевизоре пульт встал на кнопку питания. */
    fun atPower() = _state.update { it.copy(toPower = null) }

    /**
     * Сканер вернул текст. Код телевизора (D-162) — вместо поля открывается панель отправки, и
     * активная подписка в ней уже выбрана. Всё остальное — ссылка в поле, как раньше.
     */
    fun scanned(text: String) {
        val tv = TvLink.parse(text.trim()) ?: return setInput(text)

        _state.update {
            val sendable = it.profiles.sendable

            it.copy(
                adding = false,
                input = "",
                addError = "",
                sendTo = tv,
                sendChoice = (sendable.firstOrNull { profile -> profile.active } ?: sendable.firstOrNull())?.uuid,
                sendError = "",
                sendBusy = false,
                sent = false,
            )
        }
    }

    fun chooseSend(uuid: UUID) = _state.update { it.copy(sendChoice = uuid, sendError = "") }

    /** Выбор, итог и ошибка остаются: панель ещё уезжает вниз. */
    fun closeSend() = _state.update { it.copy(sendTo = null) }

    /** Ссылка выбранной подписки уходит на телевизор, зашифрованная ключом из его кода. */
    fun confirmSend(context: Context) {
        val state = _state.value
        val to = state.sendTo ?: return
        val link = state.profiles.firstOrNull { it.uuid == state.sendChoice }?.source ?: return

        viewModelScope.launch {
            _state.update { it.copy(sendBusy = true, sendError = "") }

            val accepted = try {
                send(to, link)
            } catch (e: IOException) {
                null
            }

            _state.update {
                it.copy(
                    sendBusy = false,
                    sent = accepted == true,
                    sendError = when (accepted) {
                        true -> ""
                        false -> context.getString(R.string.send_tv_rejected)
                        null -> context.getString(R.string.send_tv_unreachable)
                    },
                )
            }
        }
    }

    /**
     * `update` в службе только ставит задачу в очередь, сама загрузка идёт в фоне
     * (`ProfileWorker`). Поэтому исключение сюда не приходит и результат узнаётся
     * бродкастами: [reportUpdateFinished] и [reportUpdateFailed].
     */
    fun update(context: Context, uuid: UUID, force: Boolean = false) {
        // Подписка, которая ни разу не загрузилась, обновлению не поддаётся: обновлять
        // нечего, чинится ссылка или удаление (D-83). Молча оставлять кнопку крутиться
        // нельзя — это и было прежнее поведение.
        val loaded = _state.value.profiles.firstOrNull { it.uuid == uuid }?.imported != false

        if (!loaded) {
            _state.update {
                it.copy(
                    updating = null,
                    updateError = context.getString(R.string.profile_not_loaded_hint),
                    updateErrorFor = uuid,
                    updateFailure = null,
                )
            }

            return
        }

        // Правку конфига обновление сотрёт — сначала спросить (D-125).
        if (!force && ConfigEdits.edited(context, uuid)) {
            _state.update { it.copy(confirmUpdate = uuid) }

            return
        }

        viewModelScope.launch {
            _state.update { it.copy(updating = uuid, updateError = "", updateErrorFor = null, updateFailure = null) }

            runCatching { withProfile { update(uuid) } }
                .onFailure { failure -> reportUpdateFailed(context, uuid, failure.message) }

            refresh(context)

            // Об окончании работы служба сообщает бродкастом, а он может и не прийти:
            // службу убили, приложение было свёрнуто. Без сторожа карточка осталась бы
            // в «Обновляю…» навсегда.
            launch {
                delay(UPDATE_TIMEOUT)

                if (_state.value.updating == uuid) {
                    _state.update {
                        it.copy(
                            updating = null,
                            updateError = context.getString(R.string.update_timeout),
                            updateErrorFor = uuid,
                            updateFailure = null,
                        )
                    }
                }
            }
        }
    }

    fun dismissConfirmUpdate() = _state.update { it.copy(confirmUpdate = null) }

    /**
     * Окончание — только своей подписки: служба сообщает и о фоновых обновлениях по расписанию,
     * и чужое «готово» снимало «Обновляю…» с той, что ещё качается. Удалось — её прежняя ошибка
     * больше не правда.
     */
    fun reportUpdateFinished(context: Context, uuid: UUID?) {
        _state.update {
            it.copy(
                updating = if (it.updating == uuid) null else it.updating,
                updateError = if (it.updateErrorFor == uuid) "" else it.updateError,
                updateErrorFor = if (it.updateErrorFor == uuid) null else it.updateErrorFor,
                updateFailure = if (it.updateErrorFor == uuid) null else it.updateFailure,
            )
        }

        refresh(context)
    }

    /**
     * Молчать нельзя: профиль при отказе остаётся прежним, и без объяснения это выглядит
     * так, будто кнопка «Обновить» ничего не делает (D-26).
     */
    fun reportUpdateFailed(context: Context, uuid: UUID?, reason: String?) {
        // Причина приходит сырым текстом ядра или службы — словами её говорит `Failure` (D-227).
        val failure = reason?.takeIf { it.isNotBlank() }?.let { Failure.of(it, Failure.Kind.BadConfig) }

        _state.update {
            it.copy(
                updating = if (it.updating == uuid) null else it.updating,
                updateError = failure?.text(context) ?: context.getString(R.string.update_failed),
                updateErrorFor = uuid,
                updateFailure = failure,
            )
        }

        refresh(context)
    }

    fun dismissUpdateError() {
        _state.update { it.copy(updateError = "", updateErrorFor = null, updateFailure = null) }
    }

    fun showEdit(profile: Profile?) {
        _state.update {
            if (profile == null) {
                it.copy(editing = null, editError = "", editBusy = false)
            } else {
                it.copy(
                    editing = profile.uuid,
                    // То, что видно на карточке, а не внутреннее имя: при добавлении по
                    // ссылке это домен, и в правке вместо названия подписки стоял сайт (D-117).
                    editName = profile.shownName,
                    editTitle = profile.profileTitle,
                    editLink = profile.source,
                    editInterval = profile.interval,
                    editError = "",
                    editBusy = false,
                )
            }
        }
    }

    fun setEditName(value: String) = _state.update { it.copy(editName = value) }

    fun setEditLink(value: String) = _state.update { it.copy(editLink = value) }

    fun setEditInterval(value: Long) = _state.update { it.copy(editInterval = value) }

    /**
     * Сохраняет правку. `patch` кладёт изменения в ожидающий профиль, а `commit` их
     * применяет — то есть заново скачивает подписку по ссылке, поэтому это может занять
     * несколько секунд и может не получиться.
     */
    fun saveEdit(context: Context) {
        val current = _state.value
        val uuid = current.editing ?: return

        _state.update { it.copy(editBusy = true, editError = "") }

        val link = current.editLink.trim()
        val typed = current.editName.trim()

        // Название провайдера себе не присваиваем: оставили как есть или стёрли — имя снова
        // «не своё», и карточка покажет то, что пришлёт провайдер при следующем обновлении.
        val name = if (typed.isEmpty() || typed == current.editTitle) {
            defaultName(link).ifEmpty { context.getString(R.string.subscription_default_name) }
        } else typed

        viewModelScope.launch {
            val result = runCatching {
                withProfile {
                    patch(uuid, name, link, current.editInterval, null)
                    commit(uuid)
                }
            }

            result.onSuccess {
                _state.update { it.copy(editing = null, editBusy = false) }

                refresh(context)
            }

            result.onFailure { failure ->
                _state.update {
                    it.copy(
                        editBusy = false,
                        editError = failure.shown(context).ifEmpty { context.getString(R.string.update_failed) },
                    )
                }
            }
        }
    }

    /** Подписку [from] перетащили на место [to]: пока палец на экране — только в списке на экране. */
    fun move(from: UUID, to: UUID) = _state.update {
        val list = it.profiles.toMutableList()
        val target = list.indexOfFirst { p -> p.uuid == to }
        val moved = list.indexOfFirst { p -> p.uuid == from }

        if (target < 0 || moved < 0) return@update it

        list.add(target, list.removeAt(moved))
        it.copy(profiles = list)
    }

    /** Палец отпустили — порядок запоминается. */
    fun saveOrder(context: Context) {
        val order = _state.value.profiles.joinToString(",") { it.uuid.toString() }

        viewModelScope.launch(Dispatchers.IO) { AppStore(context).profileOrder = order }
    }

    fun setActive(context: Context, profile: Profile) {
        viewModelScope.launch {
            runCatching { withProfile { setActive(profile) } }

            refresh(context)
        }
    }

    fun openRules(context: Context, uuid: UUID) {
        val profile = _state.value.profiles.firstOrNull { it.uuid == uuid }

        viewModelScope.launch {
            val rules = withContext(Dispatchers.IO) { UserRulesStore(context).read(uuid) }

            _state.update {
                it.copy(rulesFor = uuid, rulesName = profile?.shownName.orEmpty(), rules = rules, rulesBypass = null)
            }

            // Списки провайдера читает служба — файл профиля разбирает ядро; выбор человека в
            // «Приложениях» — тот же, что у туннеля (PackageFilter).
            val bypass = runCatching {
                val network = withClash { queryProfileNetwork(uuid) }
                val (mode, chosen) = withContext(Dispatchers.IO) {
                    ServiceStore(context).let { it.accessControlMode to it.accessControlPackages.toSet() }
                }

                Bypass(
                    provider = PackageFilter.decide(AccessControlMode.AcceptAll, emptySet(), network),
                    all = PackageFilter.decide(mode, chosen, network),
                )
            }.getOrNull()

            _state.update { if (it.rulesFor == uuid) it.copy(rulesBypass = bypass) else it }
        }
    }

    fun closeRules() = _state.update { it.copy(rulesFor = null) }

    /**
     * Правило на тот же сайт или приложение заменяет прежнее на его месте, а не встаёт вторым:
     * второе ядро всё равно не увидело бы — решает первое совпадение (D-135).
     */
    fun addRule(context: Context, rule: UserRule) = saveRules(context) { rules ->
        val same = rules.indexOfFirst { it.type == rule.type && it.value == rule.value }

        if (same < 0) rules + rule else rules.toMutableList().apply { set(same, rule) }
    }

    fun removeRule(context: Context, rule: UserRule) = saveRules(context) { it - rule }

    /**
     * Правила — в файл подписки, а если она в работе — служба перечитывает профиль с ними
     * (D-123). У неактивной подписки правила вступят в силу, когда её выберут.
     */
    private fun saveRules(context: Context, change: (List<UserRule>) -> List<UserRule>) {
        val uuid = _state.value.rulesFor ?: return
        val rules = change(_state.value.rules)

        _state.update { it.copy(rules = rules, ruleCounts = it.ruleCounts + (uuid to rules.size)) }

        viewModelScope.launch {
            withContext(Dispatchers.IO) { UserRulesStore(context).write(uuid, rules) }

            val active = _state.value.profiles.firstOrNull { it.uuid == uuid }?.active == true

            if (active && Remote.broadcasts.clashRunning) context.sendOverrideChanged()
        }
    }

    fun askDelete(profile: Profile?) {
        _state.update {
            if (profile == null) it.copy(deleting = null)
            else it.copy(
                deleting = profile.uuid,
                deletingName = profile.shownName,
            )
        }
    }

    fun delete(context: Context, uuid: UUID) {
        _state.update { it.copy(deleteBusy = true) }

        viewModelScope.launch {
            // Удалять подписку, через которую сейчас идёт трафик, и оставлять туннель
            // поднятым неправильно: человек убрал провайдера, а трафик продолжает идти
            // через его узлы. Ядро работает с уже загруженным конфигом и само бы
            // не остановилось.
            val active = runCatching { withProfile { queryActive() } }.getOrNull()

            if (active?.uuid == uuid && Remote.broadcasts.clashRunning) {
                context.stopClashService()
            }

            runCatching { withProfile { delete(uuid) } }

            _state.update { it.copy(deleting = null, deleteBusy = false) }

            refresh(context)
        }
    }

    fun setSendHwid(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ServiceStore(context).sendHwid = enabled }

            refresh(context)
        }
    }

    private companion object {
        /** Сколько ждём ответа службы об окончании обновления, прежде чем сдаться. */
        const val UPDATE_TIMEOUT = 90_000L

        /** Сколько ждём от системы адрес домашней сети, прежде чем сказать, что её нет. */
        const val NO_NETWORK_MS = 500L

        /** Конфиг mihomo, а не ссылки: без узлов или их источников конфиг подписке ни к чему. */
        val CONFIG = Regex("""^(proxies|proxy-providers)\s*:""", RegexOption.MULTILINE)

        /** The pasted text on its way to the service, in the cache directory: one add runs at a time. */
        const val PASTED = "pasted-links.txt"
    }

}

/**
 * Список в порядке [order] (номера подписок): известные — по нему, новые — в конце, в каком пришли
 * от службы (по времени добавления). Сортировка устойчивая, поэтому новые друг с другом не мешаются.
 */
internal fun <T> List<T>.inOrder(order: List<String>, key: (T) -> String): List<T> {
    val place = order.withIndex().associate { it.value to it.index }

    return sortedBy { place[key(it)] ?: Int.MAX_VALUE }
}

/**
 * Доля загрузки добавляемой подписки по сообщению ядра (D-212). Конфиг приходит одним куском
 * неизвестного размера — ему отдан небольшой запас в начале. Наборы правил и провайдеры узлов
 * качаются по одному, ядро называет номер и общее число — основная часть идёт по этому счёту.
 * Остаток — проверка конфига. Сообщение не о ходе загрузки — `null`.
 */
internal fun addProgress(status: FetchStatus): Float? = when (status.action) {
    FetchStatus.Action.FetchConfiguration -> CONFIG_PART
    FetchStatus.Action.FetchProviders ->
        if (status.max > 0) CONFIG_PART + PROVIDERS_PART * status.progress.coerceIn(0, status.max) / status.max
        else null
    FetchStatus.Action.Verifying -> CONFIG_PART + PROVIDERS_PART
    else -> null
}

/**
 * Доли заливки: скачивание конфига и наборы правил; остальное — проверка. По замеру на боевой
 * подписке (F-220): из 10 с конфиг занял 2, наборы правил — 7,7.
 */
private const val CONFIG_PART = 0.2f
private const val PROVIDERS_PART = 0.77f

/** Сколько панель ждёт, пока кнопка дольётся до конца: чуть дольше самой доливки (`SheetButton`). */
private const val FILL_UP_MS = 350L

/** Ошибка, у которой текст уже для человека, — в отличие от сырых текстов службы и ядра. */
private class Told(text: String) : Exception(text)

/**
 * Неудача загрузки подписки словами (D-227). Незнакомую ошибку ядра дописываем строкой ниже, без
 * адресов: по ней поддержка поймёт, что не так с конфигом.
 */
private fun Throwable.shown(context: Context): String {
    if (this is Told) return message.orEmpty()

    val failure = message?.takeIf { it.isNotBlank() }?.let { Failure.of(it, Failure.Kind.BadConfig) } ?: return ""

    return listOf(failure.text(context), LogScrubber.scrub(failure.detail)).filter { it.isNotBlank() }.joinToString("\n")
}

/** Имя подписки, добавленной по ссылке, пока человек не назвал её сам: домен ссылки. */
internal fun defaultName(link: String): String = runCatching { Uri.parse(link).host }.getOrNull().orEmpty()

/**
 * Имя подписки на экране (D-117): своё, если человек его задал; иначе название от
 * провайдера (`profile-title`); иначе то, что поставили при добавлении, — домен ссылки.
 */
/**
 * Свой конфиг — без ссылки (D-125): копия правленого конфига или файл с телефона. Обновлять
 * его неоткуда, трафика и срока у него нет.
 */
val Profile.local: Boolean
    get() = source.isBlank()

/** Что можно отправить на телевизор (D-162): подписки со ссылкой, которую он возьмёт. */
internal val List<Profile>.sendable: List<Profile>
    get() = filter { subscriptionLink(it.source) != null }

val Profile.shownName: String
    get() = if (name.isNotEmpty() && name != defaultName(source)) name else profileTitle.ifEmpty { name }
