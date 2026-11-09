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
 * @param backups how many of the provider's backup addresses failed too (D-345).
 */
data class Failure(val kind: Kind, val code: Int = 0, val detail: String = "", val backups: Int = 0) {
    /** Что поможет — по этому экран выбирает кнопку. */
    enum class Fix { None, Retry, EditLink, Support, VpnSettings, Background }

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
        /** A web page came instead: a block page, a captive portal, or a link to a site page (D-345). */
        WebPage(R.string.subscription_web_page, Fix.Retry, download = true),
        /**
         * The provider encrypted the body to its user's age key, and the subscription has none or another one. Not the
         * built-in template's case: there is nothing to read nodes from. Its key goes in «Изменить».
         */
        Encrypted(R.string.subscription_encrypted, Fix.EditLink, download = true),

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
        Crashed(R.string.vpn_crashed),

        /** The tunnel's process died with no crash record of ours: the system closed it (F-418). */
        Killed(R.string.vpn_killed, Fix.Background),

        /** Nothing else explains the failure, and the phone is out of space (Q-104). */
        NoSpace(R.string.no_space),
        Unknown(R.string.failure_try_again),
    }

    fun text(context: Context): String = context.getString(kind.text, code).let {
        if (backups > 0) context.getString(R.string.subscription_backups_failed, it, backups) else it
    }

    /** The heading of a stop, shared by the notification and the home card. */
    @get:StringRes
    val stopTitle: Int
        get() = if (kind == Kind.Crashed || kind == Kind.Killed) R.string.vpn_crashed_title else R.string.vpn_failure_title

    companion object {
        const val NO_PROFILE = "No profile selected"
        const val VPN_REJECTED = "Establish VPN rejected by system"

        /** The tunnel's process crashed while running; its own crash handler reports it (D-343). */
        const val CRASHED = "Tunnel process crashed"

        /** The tunnel's process died with no crash record since the tunnel came up (`TunGuard.leftDown`). */
        const val KILLED = "Tunnel process killed by the system"

        /** Метки службы перед текстом ядра — по образцу `DOWNLOAD_FAILED:` из `config/download.go`. */
        const val LOAD = "LOAD_FAILED: "
        const val RELOAD = "RELOAD_FAILED: "
        const val HWID = "HWID_BLOCKED: "

        private const val DOWNLOAD = "DOWNLOAD_FAILED: "
        private const val TOO_LARGE = "DOWNLOAD_TOO_LARGE: "

        /** «HTTP 503 Service Unavailable» — так ядро сообщает ответ сервера (`fetch.go`, F-155). */
        private val HTTP = Regex("""^HTTP (\d{3})\b""")

        /** The core's mark of the mirrors tried besides the failure shown (`subscription_mirrors.go`). */
        private val BACKUPS = Regex(""" \(\+(\d+) backups\)$""")

        /**
         * @param unknown чем считать незнакомый текст. При загрузке подписки это ошибка разбора
         * конфига: всё, что случилось до него, ядро и служба помечают сами.
         * @param outOfSpace the phone is out of space (`Context.outOfSpace`): a text no other kind explains is
         * put down to it rather than to [unknown], which would send the person to their provider for nothing.
         */
        fun of(raw: String?, unknown: Kind = Kind.Unknown, outOfSpace: Boolean = false): Failure {
            val text = raw.orEmpty()
            val backups = BACKUPS.find(text)?.takeIf { text.startsWith(DOWNLOAD) }
            val failure = parse(if (backups != null) text.removeRange(backups.range) else text, unknown, outOfSpace)

            return if (backups != null) failure.copy(backups = backups.groupValues[1].toInt()) else failure
        }

        private fun parse(text: String, unknown: Kind, outOfSpace: Boolean): Failure {
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
                        "a web page instead of a subscription" in text -> Kind.WebPage
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
                // The core's text for an age-armored config it could not decrypt (`config/config.go`, D-442).
                text.startsWith("decrypt config error") -> Failure(Kind.Encrypted)
                text == NO_PROFILE -> Failure(Kind.NoProfile)
                text == VPN_REJECTED -> Failure(Kind.VpnRejected)
                text == CRASHED -> Failure(Kind.Crashed)
                text == KILLED -> Failure(Kind.Killed)
                text == TunModule.START_FAILED -> Failure(Kind.TunnelFailed)
                text == "Invalid url" || text.startsWith("Unsupported url") -> Failure(Kind.BadLink)
                // Вставили текст, а не адрес: ядро не разобрало его как ссылки протоколов или не
                // нашло ни одной (`convert/convert.go`).
                "convert v2ray subscribe error" in text || "не похоже на список ссылок" in text ->
                    Failure(Kind.NotLinks)
                "does not contain `proxies`" in text -> Failure(Kind.NoServers)
                outOfSpace -> Failure(Kind.NoSpace)
                else -> Failure(unknown, detail = text)
            }
        }
    }
}
