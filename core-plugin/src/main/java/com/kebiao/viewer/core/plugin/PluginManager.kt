package com.kebiao.viewer.core.plugin

import android.content.Context
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginInstallPreview
import com.kebiao.viewer.core.plugin.install.PluginInstallResult
import com.kebiao.viewer.core.plugin.install.PluginInstallSource
import com.kebiao.viewer.core.plugin.install.PluginInstaller
import com.kebiao.viewer.core.plugin.install.PluginRegistryRepository
import com.kebiao.viewer.core.plugin.logging.PluginLogger
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
        PluginLogger.info(
            "plugin.manager.preview.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
        return installer.previewPackage(bytes, source)
    }

    suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallResult {
        PluginLogger.info(
            "plugin.manager.install.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
        return installer.installPackage(bytes, source)
    }

    suspend fun ensureBundledPlugin(assetRoot: String) {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.bundled.ensure.start",
            mapOf("assetRoot" to assetRoot),
        )
        try {
            val manifest = json.decodeFromString<com.kebiao.viewer.core.plugin.manifest.PluginManifest>(
                fileStore.loadAssetText("$assetRoot/manifest.json"),
            )
            val existing = registryRepository.find(manifest.pluginId)
            if (existing == null) {
                installer.installBundledAssetDirectory(assetRoot)
                PluginLogger.info(
                    "plugin.bundled.ensure.installed",
                    mapOf(
                        "assetRoot" to assetRoot,
                        "pluginId" to manifest.pluginId,
                        "version" to manifest.version,
                        "versionCode" to manifest.versionCode,
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
                return
            }
            if (existing.version != manifest.version || existing.versionCode != manifest.versionCode || !existing.isBundled) {
                PluginLogger.info(
                    "plugin.bundled.ensure.update",
                    mapOf(
                        "assetRoot" to assetRoot,
                        "pluginId" to manifest.pluginId,
                        "oldVersion" to existing.version,
                        "oldVersionCode" to existing.versionCode,
                        "newVersion" to manifest.version,
                        "newVersionCode" to manifest.versionCode,
                    ),
                )
                removePlugin(manifest.pluginId)
                installer.installBundledAssetDirectory(assetRoot)
                PluginLogger.info(
                    "plugin.bundled.ensure.updated",
                    mapOf(
                        "assetRoot" to assetRoot,
                        "pluginId" to manifest.pluginId,
                        "version" to manifest.version,
                        "versionCode" to manifest.versionCode,
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
                return
            }
            PluginLogger.info(
                "plugin.bundled.ensure.skipped",
                mapOf(
                    "assetRoot" to assetRoot,
                    "pluginId" to manifest.pluginId,
                    "version" to manifest.version,
                    "versionCode" to manifest.versionCode,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.bundled.ensure.failure",
                mapOf("assetRoot" to assetRoot, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
        }
    }

    suspend fun removePlugin(pluginId: String) {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info("plugin.remove.start", mapOf("pluginId" to pluginId))
        try {
            val record = registryRepository.find(pluginId)
            record?.let {
                File(it.storagePath).deleteRecursively()
            }
            registryRepository.removeInstalledPlugin(pluginId)
            PluginLogger.info(
                "plugin.remove.success",
                mapOf(
                    "pluginId" to pluginId,
                    "storagePathPresent" to (record?.storagePath?.isNotBlank() == true),
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.remove.failure",
                mapOf("pluginId" to pluginId, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
        }
    }

    suspend fun fetchMarketIndex(url: String): MarketIndexPayload {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.market.fetch.start",
            mapOf("url" to PluginLogger.sanitizeUrl(url)),
        )
        return try {
            val payload = marketIndexRepository.fetch(url)
            PluginLogger.info(
                "plugin.market.fetch.success",
                mapOf(
                    "url" to PluginLogger.sanitizeUrl(url),
                    "pluginCount" to payload.plugins.size,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            payload
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.market.fetch.failure",
                mapOf("url" to PluginLogger.sanitizeUrl(url), "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            throw error
        }
    }

    suspend fun downloadRemotePackage(url: String): ByteArray {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.market.download.start",
            mapOf("url" to PluginLogger.sanitizeUrl(url)),
        )
        return try {
            val bytes = marketIndexRepository.downloadPackage(url)
            PluginLogger.info(
                "plugin.market.download.success",
                mapOf(
                    "url" to PluginLogger.sanitizeUrl(url),
                    "bytes" to bytes.size,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            bytes
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.market.download.failure",
                mapOf("url" to PluginLogger.sanitizeUrl(url), "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            throw error
        }
    }

    suspend fun loadUiSchema(pluginId: String): PluginUiSchema {
        val startedAt = System.currentTimeMillis()
        return try {
            val record = requirePlugin(pluginId)
            val schema = fileStore.loadUiSchema(record)
            PluginLogger.info(
                "plugin.ui_schema.load.success",
                mapOf("pluginId" to pluginId, "elapsedMs" to elapsedSince(startedAt)),
            )
            schema
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.warn(
                "plugin.ui_schema.load.failure",
                mapOf("pluginId" to pluginId, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            PluginUiSchema()
        }
    }

    suspend fun loadTimingProfile(pluginId: String): TermTimingProfile? {
        val startedAt = System.currentTimeMillis()
        return try {
            val record = requirePlugin(pluginId)
            val profile = fileStore.loadTimingProfile(record)
            PluginLogger.info(
                "plugin.timing.load.success",
                mapOf(
                    "pluginId" to pluginId,
                    "hasProfile" to (profile != null),
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            profile
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.warn(
                "plugin.timing.load.failure",
                mapOf("pluginId" to pluginId, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            null
        }
    }

    suspend fun startSync(request: PluginSyncInput): WorkflowExecutionResult {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.sync.start",
            mapOf(
                "pluginId" to request.pluginId,
                "usernamePresent" to request.username.isNotBlank(),
                "termIdPresent" to request.termId.isNotBlank(),
                "baseUrl" to PluginLogger.sanitizeUrl(request.baseUrl),
            ),
        )
        return try {
            val bundle = loadBundle(request.pluginId)
            val result = engine.start(bundle, request) { path ->
                fileStore.readText(bundle.record, path)
            }
            logWorkflowResult("plugin.sync.start.result", request.pluginId, startedAt, result)
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.sync.start.failure",
                mapOf("pluginId" to request.pluginId, "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            WorkflowExecutionResult.Failure(error.message?.takeIf(String::isNotBlank) ?: "插件同步启动失败")
        }
    }

    suspend fun resumeSync(pluginId: String, token: String, packet: WebSessionPacket): WorkflowExecutionResult {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.sync.resume.start",
            mapOf(
                "pluginId" to pluginId,
                "tokenPrefix" to token.take(8),
                "finalUrl" to PluginLogger.sanitizeUrl(packet.finalUrl),
                "cookieCount" to packet.cookies.size,
                "localStorageCount" to packet.localStorageSnapshot.size,
                "sessionStorageCount" to packet.sessionStorageSnapshot.size,
                "capturedFieldCount" to packet.capturedFields.size,
                "htmlDigest" to packet.htmlDigest,
            ),
        )
        return try {
            requirePlugin(pluginId)
            val result = engine.resume(token, packet) { bundle, path ->
                fileStore.readText(bundle.record, path)
            }
            logWorkflowResult("plugin.sync.resume.result", pluginId, startedAt, result)
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.sync.resume.failure",
                mapOf("pluginId" to pluginId, "tokenPrefix" to token.take(8), "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
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

    private fun logWorkflowResult(
        event: String,
        pluginId: String,
        startedAt: Long,
        result: WorkflowExecutionResult,
    ) {
        when (result) {
            is WorkflowExecutionResult.Success -> {
                PluginLogger.info(
                    event,
                    mapOf(
                        "pluginId" to pluginId,
                        "result" to "success",
                        "courseCount" to result.schedule.dailySchedules.sumOf { it.courses.size },
                        "dailyScheduleCount" to result.schedule.dailySchedules.size,
                        "messageCount" to result.messages.size,
                        "recommendationCount" to result.recommendations.size,
                        "hasTimingProfile" to (result.timingProfile != null),
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
            }

            is WorkflowExecutionResult.AwaitingWebSession -> {
                PluginLogger.info(
                    event,
                    mapOf(
                        "pluginId" to pluginId,
                        "result" to "awaiting_web_session",
                        "sessionId" to result.request.sessionId,
                        "startUrl" to PluginLogger.sanitizeUrl(result.request.startUrl),
                        "allowedHostCount" to result.request.allowedHosts.size,
                        "messageCount" to result.messages.size,
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
            }

            is WorkflowExecutionResult.Failure -> {
                PluginLogger.warn(
                    event,
                    mapOf(
                        "pluginId" to pluginId,
                        "result" to "failure",
                        "failureMessage" to result.message,
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
            }
        }
    }

    private fun elapsedSince(startedAt: Long): Long {
        return System.currentTimeMillis() - startedAt
    }
}
