package com.x500x.cursimple.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class AutoSilenceManifestTest {
    @Test
    fun `自动静音接收器不导出并覆盖恢复所需的广播`() {
        val receiver = manifestElement("receiver", ".app.reminder.AutoSilenceReceiver")

        assertNotNull(receiver)
        assertEquals("false", receiver!!.androidAttribute("exported"))
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                AutoSilenceController.ACTION_BOUNDARY,
                AutoSilenceController.ACTION_RESTORE_NOW,
            ),
            receiver.intentActions(),
        )
    }

    @Test
    fun `声明了切换铃声模式与勿扰所需的权限`() {
        val permissions = declaredPermissions()

        assertTrue(permissions.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(permissions.contains("android.permission.ACCESS_NOTIFICATION_POLICY"))
    }

    private fun declaredPermissions(): Set<String> {
        val nodes = manifestDocument().getElementsByTagName("uses-permission")
        return buildSet {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                add(node.androidAttribute("name"))
            }
        }
    }

    private fun manifestElement(tag: String, name: String): Element? {
        val nodes = manifestDocument().getElementsByTagName(tag)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as? Element ?: continue
            if (node.androidAttribute("name") == name) return node
        }
        return null
    }

    private fun manifestDocument() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(manifestPath().toFile())

    private fun manifestPath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/AndroidManifest.xml"),
            userDir.resolve("app/src/main/AndroidManifest.xml"),
        ).firstOrNull(Files::isRegularFile) ?: error("Cannot find app AndroidManifest.xml")
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
