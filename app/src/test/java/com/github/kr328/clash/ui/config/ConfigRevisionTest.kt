package com.github.kr328.clash.ui.config

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfigRevisionTest {
    @Test
    fun `every edit changes the snapshot even when already dirty`() {
        val model = ConfigViewModel()
        model.markDirty()
        val saving = model.state.value
        model.markDirty()
        assertTrue(model.state.value.dirty)
        assertNotEquals(saving, model.state.value)
    }
}
