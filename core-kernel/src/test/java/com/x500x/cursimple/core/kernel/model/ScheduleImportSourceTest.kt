package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ScheduleImportSourceTest {
    private val json = Json { encodeDefaults = true }

    @Test fun legacySchedulesRemainReadableWithoutGuessingAnImporter() {
        val old = json.decodeFromString<TermSchedule>("""{"termId":"old","updatedAt":"2026-09-07","dailySchedules":[]}""")
        assertNull(old.sourcePluginId)
        assertNull(old.legacyDisplayPluginId)
        assertFalse(old.externalImport)
    }

    @Test fun eachTimetableRetainsItsOwnSourceAcrossPersistence() {
        val first = TermSchedule("a", "today", sourcePluginId = "plugin-a")
        val second = first.copy(termId = "b", sourcePluginId = null, legacyDisplayPluginId = "plugin-b")
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TermSchedule.serializer()), listOf(first, second))
        assertEquals(listOf(first, second), json.decodeFromString<List<TermSchedule>>(encoded))
    }

    @Test fun externalImportRemovesClaimedSourceWhilePreservingAllCourseData() {
        val course = CourseItem("c", "高等数学", teacher = "教师", location = "教室", weekTeachers = mapOf(2 to "代课教师"),
            weekLocations = mapOf(3 to "实验室"), weeks = listOf(1, 2, 3), time = CourseTimeSlot(1, 1, 1))
        val claimed = TermSchedule("term", "today", listOf(DailySchedule(1, listOf(course))), "yangtzeu-eams-js-v1", "legacy-plugin")
        val received = json.decodeFromString<TermSchedule>(json.encodeToString(TermSchedule.serializer(), claimed)).asExternalImport()
        assertNull(received.sourcePluginId)
        assertNull(received.legacyDisplayPluginId)
        assertTrue(received.externalImport)
        assertEquals(claimed.dailySchedules, received.dailySchedules)
        assertEquals(claimed.termId, received.termId)
        assertEquals(claimed.updatedAt, received.updatedAt)
        assertEquals("yangtzeu-eams-js-v1", claimed.sourcePluginId)
    }
}
