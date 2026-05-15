package com.x500x.cursimple.core.plugin.web

import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.manifest.PluginNetworkCaptureSpec
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebSessionRequest(
    @SerialName("token") val token: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("title") val title: String,
    @SerialName("startUrl") val startUrl: String,
    @SerialName("termId") val termId: String = "",
    @SerialName("allowedHosts") val allowedHosts: List<String>,
    @SerialName("entryScript") val entryScript: String = "",
    @SerialName("permissions") val permissions: List<PluginPermission> = emptyList(),
    @SerialName("limits") val limits: PluginRuntimeLimits = PluginRuntimeLimits(),
    @SerialName("completionUrlContains") val completionUrlContains: String? = null,
    @SerialName("autoNavigateOnUrlContains") val autoNavigateOnUrlContains: String? = null,
    @SerialName("autoNavigateToUrl") val autoNavigateToUrl: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    @SerialName("captureSelectors") val captureSelectors: List<String> = emptyList(),
    @SerialName("capturePackets") val capturePackets: List<WebSessionCaptureSpec> = emptyList(),
    @SerialName("networkCaptures") val networkCaptures: List<PluginNetworkCaptureSpec> = emptyList(),
    @SerialName("extractCookies") val extractCookies: Boolean = true,
    @SerialName("extractLocalStorage") val extractLocalStorage: Boolean = true,
    @SerialName("extractSessionStorage") val extractSessionStorage: Boolean = true,
    @SerialName("extractHtmlDigest") val extractHtmlDigest: Boolean = true,
)

@Serializable
data class WebSessionCaptureSpec(
    @SerialName("id") val id: String,
    @SerialName("required") val required: Boolean = true,
    @SerialName("urlContains") val urlContains: String? = null,
    @SerialName("urlHost") val urlHost: String? = null,
    @SerialName("urlPathContains") val urlPathContains: String? = null,
    @SerialName("captureSelectors") val captureSelectors: List<String> = emptyList(),
    @SerialName("requiredSelectors") val requiredSelectors: List<String> = emptyList(),
    @SerialName("requiredCookies") val requiredCookies: List<String> = emptyList(),
    @SerialName("requiredLocalStorageKeys") val requiredLocalStorageKeys: List<String> = emptyList(),
    @SerialName("requiredSessionStorageKeys") val requiredSessionStorageKeys: List<String> = emptyList(),
    @SerialName("minCookieCount") val minCookieCount: Int = 0,
    @SerialName("minLocalStorageCount") val minLocalStorageCount: Int = 0,
    @SerialName("minSessionStorageCount") val minSessionStorageCount: Int = 0,
)

@Serializable
data class WebCapturedPacket(
    @SerialName("id") val id: String,
    @SerialName("finalUrl") val finalUrl: String,
    @SerialName("cookies") val cookies: Map<String, String> = emptyMap(),
    @SerialName("localStorageSnapshot") val localStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("sessionStorageSnapshot") val sessionStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("htmlDigest") val htmlDigest: String = "",
    @SerialName("capturedFields") val capturedFields: Map<String, String> = emptyMap(),
    @SerialName("timestamp") val timestamp: String,
)

@Serializable
data class WebSessionPacket(
    @SerialName("finalUrl") val finalUrl: String,
    @SerialName("cookies") val cookies: Map<String, String> = emptyMap(),
    @SerialName("localStorageSnapshot") val localStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("sessionStorageSnapshot") val sessionStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("htmlDigest") val htmlDigest: String = "",
    @SerialName("capturedFields") val capturedFields: Map<String, String> = emptyMap(),
    @SerialName("capturedPackets") val capturedPackets: Map<String, WebCapturedPacket> = emptyMap(),
    @SerialName("networkPackets") val networkPackets: Map<String, List<WebNetworkPacket>> = emptyMap(),
    @SerialName("scheduleDraftJson") val scheduleDraftJson: String? = null,
    @SerialName("timestamp") val timestamp: String,
)

@Serializable
data class WebNetworkPacket(
    @SerialName("captureId") val captureId: String,
    @SerialName("url") val url: String,
    @SerialName("method") val method: String,
    @SerialName("requestHeaders") val requestHeaders: Map<String, String> = emptyMap(),
    @SerialName("requestBody") val requestBody: String? = null,
    @SerialName("statusCode") val statusCode: Int? = null,
    @SerialName("reasonPhrase") val reasonPhrase: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("responseHeaders") val responseHeaders: Map<String, String> = emptyMap(),
    @SerialName("responseBody") val responseBody: String? = null,
    @SerialName("timestamp") val timestamp: String,
)
