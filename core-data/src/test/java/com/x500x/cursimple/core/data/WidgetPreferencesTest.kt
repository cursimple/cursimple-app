package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.data.widget.WidgetBackgroundMode
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.resolveAccent
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
        assertEquals(true, prefs.openAppOnDoubleClickEnabled)
        assertEquals(true, prefs.followsAppThemeAccent)
    }

    @Test
    fun `widget follows the app accent until one is picked for it`() {
        val following = WidgetThemePreferences()

        assertEquals(ThemeAccent.Blue, following.resolveAccent(ThemeAccent.Blue).themeAccent)
    }

    @Test
    fun `a widget accent picked by hand wins over the app accent`() {
        val picked = WidgetThemePreferences(
            themeAccent = ThemeAccent.Pink,
            followsAppThemeAccent = false,
        )

        assertEquals(ThemeAccent.Pink, picked.resolveAccent(ThemeAccent.Blue).themeAccent)
    }
}
