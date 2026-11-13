package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.PendingDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The core keeps one global set of age keys, read by every config parse and by every timed update of a proxy
 * provider of the running config (`component/age/age.go`). One subscription's key there broke the others: a
 * download of another replaced the running one's key, and a reload during a download gave it the wrong key
 * (D-503). So the core gets every subscription's key at once; age opens a file only with
 * the key it was encrypted to, the others just do not match.
 */
object AgeKeys {
    private val lock = Mutex()
    private var sent: String? = null

    /** Query and send under one lock: an older query sent last would drop a key just added. */
    suspend fun apply() = lock.withLock {
        val keys = joined(ImportedDao().queryAgeSecretKeys() + PendingDao().queryAgeSecretKeys(), Clash::veritySecretKeys)

        // Sent only on a change: the core reads the set from other goroutines while it is rewritten.
        if (keys != sent) {
            Clash.setAgeSecretKey(keys)
            sent = keys
        }
    }

    /**
     * One key per line, as age parses an identity file. A key age rejects is left out: nothing checks it at
     * entry, and one bad line would fail every decryption. A subscription with such a key fails as one without.
     */
    fun joined(keys: List<String?>, valid: (String) -> Boolean): String? =
        keys.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().filter(valid).joinToString("\n").ifEmpty { null }
}
