package com.x500x.cursimple.app.notice

import org.junit.Assert.*
import org.junit.Test

class NoticeReceiptTest {
    @Test fun missingPrivateFieldsRequireOwnedRecordAndMatchingPublicIdentity() {
        assertTrue(matchesNoticeIdentity("Math", 100, null, null, "Math", true, false))
        assertFalse(matchesNoticeIdentity("Math", 100, null, null, "Math", false, false))
        assertFalse(matchesNoticeIdentity("Math", 100, null, null, "Other", true, false))
        assertTrue(matchesNoticeIdentity("Math", 100, null, null, "System card", true, true))
    }

    @Test fun explicitConflictingMetadataAlwaysRejectsAnOldNotification() {
        assertFalse(matchesNoticeIdentity("Math", 200, "Math", 100, "Math", true, true))
        assertFalse(matchesNoticeIdentity("Math", 100, "Other", null, "Math", true, true))
        assertTrue(matchesNoticeIdentity("Math", 100, "Math", 100, "Math", false, false))
    }

    @Test fun oldCenterResultsMeanBannerSuccessNotLiveCourseContent() {
        val old = NoticeDisplayRun("sample", NoticeDisplayTarget.Center, 1000, 1, true, feedback = NoticeDisplayFeedback.CourseContent)
        assertEquals(NoticeDisplayFeedback.BannerAppeared, old.normalized().feedback)
        assertEquals(NoticeDisplayStep.NotApplicable, old.normalized().step)
        assertEquals(NoticeDisplayFeedback.CourseContent, old.copy(target = NoticeDisplayTarget.Aod).normalized().feedback)
    }
}
