package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.CourseDetailField
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.assertPluginError
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDraftTest {
    @Test
    fun `draft converts courses into grouped term schedule`() {
        val schedule = ScheduleDraft(
            termId = "2026-spring",
            courses = listOf(
                ScheduleDraftCourse(
                    title = "线性代数",
                    dayOfWeek = 3,
                    startNode = 3,
                    endNode = 4,
                    weeks = listOf(4, 2, 2),
                ),
                ScheduleDraftCourse(
                    title = "高等数学",
                    teacher = "张老师",
                    location = "A101",
                    dayOfWeek = 1,
                    startNode = 1,
                    endNode = 2,
                    weeks = listOf(1, 2, 3),
                ),
            ),
        ).toTermSchedule()

        assertEquals("2026-spring", schedule.termId)
        assertEquals(listOf(1, 3), schedule.dailySchedules.map { it.dayOfWeek })
        val firstCourse = schedule.dailySchedules.first().courses.single()
        assertEquals("高等数学", firstCourse.title)
        assertTrue(firstCourse.id.startsWith("plugin-"))
        val secondCourse = schedule.dailySchedules.last().courses.single()
        assertEquals(listOf(2, 4), secondCourse.weeks)
    }

    @Test
    fun `draft rejects invalid day`() {
        val error = runCatching {
            ScheduleDraft(
                termId = "2026-spring",
                courses = listOf(
                    ScheduleDraftCourse(
                        title = "高等数学",
                        dayOfWeek = 8,
                        startNode = 1,
                        endNode = 2,
                    ),
                ),
            ).toTermSchedule()
        }.exceptionOrNull()

        assertPluginError(R.string.plugin_error_draft_invalid_day_of_week, error, 8)
    }

    @Test
    fun `draft enforces max course count`() {
        val error = runCatching {
            ScheduleDraft(
                termId = "2026-spring",
                courses = listOf(
                    ScheduleDraftCourse(
                        title = "高等数学",
                        dayOfWeek = 1,
                        startNode = 1,
                        endNode = 2,
                    ),
                ),
            ).toTermSchedule(PluginRuntimeLimits(maxCourses = 0))
        }.exceptionOrNull()

        assertPluginError(R.string.plugin_error_draft_too_many_courses, error)
    }

    @Test
    fun `插件给的课程序号这类附加信息带进课程，空的与重名的丢掉`() {
        val course = ScheduleDraft(
            termId = "t",
            courses = listOf(
                ScheduleDraftCourse(
                    title = "数值分析",
                    dayOfWeek = 5,
                    startNode = 2,
                    endNode = 2,
                    details = listOf(
                        CourseDetailField(" 课程序号 ", " 12345 "),
                        CourseDetailField("学分", ""),
                        CourseDetailField("课程序号", "67890"),
                        CourseDetailField("课程代码", "MATH2001"),
                    ),
                ),
            ),
        ).toTermSchedule().dailySchedules.single().courses.single()

        assertEquals(
            listOf(CourseDetailField("课程序号", "12345"), CourseDetailField("课程代码", "MATH2001")),
            course.details,
        )
    }

    @Test
    fun `附加信息条数和长度都有上限`() {
        val many = (1..20).map { CourseDetailField("字段$it", "值".repeat(500)) }

        val normalized = normalizeDetails(many)

        assertEquals(12, normalized.size)
        assertTrue(normalized.all { it.value.length == 200 })
    }
}
