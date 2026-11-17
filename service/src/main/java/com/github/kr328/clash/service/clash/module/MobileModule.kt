package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.MobileState
import com.github.kr328.clash.service.model.NetworkKind
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.sendSelectorChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * «Мобильная сеть» (D-353, D-355, D-423, D-424): off Wi-Fi, the subscription chosen for the mobile network becomes the
 * active one with the first node of its list that answers; back on Wi-Fi, what Wi-Fi had before. While it holds, the
 * list's node in use is watched and gives way to the next that answers once it stops (D-533).
 *
 * Without haste (D-355): a new kind of network must hold a minute with the screen off — a weak Wi-Fi at the door comes
 * and goes — and seven seconds with it on; any change starts the count again. A pick of the person's own, a
 * subscription or a node of the main group, holds until the kind changes ([manual]). What was acted on is kept in
 * [ServiceStore], so a restart of the VPN neither repeats a switch nor forgets what to bring back.
 */
class MobileModule(service: Service, private val configReady: () -> Boolean = { true }) : Module<Unit>(service) {
    private val store = ServiceStore(service)
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val power = service.getSystemService<PowerManager>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val networks = ConcurrentHashMap<Network, NetworkCapabilities>()

    /** Moves with every pick of the person's own and every switch begun: a switch whose number moved stops. */
    private val epoch = AtomicInteger()

    /** The kind of network the phone is on now, and since when (`elapsedRealtime`). */
    @Volatile
    var kind = NetworkKind.None
        private set

    @Volatile
    private var since = SystemClock.elapsedRealtime()

    /** The page changed the settings. */
    @Volatile
    private var changed = false

    override suspend fun run() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                networks[network] = capabilities

