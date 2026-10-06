package com.x500x.cursimple.app

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.PluginApiVersion
import com.x500x.cursimple.core.plugin.PluginArgumentException
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.pluginHostVersion
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSpec
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.component.PluginComponentInstallResult
import com.x500x.cursimple.core.plugin.component.PluginComponentPackageManifest
import com.x500x.cursimple.core.plugin.component.PluginComponentType
import com.x500x.cursimple.core.plugin.pluginErrorText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.x500x.cursimple.core.plugin.R as PluginR

/** Dedicated emulator only; exercises the actual APK installer without UI confirmation. */
class InstallCompatibilityQaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ClassScheduleApplication).appContainer

    @Before fun setUp() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installGuardQa") == "true")
        container.bootstrapJob.join()
    }

    @Test fun futureApisAreBlockedForPluginsAndComponentsAcrossInstallSources() = runBlocking {
        val manager = container.pluginManager
        for (extension in listOf(false, true)) for (source in PluginInstallSource.entries) {
            val id = "qa.guard.${UUID.randomUUID()}"
            val manifest = manifest(id, PluginApiVersion.CURRENT + 1, extension = extension)
            val bytes = pluginZip(manifest)
            val preview = manager.previewPackage(bytes, source)
            assertTrue(preview.checksumVerified)
            assertFalse(preview.installable)
            val result = manager.installPackage(bytes, source)
            assertTrue(result.toString(), result is PluginInstallResult.Failure)
            val error = (result as PluginInstallResult.Failure).error as PluginArgumentException
            assertEquals(PluginR.string.plugin_error_compatibility_api_too_new, error.messageRes)
            assertEquals(context.getString(error.messageRes, PluginApiVersion.CURRENT + 1, PluginApiVersion.CURRENT), context.pluginErrorText(error))
            assertTrue(manager.getInstalledPlugins().none { it.pluginId == id })
            assertTrue(File(context.filesDir, "plugins-v3").listFiles().orEmpty().none { it.name.startsWith("$id-") })
        }
    }

    @Test fun minimumVersionUsesApkMetadataAndRejectedUpgradePreservesTheOldInstall() = runBlocking {
        val manager = container.pluginManager
        val id = "qa.guard.${UUID.randomUUID()}"
        val currentVersion = context.pluginHostVersion()
        assertTrue(currentVersion.isNotBlank())
        val valid = manifest(id, 2).copy(minHostVersion = currentVersion.removeSuffix("-ci"))
        val installed = manager.installPackage(pluginZip(valid), PluginInstallSource.Local)
        assertTrue(installed.toString(), installed is PluginInstallResult.Success)
        val record = (installed as PluginInstallResult.Success).record
        try {
            val next = valid.copy(version = "2.0.0", versionCode = 2, minHostVersion = "999.0.0")
            val bytes = pluginZip(next, "replacement")
            assertFalse(manager.previewPackage(bytes, PluginInstallSource.Local).installable)
            val result = manager.installPackage(bytes, PluginInstallSource.Local) as PluginInstallResult.Failure
            val error = result.error as PluginArgumentException
            assertEquals(PluginR.string.plugin_error_compatibility_host_too_old, error.messageRes)
            assertEquals(listOf("999.0.0", currentVersion), error.formatArgs)
            assertEquals(record, manager.getInstalledPlugins().first { it.installKey == record.installKey })
            assertEquals("original", File(record.storagePath, "main.js").readText())
            assertEquals(1, File(context.filesDir, "plugins-v3").listFiles().orEmpty().count { it.name.startsWith("$id-") })
        } finally { manager.removePlugin(record.installKey) }
    }

    @Test fun runtimeAssetImportsAlsoEnforceTheApkVersion() = runBlocking {
        val id = "qa.guard.${UUID.randomUUID()}"
        val payload = "model"
        val manifest = PluginComponentPackageManifest(id, PluginComponentType.GenericAsset, "1.0.0",
            sha256 = sha(payload), files = listOf("model.bin"), minHostVersion = "999.0.0")
        val bytes = zip(linkedMapOf("manifest.json" to Json.encodeToString(manifest), "model.bin" to payload))
        val installer = container.pluginComponentInstaller
        for (result in listOf(installer.installLocalPackage(bytes), installer.installRemotePackage(bytes))) {
            assertTrue(result.toString(), result is PluginComponentInstallResult.Failure)
            val error = (result as PluginComponentInstallResult.Failure).reason.error as PluginArgumentException
            assertEquals(PluginR.string.plugin_error_compatibility_host_too_old, error.messageRes)
        }
        assertTrue(container.pluginComponentRepository.getInstalledComponents().none { it.id == id })
        assertTrue(File(context.filesDir, "plugin-components-v1").listFiles().orEmpty().none { it.name.startsWith("$id-") })
    }

    @Test fun publishedCompatibleComponentPassesTheNativePreview() = runBlocking {
        val file = File(context.filesDir, "qa-compatible-component.zip")
        assumeTrue("Push the published component ZIP", file.isFile)
        val preview = container.pluginManager.previewPackage(file.readBytes(), PluginInstallSource.Remote)
        assertEquals("1.3.1", preview.manifest.version)
        assertTrue(preview.installable)
    }

    private fun manifest(id: String, api: Int, extension: Boolean = false) = PluginManifest(
        id, "QA", version = "1.0.0", versionCode = 1, apiVersion = api, entry = "main.js",
        kind = if (extension) PluginManifest.KIND_EXTENSION else PluginManifest.KIND_SCHEDULE,
        allowedHosts = if (extension) listOf("example.invalid") else emptyList(),
        extension = if (extension) PluginExtensionSpec(loginUrl = "https://example.invalid/", runUrl = "https://example.invalid/") else null)

    private fun pluginZip(manifest: PluginManifest, script: String = "original"): ByteArray {
        val files = linkedMapOf("manifest.json" to Json.encodeToString(manifest), "main.js" to script)
        files["checksums.json"] = """{"algorithm":"SHA-256","files":${Json.encodeToString(files.mapValues { sha(it.value) })}}"""
        return zip(files)
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun zip(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return output.toByteArray()
    }
}
