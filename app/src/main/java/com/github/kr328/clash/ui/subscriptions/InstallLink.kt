package com.github.kr328.clash.ui.subscriptions

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A subscription link with the provider's backup addresses that came with it (stage 21, package 3):
 * `ducker://install-config?url=…&mirror=…` from the provider's page, «Поделиться» and the TV transfer,
 * so a new device does not start from a blocked link. A [mirrors] entry is written as in
 * `ducker-mirrors`, a bare host or a full link; the add resolves them against the link
 * (`SubscriptionMirrors.resolve`) and drops what does not belong.
 */
data class InstallLink(val link: String, val mirrors: List<String> = emptyList()) {
    /** Without mirrors it is the bare link, as before them: any scanner and an older TV take it. */
    override fun toString(): String = if (mirrors.isEmpty()) link else
        "ducker://install-config?url=${encode(link)}" + mirrors.joinToString("") { "&mirror=${encode(it)}" }

    companion object {
        /** Schemes of an `…://install-config` link: ours and those the panels give. */
        val SCHEMES = setOf("ducker", "clash", "clashmeta")

        /** A one-line http(s) link, or an `install-config` link carrying one; `null` — neither. */
        fun parse(text: String): InstallLink? {
            subscriptionLink(text)?.let { return InstallLink(it) }

            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES || uri.host != "install-config") return null

            val params = uri.rawQuery.orEmpty().split('&').map { it.substringBefore('=') to decode(it.substringAfter('=', "")) }
            val link = params.firstOrNull { it.first == "url" }?.second?.trim()?.let(::subscriptionLink) ?: return null

            return InstallLink(link, params.filter { it.first == "mirror" }.mapNotNull { it.second })
        }

        /** [mirrors] of [link], each as short as it goes: one on the link's own path as its bare host. */
        fun of(link: String, mirrors: List<String>): InstallLink {
            val own = runCatching { URI(link) }.getOrNull()

            return InstallLink(link, mirrors.map { mirror ->
                val uri = runCatching { URI(mirror) }.getOrNull()

                if (own != null && uri != null && uri.scheme == own.scheme?.lowercase() &&
                    uri.rawPath == own.rawPath && uri.rawQuery == own.rawQuery) uri.rawAuthority else mirror
            })
        }

        // A literal `+` stays: our own links encode it, and a provider's unencoded token may carry it.
        private fun decode(value: String): String? = runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrNull()

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
