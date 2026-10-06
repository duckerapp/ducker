package com.github.kr328.clash.util

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoAssetsTest {
    @get:Rule val folder = TemporaryFolder()
    private val stamp = 2_000_000_000_000L

    @Test fun replacesOldFileOnlyAfterCopyAndClosesSource() {
        val file = folder.newFile("geosite.dat").apply { writeText("old"); setLastModified(1) }
        var closed = false
        val input = object : ByteArrayInputStream("new".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        extractGeoAsset(file, stamp) {
            assertEquals("old", file.readText())
            input
        }
        assertEquals("new", file.readText())
        assertEquals(stamp, file.lastModified())
        assertTrue(closed)
        assertFalse(folder.root.resolve("geosite.dat.tmp").exists())
    }

    @Test fun readFailurePreservesOldFileAndAllowsRetry() {
        val file = folder.newFile("geoip.metadb").apply { writeText("old"); setLastModified(1) }
        var closed = false
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("interrupted")
            override fun close() { closed = true }
        }
        assertFailsWith<IOException> { extractGeoAsset(file, stamp) { failing } }
        assertEquals("old", file.readText())
        assertEquals(1L, file.lastModified())
        assertTrue(closed)
        assertFalse(folder.root.resolve("geoip.metadb.tmp").exists())
        extractGeoAsset(file, stamp) { ByteArrayInputStream("complete".toByteArray()) }
        assertEquals("complete", file.readText())
    }

    @Test fun retriesAnInterruptedFirstExtraction() {
        val file = folder.root.resolve("geosite.dat")
        folder.newFile("geosite.dat.tmp").writeText("partial")
        extractGeoAsset(file, stamp) { ByteArrayInputStream("complete".toByteArray()) }
        assertEquals("complete", file.readText())
        assertFalse(folder.root.resolve("geosite.dat.tmp").exists())
    }

    @Test fun doesNotReextractCurrentFile() {
        val file = folder.newFile("geosite.dat").apply { writeText("current"); setLastModified(stamp) }
        extractGeoAsset(file, stamp) { error("asset should not be opened") }
        assertEquals("current", file.readText())
    }

    @Test fun renameFailureIsReportedAndTempRemoved() {
        val file = folder.newFolder("geosite.dat")
        assertFailsWith<IOException> {
            extractGeoAsset(file, stamp) { ByteArrayInputStream("complete".toByteArray()) }
        }
        assertTrue(file.isDirectory)
        assertFalse(folder.root.resolve("geosite.dat.tmp").exists())
    }
}
