package com.kebiao.viewer.core.plugin

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
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
        try {
            val manifest = json.decodeFromString<com.kebiao.viewer.core.plugin.manifest.PluginManifest>(
                fileStore.loadAssetText("$assetRoot/manifest.json"),
            )
            val existing = registryRepository.find(manifest.pluginId)
            if (existing == null) {
                installer.installBundledAssetDirectory(assetRoot)
                return
            }
            if (existing.version != manifest.version || existing.versionCode != manifest.versionCode || !existing.isBundled) {
                removePlugin(manifest.pluginId)
                installer.installBundledAssetDirectory(assetRoot)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "内置插件初始化失败: $assetRoot", error)
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
        return try {
            val record = requirePlugin(pluginId)
            fileStore.loadUiSchema(record)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w(TAG, "插件 UI 配置加载失败: $pluginId", error)
            PluginUiSchema()
        }
    }

    suspend fun loadTimingProfile(pluginId: String): TermTimingProfile? {
        return try {
            val record = requirePlugin(pluginId)
            fileStore.loadTimingProfile(record)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w(TAG, "插件时间配置加载失败: $pluginId", error)
            null
        }
    }

    suspend fun startSync(request: PluginSyncInput): WorkflowExecutionResult {
        return try {
            val bundle = loadBundle(request.pluginId)
            engine.start(bundle, request) { path ->
                fileStore.readText(bundle.record, path)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "插件同步启动失败: ${request.pluginId}", error)
            WorkflowExecutionResult.Failure(error.message?.takeIf(String::isNotBlank) ?: "插件同步启动失败")
        }
    }

    suspend fun resumeSync(pluginId: String, token: String, packet: WebSessionPacket): WorkflowExecutionResult {
        return try {
            requirePlugin(pluginId)
            engine.resume(token, packet) { bundle, path ->
                fileStore.readText(bundle.record, path)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "插件同步恢复失败: $pluginId", error)
            WorkflowExecutionResult.Failure(error.message?.takeIf(String::isNotBlank) ?: "插件同步恢复失败")
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

    private companion object {
        const val TAG = "PluginManager"
    }
}
