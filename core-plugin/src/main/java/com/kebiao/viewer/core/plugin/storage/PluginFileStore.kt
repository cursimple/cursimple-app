package com.kebiao.viewer.core.plugin.storage

import android.content.Context
import android.content.res.AssetManager
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.manifest.PluginManifest
import com.kebiao.viewer.core.plugin.packageformat.PluginPackageLayout
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.workflow.WorkflowDefinition
import kotlinx.serialization.json.Json
import java.io.File

class PluginFileStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val rootDir: File = File(context.filesDir, "plugins-v2").apply { mkdirs() }

    fun writeLayout(manifest: PluginManifest, layout: PluginPackageLayout): File {
        val targetDir = File(rootDir, "${manifest.pluginId}-${manifest.versionCode}").apply {
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

    fun copyBundledAssetDirectory(assetRoot: String): File {
        val manifest = loadAssetText("$assetRoot/manifest.json")
        val pluginManifest = json.decodeFromString<PluginManifest>(manifest)
        val targetDir = File(rootDir, "${pluginManifest.pluginId}-${pluginManifest.versionCode}").apply {
            if (exists()) {
                deleteRecursively()
            }
            mkdirs()
        }
        copyAssetDirectory(context.assets, assetRoot, targetDir)
        return targetDir
    }

    fun loadManifest(record: InstalledPluginRecord): PluginManifest {
        return json.decodeFromString(readText(record, "manifest.json"))
    }

    fun loadWorkflow(record: InstalledPluginRecord): WorkflowDefinition {
        return json.decodeFromString(readText(record, "workflow.json"))
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

    fun loadAssetText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun copyAssetDirectory(assetManager: AssetManager, assetPath: String, targetDir: File) {
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            assetManager.open(assetPath).use { input ->
                targetDir.parentFile?.mkdirs()
                targetDir.writeBytes(input.readBytes())
            }
            return
        }
        children.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val target = File(targetDir, child)
            val grandChildren = assetManager.list(childAssetPath).orEmpty()
            if (grandChildren.isEmpty()) {
                target.parentFile?.mkdirs()
                assetManager.open(childAssetPath).use { input ->
                    target.writeBytes(input.readBytes())
                }
            } else {
                target.mkdirs()
                copyAssetDirectory(assetManager, childAssetPath, target)
            }
        }
    }
}
