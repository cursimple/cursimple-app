package com.x500x.cursimple.core.plugin.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
