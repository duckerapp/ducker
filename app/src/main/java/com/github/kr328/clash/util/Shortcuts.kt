package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.InternalControlActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.common.constants.Intents

/**
 * Ярлыки на значке приложения: долгое нажатие → «Подключить» / «Отключить» (D-87).
 *
 * Динамические, а не из XML: в статическом описании нельзя подставить имя пакета,
 * а сборка с собственным идентификатором (`custom.application.id`) получила бы ярлыки,
 * ведущие в чужое приложение. Лаунчер запускает опубликованные ярлыки от имени
 * ducker, поэтому они ведут в закрытую `InternalControlActivity`.
 *
 * Вызывать можно сколько угодно раз: список заменяется целиком. При маскировке (D-147)
 * ярлыков нет: «Подключить» на значке калькулятора выдало бы его.
 */
fun Context.publishShortcuts() {
    if (disguised) return ShortcutManagerCompat.removeAllDynamicShortcuts(this)

    fun shortcut(id: String, label: Int, icon: Int, action: String) =
        ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(getString(label))
            .setIcon(IconCompat.createWithResource(this, icon))
            .setIntent(
                Intent(action)
                    .setClass(this, InternalControlActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            .build()

    ShortcutManagerCompat.setDynamicShortcuts(
        this,
        listOf(
            shortcut("connect", R.string.shortcut_connect, R.drawable.ic_shortcut_connect, Intents.ACTION_START_CLASH),
            shortcut("disconnect", R.string.shortcut_disconnect, R.drawable.ic_shortcut_disconnect, Intents.ACTION_STOP_CLASH),
        ),
    )
}
