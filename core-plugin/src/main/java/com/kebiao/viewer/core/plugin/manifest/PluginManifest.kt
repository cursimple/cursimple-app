package com.kebiao.viewer.core.plugin.manifest

import com.kebiao.viewer.core.plugin.PluginApiVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String,
    @SerialName("version") val version: String,
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("description") val description: String = "",
    @SerialName("minHostVersion") val minHostVersion: String = "0.1.0",
    @SerialName("targetApiVersion") val targetApiVersion: Int = PluginApiVersion.CURRENT,
    @SerialName("entryWorkflow") val entryWorkflow: String = "sync-schedule",
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("supportUrl") val supportUrl: String? = null,
    @SerialName("declaredPermissions") val declaredPermissions: List<PluginPermission> = emptyList(),
    @SerialName("allowedHosts") val allowedHosts: List<String> = emptyList(),
    @SerialName("dataPackVersion") val dataPackVersion: String = "1",
)
