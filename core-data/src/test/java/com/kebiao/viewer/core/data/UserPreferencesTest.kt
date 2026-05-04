package com.kebiao.viewer.core.data

import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {
    @Test
    fun `total schedule display is enabled by default`() {
        assertTrue(UserPreferences().totalScheduleDisplayEnabled)
    }
}
