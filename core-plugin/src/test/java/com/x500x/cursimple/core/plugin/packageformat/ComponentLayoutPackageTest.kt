package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.assertPluginError
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComponentLayoutPackageTest {
    @Test fun `layouts require api 12`() {
        assertPluginError(R.string.plugin_error_extension_api_too_old,
            runCatching { PluginPackageReader().read(bundle(api = 11)) }.exceptionOrNull(), 12)
    }

    @Test fun `valid layouts install`() {
        val manifest = PluginPackageReader().read(bundle()).decodeManifest(Json)
        val layout = manifest.extension!!.layouts.single()
        assertEquals("bottom-bar", layout.id)
        assertEquals("底边栏", layout.title)
        assertEquals("bottomBar", layout.shell)
    }

    @Test fun `unknown shell is accepted for forward compatibility`() {
        val manifest = PluginPackageReader().read(bundle(layouts = layout.replace("bottomBar", "rail")))
            .decodeManifest(Json)
        assertEquals("rail", manifest.extension!!.layouts.single().shell)
    }

    @Test fun `duplicate, unsafe or blank layouts are rejected`() {
        val invalid = listOf(
            "$layout,$layout",
            layout.replace("bottom-bar", "../x"),
            layout.replace("底边栏", " "),
            layout.replace("bottomBar", "bottom bar!"),
            (1..9).joinToString(",") { layout.replace("bottom-bar", "layout-$it") },
        )
        invalid.forEach { layouts ->
            val failure = runCatching { PluginPackageReader().read(bundle(layouts = layouts)) }.exceptionOrNull()
            assertEquals(layouts, true, failure != null)
        }
        assertPluginError(R.string.plugin_error_package_illegal_path,
            runCatching { PluginPackageReader().read(bundle(layouts = "$layout,$layout")) }.exceptionOrNull(), "layouts")
    }

    private val layout = """{"id":"bottom-bar","title":"底边栏","shell":"bottomBar"}"""
    private fun bundle(api: Int = 12, layouts: String = layout): ByteArray {
        val files = linkedMapOf("manifest.json" to """{
            "id":"example.layout","name":"Example","version":"1.0.0","versionCode":1,
            "apiVersion":$api,"kind":"extension","entry":"main.js","allowedHosts":["example.invalid"],
            "extension":{"loginUrl":"https://example.invalid/","runUrl":"https://example.invalid/","layouts":[$layouts]}}
        """.trimIndent(), "main.js" to "export async function sync() {}")
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (path, content) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
}
