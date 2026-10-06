package com.github.kr328.clash.log

import android.content.Context
import com.github.kr328.clash.service.util.SubscriptionHeaders
import com.github.kr328.clash.service.util.importedDir
import java.io.IOException

/** The same known secrets and node addresses for all exported diagnostics. */
class ReportScrubber private constructor(private val secrets: List<String>, private val servers: Regex?) {
    fun scrub(text: String): String = LogScrubber.scrub(text, secrets, servers)

    companion object {
        private const val PROVIDER_LIMIT = 1024 * 1024L

        fun from(context: Context): ReportScrubber {
            // A failure collecting private values must not fall back to raw text.
            val secrets = listOf(SubscriptionHeaders.hwidOf(context)).filter { it.isNotBlank() }
            val profiles = context.importedDir.let { dir ->
                dir.listFiles()?.toList() ?: if (dir.exists()) throw IOException("Profile metadata unavailable") else emptyList()
            }
            val servers = profiles.flatMap { profile ->
                (listOf(profile.resolve("config.yaml")) + profile.resolve("providers").walk().onFail { _, error -> throw error })
                    .filter { it.isFile && it.length() <= PROVIDER_LIMIT }
                    .flatMap { LogScrubber.serversIn(it.readText()) }
            }.toSet()
            return ReportScrubber(secrets, LogScrubber.serversRegex(servers))
        }
    }
}
