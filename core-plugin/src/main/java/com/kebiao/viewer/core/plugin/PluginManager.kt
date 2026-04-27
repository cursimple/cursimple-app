package com.kebiao.viewer.core.plugin

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginInstallPreview
import com.kebiao.viewer.core.plugin.install.PluginInstallResult
import com.kebiao.viewer.core.plugin.install.PluginInstallSource
import com.kebiao.viewer.core.plugin.install.PluginInstaller
import com.kebiao.viewer.core.plugin.install.PluginRegistryRepository
import com.kebiao.viewer.core.plugin.market.MarketIndexPayload
import com.kebiao.viewer.core.plugin.market.MarketIndexRepository
import com.kebiao.viewer.core.plugin.runtime.DefaultWorkflowEngine
import com.kebiao.viewer.core.plugin.runtime.InstalledPluginBundle
import com.kebiao.viewer.core.plugin.runtime.PluginSyncInput
import com.kebiao.viewer.core.plugin.runtime.WorkflowEngine
import com.kebiao.viewer.core.plugin.runtime.WorkflowExecutionResult
import com.kebiao.viewer.core.plugin.storage.PluginFileStore
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.File

class PluginManager(
    context: Context,
    private val registryRepository: PluginRegistryRepository,
    private val marketIndexRepository: MarketIndexRepository = MarketIndexRepository(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val fileStore = PluginFileStore(context, json)
    private val installer = PluginInstaller(
        registryRepository = registryRepository,
        fileStore = fileStore,
        json = json,
    )
    private val engine: WorkflowEngine = DefaultWorkflowEngine(json = json)

    val installedPluginsFlow: Flow<List<InstalledPluginRecord>> = registryRepository.installedPluginsFlow

    suspend fun getInstalledPlugins(): List<InstalledPluginRecord> = registryRepository.getInstalledPlugins()

    suspend fun previewPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallPreview {
        return installer.previewPackage(bytes, source)
    }

    suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallResult {
        return installer.installPackage(bytes, source)
    }

    suspend fun ensureBundledPlugin(assetRoot: String) {
        val manifest = json.decodeFromString<com.kebiao.viewer.core.plugin.manifest.PluginManifest>(
            fileStore.loadAssetText("$assetRoot/manifest.json"),
        )
        if (registryRepository.find(manifest.pluginId) == null) {
            installer.installBundledAssetDirectory(assetRoot)
        }
    }

    suspend fun removePlugin(pluginId: String) {
        registryRepository.find(pluginId)?.let { record ->
            File(record.storagePath).deleteRecursively()
        }
        registryRepository.removeInstalledPlugin(pluginId)
    }

    suspend fun fetchMarketIndex(url: String): MarketIndexPayload {
        return marketIndexRepository.fetch(url)
    }

    suspend fun downloadRemotePackage(url: String): ByteArray {
        return marketIndexRepository.downloadPackage(url)
    }

    suspend fun loadUiSchema(pluginId: String): PluginUiSchema {
        val record = requirePlugin(pluginId)
        return fileStore.loadUiSchema(record)
    }

    suspend fun loadTimingProfile(pluginId: String): TermTimingProfile? {
        val record = requirePlugin(pluginId)
        return fileStore.loadTimingProfile(record)
    }

    suspend fun startSync(request: PluginSyncInput): WorkflowExecutionResult {
        val bundle = loadBundle(request.pluginId)
        return engine.start(bundle, request) { path ->
            fileStore.readText(bundle.record, path)
        }
    }

    suspend fun resumeSync(pluginId: String, token: String, packet: WebSessionPacket): WorkflowExecutionResult {
        requirePlugin(pluginId)
        return engine.resume(token, packet) { bundle, path ->
            fileStore.readText(bundle.record, path)
        }
    }

    private suspend fun loadBundle(pluginId: String): InstalledPluginBundle {
        val record = requirePlugin(pluginId)
        return InstalledPluginBundle(
            record = record,
            workflow = fileStore.loadWorkflow(record),
            uiSchema = fileStore.loadUiSchema(record),
            timingProfile = fileStore.loadTimingProfile(record),
        )
    }

    private suspend fun requirePlugin(pluginId: String): InstalledPluginRecord {
        return registryRepository.find(pluginId) ?: error("未找到插件: $pluginId")
    }
}
