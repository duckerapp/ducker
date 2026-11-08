package com.github.kr328.clash.util

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import com.github.kr328.clash.R
import java.util.Locale

/**
 * Оболочки, которые сами закрывают или замораживают приложения в фоне поверх Android,
 * вместе со службой VPN (F-116, F-121). У каждой свои пункты настроек, и об их существовании
 * знают только они: исключения из экономии батареи Android им мало. Обхода со стороны
 * приложения ни у одной нет — только шаги для человека (D-107).
 *
 * Пункты переезжали от версии к версии (F-122), поэтому шаги выбираются по версии Android:
 * её мы знаем точно, а название оболочки и её номер у каждого производителя свой.
 */
enum class Vendor(@StringRes val names: Int, private vararg val keys: String) {
    Xiaomi(R.string.vendor_xiaomi, "xiaomi", "redmi", "poco"),
    Huawei(R.string.vendor_huawei, "huawei", "honor"),
    Vivo(R.string.vendor_vivo, "vivo", "iqoo"),
    ColorOs(R.string.vendor_coloros, "oppo", "realme", "oneplus"),
    Samsung(R.string.vendor_samsung, "samsung"),
    Transsion(R.string.vendor_transsion, "tecno", "infinix", "itel", "transsion");

    /** Что сделать на этом телефоне. */
    @get:StringRes
    val steps: Int
        get() = stepsFor(Build.VERSION.SDK_INT, oneplus)

    /**
     * Шаги под версию Android (F-122). Границы: Android 12 (API 31) — ColorOS 12, OxygenOS 12
     * на базе ColorOS, One UI 4 с «Без ограничений», XOS/HiOS с автозапуском в настройках;
     * Android 13 (API 33) — у vivo «Батарея» в настройках приложения.
     */
    @StringRes
    fun stepsFor(sdk: Int, oneplus: Boolean): Int = when (this) {
        Xiaomi -> R.string.background_steps_xiaomi
        Huawei -> R.string.background_steps_huawei
        Vivo -> if (sdk >= 33) R.string.background_steps_vivo else R.string.background_steps_vivo_old
        ColorOs -> when {
            sdk >= 31 -> R.string.background_steps_coloros
            oneplus -> R.string.background_steps_oxygenos_old
            else -> R.string.background_steps_coloros_old
        }
        Samsung -> if (sdk >= 31) R.string.background_steps_samsung else R.string.background_steps_samsung_old
        Transsion -> if (sdk >= 31) R.string.background_steps_transsion else R.string.background_steps_transsion_old
    }

    companion object {
        private val brands = listOf(Build.MANUFACTURER, Build.BRAND).map { it.orEmpty().lowercase(Locale.ROOT) }

        /** OnePlus до Android 12 — своя OxygenOS, не ColorOS. */
        private val oneplus = "oneplus" in brands

        /** Семейство этого телефона; `null` — оболочка без своих убийц фона. */
        val current: Vendor? = entries.firstOrNull { vendor -> vendor.keys.any { it in brands } }
    }
}

/** Android не усыпляет приложение: оно в исключениях экономии батареи. */
fun Context.backgroundAllowed(): Boolean =
    getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true

/**
 * MIUI's own «Автозапуск» (F-418): without it MIUI closes ducker in the background and Android does not restart
 * it. MIUI keeps the switch as its app-op 10008, read through `AppOpsManager.checkOpNoThrow(int, int, String)`,
 * hidden from the SDK. `null` — not a Xiaomi, or MIUI did not answer.
 */
fun Context.miuiAutostart(): Boolean? {
    if (Vendor.current != Vendor.Xiaomi) return null

    val mode = runCatching {
        AppOpsManager::class.java
            .getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
            .invoke(getSystemService<AppOpsManager>(), MIUI_AUTOSTART, Process.myUid(), packageName)
    }.getOrNull()

    return when (mode) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_IGNORED -> false
        else -> null
    }
}

/** The shortcut's label with what MIUI says of its autostart; the other screens cannot be read. */
@StringRes
fun Shortcut?.labelWith(autostart: Boolean?): Int = when {
    this == null -> R.string.background_app_settings
    label != R.string.background_autostart || autostart == null -> label
    autostart -> R.string.background_autostart_on
    else -> R.string.background_autostart_off
}

/**
 * Системное окно «Разрешить работу в фоне?». VPN — ровно тот случай, ради которого оно
 * есть: без исключения служба в фоне засыпает и туннель отваливается. Некоторые прошивки
 * окна не показывают — тогда открываем общий список исключений.
 *
 * Окно — именно из системных «Настроек» (D-128): на MIUI то же действие ловят ещё два
 * экрана оболочки, и вместо вопроса открывался выбор «Battery details / Battery &
 * performance», в котором человек терялся. Свои пункты оболочки — следующим шагом.
 */
@SuppressLint("BatteryLife")
fun Context.requestBackgroundWork() {
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))

    runCatching {
        startActivity(Intent(ask).setPackage(SYSTEM_SETTINGS))
    }.recoverCatching {
        startActivity(ask)
    }.recoverCatching {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/**
 * Страница приложения в настройках телефона. У большинства оболочек там же их собственные
 * пункты — «Батарея», «Автозапуск»; прямых ссылок на них нет или они меняются от версии
 * к версии.
 */
fun Context.openAppDetails() {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}

/**
 * Экран оболочки прямой ссылкой (D-128): подпись кнопки и как его открыть; `false` — не
 * открылся, тогда показываем страницу приложения.
 */
class Shortcut(@StringRes val label: Int, val open: Context.() -> Boolean)

/**
 * Свои экраны оболочки. У MIUI 14 «Автозапуск» и экономия заряда — два разных экрана, и оба
 * не на странице приложения: шаг «в настройках ducker включите Автозапуск» там было не
 * выполнить. Пусто — прямых ссылок нет, остаётся страница приложения.
 */
val Vendor.shortcuts: List<Shortcut>
    get() = when (this) {
        Vendor.Xiaomi -> listOf(
            Shortcut(R.string.background_autostart) {
                start(Intent().setClassName(MIUI_SECURITY, "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            },
            Shortcut(R.string.background_battery) {
                start(
                    Intent().setClassName(MIUI_POWER, "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .setData(Uri.parse("package:$packageName"))
                        .putExtra("package_name", packageName)
                        .putExtra("package_label", getString(R.string.launch_name)),
                )
            },
        )
        else -> emptyList()
    }

/** Экран оболочки, а не открылся — страница приложения. */
fun Context.openVendor(shortcut: Shortcut?) {
    if (shortcut == null || !shortcut.open(this)) openAppDetails()
}

private fun Context.start(intent: Intent) = runCatching { startActivity(intent) }.isSuccess

private const val SYSTEM_SETTINGS = "com.android.settings"
private const val MIUI_SECURITY = "com.miui.securitycenter"
private const val MIUI_POWER = "com.miui.powerkeeper"
private const val MIUI_AUTOSTART = 10008
