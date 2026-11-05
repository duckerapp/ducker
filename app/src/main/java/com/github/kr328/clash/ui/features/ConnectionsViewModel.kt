package com.github.kr328.clash.ui.features

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.Connection
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who opened a connection: an app with its package for the icon, the core itself, or nobody Android could name. */
data class ConnectionApp(val key: String, val kind: Kind, val label: String = "", val packageName: String? = null) {
    enum class Kind { App, Core, Unknown }
}

data class ConnectionRow(
    val id: String,
    val app: ConnectionApp,
    /** The domain, or the address when the app connected by one. */
    val site: String,
    val port: Int,
    /** «rule → node»: what sent it where. */
    val route: String,
    /** Bytes per second both ways over the last refresh; 0 — quiet. */
    val speed: Long,
    val total: Long,
    val start: Long,
    /** Gone from the core: it stays on the page, dimmed, so a short one can still be read and tapped. */
    val closed: Boolean = false,
)

data class ConnectionsState(
    val loaded: Boolean = false,
    /** What the page shows: the filter applied, the newest first. */
    val rows: List<ConnectionRow> = emptyList(),
    /** Every open connection, before the filter; the closed ones are not counted. */
    val count: Int = 0,
    /** All of them together, bytes per second. */
    val speed: Long = 0,
    /** The filter, narrowed by taps: an app, then a site within it. */
    val app: ConnectionApp? = null,
    val site: String? = null,
    /** The search: an app, a site, a rule or a node. */
    val query: String = "",
)

/**
 * «Подключения» (stage 15): the core's open connections, read about once a second while the page is open and never in
 * the background — the request waits while the app is hidden, as the log's does. The core keeps what closed meanwhile.
 */
class ConnectionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ConnectionsState())
    val state: StateFlow<ConnectionsState> = _state.asStateFlow()

    private var pump: Job? = null
    /** Open and closed rows by id; reset by «Сбросить все». */
    private var all: Map<String, ConnectionRow> = emptyMap()
    /** Moves on «Сбросить все»: a read begun before it would bring back what it closed. */
    private var epoch = 0
    /** When «Сбросить все» was pressed: what it closed comes back from the core as closed, and is not shown. */
    private var resetAt = 0L
    private val apps = HashMap<String, ConnectionApp>()
    private var last: Map<String, Long> = emptyMap()
    private var lastAt = 0L

    fun start(context: Context) {
        if (pump != null) return

        val app = context.applicationContext

        // A fresh page each time, without the old filter; the closing page keeps its rows while it slides away.
        all = emptyMap()
        last = emptyMap()
        apps.clear()
        _state.value = ConnectionsState()

        pump = viewModelScope.launch {
            while (isActive) {
                read(app)
                delay(REFRESH_MS)
            }
        }
    }

    /** The page closed: nothing is read until it opens again. */
    fun stop() {
        pump?.cancel()
        pump = null
    }

    /** A tap on a row narrows the list: to its app, then to its site. */
    fun pick(row: ConnectionRow) = filter {
        when {
            it.app == null -> it.copy(app = row.app)
            it.site == null -> it.copy(site = row.site)
            else -> it
        }
    }

    fun clearApp() = filter { it.copy(app = null, site = null) }

    fun clearSite() = filter { it.copy(site = null) }

    fun search(query: String) = filter { it.copy(query = query) }

    /** «Сбросить все»: apps open new connections by the current rules at once. */
    fun closeAll(context: Context) {
        val app = context.applicationContext

        viewModelScope.launch {
            runCatching { withClash { closeAllConnections() } }
            // The page starts over: what was closed is not kept, unlike a connection an app closes itself.
            epoch++
            all = emptyMap()
            resetAt = System.currentTimeMillis()
            read(app)
        }
    }

    private fun filter(change: (ConnectionsState) -> ConnectionsState) {
        _state.update { change(it).shown() }
    }

    private suspend fun read(context: Context) {
        val began = epoch
        val connections = runCatching { withClash { queryConnections() } }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        val seconds = ((now - lastAt) / 1000.0).takeIf { lastAt != 0L && it > 0 }
        val rows = withContext(Dispatchers.IO) {
            connections.filter { !it.closed || it.start >= resetAt }.map { it.row(appOf(context, it), last[it.id], seconds) }
        }

        if (began != epoch) return

        last = connections.associate { it.id to it.upload + it.download }
        lastAt = now
        all = merge(all, rows)

        val open = rows.filterNot(ConnectionRow::closed)

        _state.update { it.copy(loaded = true, count = open.size, speed = open.sumOf(ConnectionRow::speed)).shown() }
    }

    private fun ConnectionsState.shown() = copy(rows = all.values.filtered(app, site, query))

    private fun appOf(context: Context, connection: Connection): ConnectionApp {
        val key = connectionKey(connection)

        return apps.getOrPut(key) { resolve(context.packageManager, key, connection) }
    }
}

