package com.kebiao.viewer.core.data

import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
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

    @Test
    fun `alarm settings default to app managed clock`() {
        val prefs = UserPreferences()

        assertEquals(ReminderAlarmBackend.AppAlarmClock, prefs.alarmBackend)
        assertEquals(60, prefs.alarmRingDurationSeconds)
        assertEquals(120, prefs.alarmRepeatIntervalSeconds)
        assertEquals(1, prefs.alarmRepeatCount)
        assertEquals(0L, prefs.lastAlarmPollAtMillis)
    }
}
