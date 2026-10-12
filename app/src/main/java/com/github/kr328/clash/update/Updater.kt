package com.github.kr328.clash.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat
import com.github.kr328.clash.BuildConfig
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.ui.settings.SettingsStore
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Что не вышло при обновлении. */
enum class UpdateError {
    /** Файл не скачался или не сошёлся с контрольной суммой ни по одному адресу. */
    Download,

    /** Файл скачан, но это не наш выпуск: другой пакет, не новее или подписан не нашим ключом. */
    Foreign,

    /** Отказал Android; что он сказал — в [UpdateState.errorDetail]. */
    Install,
}

/** The version the app has just updated itself to, and what it brought. */
data class Updated(val version: String, val notes: Map<String, String>)

data class UpdateState(
    /** Есть что ставить; `null` — установлена последняя или ещё не проверяли. */
    val release: Release? = null,
    val checking: Boolean = false,
    /**
     * Чем кончилась проверка: `true` — ответ получен, `false` — не вышло. Неудачу показываем
     * только после проверки по кнопке: фоновая попробует ещё раз сама.
     */
    val checked: Boolean? = null,
    /** Скачивание идёт: доля сделанного. */
    val progress: Float? = null,
    val installing: Boolean = false,
    val error: UpdateError? = null,
    val errorDetail: String = "",
    /** Системное окно, без которого Android не ставит: открыть его может только окно приложения. */
    val confirm: Intent? = null,
    /**
     * The app has just updated itself and the person has not yet seen that on screen: the
     * update closed the app, and the notification reaches only those who allowed notifications.
     */
    val updated: Updated? = null,
) {
    val busy: Boolean
        get() = progress != null || installing
}

/**
 * Обновление приложения из самого приложения: проверка `update.json`, скачивание, установка.
 *
 * Один на процесс интерфейса: фоновая проверка ([UpdateJob]) и ответ установщика
 * ([UpdateReceiver]) приходят сюда же, а экраны читают готовое [state]. Скачивание не привязано
 * к окну: панель можно закрыть, файл докачается.
 */
object Updater {
    /** Уведомление о новой версии открывает приложение с панелью обновления. */
    const val ACTION_SHOW = "${BuildConfig.APPLICATION_ID}.action.SHOW_UPDATE"

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val context: Context
        get() = Global.application

    private val store by lazy { UpdateStore(context) }
    private val dir: File
        get() = File(context.cacheDir, "update")

    private var checkJob: Job? = null
    private var installJob: Job? = null

    /**
     * Приложение на экране: показать то, что знали в прошлый раз, и проверить заново, если
     * давно не проверяли. Заодно — расписание фоновой проверки: оно переживает перезагрузку и
     * обновление, поэтому ставится один раз.
     */
    fun opened() {
        Global.launch {
            UpdateJob.schedule(context)

            val known = pick()

            _state.update { it.copy(release = known, updated = updated()) }

            if (System.currentTimeMillis() - store.checkedAt > OPEN_INTERVAL) check()
        }
    }

    /**
     * The check on the system's schedule, from [UpdateJob]. The job runs the moment it is first
     * scheduled — right when the app opens and checks by itself: the server should see one
     * request then, not two.
     */
    suspend fun scheduled() {
        if (System.currentTimeMillis() - store.checkedAt > JUST_CHECKED) check().join()
    }

    /**
     * [manual] — человек нажал «Проверить обновления» и ждёт ответа, каким бы тот ни был.
     * A check already in flight is returned as is.
     */
    @Synchronized
    fun check(manual: Boolean = false): Job {
        checkJob?.takeIf { it.isActive }?.let { return it }

        return Global.launch { refresh(manual) }.also { checkJob = it }
    }

