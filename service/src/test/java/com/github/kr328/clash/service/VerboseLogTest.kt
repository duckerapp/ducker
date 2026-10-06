package com.github.kr328.clash.service

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Подробный журнал» выключается сам через сутки (D-224). */
class VerboseLogTest {
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 1_800_000_000_000L

    @Test
    fun `сутки идут от включения`() {
        val since = now - 23 * hour

        assertEquals(since + 24 * hour, verboseLogUntil(since, now))
        assertTrue(verboseLogUntil(since, now) > now)
        assertTrue(verboseLogUntil(now - 24 * hour, now) <= now)
        assertTrue(verboseLogUntil(now - 100 * hour, now) <= now)
    }

    @Test
    fun `без отметки и после перевода часов назад — сутки с этой минуты`() {
        assertEquals(now + 24 * hour, verboseLogUntil(0, now))
        assertEquals(now + 24 * hour, verboseLogUntil(now + 5 * hour, now))
    }
}
