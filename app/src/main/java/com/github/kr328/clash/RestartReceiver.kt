package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.util.publishShortcuts
import com.github.kr328.clash.util.startClashService

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Refresh existing dynamic/pinned shortcuts before their first click after an update.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { context.publishShortcuts() }
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (StatusProvider.shouldStartClashOnBoot)
                    context.startClashService()
            }
        }
    }
}
