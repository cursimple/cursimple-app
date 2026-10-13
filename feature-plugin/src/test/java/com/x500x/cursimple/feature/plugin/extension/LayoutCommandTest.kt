package com.x500x.cursimple.feature.plugin.extension

import com.x500x.cursimple.core.data.navigation.AppLayoutSelection
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.data.navigation.SHELL_BOTTOM_BAR
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCommandTest {
    private val declared = setOf(SHELL_BOTTOM_BAR)
    private fun payload(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    @Test fun `drawer hands the layout back to the host`() {
        assertNull(applyLayoutRequest("layout", declared, payload("""{"shell":"drawer"}""")))
    }

    @Test fun `valid bottom bar becomes the caller's selection`() {
        assertEquals(
            AppLayoutSelection("layout", SHELL_BOTTOM_BAR, listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)),
            applyLayoutRequest("layout", declared, payload("""{"shell":"bottomBar","items":["schedule","memos","components"]}""")),
        )
    }

    @Test fun `undeclared or unknown shells are rejected`() {
        listOf(
            "layout" to emptySet<String>() to """{"shell":"bottomBar","items":["schedule","memos"]}""",
            "layout" to setOf("rail") to """{"shell":"rail","items":["schedule","memos"]}""",
            "layout" to declared to """{"items":["schedule","memos"]}""",
        ).forEach { (owner, raw) ->
            val failure = runCatching { applyLayoutRequest(owner.first, owner.second, payload(raw)) }.exceptionOrNull()
            assertTrue(raw, failure is IllegalArgumentException)
        }
    }

    @Test fun `invalid items are rejected`() {
        listOf(
            """{"shell":"bottomBar","items":["memos","schedule"]}""",
            """{"shell":"bottomBar","items":"schedule"}""",
            """{"shell":"bottomBar","items":["schedule",1]}""",
            """{"shell":"bottomBar"}""",
        ).forEach { raw ->
            val failure = runCatching { applyLayoutRequest("layout", declared, payload(raw)) }.exceptionOrNull()
            assertTrue(raw, failure is IllegalArgumentException)
        }
    }

    @Test fun `capabilities list every destination and the bar limit`() {
        val caps = Json.parseToJsonElement(layoutCapabilitiesJson()).jsonObject
        assertEquals(
            listOf("schedule", "courses", "memos", "components", "plugins", "reminders", "settings", "about"),
            caps.getValue("destinations").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf("drawer", "bottomBar"), caps.getValue("shells").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(5, caps.getValue("maxBarItems").jsonPrimitive.int)
    }

    @Test fun `current layout reports ownership`() {
        val bar = AppLayoutSelection("layout", SHELL_BOTTOM_BAR, listOf(NavDestination.Schedule, NavDestination.Memos))
        val mine = currentLayoutJson(bar, "layout").jsonObject
        assertEquals("bottomBar", mine.getValue("shell").jsonPrimitive.content)
        assertEquals(listOf("schedule", "memos"), mine.getValue("items").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", mine.getValue("ownedByCaller").jsonPrimitive.content)
        assertEquals("false", currentLayoutJson(bar, "other").jsonObject.getValue("ownedByCaller").jsonPrimitive.content)
        val drawer = currentLayoutJson(null, "layout").jsonObject
        assertEquals("drawer", drawer.getValue("shell").jsonPrimitive.content)
        assertEquals(0, drawer.getValue("items").jsonArray.size)
    }
}
