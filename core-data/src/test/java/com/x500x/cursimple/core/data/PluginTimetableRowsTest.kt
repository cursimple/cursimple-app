package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PluginTimetableRowsTest {
    private val owner = "yangtzeu-eams-js-v1"
    private val noon = PluginTimetableRowSetting("midday", "显示午间课", startNode = 3, endNode = 3, slotLabel = "午间课", startTime = "12:00", endTime = "13:35")
    private val night = noon.copy(id = "evening", startNode = 6, endNode = 6, slotLabel = "晚间课", startTime = "18:00", endTime = "18:45")
    private val profile = TermTimingProfile("2026-09-07", listOf(
        ClassSlotTime(1, 1, "08:00", "09:35", "第一节"), ClassSlotTime(2, 2, "10:05", "11:40", "第二节"),
        ClassSlotTime(3, 3, "12:00", "13:35", "午间课"), ClassSlotTime(4, 4, "14:00", "15:35", "第三节"),
        ClassSlotTime(5, 5, "16:05", "17:40", "第四节"), ClassSlotTime(6, 6, "18:00", "18:45", "晚间课"),
        ClassSlotTime(7, 7, "19:00", "20:35", "第五节"), ClassSlotTime(8, 8, "20:45", "22:20", "第六节")))
    private val schema = PluginUiSchema(timetableRows = listOf(noon, night), timetableRowFormat = profile.slotTimes)
    private val imported = TermSchedule("2026-fall", "2026-09-07", sourcePluginId = owner)
    private val off = mapOf("midday" to false, "evening" to false)
    private val start = LocalDate.of(2026, 9, 7)
    private val course = CourseItem("class", "课", weeks = listOf(1), time = CourseTimeSlot(1, 3, 3))

    private fun hidden(schedule: TermSchedule? = imported, timing: TermTimingProfile? = profile,
        prefs: Map<String, Boolean> = off, courses: List<CourseItem> = emptyList(),
        overrides: List<TemporaryScheduleOverride> = emptyList(), definition: PluginUiSchema = schema,
        plugin: String = owner) = hiddenPluginTimetableRows(plugin, definition, prefs, schedule, timing, courses, overrides, start)

    @Test fun bothRowsDefaultOnAndToggleImmediatelyWithoutChangingTheSchedule() {
        assertTrue(noon.defaultVisible)
        assertTrue(hidden(prefs = emptyMap()).isEmpty())
        assertEquals(setOf(3 to 3, 6 to 6), hidden())
        assertEquals(setOf(6 to 6), hidden(prefs = off + ("midday" to true)))
        assertTrue(hidden(prefs = mapOf("midday" to true, "evening" to true)).isEmpty())
        assertEquals(8, profile.slotTimes.size)
        assertEquals(owner, imported.sourcePluginId)
    }

    @Test fun manualSharedAiAndUnprovenLegacyTimetablesKeepAllRowsEvenWithIdenticalTimes() {
        assertTrue(hidden(schedule = imported.copy(sourcePluginId = null)).isEmpty())
        assertTrue(hidden(schedule = imported.asExternalImport()).isEmpty())
        assertTrue(hidden(schedule = null).isEmpty())
        assertTrue(hidden(schedule = imported.copy(sourcePluginId = "")).isEmpty())
    }

    @Test fun confirmedLegacyImportEnablesExistingRowsWithoutChangingCoursesOrTiming() {
        val old = imported.copy(sourcePluginId = null, dailySchedules = listOf(DailySchedule(1, listOf(course))))
        assertTrue(canBindLegacyTimetableDisplayPlugin(owner, schema, old, profile))
        assertTrue(hidden(schedule = old, courses = listOf(course)).isEmpty())
        val bound = legacyTimetableDisplayBinding(old, old, owner, schema, profile, true)!!
        assertNull(bound.sourcePluginId)
        assertEquals(owner, bound.legacyDisplayPluginId)
        assertEquals(old.dailySchedules, bound.dailySchedules)
        assertEquals(old.updatedAt, bound.updatedAt)
        assertEquals(setOf(6 to 6), hidden(schedule = bound, courses = listOf(course)))
        assertEquals(setOf(3 to 3, 6 to 6), hidden(schedule = bound))
    }

    @Test fun legacyConfirmationDoesNotCarryOverToAnotherTimetableOrPlugin() {
        val old = imported.copy(sourcePluginId = null)
        val bound = legacyTimetableDisplayBinding(old, old, owner, schema, profile, true)!!
        assertEquals(setOf(3 to 3, 6 to 6), hidden(schedule = bound))
        assertTrue(hidden(schedule = old.copy(termId = "other")).isEmpty())
        assertTrue(hidden(schedule = bound, plugin = "school-b").isEmpty())
        assertFalse(canBindLegacyTimetableDisplayPlugin("school-b", schema, bound, profile))
        assertNull(legacyTimetableDisplayBinding(bound, bound, "school-b", schema, profile, true))
    }

    @Test fun onlyUnclassifiedLegacyDataWithTheCompleteFormatCanBeConfirmed() {
        val old = imported.copy(sourcePluginId = null)
        val missingNight = profile.copy(slotTimes = profile.slotTimes.filterNot { it.startNode == 6 })
        assertFalse(canBindLegacyTimetableDisplayPlugin(owner, schema, old, missingNight))
        assertNull(legacyTimetableDisplayBinding(old, old, owner, schema, missingNight, true))
        assertFalse(canBindLegacyTimetableDisplayPlugin(owner, schema, imported, profile))
        assertFalse(canBindLegacyTimetableDisplayPlugin(owner, schema, imported.copy(sourcePluginId = "school-b"), profile))
        assertFalse(canBindLegacyTimetableDisplayPlugin(owner, schema, null, profile))
    }

    @Test fun externalImportsCannotClaimOrRetainLegacyDisplayOwnership() {
        val old = imported.copy(sourcePluginId = null)
        val bound = legacyTimetableDisplayBinding(old, old, owner, schema, profile, true)!!
        val external = bound.asExternalImport()
        assertTrue(external.externalImport)
        assertNull(external.legacyDisplayPluginId)
        assertFalse(canBindLegacyTimetableDisplayPlugin(owner, schema, external, profile))
        assertNull(legacyTimetableDisplayBinding(external, external, owner, schema, profile, true))
        assertTrue(hidden(schedule = external.copy(legacyDisplayPluginId = owner, sourcePluginId = owner)).isEmpty())
    }

    @Test fun aConcurrentImportOrEditCannotBeOverwrittenByTheCompatibilityAction() {
        val old = imported.copy(sourcePluginId = null)
        val changed = old.copy(dailySchedules = listOf(DailySchedule(1, listOf(course))))
        assertNull(legacyTimetableDisplayBinding(changed, old, owner, schema, profile, true))
        assertNull(legacyTimetableDisplayBinding(imported, old, owner, schema, profile, true))
        assertNull(legacyTimetableDisplayBinding(null, old, owner, schema, profile, true))
    }

    @Test fun removingCompatibilityRestoresUnclassifiedDataEvenAfterTheFormatChanges() {
        val old = imported.copy(sourcePluginId = null)
        val bound = legacyTimetableDisplayBinding(old, old, owner, schema, profile, true)!!
        assertEquals(old, legacyTimetableDisplayBinding(bound, bound, owner, schema, null, false))
        assertNull(legacyTimetableDisplayBinding(bound, bound, "school-b", schema, profile, false))
        assertNull(legacyTimetableDisplayBinding(imported, imported, owner, schema, profile, false))
    }

    @Test fun anotherSchoolOrPluginCannotUseThisPluginsSavedOffChoice() {
        assertTrue(hidden(schedule = imported.copy(sourcePluginId = "school-b")).isEmpty())
        assertTrue(hidden(plugin = "school-b").isEmpty())
        assertTrue(hidden(plugin = "").isEmpty())
        // Returning to the original term reads its source, independently of global plugin selection.
        assertEquals(setOf(3 to 3, 6 to 6), hidden())
    }

    @Test fun fullFormatRequiresBothOptionalRowsAndAllRegularRows() {
        for (missing in profile.slotTimes.indices) {
            assertTrue(hidden(timing = profile.copy(slotTimes = profile.slotTimes.filterIndexed { i, _ -> i != missing })).isEmpty())
        }
        assertTrue(hidden(timing = profile.copy(slotTimes = profile.slotTimes + profile.slotTimes.first())).isEmpty())
        assertTrue(hidden(timing = null).isEmpty())
    }

    @Test fun changedTimesLabelsOrPeriodNumberingMakeTheFormatInapplicable() {
        val first = profile.slotTimes.first()
        for (changed in listOf(first.copy(startTime = "08:10"), first.copy(endTime = "09:40"),
            first.copy(startNode = 9, endNode = 9), first.copy(label = "其他节次"), first.copy(startTime = "bad"))) {
            assertTrue(hidden(timing = profile.copy(slotTimes = listOf(changed) + profile.slotTimes.drop(1))).isEmpty())
        }
        assertTrue(hidden(timing = profile.copy(slotTimes = profile.slotTimes.map { it.copy(startNode = it.startNode * 2 - 1, endNode = it.endNode * 2) })).isEmpty())
    }

    @Test fun equivalentClockNotationAndUnsortedSlotsStillMatchTheCompleteFormat() {
        val equivalent = profile.copy(slotTimes = profile.slotTimes.reversed().map { it.copy(startTime = it.startTime + ":00") })
        assertEquals(setOf(3 to 3, 6 to 6), hidden(timing = equivalent))
    }

    @Test fun incompleteOrMalformedPluginDeclarationsCannotHideRows() {
        assertTrue(hidden(definition = schema.copy(timetableRowFormat = emptyList())).isEmpty())
        assertTrue(hidden(definition = schema.copy(timetableRows = emptyList())).isEmpty())
        assertTrue(hidden(definition = schema.copy(timetableRows = listOf(noon, noon))).isEmpty())
        assertTrue(hidden(definition = schema.copy(timetableRows = listOf(noon.copy(startNode = 1)))).isEmpty())
        assertTrue(hidden(definition = schema.copy(timetableRowFormat = profile.slotTimes.map { it.copy(startTime = "bad") })).isEmpty())
    }

    @Test fun importedOrManualClassesForceTheRowVisibleEvenWithASavedOffChoice() {
        assertEquals(setOf(6 to 6), hidden(courses = listOf(course)))
        assertEquals(setOf(6 to 6), hidden(courses = listOf(course.copy(time = CourseTimeSlot(2, 2, 4)))))
        assertEquals(setOf(3 to 3, 6 to 6), hidden(courses = listOf(course.copy(hidden = true), course.copy(reminderOnly = true))))
    }

    @Test fun movingEveryNoonMeetingOutMakesNoonEmptyAndItsDestinationOccupied() {
        val move = TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse, sourceDate = start.toString(),
            targetDate = start.plusDays(3).toString(), moveCourseId = course.id, moveToStartNode = 6, moveToEndNode = 6)
        assertEquals(setOf(3 to 3), hidden(courses = listOf(course), overrides = listOf(move)))
    }

    @Test fun cancellingAllMeetingsAllowsTheCorrespondingEmptyRowToHide() {
        val cancel = TemporaryScheduleOverride("cancel", TemporaryScheduleOverrideType.CancelCourse, targetDate = start.toString(),
            cancelCourseId = course.id, cancelStartNode = 3, cancelEndNode = 3)
        assertEquals(setOf(3 to 3, 6 to 6), hidden(courses = listOf(course), overrides = listOf(cancel)))
    }

    @Test fun missingTermStartUsesConservativeMoveChecksWithoutIgnoringASingleNodeDestination() {
        val morning = course.copy(time = CourseTimeSlot(1, 1, 1))
        val move = TemporaryScheduleOverride("move", TemporaryScheduleOverrideType.MoveCourse, sourceDate = start.toString(),
            targetDate = start.plusDays(1).toString(), moveCourseId = morning.id, moveToStartNode = 3)
        assertTrue(pluginRowHasCourses(noon, listOf(morning), listOf(move)))
        assertFalse(pluginRowHasCourses(noon, listOf(morning), listOf(move.copy(targetDate = "invalid"))))
    }

    @Test fun invalidAndDuplicateDeclarationsCannotHideUnrelatedRows() {
        assertEquals(listOf(noon), validPluginTimetableRows(listOf(noon, noon, noon.copy(id = "bad key"), noon.copy(startNode = 0), noon.copy(slotLabel = ""))))
    }

    @Test fun preferenceDecodingKeepsOwnersSeparateAndMalformedValuesFallBackToDefaults() {
        assertEquals(mapOf("school-a" to mapOf("midday" to false), "school-b" to mapOf("midday" to true)),
            decodePluginTimetableRows("""{"school-a":{"midday":false},"school-b":{"midday":true}}"""))
        assertTrue(decodePluginTimetableRows("""{"school-a":{"midday":"bad"}}""").isEmpty())
        assertTrue(decodePluginTimetableRows("").isEmpty())
    }
}
