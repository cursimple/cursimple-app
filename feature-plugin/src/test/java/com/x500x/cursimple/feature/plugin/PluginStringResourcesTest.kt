package com.x500x.cursimple.feature.plugin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStringResourcesTest {
    @Test
    fun `all three languages declare the same string names`() {
        val zh = readStrings("values")
        listOf("values-en", "values-zh-rTW").forEach { qualifier ->
            assertEquals(qualifier, zh.keys.sorted(), readStrings(qualifier).keys.sorted())
        }
    }

    @Test
    fun `all three languages use the same format placeholders`() {
        val zh = readStrings("values")
        listOf("values-en", "values-zh-rTW").forEach { qualifier ->
            val translated = readStrings(qualifier)
            zh.forEach { (name, text) ->
                assertEquals("$qualifier/$name 的占位符不一致", placeholders(text), placeholders(translated.getValue(name)))
            }
        }
    }

    @Test
    fun `install preview wording stays specific`() {
        val zh = readStrings("values")

        assertTrue(zh.getValue("plugin_install_allowed_hosts_empty").contains("未声明"))
        assertTrue(zh.getValue("plugin_install_checksum_verified").contains("checksums.json"))
        assertTrue(zh.getValue("plugin_install_block_checksum").contains("checksums.json"))
        assertTrue(zh.getValue("plugin_install_permission_none").contains("未声明"))
    }

    private fun readStrings(qualifier: String): Map<String, String> {
        val file = resourceFile("src/main/res/$qualifier/strings.xml")
        val text = file.readText().replace(COMMENT, "")
        return ENTRY.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun resourceFile(relativePath: String): File {
        val inModule = File(relativePath)
        if (inModule.isFile) return inModule
        return File("feature-plugin/$relativePath")
    }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.toList()

    private companion object {
        val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val ENTRY = Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val PLACEHOLDER = Regex("%(?:\\d+\\\$)?[a-zA-Z%]")
    }
}
