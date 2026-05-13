package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPreferencesTest {
    @Test
    fun `widget theme defaults to green theme background`() {
        val prefs = WidgetThemePreferences()

        assertEquals(ThemeAccent.Green, prefs.themeAccent)
        assertEquals(WidgetBackgroundMode.Theme, prefs.backgroundMode)
        assertNull(prefs.backgroundImageUri)
        assertEquals(false, prefs.openAppOnDoubleClickEnabled)
    }

    @Test
    fun `widget theme and image background are mutually exclusive`() {
        val image = WidgetThemePreferences()
            .selectBackgroundImage("content://image")

        assertEquals(WidgetBackgroundMode.Image, image.backgroundMode)
        assertEquals("content://image", image.backgroundImageUri)

        val theme = image.selectTheme(ThemeAccent.Purple)

        assertEquals(ThemeAccent.Purple, theme.themeAccent)
        assertEquals(WidgetBackgroundMode.Theme, theme.backgroundMode)
        assertNull(theme.backgroundImageUri)
        assertEquals(false, theme.openAppOnDoubleClickEnabled)
    }

    @Test
    fun `widget open app setting survives theme and image switches`() {
        val prefs = WidgetThemePreferences(openAppOnDoubleClickEnabled = true)
        val image = prefs.selectBackgroundImage("content://image")
        val theme = image.selectTheme(ThemeAccent.Blue)

        assertEquals(true, image.openAppOnDoubleClickEnabled)
        assertEquals(true, theme.openAppOnDoubleClickEnabled)
    }
}
