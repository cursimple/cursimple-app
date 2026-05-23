package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class WidgetAlarmGuardManifestTest {
    @Test
    fun `silent guard receiver is app-internal`() {
        val receiver = manifestReceiver(".WidgetAlarmGuardReceiver")

        assertNotNull(receiver)
        assertEquals("false", receiver!!.androidAttribute("exported"))
        assertEquals(0, receiver.getElementsByTagName("intent-filter").length)
    }

    private fun manifestReceiver(name: String): Element? {
        val manifest = manifestCandidates().firstOrNull(Files::isRegularFile)
            ?: error("Cannot find feature-widget AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val receivers = factory.newDocumentBuilder()
            .parse(manifest.toFile())
            .getElementsByTagName("receiver")
        for (index in 0 until receivers.length) {
            val receiver = receivers.item(index) as? Element ?: continue
            if (receiver.androidAttribute("name") == name) return receiver
        }
        return null
    }

    private fun manifestCandidates(): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/AndroidManifest.xml"),
            userDir.resolve("feature-widget/src/main/AndroidManifest.xml"),
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
