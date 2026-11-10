package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.service.util.PackageFilter
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Apps installed while the tunnel is up (D-454).
 *
 * Android works out who goes through the tunnel when the interface is built, so an app the filter names — a bank
 * from the provider's exclusions — went through the tunnel until the next connect. This module only reports the new
 * packages Android tells of, joined when they come in a row; whether the interface is rebuilt for them, the service
 * decides with [namedBy].
 */
class AppInstallModule(service: Service) : Module<Set<String>>(service) {
    override suspend fun run() {
        val added = receiveBroadcast(false) {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }

        while (true) {
            enqueueEvent(
                added.joined(JOIN_MS) {
                    installedPackage(it.action, it.getBooleanExtra(Intent.EXTRA_REPLACING, false), it.data?.schemeSpecificPart)
                }
            )
        }
    }
}

/** A store installs several apps in a row: they make one rebuild, not one each. */
internal const val JOIN_MS = 2_000L

/**
 * The package a broadcast newly installs, or null. An update keeps its uid, so the interface already has it right; a
 * removal leaves an unused uid, which is harmless and gone at the next connect.
 */
internal fun installedPackage(action: String?, replacing: Boolean, name: String?): String? =
    name.takeIf { action == Intent.ACTION_PACKAGE_ADDED && !replacing }

/** The first item [pick] takes and whatever else it takes within [windowMs] of it. */
internal suspend fun <T, R : Any> ReceiveChannel<T>.joined(windowMs: Long, pick: (T) -> R?): Set<R> {
    val picked = mutableSetOf<R>()

    while (picked.isEmpty()) pick(receive())?.let(picked::add)

    withTimeoutOrNull(windowMs) { for (item in this@joined) pick(item)?.let(picked::add) }

    return picked
}

/**
 * Which of the installed apps the filter's [decision] lists: the interface was built without them, so they are on
 * the wrong side of the tunnel. Any other install changes nothing and must not rebuild it.
 */
internal fun Set<String>.namedBy(decision: PackageFilter.Decision): List<String> =
    filter { it in (decision.allowed ?: decision.denied) }