                observe()
            }

            override fun onLost(network: Network) {
                networks.remove(network)

                observe()
            }
        }

        val screen = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // Bounded: only activate reads it, and it first drains the loads that came before. Only those pile up, so a
        // full buffer drops only loads no switch waits for. A refused load comes as a failed update (`RELOAD_FAILED`).
        val loaded = receiveBroadcast(capacity = Channel.BUFFERED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
            addAction(Intents.ACTION_PROFILE_UPDATE_FAILED)
        }

        val watching = runCatching { connectivity.registerNetworkCallback(REQUEST, callback) }
            .onFailure { Log.w("Mobile: network callback: $it", it) }
            .isSuccess

        active = this
        note = noteFor(service, store.mobileShownNow)

        try {
            // The networks there are come within milliseconds of registering (D-109): the start acts on all of them.
            delay(SETTLE_MS)

            // The tunnel's first config may still be loading: the core has no groups yet, a switch made now set no
            // node while Home said it had, and saved no Wi-Fi node to bring back.
            if (!awaitReady(LOAD_WAIT_MS, CONFIG_POLL_MS, configReady)) Log.w("Mobile: no config after a minute, acting anyway")

            // Also at every start: the pick may be older than the fetch at the pick, or that fetch may have failed.
            if (store.mobileOn) store.mobileProfile?.let { ProfileProcessor.prefetch(service, it) }

            // At the start nothing waits: the VPN was just turned on, or the phone has just come up.
            var starting = true
            var failed = false

            // After none of the list answered: the checks made since, and when the next is due (0 — not set yet).
            var rechecks = 0
            var recheckAt = 0L

            // While a node of the list is in use: its misses in a row, and when it is checked next (0 — not set yet).
            var misses = 0
            var watchAt = 0L

            while (true) {
                if (changed) {
                    changed = false
                    rechecks = 0
                    recheckAt = 0
                    misses = 0
                    watchAt = 0

                    if (!settingsChanged(loaded)) failed = true
                }

                val now = kind
                val settled = NetworkKind.entries.getOrElse(store.mobileSettled) { NetworkKind.None }
                val wait = mobileWait(now, settled, starting, failed, SystemClock.elapsedRealtime() - since, power?.isInteractive != false)
                val clock = SystemClock.elapsedRealtime()

                // The mobile choice holds on the settled mobile network; `wait` is UNTIL_WAKE then. None of the list
                // answered at the switch — checked again, as a node often answers only seconds after the network
                // comes. One did — it is watched, and gives way to the next that answers once it stops (D-533).
                val holding = now == NetworkKind.Mobile && settled == NetworkKind.Mobile
                val shown = if (holding) store.mobileShownNow else MobileState.Shown.None
                val silent = shown == MobileState.Shown.NoneAnswered
                val watching = shown == MobileState.Shown.InEffect && store.mobileNodes.isNotEmpty()

                if (!silent) {
                    rechecks = 0
                    recheckAt = 0
                } else if (recheckAt == 0L) {
                    recheckAt = clock + recheckIn(rechecks)
                }

                if (!watching) {
                    misses = 0
                    watchAt = 0
                } else if (watchAt == 0L) {
                    watchAt = clock + watchIn(misses, power?.isInteractive != false)
                }

                val due = if (silent) recheckAt else if (watching) watchAt else 0L

                if (due != 0L && clock >= due) {
                    if (silent) {
                        recheck()

                        rechecks++
                        recheckAt = 0
                    } else {
                        misses = watch(misses)
                        watchAt = 0
                    }

                    continue
                }

                if (wait != 0L) {
                    if (wait == UNTIL_WAKE) {
                        starting = false
                        failed = false
                    }

                    val screenOn = pause(screen, if (due != 0L) due - clock else wait.coerceAtLeast(0))

                    // The screen coming on is when the person wants the internet: the check goes now.
                    if (screenOn && silent) recheckAt = SystemClock.elapsedRealtime()
                    if (screenOn && watching) watchAt = SystemClock.elapsedRealtime()

                    continue
                }

                starting = false
                failed = !act(now, settled, loaded)
            }
        } finally {
            withContext(NonCancellable) {
                active = null

                if (watching) runCatching { connectivity.unregisterNetworkCallback(callback) }
            }
        }
    }

    /** The settings changed on the page. */
    fun changed() {
        changed = true

        wake.trySend(Unit)
    }

    /**
     * Until something changes: the network, the screen coming on, the settings, or [timeout] ms when above zero.
     * True — the screen came on.
     */
    private suspend fun pause(screen: ReceiveChannel<Intent>, timeout: Long): Boolean {
        val wait: suspend () -> Boolean = {
            select {
                wake.onReceive { false }
                screen.onReceive { true }
            }
        }

        return if (timeout > 0) withTimeoutOrNull(timeout) { wait() } == true else wait()
    }

    private fun observe() {
        val next = kindOf(networks.values.minByOrNull(::rank))

        if (next == kind) return

        Log.i("Mobile: network ${kind.name} -> ${next.name}")

        kind = next
        since = SystemClock.elapsedRealtime()

        wake.trySend(Unit)
    }

    /** False — the switch failed, and the kind stays unsettled for the next try. */
    private suspend fun act(now: NetworkKind, settled: NetworkKind, loaded: ReceiveChannel<Intent>): Boolean {
        store.mobileSettled = now.ordinal

        val action = mobileAction(now, configured(), store.mobileBackProfile != null)

        Log.i("Mobile: ${settled.name} -> ${now.name}, ${action.name}")

        val done = when (action) {
            MobileAction.Apply -> apply(loaded)
            MobileAction.Restore -> restore(loaded)
            MobileAction.Nothing -> true
        }

        if (!done) {
            Log.w("Mobile: the switch to ${now.name} failed, again at the next screen-on or change")

            store.mobileSettled = settled.ordinal
        }

        return done
    }

    private suspend fun settingsChanged(loaded: ReceiveChannel<Intent>): Boolean {
        if (!store.mobileOn) return true

        // Set up on the mobile network itself: the person's new choice applies at once, a pick of theirs before it
        // included — it is them who changed it.
        if (kind == NetworkKind.Mobile && configured()) {
            store.mobileSettled = NetworkKind.Mobile.ordinal

            if (!apply(loaded)) {
                // Unsettled, not the kind it was: that was usually Mobile already, and the loop then never tried again.
                store.mobileSettled = NetworkKind.None.ordinal

                return false
            }
        }

        return true
    }

    private suspend fun configured(): Boolean =
        store.mobileOn && store.mobileProfile?.let { ImportedDao().exists(it) } == true

    /** False — it failed. A pick of the person's own that came meanwhile is not a failure: the switch gave way. */
    private suspend fun apply(loaded: ReceiveChannel<Intent>): Boolean {
        val target = store.mobileProfile ?: return true
        val started = epoch.incrementAndGet()

        if (store.mobileBackProfile == null) saveBack(store)

        if (store.activeProfile != target && !activate(target, started, loaded)) return epoch.get() != started
        if (epoch.get() != started) return true

        val main = mainGroup()
        val members = main?.let { members(it) }.orEmpty()
        val list = store.mobileNodes.filter { it in members }

        // Nodes are set, yet the core has no main group: nothing was switched, so nothing may say it was.
        if (main == null && store.mobileNodes.isNotEmpty()) return false

        if (main == null || list.isEmpty()) {
            show(MobileState.Shown.InEffect)

            return true
        }

        pick(main, list.first())
        show(MobileState.Shown.InEffect)
        settle(main, list, started)

        return true
    }

    /** Sets the first of [list] that answers; none — the node in use stays, and the note says so. */
    private suspend fun settle(main: String, list: List<String>, started: Int) {
        val answered = firstAnswering(list)

        if (epoch.get() != started) return

        Log.i("Mobile: ${answered ?: "none"} of ${list.size} answered")

        if (answered == null) {
            if (store.mobileShown != MobileState.Shown.NoneAnswered.ordinal) show(MobileState.Shown.NoneAnswered)

            return
        }

        // Not always the first: after a fallback the one in use is further down the list.
        if (answered != current(main)) pick(main, answered)
        if (store.mobileShown != MobileState.Shown.InEffect.ordinal) show(MobileState.Shown.InEffect)
    }

    /** The list once more after none of it answered; the loop has seen the mobile choice still in effect. */
    private suspend fun recheck() {
        val started = epoch.incrementAndGet()
        val main = mainGroup() ?: return
        val members = members(main)
        val list = store.mobileNodes.filter { it in members }

        Log.i("Mobile: the list again")

        if (list.isNotEmpty()) settle(main, list, started)
    }

    private suspend fun restore(loaded: ReceiveChannel<Intent>): Boolean {
        val back = store.mobileBackProfile ?: return true
        val group = store.mobileBackGroup
        val node = store.mobileBackNode
        val started = epoch.incrementAndGet()

        // Home must not say «мобильная сеть» on Wi-Fi, whatever comes of the switch.
        show(MobileState.Shown.None)

        if (ImportedDao().exists(back)) {
            if (store.activeProfile != back && !activate(back, started, loaded)) return epoch.get() != started
            if (epoch.get() != started) return true

            // The same subscription on both sides keeps one pick per group: the list's node is in it now (D-423).
            if (group.isNotEmpty() && node.isNotEmpty()) pick(group, node)
        }

        // Only once done: a switch that failed keeps what to bring back for its next try.
        forget(service)

        return true
    }

    /** Makes [uuid] the active subscription and waits for the core to load it; false — it did not, or a pick came. */
    private suspend fun activate(uuid: UUID, started: Int, loaded: ReceiveChannel<Intent>): Boolean {
        // Loads that came before say nothing about this one.
        while (loaded.tryReceive().isSuccess) Unit

        if (epoch.get() != started) return false

        val before = store.activeProfile

        ProfileProcessor.active(service, uuid)

        val done = withTimeoutOrNull(LOAD_WAIT_MS) {
            var outcome: Boolean? = null

            while (outcome == null) {
                val intent = loaded.receive()

                outcome = loadOutcome(
                    uuid.toString(),
                    intent.getStringExtra(Intents.EXTRA_UUID),
                    intent.action == Intents.ACTION_PROFILE_LOADED,
                    intent.getStringExtra(Intents.EXTRA_FAIL_REASON),
                )
            }

            outcome
        } == true

        // A config the core refuses keeps the previous one running (D-207); its card names the error. The mark of the
        // active subscription goes back to it too: else Home named a subscription the core does not run, and the
        // next try took the mark for a done switch.
        if (!done) {
            Log.w("Mobile: $uuid refused or not loaded in time")

            if (epoch.get() == started && before != null && store.activeProfile == uuid) ProfileProcessor.active(service, before)
        }

        return done
    }

    /**
     * The list's node in use, checked; returns its misses in a row. At [WATCH_MISSES] the list is checked from the top
     * and the first that answers is set — a node of the person's own, not of the list, is not watched.
     */
    private suspend fun watch(misses: Int): Int {
        val started = epoch.incrementAndGet()
        val main = mainGroup() ?: return 0
        val members = members(main)
        val list = store.mobileNodes.filter { it in members }
        val node = current(main)?.takeIf { it in list } ?: return 0

        if (withContext(Dispatchers.IO) { Clash.probeProxy(node).ok }) return 0
        if (epoch.get() != started) return 0
        if (misses + 1 < WATCH_MISSES) return misses + 1

        Log.i("Mobile: $node stopped answering")

        settle(main, list, started)

        return 0
    }

    private fun current(group: String): String? =
        runCatching { Clash.queryGroup(group, ProxySort.Default).now }.getOrNull()?.ifEmpty { null }

    /** The first of [list] that answers, in the person's order; the checks go a few at a time. */
    private suspend fun firstAnswering(list: List<String>): String? = coroutineScope {
        for (batch in list.chunked(CHECKS_AT_ONCE)) {
            val checks = batch.map { async(Dispatchers.IO) { Clash.probeProxy(it).ok } }
            val found = batch.indices.firstOrNull { checks[it].await() }

            if (found != null) return@coroutineScope batch[found]
        }

        null
    }

    private suspend fun pick(group: String, node: String) {
        if (!Clash.patchSelector(group, node)) return

        store.activeProfile?.let { SelectionDao().setSelected(Selection(it, group, node)) }

        service.sendSelectorChanged()
    }

    private fun show(shown: MobileState.Shown) {
        store.mobileShown = shown.ordinal
        note = noteFor(service, store.mobileShownNow)

        service.sendSelectorChanged()
    }

    companion object {
        /** The running module, while the VPN is up. */
        @Volatile
        var active: MobileModule? = null
            private set

        /** After the node in the VPN notification: « · мобильная сеть» while the mobile choice is in effect. */
        @Volatile
        var note: String = ""
            private set

        /**
         * A pick of the person's own — a subscription, or a node of the main group — made with the VPN on or off, just
         * before it takes effect. It holds until the kind of network changes: on the mobile network Wi-Fi still gets
         * back what it had; on Wi-Fi the pick is what Wi-Fi has now.
         */
        fun manual(context: Context) {
            val store = ServiceStore(context)

            if (!store.mobileOn || store.mobileProfile == null) return

            val module = active

            module?.epoch?.incrementAndGet()

            when (module?.kind ?: kindNow(context)) {
                NetworkKind.Mobile -> {
                    if (store.mobileBackProfile == null) saveBack(store)

                    store.mobileSettled = NetworkKind.Mobile.ordinal
                }
                NetworkKind.Wifi -> {
                    store.mobileBackProfile = null
                    store.mobileBackGroup = ""
                    store.mobileBackNode = ""
                    store.mobileSettled = NetworkKind.Wifi.ordinal
                }
                NetworkKind.None -> return
            }

            store.mobileShown = MobileState.Shown.None.ordinal
            note = ""

            Log.i("Mobile: a pick of the person's own")
        }

        /** Nothing to bring back and nothing to show: the switch was turned off, or Wi-Fi is back. */
        fun forget(context: Context) {
            val store = ServiceStore(context)

            store.mobileBackProfile = null
            store.mobileBackGroup = ""
            store.mobileBackNode = ""
            store.mobileShown = MobileState.Shown.None.ordinal
            note = ""

            context.sendSelectorChanged()
        }

        /** For the page: the settings, the network now, and the subscription Wi-Fi has. */
        fun state(context: Context): MobileState {
            val store = ServiceStore(context)

            return MobileState(
                on = store.mobileOn,
                profile = store.mobileProfile?.toString().orEmpty(),
                nodes = store.mobileNodes,
                now = active?.kind ?: kindNow(context),
                wifi = (store.mobileBackProfile ?: store.activeProfile)?.toString().orEmpty(),
            )
        }

        /** The main group, as Home's node sheet has it (`HomeActivity`): the first selectable one. */
        fun mainGroup(): String? = runCatching { Clash.queryGroupNames(true).firstOrNull() }.getOrNull()

        fun members(group: String): List<String> =
            runCatching { Clash.queryGroup(group, ProxySort.Default).proxies.map { it.name } }.getOrDefault(emptyList())

        /** What Wi-Fi has now, to bring back: the subscription, and the main group's pick while the core runs it. */
        private fun saveBack(store: ServiceStore) {
            val active = store.activeProfile ?: return
            val main = if (StatusProvider.serviceRunning) mainGroup() else null
            val now = main?.let { runCatching { Clash.queryGroup(it, ProxySort.Default).now }.getOrNull() }.orEmpty()

            store.mobileBackProfile = active
            store.mobileBackGroup = if (now.isEmpty()) "" else main.orEmpty()
            store.mobileBackNode = now
        }

        /** With the VPN off the system's own network is the phone's network. */
        private fun kindNow(context: Context): NetworkKind {
            val connectivity = context.getSystemService<ConnectivityManager>() ?: return NetworkKind.None
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)

            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return NetworkKind.None

            return kindOf(capabilities)
        }

        private fun noteFor(context: Context, shown: MobileState.Shown): String = when (shown) {
            MobileState.Shown.None -> ""
            MobileState.Shown.InEffect -> " · " + context.getString(R.string.mobile_note)
            MobileState.Shown.NoneAnswered -> " · " + context.getString(R.string.mobile_note_silent)
        }

        private val REQUEST = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        private const val SETTLE_MS = 1_500L
        private const val CONFIG_POLL_MS = 250L
        private const val LOAD_WAIT_MS = 60_000L
        private const val CHECKS_AT_ONCE = 5
    }
}

