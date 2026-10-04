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
    /** 插件种类：[KIND_SCHEDULE] 导课，[KIND_EXTENSION] 常驻的扩展组件（见 [PluginExtensionSpec]） */
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
 * 扩展组件：装好后常驻，登录一次，之后在后台定时跑入口脚本，把拿到的条目（作业、公告……）交给宿主。
 *
 * 和导课插件不同，它不产出课表，也不需要用户守着网页；宿主负责通知、侧边栏日历和写进课表事务。
 * 入口脚本导出 `checkLogin(ctx)` 与 `sync(ctx)` 两个函数，ctx 见 docs/plugin-system.md「扩展组件」。
 *
 * [loginUrl]、[runUrl] 里可以写 `{settings.KEY}`，按用户在 [settings] 里选的值替换，
 * 替换后的地址必须落在 allowedHosts 里。
 */
@Serializable
data class PluginExtensionSpec(
    /** 侧边栏与通知里用的短名，缺省用插件名 */
    @SerialName("title") val title: String = "",
    /** 登录页：用户在这里登录，宿主在页面上反复调 checkLogin 判断登上没有 */
    @SerialName("loginUrl") val loginUrl: String,
    /** 后台同步时打开的页面：只要是那个站点的同源页面就行，越轻越好 */
    @SerialName("runUrl") val runUrl: String,
    @SerialName("syncIntervalMinutes") val syncIntervalMinutes: Int = 60,
    /**
     * 登录页按这个宽度（CSS 像素）排版再缩放到屏幕上。给只做了电脑版的登录页用：
     * 有的站点登录页是 950px 宽的桌面布局，却把 viewport 锁成手机宽度，登录框就跑到屏幕外面。
     */
    @SerialName("loginViewportWidth") val loginViewportWidth: Int? = null,
    @SerialName("feedTypes") val feedTypes: List<PluginFeedTypeSpec> = emptyList(),
    @SerialName("settings") val settings: List<PluginExtensionSetting> = emptyList(),
    /** 组件自带的界面入口；存在时由宿主通用容器加载，不把页面写死在 APK。 */
    @SerialName("ui") val ui: PluginExtensionUiSpec? = null,
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

/** 条目的一种类型（作业、考试、公告……），日历里按 [color] 上色 */
@Serializable
data class PluginFeedTypeSpec(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    /** `#RRGGBB`；认不出就按类型名从调色板里挑 */
    @SerialName("color") val color: String? = null,
    /**
     * 这类内容的语义：`task`（有截止，会算逾期、能写进课表）或 `notice`（读完即止）。
     * 组件自己声明，宿主不去猜类型名；不填时按有没有时间推断。
     */
    @SerialName("kind") val kind: String? = null,
)

/** 组件自己的一项设置，宿主按 [type] 画成开关 / 单选 / 数字 / 文本框 */
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
    /** 改了这一项登录就作废（比如换了站点），宿主会要求重新登录 */
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
