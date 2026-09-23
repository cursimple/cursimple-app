package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class WidgetProviderMetadataTest {
    @Test
    fun `daily schedule provider spans a full home row by default`() {
        val provider = providerXml("schedule_widget_info.xml")

        // 桌面一行常见是 4 格，装上就铺满整行
        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        // 最小尺寸压到两格宽：鸿蒙、EMUI 这些按 minWidth 过滤的启动器才愿意把它列进选择器
        assertEquals("180dp", provider.androidAttribute("minWidth"))
        assertEquals("110dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("80dp", provider.androidAttribute("minResizeHeight"))
    }

    @Test
    fun `next course and reminder providers span a full home row too`() {
        val next = providerXml("next_course_widget_info.xml")
        assertEquals("4", next.androidAttribute("targetCellWidth"))
        // 默认两行：一行只放得下状态和课名，时间与地点会被截掉
        assertEquals("2", next.androidAttribute("targetCellHeight"))
        assertEquals("110dp", next.androidAttribute("minHeight"))
        assertEquals("180dp", next.androidAttribute("minWidth"))

        val reminder = providerXml("reminder_widget_info.xml")
        assertEquals("4", reminder.androidAttribute("targetCellWidth"))
        assertEquals("2", reminder.androidAttribute("targetCellHeight"))
        assertEquals("180dp", reminder.androidAttribute("minWidth"))
    }

    @Test
    fun `previews are bitmaps so vendor pickers can render them`() {
        // EMUI / 鸿蒙 的小组件选择器按位图处理 previewImage，遇到矢量图会把整项过滤掉，
        // 表现就是「应用装了，桌面却找不到这个小组件」
        listOf("today", "next", "reminder").forEach { id ->
            assertTrue(
                "widget_preview_$id 必须是位图",
                resCandidates("drawable-nodpi/widget_preview_$id.png").any(Files::isRegularFile),
            )
            assertFalse(
                "widget_preview_$id 不能再有矢量版本",
                resCandidates("drawable/widget_preview_$id.xml").any(Files::isRegularFile),
            )
        }
    }

    private fun resCandidates(relative: String): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/res").resolve(relative),
            userDir.resolve("feature-widget/src/main/res").resolve(relative),
        )
    }

    private fun providerXml(fileName: String): Element {
        val path = widgetXmlCandidates(fileName).firstOrNull(Files::isRegularFile)
            ?: error("Cannot find widget metadata XML: $fileName")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(path.toFile()).documentElement
    }

    private fun widgetXmlCandidates(fileName: String): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/res/xml").resolve(fileName),
            userDir.resolve("feature-widget/src/main/res/xml").resolve(fileName),
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
