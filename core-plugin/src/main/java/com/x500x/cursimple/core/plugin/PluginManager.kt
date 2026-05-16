package com.x500x.cursimple.core.plugin

import android.content.Context
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.component.PluginComponentRepository
import com.x500x.cursimple.core.plugin.component.PluginComponentStatus
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginCompatibilityStatus
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.install.PluginInstaller
import com.x500x.cursimple.core.plugin.install.PluginRegistryRepository
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import com.x500x.cursimple.core.plugin.manifest.PluginWebEngineRequirement
import com.x500x.cursimple.core.plugin.market.ComponentMarketIndexPayload
import com.x500x.cursimple.core.plugin.market.MarketIndexPayload
import com.x500x.cursimple.core.plugin.market.MarketIndexRepository
import com.x500x.cursimple.core.plugin.runtime.PluginSyncInput
import com.x500x.cursimple.core.plugin.runtime.ScheduleDraft
import com.x500x.cursimple.core.plugin.runtime.WorkflowExecutionResult
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PluginManager(
    context: Context,
    private val registryRepository: PluginRegistryRepository,
    private val componentRepository: PluginComponentRepository? = null,
    private val marketIndexRepository: MarketIndexRepository = MarketIndexRepository(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val fileStore = PluginFileStore(context, json)
    private val installer = PluginInstaller(
        registryRepository = registryRepository,
        fileStore = fileStore,
        json = json,
    )
    private val pendingSessions = ConcurrentHashMap<String, PendingPluginSession>()

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

    suspend fun removePlugin(pluginId: String) {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info("plugin.remove.start", mapOf("pluginId" to pluginId))
        try {
            val record = registryRepository.find(pluginId)
            record?.let {
                File(it.storagePath).deleteRecursively()
            }
            pendingSessions.entries.removeIf { it.value.record.pluginId == pluginId }
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

    suspend fun fetchComponentMarketIndex(url: String): ComponentMarketIndexPayload {
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.component_market.fetch.start",
            mapOf("url" to PluginLogger.sanitizeUrl(url)),
        )
        return try {
            val payload = marketIndexRepository.fetchComponentIndex(url)
            PluginLogger.info(
                "plugin.component_market.fetch.success",
                mapOf(
                    "url" to PluginLogger.sanitizeUrl(url),
                    "componentCount" to payload.components.size,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
            payload
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.component_market.fetch.failure",
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
            val record = requirePlugin(request.pluginId)
            if (record.compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
                return WorkflowExecutionResult.Failure(
                    record.compatibilityMessage ?: "插件与当前插件平台不兼容",
                )
            }

            val manifest = fileStore.loadManifest(record)
            val engineFailure = validateWebEngine(manifest)
            if (engineFailure != null) {
                return engineFailure.also {
                    logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it)
                }
            }

            val missingComponents = findMissingRequiredComponents(manifest)
            if (missingComponents.isNotEmpty()) {
                return WorkflowExecutionResult.NeedsComponents(
                    pluginId = record.pluginId,
                    components = missingComponents,
                ).also {
                    logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it)
                }
            }

            val entryScript = fileStore.loadEntryScript(record)
            val startUrl = resolveStartUrl(request, manifest)
            val uiSchema = fileStore.loadUiSchema(record)
            val timingProfile = fileStore.loadTimingProfile(record)
            val token = UUID.randomUUID().toString()
            val sessionId = "${record.pluginId}-${token.take(8)}"
            val messages = listOf("插件运行时已准备，需要在 WebView 会话中执行入口脚本")
            val session = PendingPluginSession(
                record = record,
                manifest = manifest,
                input = request,
                uiSchema = uiSchema,
                timingProfile = timingProfile,
                messages = messages,
            )
            pendingSessions[token] = session

            WorkflowExecutionResult.AwaitingWebSession(
                request = buildWebSessionRequest(
                    token = token,
                    record = record,
                    sessionId = sessionId,
                    startUrl = startUrl,
                    termId = request.termId,
                    entryScript = entryScript,
                    manifest = manifest,
                ),
                uiSchema = uiSchema,
                messages = messages,
            ).also {
                logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it)
            }
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

    suspend fun resumeSync(
        pluginId: String,
        token: String,
        packet: WebSessionPacket,
    ): WorkflowExecutionResult {
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
                "capturedPacketCount" to packet.capturedPackets.size,
                "htmlDigest" to packet.htmlDigest,
            ),
        )
        return try {
            val session = pendingSessions.remove(token)
                ?: return WorkflowExecutionResult.Failure("待恢复的 Web 会话不存在")
            if (session.record.pluginId != pluginId) {
                return WorkflowExecutionResult.Failure("Web 会话与插件不匹配")
            }
            if (PluginPermission.ScheduleWrite !in session.manifest.permissions) {
                return WorkflowExecutionResult.Failure("插件未声明 schedule.write 权限")
            }

            val draftJson = packet.scheduleDraftJson?.takeIf { it.isNotBlank() }
                ?: return WorkflowExecutionResult.Failure("插件未提交课程草稿")
            val draftBytes = draftJson.toByteArray(Charsets.UTF_8)
            require(draftBytes.size <= session.manifest.limits.maxOutputBytes) {
                "插件课程草稿超过输出大小限制"
            }
            val draft = json.decodeFromString<ScheduleDraft>(draftJson)
            val effectiveDraft = if (draft.termId.isBlank() && session.input.termId.isNotBlank()) {
                draft.copy(termId = session.input.termId)
            } else {
                draft
            }
            WorkflowExecutionResult.Success(
                schedule = effectiveDraft.toTermSchedule(session.manifest.limits),
                uiSchema = session.uiSchema,
                timingProfile = session.timingProfile,
                messages = session.messages,
            ).also {
                logRuntimeResult("plugin.sync.resume.result", pluginId, startedAt, it)
            }
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

    private suspend fun findMissingRequiredComponents(
        manifest: PluginManifest,
    ): List<PluginComponentRequirement> {
        val required = requiredComponents(manifest)
        if (required.isEmpty()) {
            return emptyList()
        }
        val repository = componentRepository ?: return required
        val installed = repository.getInstalledComponents().associateBy { it.id }
        return required.filter { requirement ->
            val record = installed[requirement.id] ?: return@filter true
            if (record.status != PluginComponentStatus.Installed) {
                return@filter true
            }
            if (requirement.version != null && record.version != requirement.version) {
                return@filter true
            }
            if (requirement.abi != null && record.abi != null && record.abi != requirement.abi) {
                return@filter true
            }
            false
        }
    }

    private fun requiredComponents(manifest: PluginManifest): List<PluginComponentRequirement> {
        val declared = manifest.components.filter { it.required }.toMutableList()
        if (manifest.webEngine.preferred == PluginWebEngineRequirement.ENGINE_CHROMIUM) {
            val chromiumComponent = manifest.webEngine.chromiumComponent
                ?: throw IllegalArgumentException("插件声明 Chromium Web 引擎但未声明 Chromium 组件")
            declared += PluginComponentRequirement(
                id = chromiumComponent,
                type = "engine_chromium",
                required = true,
            )
        }
        return declared.distinctBy { it.id }
    }

    private fun validateWebEngine(manifest: PluginManifest): WorkflowExecutionResult.Failure? {
        return when (manifest.webEngine.preferred) {
            PluginWebEngineRequirement.ENGINE_SYSTEM_WEBVIEW,
            PluginWebEngineRequirement.ENGINE_CHROMIUM,
            -> null

            else -> WorkflowExecutionResult.Failure(
                "插件声明了不支持的 Web 引擎: ${manifest.webEngine.preferred}",
            )
        }
    }

    private fun resolveStartUrl(request: PluginSyncInput, manifest: PluginManifest): String {
        return resolveWebSessionStartUrl(
            requestBaseUrl = request.baseUrl,
            manifestStartUrl = manifest.startUrl,
            allowedHosts = manifest.allowedHosts,
        )
    }

    private suspend fun requirePlugin(pluginId: String): InstalledPluginRecord {
        return registryRepository.find(pluginId) ?: error("未找到插件: $pluginId")
    }

    private fun logRuntimeResult(
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

            is WorkflowExecutionResult.NeedsComponents -> {
                PluginLogger.info(
                    event,
                    mapOf(
                        "pluginId" to pluginId,
                        "result" to "needs_components",
                        "componentCount" to result.components.size,
                        "componentIds" to result.components.joinToString { it.id },
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

    private data class PendingPluginSession(
        val record: InstalledPluginRecord,
        val manifest: PluginManifest,
        val input: PluginSyncInput,
        val uiSchema: PluginUiSchema,
        val timingProfile: TermTimingProfile?,
        val messages: List<String>,
    )
}

internal fun buildWebSessionRequest(
    token: String,
    record: InstalledPluginRecord,
    sessionId: String,
    startUrl: String,
    termId: String,
    entryScript: String,
    manifest: PluginManifest,
): WebSessionRequest {
    return WebSessionRequest(
        token = token,
        pluginId = record.pluginId,
        sessionId = sessionId,
        title = record.name,
        startUrl = startUrl,
        termId = termId,
        allowedHosts = manifest.allowedHosts,
        entryScript = entryScript,
        permissions = manifest.permissions,
        limits = manifest.limits,
        userAgent = manifest.userAgent,
        networkCaptures = if (PluginPermission.WebCapturePacket in manifest.permissions) {
            manifest.networkCaptures
        } else {
            emptyList()
        },
        extractCookies = PluginPermission.WebReadCookies in manifest.permissions,
        extractLocalStorage = PluginPermission.StoragePlugin in manifest.permissions,
        extractSessionStorage = PluginPermission.StoragePlugin in manifest.permissions,
        extractHtmlDigest = PluginPermission.WebReadDom in manifest.permissions,
    )
}

internal fun resolveWebSessionStartUrl(
    requestBaseUrl: String,
    manifestStartUrl: String?,
    allowedHosts: List<String>,
): String {
    val explicitUrl = requestBaseUrl.trim()
    if (explicitUrl.isNotBlank()) {
        return validateStartUrl(explicitUrl, allowedHosts, "插件起始地址")
    }

    val declaredStartUrl = manifestStartUrl?.trim().orEmpty()
    if (declaredStartUrl.isNotBlank()) {
        return validateStartUrl(declaredStartUrl, allowedHosts, "插件 manifest startUrl")
    }

    val firstHost = allowedHosts.firstOrNull { it.isNotBlank() }
        ?: throw IllegalArgumentException("插件缺少可打开的起始地址")
    val normalizedHost = normalizeAllowedHost(firstHost)
    return "https://$normalizedHost"
}

private fun validateStartUrl(value: String, allowedHosts: List<String>, label: String): String {
    val uri = parseHttpUri(value)
    val host = uri.host?.lowercase().orEmpty()
    require(host.isNotBlank()) { "$label 缺少域名" }
    require(isAllowedHost(host, allowedHosts)) { "$label 不在 allowedHosts 中: $host" }
    return uri.toString()
}

private fun parseHttpUri(value: String): URI {
    val uri = runCatching { URI(value) }
        .getOrElse { throw IllegalArgumentException("插件起始地址无效: $value") }
    require(uri.scheme == "http" || uri.scheme == "https") { "插件起始地址必须使用 http 或 https" }
    return uri
}

private fun isAllowedHost(host: String, allowedHosts: List<String>): Boolean {
    return allowedHosts.any { allowed ->
        val normalized = normalizeAllowedHost(allowed)
        host == normalized || host.endsWith(".$normalized")
    }
}

private fun normalizeAllowedHost(rawHost: String): String {
    val host = rawHost.trim().lowercase()
    require(host.isNotBlank()) { "插件 allowedHosts 包含空域名" }
    require(!host.contains("://")) { "插件 allowedHosts 只能声明域名: $rawHost" }
    require(!host.contains('/')) { "插件 allowedHosts 只能声明域名: $rawHost" }
    val uri = runCatching { URI("https://$host") }
        .getOrElse { throw IllegalArgumentException("插件 allowedHosts 域名无效: $rawHost") }
    val normalized = uri.host?.lowercase().orEmpty()
    require(normalized.isNotBlank()) { "插件 allowedHosts 域名无效: $rawHost" }
    return normalized
}
