package com.github.kr328.clash.ui.features

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.service.model.MobileState
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.store.AppStore
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

/** A subscription to choose from: its id and the name «Подписки» shows. */
@Immutable
data class MobileProfile(val uuid: String, val name: String)

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

    fun load(context: Context) {
        val began = epoch
        val app = context.applicationContext

        viewModelScope.launch {
            val settings = ask { queryMobile() } ?: return@launch
            // In the order «Подписки» has them, as the person dragged them.
            val order = withContext(Dispatchers.IO) { AppStore(app).profileOrder }
            val profiles = runCatching { withProfile { queryAll() } }.getOrDefault(emptyList())
                .filter { it.imported }
                .inOrder(order.split(',')) { it.uuid.toString() }
                .map { MobileProfile(it.uuid.toString(), it.shownName) }
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

    /** Another subscription: the list was of its nodes, it starts empty. The first choice turns the switch on. */
    fun pickProfile(uuid: String) {
        val current = _state.value.settings

        if (uuid == current.profile) return

        save { it.copy(on = it.on || it.profile.isEmpty(), profile = uuid, nodes = emptyList()) }

        _state.update { it.copy(members = null) }

        val began = epoch

        viewModelScope.launch {
            val members = members(uuid, _state.value.profiles)

            if (began == epoch) _state.update { it.copy(members = members) }
        }
    }

    fun setNodes(nodes: List<String>) = save { it.copy(nodes = nodes) }

    /** A step of a drag: on screen only; [dropped] saves where it ended. */
    fun move(from: String, to: String) {
        _state.update { page ->
            val nodes = page.settings.nodes.toMutableList()
            val a = nodes.indexOf(from)
            val b = nodes.indexOf(to)

            if (a < 0 || b < 0) return@update page

            nodes.add(b, nodes.removeAt(a))

            page.copy(settings = page.settings.copy(nodes = nodes))
        }
    }

    fun dropped() = save { it }

    private fun save(change: (MobileState) -> MobileState) {
        epoch++

        val began = epoch
        val next = change(_state.value.settings)

        _state.update { it.copy(settings = next) }

        viewModelScope.launch {
            val saved = saving.withLock { ask { setMobile(next.on, next.profile, next.nodes) } } ?: return@launch

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
