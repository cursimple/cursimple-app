package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.util.Locale

class PluginStorageUsageTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun File.fill(path: String, bytes: Int) = File(this, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    private fun record(id: String, storage: File, extension: Boolean) = InstalledPluginRecord(
        pluginId = id, name = id, version = "1.0.0", versionCode = 1, storagePath = storage.path,
        installedAt = "2026-10-05T08:00:00+08:00", source = PluginInstallSource.Remote,
        kind = if (extension) PluginManifest.KIND_EXTENSION else PluginManifest.KIND_SCHEDULE,
    )

    @Test
    fun `directory size adds nested files and a missing path is zero`() {
        val root = folder.newFolder()
        root.fill("a.bin", 10)
        root.fill("sub/b.bin", 20)
        root.fill("sub/deeper/c.bin", 30)
        assertEquals(60L, directorySizeBytes(root))
        assertEquals(10L, directorySizeBytes(File(root, "a.bin")))
        assertEquals(0L, directorySizeBytes(File(root, "missing")))
    }

    @Test
    fun `a component counts its package plus saved data and downloads`() = runBlocking {
        val files = folder.newFolder()
        val storage = folder.newFolder().also { it.fill("main.js", 100); it.fill("ui/app.js", 50) }
        files.fill("extensions-v1/notify.json", 7)
        files.fill("extension-downloads/notify/a.pdf", 1000)
        files.fill("extension-downloads/other/x.pdf", 5000)

        val usage = measurePluginStorage(files, record("notify", storage, extension = true))

        assertEquals(150L, usage.packageBytes)
        assertEquals(1007L, usage.dataBytes)
        assertEquals(1157L, usage.totalBytes)
    }

    @Test
    fun `a school plugin has no component data and unsafe ids never leave the app directory`() = runBlocking {
        val files = folder.newFolder()
        val storage = folder.newFolder().also { it.fill("main.js", 40) }
        files.fill("extensions-v1/school.json", 99)

        assertEquals(PluginStorageUsage(40L, 0L), measurePluginStorage(files, record("school", storage, extension = false)))
        files.fill("secret.json", 500)
        assertEquals(0L, measurePluginStorage(files, record("../secret", storage, extension = true)).dataBytes)
    }

    @Test
    fun `install time is shown in the local zone and unparsable values pass through`() {
        val utc = formatInstalledAt("2026-10-05T00:00:00Z", ZoneId.of("Asia/Shanghai"), Locale.SIMPLIFIED_CHINESE)
        // Assert converted clock fields without depending on locale-specific formatting.
        assertEquals(true, utc.contains("2026") && utc.contains("8:00"))
        assertEquals("not a time", formatInstalledAt("not a time"))
    }
}
