package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import org.junit.Assert.*
import org.junit.Test

class NoticeDisplayRunTest {
    private val run = NoticeDisplayRun("current", NoticeDisplayTarget.Aod, 10_000, 3, true)

    @Test fun staleCancelledAndDuplicateDeliveriesAreRejected() {
        assertTrue(run.canDeliver("current", 10_000, 3))
        assertFalse(run.canDeliver("old", 10_000, 3))
        assertFalse(run.canDeliver("current", 9_999, 3))
        listOf(NoticeDisplayPhase.Cancelled, NoticeDisplayPhase.Sent, NoticeDisplayPhase.Disabled).forEach {
            assertFalse(run.copy(phase = it).canDeliver("current", 10_000, 3))
        }
    }

    @Test fun lateAlarmsAndRunsFromAnotherBootCannotPost() {
        assertFalse(run.expired(129_999, 3))
        assertTrue(run.expired(130_000, 3))
        assertFalse(run.canDeliver("current", 130_000, 3))
        assertTrue(run.expired(100, 4))
        assertFalse(run.canDeliver("current", 10_000, 4))
    }

    @Test fun fixturesNeverUseOverlaysOrChangeUnrelatedPreferences() {
        val source = ClassNoticePreferences(skin = ClassNoticeSkin.Overlay, vibrationEnabled = true, lockScreenEnabled = false)
        NoticeDisplayTarget.entries.forEach {
            val selected = it.preferences(source)
            assertEquals(ClassNoticeSkin.System, selected.skin)
            assertTrue(selected.vibrationEnabled)
            assertEquals(it == NoticeDisplayTarget.Center, selected.headsUpEnabled)
            assertEquals(source.advanceMinutes, selected.advanceMinutes)
        }
        assertFalse(NoticeDisplayTarget.Center.preferences(source).focusNotificationEnabled)
        assertFalse(NoticeDisplayTarget.Center.preferences(source).lockScreenEnabled)
        assertTrue(NoticeDisplayTarget.Island.preferences(source).focusNotificationEnabled)
        assertTrue(NoticeDisplayTarget.Aod.preferences(source).lockScreenEnabled)
        assertFalse(NoticeDisplayTarget.Aod.preferences(ClassNoticePreferences()).vibrationEnabled)
    }

    @Test fun fixturesHaveDistinctIdsOutsideRealReminderSlots() {
        val ids = NoticeDisplayTarget.entries.map { it.notificationId }
        assertEquals(3, ids.toSet().size)
        assertFalse(ClassNoticeNotifier.NOTIFICATION_ID in ids)
        assertFalse(ClassNoticeNotifier.ALARM_NOTIFICATION_ID in ids)
    }
}
