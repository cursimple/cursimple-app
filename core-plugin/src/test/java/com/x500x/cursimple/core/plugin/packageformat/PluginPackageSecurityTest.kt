package com.x500x.cursimple.core.plugin.packageformat

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginPackageSecurityTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun directoryPayloadAndDirectoryCountAreBounded() {
        assertTrue(runCatching { PluginPackageReader(maxUncompressedBytes = 1024).read(zip("padding/" to ByteArray(1025))) }.isFailure)
        assertTrue(runCatching { PluginPackageReader(maxFileCount = 1).read(zip("a/" to byteArrayOf(), "b/" to byteArrayOf())) }.isFailure)
    }

    @Test fun rejectsDangerousIdsAndPaths() {
        listOf(".", "..", "x".repeat(129), "../escape").forEach { id ->
            assertTrue(id, runCatching { requireSafePluginId(id) }.isFailure)
        }
        listOf("a\u0000b", "../escape", "/absolute", "a/./b", "a".repeat(1025)).forEach { path ->
            assertTrue(path, runCatching { normalizePluginPackagePath(path) }.isFailure)
        }
    }

    @Test fun rejectsFileUsedAsDirectory() {
        assertTrue(runCatching { PluginPackageReader().read(zip("a" to byteArrayOf(), "a/b" to byteArrayOf())) }.isFailure)
    }
}
