package com.github.kr328.clash.service.util

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * A subscription's directory is replaced so that a death at any step leaves a whole copy: deleting first and
 * copying after left a subscription without its config that no update could repair (verified on Android 15).
 */
class FilesTest {
    private val root: File = Files.createTempDirectory("files-test").toFile()
    private val imported = root.resolve("imported").apply { mkdirs() }
    private val target = imported.resolve("uuid")

    private fun dir(at: File, config: String): File = at.apply {
        mkdirs()
        resolve("config.yaml").writeText(config)
        resolve("providers").mkdirs()
        resolve("providers/rules.yaml").writeText("rules of $config")
    }

    private fun names(): List<String> = imported.list().orEmpty().sorted()

    @Test
    fun `replaces the old copy and leaves nothing beside it`() {
        dir(target, "old")
        val source = dir(root.resolve("processing"), "new")

        target.replaceWithCopyOf(source)

        assertEquals("new", target.resolve("config.yaml").readText())
        assertEquals("rules of new", target.resolve("providers/rules.yaml").readText())
        assertEquals(listOf("uuid"), names())
    }

    @Test
    fun `a first copy needs no old one`() {
        target.replaceWithCopyOf(dir(root.resolve("processing"), "new"))

        assertEquals("new", target.resolve("config.yaml").readText())
        assertEquals(listOf("uuid"), names())
    }

    @Test
    fun `a failed copy keeps the old one`() {
        dir(target, "old")

        assertFailsWith<Exception> { target.replaceWithCopyOf(root.resolve("missing")) }

        assertEquals("old", target.resolve("config.yaml").readText())
        assertEquals(listOf("uuid"), names())
    }

    @Test
    fun `a death between the renames is undone at the next start`() {
        dir(imported.resolve("uuid.old"), "old")
        dir(imported.resolve("uuid.new"), "new")

        imported.recoverReplaced()

        assertEquals("old", target.resolve("config.yaml").readText())
        assertEquals(listOf("uuid"), names())
    }

    @Test
    fun `a death after the second rename keeps the new copy`() {
        dir(target, "new")
        dir(imported.resolve("uuid.old"), "old")

        imported.recoverReplaced()

        assertEquals("new", target.resolve("config.yaml").readText())
        assertEquals(listOf("uuid"), names())
    }

    @Test
    fun `a death during the copy drops the half copy`() {
        dir(target, "old")
        imported.resolve("uuid.new").mkdirs()

        imported.recoverReplaced()

        assertEquals("old", target.resolve("config.yaml").readText())
        assertFalse(imported.resolve("uuid.new").exists())
    }
}
