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
}
