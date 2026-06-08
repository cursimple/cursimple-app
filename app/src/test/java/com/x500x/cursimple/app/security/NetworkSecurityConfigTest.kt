package com.x500x.cursimple.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class NetworkSecurityConfigTest {
    @Test
    fun `manifest references network security config`() {
        val application = manifestApplication()

        assertEquals("@xml/network_security_config", application.androidAttribute("networkSecurityConfig"))
    }

    @Test
    fun `network security config does not globally allow cleartext`() {
        val baseConfig = networkSecurityBaseConfig()

        assertNotNull(baseConfig)
        assertEquals("false", baseConfig!!.getAttribute("cleartextTrafficPermitted"))
    }

    private fun manifestApplication(): Element {
        val manifest = manifestCandidates().firstOrNull(Files::isRegularFile)
            ?: error("Cannot find app AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder()
            .parse(manifest.toFile())
            .getElementsByTagName("application")
            .item(0) as Element
    }

    private fun networkSecurityBaseConfig(): Element? {
        val config = networkSecurityConfigCandidates().firstOrNull(Files::isRegularFile)
            ?: error("Cannot find network_security_config.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val baseConfigs = factory.newDocumentBuilder()
            .parse(config.toFile())
            .getElementsByTagName("base-config")
        return baseConfigs.item(0) as? Element
    }

    private fun manifestCandidates(): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/AndroidManifest.xml"),
            userDir.resolve("app/src/main/AndroidManifest.xml"),
        )
    }

    private fun networkSecurityConfigCandidates(): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/res/xml/network_security_config.xml"),
            userDir.resolve("app/src/main/res/xml/network_security_config.xml"),
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
