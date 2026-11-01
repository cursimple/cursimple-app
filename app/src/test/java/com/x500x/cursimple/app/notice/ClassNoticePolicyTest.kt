package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.data.ClassNoticePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassNoticePolicyTest {
    private val allEnabled = ClassNoticePreferences(
        enabled = true,
        eveningBriefingEnabled = true,
        transitReminderEnabled = true,
        examAlertEnabled = true,
    )

    private fun canDeliver(preferences: ClassNoticePreferences, kind: ScheduledClassNoticeKind) =
        shouldDeliverScheduledClassNotice(preferences, kind, notificationsAllowed = true)

    @Test fun `new reminder kinds stay off by default`() {
        val defaults = ClassNoticePreferences()
        assertFalse(canDeliver(defaults, ScheduledClassNoticeKind.EveningBriefing))
        assertFalse(canDeliver(defaults, ScheduledClassNoticeKind.Transit))
        assertFalse(canDeliver(defaults, ScheduledClassNoticeKind.Exam))
    }

    @Test fun `enabled reminders can deliver but closing master suppresses queued messages of every kind`() {
        for (kind in ScheduledClassNoticeKind.entries) {
            assertTrue(kind.name, canDeliver(allEnabled, kind))
            assertFalse(kind.name, canDeliver(allEnabled.copy(enabled = false), kind))
        }
    }

    @Test fun `closing one child suppresses its queued message and preserves all other reminders`() {
        val closedChildren = listOf(
            ScheduledClassNoticeKind.EveningBriefing to allEnabled.copy(eveningBriefingEnabled = false),
            ScheduledClassNoticeKind.Transit to allEnabled.copy(transitReminderEnabled = false),
            ScheduledClassNoticeKind.Exam to allEnabled.copy(examAlertEnabled = false),
        )
        for ((closedKind, latestPreferences) in closedChildren) {
            for (kind in ScheduledClassNoticeKind.entries) {
                if (kind == closedKind) {
                    assertFalse(kind.name, canDeliver(latestPreferences, kind))
                } else {
                    assertTrue("closing $closedKind must preserve $kind", canDeliver(latestPreferences, kind))
                }
            }
        }
    }

    @Test fun `permission denial suppresses even enabled notices and permission grant restores eligibility`() {
        for (kind in ScheduledClassNoticeKind.entries) {
            assertFalse(kind.name, shouldDeliverScheduledClassNotice(allEnabled, kind, notificationsAllowed = false))
            assertTrue(kind.name, canDeliver(allEnabled, kind))
        }
    }

    @Test fun `switch and timing changes require rescheduling in both directions`() {
        val changes = listOf(
            allEnabled.copy(enabled = false),
            allEnabled.copy(advanceMinutes = 10),
            allEnabled.copy(eveningBriefingEnabled = false),
            allEnabled.copy(eveningBriefingHour = 20),
            allEnabled.copy(eveningBriefingMinute = 0),
            allEnabled.copy(transitReminderEnabled = false),
            allEnabled.copy(examAlertEnabled = false),
            allEnabled.copy(examAlertAdvanceMinutes = 30),
        )
        for (changed in changes) {
            assertTrue(classNoticeScheduleChanged(allEnabled, changed))
            assertTrue(classNoticeScheduleChanged(changed, allEnabled))
        }
    }

    @Test fun `unchanged or presentation-only settings do not replace pending alarms`() {
        assertFalse(classNoticeScheduleChanged(allEnabled, allEnabled.copy()))
        assertFalse(classNoticeScheduleChanged(allEnabled, allEnabled.copy(
            headsUpEnabled = false,
            vibrationEnabled = true,
            lockScreenEnabled = false,
            focusNotificationEnabled = false,
            blurEnabled = false,
            blurStrength = 0,
            bannerDurationSeconds = 9,
        )))
    }
}
