package com.x500x.cursimple.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class SystemAlarmReceiverManifestTest {
    @Test
    fun `system environment receiver is internal and listens for system broadcasts`() {
        val receiver = manifestReceiver(".app.reminder.SystemAlarmEnvironmentReceiver")

        assertNotNull(receiver)
        assertEquals("false", receiver!!.androidAttribute("exported"))
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.intent.action.DATE_CHANGED",
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            ),
            receiver.intentActions(),
        )
    }

    @Test
    fun `internal system alarm check receiver is not exported`() {
        val receiver = manifestReceiver(".app.reminder.SystemAlarmCheckReceiver")

        assertNotNull(receiver)
        assertEquals("false", receiver!!.androidAttribute("exported"))
        assertEquals(0, receiver.getElementsByTagName("intent-filter").length)
    }

    private fun manifestReceiver(name: String): Element? {
        val manifest = manifestCandidates().firstOrNull(Files::isRegularFile)
            ?: error("Cannot find app AndroidManifest.xml")
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
            userDir.resolve("app/src/main/AndroidManifest.xml"),
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.intentActions(): Set<String> {
        val actions = getElementsByTagName("action")
        return buildSet {
            for (index in 0 until actions.length) {
                val action = actions.item(index) as? Element ?: continue
                add(action.androidAttribute("name"))
            }
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
