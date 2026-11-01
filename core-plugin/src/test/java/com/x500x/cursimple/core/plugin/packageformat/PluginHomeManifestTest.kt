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
            "ui/home.js", "https://school.example.edu/home", "https://school.example.edu/login",
            listOf(PluginHomeSectionSpec("grades", "Grades", url = "https://school.example.edu/grades")),
        ),
    )

    private fun validate(value: PluginManifest, includeHome: Boolean = true): PluginManifest {
        val files = mutableMapOf("manifest.json" to Json.encodeToString(value).toByteArray(), "main.js" to byteArrayOf())
        if (includeHome) files["ui/home.js"] = byteArrayOf()
        return PluginPackageLayout(files).decodeValidatedManifest(Json)
    }

    @Test fun homeIsOptionalForExistingPlugins() {
        assertNull(validate(manifest.copy(home = null), false).home)
        assertEquals("grades", validate(manifest).home!!.sections.single().id)
    }

    @Test fun homeCannotLoadAnExternalOrUnencryptedQueryPage() {
        for (url in listOf("http://school.example.edu/grades", "https://evil.example/grades", "https://u:p@school.example.edu/grades")) {
            assertThrows(IllegalArgumentException::class.java) {
                validate(manifest.copy(home = manifest.home!!.copy(
                    sections = listOf(PluginHomeSectionSpec("grades", "Grades", url = url)),
                )))
            }
        }
    }

    @Test fun homeRequiresItsOwnEntryAndUniqueSections() {
        assertThrows(Exception::class.java) { validate(manifest, false) }
        assertThrows(IllegalArgumentException::class.java) {
            validate(manifest.copy(home = manifest.home!!.copy(
                sections = listOf(PluginHomeSectionSpec("grades", "A"), PluginHomeSectionSpec("grades", "B")),
            )))
        }
    }
}
