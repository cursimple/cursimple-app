package com.kebiao.viewer.core.plugin.runtime

import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.plugin.logging.PluginLogger
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
        val startedAt = System.currentTimeMillis()
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
        PluginLogger.info(
            "plugin.workflow.start",
            mapOf(
                "pluginId" to bundle.record.pluginId,
                "workflowId" to bundle.workflow.workflowId,
                "tokenPrefix" to execution.token.take(8),
                "stepCount" to bundle.workflow.steps.size,
                "usernamePresent" to input.username.isNotBlank(),
                "termIdPresent" to input.termId.isNotBlank(),
                "baseUrl" to PluginLogger.sanitizeUrl(input.baseUrl),
            ),
        )
        runWorkflowSafely {
            val result = runSteps(execution, assetReader = { assetReader(it) }, bundleReader = null)
            logWorkflowCompleted("plugin.workflow.start.completed", execution, startedAt, result)
            result
        }
    }

    override suspend fun resume(
        token: String,
        packet: WebSessionPacket,
        assetReader: (InstalledPluginBundle, String) -> String,
    ): WorkflowExecutionResult = withContext(Dispatchers.Default) {
        val startedAt = System.currentTimeMillis()
        val execution = mutex.withLock { pendingExecutions.remove(token) }
            ?: run {
                PluginLogger.warn(
                    "plugin.workflow.resume.missing",
                    mapOf(
                        "tokenPrefix" to token.take(8),
                        "finalUrl" to PluginLogger.sanitizeUrl(packet.finalUrl),
                        "cookieCount" to packet.cookies.size,
                        "localStorageCount" to packet.localStorageSnapshot.size,
                        "sessionStorageCount" to packet.sessionStorageSnapshot.size,
                        "capturedFieldCount" to packet.capturedFields.size,
                    ),
                )
                return@withContext WorkflowExecutionResult.Failure("待恢复的 Web 会话不存在")
            }
        PluginLogger.info(
            "plugin.workflow.resume",
            mapOf(
                "pluginId" to execution.bundle.record.pluginId,
                "workflowId" to execution.bundle.workflow.workflowId,
                "tokenPrefix" to token.take(8),
                "nextStepIndex" to execution.nextStepIndex,
                "finalUrl" to PluginLogger.sanitizeUrl(packet.finalUrl),
                "cookieCount" to packet.cookies.size,
                "localStorageCount" to packet.localStorageSnapshot.size,
                "sessionStorageCount" to packet.sessionStorageSnapshot.size,
                "capturedFieldCount" to packet.capturedFields.size,
                "htmlDigest" to packet.htmlDigest,
            ),
        )
        val step = execution.bundle.workflow.steps.getOrNull(execution.nextStepIndex - 1)
            ?: run {
                PluginLogger.warn(
                    "plugin.workflow.resume.step_missing",
                    mapOf(
                        "pluginId" to execution.bundle.record.pluginId,
                        "workflowId" to execution.bundle.workflow.workflowId,
                        "tokenPrefix" to token.take(8),
                        "nextStepIndex" to execution.nextStepIndex,
                    ),
                )
                return@withContext WorkflowExecutionResult.Failure("待恢复的工作流步骤不存在")
            }
        val sessionId = step.sessionId.orEmpty()
        val resumed = execution.copy(
            webPackets = execution.webPackets + (sessionId to packet),
        )
        runWorkflowSafely {
            val result = runSteps(resumed, assetReader = null, bundleReader = assetReader)
            logWorkflowCompleted("plugin.workflow.resume.completed", resumed, startedAt, result)
            result
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
            val stepStartedAt = System.currentTimeMillis()
            execution = execution.copy(nextStepIndex = index + 1)
            PluginLogger.info(
                "plugin.workflow.step.start",
                stepFields(execution, step, index),
            )
            try {
                var successFields: Map<String, Any?> = emptyMap()
                when (step.type) {
                    WorkflowStepType.UiEmit -> {
                        // UI schema is loaded outside the engine; this step acts as an explicit checkpoint.
                    }

                    WorkflowStepType.Message -> {
                        step.message?.let {
                            val rendered = renderTemplate(it, execution)
                            execution = execution.copy(messages = execution.messages + rendered)
                            successFields = mapOf("messageLength" to rendered.length, "messageCount" to execution.messages.size)
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
                        successFields = mapOf(
                            "recommendedAdvanceMinutes" to minutes,
                            "recommendationCount" to execution.recommendations.size,
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
                            autoNavigateOnUrlContains = step.autoNavigateOnUrlContains?.let { renderTemplate(it, execution) },
                            autoNavigateToUrl = step.autoNavigateToUrl?.let { renderTemplate(it, execution) },
                            userAgent = renderTemplate(step.userAgent.orEmpty(), execution).takeIf(String::isNotBlank),
                            captureSelectors = step.captureSelectors,
                            capturePackets = step.capturePackets,
                            extractCookies = step.extractCookies,
                            extractLocalStorage = step.extractLocalStorage,
                            extractSessionStorage = step.extractSessionStorage,
                            extractHtmlDigest = step.extractHtmlDigest,
                        )
                        PluginLogger.info(
                            "plugin.workflow.web_session.request",
                            stepFields(execution, step, index) + mapOf(
                                "sessionId" to request.sessionId,
                                "titlePresent" to request.title.isNotBlank(),
                                "startUrl" to PluginLogger.sanitizeUrl(request.startUrl),
                                "completionUrlContainsPresent" to !request.completionUrlContains.isNullOrBlank(),
                                "autoNavigateOnUrlContainsPresent" to !request.autoNavigateOnUrlContains.isNullOrBlank(),
                                "autoNavigateToUrlPresent" to !request.autoNavigateToUrl.isNullOrBlank(),
                                "allowedHostCount" to request.allowedHosts.size,
                                "captureSelectorCount" to request.captureSelectors.size,
                                "capturePacketCount" to request.capturePackets.size,
                                "extractCookies" to request.extractCookies,
                                "extractLocalStorage" to request.extractLocalStorage,
                                "extractSessionStorage" to request.extractSessionStorage,
                                "extractHtmlDigest" to request.extractHtmlDigest,
                                "elapsedMs" to elapsedSince(stepStartedAt),
                            ),
                        )
                        mutex.withLock { pendingExecutions[execution.token] = execution }
                        PluginLogger.info(
                            "plugin.workflow.step.awaiting_web_session",
                            stepFields(execution, step, index) + mapOf(
                                "sessionId" to request.sessionId,
                                "elapsedMs" to elapsedSince(stepStartedAt),
                            ),
                        )
                        return WorkflowExecutionResult.AwaitingWebSession(
                            request = request,
                            uiSchema = execution.bundle.uiSchema,
                            messages = execution.messages,
                        )
                    }

                    WorkflowStepType.HttpRequest -> {
                        ensurePermission(execution, PluginPermission.Network)
                        val response = performHttpRequest(step, execution, index)
                        val key = step.responseKey ?: step.id
                        execution = execution.copy(contextData = execution.contextData + (key to response))
                        successFields = mapOf("responseKey" to key, "responseLength" to response.length)
                    }

                    WorkflowStepType.EamsExtractMeta -> {
                        val source = step.sourceContextKey?.let(execution.contextData::get)
                            ?: throw IllegalStateException("未找到 EAMS 元数据源: ${step.sourceContextKey}")
                        val meta = eamsScheduleParser.extractMetadata(source)
                        val key = step.responseKey ?: step.id
                        execution = execution.copy(
                            contextData = execution.contextData + (key to json.encodeToString(EamsCourseTableMeta.serializer(), meta)),
                        )
                        successFields = mapOf(
                            "responseKey" to key,
                            "semesterIdPresent" to meta.semesterId.isNotBlank(),
                            "idsPresent" to meta.ids.isNotBlank(),
                            "projectIdPresent" to meta.projectId.isNotBlank(),
                            "maxWeek" to meta.maxWeek,
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
                        successFields = scheduleSummaryFields(schedule)
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
                        successFields = scheduleSummaryFields(schedule)
                    }
                }
                PluginLogger.info(
                    "plugin.workflow.step.success",
                    stepFields(execution, step, index) + successFields + mapOf("elapsedMs" to elapsedSince(stepStartedAt)),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                PluginLogger.error(
                    "plugin.workflow.step.failure",
                    stepFields(execution, step, index) + mapOf("elapsedMs" to elapsedSince(stepStartedAt)),
                    error,
                )
                throw error
            }
        }
        return schedule?.let {
            PluginLogger.info(
                "plugin.workflow.success",
                workflowFields(execution) + scheduleSummaryFields(it),
            )
            WorkflowExecutionResult.Success(
                schedule = it,
                uiSchema = execution.bundle.uiSchema,
                timingProfile = execution.bundle.timingProfile,
                recommendations = execution.recommendations,
                messages = execution.messages,
            )
        } ?: run {
            PluginLogger.warn(
                "plugin.workflow.failure",
                workflowFields(execution) + mapOf("failureMessage" to "插件未产出课表"),
            )
            WorkflowExecutionResult.Failure("插件未产出课表")
        }
    }

    private suspend fun performHttpRequest(
        step: WorkflowStepDefinition,
        execution: PendingWorkflowExecution,
        stepIndex: Int,
    ): String = withContext(Dispatchers.IO) {
        val repeat = resolveHttpRepeat(step, execution)
        if (repeat == null) {
            return@withContext executeHttpRequestOnce(step, execution, stepIndex)
        }
        val responses = repeat.values.mapIndexed { offset, value ->
            val repeatedExecution = execution.copy(
                contextData = execution.contextData + (repeat.variable to value.toString()),
            )
            executeHttpRequestOnce(
                step = step,
                execution = repeatedExecution,
                stepIndex = stepIndex,
                extraFields = mapOf(
                    "repeatVariable" to repeat.variable,
                    "repeatValue" to value,
                    "repeatIndex" to offset,
                    "repeatCount" to repeat.values.size,
                ),
            )
        }
        responses.joinToString("\n")
    }

    private fun executeHttpRequestOnce(
        step: WorkflowStepDefinition,
        execution: PendingWorkflowExecution,
        stepIndex: Int,
        extraFields: Map<String, Any?> = emptyMap(),
    ): String {
        val startedAt = System.currentTimeMillis()
        val url = renderTemplate(step.urlTemplate.orEmpty(), execution)
        val method = step.httpMethod?.uppercase().orEmpty().ifBlank { "GET" }
        val baseFields = stepFields(execution, step, stepIndex) + extraFields + mapOf(
            "method" to method,
            "url" to PluginLogger.sanitizeUrl(url),
        )
        PluginLogger.info("plugin.workflow.http.start", baseFields)
        return try {
            require(isHostAllowed(url, execution.bundle.record.allowedHosts)) { "目标域名不在插件白名单中: ${PluginLogger.sanitizeUrl(url)}" }
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
            if (method == "GET" || method == "HEAD") {
                builder.method(method, null)
            } else {
                val body = renderTemplate(step.httpBodyTemplate.orEmpty(), execution)
                val contentType = step.httpContentType?.ifBlank { null } ?: "application/json; charset=utf-8"
                builder.method(method, body.toRequestBody(contentType.toMediaType()))
            }
            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body.string()
                val summaryFields = baseFields + mapOf(
                    "statusCode" to response.code,
                    "requestHeaderCount" to step.httpHeaders.size + if (step.cookieSessionId != null) 1 else 0,
                    "responseHeaderCount" to response.headers.names().size,
                    "responseLength" to responseBody.length,
                    "responseSha256" to PluginLogger.sha256(responseBody),
                    "elapsedMs" to elapsedSince(startedAt),
                )
                if (response.isSuccessful) {
                    PluginLogger.info("plugin.workflow.http.success", summaryFields)
                } else {
                    PluginLogger.warn("plugin.workflow.http.failure_status", summaryFields)
                }
                check(response.isSuccessful) { "HTTP 请求失败: ${response.code}" }
                responseBody
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PluginLogger.error(
                "plugin.workflow.http.failure",
                baseFields + mapOf("elapsedMs" to elapsedSince(startedAt)),
                error,
            )
            throw error
        }
    }

    private fun resolveHttpRepeat(
        step: WorkflowStepDefinition,
        execution: PendingWorkflowExecution,
    ): HttpRepeat? {
        val endTemplate = step.httpRepeatEndTemplate?.takeIf(String::isNotBlank) ?: return null
        val start = step.httpRepeatStart ?: 1
        val end = renderTemplate(endTemplate, execution).trim().toIntOrNull()
            ?: error("HTTP 重复请求结束值无效: $endTemplate")
        require(start >= 1) { "HTTP 重复请求起始值必须大于 0" }
        require(end >= start) { "HTTP 重复请求结束值不能小于起始值" }
        val variable = step.httpRepeatVariable?.trim().orEmpty().ifBlank { "repeat" }
        return HttpRepeat(variable = variable, values = (start..end).toList())
    }

    private data class HttpRepeat(
        val variable: String,
        val values: List<Int>,
    )

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
                PluginLogger.warn(
                    "plugin.workflow.template.unknown_placeholder",
                    workflowFields(execution) + mapOf("expression" to expression),
                )
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
            "packet" -> resolveCapturedPacketExpression(packet, segments.drop(2))
            else -> null
        }
    }

    private fun resolveCapturedPacketExpression(packet: WebSessionPacket, segments: List<String>): String? {
        val packetId = segments.getOrNull(0) ?: return null
        val captured = packet.capturedPackets[packetId] ?: return null
        return when (segments.getOrNull(1)) {
            "finalUrl" -> captured.finalUrl
            "htmlDigest" -> captured.htmlDigest
            "cookie" -> captured.cookies[segments.getOrNull(2)]
            "localStorage" -> captured.localStorageSnapshot[segments.getOrNull(2)]
            "sessionStorage" -> captured.sessionStorageSnapshot[segments.getOrNull(2)]
            "field" -> captured.capturedFields[segments.getOrNull(2)]
            else -> null
        }
    }

    private fun logWorkflowCompleted(
        event: String,
        execution: PendingWorkflowExecution,
        startedAt: Long,
        result: WorkflowExecutionResult,
    ) {
        val fields = workflowFields(execution) + mapOf("elapsedMs" to elapsedSince(startedAt))
        when (result) {
            is WorkflowExecutionResult.Success -> {
                PluginLogger.info(
                    event,
                    fields + mapOf("result" to "success") + scheduleSummaryFields(result.schedule),
                )
            }

            is WorkflowExecutionResult.AwaitingWebSession -> {
                PluginLogger.info(
                    event,
                    fields + mapOf(
                        "result" to "awaiting_web_session",
                        "sessionId" to result.request.sessionId,
                        "startUrl" to PluginLogger.sanitizeUrl(result.request.startUrl),
                        "allowedHostCount" to result.request.allowedHosts.size,
                        "messageCount" to result.messages.size,
                    ),
                )
            }

            is WorkflowExecutionResult.Failure -> {
                PluginLogger.warn(
                    event,
                    fields + mapOf("result" to "failure", "failureMessage" to result.message),
                )
            }
        }
    }

    private fun workflowFields(execution: PendingWorkflowExecution): Map<String, Any?> {
        return mapOf(
            "pluginId" to execution.bundle.record.pluginId,
            "workflowId" to execution.bundle.workflow.workflowId,
            "tokenPrefix" to execution.token.take(8),
            "nextStepIndex" to execution.nextStepIndex,
            "stepCount" to execution.bundle.workflow.steps.size,
        )
    }

    private fun stepFields(
        execution: PendingWorkflowExecution,
        step: WorkflowStepDefinition,
        stepIndex: Int,
    ): Map<String, Any?> {
        return workflowFields(execution) + mapOf(
            "stepId" to step.id,
            "stepType" to step.type,
            "stepIndex" to stepIndex,
        )
    }

    private fun scheduleSummaryFields(schedule: TermSchedule?): Map<String, Any?> {
        return mapOf(
            "dailyScheduleCount" to (schedule?.dailySchedules?.size ?: 0),
            "courseCount" to (schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0),
            "termIdPresent" to !schedule?.termId.isNullOrBlank(),
        )
    }

    private fun elapsedSince(startedAt: Long): Long {
        return System.currentTimeMillis() - startedAt
    }
}
