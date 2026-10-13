package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarSettingsLogicTest {
    @Test fun `locked destinations cannot be hidden`() {
        val layout = DrawerLayout.DEFAULT
        assertEquals(layout, layout.toggled(NavDestination.Schedule))
        assertEquals(layout, layout.toggled(NavDestination.Settings))
    }

    @Test fun `toggling hides and restores`() {
        val hidden = DrawerLayout.DEFAULT.toggled(NavDestination.Courses)
        assertEquals(setOf(NavDestination.Courses), hidden.hidden)
        assertEquals(listOf(NavDestination.Courses), hidden.overflow)
        assertEquals(DrawerLayout.DEFAULT, hidden.toggled(NavDestination.Courses))
    }

    @Test fun `moving reorders without losing items`() {
        val moved = DrawerLayout.DEFAULT.moved(from = 6, to = 0)
        assertEquals(
            listOf("about", "schedule", "courses", "memos", "plugins", "reminders", "settings"),
            moved.order.map { it.id },
        )
        assertEquals(moved, moved.moved(from = 3, to = 99).moved(from = 6, to = 3))
        assertEquals(moved, moved.moved(from = -1, to = 2))
    }
}
