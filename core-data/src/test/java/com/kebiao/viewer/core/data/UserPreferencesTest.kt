package com.kebiao.viewer.core.data

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {
    @Test
    fun `total schedule display is enabled by default`() {
        assertTrue(UserPreferences().totalScheduleDisplayEnabled)
    }

    @Test
    fun `temporary schedule overrides are empty by default`() {
        assertEquals(emptyList<Any>(), UserPreferences().temporaryScheduleOverrides)
    }
}
