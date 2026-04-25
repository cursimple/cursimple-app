package com.kebiao.viewer.core.kernel.plugin

import com.kebiao.viewer.core.kernel.model.TermSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncCredentials(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("captcha") val captcha: String? = null,
)

@Serializable
data class PluginContext(
    @SerialName("termId") val termId: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("extra") val extra: Map<String, String> = emptyMap(),
)

data class PluginSyncRequest(
    val pluginId: String,
    val context: PluginContext,
    val credentials: SyncCredentials,
)

@Serializable
data class PluginDescriptor(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("entryAsset") val entryAsset: String,
    @SerialName("description") val description: String = "",
)

interface PluginCatalog {
    suspend fun list(): List<PluginDescriptor>
    suspend fun find(pluginId: String): PluginDescriptor?
    suspend fun loadScript(descriptor: PluginDescriptor): String
}

interface SchedulePluginExecutor {
    suspend fun execute(
        descriptor: PluginDescriptor,
        script: String,
        request: PluginSyncRequest,
    ): TermSchedule
}

sealed interface SyncFailure {
    data object PluginNotFound : SyncFailure
    data class PluginLoadFailed(val message: String) : SyncFailure
    data class ExecutionFailed(val message: String) : SyncFailure
}

sealed interface SyncResult {
    data class Success(val schedule: TermSchedule) : SyncResult
    data class Failure(val reason: SyncFailure) : SyncResult
}

