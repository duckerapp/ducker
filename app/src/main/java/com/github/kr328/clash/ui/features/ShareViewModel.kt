package com.github.kr328.clash.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.service.model.ShareState
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * «Раздача по Wi-Fi» for its card on «Возможности» (D-419–D-421). The service turns it off by itself when the network
 * changes, and the VPN can stop from the shade, so the card asks again every couple of seconds while the page is open.
 */
class ShareViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private var pump: Job? = null

    /** Moves on every tap: a read begun before it would show the switch as it was. */
    private var epoch = 0

    fun start() {
        if (pump != null) return

        pump = viewModelScope.launch {
            while (isActive) {
                val began = epoch

                ask { queryShare() }?.let { if (began == epoch) _state.value = it }

                delay(REFRESH_MS)
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null
    }

    fun turn(on: Boolean) = act { setShare(on) }

    fun password(on: Boolean) = act { setSharePassword(on) }

    private fun act(call: suspend IClashManager.() -> ShareState) {
        epoch++

        viewModelScope.launch { ask(call)?.let { _state.value = it } }
    }

    private suspend fun ask(call: suspend IClashManager.() -> ShareState): ShareState? =
        runCatching { withClash { call() } }.getOrNull()
}

private const val REFRESH_MS = 2_000L
