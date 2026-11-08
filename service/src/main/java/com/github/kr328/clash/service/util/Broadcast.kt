package com.github.kr328.clash.service.util

import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import java.util.*

/**
 * Foreground queue: these also reach the widgets' manifest receiver (`WidgetEvents`), and on a slow phone the
 * background queue holds a manifest receiver's broadcast for over an hour (F-421, D-435).
 */
fun Context.sendBroadcastSelf(intent: Intent) {
    sendBroadcast(
        intent.setPackage(this.packageName).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        Permissions.RECEIVE_SELF_BROADCASTS
    )
}

/** Человек выбрал другой узел — уведомление в шторке должно назвать новый. */
fun Context.sendSelectorChanged() {
    sendBroadcastSelf(Intent(Intents.ACTION_SELECTOR_CHANGED))
}

fun Context.sendProfileChanged(uuid: UUID) {
    val intent = Intent(Intents.ACTION_PROFILE_CHANGED)
        .putExtra(Intents.EXTRA_UUID, uuid.toString())

    sendBroadcastSelf(intent)
}

fun Context.sendProfileLoaded(uuid: UUID) {
    val intent = Intent(Intents.ACTION_PROFILE_LOADED)
        .putExtra(Intents.EXTRA_UUID, uuid.toString())

    sendBroadcastSelf(intent)
}

fun Context.sendProfileUpdateCompleted(uuid: UUID) {
    val intent = Intent(Intents.ACTION_PROFILE_UPDATE_COMPLETED)
        .putExtra(Intents.EXTRA_UUID, uuid.toString())

    sendBroadcastSelf(intent)
}

fun Context.sendProfileUpdateFailed(uuid: UUID, reason: String) {
    val intent = Intent(Intents.ACTION_PROFILE_UPDATE_FAILED)
        .putExtra(Intents.EXTRA_UUID, uuid.toString())
        .putExtra(Intents.EXTRA_FAIL_REASON, reason)

    sendBroadcastSelf(intent)
}

fun Context.sendOverrideChanged() {
    val intent = Intent(Intents.ACTION_OVERRIDE_CHANGED)

    sendBroadcastSelf(intent)
}

fun Context.sendServiceRecreated() {
    sendBroadcastSelf(Intent(Intents.ACTION_SERVICE_RECREATED))
}

fun Context.sendClashStarted() {
    sendBroadcastSelf(Intent(Intents.ACTION_CLASH_STARTED))
}

fun Context.sendClashStopped(reason: String?) {
    sendBroadcastSelf(
        Intent(Intents.ACTION_CLASH_STOPPED).putExtra(
            Intents.EXTRA_STOP_REASON,
            reason
        )
    )
}
