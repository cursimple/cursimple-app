package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.plugin.manifest.PluginExtensionSetting
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalTime

/** UI may modify declared settings only; platform authentication belongs to the component. */
internal fun updateComponentSettings(data: ExtensionData, manifest: PluginManifest, values: JsonObject): ExtensionData {
    val declarations = manifest.extension?.settings.orEmpty().associateBy { it.key }
    values.forEach { (key, value) ->
        val setting = requireNotNull(declarations[key]) { "组件未声明设置项：$key" }
        val primitive = value as? JsonPrimitive ?: error("设置项格式错误：$key")
        val valid = when (setting.type) {
            PluginExtensionSetting.TYPE_SWITCH -> !primitive.isString && primitive.booleanOrNull != null
            PluginExtensionSetting.TYPE_SELECT -> primitive.isString && setting.options.any { it.value == primitive.content }
            PluginExtensionSetting.TYPE_NUMBER -> !primitive.isString && primitive.doubleOrNull?.let { number ->
                number.isFinite() && (setting.min?.let { number >= it } ?: true) && (setting.max?.let { number <= it } ?: true)
            } == true
            PluginExtensionSetting.TYPE_TEXT -> primitive.isString && primitive.content.length <= 4096
            else -> false
        }
        require(valid) { "设置项值无效：$key" }
    }
    val effective = manifest.extension?.let { ExtensionUrls.effectiveSettings(it, data.settings) }.orEmpty()
    val relogin = values.any { (key, value) -> declarations[key]?.requiresRelogin == true && effective[key] != value }
    val updated = data.copy(settings = data.settings + values)
    return if (relogin) updated.copy(
        sessionRevision = data.sessionRevision + 1,
        loginState = if (data.loginState == ExtensionLoginState.LoggedIn || data.loginState == ExtensionLoginState.Expired) ExtensionLoginState.Expired else data.loginState,
        account = null, state = emptyMap(), items = emptyList(), baselineReady = false,
        remindedKeys = emptySet(), expiredNotified = false,
        ignoredItemIds = emptySet(), restoredItemIds = emptySet(),
    ) else updated
}

internal fun decodeHostSettings(values: JsonElement): ExtensionHostSettings {
    val settings = extensionJson.decodeFromJsonElement(ExtensionHostSettings.serializer(), values)
    require(settings.dueReminderHours in 0..168) { "提醒时间无效" }
    require(settings.syncIntervalMinutes == null || settings.syncIntervalMinutes in 30..10080) { "同步间隔无效" }
    require(settings.feed.historyDays in 0..36500 && settings.schedule.historyDays in 0..36500) { "历史范围无效" }
    require(settings.schedule.durationMinutes in 1..1440) { "事务时长无效" }
    LocalTime.parse(settings.schedule.defaultStartTime)
    require(settings.schedule.typeRules.values.all { it.dayOffset in -365..365 }) { "日期偏移无效" }
    return settings
}

internal fun acceptComponentLogin(data: ExtensionData, result: JsonObject): ExtensionData {
    require((result["loggedIn"] as? JsonPrimitive)?.booleanOrNull == true) { "登录状态尚未确认" }
    val account = (result["account"] as? JsonObject)?.let { extensionJson.decodeFromJsonElement(ExtensionAccount.serializer(), it) }
    val changed = account != null && account.id != data.account?.id
    return data.copy(
        sessionRevision = data.sessionRevision + 1,
        loginState = ExtensionLoginState.LoggedIn,
        account = account ?: data.account,
        items = if (changed) emptyList() else data.items,
        ignoredItemIds = if (changed) emptySet() else data.ignoredItemIds,
        restoredItemIds = if (changed) emptySet() else data.restoredItemIds,
        state = if (changed) emptyMap() else data.state,
        baselineReady = if (changed) false else data.baselineReady,
        remindedKeys = if (changed) emptySet() else data.remindedKeys,
        lastSyncAt = if (changed) null else data.lastSyncAt,
        lastAttemptAt = if (changed) null else data.lastAttemptAt,
        lastMessage = if (changed) null else data.lastMessage,
        expiredNotified = false, lastError = null,
    )
}

internal fun loggedOutComponent(data: ExtensionData): ExtensionData = data.copy(
    sessionRevision = data.sessionRevision + 1,
    loginState = ExtensionLoginState.LoggedOut, account = null, items = emptyList(),
    state = emptyMap(), baselineReady = false, remindedKeys = emptySet(), expiredNotified = false,
    ignoredItemIds = emptySet(), restoredItemIds = emptySet(),
)

internal fun componentUiJsonForScript(value: String): String = value.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
