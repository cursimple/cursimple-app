package com.x500x.cursimple.core.plugin.packageformat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedStreamReadTest {
    @Test
    fun `content within the limit is returned unchanged`() {
        val payload = ByteArray(1000) { (it % 251).toByte() }

        val read = ByteArrayInputStream(payload).readAtMostBytes(1000L)

        assertNotNull(read)
        assertArrayEquals(payload, read)
    }

    @Test
    fun `oversized content is rejected without buffering the whole stream`() {
        val source = CountingInputStream(ByteArrayInputStream(ByteArray(1024 * 1024)))

        val read = source.readAtMostBytes(16L)

        assertNull(read)
        assertTrue(
            "已读取 ${source.readCount} 字节，超出上限加一个缓冲区的范围",
            source.readCount <= 16L + BOUNDED_READ_BUFFER_BYTES,
        )
    }

    @Test
    fun `zero limit accepts only an empty stream`() {
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMostBytes(0L))
        assertNull(ByteArrayInputStream(ByteArray(1)).readAtMostBytes(0L))
    }

    @Test
    fun `negative remaining budget is rejected`() {
        assertNull(ByteArrayInputStream(ByteArray(1)).readAtMostBytes(-1L))
    }

    private class CountingInputStream(source: InputStream) : FilterInputStream(source) {
        var readCount: Long = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                readCount += 1
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val value = super.read(b, off, len)
            if (value > 0) {
                readCount += value.toLong()
            }
            return value
        }
    }
}

class PluginPackageSizeLimitTest {
    @Test
    fun `single oversized entry is rejected while streaming`() {
        val reader = PluginPackageReader(maxUncompressedBytes = 4096L)

        val error = runCatching { reader.read(bombPackageBytes()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("解压后体积超过限制"))
    }

    @Test
    fun `package within the limit is still accepted`() {
        val reader = PluginPackageReader(maxUncompressedBytes = 64L * 1024L)

        val layout = reader.read(
            zipBytes(
                "manifest.json" to MANIFEST_JSON.toByteArray(),
                "main.js" to "export async function run(ctx) { return ctx; }".toByteArray(),
            ),
        )

        assertTrue(layout.files.containsKey("main.js"))
    }

    private fun bombPackageBytes(): ByteArray {
        return zipBytes(
            "manifest.json" to MANIFEST_JSON.toByteArray(),
            "main.js" to "export async function run(ctx) { return ctx; }".toByteArray(),
            "payload.bin" to ByteArray(8 * 1024 * 1024),
        )
    }

    private fun zipBytes(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val MANIFEST_JSON = """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "main.js"
            }
        """
    }
}
