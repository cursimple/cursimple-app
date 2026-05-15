package com.x500x.cursimple.core.plugin.runtime

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

        assertTrue(error?.message.orEmpty().contains("无效星期"))
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

        assertTrue(error?.message.orEmpty().contains("课程数量过多"))
    }
}
