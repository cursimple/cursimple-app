package com.x500x.cursimple.core.plugin.storage

import android.content.Context
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import kotlinx.serialization.json.Json
import java.io.File

class PluginFileStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val rootDir: File = File(context.filesDir, "plugins-v3").apply { mkdirs() }

    fun writeLayout(manifest: PluginManifest, layout: PluginPackageLayout): File {
        val targetDir = File(rootDir, "${manifest.id}-${manifest.versionCode}").apply {
            if (exists()) {
                deleteRecursively()
            }
            mkdirs()
        }
        layout.files.forEach { (path, bytes) ->
            val target = File(targetDir, path)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        return targetDir
    }

    fun loadManifest(record: InstalledPluginRecord): PluginManifest {
        return json.decodeFromString(readText(record, "manifest.json"))
    }

    fun loadEntryScript(record: InstalledPluginRecord): String {
        require(record.entry.isNotBlank()) { "插件记录缺少入口文件" }
        return readText(record, record.entry)
    }

    fun loadUiSchema(record: InstalledPluginRecord, path: String = "ui/schedule.json"): PluginUiSchema {
        val file = File(record.storagePath, path)
        if (!file.exists()) {
            return PluginUiSchema()
        }
        return json.decodeFromString(file.readText())
    }

    fun loadTimingProfile(record: InstalledPluginRecord, path: String = "datapack/timing.json"): TermTimingProfile? {
        val file = File(record.storagePath, path)
        if (!file.exists()) {
            return null
        }
        return json.decodeFromString(file.readText())
    }

    fun readText(record: InstalledPluginRecord, relativePath: String): String {
        return File(record.storagePath, relativePath).readText()
    }

}
