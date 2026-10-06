package com.github.kr328.clash.common.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.Global
import kotlin.reflect.KClass

val KClass<*>.componentName: ComponentName
    get() = ComponentName(Global.application.packageName, this.java.name)

val KClass<*>.intent: Intent
    get() = Intent(Global.application, this.java)
/**
 * Алиас, который стоит в списке приложений. Выключенный компонент убирает значок —
 * на этом и держится настройка «скрыть иконку».
 */
val Context.mainActivityAlias: ComponentName
    get() = ComponentName(this, "com.github.kr328.clash.MainActivityAlias")
