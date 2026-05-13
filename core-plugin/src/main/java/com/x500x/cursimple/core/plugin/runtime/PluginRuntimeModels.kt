package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import com.x500x.cursimple.core.plugin.workflow.WorkflowDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class InstalledPluginBundle(
    val record: InstalledPluginRecord,
    val workflow: WorkflowDefinition,
    val uiSchema: PluginUiSchema,
    val timingProfile: TermTimingProfile?,
)

@Serializable
data class PluginSyncInput(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("termId") val termId: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("extraInputs") val extraInputs: Map<String, String> = emptyMap(),
)

data class AlarmRecommendation(
    val pluginId: String,
    val advanceMinutes: Int,
    val note: String,
)

sealed interface WorkflowExecutionResult {
    data class Success(
        val schedule: TermSchedule,
        val uiSchema: PluginUiSchema,
        val timingProfile: TermTimingProfile?,
        val recommendations: List<AlarmRecommendation>,
        val messages: List<String>,
    ) : WorkflowExecutionResult

    data class AwaitingWebSession(
        val request: WebSessionRequest,
        val uiSchema: PluginUiSchema,
        val messages: List<String>,
    ) : WorkflowExecutionResult

    data class Failure(val message: String) : WorkflowExecutionResult
}

data class PendingWorkflowExecution(
    val token: String,
    val bundle: InstalledPluginBundle,
    val input: PluginSyncInput,
    val nextStepIndex: Int,
    val contextData: Map<String, String>,
    val webPackets: Map<String, WebSessionPacket>,
    val recommendations: List<AlarmRecommendation>,
    val messages: List<String>,
)
