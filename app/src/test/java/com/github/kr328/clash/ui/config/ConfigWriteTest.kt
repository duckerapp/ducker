package com.github.kr328.clash.ui.config

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigWriteTest {
    @get:Rule val folder = TemporaryFolder()

    private fun write(file: File, text: String) {
        val method = ConfigViewModel::class.java.getDeclaredMethod("writeAtomically", File::class.java, String::class.java)
        method.isAccessible = true
        try { method.invoke(ConfigViewModel(), file, text) }
        catch (e: InvocationTargetException) { throw e.targetException }
    }

    @Test
    fun `failed rename throws preserves destination and removes temporary secrets`() {
        val destination = folder.newFolder("config.yaml")
        val original = File(destination, "keep").apply { writeText("original") }
        assertFailsWith<IOException> { write(destination, "secret: QA_SECRET\n") }
        assertEquals("original", original.readText())
        assertFalse(File(folder.root, "config.yaml.tmp").exists())
    }

    @Test
    fun `write failure preserves previous config`() {
        val destination = folder.newFile("config.yaml").apply { writeText("original") }
        val obstruction = folder.newFolder("config.yaml.tmp")
        File(obstruction, "keep").writeText("blocker")
        assertFailsWith<IOException> { write(destination, "new") }
        assertEquals("original", destination.readText())
        assertTrue(File(obstruction, "keep").exists())
    }

    @Test
    fun `successful replacement contains all UTF8 bytes and no temp`() {
        val destination = folder.newFile("config.yaml").apply { writeText("original") }
        val text = "# Проверка\nmode: rule\n"
        write(destination, text)
        assertEquals(text, destination.readText())
        assertFalse(File(folder.root, "config.yaml.tmp").exists())
    }
}
