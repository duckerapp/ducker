package com.github.kr328.clash.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.github.kr328.clash.common.util.mainActivityAlias

/**
 * Маскировка (D-147): на рабочем столе вместо ducker — «Калькулятор» со своим значком.
 *
 * Своего хранилища у настройки нет: это состояние двух алиасов из манифеста, включён ровно
 * один. Имя в «Настройках» Android и в уведомлении VPN остаётся ducker — его задаёт
 * установленный файл, на ходу его не сменить.
 */
private val Context.disguiseAlias: ComponentName
    get() = ComponentName(this, "com.github.kr328.clash.DisguiseAlias")

val Context.disguised: Boolean
    get() = packageManager.getComponentEnabledSetting(disguiseAlias) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED

fun Context.setDisguised(on: Boolean) {
    // Сначала включаем новый значок, потом выключаем старый: без единого включённого
    // значка система подставила бы ярлык «О приложении» (F-159). «По умолчанию» — это
    // состояние из манифеста: так выключение маскировки возвращает всё как было.
    val (show, hide) = if (on) disguiseAlias to mainActivityAlias else mainActivityAlias to disguiseAlias

    packageManager.setComponentEnabledSetting(
        show,
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.DONT_KILL_APP,
    )
    packageManager.setComponentEnabledSetting(
        hide,
        if (on) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.DONT_KILL_APP,
    )

    // Ярлыки «Подключить» / «Отключить» на долгом нажатии выдали бы калькулятор.
    publishShortcuts()
}
