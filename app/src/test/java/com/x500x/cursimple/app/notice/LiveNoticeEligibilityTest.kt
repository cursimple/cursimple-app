package com.x500x.cursimple.app.notice

import org.junit.Assert.*
import org.junit.Test

class LiveNoticeEligibilityTest {
    @Test fun `distant reminders and finished classes remain ordinary notifications`() {
        val now = 1_000_000L
        assertFalse(liveNoticeEligible(now + 20 * 60_000, now + 80 * 60_000, now))
        assertFalse(liveNoticeEligible(now + 5 * 60_000, now + 80 * 60_000, now))
        assertFalse(liveNoticeEligible(now + 1, now + 80 * 60_000, now))
        assertTrue(liveNoticeEligible(now, now + 80 * 60_000, now))
        assertTrue(liveNoticeEligible(now - 60_000, now + 60_000, now))
        assertFalse(liveNoticeEligible(now - 60_000, now, now))
        assertFalse(liveNoticeEligible(0, now + 60_000, now))
    }
}