    /** One check from start to finish. */
    private fun refresh(manual: Boolean) {
        // Канал могли сменить — сначала то, что уже лежит на телефоне.
        val known = pick()

        _state.update { it.copy(release = known, checking = true) }

        store.checkedAt = System.currentTimeMillis()

        // The address answered that there is no such file: nothing is published there. That is
        // an answer, not a failed check — otherwise builds made before the first published
        // release would report an error to someone who has the latest version.
        var absent = false
        val mirrors = runCatching { parseFeed(store.feed).mirrors }.getOrDefault(emptyList())
        val fresh = (BuildConfig.UPDATE_URLS.split(',') + mirrors).firstNotNullOfOrNull { url ->
            // Ответ не того вида — тот же отказ: идём к следующему адресу.
            runCatching { fetchText(url).also(::parseFeed) }
                .onFailure { if (it is FileNotFoundException) absent = true }
                .getOrNull()
        }

        if (fresh != null) store.feed = fresh

        val release = pick()

        _state.update {
            it.copy(
                release = release,
                checking = false,
                checked = when {
                    fresh != null || absent -> true
                    manual -> false
                    else -> it.checked
                },
            )
        }

        // Скачанное под прежние выпуски больше не нужно.
        dir.listFiles()?.filter { release == null || it != file(release) }?.forEach { it.delete() }

        // Уведомление — один раз на версию и только когда приложения нет на экране: на экране
        // о ней говорит карточка главной.
        if (release != null && release.code != store.notified) {
            store.notified = release.code

            if (!ApplicationObserver.visible) UpdateNotice.available(context, release.version)
        }
    }

    /** Скачать, сверить и отдать установщику. Повторное нажатие, пока идёт, ничего не делает. */
    @Synchronized
    fun install() {
        val release = _state.value.release ?: return

        if (installJob?.isActive == true) return

        installJob = Global.launch {
            _state.update { it.copy(progress = 0f, installing = false, error = null, errorDetail = "") }

            val file = file(release)

            try {
                dir.mkdirs()

                var shown = -1

                download(release.apk, file) { done ->
                    // Раз на процент: экрану незачем перерисовывать кнопку на каждый кусок файла.
                    val percent = (done * 100).toInt()

                    if (percent != shown) {
                        shown = percent
                        _state.update { it.copy(progress = done) }
                    }
                }
            } catch (e: IOException) {
                return@launch stale(UpdateError.Download)
            }

            _state.update { it.copy(progress = null, installing = true) }

            if (!ours(file)) {
                file.delete()

                return@launch stale(UpdateError.Foreign)
            }

            try {
                commit(file, release.code)
            } catch (e: Exception) {
                fail(UpdateError.Install, e.message.orEmpty())
            }
        }
    }

