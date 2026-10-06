package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.assertPluginError
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComponentWidgetPackageTest {
    @Test fun `local HTML widget is declared independently of feed types`() {
        val manifest = PluginPackageReader().read(bundle()).decodeManifest(Json)
        assertEquals("Dashboard", manifest.extension!!.widgets.single().title)
        assertEquals(emptyList<Any>(), manifest.extension.feedTypes)
    }

    @Test fun `widgets require the owning API version`() {
        assertPluginError(R.string.plugin_error_extension_api_too_old,
            runCatching { PluginPackageReader().read(bundle(api = 8)) }.exceptionOrNull(), 9)
    }

    @Test fun `duplicate widget IDs cannot share a binding`() {
        assertPluginError(R.string.plugin_error_package_illegal_path,
            runCatching { PluginPackageReader().read(bundle(widgets = "$widget,$widget")) }.exceptionOrNull(), "widgets")
    }

    @Test fun `widget entry must exist in the package`() {
        assertPluginError(R.string.plugin_error_package_missing_ui_file,
            runCatching { PluginPackageReader().read(bundle(includeHtml = false)) }.exceptionOrNull(), "ui/widget.html")
    }

    @Test fun `widget entry cannot escape the package`() {
        assertPluginError(R.string.plugin_error_package_path_traversal,
            runCatching { PluginPackageReader().read(bundle(widgets = widget.replace("ui/widget.html", "../widget.html"))) }.exceptionOrNull())
    }

    @Test fun `widget IDs cannot inject a second owner`() {
        assertPluginError(R.string.plugin_error_plugin_id_charset,
            runCatching { PluginPackageReader().read(bundle(widgets = widget.replace("dashboard", "other/dashboard"))) }.exceptionOrNull())
    }

    @Test fun `unsupported sizes and non HTML entries are rejected`() {
        for (invalid in listOf(widget.replace("\"columns\":4", "\"columns\":0"),
            widget.replace("\"rows\":2", "\"rows\":7"), widget.replace("ui/widget.html", "main.js"))) {
            assertPluginError(R.string.plugin_error_package_illegal_path,
                runCatching { PluginPackageReader().read(bundle(widgets = invalid)) }.exceptionOrNull())
        }
    }

    private val widget = """{"id":"dashboard","title":"Dashboard","entry":"ui/widget.html","columns":4,"rows":2}"""
    private fun bundle(api: Int = 9, widgets: String = widget, includeHtml: Boolean = true): ByteArray {
        val files = linkedMapOf("manifest.json" to """{
            "id":"example.widget","name":"Example","version":"1.0.0","versionCode":1,
            "apiVersion":$api,"kind":"extension","entry":"main.js","allowedHosts":["example.invalid"],
            "extension":{"loginUrl":"https://example.invalid/","runUrl":"https://example.invalid/","widgets":[$widgets]}}
        """.trimIndent(), "main.js" to "export async function sync() {}")
        if (includeHtml) files["ui/widget.html"] = "<html><head></head><body>Example</body></html>"
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (path, content) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
}
