package com.x500x.cursimple.core.reminder.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScopeTypeTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun legacyScopeTypeValuesStillDeserialize() {
        val payload = """
            [
              {"ruleId":"a","pluginId":"demo","scopeType":"single_course","courseId":"math",
               "advanceMinutes":15,"createdAt":"2026-02-23T00:00:00+08:00","updatedAt":"2026-02-23T00:00:00+08:00"},
              {"ruleId":"b","pluginId":"demo","scopeType":"time_slot","dayOfWeek":1,"startNode":1,"endNode":2,
               "advanceMinutes":15,"createdAt":"2026-02-23T00:00:00+08:00","updatedAt":"2026-02-23T00:00:00+08:00"},
              {"ruleId":"c","pluginId":"demo","scopeType":"exam","mutedCourseIds":["exam-a"],
               "advanceMinutes":40,"createdAt":"2026-02-23T00:00:00+08:00","updatedAt":"2026-02-23T00:00:00+08:00"},
              {"ruleId":"d","pluginId":"demo","scopeType":"first_course_of_period",
               "advanceMinutes":20,"createdAt":"2026-02-23T00:00:00+08:00","updatedAt":"2026-02-23T00:00:00+08:00"},
              {"ruleId":"e","pluginId":"demo","scopeType":"label_rule",
               "advanceMinutes":20,"createdAt":"2026-02-23T00:00:00+08:00","updatedAt":"2026-02-23T00:00:00+08:00"}
            ]
        """.trimIndent()

        val rules = json.decodeFromString(ListSerializer(ReminderRule.serializer()), payload)

        assertEquals(
            listOf(
                ReminderScopeType.SingleCourse,
                ReminderScopeType.TimeSlot,
                ReminderScopeType.Exam,
                ReminderScopeType.FirstCourseOfPeriod,
                ReminderScopeType.LabelRule,
            ),
            rules.map { it.scopeType },
        )
        assertEquals(listOf("exam-a"), rules.first { it.ruleId == "c" }.mutedCourseIds)
    }

    @Test
    fun scopeTypeSerialNamesAreStable() {
        val encoded = ReminderScopeType.entries.map {
            json.encodeToString(ReminderScopeType.serializer(), it).trim('"')
        }

        assertEquals(
            listOf("single_course", "time_slot", "exam", "first_course_of_period", "label_rule"),
            encoded,
        )
    }

    @Test
    fun onlyLabelAndFirstCourseScopesAreSyncable() {
        assertEquals(
            setOf(ReminderScopeType.LabelRule, ReminderScopeType.FirstCourseOfPeriod),
            SYNCABLE_REMINDER_SCOPE_TYPES,
        )
        assertTrue(ReminderScopeType.LabelRule.isSyncable())
        assertTrue(ReminderScopeType.FirstCourseOfPeriod.isSyncable())
        assertTrue(ReminderScopeType.SingleCourse.isLegacy())
        assertTrue(ReminderScopeType.TimeSlot.isLegacy())
        assertTrue(ReminderScopeType.Exam.isLegacy())
        assertFalse(ReminderScopeType.LabelRule.isLegacy())
    }
}
