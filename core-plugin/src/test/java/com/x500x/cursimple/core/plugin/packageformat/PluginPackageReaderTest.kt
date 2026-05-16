package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstaller
import com.x500x.cursimple.core.plugin.install.PluginRegistryRepository
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginPackageReaderTest {
    private val reader = PluginPackageReader()

    @Test
    fun `reader accepts package with manifest and entry`() {
        val layout = reader.read(
            zipBytes(
                "manifest.json" to manifestJson(entry = "main.js"),
                "main.js" to "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }",
            ),
        )

        assertTrue(layout.files.containsKey("manifest.json"))
        assertEquals("edu.demo", layout.decodeManifest(kotlinx.serialization.json.Json).id)
    }

    @Test
    fun `reader accepts package wrapped in a single root directory`() {
        val layout = reader.read(
            zipBytes(
                "yangtzeu-eams/manifest.json" to manifestJson(entry = "main.js"),
                "yangtzeu-eams/main.js" to "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }",
            ),
        )

        assertTrue(layout.files.containsKey("manifest.json"))
        assertTrue(layout.files.containsKey("main.js"))
        assertEquals("edu.demo", layout.decodeManifest(kotlinx.serialization.json.Json).id)
    }

    @Test
    fun `reader rejects zip slip path`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "main.js"),
                    "../main.js" to "bad",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("路径穿越"))
    }

    @Test
    fun `reader rejects duplicate normalized path`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "dir/main.js"),
                    "dir/main.js" to "one",
                    "dir\\main.js" to "two",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("重复文件"))
    }

    @Test
    fun `reader requires entry file`() {
        val error = runCatching {
            reader.read(
                zipBytes(
                    "manifest.json" to manifestJson(entry = "missing.js"),
                    "main.js" to "ok",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("入口文件"))
    }

    private fun manifestJson(entry: String): String {
        return """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "$entry",
              "permissions": ["schedule.write"]
            }
        """.trimIndent()
    }

    private fun zipBytes(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}

class PluginInstallerLocalPackageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `local package skips signature verification`() = runBlocking {
        val repository = FakePluginRegistryRepository()
        val installer = PluginInstaller(
            registryRepository = repository,
            fileStore = PluginFileStore(temporaryFolder.newFolder("plugins")),
        )
        val packageBytes = unsignedPluginZip()

        val preview = installer.previewPackage(packageBytes, PluginInstallSource.Local)
        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(preview.checksumVerified)
        assertFalse(preview.signatureRequired)
        assertFalse(preview.signatureVerified)
        assertTrue(result is PluginInstallResult.Success)
        val record = (result as PluginInstallResult.Success).record
        assertEquals("edu.demo", record.pluginId)
        assertEquals(record, repository.find("edu.demo"))
    }

    @Test
    fun `remote package still requires signature verification`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("remote-plugins")),
        )
        val packageBytes = unsignedPluginZip()

        val previewError = runCatching {
            installer.previewPackage(packageBytes, PluginInstallSource.Remote)
        }.exceptionOrNull()
        val result = installer.installPackage(packageBytes, PluginInstallSource.Remote)

        assertTrue(previewError?.message.orEmpty().contains("publicKeyPem"))
        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("publicKeyPem"))
    }

    private fun unsignedPluginZip(): ByteArray {
        val mainScript = "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }"
        val files = linkedMapOf(
            "manifest.json" to manifestJson(),
            "main.js" to mainScript,
        )
        val checksumEntries = files.mapValues { (_, content) -> sha256(content.toByteArray()) }
        val filesWithMetadata = files + mapOf(
            "checksums.json" to checksumsJson(checksumEntries),
            "signature.json" to unsignedSignatureJson(),
        )
        return zipBytes(filesWithMetadata)
    }

    private fun manifestJson(): String {
        return """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "main.js",
              "permissions": ["schedule.write"]
            }
        """.trimIndent()
    }

    private fun checksumsJson(files: Map<String, String>): String {
        val entries = files.entries.joinToString(",\n") { (path, checksum) ->
            """    "$path": "$checksum""""
        }
        return """
            {
              "algorithm": "SHA-256",
              "files": {
            $entries
              }
            }
        """.trimIndent()
    }

    private fun unsignedSignatureJson(): String {
        return """
            {
              "scheme": "unsigned-dev-package",
              "signed": false,
              "coveredChecksumFile": "checksums.json"
            }
        """.trimIndent()
    }

    private fun zipBytes(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private class FakePluginRegistryRepository : PluginRegistryRepository {
        private val records = MutableStateFlow<List<InstalledPluginRecord>>(emptyList())

        override val installedPluginsFlow: Flow<List<InstalledPluginRecord>> = records

        override suspend fun getInstalledPlugins(): List<InstalledPluginRecord> = records.value

        override suspend fun find(pluginId: String): InstalledPluginRecord? {
            return records.value.firstOrNull { it.pluginId == pluginId }
        }

        override suspend fun saveInstalledPlugin(record: InstalledPluginRecord) {
            records.value = records.value.filterNot { it.pluginId == record.pluginId } + record
        }

        override suspend fun removeInstalledPlugin(pluginId: String) {
            records.value = records.value.filterNot { it.pluginId == pluginId }
        }
    }
}
