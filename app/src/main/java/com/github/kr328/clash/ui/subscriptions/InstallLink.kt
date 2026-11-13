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
 *
 * [key] is an encrypted subscription's age key, `&key=…` (D-505): without it the subscription does not open on the
 * other device. Only a value shaped as a key is taken ([ageKey]); an older ducker ignores it and asks for the key.
 *
 * [name] is the provider's `name=`: the subscription's name while the provider sends no `profile-title` (D-506). It
 * is read, not written: «Поделиться» keeps the bare link for a subscription without mirrors and a key.
 */
data class InstallLink(
    val link: String,
    val mirrors: List<String> = emptyList(),
    val key: String? = null,
    val name: String? = null,
) {
    /** Without mirrors and a key it is the bare link, as before them: any scanner and an older TV take it. */
    override fun toString(): String = if (mirrors.isEmpty() && key == null) link else
        "ducker://install-config?url=${encode(link)}" + mirrors.joinToString("") { "&mirror=${encode(it)}" } +
            key?.let { "&key=${encode(it)}" }.orEmpty()

    companion object {
        /** Schemes of an `…://install-config` link: ours and those the panels give. */
        val SCHEMES = setOf("ducker", "clash", "clashmeta")

        /** A one-line http(s) link, or an `install-config` link carrying one; `null` — neither. */
        fun parse(text: String): InstallLink? {
            subscriptionLink(text)?.let { return InstallLink(it) }

            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES || uri.host != "install-config") return null

            val raw = uri.rawQuery.orEmpty().split('&').map { it.substringBefore('=') to it.substringAfter('=', "") }
            val params = raw.map { it.first to decode(it.second) }
            val link = params.firstOrNull { it.first == "url" }?.second?.trim()?.let(::subscriptionLink) ?: return null

            return InstallLink(
                link,
                params.filter { it.first == "mirror" }.mapNotNull { it.second },
                ageKey(params.firstOrNull { it.first == "key" }?.second),
                // A `+` in a name is a space, as Android reads it from a link the camera opens (`HomeActivity`).
                linkName(raw.firstOrNull { it.first == "name" }?.second?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }),
            )
        }

        /** [mirrors] of [link], each as short as it goes: one on the link's own path as its bare host. */
        fun of(link: String, mirrors: List<String>, key: String? = null): InstallLink {
            val own = runCatching { URI(link) }.getOrNull()

            return InstallLink(link, mirrors.map { mirror ->
                val uri = runCatching { URI(mirror) }.getOrNull()

                if (own != null && uri != null && uri.scheme == own.scheme?.lowercase() &&
                    uri.rawPath == own.rawPath && uri.rawQuery == own.rawQuery) uri.rawAuthority else mirror
            }, ageKey(key))
        }

        /**
         * [value] if it is shaped as one age key, X25519 or post-quantum (`AGE-SECRET-KEY-PQ-1…`), else `null`: a link
         * is anyone's input, and its key lands in the add sheet's field and in the core's set of keys.
         */
        fun ageKey(value: String?): String? = value?.trim()?.takeIf(AGE_KEY::matches)

        private val AGE_KEY = Regex("AGE-SECRET-KEY-[0-9A-Z-]{1,113}")

        /** [value] as a name on a card, or `null`: empty, over 128 characters, with control characters or broken bytes. */
        fun linkName(value: String?): String? =
            value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 && it.none { c -> c.isISOControl() || c == '\uFFFD' } }

        // A literal `+` stays: our own links encode it, and a provider's unencoded token may carry it.
        private fun decode(value: String): String? = runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrNull()

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
