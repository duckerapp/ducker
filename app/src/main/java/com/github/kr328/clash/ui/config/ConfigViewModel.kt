package com.github.kr328.clash.ui.config

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.R
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.outOfSpace
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendOverrideChanged
import com.github.kr328.clash.ui.subscriptions.shownName
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Чем кончилась проверка или запись (D-124). */
sealed interface ConfigResult {
    data object Valid : ConfigResult
    /** [line] — строка из ошибки YAML, если ядро её назвало. */
    data class Invalid(val message: String, val line: Int?) : ConfigResult
    data object Saved : ConfigResult
    data object SaveFailed : ConfigResult
    /** Nothing could be written, the phone is out of space (Q-104); the edit stays in the editor. */
    data object NoSpace : ConfigResult
    data class Copied(val name: String) : ConfigResult
    /** Файл с телефона добавлен как свой профиль. */
    data class Added(val name: String) : ConfigResult
    /** Файл открыт только для чтения — записать некуда. */
    data object ReadOnly : ConfigResult
    /** Ошибок нет, отступы выровнены. */
    data object Formatted : ConfigResult
    /** Ошибок нет, но выровненный конфиг ядро не разобрало — оставлен как был. */
    data object FormatFailed : ConfigResult
    /** Выгружен в файл [name] на телефоне. */
    data class Exported(val name: String) : ConfigResult
    /** «Заменить все»: сколько замен; [deleted] — заменяли на пустое. */
    data class Replaced(val count: Int, val deleted: Boolean) : ConfigResult
}

/**
 * Правленые конфиги подписок (D-124): uuid → время файла после нашей записи. Пока файл тот же,
 * правка на месте, и обновление подписки её сотрёт — перед ним спрашиваем. Файл сменился
 * (подписка обновилась) — отметка устарела и снимается.
 */
internal object ConfigEdits {
    fun mark(context: Context, uuid: UUID) = prefs(context).edit {
        putLong(uuid.toString(), configFile(context, uuid).lastModified())
    }

    fun edited(context: Context, uuid: UUID): Boolean {
        val marked = prefs(context).getLong(uuid.toString(), 0)
        if (marked == 0L) return false
        if (configFile(context, uuid).lastModified() == marked) return true

        prefs(context).edit { remove(uuid.toString()) }

        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences("config_edits", Context.MODE_PRIVATE)
}

internal fun configFile(context: Context, uuid: UUID) = context.importedDir.resolve(uuid.toString()).resolve("config.yaml")

/**
 * Черновик правки (D-228, аудит 9б.2). Приложение ушло в фон с несохранённой правкой — текст
 * редактора ложится в файл; выгрузила система процесс — при следующем открытии того же конфига
 * правку предлагаем вернуть. [key] — uuid подписки или адрес файла с телефона.
 *
 * ponytail: черновик один, последний — правка второго конфига стирает черновик первого, зато
 * от удалённой подписки не остаётся файла с ключами. Станет мешать — по файлу на конфиг и
 * чистка при удалении подписки.
 */
internal class ConfigDraft(private val dir: File) {
    fun write(key: String, text: String) {
        val file = file(key)
        // Через временный файл: оборванная запись не должна стать обрезанным черновиком.
        val temp = File(dir, "${file.name}.tmp")

        dir.mkdirs()
        temp.writeText(text)
        if (!temp.renameTo(file)) throw IOException("Cannot replace config draft")
        dir.listFiles()?.forEach { if (it != file) it.delete() }
    }

    fun read(key: String): String? = runCatching { file(key).readText() }.getOrNull()

    fun drop(key: String) {
        file(key).delete()
    }

