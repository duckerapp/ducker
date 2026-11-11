package com.github.kr328.clash.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.FlightRecorder
import com.github.kr328.clash.util.Vendor
import com.github.kr328.clash.util.backgroundAllowed
import com.github.kr328.clash.util.miuiAutostart
import java.io.BufferedWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.*

class LogcatFilter(output: Writer, private val context: Context) : BufferedWriter(output) {
    /**
     * Значения, которые мы знаем точно и вырезаем по совпадению, а не по догадке.
     * Остальное — на [LogScrubber] (инвариант 4).
     */
    private val scrubber = ReportScrubber.from(context)

    fun writeHeader(time: Date) {
        appendLine("# Capture on ${time.formatLog(context)}")
    }

    /**
     * Справка о телефоне и среде (D-111). Жалобу присылают издалека и один раз:
     * чем меньше переспрашивать, тем больше шансов разобраться с первой выгрузки.
     */
    fun writeDevice() {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

        appendLine("# App: ducker $version")
        appendLine("# Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("# Build: ${Build.DISPLAY}")
        appendLine("# Shell family: ${Vendor.current?.name ?: "stock"}")

        // Without it MIUI closes the VPN's process and blocks its restart: a death with no trace (F-418).
        if (Vendor.current == Vendor.Xiaomi) {
            appendLine("# MIUI autostart: ${context.miuiAutostart()?.let { if (it) "on" else "off" } ?: "unknown"}")
        }

        val power = context.getSystemService<PowerManager>()
        val activity = context.getSystemService<ActivityManager>()
        val usage = context.getSystemService<UsageStatsManager>()

        // Both are Android 9+.
        val p = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val bucket = if (p) usage?.appStandbyBucket?.let(::bucketOf) else null
        val restricted = if (p) activity?.isBackgroundRestricted else null

        appendLine(
            "# Background: battery exemption=${context.backgroundAllowed()}" +
                ", power saver=${power?.isPowerSaveMode}" +
                ", standby bucket=$bucket" +
                ", restricted by user=$restricted",
        )

        val connectivity = context.getSystemService<ConnectivityManager>()
        val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val transports = listOfNotNull(
            "vpn".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true },
            "wifi".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true },
            "cellular".takeIf { capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true },
        )

        appendLine(
            "# Network: ${transports.ifEmpty { listOf("none") }.joinToString("+")}" +
                ", data saver=${connectivity?.restrictBackgroundStatus?.let(::dataSaverOf)}",
        )
    }

    /**
     * Записи самописца службы (D-111): что было до того, как открыли этот экран. Сначала
     * сводка — сколько раз за последний час менялась сеть: частая смена сама по себе
     * причина обрывов (так ведут себя «ускорители сети» в оболочках).
     */
    fun writeFlight() {
        val dir = context.filesDir.resolve(FlightRecorder.DIR)
        val lines = listOf(FlightRecorder.PREVIOUS, FlightRecorder.CURRENT)
            .flatMap { name ->
                CrashReport.readTail(dir.resolve(name), limit = 256 * 1024, scrub = scrubber::scrub)
                    .lineSequence().filter { it.isNotBlank() }.toList()
            }
            .takeLast(FLIGHT_LINES)

        if (lines.isEmpty()) return

        val hourAgo = System.currentTimeMillis() - HOUR
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        val switches = lines.count { line ->
            "best network" in line &&
                (runCatching { stamp.parse(line.take(23))?.time }.getOrNull() ?: 0) >= hourAgo
        }

        appendLine("# Network switches in the last hour: $switches")
        appendLine("# Flight recorder, last ${lines.size} lines:")

        lines.forEach { appendLine(it) }
    }

    fun writeCrashes() {
        append(CrashReport.stored(context.filesDir, scrubber::scrub))
        appendLine("# Live log from this screen:")
    }

    /**
     * Почему процессы приложения завершались в последний раз (D-87, Android 11+).
     *
     * Самая частая жалоба на VPN — «сам выключается». Ответ на вопрос «кто его выключил»
     * у системы есть: нехватка памяти, ограничитель памяти Android 17
     * («MemoryLimiter:AnonSwap»), падение ядра, ANR, остановка из настроек. Раньше его
     * можно было достать только из системного журнала, которого у человека нет;
     * теперь он едет в отчёте рядом с журналом ядра.
     *
     * Описание причины проходит через тот же чистильщик, что и журнал: в тексте падения
     * может оказаться адрес узла.
     */
    fun writeExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val exits = runCatching {
            context.getSystemService<ActivityManager>()
                ?.getHistoricalProcessExitReasons(context.packageName, 0, EXIT_HISTORY)
        }.getOrNull().orEmpty()

        if (exits.isEmpty()) return

        appendLine("# Last process exits:")

        exits.forEach { exit ->
            val process = exit.processName.substringAfter(':', "main")
            val description = exit.description?.takeIf { it.isNotBlank() }
                ?.let { " — " + scrubber.scrub(it) }
                .orEmpty()

            appendLine("#   ${Date(exit.timestamp).formatLog(context)}  $process  ${reasonOf(exit.reason)}$description")
        }
    }

    fun writeMessage(message: LogMessage) {
        val time = message.time.formatLog(context, includeDate = false)
        val level = message.level.name

        appendLine(FORMAT.format(time, level, scrubber.scrub(message.message)))
    }

    companion object {
        private const val FORMAT = "%12s %7s: %s"

        /** Сколько последних завершений показывать: больше в отчёте только мешает. */
        private const val EXIT_HISTORY = 8

        /** Строк самописца в выгрузке: хватает на часы, и копирование не упрётся в предел. */
        private const val FLIGHT_LINES = 1500

        private const val HOUR = 60 * 60 * 1000L

        /** `UsageStatsManager.STANDBY_BUCKET_EXEMPTED`, скрытая в SDK. */
        private const val STANDBY_BUCKET_EXEMPTED = 5

        private fun bucketOf(bucket: Int): String = when (bucket) {
            // Публичной константы нет: так система помечает приложения, которые не
            // ограничивает вовсе, — например, исключённые из экономии батареи.
            STANDBY_BUCKET_EXEMPTED -> "exempted"
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            else -> "bucket $bucket"
        }

        private fun dataSaverOf(status: Int): String = when (status) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "off"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "on, ducker allowed"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
            else -> "status $status"
        }

        private fun reasonOf(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exited itself"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by signal"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by user"
            ApplicationExitInfo.REASON_USER_STOPPED -> "force-stopped by user"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
            // Три причины ниже появились в Android 14; числа встраиваются при сборке,
            // так что на старых системах ссылка на них ничего не ломает.
            ApplicationExitInfo.REASON_FREEZER -> "frozen by system"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state changed"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "app updated"
            ApplicationExitInfo.REASON_OTHER -> "other"
            else -> "reason $reason"
        }
    }
}
