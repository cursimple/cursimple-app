package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.isCourseTemporarilyCancelled
import com.kebiao.viewer.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.kernel.model.targetDates
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.reminder.model.FirstCourseCandidateScope
import com.kebiao.viewer.core.reminder.model.ReminderAction
import com.kebiao.viewer.core.reminder.model.ReminderActionType
import com.kebiao.viewer.core.reminder.model.ReminderCondition
import com.kebiao.viewer.core.reminder.model.ReminderConditionMode
import com.kebiao.viewer.core.reminder.model.ReminderConditionType
import com.kebiao.viewer.core.reminder.model.ReminderCustomOccupancy
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderNodeRange
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderTimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal class FirstCourseRuleEvaluator {
    fun expand(
        rule: ReminderRule,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        customOccupancies: List<ReminderCustomOccupancy>,
    ): List<ReminderPlanTarget> {
        val termStart = timingProfile.termStartLocalDate()
        val occurrences = schedule.dailySchedules
            .flatMap { it.courses }
            .flatMap { course ->
                val slot = timingProfile.findSlot(course.time.startNode, course.time.endNode)
                    ?: return@flatMap emptyList()
                courseOccurrenceDates(
                    course = course,
                    termStart = termStart,
                    fromDate = fromDate,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                ).map { courseDate ->
                    CourseOccurrence(
                        course = course,
                        courseDate = courseDate,
                        termWeek = resolveTermWeek(termStart, resolveTemporaryScheduleSourceDate(courseDate, temporaryScheduleOverrides)),
                        slot = slot,
                    )
                }
            }

        return if (rule.usesLegacyFirstCourseShape()) {
            expandLegacy(rule, occurrences)
        } else {
            expandFlexible(rule, occurrences, customOccupancies, termStart)
        }
            .distinctBy { "${it.courseDate}_${it.course.id}_${it.slot.startTime}" }
            .sortedWith(compareBy<ReminderPlanTarget>({ it.courseDate }, { it.slot.startLocalTime() }, { it.course.time.startNode }))
    }

    private fun expandLegacy(
        rule: ReminderRule,
        occurrences: List<CourseOccurrence>,
    ): List<ReminderPlanTarget> {
        val period = rule.period ?: return emptyList()
        return occurrences
            .filter { occurrence ->
                val muted = rule.mutedNodeRanges.any {
                    it.overlaps(occurrence.course.time.startNode, occurrence.course.time.endNode)
                }
                val inPeriod = rule.includesCourseInLegacyPeriod(occurrence.course, occurrence.slot.startLocalTime())
                muted || inPeriod
            }
            .groupBy { it.courseDate }
            .values
            .mapNotNull { dayOccurrences ->
                if (dayOccurrences.any { occurrence ->
                        rule.mutedNodeRanges.any {
                            it.overlaps(occurrence.course.time.startNode, occurrence.course.time.endNode)
                        }
                    }
                ) {
                    return@mapNotNull null
                }
                dayOccurrences
                    .filter { rule.includesCourseInLegacyPeriod(it.course, it.slot.startLocalTime()) }
                    .earliest()
                    ?.toTarget(period)
            }
    }

    private fun expandFlexible(
        rule: ReminderRule,
        occurrences: List<CourseOccurrence>,
        customOccupancies: List<ReminderCustomOccupancy>,
        termStart: LocalDate,
    ): List<ReminderPlanTarget> {
        val baseScope = rule.firstCourseCandidate ?: rule.legacyCandidateScope()
        return occurrences
            .groupBy { it.courseDate }
            .values
            .mapNotNull { dayOccurrences ->
                val date = dayOccurrences.firstOrNull()?.courseDate ?: return@mapNotNull null
                val activeOccupancies = customOccupancies.filter {
                    it.pluginId == rule.pluginId && it.isActiveOn(date, termStart)
                }
                val context = FirstCourseEvaluationContext(
                    rule = rule,
                    date = date,
                    termWeek = resolveTermWeek(termStart, date),
                    dayOccurrences = dayOccurrences,
                    candidateOccurrences = dayOccurrences.filter { it.matchesScope(baseScope, date) },
                    activeOccupancies = activeOccupancies,
                )
                if (!context.conditionsMatch()) return@mapNotNull null
                context.applyActions(baseScope)?.toTarget(rule.period)
            }
    }

    private fun FirstCourseEvaluationContext.conditionsMatch(): Boolean {
        if (rule.conditions.isEmpty()) return true
        return when (rule.conditionMode) {
            ReminderConditionMode.All -> rule.conditions.all { it.matches(this) }
            ReminderConditionMode.Any -> rule.conditions.any { it.matches(this) }
        }
    }

    private fun FirstCourseEvaluationContext.applyActions(
        baseScope: FirstCourseCandidateScope,
    ): CourseOccurrence? {
        val actions = rule.actions.ifEmpty {
            listOf(ReminderAction(ReminderActionType.RemindFirstCandidate))
        }
        for (action in actions) {
            when (action.type) {
                ReminderActionType.Skip -> return null
                ReminderActionType.RemindFirstCandidate -> candidateOccurrences.earliest()?.let { return it }
                ReminderActionType.ContinueAfterNode -> {
                    val afterNode = action.afterNode ?: continue
                    candidateOccurrences
                        .filter { it.course.time.startNode > afterNode }
                        .earliest()
                        ?.let { return it }
                }
                ReminderActionType.ContinueAfterTime -> {
                    val afterTime = action.afterTime?.parseLocalTimeOrNull() ?: continue
                    candidateOccurrences
                        .filter { it.slot.startLocalTime().isAfter(afterTime) }
                        .earliest()
                        ?.let { return it }
                }
                ReminderActionType.UseCandidateScope -> {
                    val nextScope = action.candidateScope ?: baseScope
                    dayOccurrences
                        .filter { it.matchesScope(nextScope, date) }
                        .earliest()
                        ?.let { return it }
                }
            }
        }
        return null
    }

    private fun ReminderCondition.matches(context: FirstCourseEvaluationContext): Boolean {
        return when (type) {
            ReminderConditionType.CourseExistsInNodes -> nodeRange?.let { range ->
                context.dayOccurrences.any { it.course.overlaps(range) }
            } ?: false
            ReminderConditionType.CourseAbsentInNodes -> nodeRange?.let { range ->
                context.dayOccurrences.none { it.course.overlaps(range) }
            } ?: false
            ReminderConditionType.CourseExistsInTime -> timeRange?.let { range ->
                context.dayOccurrences.any { it.slot.overlaps(range) }
            } ?: false
            ReminderConditionType.CourseAbsentInTime -> timeRange?.let { range ->
                context.dayOccurrences.none { it.slot.overlaps(range) }
            } ?: false
            ReminderConditionType.OccupancyExists -> context.activeOccupancies.any { it.matchesOccupancyId(occupancyId) }
            ReminderConditionType.OccupancyAbsent -> context.activeOccupancies.none { it.matchesOccupancyId(occupancyId) }
            ReminderConditionType.OccupancyOverlapsCourse -> context.activeOccupancies
                .filter { it.matchesOccupancyId(occupancyId) }
                .any { occupancy -> context.dayOccurrences.any { occupancy.timeRange.overlaps(it.slot) } }
            ReminderConditionType.OccupancyBeforeCourse -> context.activeOccupancies
                .filter { it.matchesOccupancyId(occupancyId) }
                .any { occupancy ->
                    val end = occupancy.timeRange.endLocalTimeOrNull() ?: return@any false
                    context.candidateOccurrences.any { !end.isAfter(it.slot.startLocalTime()) }
                }
            ReminderConditionType.WeekdayMatches -> daysOfWeek.isEmpty() || context.date.dayOfWeek.value in daysOfWeek
            ReminderConditionType.WeekMatches -> weeks.isEmpty() || context.termWeek in weeks
            ReminderConditionType.DateMatches -> dates.isEmpty() || context.date.toString() in dates
            ReminderConditionType.CourseTextMatches -> {
                val needle = text?.takeIf { it.isNotBlank() } ?: return false
                context.dayOccurrences.any { occurrence ->
                    occurrence.course.title.contains(needle, ignoreCase = true) ||
                        occurrence.course.teacher.contains(needle, ignoreCase = true) ||
                        occurrence.course.location.contains(needle, ignoreCase = true)
                }
            }
        }
    }

    private fun CourseOccurrence.matchesScope(
        scope: FirstCourseCandidateScope,
        date: LocalDate,
    ): Boolean {
        if (scope.daysOfWeek.isNotEmpty() && date.dayOfWeek.value !in scope.daysOfWeek) return false
        if (scope.weeks.isNotEmpty() && termWeek !in scope.weeks) return false
        if (scope.includeDates.isNotEmpty() && date.toString() !in scope.includeDates) return false
        if (date.toString() in scope.excludeDates) return false
        scope.nodeRange?.let { if (!course.overlaps(it)) return false }
        scope.timeRange?.let { if (!slot.overlaps(it)) return false }
        if (scope.categories.isNotEmpty() && course.category !in scope.categories) return false
        scope.titleContains?.takeIf { it.isNotBlank() }?.let {
            if (!course.title.contains(it, ignoreCase = true)) return false
        }
        scope.teacherContains?.takeIf { it.isNotBlank() }?.let {
            if (!course.teacher.contains(it, ignoreCase = true)) return false
        }
        scope.locationContains?.takeIf { it.isNotBlank() }?.let {
            if (!course.location.contains(it, ignoreCase = true)) return false
        }
        return true
    }

    private fun ReminderRule.legacyCandidateScope(): FirstCourseCandidateScope = FirstCourseCandidateScope(
        nodeRange = if (periodStartNode != null && periodEndNode != null) {
            ReminderNodeRange(periodStartNode, periodEndNode).normalized()
        } else {
            null
        },
        categories = emptyList(),
    )

    private fun ReminderRule.usesLegacyFirstCourseShape(): Boolean =
        firstCourseCandidate == null && conditions.isEmpty() && actions.isEmpty()

    private fun ReminderRule.includesCourseInLegacyPeriod(course: CourseItem, startTime: LocalTime): Boolean {
        val periodStart = periodStartNode
        val periodEnd = periodEndNode
        if (periodStart != null && periodEnd != null) {
            val normalizedStart = minOf(periodStart, periodEnd)
            val normalizedEnd = maxOf(periodStart, periodEnd)
            return course.time.startNode <= normalizedEnd && course.time.endNode >= normalizedStart
        }
        return period?.includes(startTime) == true
    }

    private fun ReminderDayPeriod.includes(time: LocalTime): Boolean = when (this) {
        ReminderDayPeriod.Morning -> time.isBefore(NOON)
        ReminderDayPeriod.Afternoon -> !time.isBefore(NOON) && time.isBefore(EVENING)
        ReminderDayPeriod.Evening -> !time.isBefore(EVENING)
    }

    private fun CourseItem.overlaps(range: ReminderNodeRange): Boolean =
        range.normalized().overlaps(time.startNode, time.endNode)

    private fun ClassSlotTime.overlaps(range: ReminderTimeRange): Boolean {
        val start = range.startLocalTimeOrNull() ?: return false
        val end = range.endLocalTimeOrNull() ?: return false
        return startLocalTime().isBefore(end) && endLocalTime().isAfter(start)
    }

    private fun ReminderTimeRange.overlaps(slot: ClassSlotTime): Boolean {
        val start = startLocalTimeOrNull() ?: return false
        val end = endLocalTimeOrNull() ?: return false
        return start.isBefore(slot.endLocalTime()) && end.isAfter(slot.startLocalTime())
    }

    private fun ReminderCustomOccupancy.isActiveOn(date: LocalDate, termStart: LocalDate): Boolean {
        val week = resolveTermWeek(termStart, date)
        if (daysOfWeek.isNotEmpty() && date.dayOfWeek.value !in daysOfWeek) return false
        if (weeks.isNotEmpty() && week !in weeks) return false
        if (includeDates.isNotEmpty() && date.toString() !in includeDates) return false
        if (date.toString() in excludeDates) return false
        return true
    }

    private fun ReminderCustomOccupancy.matchesOccupancyId(id: String?): Boolean =
        id.isNullOrBlank() || occupancyId == id

    private fun List<CourseOccurrence>.earliest(): CourseOccurrence? =
        minWithOrNull(
            compareBy<CourseOccurrence>(
                { it.slot.startLocalTime() },
                { it.course.time.startNode },
                { it.course.time.endNode },
                { it.course.title },
                { it.course.id },
            ),
        )

    private fun CourseOccurrence.toTarget(period: ReminderDayPeriod?): ReminderPlanTarget =
        ReminderPlanTarget(
            course = course,
            courseDate = courseDate,
            slot = slot,
            period = period,
        )

    private fun courseOccurrenceDates(
        course: CourseItem,
        termStart: LocalDate,
        fromDate: LocalDate,
        temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    ): List<LocalDate> {
        val weeks = course.weeks.ifEmpty { (1..60).toList() }
        val regularDates = weeks.map { week ->
            termStart
                .plusWeeks((week - 1).toLong())
                .plusDays((course.time.dayOfWeek - 1).toLong())
        }
        val overrideTargetDates = temporaryScheduleOverrides.flatMap { it.targetDates() }
        return (regularDates + overrideTargetDates)
            .distinct()
            .filterNot { it.isBefore(fromDate) }
            .filter { date ->
                val sourceDate = resolveTemporaryScheduleSourceDate(date, temporaryScheduleOverrides)
                sourceDate.dayOfWeek.value == course.time.dayOfWeek &&
                    course.isActiveOnSourceDate(termStart, sourceDate) &&
                    !isCourseTemporarilyCancelled(date, course, temporaryScheduleOverrides)
            }
    }

    private fun CourseItem.isActiveOnSourceDate(termStart: LocalDate, sourceDate: LocalDate): Boolean {
        if (weeks.isEmpty()) return true
        return resolveTermWeek(termStart, sourceDate) in weeks
    }

    private fun resolveTermWeek(termStart: LocalDate, date: LocalDate): Int {
        val termStartMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(termStartMonday, dateMonday).toInt() + 1
    }

    private fun String.parseLocalTimeOrNull(): LocalTime? =
        runCatching { LocalTime.parse(this) }.getOrNull()

    private fun ReminderTimeRange.startLocalTimeOrNull(): LocalTime? = startTime.parseLocalTimeOrNull()

    private fun ReminderTimeRange.endLocalTimeOrNull(): LocalTime? = endTime.parseLocalTimeOrNull()

    private data class FirstCourseEvaluationContext(
        val rule: ReminderRule,
        val date: LocalDate,
        val termWeek: Int,
        val dayOccurrences: List<CourseOccurrence>,
        val candidateOccurrences: List<CourseOccurrence>,
        val activeOccupancies: List<ReminderCustomOccupancy>,
    )

    private data class CourseOccurrence(
        val course: CourseItem,
        val courseDate: LocalDate,
        val termWeek: Int,
        val slot: ClassSlotTime,
    )

    private companion object {
        val NOON: LocalTime = LocalTime.NOON
        val EVENING: LocalTime = LocalTime.of(18, 0)
    }
}

internal data class ReminderPlanTarget(
    val course: CourseItem,
    val courseDate: LocalDate,
    val slot: ClassSlotTime,
    val period: ReminderDayPeriod?,
)
