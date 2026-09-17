package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleModelsTest {

    @Test
    fun coursesOfDay_returnsMatchedCourses() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-25T08:00:00Z",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "c1",
                            title = "高等数学",
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, schedule.coursesOfDay(1).size)
        assertEquals(0, schedule.coursesOfDay(2).size)
    }

    @Test
    fun placeholderCourseRoundTripsAndCanBeFilteredForDisplay() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val course = CourseItem(
            id = "placeholder",
            title = "早自习",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            reminderOnly = true,
            slotLabelOverride = "早自习",
            reminderStartTime = "07:10",
            reminderEndTime = "07:50",
        )

        val decoded = json.decodeFromString(CourseItem.serializer(), json.encodeToString(CourseItem.serializer(), course))

        assertEquals(true, decoded.isReminderOnly())
        assertEquals("早自习", decoded.slotLabelOverride)
        assertEquals(emptyList<CourseItem>(), listOf(decoded).visibleScheduleCourses())
        assertEquals(listOf(decoded), listOf(decoded))
    }

    @Test
    fun reminderSlotLabelUsesOverrideBeforeTimingProfileLabel() {
        val timingProfile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(ClassSlotTime(1, 1, "08:00", "08:45", "第一节课")),
        )
        val course = CourseItem(
            id = "placeholder",
            title = "早自习",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
            reminderOnly = true,
            slotLabelOverride = "早自习",
        )

        assertEquals("早自习", course.reminderSlotLabel(timingProfile))
    }

    @Test
    fun manualCourseOverridesThePluginCourseWithTheSameId() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-25T08:00:00Z",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        courseOf("shared", "插件版"),
                        courseOf("plugin-only", "只有插件有"),
                    ),
                ),
            ),
        )

        val merged = schedule.allCoursesWith(listOf(courseOf("shared", "改过的"), courseOf("m1", "自己加的")))

        // 改过的那门只能算一次，否则网格里会并排显示新旧两份，提醒也会重复响
        assertEquals(listOf("plugin-only", "shared", "m1"), merged.map { it.id })
        assertEquals("改过的", merged.first { it.id == "shared" }.title)
    }

    @Test
    fun mergingWithoutManualCoursesKeepsThePluginListIntact() {
        val plugin = listOf(courseOf("a"), courseOf("b"))

        assertEquals(plugin, mergeCourseSources(plugin, emptyList()))
        assertEquals(plugin, TermSchedule("t", "now", listOf(DailySchedule(1, plugin))).allCoursesWith(emptyList()))
    }

    @Test
    fun mergingWithoutAScheduleLeavesOnlyManualCourses() {
        val manual = listOf(courseOf("m1"))

        assertEquals(manual, (null as TermSchedule?).allCoursesWith(manual))
    }

    @Test
    fun deletingAPluginCourseHidesItEverywhereWhileKeepingTheTombstone() {
        val schedule = TermSchedule(
            termId = "t",
            updatedAt = "now",
            dailySchedules = listOf(DailySchedule(1, listOf(courseOf("plugin-a"), courseOf("plugin-b")))),
        )
        // 删掉插件课 = 按原 id 存一条标了 hidden 的手动课，原件与墓碑一起从结果里消失
        val manual = listOf(courseOf("plugin-a").copy(hidden = true))

        assertEquals(listOf("plugin-b"), schedule.allCoursesWith(manual).map { it.id })
        // 墓碑本身仍留在手动课程里，用户才能恢复
        assertEquals(listOf("plugin-a"), manual.hiddenCourses().map { it.id })
    }

    @Test
    fun deletingAManualCourseNeedsNoTombstone() {
        val manual = listOf(courseOf("m1"), courseOf("m2").copy(hidden = true))

        assertEquals(listOf("m1"), (null as TermSchedule?).allCoursesWith(manual).map { it.id })
    }

    private fun courseOf(id: String, title: String = id) = CourseItem(
        id = id,
        title = title,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )
}