    private fun file(key: String) = File(dir, UUID.nameUUIDFromBytes(key.toByteArray()).toString())
}

data class ConfigState(
    val uuid: UUID? = null,
    /** Файл, открытый из файлового менеджера (D-124); `null` — конфиг подписки. */
    val file: Uri? = null,
    val name: String = "",
    /** Конфиг подписки: перекачается при обновлении — об этом предупреждаем. */
    val fromSubscription: Boolean = false,
    val active: Boolean = false,
    /** Текст из файла; `null` — ещё читается. */
    val text: String? = null,
    /** Чтение завершилось отказом: редактор и запись недоступны. */
    val loadError: String? = null,
    val busy: Boolean = false,
    val result: ConfigResult? = null,
    /** Есть несохранённая правка. */
    val dirty: Boolean = false,
    /** Меняется при каждой правке, даже если dirty уже true. */
    val revision: Long = 0,
    /** Спросить, выходить ли без сохранения. */
    val askLeave: Boolean = false,
    /** Правка, не сохранённая в прошлый раз: спросить, вернуть ли её (D-228). */
    val draft: String? = null,
) {
    val open: Boolean get() = uuid != null || file != null
    val draftKey: String? get() = uuid?.toString() ?: file?.toString()
}

/**
 * Просмотр и правка конфига подписки (D-124). Сохранение — прямо в файл профиля, после
 * проверки ядром; у работающей подписки служба сразу перечитывает профиль. «Копией» —
 * свой профиль без обновлений: правка не потеряется.
 */
class ConfigViewModel : ViewModel() {
    private val _state = MutableStateFlow(ConfigState())
    val state: StateFlow<ConfigState> = _state.asStateFlow()
    private var document = 0L

    // В `no_backup`: в конфиге ключи узлов, в облачную копию ему нельзя, как и самим профилям.
    private var drafts: ConfigDraft? = null
    // Запись, чтение и удаление черновика — строго по очереди.
    private val disk = Dispatchers.IO.limitedParallelism(1)

