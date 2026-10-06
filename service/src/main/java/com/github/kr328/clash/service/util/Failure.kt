package com.github.kr328.clash.service.util

import android.content.Context
import androidx.annotation.StringRes
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.clash.module.TunModule

/**
 * Неудача словами для человека (D-227): сырой текст ядра или службы — в вид неудачи, а вид — в
 * объяснение и в то, что с этим делать. Таблица одна на все места, где неудачу видно: окна
 * «Добавить» и «Изменить», карточка обновления, главная, уведомления.
 *
 * Между процессами ходит сырой текст, а словами он становится там, где его показывают: по готовой
 * строке экран не узнал бы, какую кнопку предложить.
 *
 * @param code код ответа сервера подписки — подставляется в объяснение.
 * @param detail текст ядра для незнакомой ошибки: показывать мелко и без адресов (`LogScrubber`).
 */
data class Failure(val kind: Kind, val code: Int = 0, val detail: String = "") {
    /** Что поможет — по этому экран выбирает кнопку. */
    enum class Fix { None, Retry, EditLink, Support, VpnSettings }

    /**
     * @param download подписку не удалось скачать, до разбора конфига дело не дошло: встроенному
     * шаблону разбирать нечего (`ProfileProcessor.fetchProfile`).
     */
    enum class Kind(@StringRes val text: Int, val fix: Fix = Fix.None, val download: Boolean = false) {
        Network(R.string.subscription_download_failed, Fix.Retry, download = true),
        Denied(R.string.subscription_http_denied, Fix.Support, download = true),
        NotFound(R.string.subscription_http_not_found, Fix.EditLink, download = true),
        Busy(R.string.subscription_http_busy, Fix.Retry, download = true),
        Server(R.string.subscription_http_server, Fix.Retry, download = true),
        Http(R.string.subscription_http_status, Fix.Retry, download = true),
        ProfileTooLarge(R.string.subscription_profile_too_large, Fix.Support, download = true),
        ProviderTooLarge(R.string.subscription_provider_too_large, Fix.Support, download = true),
        Redirects(R.string.subscription_redirects, Fix.Support, download = true),
        BadLink(R.string.subscription_bad_link, Fix.EditLink, download = true),

        NotLinks(R.string.subscription_not_links),
        NoServers(R.string.subscription_no_servers, Fix.Support),
        BadConfig(R.string.subscription_bad_config, Fix.Support),

        LimitDevices(R.string.subscription_limit_devices, Fix.Support),
        LimitHwid(R.string.subscription_limit_hwid, Fix.Support),
        LimitOther(R.string.subscription_limit_other, Fix.Support),

        /** Туннель не поднялся: конфиг активной подписки ядро не приняло. */
        LoadFailed(R.string.vpn_config_failed, Fix.Retry),

        /** Новый конфиг ядро не приняло, туннель работает на прежнем (D-207). */
        Reload(R.string.config_reload_failed),

        NoProfile(R.string.vpn_no_profile),
        VpnRejected(R.string.vpn_rejected, Fix.VpnSettings),
        TunnelFailed(R.string.failure_try_again),
        Unknown(R.string.failure_try_again),
    }

    fun text(context: Context): String = context.getString(kind.text, code)

    companion object {
        const val NO_PROFILE = "No profile selected"
        const val VPN_REJECTED = "Establish VPN rejected by system"

        /** Метки службы перед текстом ядра — по образцу `DOWNLOAD_FAILED:` из `config/download.go`. */
        const val LOAD = "LOAD_FAILED: "
        const val RELOAD = "RELOAD_FAILED: "
        const val HWID = "HWID_BLOCKED: "

        private const val DOWNLOAD = "DOWNLOAD_FAILED: "
        private const val TOO_LARGE = "DOWNLOAD_TOO_LARGE: "

        /** «HTTP 503 Service Unavailable» — так ядро сообщает ответ сервера (`fetch.go`, F-155). */
        private val HTTP = Regex("""^HTTP (\d{3})\b""")

        /**
         * @param unknown чем считать незнакомый текст. При загрузке подписки это ошибка разбора
         * конфига: всё, что случилось до него, ядро и служба помечают сами.
         */
        fun of(raw: String?, unknown: Kind = Kind.Unknown): Failure {
            val text = raw.orEmpty()

            HTTP.find(text.removePrefix("${DOWNLOAD}profile: "))?.let {
                val code = it.groupValues[1].toInt()

                return Failure(
                    when (code) {
                        401, 403 -> Kind.Denied
                        404, 410 -> Kind.NotFound
                        429 -> Kind.Busy
                        in 500..599 -> Kind.Server
                        else -> Kind.Http
                    },
                    code,
                )
            }

            return when {
                text == "${TOO_LARGE}profile: 16777216" -> Failure(Kind.ProfileTooLarge)
                text == "${TOO_LARGE}provider: 67108864" -> Failure(Kind.ProviderTooLarge)
                text.startsWith(TOO_LARGE) -> Failure(Kind.Network)
                text.startsWith(DOWNLOAD) -> Failure(
                    when {
                        "unsupported scheme" in text -> Kind.BadLink
                        "after 10 redirects" in text -> Kind.Redirects
                        else -> Kind.Network
                    }
                )
                text.startsWith(HWID) -> Failure(
                    when (text.removePrefix(HWID)) {
                        "max_devices" -> Kind.LimitDevices
                        "not_supported" -> Kind.LimitHwid
                        else -> Kind.LimitOther
                    }
                )
                text.startsWith(LOAD) -> Failure(Kind.LoadFailed, detail = text.removePrefix(LOAD))
                text.startsWith(RELOAD) -> Failure(Kind.Reload, detail = text.removePrefix(RELOAD))
                text == NO_PROFILE -> Failure(Kind.NoProfile)
                text == VPN_REJECTED -> Failure(Kind.VpnRejected)
                text == TunModule.START_FAILED -> Failure(Kind.TunnelFailed)
                text == "Invalid url" || text.startsWith("Unsupported url") -> Failure(Kind.BadLink)
                // Вставили текст, а не адрес: ядро не разобрало его как ссылки протоколов или не
                // нашло ни одной (`convert/convert.go`).
                "convert v2ray subscribe error" in text || "не похоже на список ссылок" in text ->
                    Failure(Kind.NotLinks)
                "does not contain `proxies`" in text -> Failure(Kind.NoServers)
                else -> Failure(unknown, detail = text)
            }
        }
    }
}
