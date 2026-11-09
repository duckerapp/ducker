package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The User-Agent of D-439 against each panel's default rule for a mihomo config, as read in R1
 * (`notes/PANELS-2026-11.md`, section 1). Each panel matches from the start of the string.
 */
class SubscriptionHeadersTest {
    private val ua = SubscriptionHeaders.userAgent("1.0.0")

    private fun Regex.fromStart(text: String) = find(text)?.range?.first == 0

    @Test
    fun `the platform closes the string`() {
        assertEquals("mihomo/${SubscriptionHeaders.MIHOMO_RELEASE} ducker/1.0.0 (android)", ua)
    }

    @Test
    fun `Remnawave, PasarGuard and Marzban give their mihomo config`() {
        val rules = mapOf(
            // prisma/seed/default/response-rules.ts:35, case-insensitive.
            "Remnawave" to Regex(
                "^(?:flclash|rabbit|flowvy|murge|mihomo|prizrak-box|koala-clash|clash(?:-verge|-nyanpasu|x meta|[-.]?meta))",
                RegexOption.IGNORE_CASE,
            ),
            // app/db/migrations/versions/9af04c077ede_init_settings.py:203.
            "PasarGuard" to Regex(
                "^(?:FlClashX?|Flowvy|[Cc]lash(?:-(?:[Vv]erge|nyanpasu)|X [Mm]eta|-?[Mm]eta)|[Kk]oala-[Cc]lash|[Mm](?:urge|ihomo)|prizrak-box|clash\\.meta)"
            ),
            // app/routers/subscription.py:81.
            "Marzban" to Regex("^([Cc]lash-verge|[Cc]lash[-.]?[Mm]eta|[Ff][Ll][Cc]lash|[Mm]ihomo)"),
        )

        for ((panel, rule) in rules) assertEquals(panel, true, rule.fromStart(ua))
    }

    /** These three give a link list by default: their providers get the explicit links of R7 (D-441). */
    @Test
    fun `Marzneshin, 3x-ui and Hiddify stay on the link list by default`() {
        val rules = mapOf(
            // app/db/migrations/versions/20240608_9001356f0063_create_settings.py:39-60.
            "Marzneshin" to Regex("^([Cc]lash-verge|[Cc]lash-?[Mm]eta)"),
            // hiddifypanel/panel/user/user.py:344, case-insensitive.
            "Hiddify" to Regex(
                "^(Clash|Clash-verge|Clash-?Meta|Stash|NekoBox|NekoRay|Pharos|hiddify-desktop)",
                RegexOption.IGNORE_CASE,
            ),
        )

        for ((panel, rule) in rules) assertEquals(panel, false, rule.fromStart(ua))
        // 3x-ui: `(?i)(clash|mihomo)` anywhere, but only once its admin turns Clash on (setting.go:185).
        assertEquals(true, Regex("(clash|mihomo)", RegexOption.IGNORE_CASE).containsMatchIn(ua))
    }
}
