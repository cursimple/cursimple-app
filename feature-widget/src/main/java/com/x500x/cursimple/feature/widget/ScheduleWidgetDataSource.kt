package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.filterTemporaryCancelledCourses
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay

internal data class ScheduleWidgetCourseRow(
    val id: String,
    val nodeRange: String,
    val timeRange: String,
    val title: String,
    val subtitle: String,
    val hasReminder: Boolean,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ScheduleWidgetDayData(
    val offset: Int,
    val manualOffset: Int,
    val targetDate: LocalDate,
    val weekdayLabel: String,
    val sourceDate: LocalDate,
    val rows: List<ScheduleWidgetCourseRow>,
    val widgetTheme: WidgetThemePreferences = WidgetThemePreferences(),
    val beforeTermStart: Boolean = false,
    val termStartMissing: Boolean = false,
    val termStartDate: LocalDate? = null,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

/**
 * 一次刷新里头部和列表共用同一份读取结果的短时缓存；超出 [ttlNanos] 或换了小组件就重新读。
 */
internal class ScheduleWidgetDayCache(private val ttlNanos: Long = DEFAULT_TTL_NANOS) {
    private class Entry(
        val appWidgetId: Int,
        val atNanos: Long,
        val data: ScheduleWidgetDayData,
    )

    @Volatile
    private var entry: Entry? = null

    fun get(appWidgetId: Int, nowNanos: Long): ScheduleWidgetDayData? {
        val current = entry ?: return null
        if (current.appWidgetId != appWidgetId) return null
        val age = nowNanos - current.atNanos
        return if (age in 0 until ttlNanos) current.data else null
    }

    fun put(appWidgetId: Int, nowNanos: Long, data: ScheduleWidgetDayData) {
        entry = Entry(appWidgetId, nowNanos, data)
    }

    companion object {
        const val DEFAULT_TTL_NANOS: Long = 5_000_000_000L
    }
}

internal object ScheduleWidgetDataSource {
    private val dayCache = ScheduleWidgetDayCache()

    /** [reuseRecent] 为 true 时优先复用刚读出的当次结果，让列表跟着头部走同一份数据。 */
    suspend fun loadDay(
        context: Context,
        appWidgetId: Int,
        reuseRecent: Boolean = false,
    ): ScheduleWidgetDayData {
        if (reuseRecent) {
            dayCache.get(appWidgetId, System.nanoTime())?.let { return it }
        }
        return loadFreshDay(context, appWidgetId)
            .also { dayCache.put(appWidgetId, System.nanoTime(), it) }
    }

    private suspend fun loadFreshDay(context: Context, appWidgetId: Int): ScheduleWidgetDayData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)

        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val manualOffset = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetPreferencesRepository.widgetDayOffsetFlow.first()
        } else {
            widgetPreferencesRepository.widgetDayOffset(appWidgetId)
        }
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )

        val currentDay = loadDate(
            targetDate = today,
            offset = 0,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            scheduleRepository = scheduleRepository,
            manualCourseRepository = manualCourseRepository,
            reminderRepository = reminderRepository,
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
            widgetTheme = widgetTheme,
        )
        if (manualOffset == 0 && shouldShowNextDayAtNight(BeijingTime.nowTimeIn(zone), currentDay.courses, timingProfile)) {
            return loadDate(
                targetDate = today.plusDays(1),
                offset = 1,
                manualOffset = manualOffset,
                termStart = termStart,
                timingProfile = timingProfile,
                scheduleRepository = scheduleRepository,
                manualCourseRepository = manualCourseRepository,
                reminderRepository = reminderRepository,
                temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
                widgetTheme = widgetTheme,
            ).data
        }
        if (manualOffset == 0) return currentDay.data

        return loadDate(
            targetDate = today.plusDays(manualOffset.toLong()),
            offset = manualOffset,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            scheduleRepository = scheduleRepository,
            manualCourseRepository = manualCourseRepository,
            reminderRepository = reminderRepository,
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
            widgetTheme = widgetTheme,
        ).data
    }

    private suspend fun loadDate(
        targetDate: LocalDate,
        offset: Int,
        manualOffset: Int,
        termStart: LocalDate?,
        timingProfile: TermTimingProfile?,
        scheduleRepository: DataStoreScheduleRepository,
        manualCourseRepository: DataStoreManualCourseRepository,
        reminderRepository: DataStoreReminderRepository,
        temporaryScheduleOverrides: List<com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride>,
        holidayCalendar: HolidayCalendarSettings,
        widgetTheme: WidgetThemePreferences,
    ): LoadedDay {
        val dayResolution = resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar)
        val sourceDate = dayResolution.sourceDate
        val weekIndex = resolveWeekIndex(sourceDate, termStart)
        val dayOfWeek = sourceDate.dayOfWeek.value

        val importedCourses = scheduleRepository.scheduleFlow.first()
            ?.coursesOfDay(dayOfWeek)
            .orEmpty()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
            .filter { it.time.dayOfWeek == dayOfWeek }
        val reminderRules = reminderRepository.reminderRulesFlow.first()
        val courses = if (dayResolution.isHoliday) emptyList() else filterTemporaryCancelledCourses(
            date = targetDate,
            courses = importedCourses + manualCourses,
            overrides = temporaryScheduleOverrides,
        )
            .visibleScheduleCourses()
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }
        val rows = courses.map { it.toRow(timingProfile, reminderRules) }

        return LoadedDay(
            data = ScheduleWidgetDayData(
                offset = offset,
                manualOffset = manualOffset,
                targetDate = targetDate,
                weekdayLabel = weekdayLabel(targetDate),
                sourceDate = sourceDate,
                rows = rows,
                widgetTheme = widgetTheme,
                beforeTermStart = isBeforeTermStart(weekIndex),
                termStartMissing = weekIndex == null,
                termStartDate = termStart,
            ),
            courses = courses,
        )
    }

    private data class LoadedDay(
        val data: ScheduleWidgetDayData,
        val courses: List<CourseItem>,
    )

    private fun CourseItem.toRow(
        timingProfile: TermTimingProfile?,
        reminderRules: List<ReminderRule>,
    ): ScheduleWidgetCourseRow {
        val nodeRange = "${time.startNode}-${time.endNode}节"
        val timeRange = timingProfile?.courseClockRange(this) ?: nodeRange
        val subtitle = listOf(location, teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "待定" }
        return ScheduleWidgetCourseRow(
            id = id,
            nodeRange = nodeRange,
            timeRange = timeRange,
            title = if (category == CourseCategory.Exam) "考试 · $title" else title,
            subtitle = subtitle,
            hasReminder = reminderRules.any { it.matches(this, timingProfile) },
        )
    }

    private fun ReminderRule.matches(
        course: CourseItem,
        timingProfile: TermTimingProfile?,
    ): Boolean = enabled && when (scopeType) {
        ReminderScopeType.SingleCourse -> courseId == course.id
        ReminderScopeType.TimeSlot ->
            startNode == course.time.startNode && endNode == course.time.endNode
        ReminderScopeType.Exam ->
            course.category == CourseCategory.Exam && course.id !in mutedCourseIds
        ReminderScopeType.FirstCourseOfPeriod -> false
        ReminderScopeType.LabelRule -> {
            // 节次名优先取课程自身覆盖，其次回退到计时档案，与提醒评估器口径一致
            val slotLabel = timingProfile?.let { course.reminderSlotLabel(it) }
                ?: course.slotLabelOverride
            slotLabel != null && labelActions.any {
                it.action == com.x500x.cursimple.core.reminder.model.ReminderLabelActionType.Remind &&
                    it.slotLabel == slotLabel
            }
        }
    }

    private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> ""
    }
}

