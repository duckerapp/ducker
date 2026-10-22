package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.util.providerLink
import java.net.URI

/**
 * Other addresses of the same subscription (D-345, stage 21 of `PLAN.md`): the provider's
 * `ducker-mirrors` and Happ's `fallback-url`, and a permanent move — Happ's `new-url` and `new-domain`
 * (F-329). The core tries them in [order] (`config/subscription_mirrors.go`).
 *
 * An address is never less protected than the link itself: an https link gets https addresses only;
 * http is allowed only for an http link, whose token travels in the clear anyway. No `login:password@`.
 */
object SubscriptionMirrors {
    /** Mirrors kept at most: the list is replaced on every update, so an abandoned domain soon drops out. */
    const val MAX = 5

    /**
     * The mirrors of [source]: the comma-separated entries of `ducker-mirrors`, then `fallback-url`.
     * A bare host (`sub2.example.com`, a port allowed) is the link itself on another domain — its scheme,
     * path and query kept — so one static header value serves every user of any panel. A full address
     * is taken as is. Bad entries, repeats and the link itself are skipped silently: the header is a hint.
     */
    fun resolve(source: String, mirrors: String, fallbackUrl: String): List<String> {
        val link = parse(source) ?: return emptyList()
        val own = itself(source)

        return (mirrors.split(',') + fallbackUrl)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { address(link, it, host = !it.contains("://")) }
            .distinct()
            .filter { it != own }
            .take(MAX)
    }

    /** The order to try: the address that answered last time while it still belongs, the link, the mirrors. */
    fun order(source: String, mirrors: List<String>, servedBy: String): List<String> =
        (listOfNotNull(servedBy.takeIf { it == source || it in mirrors }) + source + mirrors).distinct()

    /**
     * Where the provider moves the subscription: `new-url` as is, or the link on the `new-domain`;
     * `null` when it sends neither, the address is bad or weaker, or it is the link itself.
     */
    fun moveTarget(source: String, newUrl: String, newDomain: String): String? {
        val link = parse(source) ?: return null

        val target = when {
            newUrl.isNotBlank() -> address(link, newUrl.trim(), host = false)
            newDomain.isNotBlank() -> address(link, newDomain.trim(), host = true)
            else -> null
        }

        return target?.takeIf { it != itself(source) }
    }

    /** The host (and port) to show for an address: never the path, which carries the person's token. */
    fun host(address: String): String = runCatching {
        val uri = URI(address)
        uri.rawAuthority.substringAfterLast('@')
    }.getOrDefault("")

    /** The link as an address compares: the fragment after `#` never reaches the server. */
    private fun itself(source: String): String? = providerLink(source.substringBefore('#'))

    private fun parse(source: String): URI? = runCatching { URI(source) }.getOrNull()
        ?.takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }

    /** [entry] as an address for [link]: a bare host when [host], a full address otherwise. */
    private fun address(link: URI, entry: String, host: Boolean): String? {
        val raw = if (host) {
            if (entry.any { it in "/?#@" }) return null
            "${link.scheme.lowercase()}://$entry${link.rawPath.orEmpty()}" + (link.rawQuery?.let { "?$it" } ?: "")
        } else entry

        val address = providerLink(raw.substringBefore('#')) ?: return null
        val uri = URI(address)

        return address.takeIf {
            uri.rawAuthority?.contains('@') == false &&
                (uri.scheme == "https" || (uri.scheme == "http" && link.scheme.equals("http", true)))
        }
    }
}
