package com.github.kr328.clash.ui.components

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogoModelTest {
    @Test
    fun `ссылка остаётся ссылкой, пустое — ничем`() {
        assertEquals("https://example.com/logo.png", logoModel(" https://example.com/logo.png "))
        assertNull(logoModel(null))
        assertNull(logoModel("  "))
    }

    @Test
    fun `ссылка не в сеть — запасной логотип`() {
        assertEquals("http://example.com/logo.png", logoModel("http://example.com/logo.png"))
        assertEquals("HTTPS://example.com/logo.png", logoModel("HTTPS://example.com/logo.png"))
        assertNull(logoModel("file:///data/data/app.ducker.android/files/clash/config.yaml"))
        assertNull(logoModel("content://media/external/images/media/1"))
        assertNull(logoModel("android.resource://app.ducker.android/drawable/ducker_mark_dark"))
        assertNull(logoModel("example.com/logo.png"))
    }

    @Test
    fun `data с base64 превращается в байты картинки`() {
        // «PNG» в base64, с переносом строки внутри — так заголовок приходит из панели.
        val model = logoModel("data:image/png;base64,iVBO\nRw==")

        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), model as ByteArray)
    }

    @Test
    fun `data без base64 — это текст, например SVG`() {
        val model = logoModel("data:image/svg+xml,%3Csvg%20xmlns%3D%22a%22%2F%3E")

        assertEquals("<svg xmlns=\"a\"/>", String(model as ByteArray))
    }

    @Test
    fun `битая строка data не роняет, а даёт запасной логотип`() {
        assertNull(logoModel("data:image/png;base64,***"))
        assertNull(logoModel("data:image/png;base64,"))
    }
}
