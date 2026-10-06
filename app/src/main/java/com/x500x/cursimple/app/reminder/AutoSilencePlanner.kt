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
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.isTermWeekNumberStarted
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ClassBlock(
    val start: LocalDateTime,
    val end: LocalDateTime,
)

sealed interface AutoSilenceDecision {
    data class Enter(val block: ClassBlock) : AutoSilenceDecision

    object Keep : AutoSilenceDecision

    object Restore : AutoSilenceDecision

    object Idle : AutoSilenceDecision
}

/** Merge short gaps to avoid repeatedly switching between adjacent classes. */
const val DEFAULT_CLASS_BLOCK_MERGE_GAP_MINUTES = 20L

const val AUTO_SILENCE_EXPIRY_GRACE_MILLIS = 2 * 60 * 1000L

/** Absolute silence duration cap; restore regardless of schedule state. */
const val AUTO_SILENCE_MAX_SESSION_MILLIS = 6 * 60 * 60 * 1000L

const val AUTO_SILENCE_CLOCK_REWIND_TOLERANCE_MILLIS = 60 * 1000L

/** Resolve real class intervals after holiday, cancellation and swap rules. */
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
    val termWeek = resolveTermWeekNumber(termStart, day.sourceDate)
    if (!isTermWeekNumberStarted(termWeek)) return emptyList()
    val movedIn = coursesMovedTo(
        date = date,
        overrides = overrides,
        courseById = { id -> courses.firstOrNull { it.id == id } },
        isOriginallyActive = { course, from ->
            course.isActiveInTermWeekNumber(resolveTermWeekNumber(termStart, from))
        },
    )
    if (day.isHoliday) {
        return mergeClassBlocks(
            movedIn
                .filterNot { isCourseTemporarilyCancelled(date, it, overrides) }
                .mapNotNull { course -> course.classInterval(timingProfile)?.toBlockOn(date) },
            mergeGapMinutes,
        )
    }
    val intervals = courses
        .asSequence()
        .filterNot { isCourseMovedAwayFrom(date, it, overrides) }
        // Per-course source-day resolution handles mixed schedules after partial swaps.
        .mapNotNull { course ->
            temporaryScheduleCourseSourceDate(date, course, day.sourceDate, overrides)
                ?.let { course to it }
        }
        .filter { (course, courseSource) ->
            course.isActiveInTermWeekNumber(resolveTermWeekNumber(termStart, courseSource))
        }
        .map { (course, _) -> course }
        .plus(movedIn)
        .filterNot { isCourseTemporarilyCancelled(date, it, overrides) }
        .mapNotNull { course -> course.classInterval(timingProfile)?.toBlockOn(date) }
        .toList()
    return mergeClassBlocks(intervals, mergeGapMinutes)
}

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

fun activeClassBlockAt(now: LocalDateTime, blocks: List<ClassBlock>): ClassBlock? =
    blocks.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

fun nextClassBoundaryAfter(now: LocalDateTime, blocks: List<ClassBlock>): LocalDateTime? =
    blocks
        .asSequence()
        .flatMap { sequenceOf(it.start, it.end) }
        .filter { it.isAfter(now) }
        .minOrNull()

/** Restore whenever disabled or outside class while a saved state exists. */
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

/** Expire saved state by timestamps alone, even if schedule data is unavailable. */
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

fun resolveRingerModeToApply(mode: AutoSilenceMode, currentRingerMode: Int): Int? = when (mode) {
    AutoSilenceMode.Silent -> RingerModeValues.SILENT.takeIf {
        currentRingerMode == RingerModeValues.NORMAL || currentRingerMode == RingerModeValues.VIBRATE
    }

    AutoSilenceMode.Vibrate ->
        RingerModeValues.VIBRATE.takeIf { currentRingerMode == RingerModeValues.NORMAL }

    AutoSilenceMode.DoNotDisturb -> null
}

/** Use priority-only DND, never total silence. */
fun resolveInterruptionFilterToApply(mode: AutoSilenceMode, currentFilter: Int): Int? = when (mode) {
    AutoSilenceMode.DoNotDisturb ->
        InterruptionFilterValues.PRIORITY.takeIf { currentFilter == InterruptionFilterValues.ALL }

    AutoSilenceMode.Silent, AutoSilenceMode.Vibrate -> null
}

/**
 * Restore only if the current mode still matches the app's applied mode; preserve user changes.
 */
fun resolveRingerModeToRestore(session: AutoSilenceSession, currentRingerMode: Int): Int? {
    if (session.appliedRingerMode == RingerModeValues.UNKNOWN) return null
    if (session.previousRingerMode == RingerModeValues.UNKNOWN) return null
    if (currentRingerMode != session.appliedRingerMode) return null
    if (currentRingerMode == session.previousRingerMode) return null
    return session.previousRingerMode
}

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

/** Prefer timing-profile slots, falling back to course reminder times. */
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
