package com.github.kr328.clash.service.util

import android.util.Base64
import com.github.kr328.clash.core.model.FetchStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сведения о подписке, пришедшие заголовками ответа: как её назвал провайдер, что он хочет
 * сказать человеку, куда его отправить и не упёрся ли он в лимит устройств.
 *
 * Лежат файлом в каталоге профиля, а не в базе (D-32): каталог и так копируется целиком
 * `processing` → `imported`, поэтому при неудачном обновлении остаются прежние сведения
 * вместе с прежним профилем, и миграция базы при каждом новом заголовке не нужна.
 */
@Serializable
data class ProviderInfo(
    val profileTitle: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val profileWebPageUrl: String = "",
    val brandName: String = "",
    val brandLogo: String = "",
    val buyUrl: String = "",
    /** `ducker-override-user: on` — провайдер главнее пользователя в настройках (D-16). */
    val overrideUser: Boolean = false,
    /** `ducker-global: off` — спрятать переключатель Global/Rules на «Прокси» (D-143). */
    val globalHidden: Boolean = false,
    /** `ducker-local-proxy: off` — поднять туннель с закрытыми портами конфига (D-218). */
    val localProxyOff: Boolean = false,
    /** `ducker-share: off` — keep «Раздача по Wi-Fi» closed (D-471). */
    val shareOff: Boolean = false,
    val updateIntervalHours: Int = 0,
    /** `subscription-refill-date` — когда обнулится счётчик трафика, unix-секунды. */
    val refillAt: Long = 0,
    /**
     * Панель отказала из-за идентификатора устройства. Профиль в этом случае приходит
     * заглушкой, которая проходит проверку ядра (FACTS.md, F-51), поэтому отличить её
     * можно только по заголовкам.
     */
    val hwidBlocked: Boolean = false,
    /** `max_devices`, `not_supported` или пусто. */
    val hwidReason: String = "",
    /**
     * Конфиг провайдера не прошёл проверку, и включён встроенный шаблон (D-19).
     * Человеку это показывается плашкой вместе с [lastError], чтобы он мог отправить
     * текст провайдеру.
     */
    val usedTemplate: Boolean = false,
    val lastError: String = "",
    /**
     * Шаблон включён не потому, что пришёл список ссылок, а потому, что в конфиге провайдера
     * ошибка: узлы взяты из него (D-246). Тогда [lastError] — текст ошибки ядра, и человеку
     * его показывают, чтобы переслал провайдеру.
     */
    val templateFromConfig: Boolean = false,
    /** The link this response was fetched for: [mirrors] and [movedFrom] belong to it alone (D-345). */
    val source: String = "",
    /** The provider's other addresses of [source] (`ducker-mirrors`, `fallback-url`), resolved. */
    val mirrors: List<String> = emptyList(),
    /** The address that gave this response, and how: "", "direct" or "proxy" (Q-81). */
    val servedBy: String = "",
    val servedVia: String = "",
    /** Where the provider moves the subscription (`new-url`, `new-domain`; Q-80); empty — nowhere. */
    val moveTo: String = "",
    /** The link the provider moved the subscription from, and when: shown in its sheet for [MOVED_SHOWN]. */
    val movedFrom: String = "",
    val movedAt: Long = 0,
    /** The last download's tries (Q-82), kept when it failed too; [triedAt] — when. */
    val tries: List<FetchStatus.Try> = emptyList(),
    val triedAt: Long = 0,
    /**
     * When the last update of this subscription ended, and its raw failure; empty — it succeeded (D-436). The
     * screens read them back: they hear of an update's end only while they are open.
     */
    val updateEndedAt: Long = 0,
    val updateFailure: String = "",
) {
    fun mirrorsFor(link: String): List<String> = if (source == link) mirrors else emptyList()

    companion object {
        private const val FILE_NAME = "provider.json"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val MOVED_SHOWN = TimeUnit.DAYS.toMillis(7)

        /** @param source the link the response was fetched for: bare-host mirrors take its path. */
        fun of(headers: Map<String, String>, source: String = ""): ProviderInfo {
            val h = headers.mapKeys { it.key.lowercase() }

            val maxDevices = h["x-hwid-max-devices-reached"].isTrue()
            val notSupported = h["x-hwid-not-supported"].isTrue()

            return ProviderInfo(
                profileTitle = h["profile-title"].decode().readable(),
                announce = h["announce"].decode(),
                supportUrl = h["support-url"].decode(),
                profileWebPageUrl = h["profile-web-page-url"].decode(),
                brandName = h["ducker-brand-name"].decode().readable(),
                brandLogo = h["ducker-brand-logo"].decode(),
                buyUrl = h["ducker-buy-url"].decode(),
                overrideUser = h["ducker-override-user"]?.trim()?.equals("on", true) == true,
                globalHidden = h["ducker-global"]?.trim()?.equals("off", true) == true,
                localProxyOff = h["ducker-local-proxy"]?.trim()?.equals("off", true) == true,
                shareOff = h["ducker-share"]?.trim()?.equals("off", true) == true,
                updateIntervalHours = h["profile-update-interval"]?.trim()?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: 0,
                refillAt = h["subscription-refill-date"]?.trim()?.toLongOrNull() ?: 0,
                hwidBlocked = maxDevices || notSupported,
                hwidReason = when {
                    maxDevices -> "max_devices"
                    notSupported -> "not_supported"
                    else -> ""
                },
                source = source,
                mirrors = SubscriptionMirrors.resolve(source, h["ducker-mirrors"].decode(), h["fallback-url"].decode()),
                moveTo = SubscriptionMirrors.moveTarget(source, h["new-url"].decode(), h["new-domain"].decode()).orEmpty(),
            )
        }

        /**
         * [info] of a new response with what it keeps from [previous] of the same link (D-345). A
         * mirror's response without a mirror header keeps the list: Happ-style backups often sit on a
         * plain server that does not repeat headers (F-329). The own link's response is authoritative —
         * no header, no mirrors: that is how a provider removes them. The mark of a move stays a week.
         */
        fun continued(
            info: ProviderInfo,
            previous: ProviderInfo,
            headers: Map<String, String>,
            servedBy: String,
            servedVia: String,
            now: Long = System.currentTimeMillis(),
        ): ProviderInfo {
            val same = previous.source == info.source
            val sent = headers.keys.any { it.equals("ducker-mirrors", true) || it.equals("fallback-url", true) }
            val keep = same && !sent && servedBy != info.source

            return info.copy(
                servedBy = servedBy,
                servedVia = servedVia,
                mirrors = if (keep) previous.mirrors else info.mirrors,
                movedFrom = if (same && now - previous.movedAt < MOVED_SHOWN) previous.movedFrom else "",
                movedAt = if (same && now - previous.movedAt < MOVED_SHOWN) previous.movedAt else 0,
            )
        }

        fun write(profileDir: File, info: ProviderInfo) {
            runCatching {
                profileDir.mkdirs()
                File(profileDir, FILE_NAME).writeText(json.encodeToString(serializer(), info))
            }
        }

        fun read(profileDir: File): ProviderInfo {
            return runCatching {
                json.decodeFromString(serializer(), File(profileDir, FILE_NAME).readText())
            }.getOrDefault(ProviderInfo())
        }

        private fun String?.isTrue(): Boolean = this?.trim()?.equals("true", true) == true

        /**
         * Панель кодирует текст, который может содержать перевод строки или кириллицу,
         * как `base64:<…>` (FACTS.md, F-33). Если префикса нет — значение берётся как есть.
         *
         * Одинокий прочерк значит «не задано» (D-68). Пустое значение заголовка панель
         * сохранить не даёт, поэтому убрать свой заголовок — например, чтобы в шапке
         * стояла марка приложения, — провайдер иначе не может.
         */
        private fun String?.decode(): String {
            val value = this?.trim().orEmpty()

            // Broken base64 counts as not sent, not as the text «base64:…» on screen.
            val decoded = if (value.startsWith("base64:")) {
                runCatching {
                    String(Base64.decode(value.removePrefix("base64:"), Base64.DEFAULT))
                }.getOrDefault("")
            } else value

            return if (decoded.trim() == "-") "" else decoded
        }

        /**
         * A name in a broken encoding counts as not sent (Q-96): cp1251 bytes, raw or in `base64:`, came as «���». The
         * core replaces invalid UTF-8 with U+FFFD, and so does the decoding above.
         */
        private fun String.readable(): String = if ('\uFFFD' in this) "" else this
    }
}