/**
 * The network traffic goes through: Android prefers a validated one, Wi-Fi among those. A Wi-Fi without internet at
 * the door is not it, the mobile network still carries the traffic then.
 */
private fun rank(capabilities: NetworkCapabilities): Int {
    val transport = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
        else -> 5
    }

    return transport + if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 0 else 10
}

private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind = if (capabilities == null) NetworkKind.None else
    networkKind(
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )

/** A metered network that is not the phone's own SIM is another phone's mobile internet, or marked so by hand (Q-116). */
internal fun networkKind(cellular: Boolean, metered: Boolean): NetworkKind =
    if (cellular || metered) NetworkKind.Mobile else NetworkKind.Wifi

internal enum class MobileAction { Nothing, Apply, Restore }

/** What a settled change to [now] does: the mobile choice when it is set up, Wi-Fi's own back when it was saved. */
internal fun mobileAction(now: NetworkKind, configured: Boolean, back: Boolean): MobileAction = when (now) {
    NetworkKind.Mobile -> if (configured) MobileAction.Apply else MobileAction.Nothing
    NetworkKind.Wifi -> if (back) MobileAction.Restore else MobileAction.Nothing
    NetworkKind.None -> MobileAction.Nothing
}

/** How much longer a new network must hold, ms: a minute in the pocket, seconds with the screen on (D-355). */
internal fun holdLeft(heldMs: Long, screenOn: Boolean): Long =
    ((if (screenOn) SCREEN_ON_HOLD_MS else POCKET_HOLD_MS) - heldMs).coerceAtLeast(0)

