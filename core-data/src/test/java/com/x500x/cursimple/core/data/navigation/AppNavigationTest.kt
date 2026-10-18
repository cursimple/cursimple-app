package com.x500x.cursimple.core.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test fun `default drawer lists every drawer destination in order`() {
        assertEquals(
            listOf("schedule", "courses", "memos", "plugins", "reminders", "settings", "about"),
            DrawerLayout.DEFAULT.visible.map { it.id },
        )
        assertTrue(DrawerLayout.DEFAULT.overflow.isEmpty())
        assertTrue(DrawerLayout.DEFAULT.shortcutsVisible)
    }

    @Test fun `decode drops unknown ids, unhides locked items and appends missing ones`() {
        val layout = decodeDrawerLayout(
            """{"order":["about","x","schedule","about","components"],"hidden":["settings","memos","x"],"shortcuts":false}""",
        )

        assertEquals(
            listOf("about", "schedule", "courses", "memos", "plugins", "reminders", "settings"),
            layout.order.map { it.id },
        )
        assertEquals(setOf(NavDestination.Memos), layout.hidden)
        assertEquals(false, layout.shortcutsVisible)
        assertEquals(listOf(NavDestination.Memos), layout.overflow)
    }

    @Test fun `decode of garbage returns default`() {
        assertEquals(DrawerLayout.DEFAULT, decodeDrawerLayout("{"))
        assertEquals(DrawerLayout.DEFAULT, decodeDrawerLayout(null))
        assertEquals(DrawerLayout.DEFAULT, decodeDrawerLayout("[1,2]"))
    }

    @Test fun `drawer layout round trips`() {
        val layout = DrawerLayout(
            order = DrawerLayout.DEFAULT.order.reversed(),
            hidden = setOf(NavDestination.Courses, NavDestination.About),
            shortcutsVisible = false,
        )

        assertEquals(layout, decodeDrawerLayout(encodeDrawerLayout(layout)))
    }

    @Test fun `bottom bar items must start with schedule and fit beside me`() {
        assertEquals(
            listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components),
            validateBottomBarItems(listOf("schedule", "memos", "components")),
        )
        listOf(
            emptyList(),
            listOf("memos"),
            listOf("schedule"),
            listOf("schedule", "memos", "memos"),
            listOf("schedule", "me"),
            listOf("schedule", "a", "b", "c", "d"),
            listOf("schedule", "memos", "courses", "plugins", "about"),
        ).forEach { ids ->
            val failure = runCatching { validateBottomBarItems(ids) }.exceptionOrNull()
            assertTrue("$ids should be rejected", failure is IllegalArgumentException)
        }
    }

    @Test fun `overflow keeps fixed order`() {
        assertEquals(
            listOf("courses", "plugins", "reminders", "settings", "about"),
            bottomBarOverflow(listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)).map { it.id },
        )
    }

    @Test fun `app layout round trips and rejects garbage`() {
        val selection = AppLayoutSelection(
            ownerId = "owner",
            shell = SHELL_BOTTOM_BAR,
            items = listOf(NavDestination.Schedule, NavDestination.Memos),
        )

        assertEquals(selection, decodeAppLayout(encodeAppLayout(selection)))
        assertNull(encodeAppLayout(null))
        assertNull(decodeAppLayout(null))
        assertNull(decodeAppLayout("nope"))
        assertNull(decodeAppLayout("""{"ownerId":"owner","shell":"bottomBar","items":["schedule","nope"]}"""))
    }

    @Test fun `bottom bar size defaults to medium and reads saved ids`() {
        assertEquals(BottomBarSize.Medium, BottomBarSize.fromId(null))
        assertEquals(BottomBarSize.Medium, BottomBarSize.fromId("huge"))
        BottomBarSize.entries.forEach { assertEquals(it, BottomBarSize.fromId(it.id)) }
    }
}
