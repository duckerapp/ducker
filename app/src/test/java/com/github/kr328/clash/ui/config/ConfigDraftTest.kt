package com.github.kr328.clash.ui.config

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigDraftTest {
    @get:Rule val folder = TemporaryFolder()

    private fun drafts() = ConfigDraft(File(folder.root, "config-draft"))

    @Test
    fun `черновик переживает новый процесс и читается по своему ключу`() {
        drafts().write("a", "mode: rule\n")

        assertEquals("mode: rule\n", drafts().read("a"))
        assertNull(drafts().read("b"))
    }

    @Test
    fun `черновик один — новый стирает прежний`() {
        drafts().write("a", "first")
        drafts().write("content://files/config.yaml", "second")

        assertNull(drafts().read("a"))
        assertEquals("second", drafts().read("content://files/config.yaml"))
        assertEquals(1, File(folder.root, "config-draft").list()?.size)
    }

    @Test
    fun `удаление чужого ключа черновик не трогает`() {
        drafts().write("a", "text")
        drafts().drop("b")
        assertEquals("text", drafts().read("a"))

        drafts().drop("a")
        assertNull(drafts().read("a"))
    }
}
