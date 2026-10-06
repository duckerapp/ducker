package com.github.kr328.clash.service

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Уведомления о конце подписки за 7, 3 и 1 день (D-149). */
class ExpiryNoticeTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val uuid = "u"

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    // Подписка до 10 октября, 15:00.
    private val expire = at("2026-10-10T15:00")

    @Test
    fun `порог — днём того же дня`() {
        assertEquals(at("2026-10-03T15:00"), noticeAt(expire, 7, zone))
        // Порог в 06:00 — сдвигается на 10:00, в 23:00 — на 21:00, и всё в тот же день.
        assertEquals(at("2026-10-09T10:00"), noticeAt(at("2026-10-10T06:00"), 1, zone))
        assertEquals(at("2026-10-09T21:00"), noticeAt(at("2026-10-10T23:00"), 1, zone))
    }

    @Test
    fun `наступивший порог — самый близкий к концу`() {
        assertNull(dueStep(expire, at("2026-10-03T14:59"), zone))
        assertEquals(7, dueStep(expire, at("2026-10-03T15:00"), zone))
        assertEquals(3, dueStep(expire, at("2026-10-08T12:00"), zone))
        assertEquals(1, dueStep(expire, at("2026-10-09T16:00"), zone))
        assertNull(dueStep(expire, expire, zone))
    }

    @Test
    fun `по порядку — каждый порог один раз`() {
        var seen = decide(uuid, expire, at("2026-09-20T12:00"), zone, null).seen

        val first = decide(uuid, expire, at("2026-10-03T15:30"), zone, seen)
        assertEquals(7, first.notify)
        assertEquals(at("2026-10-07T15:00"), first.next)
        seen = first.seen

        assertNull(decide(uuid, expire, at("2026-10-03T18:00"), zone, seen).notify)
        assertEquals(3, decide(uuid, expire, at("2026-10-07T15:10"), zone, seen).notify)
    }

    @Test
    fun `новая подписка не звенит за прошедший порог`() {
        // Пробная на три дня: 3-дневный порог уже прошёл в момент добавления.
        val added = decide(uuid, expire, at("2026-10-07T16:00"), zone, null)
        assertNull(added.notify)
        assertEquals(at("2026-10-09T15:00"), added.next)
        assertEquals(1, decide(uuid, expire, at("2026-10-09T15:00"), zone, added.seen).notify)

        // Другая активная подписка — тоже заново, а уведомление о прежней снимаем.
        val other = decide("other", expire, at("2026-10-08T12:00"), zone, Seen(uuid, expire, 7))
        assertNull(other.notify)
        assertTrue(other.clear)
        assertFalse(added.clear)
    }

    @Test
    fun `продлили — молчим и убираем показанное`() {
        val renewed = at("2026-11-10T15:00")
        val seen = Seen(uuid, expire, 3)

        val after = decide(uuid, renewed, at("2026-10-08T12:00"), zone, seen)
        assertNull(after.notify)
        assertTrue(after.clear)
        assertEquals(at("2026-11-03T15:00"), after.next)

        // Продлили ненадолго, и порог наступил уже по новому сроку — всё равно молчим.
        assertNull(decide(uuid, at("2026-10-11T15:00"), at("2026-10-08T16:00"), zone, seen).notify)
    }

    @Test
    fun `сократили — говорим сразу`() {
        val seen = decide(uuid, at("2026-10-30T15:00"), at("2026-10-01T12:00"), zone, null).seen
        val cut = at("2026-10-03T15:00")

        val after = decide(uuid, cut, at("2026-10-01T12:00"), zone, seen)
        assertEquals(3, after.notify)
        assertFalse(after.clear)
    }

    @Test
    fun `ночью ждём утра`() {
        val seen = Seen(uuid, expire, 7)

        val night = decide(uuid, expire, at("2026-10-07T23:30"), zone, seen)
        assertNull(night.notify)
        assertEquals(at("2026-10-08T10:00"), night.next)
        // Утром — то, что ждало.
        assertEquals(3, decide(uuid, expire, at("2026-10-08T10:05"), zone, night.seen).notify)

        // Сократили ночью так, что до утра подписка не доживёт, — не ждём.
        val soon = decide(uuid, at("2026-10-08T06:00"), at("2026-10-08T01:00"), zone, seen)
        assertEquals(1, soon.notify)

        // Сокращение, найденное ночью, утром не забыто.
        val cutAtNight = decide(uuid, at("2026-10-09T20:00"), at("2026-10-08T02:00"), zone, Seen(uuid, expire, 7))
        assertNull(cutAtNight.notify)
        assertEquals(3, decide(uuid, at("2026-10-09T20:00"), at("2026-10-08T10:00"), zone, cutAtNight.seen).notify)
    }

    @Test
    fun `что помним — переживает запись в строку`() {
        val seen = Seen("a-b", 123L, 3)

        assertEquals(seen, Seen.parse(seen.toString()))
        assertNull(Seen.parse(""))
    }
}
