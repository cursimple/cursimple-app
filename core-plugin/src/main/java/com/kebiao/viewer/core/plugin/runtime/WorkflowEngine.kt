package com.kebiao.viewer.core.plugin.runtime

import android.util.Log
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.plugin.manifest.PluginPermission
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepDefinition
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.time.OffsetDateTime
import java.util.UUID

interface WorkflowEngine {
    suspend fun start(bundle: InstalledPluginBundle, input: PluginSyncInput, assetReader: (String) -> String): WorkflowExecutionResult

    suspend fun resume(token: String, packet: WebSessionPacket, assetReader: (InstalledPluginBundle, String) -> String): WorkflowExecutionResult
}

class DefaultWorkflowEngine(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val client: OkHttpClient = OkHttpClient(),
) : WorkflowEngine {
    private val pendingExecutions = linkedMapOf<String, PendingWorkflowExecution>()
    private val mutex = Mutex()
    private val eamsScheduleParser = EamsScheduleParser()

    override suspend fun start(
        bundle: InstalledPluginBundle,
        input: PluginSyncInput,
        assetReader: (String) -> String,
    ): WorkflowExecutionResult = withContext(Dispatchers.Default) {
        val execution = PendingWorkflowExecution(
            token = UUID.randomUUID().toString(),
            bundle = bundle,
            input = input,
            nextStepIndex = 0,
            contextData = emptyMap(),
            webPackets = emptyMap(),
            recommendations = emptyList(),
            messages = emptyList(),
        )
        runWorkflowSafely {
            runSteps(execution, assetReader = { assetReader(it) }, bundleReader = null)
        }
    }

    override suspend fun resume(
        token: String,
        packet: WebSessionPacket,
        assetReader: (InstalledPluginBundle, String) -> String,
    ): WorkflowExecutionResult = withContext(Dispatchers.Default) {
        val execution = mutex.withLock { pendingExecutions.remove(token) }
            ?: return@withContext WorkflowExecutionResult.Failure("待恢复的 Web 会话不存在")
        val step = execution.bundle.workflow.steps.getOrNull(execution.nextStepIndex - 1)
            ?: return@withContext WorkflowExecutionResult.Failure("待恢复的工作流步骤不存在")
        val sessionId = step.sessionId.orEmpty()
        val resumed = execution.copy(
            webPackets = execution.webPackets + (sessionId to packet),
        )
        runWorkflowSafely {
            runSteps(resumed, assetReader = null, bundleReader = assetReader)
        }
    }

    private suspend fun runWorkflowSafely(
        block: suspend () -> WorkflowExecutionResult,
    ): WorkflowExecutionResult {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            WorkflowExecutionResult.Failure(error.message?.takeIf(String::isNotBlank) ?: "插件工作流执行失败")
        }
    }

    private suspend fun runSteps(
        initial: PendingWorkflowExecution,
        assetReader: ((String) -> String)?,
        bundleReader: ((InstalledPluginBundle, String) -> String)?,
    ): WorkflowExecutionResult {
        var execution = initial
        val steps = execution.bundle.workflow.steps
        var schedule: TermSchedule? = null
        for (index in execution.nextStepIndex until steps.size) {
            val step = steps[index]
            execution = execution.copy(nextStepIndex = index + 1)
            when (step.type) {
                WorkflowStepType.UiEmit -> {
                    // UI schema is loaded outside the engine; this step acts as an explicit checkpoint.
                }

                WorkflowStepType.Message -> {
                    step.message?.let {
                        execution = execution.copy(messages = execution.messages + renderTemplate(it, execution))
                    }
                }

                WorkflowStepType.AlarmPlanEmit -> {
                    val minutes = step.recommendedAdvanceMinutes ?: 15
                    execution = execution.copy(
                        recommendations = execution.recommendations + AlarmRecommendation(
                            pluginId = execution.bundle.record.pluginId,
                            advanceMinutes = minutes,
                            note = step.message ?: "插件建议提醒",
                        ),
                    )
                }

                WorkflowStepType.WebSession -> {
                    ensurePermission(execution, PluginPermission.WebSession)
                    val request = WebSessionRequest(
                        token = execution.token,
                        pluginId = execution.bundle.record.pluginId,
                        sessionId = step.sessionId ?: step.id,
                        title = step.title ?: "插件网页登录",
                        startUrl = renderTemplate(step.urlTemplate.orEmpty(), execution),
                        allowedHosts = execution.bundle.record.allowedHosts,
                        completionUrlContains = step.completionUrlContains?.let { renderTemplate(it, execution) },
                        userAgent = renderTemplate(step.userAgent.orEmpty(), execution).takeIf(String::isNotBlank),
                        captureSelectors = step.captureSelectors,
                        extractCookies = step.extractCookies,
                        extractLocalStorage = step.extractLocalStorage,
                        extractSessionStorage = step.extractSessionStorage,
                        extractHtmlDigest = step.extractHtmlDigest,
                    )
                    mutex.withLock { pendingExecutions[execution.token] = execution }
                    return WorkflowExecutionResult.AwaitingWebSession(
                        request = request,
                        uiSchema = execution.bundle.uiSchema,
                        messages = execution.messages,
                    )
                }

                WorkflowStepType.HttpRequest -> {
                    ensurePermission(execution, PluginPermission.Network)
                    val response = performHttpRequest(step, execution)
                    val key = step.responseKey ?: step.id
                    execution = execution.copy(contextData = execution.contextData + (key to response))
                }

                WorkflowStepType.EamsExtractMeta -> {
                    val source = step.sourceContextKey?.let(execution.contextData::get)
                        ?: throw IllegalStateException("未找到 EAMS 元数据源: ${step.sourceContextKey}")
                    val meta = eamsScheduleParser.extractMetadata(source)
                    val key = step.responseKey ?: step.id
                    execution = execution.copy(
                        contextData = execution.contextData + (key to json.encodeToString(EamsCourseTableMeta.serializer(), meta)),
                    )
                }

                WorkflowStepType.ScheduleEmitStatic -> {
                    val raw = when {
                        !step.scheduleContextKey.isNullOrBlank() -> execution.contextData[step.scheduleContextKey]
                            ?: throw IllegalStateException("未找到课表上下文: ${step.scheduleContextKey}")

                        !step.scheduleAsset.isNullOrBlank() && assetReader != null -> assetReader(step.scheduleAsset)
                        !step.scheduleAsset.isNullOrBlank() && bundleReader != null -> bundleReader(execution.bundle, step.scheduleAsset)
                        else -> throw IllegalStateException("未提供课表模板")
                    }
                    val rendered = renderTemplate(raw, execution)
                    val parsed = json.decodeFromString<TermSchedule>(rendered)
                    schedule = if (step.updatedAtNow) {
                        parsed.copy(updatedAt = OffsetDateTime.now().toString())
                    } else {
                        parsed
                    }
                }

                WorkflowStepType.ScheduleEmitEams -> {
                    val metaRaw = step.metaContextKey?.let(execution.contextData::get)
                        ?: throw IllegalStateException("未找到 EAMS 元数据上下文: ${step.metaContextKey}")
                    val detailRaw = step.detailContextKey?.let(execution.contextData::get)
                        ?: throw IllegalStateException("未找到 EAMS 课表明细上下文: ${step.detailContextKey}")
                    val meta = json.decodeFromString(EamsCourseTableMeta.serializer(), metaRaw)
                    val updatedAt = if (step.updatedAtNow) {
                        OffsetDateTime.now().toString()
                    } else {
                        execution.contextData["updatedAt"] ?: OffsetDateTime.now().toString()
                    }
                    schedule = eamsScheduleParser.buildSchedule(
                        meta = meta,
                        detailHtml = detailRaw,
                        termId = renderTemplate(step.termIdTemplate.orEmpty().ifBlank { "{{termId}}" }, execution),
                        updatedAt = updatedAt,
                    )
                }
            }
        }
        return schedule?.let {
            WorkflowExecutionResult.Success(
                schedule = it,
                uiSchema = execution.bundle.uiSchema,
                timingProfile = execution.bundle.timingProfile,
                recommendations = execution.recommendations,
                messages = execution.messages,
            )
        } ?: WorkflowExecutionResult.Failure("插件未产出课表")
    }

    private suspend fun performHttpRequest(
        step: WorkflowStepDefinition,
        execution: PendingWorkflowExecution,
    ): String = withContext(Dispatchers.IO) {
        val url = renderTemplate(step.urlTemplate.orEmpty(), execution)
        require(isHostAllowed(url, execution.bundle.record.allowedHosts)) { "目标域名不在插件白名单中: $url" }
        val builder = Request.Builder().url(url)
        step.cookieSessionId
            ?.let(execution.webPackets::get)
            ?.cookies
            ?.takeIf(Map<String, String>::isNotEmpty)
            ?.entries
            ?.joinToString("; ") { (key, value) -> "$key=$value" }
            ?.let { builder.header("Cookie", it) }
        step.httpHeaders.forEach { (key, value) ->
            builder.addHeader(key, renderTemplate(value, execution))
        }
        val method = step.httpMethod?.uppercase().orEmpty().ifBlank { "GET" }
        if (method == "GET" || method == "HEAD") {
            builder.method(method, null)
        } else {
            val body = renderTemplate(step.httpBodyTemplate.orEmpty(), execution)
            val contentType = step.httpContentType?.ifBlank { null } ?: "application/json; charset=utf-8"
            builder.method(method, body.toRequestBody(contentType.toMediaType()))
        }
        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body.string()
            check(response.isSuccessful) { "HTTP 请求失败: ${response.code}" }
            responseBody
        }
    }

    private fun ensurePermission(execution: PendingWorkflowExecution, permission: PluginPermission) {
        require(permission in execution.bundle.record.declaredPermissions) {
            "插件未声明权限: $permission"
        }
    }

    private fun isHostAllowed(url: String, allowedHosts: List<String>): Boolean {
        if (allowedHosts.isEmpty()) {
            return false
        }
        val host = URL(url).host.lowercase()
        return allowedHosts.any { allowed ->
            host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}")
        }
    }

    private fun renderTemplate(template: String, execution: PendingWorkflowExecution): String {
        val regex = Regex("\\{\\{([^}]+)\\}\\}")
        return regex.replace(template) { match ->
            resolvePlaceholder(match.groupValues[1].trim(), execution) ?: ""
        }
    }

    private fun resolvePlaceholder(expression: String, execution: PendingWorkflowExecution): String? {
        return when {
            expression == "pluginId" -> execution.bundle.record.pluginId
            expression == "username" -> execution.input.username
            expression == "password" -> execution.input.password
            expression == "termId" -> execution.input.termId
            expression == "baseUrl" -> execution.input.baseUrl
            expression == "nowIso" -> OffsetDateTime.now().toString()
            expression == "nowMillis" -> System.currentTimeMillis().toString()
            expression.startsWith("input.") -> execution.input.extraInputs[expression.removePrefix("input.")]
            expression.startsWith("context.") -> resolveContextExpression(expression.removePrefix("context."), execution)
            expression.startsWith("web.") -> resolveWebExpression(expression.removePrefix("web."), execution)
            else -> {
                Log.w("WorkflowEngine", "未知模板变量: $expression")
                null
            }
        }
    }

    private fun resolveContextExpression(expression: String, execution: PendingWorkflowExecution): String? {
        val segments = expression.split(".")
        val rootKey = segments.firstOrNull().orEmpty()
        val raw = execution.contextData[rootKey] ?: return null
        if (segments.size == 1) {
            return raw
        }
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return resolveJsonPath(element, segments.drop(1))
    }

    private fun resolveJsonPath(element: JsonElement, path: List<String>): String? {
        if (path.isEmpty()) {
            return when (element) {
                is JsonPrimitive -> element.content
                is JsonObject, is JsonArray -> element.toString()
            }
        }
        val next = when (element) {
            is JsonObject -> element[path.first()]
            is JsonArray -> path.first().toIntOrNull()?.let { index -> element.getOrNull(index) }
            is JsonPrimitive -> null
        } ?: return null
        return resolveJsonPath(next, path.drop(1))
    }

    private fun resolveWebExpression(expression: String, execution: PendingWorkflowExecution): String? {
        val segments = expression.split(".")
        if (segments.size < 2) {
            return null
        }
        val sessionId = segments[0]
        val packet = execution.webPackets[sessionId] ?: return null
        return when (segments[1]) {
            "finalUrl" -> packet.finalUrl
            "htmlDigest" -> packet.htmlDigest
            "cookie" -> packet.cookies[segments.getOrNull(2)]
            "localStorage" -> packet.localStorageSnapshot[segments.getOrNull(2)]
            "sessionStorage" -> packet.sessionStorageSnapshot[segments.getOrNull(2)]
            "field" -> packet.capturedFields[segments.getOrNull(2)]
            else -> null
        }
    }
}
