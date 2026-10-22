package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.clash.module.TunModule
import com.github.kr328.clash.service.util.Failure.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сырые тексты — из исходников: `config/download.go`, `fetch.go`, `process.go`, служба. */
class FailureTest {
    @Test
    fun `server answers are told apart by code`() {
        val cases = mapOf(
            "DOWNLOAD_FAILED: profile: HTTP 403 Forbidden" to (Kind.Denied to 403),
            "DOWNLOAD_FAILED: profile: HTTP 401 Unauthorized" to (Kind.Denied to 401),
            "DOWNLOAD_FAILED: profile: HTTP 404 Not Found" to (Kind.NotFound to 404),
            "DOWNLOAD_FAILED: profile: HTTP 429 Too Many Requests" to (Kind.Busy to 429),
            "DOWNLOAD_FAILED: profile: HTTP 503 Service Unavailable" to (Kind.Server to 503),
            "DOWNLOAD_FAILED: profile: HTTP 418 I'm a teapot" to (Kind.Http to 418),
            "HTTP 404 Not Found" to (Kind.NotFound to 404),
        )

        for ((raw, expected) in cases) {
            val failure = Failure.of(raw)

            assertEquals(raw, expected, failure.kind to failure.code)
            assertTrue(raw, failure.kind.download)
        }
    }

    @Test
    fun `profile and provider limits select distinct explanations`() {
        assertEquals(Kind.ProfileTooLarge, Failure.of("DOWNLOAD_TOO_LARGE: profile: 16777216").kind)
        assertEquals(Kind.ProviderTooLarge, Failure.of("DOWNLOAD_TOO_LARGE: provider: 67108864").kind)
    }

    @Test
    fun `transport errors and unknown download markers cannot convert previous file`() {
        for (raw in listOf(
            "DOWNLOAD_FAILED: profile: unexpected EOF",
            "DOWNLOAD_FAILED: provider: gzip: invalid checksum",
            "DOWNLOAD_TOO_LARGE: profile: 1", "DOWNLOAD_TOO_LARGE: future: 42",
        )) {
            assertEquals(raw, Kind.Network, Failure.of(raw).kind)
            assertTrue(raw, Failure.of(raw).kind.download)
        }
    }

    @Test
    fun `link problems found while downloading`() {
        assertEquals(
            Kind.Redirects,
            Failure.of("DOWNLOAD_FAILED: profile: Get \"https://a.example/s\": subscription stopped after 10 redirects").kind,
        )
        assertEquals(
            Kind.BadLink,
            Failure.of("DOWNLOAD_FAILED: profile: unsupported scheme ftp of ftp://a.example/s").kind,
        )
    }

    @Test
    fun `parsing errors still permit protocol list conversion`() {
        for (raw in listOf(null, "", "yaml: line 1: cannot unmarshal", "profile does not contain `proxies` or `proxy-providers`")) {
            assertFalse(raw, Failure.of(raw, Kind.BadConfig).kind.download)
        }
    }

    @Test
    fun `the address of a failed download never becomes a detail`() {
        val failure = Failure.of("DOWNLOAD_FAILED: profile: Get \"https://a.example/sub/token\": dial tcp: lookup a.example: no such host")

        assertEquals(Failure(Kind.Network), failure)
    }

    @Test
    fun `service marks`() {
        assertEquals(Failure(Kind.LoadFailed, detail = "proxy 0: missing type"), Failure.of(Failure.LOAD + "proxy 0: missing type"))
        assertEquals(Failure(Kind.Reload, detail = "rules[3]: bad"), Failure.of(Failure.RELOAD + "rules[3]: bad"))
        assertEquals(Kind.LimitDevices, Failure.of(Failure.HWID + "max_devices").kind)
        assertEquals(Kind.LimitHwid, Failure.of(Failure.HWID + "not_supported").kind)
        assertEquals(Kind.LimitOther, Failure.of(Failure.HWID + "").kind)
        assertEquals(Kind.NoProfile, Failure.of(Failure.NO_PROFILE).kind)
        assertEquals(Kind.VpnRejected, Failure.of(Failure.VPN_REJECTED).kind)
        assertEquals(Kind.Crashed, Failure.of(Failure.CRASHED).kind)
        assertEquals(Kind.TunnelFailed, Failure.of(TunModule.START_FAILED).kind)
    }

    @Test
    fun `subscription texts of the service and the core`() {
        assertEquals(Kind.BadLink, Failure.of("Invalid url").kind)
        assertEquals(Kind.BadLink, Failure.of("Unsupported url ss://abc").kind)
        assertEquals(Kind.NotLinks, Failure.of("содержимое не похоже на список ссылок протоколов").kind)
        assertEquals(Kind.NotLinks, Failure.of("convert v2ray subscribe error: format invalid", Kind.BadConfig).kind)
        assertEquals(Kind.NoServers, Failure.of("profile does not contain `proxies` or `proxy-providers`").kind)
    }

    @Test
    fun `an unfamiliar text keeps itself as the detail`() {
        assertEquals(Failure(Kind.Unknown, detail = "tun callback closed"), Failure.of("tun callback closed"))
        assertEquals(Failure(Kind.BadConfig, detail = "yaml: line 3"), Failure.of("yaml: line 3", Kind.BadConfig))
    }

    /** `config/subscription_mirrors.go`: the failure shown, and the mirrors tried besides (D-345). */
    @Test
    fun `mirrors tried besides are counted, the failure itself still told apart`() {
        assertEquals(
            Failure(Kind.NotFound, 404, backups = 2),
            Failure.of("DOWNLOAD_FAILED: profile: HTTP 404 Not Found (+2 backups)"),
        )
        assertEquals(
            Failure(Kind.Network, backups = 1),
            Failure.of("DOWNLOAD_FAILED: profile: Get \"https://sub.example.com/x\": dial tcp: i/o timeout (+1 backups)"),
        )
        assertEquals(
            Failure(Kind.WebPage, backups = 3),
            Failure.of("DOWNLOAD_FAILED: profile: a web page instead of a subscription (+3 backups)"),
        )
        assertEquals(Failure(Kind.WebPage), Failure.of("DOWNLOAD_FAILED: profile: a web page instead of a subscription"))
        // Only a download carries the count; elsewhere the text stays whole.
        assertEquals(Failure(Kind.Unknown, detail = "odd (+2 backups)"), Failure.of("odd (+2 backups)"))
    }
}
