package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate

/**
 * 某一天的最终课表归属。
 * [sourceDate] 是当天实际按哪一天的课表上课，[isHoliday] 为真时当天不出课也不提醒。
 */
data class ScheduleDayResolution(
    val date: LocalDate,
    val sourceDate: LocalDate,
    val isHoliday: Boolean,
    val holidayName: String?,
    /** 内置假日的文案资源，用户自建的假日为 null，按用户填的名字显示。 */
    val holidayNameRes: Int? = null,
    /**
     * 这天是调休补班日。
     *
     * 本来是周末或假期，因为放假安排被调成了上课日。课照出，但日历上看着是休息日，
     * 不标一下很容易当成放假睡过去，所以表头要单独给它一个记号。
     */
    val isMakeUpWorkday: Boolean = false,
    /**
     * 调课只覆盖这个节次区间，区间外仍按本日自己的安排；整天调课或没有调课时为 null。
     *
     * 取课一律走 [temporaryScheduleCourseSourceDate]，这里留给界面区分「整天调课」和「只调某几节」。
     */
    val makeUpNodeRange: IntRange? = null,
)

/**
 * 合并临时调课与节假日日历，得出 [date] 当天的课表归属。
 *
 * 优先级由高到低：
 * 1. 用户手动写下的节假日条目，无论是设为假日还是设为调休上课日；
 * 2. 覆盖当天的临时调课（补课/调课），它明示当天要上课，因此推翻内置假日；
 * 3. 同步下来的放假安排，取不到该年数据时回落到内置快照。
 *
 * 临时取消只移除指定节次的课，不参与当天是否为假日的判定。
 */
fun resolveScheduleDay(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
): ScheduleDayResolution {
    val userEntry = holidayCalendar.userEntryOn(date)
    val effectiveEntry = when {
        userEntry != null -> userEntry
        matchingTemporaryScheduleOverride(date, overrides) != null -> null
        else -> holidayCalendar.syncedEntryOn(date) ?: holidayCalendar.builtInEntryOn(date)
    }
    val holiday = effectiveEntry?.kind == HolidayEntryKind.Holiday
    // 只有「本来该休息」的日子被调成上班才算调休；平日标成 Workday 没有信息量
    val makeUpWorkday = effectiveEntry?.kind == HolidayEntryKind.Workday &&
        date.dayOfWeek.value >= 6
    return ScheduleDayResolution(
        date = date,
        sourceDate = if (holiday) date else resolveTemporaryScheduleSourceDate(date, overrides),
        isHoliday = holiday,
        holidayName = if (holiday) effectiveEntry?.name?.takeIf { it.isNotBlank() } else null,
        holidayNameRes = when {
            !holiday || userEntry != null -> null
            else -> builtInHolidayNameResOn(date) ?: effectiveEntry?.name?.let(::holidayNameResOfName)
        },
        isMakeUpWorkday = makeUpWorkday,
        makeUpNodeRange = if (holiday) null else matchingTemporaryScheduleOverride(date, overrides)?.makeUpNodeRange(),
    )
}

/**
 * [date] 当天实际要上的课，按起始节次排序，和课表主界面同一套判定：
 * 调课按来源日取课、按来源日所在周判断上不上，被挪走的课不出现，挪过来的课落到目标节次，
 * 放假日只剩被挪过来的那几门。
 *
 * [includeCancelled] 为真时连临时取消掉的也列出来，给「选一天、逐门点取消」那种
 * 要显示已取消状态、还能点回来的界面用。[termStartDate] 为空时不按周次过滤。
 */
fun coursesScheduledOn(
    date: LocalDate,
    courses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    termStartDate: LocalDate?,
    includeCancelled: Boolean = false,
): List<CourseItem> {
    val resolution = resolveScheduleDay(date, overrides, holidayCalendar)
    val activeOn: (CourseItem, LocalDate) -> Boolean = { course, day ->
        termStartDate == null || course.isActiveInTermWeekNumber(resolveTermWeekNumber(termStartDate, day))
    }
    val movedIn = coursesMovedTo(
        date = date,
        overrides = overrides,
        courseById = { id -> courses.firstOrNull { it.id == id } },
        isOriginallyActive = activeOn,
    )
    val staying = if (resolution.isHoliday) {
        emptyList()
    } else {
        courses
            .filterNot { isCourseMovedAwayFrom(date, it, overrides) }
            .filter { course ->
                val source = temporaryScheduleCourseSourceDate(date, course, resolution.sourceDate, overrides)
                source != null && activeOn(course, source)
            }
    }
    return (staying + movedIn)
        .let { all -> if (includeCancelled) all else all.filterNot { isCourseTemporarilyCancelled(date, it, overrides) } }
        .sortedWith(compareBy({ it.time.startNode }, { it.time.endNode }))
}