/** Which app a connection belongs to, as the filter compares them. */
internal fun connectionKey(connection: Connection): String = when {
    connection.inner -> "core"
    connection.process.isNotEmpty() -> "package:${connection.process}"
    connection.uid > 0 -> "uid:${connection.uid}"
    else -> "unknown"
}

private fun resolve(pm: PackageManager, key: String, connection: Connection): ConnectionApp {
    if (connection.inner) return ConnectionApp(key, ConnectionApp.Kind.Core)

    // One uid can be several packages (a shared uid): the first names it, as Android's own data usage does.
    val packageName = connection.process.ifEmpty { pm.getPackagesForUid(connection.uid)?.firstOrNull().orEmpty() }

    if (packageName.isNotEmpty()) {
        val label = runCatching { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() }.getOrDefault(packageName)

        return ConnectionApp(key, ConnectionApp.Kind.App, label, packageName)
    }

    // A system service without a package of its own: its uid's name («android.uid.system»).
    val name = connection.uid.takeIf { it > 0 }?.let { pm.getNameForUid(it) }

    return if (name != null) ConnectionApp(key, ConnectionApp.Kind.App, name) else ConnectionApp(key, ConnectionApp.Kind.Unknown)
}

internal fun Connection.row(app: ConnectionApp, before: Long?, seconds: Double?): ConnectionRow {
    val total = upload + download
    val rule = rulePayload.ifEmpty { rule }

    return ConnectionRow(
        id = id,
        app = app,
        site = host.ifEmpty { address },
        port = port,
        route = listOf(rule, chain.firstOrNull().orEmpty()).filter { it.isNotEmpty() }.joinToString(" → "),
        speed = if (before != null && seconds != null && !closed) ((total - before).coerceAtLeast(0) / seconds).toLong() else 0,
        total = total,
        start = start,
        closed = closed,
    )
}

/**
 * The rows read and the closed ones kept: a connection that was there and is gone stays, closed, in its place. Most
 * live for a second — a page load, a check — and were gone before they could be read (stage 15). The core sends the
 * ones that closed between reads, or while the app was hidden, once, as closed.
 */
internal fun merge(before: Map<String, ConnectionRow>, read: List<ConnectionRow>): Map<String, ConnectionRow> {
    val now = read.associateBy { it.id }
    val gone = before.values.filter { it.id !in now }
        .map { if (it.closed) it else it.copy(closed = true, speed = 0) }
        .sortedByDescending { it.start }
        .take(MAX_CLOSED)

    return now + gone.associateBy { it.id }
}

/** The newest first: a new connection comes on top, the others keep their order between refreshes. */
internal fun Collection<ConnectionRow>.filtered(app: ConnectionApp?, site: String?, query: String = ""): List<ConnectionRow> {
    val words = query.trim()

    return filter {
        (app == null || it.app.key == app.key) && (site == null || it.site == site) &&
            (words.isEmpty() || listOfNotNull(it.app.label, it.app.packageName, it.site, it.route).any { field -> field.contains(words, ignoreCase = true) })
    }.sortedWith(compareByDescending<ConnectionRow> { it.start }.thenBy { it.id })
}

private const val REFRESH_MS = 1_000L

/** The closed rows kept, the newest: a page left open while browsing would grow without end. */
private const val MAX_CLOSED = 300
