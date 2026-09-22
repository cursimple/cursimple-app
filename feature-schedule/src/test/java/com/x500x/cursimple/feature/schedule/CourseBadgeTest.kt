package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课表格子上的徽章过滤。
 *
 * 有的教务插件把学校名当成课程徽章推下来，可一份课表就一所学校，
 * 校名当徽章和写在地点里一样没有信息量，格子里不该再挂着它。
 */
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
            rules = listOf(badge("长江大学")),
            schoolName = "长江大学",
        )
        assertEquals(emptyList<String>(), badges)
    }

    @Test
    fun `带校区的纯学校名徽章同样不显示`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("长江大学东校区")),
            schoolName = "长江大学",
        )
        assertEquals(emptyList<String>(), badges)
    }

    @Test
    fun `真正的徽章照常保留`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("重修"), badge("长江大学")),
            schoolName = "长江大学",
        )
        assertEquals(listOf("重修"), badges)
    }

    @Test
    fun `没认出学校名时徽章原样保留`() {
        val badges = badgesForCourse(
            course,
            rules = listOf(badge("长江大学")),
            schoolName = "",
        )
        assertEquals(listOf("长江大学"), badges)
    }
}
