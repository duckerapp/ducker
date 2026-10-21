package com.github.kr328.clash.common.constants

import android.content.ComponentName
import com.github.kr328.clash.common.util.packageName

object Components {
    private const val componentsPackageName = "com.github.kr328.clash"

    /**
     * Куда ведут уведомление и кнопка «Настроить» в системных настройках VPN.
     *
     * Именно `HomeActivity`, а не `MainActivity`: вторая — экран CMFA, который мы заменяем.
     * Пока здесь стояла она, человек из уведомления попадал в старый интерфейс.
     */
    val MAIN_ACTIVITY = ComponentName(packageName, "$componentsPackageName.HomeActivity")

    /**
     * Куда ведёт уведомление о неудачном обновлении подписки. Тоже главный экран:
     * ошибку обновления показывает карточка подписки, отдельного экрана свойств у нас нет.
     */
    val PROPERTIES_ACTIVITY = MAIN_ACTIVITY

    /** The closed command target of widgets and shortcuts; the service's notices use it too. */
    val INTERNAL_CONTROL_ACTIVITY = ComponentName(packageName, "$componentsPackageName.InternalControlActivity")
}