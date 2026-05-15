package com.x500x.cursimple.core.plugin.component

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginComponentInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `installer stores valid component package`() = runBlocking {
        val repository = FakeComponentRepository()
        val installer = PluginComponentInstaller(
            componentRoot = temporaryFolder.newFolder("components"),
            repository = repository,
            supportedAbis = listOf("arm64-v8a"),
        )
        val payload = "model".toByteArray()
        val result = installer.installLocalPackage(
            componentZip(
                manifest = componentManifest(
                    sha256 = sha256(payload),
                    files = listOf("models/model.onnx"),
                ),
                files = mapOf("models/model.onnx" to payload),
            ),
        )

        assertTrue(result is PluginComponentInstallResult.Success)
        val record = (result as PluginComponentInstallResult.Success).record
        assertEquals("runtime.onnx", record.id)
        assertEquals(record, repository.find("runtime.onnx"))
    }

    @Test
    fun `installer marks remote component source`() = runBlocking {
        val repository = FakeComponentRepository()
        val installer = PluginComponentInstaller(
            componentRoot = temporaryFolder.newFolder("remote-components"),
            repository = repository,
            supportedAbis = listOf("arm64-v8a"),
        )
        val payload = "model".toByteArray()
        val result = installer.installRemotePackage(
            componentZip(
                manifest = componentManifest(
                    sha256 = sha256(payload),
                    files = listOf("models/model.onnx"),
                ),
                files = mapOf("models/model.onnx" to payload),
            ),
        )

        assertTrue(result is PluginComponentInstallResult.Success)
        val record = (result as PluginComponentInstallResult.Success).record
        assertEquals(PluginComponentSource.Remote, record.source)
        assertEquals(record, repository.find("runtime.onnx"))
    }

    @Test
    fun `installer rejects incompatible abi`() = runBlocking {
        val installer = PluginComponentInstaller(
            componentRoot = temporaryFolder.newFolder("components"),
            repository = FakeComponentRepository(),
            supportedAbis = listOf("x86_64"),
        )
        val payload = "model".toByteArray()
        val result = installer.installLocalPackage(
            componentZip(
                manifest = componentManifest(
                    sha256 = sha256(payload),
                    abi = "arm64-v8a",
                    files = listOf("models/model.onnx"),
                ),
                files = mapOf("models/model.onnx" to payload),
            ),
        )

        assertTrue(result is PluginComponentInstallResult.Failure)
        assertTrue((result as PluginComponentInstallResult.Failure).reason.message.contains("ABI 不兼容"))
    }

    private fun componentManifest(
        sha256: String,
        abi: String = "arm64-v8a",
        files: List<String>,
    ): String {
        val fileList = files.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return """
            {
              "id": "runtime.onnx",
              "type": "onnx_runtime",
              "version": "1.0.0",
              "abi": "$abi",
              "sha256": "$sha256",
              "files": $fileList
            }
        """.trimIndent()
    }

    private fun componentZip(manifest: String, files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
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

    private class FakeComponentRepository : PluginComponentRepository {
        private val records = MutableStateFlow<List<InstalledPluginComponentRecord>>(emptyList())

        override val installedComponentsFlow: Flow<List<InstalledPluginComponentRecord>> = records

        override suspend fun getInstalledComponents(): List<InstalledPluginComponentRecord> = records.value

        override suspend fun find(componentId: String): InstalledPluginComponentRecord? {
            return records.value.firstOrNull { it.id == componentId }
        }

        override suspend fun save(record: InstalledPluginComponentRecord) {
            records.value = records.value.filterNot { it.id == record.id } + record
        }

        override suspend fun remove(componentId: String) {
            records.value = records.value.filterNot { it.id == componentId }
        }
    }
}