/** 所有小组件共用的开学日期来源，保证不同小组件算出同一个教学周。 */
internal suspend fun resolveWidgetTermStartDate(
    termProfileRepository: DataStoreTermProfileRepository,
    timingProfile: TermTimingProfile?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? {
    val activeTermId = termProfileRepository.activeTermId()
    val activeTermStartIso = termProfileRepository.termsFlow.first()
        .firstOrNull { it.id == activeTermId }
        ?.termStartDate
    return selectTermStartDate(
        activeTermStartIso = activeTermStartIso,
        timingProfileTermStartIso = timingProfile?.termStartDate,
        preferenceTermStartDate = preferenceTermStartDate,
    )
}

/** 把计时档案的开学日期换成统一解析出的日期；日期为空或本就一致时返回原档案。 */
internal fun TermTimingProfile.withTermStartDate(termStartDate: LocalDate?): TermTimingProfile {
    val iso = termStartDate?.toString() ?: return this
    return if (iso == this.termStartDate) this else copy(termStartDate = iso)
}

/** 当前学期档案 → 小组件计时档案 → 用户偏好，取第一个能解析出日期的来源。 */
internal fun selectTermStartDate(
    activeTermStartIso: String?,
    timingProfileTermStartIso: String?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? =
    activeTermStartIso?.let(::parseIsoDate)
        ?: timingProfileTermStartIso?.let(::parseIsoDate)
        ?: preferenceTermStartDate

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
