package com.github.kr328.clash.service.util

import android.content.Context
import java.io.File
import java.io.IOException

val Context.importedDir: File
    get() = filesDir.resolve("imported")

val Context.pendingDir: File
    get() = filesDir.resolve("pending")

val Context.processingDir: File
    get() = filesDir.resolve("processing")

val File.directoryLastModified: Long?
    get() {
        return walk().map { it.lastModified() }.maxOrNull()
    }

/** About what a subscription with its rule lists, or the geo databases (12.7 MB), may need at once. */
private const val LOW_SPACE = 32L * 1024 * 1024

/**
 * The phone is out of space, or close to it: the cause of a failure nothing else explains (Q-104). The failure
 * itself seldom says so: a directory that could not be created surfaces as ENOENT of the file inside it, and
 * Room's database as a disk I/O error (F-398).
 */
val Context.outOfSpace: Boolean
    get() = filesDir.usableSpace < LOW_SPACE

private const val STAGED = ".new"
private const val OLD = ".old"

/**
 * Puts a copy of [source] in place of this directory so that a process killed at any moment, or a full
 * disk, leaves the old copy or the new one whole. Deleting first and copying after left a subscription
 * without its config, and no update could repair it: the update starts from that directory. A death
 * between the two renames leaves only `<name>.old`; [recoverReplaced] puts it back at the next start.
 */
fun File.replaceWithCopyOf(source: File) {
    val staged = resolveSibling(name + STAGED)
    val old = resolveSibling(name + OLD)

    staged.deleteRecursively()
    try {
        source.copyRecursively(staged)
    } catch (e: Exception) {
        staged.deleteRecursively()
        throw e
    }

    old.deleteRecursively()
    if (exists() && !renameTo(old)) {
        staged.deleteRecursively()
        throw IOException("Cannot move $name aside")
    }
    if (!staged.renameTo(this)) {
        old.renameTo(this)
        staged.deleteRecursively()
        throw IOException("Cannot move the new $name in")
    }
    old.deleteRecursively()
}

/** Finishes what [replaceWithCopyOf] cut off by a death left in this parent directory. */
fun File.recoverReplaced() {
    listFiles().orEmpty().forEach {
        when {
            it.name.endsWith(OLD) -> {
                val target = resolve(it.name.removeSuffix(OLD))
                if (target.exists()) it.deleteRecursively() else it.renameTo(target)
            }
            it.name.endsWith(STAGED) -> it.deleteRecursively()
        }
    }
}