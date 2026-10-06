package com.x500x.cursimple.core.plugin.install

import com.x500x.cursimple.core.plugin.PluginApiVersion
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.assertPluginError
import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSpec
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginInstallCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `unsupported API blocks every install source and both bundle kinds before writes`() = runBlocking {
        for (source in PluginInstallSource.entries) for (extension in listOf(false, true)) {
            val root = temporary.newFolder()
            val registry = Registry()
            val installer = PluginInstaller(registry, PluginFileStore(root), hostVersion = "0.7.5")
            val bytes = bundle(api = PluginApiVersion.CURRENT + 1, extension = extension)
            val preview = installer.previewPackage(bytes, source)
            assertTrue(preview.checksumVerified)
            assertFalse(preview.installable)
            val result = installer.installPackage(bytes, source)
            assertTrue(result is PluginInstallResult.Failure)
            assertPluginError(R.string.plugin_error_compatibility_api_too_new,
                (result as PluginInstallResult.Failure).error, PluginApiVersion.CURRENT + 1, PluginApiVersion.CURRENT)
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertTrue(registry.installedPluginsFlow.value.isEmpty())
            assertEquals(0, registry.saves)
        }
    }

    @Test fun `undeclared and non positive APIs cannot enter storage`() = runBlocking {
        for (api in listOf(null, 0, -1)) {
            val root = temporary.newFolder(); val registry = Registry()
            val installer = PluginInstaller(registry, PluginFileStore(root), hostVersion = "0.7.5")
            val result = installer.installPackage(bundle(api), PluginInstallSource.Local)
            assertTrue(result is PluginInstallResult.Failure)
            assertPluginError(if (api == null) R.string.plugin_error_compatibility_api_undeclared else R.string.plugin_error_compatibility_api_invalid,
                (result as PluginInstallResult.Failure).error)
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertEquals(0, registry.saves)
        }
    }

    @Test fun `a compatible API cannot bypass the minimum APK version`() = runBlocking {
        val root = temporary.newFolder(); val registry = Registry()
        val installer = PluginInstaller(registry, PluginFileStore(root), hostVersion = "0.7.5-ci")
        val bytes = bundle(2, minimum = "0.7.6")
        assertFalse(installer.previewPackage(bytes, PluginInstallSource.Remote).installable)
        val result = installer.installPackage(bytes, PluginInstallSource.Remote) as PluginInstallResult.Failure
        assertPluginError(R.string.plugin_error_compatibility_host_too_old, result.error, "0.7.6", "0.7.5-ci")
        assertEquals(0, registry.saves)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `rejected upgrades preserve registry and files even when their target path collides`() = runBlocking {
        val root = temporary.newFolder(); val registry = Registry(); val files = PluginFileStore(root)
        val installer = PluginInstaller(registry, files, hostVersion = "0.7.5-ci")
        val old = (installer.installPackage(bundle(2, minimum = "0.7.5"), PluginInstallSource.Local) as PluginInstallResult.Success).record
        for (code in listOf(1L, 2L)) {
            val rejected = installer.installPackage(bundle(PluginApiVersion.CURRENT + 1, code = code, script = "bad"), PluginInstallSource.Local)
            assertTrue(rejected is PluginInstallResult.Failure)
            assertEquals(old, registry.findByInstallKey(old.installKey))
            assertEquals("original", files.loadEntryScript(old))
            assertEquals(1, root.listFiles().orEmpty().size)
            assertEquals(1, registry.saves)
        }
    }

    private fun bundle(api: Int?, minimum: String = "0.1.0", extension: Boolean = false,
        code: Long = 1L, script: String = "original"): ByteArray {
        val manifest = PluginManifest("example.guard", "Example", version = "1.0.$code", versionCode = code,
            apiVersion = api, entry = "main.js", minHostVersion = minimum,
            kind = if (extension) PluginManifest.KIND_EXTENSION else PluginManifest.KIND_SCHEDULE,
            allowedHosts = if (extension) listOf("example.invalid") else emptyList(),
            extension = if (extension) PluginExtensionSpec(loginUrl = "https://example.invalid/", runUrl = "https://example.invalid/") else null)
        val files = linkedMapOf("manifest.json" to Json.encodeToString(manifest), "main.js" to script)
        val sums = files.mapValues { (_, content) -> MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) } }
        files["checksums.json"] = """{"algorithm":"SHA-256","files":${Json.encodeToString(sums)}}"""
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return output.toByteArray()
    }

    private class Registry : PluginRegistryRepository {
        override val installedPluginsFlow = MutableStateFlow<List<InstalledPluginRecord>>(emptyList())
        var saves = 0
        override suspend fun getInstalledPlugins() = installedPluginsFlow.value
        override suspend fun find(pluginId: String) = installedPluginsFlow.value.firstOrNull { it.pluginId == pluginId }
        override suspend fun findByInstallKey(installKey: String) = installedPluginsFlow.value.firstOrNull { it.installKey == installKey }
        override suspend fun saveInstalledPlugin(record: InstalledPluginRecord) { saves++; installedPluginsFlow.value = installedPluginsFlow.value.filterNot { it.installKey == record.installKey } + record }
        override suspend fun removeInstalledPlugin(pluginId: String) { installedPluginsFlow.value = installedPluginsFlow.value.filterNot { it.pluginId == pluginId } }
        override suspend fun removeInstalledPluginByKey(installKey: String) { installedPluginsFlow.value = installedPluginsFlow.value.filterNot { it.installKey == installKey } }
    }
}
