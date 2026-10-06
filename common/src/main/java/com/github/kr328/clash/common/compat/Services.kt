package com.github.kr328.clash.common.compat

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.github.kr328.clash.common.log.Log

fun Context.startForegroundServiceCompat(intent: Intent): Boolean = tryStartForegroundService(
    start = { startForegroundService(intent) },
    rejected = { Log.e("Foreground service start rejected: ${intent.component?.className}", it) },
)

fun Service.startForegroundCompat(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(id, notification)
    }
}
