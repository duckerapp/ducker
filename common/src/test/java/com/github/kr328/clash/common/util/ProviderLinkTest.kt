package com.github.kr328.clash.common.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderLinkTest {
    @Test fun acceptsWebDestinationsAndNormalizesInternationalHost() {
        for (url in listOf("https://provider.invalid", "http://provider.invalid:8080/buy?a=1#renew",
            "https://[2001:db8::1]:443/help", "https://user:synthetic@provider.invalid/a%20b")) {
            assertEquals(url, providerLink(url))
        }
        assertEquals("https://provider.invalid/help", providerLink("HTTPS://provider.invalid/help"))
        assertEquals("https://xn--e1afmkfd.invalid/help", providerLink("https://пример.invalid/help"))
    }

    @Test fun rejectsOtherSchemesRelativeOpaqueAndAmbiguousAddresses() {
        for (url in listOf("", "/help", "//provider.invalid/help", "provider.invalid", "https:help",
            "https:///help", "file:///qa", "content://qa.invalid/1", "javascript:alert(1)",
            "intent://qa.invalid/#Intent;scheme=https;end", "mailto:qa@provider.invalid", "data:text/plain,qa",
            "ducker://install-config?url=https://provider.invalid", "https://one@two@provider.invalid",
            "https://provider.invalid\\@other.invalid", " https://provider.invalid", "https://provider.invalid/\n")) {
            assertNull(providerLink(url), url)
        }
    }

    @Test fun rejectsMalformedHostsPortsAndEscapes() {
        for (url in listOf("https://", "http://?query=qa", "https://bad host/help", "https://bad_host/help",
            "https://provider.invalid:", "https://provider.invalid:0", "https://provider.invalid:-1",
            "https://provider.invalid:65536", "https://provider.invalid:abc", "https://provider.invalid/%xx",
            "https://provider.invalid/%", "https://256.0.0.1/help", "https://[bad]/help", "https://.invalid",
            "https://a..invalid", "https://%70rovider.invalid", "https://[2001:db8::1]bad/help")) {
            assertNull(providerLink(url), url)
        }
    }

    @Test fun acceptsTelegramProviderNavigation() {
        for (url in listOf("tg://resolve?domain=qa_support_bot&start=synthetic", "tg://resolve?phone=%2B123456789",
            "tg://resolve?domain=qa_channel&post=123", "tg://join?invite=QA_synthetic-invite",
            "tg://privatepost?channel=123&post=456", "tg://resolve?domain=qa_support&text=hello%20there")) {
            assertEquals(url, providerLink(url))
        }
        assertEquals("tg://resolve?domain=qa_support", providerLink("TG://resolve?domain=qa_support"))
    }

    @Test fun rejectsUnsupportedOrMalformedTelegramLinks() {
        for (url in listOf("tg:resolve?domain=qa", "tg://", "tg://resolve", "tg://resolve?domain=",
            "tg://resolve?domain=qa.invalid", "tg://resolve?domain=qa&domain=other", "tg://join?invite=",
            "tg://privatepost?channel=123", "tg://privatepost?channel=123&post=-1",
            "tg://resolve:123?domain=qa", "tg://user@resolve?domain=qa", "tg://resolve/path?domain=qa",
            "tg://resolve?domain=qa#fragment", "tg://resolve?domain=qa&text=%00", "tg://unknown?value=qa",
            "tg://proxy?server=qa.invalid&port=443", "tg://resolve?domain=%FF")) {
            assertNull(providerLink(url), url)
        }
    }

    @Test fun acceptsTelegramBusinessAndInvoiceLinks() {
        for (url in listOf("tg://message?slug=QA_synthetic-support", "tg://invoice?slug=QA_synthetic-purchase")) {
            assertEquals(url, providerLink(url))
        }
        assertNull(providerLink("tg://message?slug="))
        assertNull(providerLink("tg://invoice?slug=bad/path"))
    }

    @Test fun rejectsConflictingTelegramDestinations() {
        assertNull(providerLink("tg://resolve?domain=bad.domain&phone=123456789"))
        assertNull(providerLink("tg://resolve?domain=qa_support&phone=123456789"))
    }

    @Test fun preferredAndFallbackLinksAreBothValidated() {
        assertEquals("https://provider.invalid/buy", firstProviderLink("https://provider.invalid/buy", "https://provider.invalid/page"))
        assertEquals("https://provider.invalid/page", firstProviderLink("file:///qa", "https://provider.invalid/page"))
        assertNull(firstProviderLink("intent://qa", "/relative"))
    }
}
