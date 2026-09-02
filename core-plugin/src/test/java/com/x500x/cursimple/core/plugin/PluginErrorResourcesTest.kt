package com.x500x.cursimple.core.plugin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginErrorResourcesTest {
    @Test
    fun `both languages declare the same string names`() {
        val zh = readStrings("values")
        val en = readStrings("values-en")

        assertEquals(zh.keys.sorted(), en.keys.sorted())
    }

    @Test
    fun `both languages use the same format placeholders`() {
        val zh = readStrings("values")
        val en = readStrings("values-en")

        zh.forEach { (name, text) ->
            assertEquals(
                "$name 的占位符不一致",
                placeholders(text),
                placeholders(en.getValue(name)),
            )
        }
    }

    @Test
    fun `checksum coverage keeps one entry per optional segment`() {
        val zh = readStrings("values")

        assertTrue(placeholders(zh.getValue("plugin_error_checksum_coverage")).isEmpty())
        assertEquals(
            listOf("%1\$s"),
            placeholders(zh.getValue("plugin_error_checksum_coverage_missing")),
        )
        assertEquals(
            listOf("%1\$s"),
            placeholders(zh.getValue("plugin_error_checksum_coverage_extra")),
        )
        assertEquals(
            listOf("%1\$s", "%2\$s"),
            placeholders(zh.getValue("plugin_error_checksum_coverage_missing_extra")),
        )
    }

    private fun readStrings(qualifier: String): Map<String, String> {
        val file = resourceFile("src/main/res/$qualifier/strings.xml")
        val text = file.readText().replace(COMMENT, "")
        return ENTRY.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    /** 单元测试的工作目录可能是模块目录，也可能是仓库根目录。 */
    private fun resourceFile(relativePath: String): File {
        val inModule = File(relativePath)
        if (inModule.isFile) return inModule
        return File("core-plugin/$relativePath")
    }

    /** 取出 %s、%1$d、%% 这类格式符，按出现顺序比较。 */
    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.toList()

    private companion object {
        val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val ENTRY = Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val PLACEHOLDER = Regex("%(?:\\d+\\\$)?[a-zA-Z%]")
    }
}
