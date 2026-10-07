package com.github.kr328.clash.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Два события обновления: приложение заменено новой версией и ответ установщика на нашу
 * установку. Снаружи недоступен: в ответе установщика лежит окно, которое приложение потом
 * открывает, — чужому приложению подсунуть своё нельзя.
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updater.replaced() else Updater.status(intent)
    }
}
