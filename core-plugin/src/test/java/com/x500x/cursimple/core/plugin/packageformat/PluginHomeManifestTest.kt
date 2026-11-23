package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.manifest.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PluginHomeManifestTest {
    private val manifest = PluginManifest(
        id = "test.home", name = "Test", version = "1", versionCode = 1, entry = "main.js",
        allowedHosts = listOf("school.example.edu"),
        home = PluginHomeSpec(
            entry = "ui/home.js",
            page = "ui/home.html",
            runUrl = "https://school.example.edu/home",
            loginUrl = "https://school.example.edu/login",
        ),
    )

    private fun validate(value: PluginManifest, files: Set<String> = setOf("ui/home.js", "ui/home.html")): PluginManifest {
        val all = mutableMapOf("manifest.json" to Json.encodeToString(value).toByteArray(), "main.js" to byteArrayOf())
        files.forEach { all[it] = byteArrayOf() }
        return PluginPackageLayout(all).decodeValidatedManifest(Json)
    }

    @Test fun homeIsOptionalForExistingPlugins() {
        assertNull(validate(manifest.copy(home = null), emptySet()).home)
    }

    @Test fun homeKeepsItsOwnPageAndScript() {
        val home = validate(manifest).home!!
        assertEquals("ui/home.html", home.page)
        assertEquals("ui/home.js", home.entry)
    }

    @Test fun homeNeedsBothItsScriptAndItsPage() {
        assertThrows(Exception::class.java) { validate(manifest, setOf("ui/home.html")) }
        assertThrows(Exception::class.java) { validate(manifest, setOf("ui/home.js")) }
    }

    @Test fun homeCannotLoadAnExternalOrUnencryptedAddress() {
        for (url in listOf("http://school.example.edu/home", "https://evil.example/home", "https://u:p@school.example.edu/home")) {
            assertThrows(IllegalArgumentException::class.java) {
                validate(manifest.copy(home = manifest.home!!.copy(runUrl = url)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                validate(manifest.copy(home = manifest.home!!.copy(loginUrl = url)))
            }
        }
    }
}
