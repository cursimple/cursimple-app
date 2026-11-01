package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.data.ClassNoticePreferences

internal enum class ScheduledClassNoticeKind {
    Course, EveningBriefing, Transit, Exam,
}

internal fun ClassNoticePreferences.isEnabled(kind: ScheduledClassNoticeKind): Boolean =
    enabled && when (kind) {
        ScheduledClassNoticeKind.Course -> true
        ScheduledClassNoticeKind.EveningBriefing -> eveningBriefingEnabled
        ScheduledClassNoticeKind.Transit -> transitReminderEnabled
        ScheduledClassNoticeKind.Exam -> examAlertEnabled
    }

/** Evaluate current settings, including for broadcasts queued before a switch was turned off. */
internal fun shouldDeliverScheduledClassNotice(
    preferences: ClassNoticePreferences,
    kind: ScheduledClassNoticeKind,
    notificationsAllowed: Boolean,
): Boolean = notificationsAllowed && preferences.isEnabled(kind)

/** Presentation settings are read at delivery; only timing and enablement require new alarms. */
internal fun classNoticeScheduleChanged(
    previous: ClassNoticePreferences,
    current: ClassNoticePreferences,
): Boolean = previous.enabled != current.enabled ||
    previous.advanceMinutes != current.advanceMinutes ||
    previous.eveningBriefingEnabled != current.eveningBriefingEnabled ||
    previous.eveningBriefingHour != current.eveningBriefingHour ||
    previous.eveningBriefingMinute != current.eveningBriefingMinute ||
    previous.transitReminderEnabled != current.transitReminderEnabled ||
    previous.examAlertEnabled != current.examAlertEnabled ||
    previous.examAlertAdvanceMinutes != current.examAlertAdvanceMinutes
