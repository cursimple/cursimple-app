package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.reminderDayPolicy
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.reminder.ReminderPlanner
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class ReminderRowData(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val title: String,
    val message: String,
    val countdown: String,
    val accentPrimary: Boolean,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ReminderWidgetData(
    val widgetTheme: WidgetThemePreferences,
    val totalCount: Int,
    val emptyTitle: String,
    val emptySubtitle: String?,
    val rows: List<ReminderRowData>,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ReminderDataSource {
    private val cache = WidgetDataCache<ReminderWidgetData>()

    /** [reuseRecent] 为 true 时优先复用刚读出的当次结果，让列表跟着头部走同一份数据。 */
    suspend fun load(context: Context, reuseRecent: Boolean = false): ReminderWidgetData {
        if (reuseRecent) {
            cache.get(WIDGET_SHARED_CACHE_KEY, System.nanoTime())?.let { return it }
        }
        return loadFresh(context)
            .also { cache.put(WIDGET_SHARED_CACHE_KEY, System.nanoTime(), it) }
    }

    private suspend fun loadFresh(context: Context): ReminderWidgetData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)

        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val rules = reminderRepository.reminderRulesFlow.first().filter { it.enabled }
        val customOccupancies = reminderRepository.customOccupanciesFlow.first()
        val alarmRecords = reminderRepository.systemAlarmRecordsFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)

        val now = BeijingTime.nowMillis(zone)
        val today = BeijingTime.todayIn(zone)
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )
        val effectiveTimingProfile = timingProfile?.withTermStartDate(termStart)
        val planner = ReminderPlanner()
        val reminderSchedule = mergeManualCourses(schedule, manualCourses)
        val plans = if (reminderSchedule != null && effectiveTimingProfile != null) {
            runCatching {
                planner.expandRules(
                    rules = rules,
                    schedule = reminderSchedule,
                    timingProfile = effectiveTimingProfile,
                    fromDate = today,
                    temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
                    customOccupancies = customOccupancies,
                    holidayCalendar = userPrefs.holidayCalendar,
                    dayPolicy = userPrefs.reminderDayPolicy(),
                )
            }.getOrDefault(emptyList())
                .filter { it.triggerAtMillis >= now }
                .sortedBy { it.triggerAtMillis }
        } else emptyList()

        val entries = buildReminderWidgetEntries(
            plans = plans,
            records = alarmRecords,
            nowMillis = now,
            defaults = appContext.reminderWidgetTextDefaults(),
        )

        val rows = entries.map { entry ->
            val ts = Instant.ofEpochMilli(entry.triggerAtMillis).atZone(zone).toLocalDateTime()
            val isToday = ts.toLocalDate() == today
            val isSoon = (entry.triggerAtMillis - now) <= 60 * 60 * 1000L
            ReminderRowData(
                id = entry.id,
                dateLabel = appContext.formatDateLabel(ts.toLocalDate(), today),
                timeLabel = ts.toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5),
                title = entry.title,
                message = entry.message,
                countdown = appContext.formatCountdown(entry.triggerAtMillis - now),
                accentPrimary = isToday || isSoon,
            )
        }

        val emptyTitle = appContext.getString(
            if (rules.isEmpty()) {
                R.string.widget_reminder_empty_no_rules
            } else {
                R.string.widget_reminder_empty_none_upcoming
            },
        )
        val emptySubtitle = appContext.getString(
            if (rules.isEmpty()) {
                R.string.widget_reminder_empty_no_rules_sub
            } else {
                R.string.widget_reminder_empty_none_upcoming_sub
            },
        )

        return ReminderWidgetData(
            widgetTheme = widgetTheme,
            totalCount = entries.size,
            emptyTitle = emptyTitle,
            emptySubtitle = emptySubtitle,
            rows = rows,
        )
    }

    private fun Context.formatDateLabel(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
        return when (days) {
            0 -> getString(R.string.widget_reminder_date_today)
            1 -> getString(R.string.widget_reminder_date_tomorrow)
            2 -> getString(R.string.widget_reminder_date_day_after)
            else -> DateTimeFormatter.ofPattern("M/d").format(date)
        }
    }

    private fun Context.formatCountdown(diffMillis: Long): String {
        if (diffMillis <= 0) return getString(R.string.widget_reminder_countdown_now)
        val totalMinutes = Duration.ofMillis(diffMillis).toMinutes()
        return when {
            totalMinutes < 60 ->
                getString(R.string.widget_reminder_countdown_minutes, totalMinutes.toInt())

            totalMinutes < 24 * 60 -> {
                val h = (totalMinutes / 60).toInt()
                val m = (totalMinutes % 60).toInt()
                if (m == 0) {
                    getString(R.string.widget_reminder_countdown_hours, h)
                } else {
                    getString(R.string.widget_reminder_countdown_hours_minutes, h, m)
                }
            }

            else -> getString(
                R.string.widget_reminder_countdown_days,
                (totalMinutes / (24 * 60)).toInt(),
            )
        }
    }

    private fun mergeManualCourses(
        schedule: com.x500x.cursimple.core.kernel.model.TermSchedule?,
        manualCourses: List<com.x500x.cursimple.core.kernel.model.CourseItem>,
    ): com.x500x.cursimple.core.kernel.model.TermSchedule? {
        if (schedule == null && manualCourses.isEmpty()) return null
        val allCourses = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
        val dailySchedules = allCourses
            .groupBy { it.time.dayOfWeek }
            .toSortedMap()
            .map { (day, courses) ->
                DailySchedule(dayOfWeek = day, courses = courses.sortedBy { it.time.startNode })
            }
        return com.x500x.cursimple.core.kernel.model.TermSchedule(
            termId = schedule?.termId ?: "manual",
            updatedAt = schedule?.updatedAt ?: "",
            dailySchedules = dailySchedules,
        )
    }
}
