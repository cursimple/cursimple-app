package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import org.junit.Assert.assertEquals
import org.junit.Test

/** Filter institution-only badges from compact timetable cells. */
class CourseBadgeTest {

    private val course = CourseItem(
        id = "c1",
        title = "高等数学",
        teacher = "张三",
        location = "东13-C-215c",
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    private fun badge(label: String) = CourseBadgeRule(id = label, label = label)

    @Test
    fun `纯学校名的徽章不显示`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("示例大学")),
            schoolName = "示例大学",
        )
        assertEquals(emptyList<String>(), badges)
    }

    @Test
    fun `带校区的纯学校名徽章同样不显示`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("示例大学东校区")),
            schoolName = "示例大学",
        )
        assertEquals(emptyList<String>(), badges)
    }

    @Test
    fun `真正的徽章照常保留`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("重修"), badge("示例大学")),
            schoolName = "示例大学",
        )
        assertEquals(listOf("重修"), badges)
    }

    @Test
    fun `没认出学校名时徽章原样保留`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("示例大学")),
            schoolName = "",
        )
        assertEquals(listOf("示例大学"), badges)
    }
}
