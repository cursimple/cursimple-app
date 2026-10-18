package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 合并学期按实际日期对齐：各学期的课平移到合并学期开学后的真实那一周。 */
class TermMergeTest {

    private fun course(id: String, weeks: List<Int>, dayOfWeek: Int = 3) = CourseItem(
        id = id, title = id, weeks = weeks,
        weekLocations = weeks.associateWith { "教室$it" },
        weekTeachers = weeks.associateWith { "老师$it" },
        time = CourseTimeSlot(dayOfWeek, 1, 2),
    )

    private fun source(
        id: String,
        start: String?,
        courses: List<CourseItem> = emptyList(),
        manualCourses: List<CourseItem> = emptyList(),
    ) = MergedTermSource(
        termId = id,
        name = id,
        termStartDate = start,
        schedule = TermSchedule(id, "2026-03-01", listOf(DailySchedule(3, courses))),
        manualCourses = manualCourses,
    )

    private fun allCourses(merged: MergedTermData) =
        merged.schedule?.dailySchedules.orEmpty().flatMap { it.courses }.associateBy { it.id }

    @Test
    fun `a later term shifts forward so both land on the same real dates`() {
        // 甲 2026-02-23 开学；乙晚两周，所以乙的第 1 周是合并表的第 3 周。
        val merged = mergeTerms(
            "merged",
            listOf(
                source("a", "2026-02-23", listOf(course("a1", listOf(1, 3)))),
                source("b", "2026-03-09", listOf(course("b1", listOf(1, 2)))),
            ),
        )

        assertEquals(mapOf("a" to 0, "b" to 2), merged.weekShift)
        assertEquals(listOf(1, 3), allCourses(merged).getValue("a1").weeks)
        assertEquals(listOf(3, 4), allCourses(merged).getValue("b1").weeks)
        assertEquals("2026-02-23", merged.termStartDate)
    }

    @Test
    fun `per-week teachers and locations move with their weeks`() {
        // 甲定下合并起点，乙晚两周，所以乙的每一条按周覆盖都要跟着挪两周。
        val merged = mergeTerms(
            "merged",
            listOf(
                source("a", "2026-02-23", listOf(course("a1", listOf(1)))),
                source("b", "2026-03-09", listOf(course("b1", listOf(1, 2)))),
            ),
        )

        val shifted = allCourses(merged).getValue("b1")
        assertEquals(mapOf(3 to "教室1", 4 to "教室2"), shifted.weekLocations)
        assertEquals(mapOf(3 to "老师1", 4 to "老师2"), shifted.weekTeachers)
    }

    @Test
    fun `the earliest start date wins even when it is not the first source`() {
        val merged = mergeTerms(
            "merged",
            listOf(
                source("late", "2026-03-09", listOf(course("l1", listOf(1)))),
                source("early", "2026-02-23", listOf(course("e1", listOf(1)))),
            ),
        )

        assertEquals("2026-02-23", merged.termStartDate)
        assertEquals(mapOf("late" to 2, "early" to 0), merged.weekShift)
        assertEquals(listOf(1), allCourses(merged).getValue("e1").weeks)
        assertEquals(listOf(3), allCourses(merged).getValue("l1").weeks)
    }

    @Test
    fun `a term without a start date keeps its weeks as they are`() {
        val merged = mergeTerms(
            "merged",
            listOf(
                source("dated", "2026-02-23", listOf(course("d1", listOf(1)))),
                source("undated", null, listOf(course("u1", listOf(1)))),
            ),
        )

        assertEquals("2026-02-23", merged.termStartDate)
        assertEquals(0, merged.weekShift.getValue("undated"))
        assertEquals(listOf(1), allCourses(merged).getValue("u1").weeks)
    }

    @Test
    fun `no source has a start date so the merged term has none either`() {
        val merged = mergeTerms("merged", listOf(source("x", null, listOf(course("x1", listOf(2))))))

        assertNull(merged.termStartDate)
        assertEquals(listOf(2), allCourses(merged).getValue("x1").weeks)
    }

    @Test
    fun `two terms sharing a course id keep only one copy`() {
        val merged = mergeTerms(
            "merged",
            listOf(
                source("a", "2026-02-23", listOf(course("same", listOf(1)))),
                source("b", "2026-03-09", listOf(course("same", listOf(1)))),
            ),
        )

        assertEquals(1, allCourses(merged).size)
        assertEquals(listOf(1), allCourses(merged).getValue("same").weeks)
    }

    @Test
    fun `per-week courses and manual courses merge into one timetable`() {
        val merged = mergeTerms(
            "merged",
            listOf(
                source("a", "2026-02-23", listOf(course("a1", listOf(1)))),
                source("b", "2026-03-09", manualCourses = listOf(course("m1", listOf(1)))),
            ),
        )

        assertEquals(listOf("a1"), allCourses(merged).keys.toList())
        assertEquals(listOf("m1"), merged.manualCourses.map { it.id })
        assertEquals(listOf(3), merged.manualCourses.single().weeks)
    }

    @Test
    fun `the merged term reviews as one user-owned timetable`() {
        val merged = mergeTerms(
            "merged",
            listOf(
                source("a", "2026-02-23", listOf(course("a1", listOf(1)))),
                source("b", "2026-03-09", listOf(course("b1", listOf(1)))),
            ),
        )

        assertEquals("merged", merged.schedule?.termId)
        assertNull(merged.schedule?.sourcePluginId)
        assertNull(merged.schedule?.legacyDisplayPluginId)
        assertEquals("2026-03-01", merged.schedule?.updatedAt)
    }

