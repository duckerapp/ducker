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
)

data class ConnectionsState(
    val loaded: Boolean = false,
    /** What the page shows: the filter applied, the newest first. */
    val rows: List<ConnectionRow> = emptyList(),
    /** Every open connection, before the filter. */
    val count: Int = 0,
    /** All of them together, bytes per second. */
    val speed: Long = 0,
    /** The filter, narrowed by taps: an app, then a site within it. */
    val app: ConnectionApp? = null,
    val site: String? = null,
)

/**
 * «Подключения» (stage 15): the core's open connections, read about once a second while the page is open and never in
 * the background — the request waits while the app is hidden, as the log's does.
 */
class ConnectionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ConnectionsState())
    val state: StateFlow<ConnectionsState> = _state.asStateFlow()

    private var pump: Job? = null
    private var all: List<ConnectionRow> = emptyList()
    private val apps = HashMap<String, ConnectionApp>()
    private var last: Map<String, Long> = emptyMap()
    private var lastAt = 0L

    fun start(context: Context) {
        if (pump != null) return

        val app = context.applicationContext

        // A fresh page each time, without the old filter; the closing page keeps its rows while it slides away.
        all = emptyList()
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

    /** «Сбросить все»: apps open new connections by the current rules at once. */
    fun closeAll(context: Context) {
        val app = context.applicationContext

        viewModelScope.launch {
            runCatching { withClash { closeAllConnections() } }
            read(app)
        }
    }

    private fun filter(change: (ConnectionsState) -> ConnectionsState) {
        _state.update { change(it).shown() }
    }

    private suspend fun read(context: Context) {
        val connections = runCatching { withClash { queryConnections() } }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        val seconds = ((now - lastAt) / 1000.0).takeIf { lastAt != 0L && it > 0 }
        val rows = withContext(Dispatchers.IO) {
            connections.map { it.row(appOf(context, it), last[it.id], seconds) }
        }

        last = connections.associate { it.id to it.upload + it.download }
        lastAt = now
        all = rows

        _state.update { it.copy(loaded = true, count = rows.size, speed = rows.sumOf(ConnectionRow::speed)).shown() }
    }

    private fun ConnectionsState.shown() = copy(rows = all.filtered(app, site))

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
        speed = if (before != null && seconds != null) ((total - before).coerceAtLeast(0) / seconds).toLong() else 0,
        total = total,
        start = start,
    )
}

/** The newest first: a new connection comes on top, the others keep their order between refreshes. */
internal fun List<ConnectionRow>.filtered(app: ConnectionApp?, site: String?): List<ConnectionRow> =
    filter { (app == null || it.app.key == app.key) && (site == null || it.site == site) }
        .sortedWith(compareByDescending<ConnectionRow> { it.start }.thenBy { it.id })

private const val REFRESH_MS = 1_000L
