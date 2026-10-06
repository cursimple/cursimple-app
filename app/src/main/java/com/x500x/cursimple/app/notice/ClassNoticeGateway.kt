package com.x500x.cursimple.app.notice

import android.content.Context
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.slotLabelText
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * Load preferences and schedules directly so cold-start receivers do not depend on an Activity.
 */
object ClassNoticeGateway {

    // Serialize cancel-and-schedule sequences to prevent concurrent startup tasks invalidating each other.
    private val rescheduleLock = Mutex()

    suspend fun preferences(context: Context): ClassNoticePreferences =
        DataStoreUserPreferencesRepository(context.applicationContext)
            .preferencesFlow.first().classNotice

    suspend fun theme(context: Context): NoticeTheme {
        val preferences = DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
        return NoticeTheme.resolve(
            context,
            preferences.themeAccent,
            preferences.themeMode,
            preferences.themeCustomColorArgb,
        )
    }

    suspend fun reschedule(context: Context) = rescheduleLock.withLock { rescheduleLocked(context) }

    /** Preview the actual next course when available, otherwise use sample content. */
    suspend fun previewContent(
        context: Context,
        notice: ClassNoticePreferences,
    ): ClassNoticeNotifier.Content {
        val app = context.applicationContext
        val upcoming = upcomingClass(app, notice, advanceMinutes = 0)
            ?: return ClassNoticeNotifier.previewContent(app, notice)
        return ClassNoticeNotifier.Content(
            courseTitle = upcoming.course.title,
            location = upcoming.displayLocation(),
            timeRange = upcoming.timeRangeText(),
            slotLabel = app.slotLabelText(upcoming.slots).orEmpty(),
            minutesUntilStart = Duration.between(BeijingTime.nowDateTime(), upcoming.startAt)
                .toMinutes().toInt().coerceAtLeast(0),
            startAtMillis = 0L,
            endAtMillis = 0L,
        )
    }

    private suspend fun rescheduleLocked(context: Context) {
        val app = context.applicationContext
        val notice = DataStoreUserPreferencesRepository(app).preferencesFlow.first().classNotice
        if (!notice.enabled) {
            ClassNoticeScheduler.cancel(app)
            return
        }
        ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice))
    }

    /**
     * Catch up missed notices before class starts, deduplicating with
     * [ClassNoticeScheduler.lastPostedKey].
     */
    suspend fun catchUpIfMissed(context: Context) {
        val app = context.applicationContext
        val notice = DataStoreUserPreferencesRepository(app).preferencesFlow.first().classNotice
        if (!notice.enabled) return

        val upcoming = upcomingClass(app, notice, advanceMinutes = 0) ?: return
        BeijingTime.setForcedNow(DataStoreUserPreferencesRepository(app).preferencesFlow.first().debugForcedDateTime)
        val now = BeijingTime.nowDateTime()
        val fireAt = upcoming.startAt.minusMinutes(notice.advanceMinutes.toLong())
        if (fireAt.isAfter(now)) return
        if (!upcoming.startAt.isAfter(now)) return

        val startAtMillis = upcoming.startAt.atZone(BeijingTime.zone).toInstant().toEpochMilli()
        val key = ClassNoticeScheduler.postedKey(upcoming.course.title, startAtMillis)
        if (ClassNoticeScheduler.lastPostedKey(app) == key) {
            ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice), now)
            return
        }

        val content = ClassNoticeNotifier.Content(
            courseTitle = upcoming.course.title,
            location = upcoming.displayLocation(),
            timeRange = upcoming.timeRangeText(),
            slotLabel = app.slotLabelText(upcoming.slots).orEmpty(),
            minutesUntilStart = Duration.between(now, upcoming.startAt).toMinutes().toInt().coerceAtLeast(0),
            startAtMillis = startAtMillis,
            endAtMillis = upcoming.endAt.atZone(BeijingTime.zone).toInstant().toEpochMilli(),
        )
        ClassNoticeNotifier.notify(app, content, notice, theme(app), forward = true)
        ClassNoticeScheduler.recordLastPosted(app, key)
        ClassNoticeScheduler.reschedule(app, notice, upcomingClass(app, notice), now)
    }

    /**
     * [advanceMinutes] skips elapsed reminder points; zero allows catch-up until class starts.
     */
    private suspend fun upcomingClass(
        app: Context,
        notice: ClassNoticePreferences,
        advanceMinutes: Int = notice.advanceMinutes,
    ): UpcomingClass? {
        val termProfileRepository = DataStoreTermProfileRepository(app)
        val scheduleRepository = DataStoreScheduleRepository(app, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(app, termProfileRepository)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
        val userPreferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()

        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()

        BeijingTime.setForcedNow(userPreferences.debugForcedDateTime)
        return ClassNoticePlanner.nextClass(
            now = BeijingTime.nowDateTime(),
            allCourses = schedule.allCoursesWith(manualCourses),
            timingProfile = timingProfile,
            termStartDate = userPreferences.termStartDate,
            overrides = userPreferences.temporaryScheduleOverrides,
            holidayCalendar = userPreferences.holidayCalendar,
            advanceMinutes = advanceMinutes,
        )
    }
}
