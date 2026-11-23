package com.x500x.cursimple.core.data.widget

import com.x500x.cursimple.core.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeModeSyncTest {
    @Test
    fun appThemeModeIsUsedWhenWidgetFollowsTheApp() {
        assertEquals(WidgetThemeMode.Light, WidgetThemeMode.followingApp(ThemeMode.Light))
        assertEquals(WidgetThemeMode.Dark, WidgetThemeMode.followingApp(ThemeMode.Dark))
        assertEquals(WidgetThemeMode.System, WidgetThemeMode.followingApp(ThemeMode.System))
    }
}
