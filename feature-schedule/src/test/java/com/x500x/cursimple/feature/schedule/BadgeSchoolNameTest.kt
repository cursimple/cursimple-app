package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.sharedLocationSuffix
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Infer institution names from badges even when location fields contain rooms only. */
class BadgeSchoolNameTest {

    private fun course(id: String, location: String) = CourseItem(
        id = id,
        title = id,
        location = location,
        weeks = emptyList(),
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    private val rules = listOf(
        CourseBadgeRule(id = "ml", titleContains = "机器学习", label = "示例大学"),
        CourseBadgeRule(id = "web", titleContains = "Web", label = "示例大学"),
    )

    @Test
    fun `地点里没有校名时，徽章文字要参与推断`() {
        val courses = listOf(course("机器学习", "东16-C-101"), course("Web", "东13-C-315"))

        // Locations alone do not establish a shared institution.
        assertEquals("", sharedLocationSuffix(courses.map { it.location }))

        val texts = courses.map { it.location } +
            courses.flatMap { matchedBadgeLabels(it, rules) }
        assertEquals("示例大学", sharedLocationSuffix(texts))
    }

    @Test
    fun `推出校名后，纯校名的徽章不再显示`() {
        val target = course("机器学习", "东16-C-101")
        assertEquals(listOf("示例大学"), matchedBadgeLabels(target, rules))
        assertTrue(
            "剥掉校名什么都不剩的徽章应当被滤掉",
            badgesForCourse(target, rules, schoolName = "示例大学").isEmpty(),
        )
    }
}
