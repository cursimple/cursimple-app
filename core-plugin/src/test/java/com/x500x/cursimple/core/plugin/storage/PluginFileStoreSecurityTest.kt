package com.x500x.cursimple.core.plugin.storage

import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginFileStoreSecurityTest {
    @get:Rule val folder = TemporaryFolder()
    private val manifest = PluginManifest("test.plugin", "test", version = "1", versionCode = 1, entry = "main.js")
    private fun record(root: File) = InstalledPluginRecord(
        pluginId = manifest.id, name = "test", version = "1", versionCode = 1,
        storagePath = root.path, installedAt = "test", source = PluginInstallSource.Local,
    )

    @Test fun failedReinstallPreservesExistingFilesAndCleansStaging() {
        val root = folder.newFolder("plugins")
        val store = PluginFileStore(root)
        val previous = store.writeLayout(manifest, PluginPackageLayout(mapOf("main.js" to "old".toByteArray())), PluginInstallSource.Local)
        val broken = PluginPackageLayout(linkedMapOf("main.js" to "new".toByteArray(), "../escape" to byteArrayOf()))
        assertTrue(runCatching { store.writeLayout(manifest, broken, PluginInstallSource.Local) }.isFailure)
        assertEquals("old", File(previous, "main.js").readText())
        assertEquals(listOf(previous), root.listFiles()!!.map { it.canonicalFile })
        val fresh = store.writeLayout(manifest, PluginPackageLayout(mapOf("main.js" to "new".toByteArray())), PluginInstallSource.Local)
        assertNotEquals(previous, fresh)
        assertEquals("old", File(previous, "main.js").readText())
    }

    @Test fun schemasCannotEscapeViaPathRecordOrSymlink() {
        val root = folder.newFolder("plugins")
        val plugin = File(root, "plugin").apply { mkdirs() }
        val outside = folder.newFolder("outside")
        File(outside, "secret.json").writeText("{}")
        val store = PluginFileStore(root)
        assertTrue(runCatching { store.loadUiSchema(record(plugin), "../../outside/secret.json") }.isFailure)
        assertTrue(runCatching { store.loadTimingProfile(record(outside), "secret.json") }.isFailure)
        assertTrue(runCatching { store.readFile(record(root), "plugin") }.isFailure)
        java.nio.file.Files.createSymbolicLink(File(plugin, "link").toPath(), outside.toPath())
        assertTrue(runCatching { store.loadUiSchema(record(plugin), "link/secret.json") }.isFailure)
    }
}
