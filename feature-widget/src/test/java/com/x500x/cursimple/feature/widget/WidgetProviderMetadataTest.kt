package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
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

        // 桌面一行常见是 4 格，装上就铺满整行，缩小仍允许到 110dp
        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        assertEquals("110dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("110dp", provider.androidAttribute("minResizeHeight"))
    }

    @Test
    fun `next course and reminder providers span a full home row too`() {
        val next = providerXml("next_course_widget_info.xml")
        assertEquals("4", next.androidAttribute("targetCellWidth"))
        assertEquals("1", next.androidAttribute("targetCellHeight"))

        val reminder = providerXml("reminder_widget_info.xml")
        assertEquals("4", reminder.androidAttribute("targetCellWidth"))
        assertEquals("2", reminder.androidAttribute("targetCellHeight"))
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