    @Test
    fun `events merge across terms and deduplicate by id`() {
        val event1 = ScheduleEvent("ev1", "Meeting", "2026-03-10", "14:00", "15:00")
        val event2 = ScheduleEvent("ev2", "Club", "2026-03-11", "18:00", "20:00")
        val eventDuplicate = ScheduleEvent("ev1", "Meeting Copy", "2026-03-10", "14:00", "15:00")
        val merged = mergeTerms(
            "merged",
            listOf(
                MergedTermSource("a", "a", "2026-02-23", events = listOf(event1, event2)),
                MergedTermSource("b", "b", "2026-03-09", events = listOf(eventDuplicate)),
            ),
        )

        assertEquals(2, merged.events.size)
        assertEquals(listOf("ev1", "ev2"), merged.events.map { it.id })
    }

    @Test
    fun `by week number alignment anchors on newest term and avoids 52-week offset`() {
        // 跨学年场景：去年的课表 (2025-02-24) 与今年的课表 (2026-03-02)
        val source2025 = source("last_year", "2025-02-24", listOf(course("old_course", listOf(1, 2, 3))))
        val source2026 = source("this_year", "2026-03-02", listOf(course("new_course", listOf(1, 2, 4))))

        // 默认按真实日期对齐：旧课表将顺延 53 周
        val realDateMerged = mergeTerms("merged_real", listOf(source2025, source2026), TermMergeAlignment.ByRealDate)
        assertEquals("2025-02-24", realDateMerged.termStartDate)
        assertEquals(mapOf("last_year" to 0, "this_year" to 53), realDateMerged.weekShift)

        // 按教学周次对齐：以最新学期为基准，第 1 周对齐第 1 周，消除 53 周断层
        val weekNumberMerged = mergeTerms("merged_week", listOf(source2025, source2026), TermMergeAlignment.ByWeekNumber)
        assertEquals("2026-03-02", weekNumberMerged.termStartDate)
        assertEquals(mapOf("last_year" to 0, "this_year" to 0), weekNumberMerged.weekShift)
        assertEquals(listOf(1, 2, 3), allCourses(weekNumberMerged).getValue("old_course").weeks)
        assertEquals(listOf(1, 2, 4), allCourses(weekNumberMerged).getValue("new_course").weeks)
    }

    @Test
    fun `specifying anchor term sets merged start date to that term and calculates relative shifts`() {
        val source2025 = source("last_year", "2025-02-24", listOf(course("old_course", listOf(1, 2))))
        val source2026 = source("this_year", "2026-03-02", listOf(course("new_course", listOf(1, 2))))

        val merged = mergeTerms(
            "merged",
            listOf(source2025, source2026),
            TermMergeAlignment.ByRealDate,
            anchorTermId = "this_year",
        )

        assertEquals("2026-03-02", merged.termStartDate)
        assertEquals(mapOf("last_year" to -53, "this_year" to 0), merged.weekShift)
        assertEquals(listOf("new_course"), allCourses(merged).keys.toList())
    }

    @Test
    fun `dual degree timetables merge seamlessly across weekdays and weekends`() {
        // 主修专业：周一数据结构、周三操作系统
        val majorCourse1 = CourseItem(id = "major_ds", title = "数据结构", weeks = listOf(1, 2, 3), time = CourseTimeSlot(1, 1, 2))
        val majorCourse2 = CourseItem(id = "major_os", title = "操作系统", weeks = listOf(1, 2, 3), time = CourseTimeSlot(3, 3, 4))
        val majorSource = MergedTermSource(
            termId = "major",
            name = "主修·计算机",
            termStartDate = "2026-03-02",
            schedule = TermSchedule("major", "2026-03-01", listOf(
                DailySchedule(1, listOf(majorCourse1)),
                DailySchedule(3, listOf(majorCourse2)),
            )),
        )

        // 双学位/辅修专业：周三撞课槽位（冲突）、周六周末课
        val dualCourse1 = CourseItem(id = "dual_econ", title = "微观经济学", weeks = listOf(1, 2), time = CourseTimeSlot(3, 3, 4))
        val dualCourse2 = CourseItem(id = "dual_fin", title = "公司金融", weeks = (1..16).toList(), time = CourseTimeSlot(6, 1, 4))
        val dualSource = MergedTermSource(
            termId = "dual",
            name = "双学位·金融",
            termStartDate = "2026-03-02",
            schedule = TermSchedule("dual", "2026-03-01", listOf(
                DailySchedule(3, listOf(dualCourse1)),
                DailySchedule(6, listOf(dualCourse2)),
            )),
        )

        val merged = mergeTerms("merged", listOf(majorSource, dualSource))

        assertEquals("2026-03-02", merged.termStartDate)
        val courses = allCourses(merged)
        assertEquals(4, courses.size)
        // 周三同时包含主修操作系统与双学位微观经济学两门课
        val wednesdayCourses = merged.schedule?.dailySchedules?.firstOrNull { it.dayOfWeek == 3 }?.courses.orEmpty()
        assertEquals(listOf("major_os", "dual_econ"), wednesdayCourses.map { it.id })
        // 自动注入课程来源学期标签
        assertEquals(
            "主修·计算机",
            wednesdayCourses.first { it.id == "major_os" }.details.first { it.label == "来源学期" }.value,
        )
        assertEquals(
            "双学位·金融",
            wednesdayCourses.first { it.id == "dual_econ" }.details.first { it.label == "来源学期" }.value,
        )
        // 周六包含双学位公司金融
        val saturdayCourses = merged.schedule?.dailySchedules?.firstOrNull { it.dayOfWeek == 6 }?.courses.orEmpty()
        assertEquals(listOf("dual_fin"), saturdayCourses.map { it.id })
        assertEquals(
            "双学位·金融",
            saturdayCourses.first { it.id == "dual_fin" }.details.first { it.label == "来源学期" }.value,
        )
    }
}
