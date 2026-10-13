package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendOverrideChanged
import java.util.concurrent.TimeUnit

/**
 * «Подробный журнал» выключается сам через сутки (D-224): забытый тумблер иначе навсегда
 * оставляет ядро писать строку о каждом соединении и DNS-запросе.
 *
 * Сам уровень живёт в постоянном оверрайде ядра, здесь — только время включения. Сверяем их
 * при каждой записи и чтении оверрайда из настроек, при подъёме туннеля и по будильнику,
 * который телефон не будит (D-85).
 */
object VerboseLog {
    const val ACTION = "app.ducker.android.service.VERBOSE_LOG_CHECK"

    @Synchronized
    fun check(context: Context) {
        val store = ServiceStore(context)
        val override = Clash.queryOverride(Clash.OverrideSlot.Persist)

        if (override.logLevel != LogMessage.Level.Debug) {
            if (store.verboseLogSince != 0L) store.verboseLogSince = 0

            LogTail.keep(false)

            return disarm(context)
        }

        val now = System.currentTimeMillis()
        val until = verboseLogUntil(store.verboseLogSince, now)

        if (until > now) {
            if (store.verboseLogSince != until - LIMIT_MS) store.verboseLogSince = until - LIMIT_MS

            // While the verbose log is on, its lines are collected with no screen open (D-290).
            LogTail.keep(true)

            return arm(context, until)
        }

        // То же, что выключение рукой (`SettingsViewModel.setDebugLog`): уровень снова задаёт
        // профиль — ядро подставит его при перезагрузке конфига, а до неё стоит спокойный info.
        override.logLevel = null

        Clash.patchOverride(Clash.OverrideSlot.Persist, override)
        Clash.setLogLevel(LogMessage.Level.Info)

        store.verboseLogSince = 0

        LogTail.keep(false)

        disarm(context)

        context.sendOverrideChanged()

        Log.i("Verbose log turned itself off after a day")
    }

    /**
     * По будильнику, после перезагрузки и перевода часов. Журнал не включали — ядро ради
     * проверки в процесс не грузим.
     */
    fun checkIfOn(context: Context) {
        if (ServiceStore(context).verboseLogSince != 0L) check(context)
    }

    /** Окно в час, как у срока подписки: неточный `set` уплывает на ¾ срока (F-180). */
    private fun arm(context: Context, at: Long) {
        context.getSystemService<AlarmManager>()
            ?.setWindow(AlarmManager.RTC, at, TimeUnit.HOURS.toMillis(1), alarm(context))
    }

    private fun disarm(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(alarm(context))
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(ACTION).setComponent(ProfileReceiver::class.componentName),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
    )
}

private val LIMIT_MS = TimeUnit.DAYS.toMillis(1)

/**
 * Когда выключать журнал, включённый в [since]. Отметки нет (журнал включили прежней сборкой)
 * или она в будущем (часы перевели назад) — сутки идут с [now].
 */
internal fun verboseLogUntil(since: Long, now: Long): Long =
    (if (since in 1..now) since else now) + LIMIT_MS
