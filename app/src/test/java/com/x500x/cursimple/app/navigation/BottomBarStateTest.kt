package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.core.data.navigation.bottomBarOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarStateTest {
    private val items = listOf(NavDestination.Schedule, NavDestination.Memos, NavDestination.Components)

    @Test fun `tab in bar is selected`() {
        assertEquals(BarTab.Destination(NavDestination.Memos), selectedBarTab(items, NavDestination.Memos))
        assertEquals(BarTab.Destination(NavDestination.Schedule), selectedBarTab(items, NavDestination.Schedule))
    }

    @Test fun `destination outside bar selects me`() {
        assertEquals(BarTab.Me, selectedBarTab(items, NavDestination.Settings))
    }

    @Test fun `the me page and pages opened from it select me`() {
        assertEquals(BarTab.Me, selectedBarTab(items, null))
        assertEquals(BarTab.Me, selectedBarTab(items.take(2), NavDestination.Components))
    }

    @Test fun `overflow contains settings unless settings is in the bar`() {
        assertTrue(NavDestination.Settings in bottomBarOverflow(items))
        assertFalse(NavDestination.Settings in bottomBarOverflow(items + NavDestination.Settings))
    }
}
