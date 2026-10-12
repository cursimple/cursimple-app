package com.x500x.cursimple.app.notice

import com.x500x.cursimple.app.notice.StatusBarChipSupport.Level
import org.junit.Assert.*
import org.junit.Test

class StatusBarChipSupportTest {
    @Test fun nativeAvailabilityDependsOnThePublicApi() {
        listOf(24, 29, 35).forEach { assertEquals(Level.Unsupported, StatusBarChipSupport.decide(it)) }
        listOf(36, 37).forEach { assertEquals(Level.PlatformDecides, StatusBarChipSupport.decide(it)) }
    }
    @Test fun oldClassTransitionsCannotChangeTheNextClassNotification() {
        assertTrue(matchesCourseNotice("Math", 100, "Math", 100))
        assertFalse(matchesCourseNotice("Math", 100, "Math", 200))
        assertFalse(matchesCourseNotice("Math", 100, "Physics", 100))
        assertFalse(matchesCourseNotice("Math", 100, null, 0))
    }
}
