package com.github.kr328.clash.ui.features

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.service.model.MobileState
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.store.AppStore
import com.github.kr328.clash.ui.settings.SettingsStore
import com.github.kr328.clash.ui.subscriptions.grouped
import com.github.kr328.clash.ui.subscriptions.inOrder
import com.github.kr328.clash.ui.subscriptions.shownName
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A subscription to choose from: its id, the name «Подписки» shows, and the marks it has there. */
@Immutable
data class MobileProfile(val uuid: String, val name: String, val active: Boolean = false, val favorite: Boolean = false)

/**
 * «Мобильная сеть» (D-423, D-424) for its card and page. [members] — the chosen subscription's main group, to pick
 * from and to mark list entries the subscription no longer has; `null` while it is read.
 */
@Immutable
data class MobilePage(
    val loaded: Boolean = false,
    val settings: MobileState = MobileState(),
    val profiles: List<MobileProfile> = emptyList(),
    val members: List<String>? = null,
) {
    /** The chosen subscription's name; `null` — none chosen, or it was deleted. */
    val profileName: String? get() = profiles.firstOrNull { it.uuid == settings.profile }?.name

    val wifiName: String? get() = profiles.firstOrNull { it.uuid == settings.wifi }?.name
}

class MobileViewModel : ViewModel() {
    private val _state = MutableStateFlow(MobilePage())
    val state: StateFlow<MobilePage> = _state.asStateFlow()

    /** Saves go one after another, in the order they were made: each carries all the settings. */
    private val saving = Mutex()

    /** Moves with every change: a read begun before it would put back what was just changed. */
    private var epoch = 0

    private var pump: Job? = null

    /** The list as a drag found it: a drag that ends where it began is not an edit. */
    private var dragged: List<String>? = null

    fun load(context: Context) {
        val began = epoch
        val app = context.applicationContext

        viewModelScope.launch {
            val settings = ask { queryMobile() } ?: return@launch
            // In the order «Подписки» shows them: the active one, the favourites, the rest, each as the person dragged.
            val order = withContext(Dispatchers.IO) { AppStore(app).profileOrder }
            val favorites = withContext(Dispatchers.IO) { SettingsStore(app).favoriteProfiles }
            val profiles = runCatching { withProfile { queryAll() } }.getOrDefault(emptyList())
                .filter { it.imported }
                .inOrder(order.split(',')) { it.uuid.toString() }
                .map { MobileProfile(it.uuid.toString(), it.shownName, it.active, it.uuid.toString() in favorites) }
                .grouped({ it.active }, { it.favorite })
            val members = members(settings.profile, profiles)

            if (began == epoch) _state.value = MobilePage(true, settings, profiles, members)
        }
    }

    /** While the page is open the network can change under it: «сейчас» follows. */
    fun start(context: Context) {
        load(context)

        if (pump != null) return

        pump = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_MS)

                val now = ask { queryMobile() } ?: continue

                _state.update { it.copy(settings = it.settings.copy(now = now.now, wifi = now.wifi)) }
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null
    }

    fun turn(on: Boolean) = save { it.copy(on = on) }

    /** «Список провайдера» (D-534): on brings the provider's list back with switching on; off keeps the list as it is. */
    fun turnProvider(on: Boolean) = save { if (on) it.copy(on = true, provider = true) else it.copy(provider = false) }

    /**
     * Another subscription: the list was of its nodes, it starts empty, or with its provider's list (D-534). The first
     * choice turns the switch on; «Не выбрана» ([uuid] empty) turns it off — with nothing to switch to it would do
     * nothing anyway.
     */
    fun pickProfile(uuid: String) {
        val current = _state.value.settings

        if (uuid == current.profile) return

        save {
            it.copy(on = uuid.isNotEmpty() && (it.on || it.profile.isEmpty()), profile = uuid, nodes = emptyList(), provider = true)
        }

        _state.update { it.copy(members = null) }

        val began = epoch

        viewModelScope.launch {
            val members = members(uuid, _state.value.profiles)

            if (began == epoch) _state.update { it.copy(members = members) }
        }
    }

    /** The person's own list: an edit turns «Список провайдера» off, a sheet closed without one does not. */
    fun setNodes(nodes: List<String>) = save { it.copy(nodes = nodes, provider = it.provider && nodes == it.nodes) }

    /** A step of a drag: on screen only; [dropped] saves where it ended. */
    fun move(from: String, to: String) {
        if (dragged == null) dragged = _state.value.settings.nodes

        _state.update { page ->
            val nodes = page.settings.nodes.toMutableList()
            val a = nodes.indexOf(from)
            val b = nodes.indexOf(to)

            if (a < 0 || b < 0) return@update page

            nodes.add(b, nodes.removeAt(a))

            page.copy(settings = page.settings.copy(nodes = nodes))
        }
    }

    fun dropped() {
        val edited = dragged != null && dragged != _state.value.settings.nodes

        dragged = null

        save { if (edited) it.copy(provider = false) else it }
    }

    private fun save(change: (MobileState) -> MobileState) {
        epoch++

        val began = epoch
        val next = change(_state.value.settings)

        _state.update { it.copy(settings = next) }

        viewModelScope.launch {
            val saved = saving.withLock { ask { setMobile(next.on, next.profile, next.nodes, next.provider) } } ?: return@launch

            if (began == epoch) _state.update { it.copy(settings = saved) }
        }
    }

    private suspend fun members(profile: String, profiles: List<MobileProfile>): List<String> {
        if (profiles.none { it.uuid == profile }) return emptyList()

        return ask { queryMobileNodes(profile) } ?: emptyList()
    }

    private suspend fun <T> ask(call: suspend IClashManager.() -> T): T? = runCatching { withClash { call() } }.getOrNull()
}

private const val REFRESH_MS = 3_000L
