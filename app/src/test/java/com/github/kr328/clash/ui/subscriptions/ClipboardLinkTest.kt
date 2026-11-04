package com.github.kr328.clash.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardLinkTest {
    @Test
    fun `subscription and node links are taken`() {
        assertEquals("https://sub.example.com/abc", clipboardLink("  https://sub.example.com/abc\n", emptyList()))
        assertEquals("VLESS://id@host:443?x=1#name", clipboardLink("VLESS://id@host:443?x=1#name", emptyList()))
        assertEquals("hy2://pw@host:443\nss://abc@host:8388", clipboardLink("hy2://pw@host:443\nss://abc@host:8388", emptyList()))
    }

    @Test
    fun `other text and a subscription already added are not`() {
        assertNull(clipboardLink("", emptyList()))
        assertNull(clipboardLink("hello https://sub.example.com/abc", emptyList()))
        assertNull(clipboardLink("https://", emptyList()))
        assertNull(clipboardLink("ftp://host/file", emptyList()))
        assertNull(clipboardLink("https://sub.example.com/abc", listOf("https://sub.example.com/abc")))
    }
}
