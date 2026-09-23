package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.sharedLocationSuffix
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校名藏在徽章里的那种课表。
 *
 * 有的教务插件把学校名塞成课程徽章，地点字段反而是干净的「东13-C-315」。
 * 只拿地点去推断校名就推不出来，那串「长江大学」会一直留在格子里——
 * 而且徽章只排一行，超宽还会被切成「长江大」。
 */
class BadgeSchoolNameTest {

    private fun course(id: String, location: String) = CourseItem(
        id = id,
        title = id,
        location = location,
        weeks = emptyList(),
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    private val rules = listOf(
        CourseBadgeRule(id = "ml", titleContains = "机器学习", label = "长江大学"),
        CourseBadgeRule(id = "web", titleContains = "Web", label = "长江大学"),
    )

    @Test
    fun `地点里没有校名时，徽章文字要参与推断`() {
        val courses = listOf(course("机器学习", "东16-C-101"), course("Web", "东13-C-315"))

        // 只看地点：推不出校名
        assertEquals("", sharedLocationSuffix(courses.map { it.location }))

        // 地点 + 徽章一起看：推得出来
        val texts = courses.map { it.location } +
            courses.flatMap { matchedBadgeLabels(it, rules) }
        assertEquals("长江大学", sharedLocationSuffix(texts))
    }

    @Test
    fun `推出校名后，纯校名的徽章不再显示`() {
        val target = course("机器学习", "东16-C-101")
        assertEquals(listOf("长江大学"), matchedBadgeLabels(target, rules))
        assertTrue(
            "剥掉校名什么都不剩的徽章应当被滤掉",
            badgesForCourse(target, rules, schoolName = "长江大学").isEmpty(),
        )
    }
}
