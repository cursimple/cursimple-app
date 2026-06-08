package com.x500x.cursimple.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesManifestTest {
    @Test
    fun `manifest references backup exclusion rule resources`() {
        val application = manifestApplication()

        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
    }

    @Test
    fun `android 12 data extraction rules exclude credential datastore from backup and transfer`() {
        val rules = parseXml("data_extraction_rules.xml")

        assertTrue(
            rules.excludesCredentialStore(parentTag = "cloud-backup"),
        )
        assertTrue(
            rules.excludesCredentialStore(parentTag = "device-transfer"),
        )
    }

    @Test
    fun `legacy backup rules exclude credential datastore`() {
        val rules = parseXml("backup_rules.xml")

        assertTrue(rules.excludesCredentialStore(parentTag = "full-backup-content"))
    }

    private fun manifestApplication(): Element {
        val manifest = manifestCandidates().firstOrNull(Files::isRegularFile)
            ?: error("Cannot find app AndroidManifest.xml")
        return parseXml(manifest)
            .getElementsByTagName("application")
            .item(0) as Element
    }

    private fun parseXml(name: String): Element {
        val xml = resourceCandidates(name).firstOrNull(Files::isRegularFile)
            ?: error("Cannot find $name")
        return parseXml(xml)
    }

    private fun parseXml(path: Path): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder()
            .parse(path.toFile())
            .documentElement
    }

    private fun Element.excludesCredentialStore(parentTag: String): Boolean {
        val parents = buildList {
            if (tagName == parentTag) add(this@excludesCredentialStore)
            val descendants = getElementsByTagName(parentTag)
            for (index in 0 until descendants.length) {
                (descendants.item(index) as? Element)?.let(::add)
            }
        }
        for (parent in parents) {
            val excludes = parent.getElementsByTagName("exclude")
            for (excludeIndex in 0 until excludes.length) {
                val exclude = excludes.item(excludeIndex) as? Element ?: continue
                if (
                    exclude.getAttribute("domain") == "file" &&
                    exclude.getAttribute("path") == CREDENTIAL_DATASTORE_PATH
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun manifestCandidates(): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/AndroidManifest.xml"),
            userDir.resolve("app/src/main/AndroidManifest.xml"),
        )
    }

    private fun resourceCandidates(name: String): Sequence<Path> {
        val userDir = Paths.get(System.getProperty("user.dir"))
        return sequenceOf(
            userDir.resolve("src/main/res/xml/$name"),
            userDir.resolve("app/src/main/res/xml/$name"),
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val CREDENTIAL_DATASTORE_PATH = "datastore/user_preferences.preferences_pb"
    }
}
