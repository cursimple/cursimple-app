package com.x500x.cursimple.core.plugin.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginManifest(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("publisher") val publisher: String = "",
    @SerialName("version") val version: String,
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("apiVersion") val apiVersion: Int? = null,
    @SerialName("entry") val entry: String,
    @SerialName("permissions") val permissions: List<PluginPermission> = emptyList(),
    @SerialName("webEngine") val webEngine: PluginWebEngineRequirement = PluginWebEngineRequirement(),
    @SerialName("components") val components: List<PluginComponentRequirement> = emptyList(),
    @SerialName("limits") val limits: PluginRuntimeLimits = PluginRuntimeLimits(),
    @SerialName("allowedHosts") val allowedHosts: List<String> = emptyList(),
    @SerialName("startUrl") val startUrl: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    @SerialName("webSession") val webSession: PluginWebSessionOptions = PluginWebSessionOptions(),
    @SerialName("networkCaptures") val networkCaptures: List<PluginNetworkCaptureSpec> = emptyList(),
    @SerialName("description") val description: String = "",
    @SerialName("minHostVersion") val minHostVersion: String = "0.1.0",
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("supportUrl") val supportUrl: String? = null,
    /**
     * [KIND_SCHEDULE] imports timetables; [KIND_EXTENSION] provides persistent component
     * functions.
     */
    @SerialName("kind") val kind: String = KIND_SCHEDULE,
    @SerialName("extension") val extension: PluginExtensionSpec? = null,
) {
    val pluginId: String get() = id

    val isExtension: Boolean get() = kind == KIND_EXTENSION

    companion object {
        const val KIND_SCHEDULE = "schedule"
        const val KIND_EXTENSION = "extension"
    }
}

/**
 * Extensions export checkLogin and sync for background feed production. Expand settings
 * placeholders only into allowed hosts.
 */
@Serializable
data class PluginExtensionSpec(
    @SerialName("title") val title: String = "",
    @SerialName("loginUrl") val loginUrl: String,
    /** Lightweight same-origin page for background execution. */
    @SerialName("runUrl") val runUrl: String,
    @SerialName("syncIntervalMinutes") val syncIntervalMinutes: Int = 60,
    /** Optional CSS viewport width for desktop-only login layouts. */
    @SerialName("loginViewportWidth") val loginViewportWidth: Int? = null,
    @SerialName("feedTypes") val feedTypes: List<PluginFeedTypeSpec> = emptyList(),
    @SerialName("settings") val settings: List<PluginExtensionSetting> = emptyList(),
    @SerialName("ui") val ui: PluginExtensionUiSpec? = null,
    val notificationReceiver: Boolean = false,
    val widgets: List<PluginWidgetSpec> = emptyList(),
)

/** Owned HTML widgets; the host provides data, rendering and declared navigation actions. */
@Serializable
data class PluginWidgetSpec(
    val id: String,
    val title: String,
    val description: String = "",
    val entry: String,
    val columns: Int = 4,
    val rows: Int = 2,
)

@Serializable
data class PluginExtensionUiSpec(
    @SerialName("entry") val entry: String = "ui/feed.html",
    @SerialName("type") val type: String = "html",
    @SerialName("settingsEntry") val settingsEntry: String? = null,
    @SerialName("loginEntry") val loginEntry: String? = null,
) {
    fun entryFor(page: PluginExtensionUiPage): String? = when (page) {
        PluginExtensionUiPage.Feed -> entry
        PluginExtensionUiPage.Settings -> settingsEntry
        PluginExtensionUiPage.Login -> loginEntry
    }
}

enum class PluginExtensionUiPage { Feed, Settings, Login }

@Serializable
data class PluginFeedTypeSpec(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("color") val color: String? = null,
    /**
     * Task or notice semantics belong to the component; infer from timing only when undeclared.
     */
    @SerialName("kind") val kind: String? = null,
)

@Serializable
data class PluginExtensionSetting(
    @SerialName("key") val key: String,
    @SerialName("type") val type: String,
    @SerialName("label") val label: String,
    @SerialName("description") val description: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<PluginExtensionSettingOption> = emptyList(),
    @SerialName("min") val min: Double? = null,
    @SerialName("max") val max: Double? = null,
    @SerialName("requiresRelogin") val requiresRelogin: Boolean = false,
) {
    companion object {
        const val TYPE_SWITCH = "switch"
        const val TYPE_SELECT = "select"
        const val TYPE_NUMBER = "number"
        const val TYPE_TEXT = "text"
    }
}

@Serializable
data class PluginExtensionSettingOption(
    @SerialName("value") val value: String,
    @SerialName("label") val label: String,
)

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
data class PluginWebSessionOptions(
    @SerialName("completionStableDelayMs") val completionStableDelayMs: Long = 1_200,
    @SerialName("autoCompleteOnScheduleDraft") val autoCompleteOnScheduleDraft: Boolean = true,
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
