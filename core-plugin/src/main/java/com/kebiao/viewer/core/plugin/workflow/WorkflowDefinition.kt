package com.kebiao.viewer.core.plugin.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowDefinition(
    @SerialName("workflowId") val workflowId: String = "sync-schedule",
    @SerialName("steps") val steps: List<WorkflowStepDefinition> = emptyList(),
)

@Serializable
data class WorkflowStepDefinition(
    @SerialName("id") val id: String,
    @SerialName("type") val type: WorkflowStepType,
    @SerialName("message") val message: String? = null,
    @SerialName("uiAsset") val uiAsset: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("urlTemplate") val urlTemplate: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("completionUrlContains") val completionUrlContains: String? = null,
    @SerialName("captureSelectors") val captureSelectors: List<String> = emptyList(),
    @SerialName("extractCookies") val extractCookies: Boolean = true,
    @SerialName("extractLocalStorage") val extractLocalStorage: Boolean = true,
    @SerialName("extractSessionStorage") val extractSessionStorage: Boolean = true,
    @SerialName("extractHtmlDigest") val extractHtmlDigest: Boolean = true,
    @SerialName("httpMethod") val httpMethod: String? = null,
    @SerialName("httpHeaders") val httpHeaders: Map<String, String> = emptyMap(),
    @SerialName("httpBodyTemplate") val httpBodyTemplate: String? = null,
    @SerialName("responseKey") val responseKey: String? = null,
    @SerialName("scheduleAsset") val scheduleAsset: String? = null,
    @SerialName("scheduleContextKey") val scheduleContextKey: String? = null,
    @SerialName("updatedAtNow") val updatedAtNow: Boolean = false,
    @SerialName("recommendedAdvanceMinutes") val recommendedAdvanceMinutes: Int? = null,
)

@Serializable
enum class WorkflowStepType {
    @SerialName("ui_emit")
    UiEmit,

    @SerialName("message")
    Message,

    @SerialName("web_session")
    WebSession,

    @SerialName("http_request")
    HttpRequest,

    @SerialName("schedule_emit_static")
    ScheduleEmitStatic,

    @SerialName("alarm_plan_emit")
    AlarmPlanEmit,
}

enum class WorkflowState {
    Installed,
    Ready,
    Running,
    WaitingUserInput,
    WaitingWebSession,
    WaitingNetwork,
    WaitingSystemAction,
    Succeeded,
    Failed,
    Suspended,
}
