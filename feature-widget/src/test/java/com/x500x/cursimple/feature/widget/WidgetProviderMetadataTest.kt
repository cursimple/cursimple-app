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

        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        // Minimum dimensions remain compatible with launcher picker filters.
        assertEquals("180dp", provider.androidAttribute("minWidth"))
        assertEquals("110dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("80dp", provider.androidAttribute("minResizeHeight"))
    }

    @Test
    fun `next course and reminder providers span a full home row too`() {
        val next = providerXml("next_course_widget_info.xml")
        assertEquals("4", next.androidAttribute("targetCellWidth"))
        // Default two-row height preserves both title/status and time/location.
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
        // Use bitmap previews because some launchers reject vector previewImage resources.
        listOf("today", "next", "reminder", "calendar", "tasks").forEach { id ->
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

    @Test
    fun `component widgets ship disabled and system widgets ship enabled`() {
        val path = sequenceOf(
            Paths.get(System.getProperty("user.dir")).resolve("src/main/AndroidManifest.xml"),
            Paths.get(System.getProperty("user.dir")).resolve("feature-widget/src/main/AndroidManifest.xml"),
        ).first(Files::isRegularFile)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(path.toFile())
        val receivers = doc.getElementsByTagName("receiver")
        val enabled = (0 until receivers.length).map { receivers.item(it) as Element }
            .associate { it.androidAttribute("name") to (it.androidAttribute("enabled") != "false") }
        // Component task providers stay hidden without an installed enabled extension.
        assertFalse(enabled.getValue(".PendingTaskWidgetReceiver"))
        assertFalse(enabled.getValue(".PendingTaskWidgetReceiverMIUI"))
        // Course calendars depend only on app-owned timetable and event data.
        assertTrue(enabled.getValue(".CalendarWidgetReceiver"))
        assertTrue(enabled.getValue(".ScheduleGlanceWidgetReceiver"))
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