    /** Ответ установщика, из [UpdateReceiver]. */
    fun status(intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> _state.update {
                it.copy(confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
            }

            // Обычно не доходит: процесс к этому времени уже заменён новой версией.
            PackageInstaller.STATUS_SUCCESS -> Unit

            // Человек сам отказался в системном окне — это не ошибка.
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.update { it.copy(installing = false) }

            else -> fail(UpdateError.Install, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }

    /** Системное окно открыто — второй раз не открывать. */
    fun confirmShown() = _state.update { it.copy(confirm = null) }

    /** Приложение заменено новой версией — нашей установкой или руками. */
    fun replaced() {
        UpdateJob.schedule(context)

        // Сказать «обновлён» стоит только про своё обновление: его человек не видел — приложение
        // просто исчезло с экрана. Поставил сам — прежнее «вышла версия» больше не правда.
        if (store.installing == BuildConfig.VERSION_CODE.toLong()) {
            UpdateNotice.updated(context, BuildConfig.VERSION_NAME)

            store.updated = BuildConfig.VERSION_CODE.toLong()
        } else {
            UpdateNotice.cancel(context)
        }

        store.installing = 0

        dir.deleteRecursively()
    }

    /** The person has seen that the app was updated: the card and the notification are done. */
    fun acknowledged() {
        store.updated = 0

        UpdateNotice.cancel(context)

        _state.update { it.copy(updated = null) }
    }

    private fun updated(): Updated? {
        val code = BuildConfig.VERSION_CODE.toLong()

        if (store.updated != code) return null

        // “What's new” is still in the answer the version was found in.
        val entry = runCatching { parseFeed(store.feed) }.getOrNull()
            ?.let { listOfNotNull(it.stable, it.beta) }
            ?.firstOrNull { it.code == code }

        return Updated(BuildConfig.VERSION_NAME, entry?.notes.orEmpty())
    }

    /**
     * Не вышло с самим файлом. Выпуск могли заменить или отозвать, а приложение помнит прежний
     * ответ ещё несколько часов — перечитываем: следующая попытка пойдёт по свежему, а отозванный
     * выпуск перестанет предлагаться.
     */
    private fun stale(error: UpdateError) {
        fail(error)
        check()
    }

    private fun fail(error: UpdateError, detail: String = "") =
        _state.update { it.copy(progress = null, installing = false, error = error, errorDetail = detail) }

    private fun pick(): Release? = runCatching { parseFeed(store.feed) }.getOrNull()?.pick(
        beta = SettingsStore(context).betaUpdates,
        installed = BuildConfig.VERSION_CODE.toLong(),
        sdk = Build.VERSION.SDK_INT,
        abis = Build.SUPPORTED_ABIS.toList(),
    )

    private fun file(release: Release) = File(dir, "ducker-${release.code}.apk")

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.connectTimeout = CONNECT_MS
        connection.readTimeout = READ_MS
        connection.setRequestProperty("User-Agent", AGENT)

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw FileNotFoundException(url)
                else -> throw IOException("HTTP ${connection.responseCode}")
            }

            // Файл — пара килобайт; больше предела не читаем, что бы ни отдал сервер.
            val buffer = ByteArray(FEED_LIMIT + 1)
            var size = 0

            connection.inputStream.use { input ->
                while (size < buffer.size) {
                    val read = input.read(buffer, size, buffer.size - size)
                    if (read < 0) break

                    size += read
                }
            }

            if (size > FEED_LIMIT) throw IOException("too large")

            return String(buffer, 0, size)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Наш ли это выпуск. Чужой файл Android и сам не поставит поверх, но узнать об этом лучше
     * до установщика: сказать человеку прямо и не показывать ему системное окно зря.
     */
    private fun ours(file: File): Boolean = runCatching {
        val manager = context.packageManager
        val mine = manager.getPackageInfo(context.packageName, SIGNERS)
        val theirs = manager.getPackageArchiveInfo(file.path, SIGNERS) ?: return false

        theirs.packageName == context.packageName && theirs.longVersionCode > mine.longVersionCode &&
            mine.signers().isNotEmpty() && theirs.signers() == mine.signers()
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<String> =
        (signingInfo?.apkContentsSigners ?: signatures).orEmpty().map { it.toCharsString() }.toSet()

    /**
     * Оба флага сразу: Android 10 читает подпись файла на диске только по устаревшему, а по
     * новому молча не отдаёт ничего — свой же выпуск выходил «чужим».
     */
    @Suppress("DEPRECATION")
    private const val SIGNERS = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

    private fun commit(file: File, code: Long) {
        val installer = context.packageManager.packageInstaller

        // Брошенные попытки держат на диске копию файла.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())

            // Android 12+: без системного окна, если «установка из этого источника» уже
            // разрешена. Раньше, и пока не разрешена, — окно покажет сам Android.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        store.installing = code

        installer.openSession(installer.createSession(params)).use { session ->
            session.openWrite("ducker.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }

                session.fsync(output)
            }

            // Изменяемый: ответ установщик дописывает в это же намерение.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

            session.commit(
                PendingIntent.getBroadcast(context, 0, Intent(context, UpdateReceiver::class.java), flags).intentSender
            )
        }
    }

    /** При открытии приложения — не чаще: адрес у людей за одним узлом общий, а сервер считает запросы. */
    private const val OPEN_INTERVAL = 6 * 60 * 60 * 1000L
    private const val JUST_CHECKED = 60 * 1000L
    private const val CONNECT_MS = 10_000
    private const val READ_MS = 15_000
    private const val FEED_LIMIT = 256 * 1024
}

private class UpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** Последний удачный ответ целиком: из него же берутся дополнительные адреса. */
    var feed: String
        get() = prefs.getString("feed", null).orEmpty()
        set(value) = prefs.edit().putString("feed", value).apply()

    var checkedAt: Long
        get() = prefs.getLong("checked_at", 0)
        set(value) = prefs.edit().putLong("checked_at", value).apply()

    /** О какой версии уже сказали уведомлением. */
    var notified: Long
        get() = prefs.getLong("notified", 0)
        set(value) = prefs.edit().putLong("notified", value).apply()

    /** Which version the app updated itself to and has not yet said so on screen. */
    var updated: Long
        get() = prefs.getLong("updated", 0)
        set(value) = prefs.edit().putLong("updated", value).apply()

    /**
     * Какую версию отдали установщику. Пишется сразу на диск: следом процесс заменят, и
     * отложенная запись могла бы не успеть.
     */
    var installing: Long
        get() = prefs.getLong("installing", 0)
        set(value) {
            prefs.edit().putLong("installing", value).commit()
        }
}
