package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.data.navigation.AppLayoutSelection
import com.x500x.cursimple.core.data.navigation.MAX_BAR_ITEMS
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.data.navigation.SHELL_BOTTOM_BAR
import com.x500x.cursimple.core.data.navigation.SHELL_DRAWER
import com.x500x.cursimple.core.data.navigation.SUPPORTED_SHELLS
import com.x500x.cursimple.core.data.navigation.validateBottomBarItems
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** `CurSimpleComponent.capabilities.layouts` for components that provide app layouts. */
internal fun layoutCapabilitiesJson(): String = buildJsonObject {
    putJsonArray("shells") { SUPPORTED_SHELLS.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("destinations") { NavDestination.entries.forEach { add(JsonPrimitive(it.id)) } }
    put("maxBarItems", MAX_BAR_ITEMS)
}.toString()

/**
 * Validates a `layout.apply` payload from [ownerId]. Null hands the layout back to the host drawer;
 * failures throw [IllegalArgumentException] with a message the component page can show.
 */
fun applyLayoutRequest(ownerId: String, declared: Set<String>, payload: JsonObject): AppLayoutSelection? {
    val shell = (payload["shell"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (shell == SHELL_DRAWER) return null
    require(shell == SHELL_BOTTOM_BAR) { "当前课简不支持这种布局" }
    require(shell in declared) { "组件未声明这种布局" }
    val ids = (payload["items"] as? JsonArray)?.map { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("底栏项目无效")
    } ?: throw IllegalArgumentException("缺少底栏项目")
    return AppLayoutSelection(ownerId, shell, validateBottomBarItems(ids))
}

/** `layout.current` reply; [effective] is the layout actually drawn, null for the drawer. */
fun currentLayoutJson(effective: AppLayoutSelection?, callerId: String): JsonElement = buildJsonObject {
    put("shell", effective?.shell ?: SHELL_DRAWER)
    putJsonArray("items") { effective?.items?.forEach { add(JsonPrimitive(it.id)) } }
    put("ownedByCaller", effective?.ownerId == callerId)
}