/**
 * How long the loop waits before it switches: 0 — it switches now; [UNTIL_WAKE] — until the next change of network,
 * screen-on or change of settings. A failed switch waits for such a wake too: tried again at once, it reloaded the core
 * in a loop — `apply` fails at once while the core has no main group.
 */
internal fun mobileWait(
    now: NetworkKind,
    settled: NetworkKind,
    starting: Boolean,
    failed: Boolean,
    heldMs: Long,
    screenOn: Boolean,
): Long = when {
    now == NetworkKind.None || now == settled || failed -> UNTIL_WAKE
    starting -> 0
    else -> holdLeft(heldMs, screenOn)
}

internal const val UNTIL_WAKE = -1L

/**
 * What a broadcast says of [target]'s load: true — loaded; false — the core refused it and runs the previous one
 * (`RELOAD_FAILED`), so nothing waits the minute out; null — about another subscription, or a failed download.
 */
internal fun loadOutcome(target: String, uuid: String?, loaded: Boolean, reason: String?): Boolean? = when {
    uuid != target -> null
    loaded -> true
    reason?.startsWith(Failure.RELOAD) == true -> false
    else -> null
}

/** When the list is checked again after none of it answered: 30 s, then twice as long each time, at most 5 min. */
internal fun recheckIn(done: Int): Long = (RECHECK_FIRST_MS shl done.coerceIn(0, 4)).coerceAtMost(RECHECK_MAX_MS)

internal const val RECHECK_FIRST_MS = 30_000L
internal const val RECHECK_MAX_MS = 300_000L

/**
 * When the list's node in use is checked next: soon after a miss, to confirm it; else a minute with the screen on, five
 * in the pocket — each check wakes the radio and spends mobile data, and the screen coming on checks at once.
 */
internal fun watchIn(misses: Int, screenOn: Boolean): Long = when {
    misses > 0 -> WATCH_CONFIRM_MS
    screenOn -> WATCH_MS
    else -> WATCH_POCKET_MS
}

internal const val WATCH_MS = 60_000L
internal const val WATCH_POCKET_MS = 300_000L
internal const val WATCH_CONFIRM_MS = 10_000L
internal const val WATCH_MISSES = 2

/** Waits until [ready], looking every [pollMs], at most [timeoutMs]; false — it never was. */
internal suspend fun awaitReady(timeoutMs: Long, pollMs: Long, ready: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMs) { while (!ready()) delay(pollMs) } != null

// Starting values, to tune on the phone (D-355).
internal const val POCKET_HOLD_MS = 60_000L
internal const val SCREEN_ON_HOLD_MS = 7_000L
