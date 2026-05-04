package com.kebiao.viewer.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class WidgetProviderMetadataTest {
    @Test
    fun `daily schedule provider keeps three by three target with two by two resize floor`() {
        val provider = providerXml("schedule_widget_info.xml")

        assertEquals("3", provider.androidAttribute("targetCellWidth"))
        assertEquals("3", provider.androidAttribute("targetCellHeight"))
        assertEquals("110dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("110dp", provider.androidAttribute("minResizeHeight"))
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
