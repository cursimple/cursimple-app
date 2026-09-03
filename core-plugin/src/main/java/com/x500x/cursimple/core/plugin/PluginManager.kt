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
import com.x500x.cursimple.core.plugin.install.pluginCompatibilityText
import com.x500x.cursimple.core.plugin.install.resolvePluginCompatibility
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import com.x500x.cursimple.core.plugin.manifest.PluginWebEngineRequirement
import com.x500x.cursimple.core.plugin.market.ComponentMarketIndexPayload
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
    private val appContext = context.applicationContext
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

    suspend fun installPackage(
        bytes: ByteArray,
        source: PluginInstallSource,
        sourceRepo: String? = null,
    ): PluginInstallResult {
        PluginLogger.info(
            "plugin.manager.install.start",
            mapOf("source" to source, "bytes" to bytes.size),
        )
        return installer.installPackage(bytes, source, sourceRepo)
    }

    suspend fun removePlugin(pluginKey: String) {
        val normalizedKey = pluginKey.trim()
        val isInstallKey = normalizedKey.contains(':')
        val logPluginId = normalizedKey.substringBefore(':')
        val startedAt = System.currentTimeMillis()
        PluginLogger.info(
            "plugin.remove.start",
            mapOf(
                "pluginId" to logPluginId,
                "pluginKey" to normalizedKey,
                "sourceAware" to isInstallKey,
            ),
        )
        try {
            val records = if (isInstallKey) {
                registryRepository.findByInstallKey(normalizedKey)?.let(::listOf).orEmpty()
            } else {
                registryRepository.getInstalledPlugins().filter { it.pluginId == normalizedKey }
            }
            records.forEach { record ->
                File(record.storagePath).deleteRecursively()
            }
            val removedKeys = records.map { it.installKey }.toSet()
            pendingSessions.entries.removeIf { it.value.record.installKey in removedKeys }
            if (isInstallKey) {
                registryRepository.removeInstalledPluginByKey(normalizedKey)
            } else {
                registryRepository.removeInstalledPlugin(normalizedKey)
            }
            PluginLogger.info(
                "plugin.remove.success",
                mapOf(
                    "pluginId" to logPluginId,
                    "pluginKey" to normalizedKey,
                    "removedCount" to records.size,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.remove.failure",
                mapOf(
                    "pluginId" to logPluginId,
                    "pluginKey" to normalizedKey,
                    "elapsedMs" to elapsedSince(startedAt),
                ),
                error,
            )
            // 记完日志继续抛出：吞掉会让界面提示“已移除插件”，而插件仍留在已安装列表里
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
        val traceId = "trace-${UUID.randomUUID().toString().take(12)}"
        val log = PluginLogger.scope(traceId = traceId, pluginId = request.pluginId)
        log.info(
            "plugin.sync.start",
            mapOf(
                "usernamePresent" to request.username.isNotBlank(),
                "termIdPresent" to request.termId.isNotBlank(),
                "baseUrl" to PluginLogger.sanitizeUrl(request.baseUrl),
            ),
        )
        return try {
            val record = requirePlugin(request.pluginId)
            if (record.compatibilityStatus == PluginCompatibilityStatus.Incompatible) {
                return WorkflowExecutionResult.Failure(incompatibleMessage(record))
            }

            val manifest = fileStore.loadManifest(record)
            val engineFailure = validateWebEngine(manifest)
            if (engineFailure != null) {
                return engineFailure.also {
                    logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it, traceId)
                }
            }

            val missingComponents = findMissingRequiredComponents(manifest)
            if (missingComponents.isNotEmpty()) {
                return WorkflowExecutionResult.NeedsComponents(
                    pluginId = record.pluginId,
                    components = missingComponents,
                    message = appContext.getString(R.string.plugin_message_needs_components),
                ).also {
                    logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it, traceId)
                }
            }

            val entryScript = fileStore.loadEntryScript(record)
            val startUrl = resolveStartUrl(request, manifest)
            val uiSchema = fileStore.loadUiSchema(record)
            val timingProfile = fileStore.loadTimingProfile(record)
            val token = UUID.randomUUID().toString()
            val sessionId = "${record.pluginId}-${token.take(8)}"
            val messages = listOf(appContext.getString(R.string.plugin_message_runtime_ready))
            val session = PendingPluginSession(
                record = record,
                manifest = manifest,
                input = request,
                uiSchema = uiSchema,
                timingProfile = timingProfile,
                messages = messages,
                traceId = traceId,
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
                    traceId = traceId,
                ),
                uiSchema = uiSchema,
                messages = messages,
            ).also {
                logRuntimeResult("plugin.sync.start.result", request.pluginId, startedAt, it, traceId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.error(
                "plugin.sync.start.failure",
                mapOf("elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            WorkflowExecutionResult.Failure(
                appContext.pluginErrorTextOr(error, R.string.plugin_error_sync_start_failed),
            )
        }
    }

    suspend fun resumeSync(
        pluginKey: String,
        token: String,
        packet: WebSessionPacket,
    ): WorkflowExecutionResult {
        val startedAt = System.currentTimeMillis()
        val pendingTraceId = pendingSessions[token]?.traceId
        val normalizedKey = pluginKey.trim()
        val logPluginId = normalizedKey.substringBefore(':')
        val log = PluginLogger.scope(traceId = pendingTraceId, pluginId = logPluginId)
        log.info(
            "plugin.sync.resume.start",
            mapOf(
                "pluginKey" to normalizedKey,
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
                ?: return WorkflowExecutionResult.Failure(
                    appContext.getString(R.string.plugin_error_web_session_missing),
                )
            val matchesSession = if (normalizedKey.contains(':')) {
                session.record.installKey == normalizedKey
            } else {
                session.record.pluginId == normalizedKey
            }
            if (!matchesSession) {
                return WorkflowExecutionResult.Failure(
                    appContext.getString(R.string.plugin_error_web_session_mismatch),
                )
            }
            if (PluginPermission.ScheduleWrite !in session.manifest.permissions) {
                return WorkflowExecutionResult.Failure(
                    appContext.getString(R.string.plugin_error_schedule_write_permission_missing),
                )
            }

            val draftJson = packet.scheduleDraftJson?.takeIf { it.isNotBlank() }
                ?: return WorkflowExecutionResult.Failure(
                    appContext.getString(R.string.plugin_error_schedule_draft_missing),
                )
            val draftBytes = draftJson.toByteArray(Charsets.UTF_8)
            pluginRequire(
                draftBytes.size <= session.manifest.limits.maxOutputBytes,
                R.string.plugin_error_schedule_draft_too_large,
            )
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
                logRuntimeResult("plugin.sync.resume.result", logPluginId, startedAt, it, session.traceId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.error(
                "plugin.sync.resume.failure",
                mapOf("tokenPrefix" to token.take(8), "elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            WorkflowExecutionResult.Failure(
                appContext.pluginErrorTextOr(error, R.string.plugin_error_sync_resume_failed),
            )
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
                ?: pluginError(R.string.plugin_error_chromium_component_undeclared)
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
                appContext.getString(
                    R.string.plugin_error_web_engine_unsupported,
                    manifest.webEngine.preferred,
                ),
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

    /** 旧记录里存过渲染好的原因，没有时按记录声明的接口版本现算。 */
    private fun incompatibleMessage(record: InstalledPluginRecord): String {
        return record.compatibilityMessage?.takeIf(String::isNotBlank)
            ?: appContext.pluginCompatibilityText(resolvePluginCompatibility(record.apiVersion))
            ?: appContext.getString(R.string.plugin_error_incompatible_platform)
    }

    private suspend fun requirePlugin(pluginKey: String): InstalledPluginRecord {
        val normalizedKey = pluginKey.trim()
        val record = if (normalizedKey.contains(':')) {
            registryRepository.findByInstallKey(normalizedKey)
        } else {
            registryRepository.find(normalizedKey)
        }
        return record ?: pluginStateError(R.string.plugin_error_plugin_not_found, pluginKey)
    }

    private fun logRuntimeResult(
        event: String,
        pluginId: String,
        startedAt: Long,
        result: WorkflowExecutionResult,
        traceId: String? = null,
    ) {
        val sessionId = (result as? WorkflowExecutionResult.AwaitingWebSession)?.request?.sessionId
        val log = PluginLogger.scope(traceId = traceId, pluginId = pluginId, sessionId = sessionId)
        when (result) {
            is WorkflowExecutionResult.Success -> {
                log.info(
                    event,
                    mapOf(
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
                log.info(
                    event,
                    mapOf(
                        "result" to "needs_components",
                        "componentCount" to result.components.size,
                        "componentIds" to result.components.joinToString { it.id },
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
            }

            is WorkflowExecutionResult.AwaitingWebSession -> {
                log.info(
                    event,
                    mapOf(
                        "result" to "awaiting_web_session",
                        "startUrl" to PluginLogger.sanitizeUrl(result.request.startUrl),
                        "allowedHostCount" to result.request.allowedHosts.size,
                        "messageCount" to result.messages.size,
                        "elapsedMs" to elapsedSince(startedAt),
                    ),
                )
            }

            is WorkflowExecutionResult.Failure -> {
                log.warn(
                    event,
                    mapOf(
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
        val traceId: String,
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
    traceId: String = "",
): WebSessionRequest {
    return WebSessionRequest(
        token = token,
        pluginId = record.pluginId,
        installKey = record.installKey,
        sessionId = sessionId,
        traceId = traceId,
        title = record.name,
        startUrl = startUrl,
        termId = termId,
        allowedHosts = manifest.allowedHosts,
        entryScript = entryScript,
        permissions = manifest.permissions,
        limits = manifest.limits,
        userAgent = manifest.userAgent,
        completionStableDelayMs = manifest.webSession.completionStableDelayMs,
        autoCompleteOnScheduleDraft = manifest.webSession.autoCompleteOnScheduleDraft,
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
        return validateStartUrl(
            explicitUrl,
            allowedHosts,
            PluginTextArg(R.string.plugin_error_start_url_label_explicit),
        )
    }

    val declaredStartUrl = manifestStartUrl?.trim().orEmpty()
    if (declaredStartUrl.isNotBlank()) {
        return validateStartUrl(
            declaredStartUrl,
            allowedHosts,
            PluginTextArg(R.string.plugin_error_start_url_label_manifest),
        )
    }

    val firstHost = allowedHosts.firstOrNull { it.isNotBlank() }
        ?: pluginError(R.string.plugin_error_start_url_missing)
    val normalizedHost = normalizeAllowedHost(firstHost)
    return "https://$normalizedHost"
}

private fun validateStartUrl(value: String, allowedHosts: List<String>, label: PluginTextArg): String {
    val uri = parseHttpUri(value)
    val host = uri.host?.lowercase().orEmpty()
    pluginRequire(host.isNotBlank(), R.string.plugin_error_start_url_missing_host, label)
    pluginRequire(
        isAllowedHost(host, allowedHosts),
        R.string.plugin_error_start_url_host_not_allowed,
        label,
        host,
    )
    return uri.toString()
}

private fun parseHttpUri(value: String): URI {
    val uri = runCatching { URI(value) }
        .getOrElse { pluginError(R.string.plugin_error_start_url_invalid, value) }
    pluginRequire(
        uri.scheme == "http" || uri.scheme == "https",
        R.string.plugin_error_start_url_scheme,
    )
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
    pluginRequire(host.isNotBlank(), R.string.plugin_error_allowed_host_blank)
    pluginRequire(!host.contains("://"), R.string.plugin_error_allowed_host_not_domain, rawHost)
    pluginRequire(!host.contains('/'), R.string.plugin_error_allowed_host_not_domain, rawHost)
    val uri = runCatching { URI("https://$host") }
        .getOrElse { pluginError(R.string.plugin_error_allowed_host_invalid, rawHost) }
    val normalized = uri.host?.lowercase().orEmpty()
    pluginRequire(normalized.isNotBlank(), R.string.plugin_error_allowed_host_invalid, rawHost)
    return normalized
}
