package com.x500x.cursimple.core.plugin.manifest

import com.x500x.cursimple.core.plugin.PluginApiVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String = "",
    @SerialName("version") val version: String,
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("apiVersion") val apiVersion: Int = PluginApiVersion.CURRENT,
    @SerialName("entry") val entry: String,
    @SerialName("permissions") val permissions: List<PluginPermission> = emptyList(),
    @SerialName("webEngine") val webEngine: PluginWebEngineRequirement = PluginWebEngineRequirement(),
    @SerialName("components") val components: List<PluginComponentRequirement> = emptyList(),
    @SerialName("limits") val limits: PluginRuntimeLimits = PluginRuntimeLimits(),
    @SerialName("allowedHosts") val allowedHosts: List<String> = emptyList(),
    @SerialName("networkCaptures") val networkCaptures: List<PluginNetworkCaptureSpec> = emptyList(),
    @SerialName("description") val description: String = "",
    @SerialName("minHostVersion") val minHostVersion: String = "0.1.0",
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("supportUrl") val supportUrl: String? = null,
) {
    val pluginId: String get() = id
    val targetApiVersion: Int get() = apiVersion
    val declaredPermissions: List<PluginPermission> get() = permissions
}

@Serializable
data class PluginWebEngineRequirement(
    @SerialName("preferred") val preferred: String = ENGINE_SYSTEM_WEBVIEW,
    @SerialName("allowChromium") val allowChromium: Boolean = false,
    @SerialName("chromiumComponent") val chromiumComponent: String? = null,
) {
    companion object {
        const val ENGINE_SYSTEM_WEBVIEW = "system_webview"
        const val ENGINE_CHROMIUM = "chromium"
    }
}

@Serializable
data class PluginComponentRequirement(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("required") val required: Boolean = true,
    @SerialName("version") val version: String? = null,
    @SerialName("abi") val abi: String? = null,
)

@Serializable
data class PluginNetworkCaptureSpec(
    @SerialName("id") val id: String,
    @SerialName("required") val required: Boolean = false,
    @SerialName("method") val method: String? = null,
    @SerialName("urlContains") val urlContains: String? = null,
    @SerialName("urlHost") val urlHost: String? = null,
    @SerialName("urlPathContains") val urlPathContains: String? = null,
    @SerialName("requestHeaders") val requestHeaders: List<String> = emptyList(),
    @SerialName("responseHeaders") val responseHeaders: List<String> = emptyList(),
    @SerialName("captureRequestBody") val captureRequestBody: Boolean = false,
    @SerialName("captureResponseBody") val captureResponseBody: Boolean = false,
    @SerialName("responseBodyMimeTypes") val responseBodyMimeTypes: List<String> = emptyList(),
    @SerialName("maxBodyBytes") val maxBodyBytes: Int = 65_536,
    @SerialName("maxPackets") val maxPackets: Int = 8,
)

@Serializable
data class PluginRuntimeLimits(
    @SerialName("timeoutMs") val timeoutMs: Long = 60_000,
    @SerialName("maxCourses") val maxCourses: Int = 1_000,
    @SerialName("maxStorageBytes") val maxStorageBytes: Long = 1_048_576,
    @SerialName("maxCapturedTextBytes") val maxCapturedTextBytes: Int = 524_288,
    @SerialName("maxOutputBytes") val maxOutputBytes: Int = 1_048_576,
)
