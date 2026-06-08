package com.x500x.cursimple.core.plugin.packageformat

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstaller
import com.x500x.cursimple.core.plugin.install.PluginRegistryRepository
import com.x500x.cursimple.core.plugin.install.isPluginInstallEnabled
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
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

class PluginInstallerPackageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `local package installs with checksum verification`() = runBlocking {
        val repository = FakePluginRegistryRepository()
        val installer = PluginInstaller(
            registryRepository = repository,
            fileStore = PluginFileStore(temporaryFolder.newFolder("plugins")),
        )
        val packageBytes = pluginZip()

        val preview = installer.previewPackage(packageBytes, PluginInstallSource.Local)
        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(preview.checksumVerified)
        assertTrue(result is PluginInstallResult.Success)
        val record = (result as PluginInstallResult.Success).record
        assertEquals("edu.demo", record.pluginId)
        assertEquals(PluginInstallSource.Local, record.source)
        assertEquals(record, repository.find("edu.demo"))
    }

    @Test
    fun `remote package installs with checksum verification`() = runBlocking {
        val repository = FakePluginRegistryRepository()
        val installer = PluginInstaller(
            registryRepository = repository,
            fileStore = PluginFileStore(temporaryFolder.newFolder("remote-plugins")),
        )
        val packageBytes = pluginZip()

        val preview = installer.previewPackage(packageBytes, PluginInstallSource.Remote)
        val result = installer.installPackage(packageBytes, PluginInstallSource.Remote)

        assertTrue(preview.checksumVerified)
        assertTrue(result is PluginInstallResult.Success)
        val record = (result as PluginInstallResult.Success).record
        assertEquals("edu.demo", record.pluginId)
        assertEquals(PluginInstallSource.Remote, record.source)
        assertEquals(record, repository.find("edu.demo"))
    }

    @Test
    fun `remote package rejects checksum mismatch`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("bad-checksum-plugins")),
        )
        val packageBytes = pluginZip(corruptChecksum = true)

        val preview = installer.previewPackage(packageBytes, PluginInstallSource.Remote)
        val result = installer.installPackage(packageBytes, PluginInstallSource.Remote)

        assertFalse(preview.checksumVerified)
        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("插件摘要校验失败"))
    }

    @Test
    fun `package rejects empty checksum manifest`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("empty-checksum-plugins")),
        )
        val packageBytes = pluginZip { it.clear() }

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("摘要不能为空"))
    }

    @Test
    fun `package rejects partial checksum coverage`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("partial-checksum-plugins")),
        )
        val packageBytes = pluginZip { it.remove("main.js") }

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("覆盖不完整"))
        assertTrue(result.message.contains("main.js"))
    }

    @Test
    fun `package rejects checksum path not present in zip`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("unknown-checksum-plugins")),
        )
        val packageBytes = pluginZip { it["missing.js"] = "0".repeat(64) }

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("不存在"))
    }

    @Test
    fun `package rejects invalid checksum digest format`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("invalid-digest-plugins")),
        )
        val packageBytes = pluginZip { it["main.js"] = "abc" }

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("格式无效"))
    }

    @Test
    fun `package rejects unsupported checksum algorithm`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("unsupported-algorithm-plugins")),
        )
        val packageBytes = pluginZip(checksumAlgorithm = "MD5")

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("只支持 SHA-256"))
    }

    @Test
    fun `package rejects path traversal plugin id`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("traversal-id-plugins")),
        )
        val packageBytes = pluginZip(id = "../evil")

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("插件 ID"))
    }

    @Test
    fun `package rejects slash containing plugin id`() = runBlocking {
        val installer = PluginInstaller(
            registryRepository = FakePluginRegistryRepository(),
            fileStore = PluginFileStore(temporaryFolder.newFolder("slash-id-plugins")),
        )
        val packageBytes = pluginZip(id = "edu/demo")

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Failure)
        assertTrue((result as PluginInstallResult.Failure).message.contains("插件 ID"))
    }

    @Test
    fun `installer stores normalized windows entry path`() = runBlocking {
        val repository = FakePluginRegistryRepository()
        val fileStore = PluginFileStore(temporaryFolder.newFolder("windows-entry-plugins"))
        val installer = PluginInstaller(
            registryRepository = repository,
            fileStore = fileStore,
        )
        val packageBytes = pluginZip(entry = "scripts\\main.js", entryFilePath = "scripts/main.js")

        val result = installer.installPackage(packageBytes, PluginInstallSource.Local)

        assertTrue(result is PluginInstallResult.Success)
        val record = (result as PluginInstallResult.Success).record
        assertEquals("scripts/main.js", record.entry)
        assertTrue(fileStore.loadEntryScript(record).contains("ctx.schedule.commit"))
    }

    @Test
    fun `file store rejects package paths outside target directory`() {
        val fileStore = PluginFileStore(temporaryFolder.newFolder("path-safe-plugins"))
        val manifest = PluginManifest(
            id = "edu.demo",
            name = "Demo",
            version = "1.0.0",
            versionCode = 1,
            entry = "main.js",
        )

        val error = runCatching {
            fileStore.writeLayout(
                manifest = manifest,
                layout = PluginPackageLayout(mapOf("../escape.txt" to "bad".toByteArray())),
                source = PluginInstallSource.Local,
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("路径穿越"))
    }

    @Test
    fun `local and remote packages with same id remain separate installs`() = runBlocking {
        val repository = FakePluginRegistryRepository()
        val installer = PluginInstaller(
            registryRepository = repository,
            fileStore = PluginFileStore(temporaryFolder.newFolder("separate-source-plugins")),
        )
        val packageBytes = pluginZip()

        val localResult = installer.installPackage(packageBytes, PluginInstallSource.Local)
        val remoteResult = installer.installPackage(packageBytes, PluginInstallSource.Remote)

        assertTrue(localResult is PluginInstallResult.Success)
        assertTrue(remoteResult is PluginInstallResult.Success)
        val localRecord = (localResult as PluginInstallResult.Success).record
        val remoteRecord = (remoteResult as PluginInstallResult.Success).record
        val installed = repository.getInstalledPlugins()
        assertEquals(2, installed.size)
        assertEquals(setOf(PluginInstallSource.Local, PluginInstallSource.Remote), installed.map { it.source }.toSet())

        repository.removeInstalledPluginByKey(localRecord.installKey)

        assertEquals(listOf(remoteRecord), repository.getInstalledPlugins())
    }

    @Test
    fun `source aware enabled keys do not enable sibling installs`() = runBlocking {
        val local = installedRecord(PluginInstallSource.Local)
        val remote = installedRecord(PluginInstallSource.Remote)
        val installed = listOf(local, remote)

        assertTrue(isPluginInstallEnabled(local, setOf(local.installKey), installed))
        assertFalse(isPluginInstallEnabled(remote, setOf(local.installKey), installed))
        assertTrue(isPluginInstallEnabled(local, setOf("edu.demo"), installed))
        assertFalse(isPluginInstallEnabled(remote, setOf("edu.demo"), installed))
    }

    private fun pluginZip(
        id: String = "edu.demo",
        entry: String = "main.js",
        entryFilePath: String = entry.replace('\\', '/'),
        corruptChecksum: Boolean = false,
        checksumAlgorithm: String = "SHA-256",
        mutateChecksums: (MutableMap<String, String>) -> Unit = {},
    ): ByteArray {
        val mainScript = "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }"
        val files = linkedMapOf(
            "manifest.json" to manifestJson(id = id, entry = entry),
            entryFilePath to mainScript,
        )
        val checksumEntries = files.mapValues { (_, content) -> sha256(content.toByteArray()) }.toMutableMap()
        if (corruptChecksum) {
            checksumEntries[entryFilePath] = "0".repeat(64)
        }
        mutateChecksums(checksumEntries)
        val filesWithMetadata = files + mapOf(
            "checksums.json" to checksumsJson(checksumEntries, checksumAlgorithm),
        )
        return zipBytes(filesWithMetadata)
    }

    private fun manifestJson(id: String = "edu.demo", entry: String = "main.js"): String {
        return """
            {
              "id": "${id.escapeJson()}",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "${entry.escapeJson()}",
              "permissions": ["schedule.write"]
            }
        """.trimIndent()
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun checksumsJson(files: Map<String, String>, algorithm: String = "SHA-256"): String {
        val entries = files.entries.joinToString(",\n") { (path, checksum) ->
            """    "$path": "$checksum""""
        }
        return """
            {
              "algorithm": "$algorithm",
              "files": {
            $entries
              }
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

    private fun installedRecord(source: PluginInstallSource): InstalledPluginRecord {
        return InstalledPluginRecord(
            pluginId = "edu.demo",
            name = "Demo",
            version = "1.0.0",
            versionCode = 1,
            storagePath = "/tmp/${source.name.lowercase()}",
            installedAt = "2026-05-31T00:00:00Z",
            source = source,
        )
    }

    private class FakePluginRegistryRepository : PluginRegistryRepository {
        private val records = MutableStateFlow<List<InstalledPluginRecord>>(emptyList())

        override val installedPluginsFlow: Flow<List<InstalledPluginRecord>> = records

        override suspend fun getInstalledPlugins(): List<InstalledPluginRecord> = records.value

        override suspend fun find(pluginId: String): InstalledPluginRecord? {
            return records.value.firstOrNull { it.pluginId == pluginId }
        }

        override suspend fun findByInstallKey(installKey: String): InstalledPluginRecord? {
            return records.value.firstOrNull { it.installKey == installKey }
        }

        override suspend fun saveInstalledPlugin(record: InstalledPluginRecord) {
            records.value = records.value.filterNot { it.pluginId == record.pluginId && it.source == record.source } + record
        }

        override suspend fun removeInstalledPlugin(pluginId: String) {
            records.value = records.value.filterNot { it.pluginId == pluginId }
        }

        override suspend fun removeInstalledPluginByKey(installKey: String) {
            records.value = records.value.filterNot { it.installKey == installKey }
        }
    }
}
