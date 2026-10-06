package com.github.kr328.clash.service

import android.content.Context
import android.os.Process
import java.util.Date
import kotlin.system.exitProcess

object CrashRecorder {
    const val DIR = "crashes"
    val PROCESSES = listOf("main", "background", "dashboard")
    private const val LIMIT = 64 * 1024L

    fun install(context: Context, processName: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
            ?: Thread.UncaughtExceptionHandler { _, _ ->
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        if (previous is CrashHandler) return
        val process = processName.substringAfter(':', "main").takeIf { it in PROCESSES } ?: "main"
        val dir = context.filesDir.resolve(DIR).resolve(process)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(previous) { thread, failure ->
            // Each process owns its pair of files. No competing flight-log writers.
            dir.mkdirs()
            FlightLogWriter(dir, LIMIT).use { writer ->
                writer.append("${Date()} FATAL $process thread ${thread.name}\n${failure.stackTraceToString()}\n")
            }
        })
    }
}
