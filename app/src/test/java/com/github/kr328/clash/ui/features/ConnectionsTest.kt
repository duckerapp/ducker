package com.github.kr328.clash.ui.features

import com.github.kr328.clash.core.model.Connection
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionsTest {
    @Test
    fun `the owner key, the site, the route and the speed`() {
        val c = Connection(
            id = "1", host = "", address = "203.0.113.5", port = 443, uid = 10123,
            rule = "RuleSet", rulePayload = "ru-direct", chain = listOf("DIRECT"), upload = 100, download = 1_900, start = 5,
        )

        assertEquals("uid:10123", connectionKey(c))
        assertEquals("package:org.example", connectionKey(c.copy(process = "org.example")))
        assertEquals("core", connectionKey(c.copy(inner = true, process = "org.example")))
        assertEquals("unknown", connectionKey(c.copy(uid = -1)))

        val app = ConnectionApp("uid:10123", ConnectionApp.Kind.App, "Example", "org.example")
        val row = c.row(app, before = 1_000, seconds = 2.0)
        assertEquals("203.0.113.5", row.site)
        assertEquals("ru-direct → DIRECT", row.route)
        assertEquals(500L, row.speed)
        // The first look has nothing to compare with; a counter that went down (a reset) is not negative.
        assertEquals(0L, c.row(app, before = null, seconds = null).speed)
        assertEquals(0L, c.row(app, before = 9_000, seconds = 1.0).speed)
        assertEquals(true to 0L, c.copy(closed = true).row(app, before = 1_000, seconds = 2.0).let { it.closed to it.speed })
        assertEquals("Match → Node", c.copy(rule = "Match", rulePayload = "", chain = listOf("Node", "Proxy")).row(app, null, null).route)
    }

    @Test
    fun `the filter narrows to an app, then a site, the newest first`() {
        val a = ConnectionApp("a", ConnectionApp.Kind.App, "A")
        val b = ConnectionApp("b", ConnectionApp.Kind.App, "B")
        val rows = listOf(
            ConnectionRow("1", a, "one.example", 443, "", 0, 0, start = 1),
            ConnectionRow("2", b, "one.example", 443, "", 0, 0, start = 3),
            ConnectionRow("3", a, "two.example", 443, "", 0, 0, start = 2),
        )

        assertEquals(listOf("2", "3", "1"), rows.filtered(null, null).map { it.id })
        assertEquals(listOf("3", "1"), rows.filtered(a, null).map { it.id })
        assertEquals(listOf("1"), rows.filtered(a, "one.example").map { it.id })
        assertEquals(listOf("3"), rows.filtered(null, null, " TWO ").map { it.id })
    }

    @Test
    fun `a gone connection stays closed in its place`() {
        val a = ConnectionApp("a", ConnectionApp.Kind.App, "A")
        val one = ConnectionRow("1", a, "one.example", 443, "", speed = 700, total = 900, start = 1)
        val two = ConnectionRow("2", a, "two.example", 443, "", speed = 0, total = 10, start = 2)

        val after = merge(merge(emptyMap(), listOf(one, two)), listOf(two))

        assertEquals(setOf("1", "2"), after.keys)
        assertEquals(one.copy(closed = true, speed = 0), after["1"])
        assertEquals(false, after.getValue("2").closed)
        assertEquals(listOf("2", "1"), after.values.filtered(null, null).map { it.id })
    }
}
