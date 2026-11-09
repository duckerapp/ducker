package com.github.kr328.clash.service.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.github.kr328.clash.service.store.ServiceStore
import java.io.File
import java.util.UUID

/**
 * Заголовки, которые уходят с запросом подписки.
 *
 * Собираются здесь, а не в Go: идентификатор устройства и версии знает только Kotlin.
 * Дальше передаются вниз одним вызовом (D-30).
 */
object SubscriptionHeaders {
    /**
     * Панель принимает идентификатор длиной 10–64 символа из латиницы, цифр, `=` и `-`
     * (`^[a-zA-Z0-9=-]{10,64}$`, см. FACTS.md F-7). ANDROID_ID — 16 шестнадцатеричных
     * символов, подходит.
     */
    private val HWID_FORMAT = Regex("^[a-zA-Z0-9=-]{10,64}$")

    /**
     * Известное «сломанное» значение ANDROID_ID: на части старых прошивок оно одинаково
     * у всех устройств, поэтому как идентификатор не годится.
     */
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

    private const val FALLBACK_FILE = "hwid"

    /**
     * Выпуск mihomo, который входит в нашу ветку ядра (D-141). В User-Agent — он, а не строка
     * сборки `ducker-<коммит>-<дата>`: та повторяла «ducker» и ничего не говорила провайдеру.
     * Точная сборка ядра видна в «Настройках». Меняется вместе с ядром (`docs/CORE-UPDATE.md`).
     */
    const val MIHOMO_RELEASE = "1.19.33"

    /**
     * Запомненный запасной идентификатор. Без него неудачная запись файла означала бы
     * новый случайный идентификатор на каждый запрос подписки — а панель считает по нему
     * устройства и выдала бы отказ по лимиту.
     */
    @Volatile
    private var cachedFallback: String? = null

    fun of(context: Context, address: String): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to userAgent(context))

        if (deviceHeadersAllowed(address) && ServiceStore(context).sendHwid) {
            headers["x-hwid"] = hwid(context)
            headers["x-device-os"] = "Android"
            headers["x-ver-os"] = Build.VERSION.RELEASE ?: "unknown"
            headers["x-device-model"] = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        }

        return headers
    }

    private fun userAgent(context: Context): String = userAgent(
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    )

    /**
     * Начинается с `mihomo/` не для красоты: панель выбирает формат ответа по User-Agent,
     * и незнакомому клиенту отдаёт base64-список ссылок вместо конфига (F-42, D-38). The platform closes it
     * (D-439): a desktop client says `(windows)`, `(linux)` or `(macos)` in the same place.
     */
    internal fun userAgent(appVersion: String): String = "mihomo/$MIHOMO_RELEASE ducker/$appVersion (android)"

    /**
     * Показывается на экране подписок (D-25): человек должен видеть, что именно уходит
     * провайдеру, и уметь назвать это в поддержке.
     */
    fun hwidOf(context: Context): String = hwid(context)

    /**
     * Идентификатор устройства — ANDROID_ID как есть, без хеширования (D-31).
     *
     * Важно: с Android 8 это значение своё у каждого приложения (точнее, у ключа подписи),
     * поэтому переход человека с другого клиента занимает у панели НОВЫЙ слот устройства,
     * а не подхватывает прежний — проверено, FACTS.md F-56. Значение всё равно годится:
     * оно стабильно для установленного приложения, переживает обновления и не требует
     * разрешений.
     *
     * Запасной вариант, если система вернула пусто, «сломанное» значение или что-то
     * не подходящее под формат панели: случайный UUID, записанный один раз в приватный
     * файл приложения.
     */
    private fun hwid(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        if (androidId != null && androidId != BROKEN_ANDROID_ID && HWID_FORMAT.matches(androidId)) {
            return androidId
        }

        return fallbackHwid(context)
    }

    private fun fallbackHwid(context: Context): String {
        cachedFallback?.let { return it }

        val file = File(context.filesDir, FALLBACK_FILE)

        val stored = runCatching { file.readText().trim() }.getOrNull()
        if (stored != null && HWID_FORMAT.matches(stored)) {
            cachedFallback = stored

            return stored
        }

        val generated = UUID.randomUUID().toString().replace("-", "")

        runCatching { file.writeText(generated) }

        cachedFallback = generated

        return generated
    }
}
