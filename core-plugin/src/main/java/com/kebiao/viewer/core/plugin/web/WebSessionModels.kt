package com.kebiao.viewer.core.plugin.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebSessionRequest(
    @SerialName("token") val token: String,
    @SerialName("pluginId") val pluginId: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("title") val title: String,
    @SerialName("startUrl") val startUrl: String,
    @SerialName("allowedHosts") val allowedHosts: List<String>,
    @SerialName("completionUrlContains") val completionUrlContains: String? = null,
    @SerialName("captureSelectors") val captureSelectors: List<String> = emptyList(),
    @SerialName("extractCookies") val extractCookies: Boolean = true,
    @SerialName("extractLocalStorage") val extractLocalStorage: Boolean = true,
    @SerialName("extractSessionStorage") val extractSessionStorage: Boolean = true,
    @SerialName("extractHtmlDigest") val extractHtmlDigest: Boolean = true,
)

@Serializable
data class WebSessionPacket(
    @SerialName("finalUrl") val finalUrl: String,
    @SerialName("cookies") val cookies: Map<String, String> = emptyMap(),
    @SerialName("localStorageSnapshot") val localStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("sessionStorageSnapshot") val sessionStorageSnapshot: Map<String, String> = emptyMap(),
    @SerialName("htmlDigest") val htmlDigest: String = "",
    @SerialName("capturedFields") val capturedFields: Map<String, String> = emptyMap(),
    @SerialName("timestamp") val timestamp: String,
)
