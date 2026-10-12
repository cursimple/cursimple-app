package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.moveToNodeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import kotlinx.serialization.json.Json

internal fun decodePluginTimetableRows(raw: String): Map<String, Map<String, Boolean>> =
    runCatching { Json.decodeFromString<Map<String, Map<String, Boolean>>>(raw) }.getOrDefault(emptyMap())

fun validPluginTimetableRows(rows: List<PluginTimetableRowSetting>): List<PluginTimetableRowSetting> = rows.filter {
    it.id.matches(Regex("[A-Za-z0-9_.-]{1,64}")) && it.startNode > 0 && it.endNode >= it.startNode && it.slotLabel.isNotBlank()
}.distinctBy { it.id }

/** Require a recorded importer or a per-timetable legacy confirmation, plus the complete format. */
fun pluginTimetableRowsApply(pluginId: String, schema: PluginUiSchema, schedule: TermSchedule?, profile: TermTimingProfile?): Boolean {
    if (pluginId.isBlank() || schedule == null || schedule.externalImport) return false
    val owner = schedule.sourcePluginId ?: schedule.legacyDisplayPluginId
    return owner == pluginId && pluginTimetableFormatMatches(schema, profile)
}

fun canBindLegacyTimetableDisplayPlugin(pluginId: String, schema: PluginUiSchema, schedule: TermSchedule?, profile: TermTimingProfile?): Boolean =
    pluginId.isNotBlank() && schedule != null && !schedule.externalImport && schedule.sourcePluginId == null &&
        (schedule.legacyDisplayPluginId == null || schedule.legacyDisplayPluginId == pluginId) && pluginTimetableFormatMatches(schema, profile)

/** Confirmation changes only legacy display ownership, retaining all timetable data. */
internal fun legacyTimetableDisplayBinding(current: TermSchedule?, expected: TermSchedule, pluginId: String,
    schema: PluginUiSchema, profile: TermTimingProfile?, enabled: Boolean): TermSchedule? {
    if (current == null || current != expected) return null
    if (enabled && !canBindLegacyTimetableDisplayPlugin(pluginId, schema, current, profile)) return null
    if (!enabled && (current.sourcePluginId != null || current.legacyDisplayPluginId != pluginId)) return null
    return current.copy(legacyDisplayPluginId = pluginId.takeIf { enabled })
}

private fun pluginTimetableFormatMatches(schema: PluginUiSchema, profile: TermTimingProfile?): Boolean {
    if (profile == null) return false
    val required = schema.timetableRowFormat.sortedBy { it.startNode }
    val actual = profile.slotTimes.sortedBy { it.startNode }
    if (required.isEmpty() || actual.size != required.size || schema.timetableRows.isEmpty()) return false
    if (validPluginTimetableRows(schema.timetableRows).size != schema.timetableRows.size) return false
    if (required.any { it.startNode !in 1..32 || it.endNode !in it.startNode..32 || it.label.isBlank() }) return false
    if (required.zipWithNext().any { (a, b) -> a.endNode >= b.startNode }) return false
    if (!actual.zip(required).all { (slot, expected) -> matchesTimetableSlot(slot, expected) }) return false
    return schema.timetableRows.all { row ->
        required.any { slot -> matchesTimetableSlot(slot, ClassSlotTime(row.startNode, row.endNode, row.startTime, row.endTime, row.slotLabel)) }
    }
}

private fun matchesTimetableSlot(actual: ClassSlotTime, expected: ClassSlotTime): Boolean {
    val start = runCatching { LocalTime.parse(expected.startTime) }.getOrNull() ?: return false
    val end = runCatching { LocalTime.parse(expected.endTime) }.getOrNull() ?: return false
    return end > start && actual.startNode == expected.startNode && actual.endNode == expected.endNode &&
        actual.label.trim() == expected.label.trim() &&
        runCatching { LocalTime.parse(actual.startTime) }.getOrNull() == start &&
        runCatching { LocalTime.parse(actual.endTime) }.getOrNull() == end
}

/** Courses and individual moves force visibility even after imports change a previously empty row. */
fun pluginRowHasCourses(row: PluginTimetableRowSetting, courses: List<CourseItem>, overrides: List<TemporaryScheduleOverride>,
    termStartDate: LocalDate? = null, maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT): Boolean {
    val visible = courses.filterNot { it.hidden || it.reminderOnly }
    if (termStartDate != null) {
        val weeks = maxOf(maxWeekCount, visible.flatMap { it.weeks }.maxOrNull() ?: 1).coerceAtLeast(1)
        val bounded = visible.map { if (it.weeks.isEmpty()) it.copy(weeks = (1..weeks).toList()) else it }
        val first = termStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until weeks * 7).any { day ->
            coursesScheduledOn(first.plusDays(day.toLong()), bounded, overrides, HolidayCalendarSettings.NONE, termStartDate)
                .any { it.time.startNode <= row.endNode && it.time.endNode >= row.startNode }
        }
    }
    if (visible.any { it.time.startNode <= row.endNode && it.time.endNode >= row.startNode }) return true
    return overrides.any { rule -> rule.type == TemporaryScheduleOverrideType.MoveCourse &&
        visible.any { it.id == rule.moveCourseId } && runCatching { LocalDate.parse(rule.targetDate) }.isSuccess &&
        rule.moveToNodeRange()?.let { it.first <= row.endNode && it.last >= row.startNode } == true }
}

fun hiddenPluginTimetableRows(
    pluginId: String, schema: PluginUiSchema, preferences: Map<String, Boolean>, schedule: TermSchedule?, profile: TermTimingProfile?,
    courses: List<CourseItem>, overrides: List<TemporaryScheduleOverride>,
    termStartDate: LocalDate? = null, maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): Set<Pair<Int, Int>> {
    if (!pluginTimetableRowsApply(pluginId, schema, schedule, profile)) return emptySet()
    return schema.timetableRows.filter { row ->
        !(preferences[row.id] ?: row.defaultVisible) && !pluginRowHasCourses(row, courses, overrides, termStartDate, maxWeekCount)
    }.mapTo(mutableSetOf()) { it.startNode to it.endNode }
}
