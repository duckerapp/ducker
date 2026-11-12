package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.service.model.UserRule
import com.github.kr328.clash.service.util.PackageFilter
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import java.io.File
import java.util.UUID

/**
 * The config's apps whose provider decision the person reversed, per subscription (D-471): a file per subscription,
 * one package per line, beside the person's rules and for the same reason — the profile's directory is rebuilt at
 * every update. Written by «Приложения» in the app's process, read by the service at every build of the interface.
 */
class ConfigAppsStore(private val context: Context) {
    private val dir = context.filesDir.resolve("config-apps")

    /** `ducker-override-user: on` in the subscription's last download: its config apps stay as the provider set them. */
    fun locked(uuid: UUID): Boolean = ProviderInfo.read(context.importedDir.resolve(uuid.toString())).appsLocked

    /** What the tunnel and both screens apply for [uuid] (D-473): the reversals and the apps with rules, none if locked. */
    fun changes(uuid: UUID): PackageFilter.Changes =
        if (locked(uuid)) PackageFilter.Changes()
        else PackageFilter.Changes(read(uuid), UserRulesStore(context).read(uuid).ruledApps())

    fun read(uuid: UUID): Set<String> = runCatching {
        file(uuid).readLines().filterTo(HashSet()) { it.isNotBlank() }
    }.getOrDefault(emptySet())

    fun write(uuid: UUID, packages: Set<String>) {
        if (packages.isEmpty()) {
            delete(uuid)

            return
        }

        dir.mkdirs()

        // Through a temporary file: the service may read it that very second.
        val temp = File(dir, "$uuid.tmp")

        temp.writeText(packages.sorted().joinToString("\n"))
        temp.renameTo(file(uuid))
    }

    fun delete(uuid: UUID) {
        file(uuid).delete()
    }

    /**
     * A `ducker-override-user: on` that came later wins (D-471): the person's changes are dropped, not kept aside,
     * so if the header goes away again the rows start from the provider's choice. Called where a download of the
     * subscription is committed into [profileDir].
     */
    fun dropIfLocked(uuid: UUID, profileDir: File) {
        if (ProviderInfo.read(profileDir).appsLocked) delete(uuid)
    }

    private fun file(uuid: UUID) = File(dir, uuid.toString())
}

/** The apps the person's rules name. */
fun List<UserRule>.ruledApps(): Set<String> = filter { it.type == UserRule.Type.App }.mapTo(HashSet()) { it.value }
