package com.x500x.cursimple.app.reminder

import org.junit.Assert.*
import org.junit.Test

class ReminderGuardRegistrationTest {
    @Test fun expiredOrPreRebootDeadlinesCannotMasqueradeAsScheduledAlarms() {
        assertFalse(guardRegistrationFresh(0, 100, 900))
        assertFalse(guardRegistrationFresh(100, 100, 900))
        assertFalse(guardRegistrationFresh(99, 100, 900))
        assertFalse(guardRegistrationFresh(10001, 100, 900))
        assertTrue(guardRegistrationFresh(1000, 100, 900))
    }
}
