package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

sealed interface WorkflowExecutionResult {
    data class Success(
        val schedule: TermSchedule,
        val uiSchema: PluginUiSchema = PluginUiSchema(),
        val timingProfile: TermTimingProfile? = null,
        val recommendations: List<AlarmRecommendation> = emptyList(),
        val messages: List<String> = emptyList(),
    ) : WorkflowExecutionResult

    data class Failure(val message: String) : WorkflowExecutionResult

    data class NeedsComponents(
        val pluginId: String,
        val components: List<PluginComponentRequirement>,
        val message: String = "插件需要安装组件后才能运行",
    ) : WorkflowExecutionResult

    data class AwaitingWebSession(
        val request: WebSessionRequest,
        val uiSchema: PluginUiSchema = PluginUiSchema(),
        val messages: List<String> = emptyList(),
    ) : WorkflowExecutionResult
}

typealias PluginRuntimeResult = WorkflowExecutionResult
