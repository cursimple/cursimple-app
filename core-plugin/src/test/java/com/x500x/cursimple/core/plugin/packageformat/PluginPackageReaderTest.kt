package com.x500x.cursimple.core.plugin.packageformat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginPackageReaderTest {
    private val reader = PluginPackageReader()

    @Test
    fun `reader accepts package with manifest and entry`() {
        val layout = reader.read(
            zipBytes(
                "manifest.json" to manifestJson(entry = "main.js"),
                "main.js" to "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }",
            ),
        )

        assertTrue(layout.files.containsKey("manifest.json"))
        assertEquals("edu.demo", layout.decodeManifest(kotlinx.serialization.json.Json).id)
    }

    @Test
    fun `reader rejects zip slip path`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "main.js"),
                    "../main.js" to "bad",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("路径穿越"))
    }

    @Test
    fun `reader rejects duplicate normalized path`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "dir/main.js"),
                    "dir/main.js" to "one",
                    "dir\\main.js" to "two",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("重复文件"))
    }

    @Test
    fun `reader requires entry file`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "missing.js"),
                    "main.js" to "ok",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("入口文件"))
    }

    private fun manifestJson(entry: String): String {
        return """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "$entry",
              "permissions": ["schedule.write"]
            }
        """.trimIndent()
    }

    private fun zipBytes(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
