package com.x500x.cursimple.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReminderPreferencesTest {
    @Test
    fun defaultsAreEnabledTwentyMinutes() {
        val value = EventReminderPreferences()
        assertTrue(value.enabled)
        assertEquals(20, value.advanceMinutes)
    }

    @Test
    fun advanceMinutesAreClampedToSupportedRange() {
        assertEquals(0, EventReminderPreferences(advanceMinutes = -5).advanceMinutes)
        assertEquals(60, EventReminderPreferences(advanceMinutes = 90).advanceMinutes)
    }
}
