package com.x500x.cursimple.core.data.navigation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Layout-independent destinations. Ids are public vocabulary for layout components; the enum
 * order is the fixed order used wherever the user has not chosen one.
 */
enum class NavDestination(val id: String, val locked: Boolean, val inDrawer: Boolean) {
    Schedule("schedule", locked = true, inDrawer = true),
    Courses("courses", locked = false, inDrawer = true),
    Memos("memos", locked = false, inDrawer = true),
    Components("components", locked = false, inDrawer = false),
    Plugins("plugins", locked = false, inDrawer = true),
    Reminders("reminders", locked = false, inDrawer = true),
    Settings("settings", locked = true, inDrawer = true),
    About("about", locked = false, inDrawer = true),
    ;

    companion object {
        fun fromId(id: String): NavDestination? = entries.firstOrNull { it.id == id }
    }
}

/** Host sidebar customization. Hidden items stay reachable through the drawer's "more" sheet. */
data class DrawerLayout(
    val order: List<NavDestination>,
    val hidden: Set<NavDestination>,
    val shortcutsVisible: Boolean,
) {
    val visible: List<NavDestination> get() = order.filterNot { it in hidden }
    val overflow: List<NavDestination> get() = order.filter { it in hidden }

    companion object {
        val DEFAULT = DrawerLayout(
            order = NavDestination.entries.filter { it.inDrawer },
            hidden = emptySet(),
            shortcutsVisible = true,
        )
    }
}

const val SHELL_DRAWER = "drawer"
const val SHELL_BOTTOM_BAR = "bottomBar"
val SUPPORTED_SHELLS = listOf(SHELL_DRAWER, SHELL_BOTTOM_BAR)

/** Host-owned bottom bar density; unknown or missing values fall back to [Medium]. */
enum class BottomBarSize(val id: String) {
    Small("small"),
    Medium("medium"),
    Large("large"),
    ;

    companion object {
        fun fromId(id: String?): BottomBarSize = entries.firstOrNull { it.id == id } ?: Medium
    }
}

/** Bottom bar entries including the trailing "me" tab. */
const val MAX_BAR_ITEMS = 5
private const val MIN_BAR_ITEMS = 3

/** The layout a component applied. [items] excludes the host-owned "me" tab. */
data class AppLayoutSelection(
    val ownerId: String,
    val shell: String,
    val items: List<NavDestination>,
)

private val navigationJson = Json { ignoreUnknownKeys = true }

fun encodeDrawerLayout(layout: DrawerLayout): String = buildJsonObject {
    putJsonArray("order") { layout.order.forEach { add(JsonPrimitive(it.id)) } }
    putJsonArray("hidden") { layout.hidden.forEach { add(JsonPrimitive(it.id)) } }
    put("shortcuts", layout.shortcutsVisible)
}.toString()

fun decodeDrawerLayout(raw: String?): DrawerLayout {
    val root = parseObject(raw) ?: return DrawerLayout.DEFAULT
    val drawerItems = DrawerLayout.DEFAULT.order
    val saved = root.destinationIds("order").filter { it in drawerItems }.distinct()
    return DrawerLayout(
        order = saved + drawerItems.filterNot { it in saved },
        hidden = root.destinationIds("hidden").filter { it in drawerItems && !it.locked }.toSet(),
        shortcutsVisible = (root["shortcuts"] as? JsonPrimitive)?.booleanOrNull ?: true,
    )
}

fun encodeAppLayout(selection: AppLayoutSelection?): String? = selection?.let {
    buildJsonObject {
        put("ownerId", it.ownerId)
        put("shell", it.shell)
        putJsonArray("items") { it.items.forEach { item -> add(JsonPrimitive(item.id)) } }
    }.toString()
}

fun decodeAppLayout(raw: String?): AppLayoutSelection? {
    val root = parseObject(raw) ?: return null
    val ownerId = root.string("ownerId")?.takeIf(String::isNotBlank) ?: return null
    val shell = root.string("shell")?.takeIf(String::isNotBlank) ?: return null
    val ids = runCatching { root["items"]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull()
        ?: emptyList()
    val items = ids.map { NavDestination.fromId(it) ?: return null }
    return AppLayoutSelection(ownerId, shell, items)
}

/**
 * Checks the leading bottom bar entries a component asked for; the host appends "me".
 * Messages are shown to the user by the component page.
 */
fun validateBottomBarItems(ids: List<String>): List<NavDestination> {
    require(ids.firstOrNull() == NavDestination.Schedule.id) { "底栏第一个必须是课表" }
    require(ids.size == ids.distinct().size) { "底栏里有重复的项目" }
    val items = ids.map { id -> requireNotNull(NavDestination.fromId(id)) { "不认识的项目：$id" } }
    val total = items.size + 1
    require(total >= MIN_BAR_ITEMS) { "底栏至少需要 $MIN_BAR_ITEMS 个入口" }
    require(total <= MAX_BAR_ITEMS) { "底栏最多放 $MAX_BAR_ITEMS 个入口" }
    return items
}

/** Destinations not on the bar, in fixed order; they are listed on the "me" page. */
fun bottomBarOverflow(items: List<NavDestination>): List<NavDestination> =
    NavDestination.entries.filterNot { it in items }

private fun parseObject(raw: String?): JsonObject? =
    raw?.let { runCatching { navigationJson.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.destinationIds(key: String): List<NavDestination> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull?.let(NavDestination::fromId)
    }
