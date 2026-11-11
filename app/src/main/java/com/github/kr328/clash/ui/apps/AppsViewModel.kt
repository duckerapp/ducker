package com.github.kr328.clash.ui.apps

import android.Manifest.permission.INTERNET
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.core.model.ProfileNetwork
import com.github.kr328.clash.service.store.ConfigAppsStore
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.PackageFilter
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AppRow(
    val packageName: String,
    val label: String,
    val system: Boolean,
    /** Судьбу решает конфиг провайдера, галочка ничего не изменит. */
    val locked: Boolean = false,
    /** A row of the config's own lists the person may turn the other way (D-471). */
    val provider: Boolean = false,
    /** The person turned the provider's decision for it the other way. */
    val changed: Boolean = false,
    /** Фактический исход при текущем режиме и выборе. */
    val inTunnel: Boolean = false,
)

data class AppsState(
    val loading: Boolean = true,
    /** Пока не прочитан — null: иначе экран на миг открывался на «Все» и прыгал на свой режим. */
    val mode: AccessControlMode? = null,
    val apps: List<AppRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val showSystem: Boolean = false,
    val total: Int = 0,
    /** Сколько приложений выводит мимо туннеля сам конфиг. */
    val profileExcluded: Int = 0,
    /** Непусто, если конфиг пускает в туннель только перечисленные приложения. */
    val profileIncluded: Int = 0,
    /** Сколько из списка провайдера реально стоит на этом телефоне. */
    val profileInstalled: Int = 0,
    /** The active subscription's config apps the person reversed (D-471). */
    val reversed: Set<String> = emptySet(),
    /** `ducker-override-user: on`: the config's rows stay locked (D-60, D-471). */
    val providerLock: Boolean = false,
)

/**
 * Выбор приложений, которые идут через туннель (замена экрана CMFA, этап 8.3).
 *
 * Список собирается один раз и держится в памяти целиком, а в состояние уходит уже
 * отфильтрованный: приложений на телефоне несколько сотен, и перечитывать `PackageManager`
 * на каждую букву поиска незачем.
 */
class AppsViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    private var all: List<AppRow> = emptyList()
    private var saved: Set<String> = emptySet()
    private var savedMode: AccessControlMode? = null
    private var profile: ProfileNetwork = ProfileNetwork()
    /** The active subscription: the config's lists and the person's reversals of them are its own (D-471). */
    private var subscription: UUID? = null
    private var savedReversed: Set<String> = emptySet()
    /** The config's rows the person may reverse; empty under the provider's lock. */
    private var reversible: Set<String> = emptySet()

    /** Кого решает конфиг, а не галочка. Зависит от режима и профиля, но не от отметок. */
    private var locked: Set<String> = emptySet()
    private var lockedFor: AccessControlMode? = null

    fun load(context: Context) {
        viewModelScope.launch {
            val store = withContext(Dispatchers.IO) { ServiceStore(context) }

            val (stored, packages) = withContext(Dispatchers.IO) {
                store.accessControlMode to store.accessControlPackages.toSet()
            }
            val uuid = withContext(Dispatchers.IO) { store.activeProfile }

            // Режима «Все» на экране нет (D-139): это «Без VPN» без отметок. Список, оставшийся
            // от прежнего выбора, при «Все» не действовал — отметить его значило бы включить молча.
            val wasAll = stored == AccessControlMode.AcceptAll
            val mode = if (wasAll) AccessControlMode.DenySelected else stored
            val selected = if (wasAll) emptySet() else packages

            saved = selected
            savedMode = mode

            // Режим — сразу: список приложений собирается заметно дольше.
            _state.update { it.copy(mode = mode, selected = selected) }

            all = withContext(Dispatchers.IO) { loadApps(context) }

            // Списки из профиля: их читает служба, а файл профиля разбирает ядро.
            // Если служба не поднята, считаем, что ограничений нет, — экран всё равно
            // покажет только выбор человека.
            profile = runCatching { withClash { queryProfileNetwork(null) } }
                .getOrDefault(ProfileNetwork())
            // Unknown counts as locked: a row that cannot be changed is shown as before.
            val providerLock = uuid == null ||
                runCatching { withProfile { queryByUUID(uuid) }?.overrideUser != false }.getOrDefault(true)
            val reversed = uuid?.takeUnless { providerLock }
                ?.let { withContext(Dispatchers.IO) { ConfigAppsStore(context).read(it) } }
                .orEmpty()

            subscription = uuid
            savedReversed = reversed
            reversible = if (providerLock) emptySet() else PackageFilter.listed(profile)
            lockedFor = null

            _state.update {
                it.copy(
                    loading = false,
                    mode = mode,
                    selected = selected,
                    total = all.size,
                    profileExcluded = profile.excludePackage.size,
                    profileIncluded = profile.includePackage.size,
                    profileInstalled = all.count {
                        it.packageName in profile.excludePackage ||
                            it.packageName in profile.includePackage
                    },
                    reversed = reversed,
                    providerLock = providerLock,
                )
            }

            refilter(resort = true)
        }
    }

    fun setMode(mode: AccessControlMode) {
        _state.update { it.copy(mode = mode) }

        refilter(resort = true)
    }

    fun toggle(packageName: String) {
        _state.update {
            // A config row turns the provider's decision the other way and back; the marks are for the other apps.
            if (packageName in reversible) {
                return@update it.copy(
                    reversed = if (packageName in it.reversed) it.reversed - packageName else it.reversed + packageName
                )
            }

            val selected =
                if (packageName in it.selected) it.selected - packageName
                else it.selected + packageName

            it.copy(selected = selected)
        }

        refilter()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }

        refilter(resort = true)
    }

    fun setShowSystem(value: Boolean) {
        _state.update { it.copy(showSystem = value) }

        refilter(resort = true)
    }

    fun selectAll() {
        _state.update {
            // Заблокированные конфигом не трогаем: их галочка всё равно ничего не решает. The config's rows change only
            // one by one, on purpose: a bank must not go into the tunnel with «Отметить все» (D-471).
            it.copy(selected = it.selected + it.apps.filterNot { it.locked || it.provider }.map(AppRow::packageName))
        }

        refilter()
    }

    fun selectNone() {
        _state.update {
            it.copy(selected = it.selected - it.apps.map(AppRow::packageName).toSet())
        }

        refilter()
    }

    /**
     * Сохраняет выбор. Возвращает true, если он изменился, — тогда вызывающему нужно
     * перезапустить ядро: список приложений задаётся при создании туннеля и на лету
     * не меняется.
     */
    suspend fun save(context: Context): Boolean = withContext(Dispatchers.IO) {
        val store = ServiceStore(context)
        val current = _state.value
        val mode = current.mode ?: return@withContext false

        // С прежним «Все» сравниваем как с «Без VPN» без отметок: для туннеля это одно и то же,
        // перезапускать его незачем.
        val changed = current.selected != saved || mode != savedMode || current.reversed != savedReversed

        store.accessControlMode = mode
        store.accessControlPackages = current.selected
        subscription?.let { if (current.reversed != savedReversed) ConfigAppsStore(context).write(it, current.reversed) }

        saved = current.selected
        savedMode = mode
        savedReversed = current.reversed

        changed
    }

    /**
     * [resort] — пересобрать порядок. При простой отметке галочки его трогать нельзя:
     * список отсортирован «отмеченные сверху», и приложение прыгало бы из середины
     * наверх прямо под пальцем.
     */
    private fun refilter(resort: Boolean = false) {
        val current = _state.value
        val mode = current.mode ?: return
        val query = current.query.trim()
        val decision = PackageFilter.decide(mode, current.selected, profile, current.reversed)

        if (lockedFor != mode) {
            locked = PackageFilter.lockedByProfile(all.map(AppRow::packageName), mode, profile) - reversible
            lockedFor = mode
        }

        val apps = all
            .asSequence()
            .filter { current.showSystem || !it.system }
            .filter {
                query.isEmpty() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .map {
                it.copy(
                    locked = it.packageName in locked,
                    provider = it.packageName in reversible,
                    changed = it.packageName in current.reversed,
                    inTunnel = PackageFilter.inTunnel(it.packageName, decision),
                )
            }
            .toList()
            .let { list ->
                if (!resort) {
                    // Порядок берём прежний: он уже показан человеку.
                    val order = current.apps.withIndex().associate { (i, row) -> row.packageName to i }

                    list.sortedBy { order[it.packageName] ?: Int.MAX_VALUE }
                } else {
                    // Сначала то, что задал конфиг, потом отмеченное человеком: иначе
                    // после поиска непонятно, что уже решено за него.
                    list.sortedWith(
                        compareByDescending<AppRow> { it.locked || it.provider }
                            .thenByDescending { it.packageName in current.selected }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    )
                }
            }

        _state.update { it.copy(apps = apps) }
    }
}

/**
 * Приложения, которым нужен интернет: и для «Приложений», и для своих правил (D-123). Список
 * собирается один раз на процесс (аудит 9б.7): с названиями это секунды на слабом телефоне, а
 * открывают его оба экрана. Заново — когда на телефоне что-то поставили, обновили или удалили
 * (об этом Android говорит сам) и при смене языка: названия переведены.
 */
@Synchronized
internal fun loadApps(context: Context): List<AppRow> {
    val locales = context.resources.configuration.locales.toLanguageTags()
    // `null` — с прошлого вызова набор приложений не менялся.
    val changed = context.packageManager.getChangedPackages(appsSequence)

    if (changed != null) appsSequence = changed.sequenceNumber

    return apps?.takeIf { changed == null && locales == appsLocales }
        ?: queryApps(context).also {
            apps = it
            appsLocales = locales
        }
}

private var apps: List<AppRow>? = null
private var appsSequence = 0
private var appsLocales = ""

private fun queryApps(context: Context): List<AppRow> {
    val pm = context.packageManager

    return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        .asSequence()
        .filter { it.packageName != context.packageName }
        .mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null

            // Без доступа в интернет приложению туннель не нужен; системные службы
            // с uid ниже пользовательского оставляем — у них разрешения не заявлены.
            val wantsInternet = info.requestedPermissions?.contains(INTERNET) == true ||
                app.uid < Process.FIRST_APPLICATION_UID

            if (!wantsInternet) return@mapNotNull null

            AppRow(
                packageName = info.packageName,
                label = app.loadLabel(pm).toString(),
                system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }
        .toList()
}
