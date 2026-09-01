package com.x500x.cursimple.app.reminder

import com.x500x.cursimple.core.data.AutoSilenceMode
import com.x500x.cursimple.core.data.AutoSilenceSession
import com.x500x.cursimple.core.data.InterruptionFilterValues
import com.x500x.cursimple.core.data.RingerModeValues
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 一段连续的上课时间，连堂课已经合并成一段。 */
data class ClassBlock(
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/** 当前时刻相对上课时段该做什么。 */
sealed interface AutoSilenceDecision {
    /** 进入上课时段，需要切换手机状态，[block] 的结束时刻即本次静音的兜底截止。 */
    data class Enter(val block: ClassBlock) : AutoSilenceDecision

    /** 仍在上课时段内，保持现状。 */
    object Keep : AutoSilenceDecision

    /** 已经离开上课时段或功能被关闭，需要恢复用户原来的状态。 */
    object Restore : AutoSilenceDecision

    /** 无事可做。 */
    object Idle : AutoSilenceDecision
}

/** 课间不超过这个分钟数就并成一段，避免连堂课之间反复切换。 */
const val DEFAULT_CLASS_BLOCK_MERGE_GAP_MINUTES = 20L

/** 超过计划结束时刻这么久还没恢复，一律强制恢复。 */
const val AUTO_SILENCE_EXPIRY_GRACE_MILLIS = 2 * 60 * 1000L

/** 单次静音的绝对上限，任何情况下超过它都强制恢复。 */
const val AUTO_SILENCE_MAX_SESSION_MILLIS = 6 * 60 * 60 * 1000L

/** 系统时间被往回调超过这个幅度，视为现场记录已经不可信。 */
const val AUTO_SILENCE_CLOCK_REWIND_TOLERANCE_MILLIS = 60 * 1000L

/**
 * 解析 [date] 当天真正会上课的时间段。
 *
 * 假日、未开学、临时取消的课都不产生时间段；临时调课按 [resolveScheduleDay] 给出的来源日取课。
 */
fun resolveClassBlocks(
    date: LocalDate,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile,
    termStart: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    mergeGapMinutes: Long = DEFAULT_CLASS_BLOCK_MERGE_GAP_MINUTES,
): List<ClassBlock> {
    val day = resolveScheduleDay(date, overrides, holidayCalendar)
    if (day.isHoliday) return emptyList()
    val termWeek = resolveTermWeekNumber(termStart, day.sourceDate)
    if (!isTermWeekNumberStarted(termWeek)) return emptyList()
    val sourceDayOfWeek = day.sourceDate.dayOfWeek.value
    val intervals = courses
        .asSequence()
        .filter { it.time.dayOfWeek == sourceDayOfWeek }
        .filter { it.isActiveInTermWeekNumber(termWeek) }
        .filterNot { isCourseTemporarilyCancelled(date, it, overrides) }
        .mapNotNull { course -> course.classInterval(timingProfile)?.toBlockOn(date) }
        .toList()
    return mergeClassBlocks(intervals, mergeGapMinutes)
}

/** 把重叠或课间不超过 [mergeGapMinutes] 的时间段并成一段。 */
fun mergeClassBlocks(
    blocks: List<ClassBlock>,
    mergeGapMinutes: Long = DEFAULT_CLASS_BLOCK_MERGE_GAP_MINUTES,
): List<ClassBlock> {
    if (blocks.isEmpty()) return emptyList()
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.end }))
    val merged = mutableListOf<ClassBlock>()
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        if (!next.start.isAfter(current.end.plusMinutes(mergeGapMinutes))) {
            if (next.end.isAfter(current.end)) {
                current = current.copy(end = next.end)
            }
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

/** [now] 落在哪一段上课时间里，开始时刻算在内，结束时刻算在外。 */
fun activeClassBlockAt(now: LocalDateTime, blocks: List<ClassBlock>): ClassBlock? =
    blocks.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

/** [now] 之后最近一次需要切换状态的时刻，也就是最近的上课开始或下课结束时刻。 */
fun nextClassBoundaryAfter(now: LocalDateTime, blocks: List<ClassBlock>): LocalDateTime? =
    blocks
        .asSequence()
        .flatMap { sequenceOf(it.start, it.end) }
        .filter { it.isAfter(now) }
        .minOrNull()

/**
 * 决定当前该进入静音、保持、恢复还是什么都不做。
 *
 * 关掉开关或不在上课时段时，只要现场记录还在就必须恢复。
 */
fun decideAutoSilence(
    now: LocalDateTime,
    nowMillis: Long,
    blocks: List<ClassBlock>,
    session: AutoSilenceSession,
    featureEnabled: Boolean,
): AutoSilenceDecision {
    if (session.active) {
        if (!featureEnabled) return AutoSilenceDecision.Restore
        return if (activeClassBlockAt(now, blocks) == null) {
            AutoSilenceDecision.Restore
        } else {
            AutoSilenceDecision.Keep
        }
    }
    if (!featureEnabled) return AutoSilenceDecision.Idle
    if (nowMillis < session.suppressedUntilMillis) return AutoSilenceDecision.Idle
    val block = activeClassBlockAt(now, blocks) ?: return AutoSilenceDecision.Idle
    return AutoSilenceDecision.Enter(block)
}

/**
 * 现场记录是否已经过期。
 *
 * 只看时间戳，不依赖课表数据，因此课表被清空、学期切换或数据读不出来时仍能兜底恢复。
 */
fun isAutoSilenceSessionExpired(session: AutoSilenceSession, nowMillis: Long): Boolean {
    if (!session.active) return false
    if (session.plannedEndAtMillis > 0L &&
        nowMillis > session.plannedEndAtMillis + AUTO_SILENCE_EXPIRY_GRACE_MILLIS
    ) {
        return true
    }
    if (session.startedAtMillis > 0L) {
        if (nowMillis - session.startedAtMillis > AUTO_SILENCE_MAX_SESSION_MILLIS) return true
        if (session.startedAtMillis - nowMillis > AUTO_SILENCE_CLOCK_REWIND_TOLERANCE_MILLIS) return true
    }
    return false
}

/**
 * 目标模式下需要写入的铃声模式，已经足够安静时返回 null 表示不动手。
 */
fun resolveRingerModeToApply(mode: AutoSilenceMode, currentRingerMode: Int): Int? = when (mode) {
    AutoSilenceMode.Silent -> RingerModeValues.SILENT.takeIf {
        currentRingerMode == RingerModeValues.NORMAL || currentRingerMode == RingerModeValues.VIBRATE
    }

    AutoSilenceMode.Vibrate ->
        RingerModeValues.VIBRATE.takeIf { currentRingerMode == RingerModeValues.NORMAL }

    AutoSilenceMode.DoNotDisturb -> null
}

/**
 * 目标模式下需要写入的勿扰级别，只用仅优先级，永远不会写入完全静音。
 */
fun resolveInterruptionFilterToApply(mode: AutoSilenceMode, currentFilter: Int): Int? = when (mode) {
    AutoSilenceMode.DoNotDisturb ->
        InterruptionFilterValues.PRIORITY.takeIf { currentFilter == InterruptionFilterValues.ALL }

    AutoSilenceMode.Silent, AutoSilenceMode.Vibrate -> null
}

/**
 * 恢复时要写回的铃声模式。
 *
 * 只有当前值仍等于当初写下去的值才恢复，用户上课途中自己改过就不动，返回 null。
 */
fun resolveRingerModeToRestore(session: AutoSilenceSession, currentRingerMode: Int): Int? {
    if (session.appliedRingerMode == RingerModeValues.UNKNOWN) return null
    if (session.previousRingerMode == RingerModeValues.UNKNOWN) return null
    if (currentRingerMode != session.appliedRingerMode) return null
    if (currentRingerMode == session.previousRingerMode) return null
    return session.previousRingerMode
}

/** 恢复时要写回的勿扰级别，判断方式与铃声模式一致。 */
fun resolveInterruptionFilterToRestore(session: AutoSilenceSession, currentFilter: Int): Int? {
    if (session.appliedInterruptionFilter == InterruptionFilterValues.UNKNOWN) return null
    if (session.previousInterruptionFilter == InterruptionFilterValues.UNKNOWN) return null
    if (currentFilter != session.appliedInterruptionFilter) return null
    if (currentFilter == session.previousInterruptionFilter) return null
    return session.previousInterruptionFilter
}

private data class ClassInterval(val start: LocalTime, val end: LocalTime)

private fun ClassInterval.toBlockOn(date: LocalDate): ClassBlock =
    ClassBlock(start = LocalDateTime.of(date, start), end = LocalDateTime.of(date, end))

/**
 * 课程的实际起止时刻。
 *
 * 先按节次在 [TermTimingProfile.slotTimes] 里取真实时刻，取不到时退回课程自带的提醒起止时间。
 */
private fun CourseItem.classInterval(timingProfile: TermTimingProfile): ClassInterval? {
    val start = timingProfile.slotContaining(time.startNode)?.let { parseLocalTime(it.startTime) }
        ?: reminderStartTime?.let(::parseLocalTime)
        ?: return null
    val end = timingProfile.slotContaining(time.endNode)?.let { parseLocalTime(it.endTime) }
        ?: reminderEndTime?.let(::parseLocalTime)
        ?: return null
    if (!end.isAfter(start)) return null
    return ClassInterval(start = start, end = end)
}

private fun TermTimingProfile.slotContaining(node: Int): ClassSlotTime? =
    slotTimes.firstOrNull { node in it.startNode..it.endNode }

private fun parseLocalTime(raw: String): LocalTime? =
    runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