    fun open(context: Context, profile: Profile) {
        drafts = ConfigDraft(context.noBackupFilesDir.resolve("config-draft"))
        val opened = ++document
        _state.value = ConfigState(
            uuid = profile.uuid,
            name = profile.shownName,
            fromSubscription = profile.type == Profile.Type.Url,
            active = profile.active,
        )

        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { configFile(context, profile.uuid).inputStream().use(::readConfigText) }
            }
            loaded(context, opened, read)
        }
    }

    /**
     * `.yaml` из файлового менеджера (D-124): тот же редактор, но «Сохранить» пишет обратно
     * в файл, если его дали на запись, а «В подписки» добавляет как свой профиль.
     */
    fun openFile(context: Context, uri: Uri) {
        // Явный Intent обходит фильтры манифеста. Проверяем до запроса имени и чтения;
        // свои content-провайдеры также не должны открываться правами ducker извне.
        if (!externalConfigUriAllowed(uri.scheme, uri.authority, context.packageName)) return

        drafts = ConfigDraft(context.noBackupFilesDir.resolve("config-draft"))
        val opened = ++document
        _state.value = ConfigState(file = uri)

        viewModelScope.launch {
            // Имя — запрос к поставщику файла: у облачных он бывает долгим (аудит, perf 3.3).
            val (name, read) = withContext(Dispatchers.IO) {
                fileName(context, uri) to runCatching { readFile(context, uri) }
            }
            if (document == opened) _state.update { it.copy(name = name) }
            loaded(context, opened, read)
        }
    }

    private suspend fun loaded(context: Context, opened: Long, read: Result<String?>) {
        if (document != opened) return
        val text = read.getOrNull()
        val error = if (text != null) null else context.getString(
            if (read.exceptionOrNull() is ConfigFileTooLargeException) R.string.config_file_too_large
            else R.string.config_file_unreadable,
        )
        // Тот же текст, что в файле, возвращать незачем.
        val key = _state.value.draftKey
        val draft = if (text == null || key == null) null else withContext(disk) { drafts?.read(key) }?.takeIf { it != text }
        if (document != opened) return
        _state.update { it.copy(text = text, loadError = error, draft = draft) }
    }

    /** Закрыть — значит правка сохранена или брошена: черновик больше не нужен. */
    fun close() {
        dropDraft()
        document++
        _state.value = ConfigState()
    }

    /** Приложение уходит в фон с несохранённой правкой [text] (D-228). */
    fun stash(text: String) {
        val key = _state.value.draftKey ?: return
        val drafts = drafts ?: return

        viewModelScope.launch(disk) { runCatching { drafts.write(key, text) } }
    }

    /** Ответ на вопрос о черновике: [restore] — правка уже в редакторе, иначе она удаляется. */
    fun answerDraft(restore: Boolean) {
        if (!restore) dropDraft()
        _state.update { it.copy(draft = null) }
    }

    private fun dropDraft() {
        val key = _state.value.draftKey ?: return
        val drafts = drafts ?: return

        viewModelScope.launch(disk) { drafts.drop(key) }
    }

    fun markDirty() = _state.update { it.copy(dirty = true, revision = it.revision + 1, result = null) }

    /** Итог, который показывает сам экран (замена), и снятие показанного. */
    fun show(result: ConfigResult?) = _state.update { it.copy(result = result) }

    /** Что выгрузить, пока человек выбирает место: текст и «без серверов». */
    private var export: Pair<String, Boolean>? = null

    fun prepareExport(text: String, bare: Boolean) {
        export = text to bare
    }

    /** Имя файла по умолчанию: имя подписки или файла, «без серверов» — с пометкой. */
    fun exportName(context: Context, bare: Boolean): String {
        val name = _state.value.name
        val base = (if (_state.value.file != null) name.substringBeforeLast('.').ifEmpty { name } else name).replace('/', '_')

        return (if (bare) context.getString(R.string.config_export_bare_name, base) else base) + ".yaml"
    }

    /**
     * «Файлом на телефон» (D-124): без проверки — выгрузить можно и недоправленный. Место и
     * имя выбирает человек в системном окне, в [uri].
     */
    fun export(context: Context, uri: Uri) {
        val (text, bare) = export ?: return
        export = null

        op { _, _ ->
            val out = if (bare) withContext(Dispatchers.Default) {
                runCatching { withoutServers(text) }.getOrNull()
            } ?: return@op ConfigResult.Invalid(context.getString(R.string.config_export_failed), null)
            else text
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.toByteArray()) } != null
                }.getOrDefault(false)
            }

            if (written) ConfigResult.Exported(withContext(Dispatchers.IO) { fileName(context, uri) })
            else ConfigResult.Invalid(context.getString(R.string.config_export_failed), null)
        }
    }

    fun askLeave(ask: Boolean) = _state.update { it.copy(askLeave = ask) }

    fun save(context: Context, text: String) = op { snapshot, opened ->
        validate(context, text) ?: write(context, text, snapshot, opened)
    }

    fun saveCopy(context: Context, text: String) = op { snapshot, _ -> validate(context, text) ?: copy(context, text, snapshot) }

    /**
     * «Проверить» (D-124): проверка ядром и заодно выравнивание отступов — одной кнопкой.
     * Сломанный конфиг сначала чинят по ошибке ядра: номер строки в ней —
     * по исходному тексту. Выровненный применяется, только если ядро его тоже разбирает, —
     * через [apply], в редактор, чтобы его можно было отменить.
     */
    fun format(context: Context, text: String, apply: (String) -> Unit) = op { snapshot, opened ->
        validate(context, text) ?: run {
            val tidy = withContext(Dispatchers.Default) { formatYaml(text) }

            when {
                tidy == text -> ConfigResult.Valid
                validate(context, tidy) != null -> ConfigResult.FormatFailed
                !current(snapshot, opened) -> null
                else -> {
                    apply(tidy)
                    ConfigResult.Formatted
                }
            }
        }
    }

    private suspend fun write(context: Context, text: String, snapshot: ConfigState, opened: Long): ConfigResult? {
        snapshot.file?.let { uri ->
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }

            if (!written) return ConfigResult.ReadOnly

            markSaved(snapshot, opened)

            return ConfigResult.Saved
        }

        val uuid = snapshot.uuid ?: return null

        try {
            withContext(Dispatchers.IO) {
                writeAtomically(configFile(context, uuid), text)
                if (snapshot.fromSubscription) ConfigEdits.mark(context, uuid)
            }
        } catch (_: IOException) {
            return if (context.outOfSpace) ConfigResult.NoSpace else ConfigResult.SaveFailed
        }

        if (snapshot.active && Remote.broadcasts.clashRunning) context.sendOverrideChanged()

        markSaved(snapshot, opened)

        return ConfigResult.Saved
    }

    /**
     * Своя подписка из этого текста: без ссылки и автообновления — правка не перекачается.
     * Новая, а не клон подписки: клон уносил её трафик и срок, а «Обновить» у него шло по
     * пустой ссылке — «unsupported scheme».
     */
    private suspend fun copy(context: Context, text: String, snapshot: ConfigState): ConfigResult {
        val file = snapshot.file != null
        val name = if (file) snapshot.name.substringBeforeLast('.').ifEmpty { snapshot.name }
        else context.getString(R.string.config_copy_name, snapshot.name)

        val uuid = withProfile { create(Profile.Type.File, name) }

        withContext(Dispatchers.IO) {
            writeAtomically(context.pendingDir.resolve(uuid.toString()).resolve("config.yaml"), text)
        }

        withProfile { commit(uuid) }

        return if (file) ConfigResult.Added(name) else ConfigResult.Copied(name)
    }

    private fun current(snapshot: ConfigState, opened: Long): Boolean =
        document == opened && _state.value.revision == snapshot.revision

    private fun markSaved(snapshot: ConfigState, opened: Long) {
        if (document != opened) return

        _state.update { if (it.revision == snapshot.revision) it.copy(dirty = false) else it }
        // Текст, набранный за время записи, остаётся несохранённым — и черновик при нём.
        if (!_state.value.dirty) dropDraft()
    }

    /** Одна операция за раз: кнопки на это время гаснут. */
    private fun op(block: suspend (ConfigState, Long) -> ConfigResult?) {
        val snapshot = _state.value
        if (snapshot.busy || snapshot.text == null) return
        val opened = document
        _state.update { it.copy(busy = true, result = null) }

        viewModelScope.launch {
            val result = runCatching { block(snapshot, opened) }.getOrElse { ConfigResult.Invalid(it.message.orEmpty(), null) }
            if (document != opened) return@launch
            _state.update {
                // Formatted само вызывает событие правки. Остальные старые итоги проверки/
                // записи относятся только к снимку, а не к новому тексту в редакторе.
                val fresh = it.revision == snapshot.revision || result == ConfigResult.Formatted ||
                    result is ConfigResult.Exported || result is ConfigResult.Copied || result is ConfigResult.Added ||
                    result == ConfigResult.SaveFailed || result == ConfigResult.ReadOnly
                it.copy(busy = false, result = result.takeIf { fresh })
            }
        }
    }

    /** Ошибка ядра или `null`, если конфиг разбирается. */
    private suspend fun validate(context: Context, text: String): ConfigResult? {
        val dir = try {
            withContext(Dispatchers.IO) {
                context.cacheDir.resolve("config-check").also {
                    it.mkdirs()
                    it.resolve("config.yaml").writeText(text)
                }
            }
        } catch (e: IOException) {
            if (context.outOfSpace) return ConfigResult.NoSpace

            throw e
        }

        val error = try {
            withClash { validateConfig(dir.absolutePath) }
        } finally {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
        }

        return error.takeIf { it.isNotEmpty() }
            ?.let { ConfigResult.Invalid(it, LINE.find(it)?.groupValues?.get(1)?.toIntOrNull()) }
    }

    private fun writeAtomically(file: File, text: String) {
        val parent = file.parentFile ?: throw IOException("Config directory missing")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create config directory")
        val temp = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temp).use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("Cannot replace config file")
        } finally {
            temp.delete()
        }
    }

    private companion object {
        val LINE = Regex("line (\\d+)")
    }
}

/**
 * Текст файла с телефона — конфиг из файлового менеджера (D-124) или «Файл» при добавлении
 * подписки. `null` — не открылся.
 */
internal fun readFile(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use(::readConfigText)
} catch (_: ConfigFileTooLargeException) {
    // Общий путь импорта показывает ту же локализованную ошибку, не сокращённый текст.
    throw ConfigFileTooLargeException(context.getString(R.string.config_file_too_large))
} catch (_: Exception) {
    null
}

/** Имя файла, как его видит человек в файловом менеджере. */
internal fun fileName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment.orEmpty()
