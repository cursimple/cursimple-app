package com.x500x.cursimple.core.plugin.workflow

import com.x500x.cursimple.core.plugin.web.WebSessionCaptureSpec
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
    @SerialName("autoNavigateOnUrlContains") val autoNavigateOnUrlContains: String? = null,
    @SerialName("autoNavigateToUrl") val autoNavigateToUrl: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    @SerialName("captureSelectors") val captureSelectors: List<String> = emptyList(),
    @SerialName("capturePackets") val capturePackets: List<WebSessionCaptureSpec> = emptyList(),
    @SerialName("extractCookies") val extractCookies: Boolean = true,
    @SerialName("extractLocalStorage") val extractLocalStorage: Boolean = true,
    @SerialName("extractSessionStorage") val extractSessionStorage: Boolean = true,
    @SerialName("extractHtmlDigest") val extractHtmlDigest: Boolean = true,
    @SerialName("httpMethod") val httpMethod: String? = null,
    @SerialName("httpContentType") val httpContentType: String? = null,
    @SerialName("httpHeaders") val httpHeaders: Map<String, String> = emptyMap(),
    @SerialName("httpBodyTemplate") val httpBodyTemplate: String? = null,
    @SerialName("httpRepeatStart") val httpRepeatStart: Int? = null,
    @SerialName("httpRepeatEndTemplate") val httpRepeatEndTemplate: String? = null,
    @SerialName("httpRepeatVariable") val httpRepeatVariable: String? = null,
    @SerialName("httpRetryBodyContains") val httpRetryBodyContains: String? = null,
    @SerialName("httpRetryDelayMillis") val httpRetryDelayMillis: Long? = null,
    @SerialName("httpFreshConnection") val httpFreshConnection: Boolean = false,
    @SerialName("cookieSessionId") val cookieSessionId: String? = null,
    @SerialName("sourceContextKey") val sourceContextKey: String? = null,
    @SerialName("responseKey") val responseKey: String? = null,
    @SerialName("scheduleAsset") val scheduleAsset: String? = null,
    @SerialName("scheduleContextKey") val scheduleContextKey: String? = null,
    @SerialName("metaContextKey") val metaContextKey: String? = null,
    @SerialName("detailContextKey") val detailContextKey: String? = null,
    @SerialName("termIdTemplate") val termIdTemplate: String? = null,
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

    @SerialName("eams_extract_meta")
    EamsExtractMeta,

    @SerialName("schedule_emit_static")
    ScheduleEmitStatic,

    @SerialName("schedule_emit_eams")
    ScheduleEmitEams,

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
