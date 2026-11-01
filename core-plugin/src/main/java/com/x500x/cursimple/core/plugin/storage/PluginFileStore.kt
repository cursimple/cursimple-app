package com.x500x.cursimple.core.plugin.storage

import android.content.Context
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.packageformat.normalizePluginPackagePath
import com.x500x.cursimple.core.plugin.packageformat.requireSafePluginId
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginRequire
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import com.x500x.cursimple.core.plugin.packageformat.requireBoundedPluginJson

class PluginFileStore(
    private val rootDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    constructor(
        context: Context,
        json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    ) : this(File(context.filesDir, "plugins-v3"), json)

    init {
        rootDir.mkdirs()
    }

    fun writeLayout(manifest: PluginManifest, layout: PluginPackageLayout, source: PluginInstallSource): File {
        val root = rootDir.canonicalFile
        root.mkdirs()
        val pluginId = requireSafePluginId(manifest.id)
        val sourceTag = source.name.lowercase()
        // Every installation gets a fresh directory. The registry is switched only after all
        // files are written; failed reinstallation cannot destroy the previously active version.
        val targetDir = File(root, "$pluginId-${manifest.versionCode}-$sourceTag-${UUID.randomUUID()}").canonicalFile
        requireContained(root, targetDir)
        check(targetDir.mkdir()) { "Cannot create plugin staging directory" }
        try {
            layout.files.forEach { (path, bytes) ->
                val normalizedPath = normalizePluginPackagePath(path)
                val target = File(targetDir, normalizedPath).canonicalFile
                requireContained(targetDir, target)
                check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                target.writeBytes(bytes)
            }
            return targetDir
        } catch (error: Exception) {
            targetDir.deleteRecursively()
            throw error
        }
    }

    fun loadManifest(record: InstalledPluginRecord): PluginManifest {
        return json.decodeFromString(readText(record, "manifest.json"))
    }

    fun loadEntryScript(record: InstalledPluginRecord): String {
        pluginRequire(record.entry.isNotBlank(), R.string.plugin_error_record_missing_entry)
        return readText(record, record.entry)
    }

    fun loadExtensionUi(record: InstalledPluginRecord, path: String): String =
        readText(record, normalizePluginPackagePath(path))

    fun loadUiSchema(record: InstalledPluginRecord, path: String = "ui/schedule.json"): PluginUiSchema {
        val file = readFile(record, path)
        if (!file.exists()) {
            return PluginUiSchema()
        }
        return json.decodeFromString(readText(record, path))
    }

    fun loadTimingProfile(record: InstalledPluginRecord, path: String = "datapack/timing.json"): TermTimingProfile? {
        val file = readFile(record, path)
        if (!file.exists()) {
            return null
        }
        return json.decodeFromString(readText(record, path))
    }

    fun readText(record: InstalledPluginRecord, relativePath: String): String {
        val file = readFile(record, relativePath)
        require(file.length() <= 50L * 1024 * 1024) { "Plugin file exceeds limit" }
        return file.readText().also { if (relativePath.endsWith(".json")) requireBoundedPluginJson(it) }
    }

    fun readFile(record: InstalledPluginRecord, relativePath: String): File {
        val root = File(record.storagePath).canonicalFile
        requireContained(rootDir.canonicalFile, root)
        pluginRequire(root != rootDir.canonicalFile, R.string.plugin_error_file_path_escape, root.path)
        val file = File(root, normalizePluginPackagePath(relativePath)).canonicalFile
        requireContained(root, file)
        return file
    }

    private fun requireContained(root: File, target: File) {
        val rootPath = root.canonicalFile.path
        val targetPath = target.canonicalFile.path
        pluginRequire(
            targetPath == rootPath || targetPath.startsWith(rootPath + File.separator),
            R.string.plugin_error_file_path_escape,
            targetPath,
        )
    }
}
