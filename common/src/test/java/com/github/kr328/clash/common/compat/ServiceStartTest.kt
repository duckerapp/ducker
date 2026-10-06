package com.github.kr328.clash.common.compat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStartTest {
    @Test fun acceptedStartReturnsTrue() {
        assertTrue(tryStartForegroundService({}, { error("unexpected rejection") }))
    }

    @Test fun androidDenialsAreReportedAndReturnFalse() {
        for (error in listOf(IllegalStateException("background"), SecurityException("permission"))) {
            var reported: RuntimeException? = null
            assertFalse(tryStartForegroundService({ throw error }, { reported = it }))
            assertEquals(error, reported)
        }
    }

    @Test fun programmingErrorsAreNotHidden() {
        assertFailsWith<IllegalArgumentException> {
            tryStartForegroundService({ throw IllegalArgumentException("invalid intent") }, {})
        }
    }
}
