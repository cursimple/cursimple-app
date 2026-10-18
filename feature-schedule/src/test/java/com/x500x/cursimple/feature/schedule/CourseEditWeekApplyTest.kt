package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.teacherForWeek
import com.x500x.cursimple.core.kernel.model.titleForWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseEditWeekApplyTest {

    private val baseCourse = CourseItem(
        id = "c1",
        title = "大学物理",
        teacher = "张教授",
        location = "理学楼101",
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        weeks = listOf(1, 2, 3, 4, 5),
        category = CourseCategory.Course,
        weekLocations = mapOf(1 to "实验楼102"),
        weekTeachers = mapOf(1 to "李助教"),
        weekTitles = mapOf(1 to "大学物理实验"),
    )

    @Test
    fun applyDraftToTargetWeek_updatesOnlyTargetWeek_preservesBaseAndOtherWeeks() {
        val draft = baseCourse.copy(
            title = "大学物理习题课",
            teacher = "王老师",
            location = "教室203",
        )

        val applied = applyDraftToTargetWeek(baseCourse, draft, targetWeek = 3)

        // Base values must remain unchanged so other weeks are not contaminated.
        assertEquals("大学物理", applied.title)
        assertEquals("张教授", applied.teacher)
        assertEquals("理学楼101", applied.location)

        // Target week 3 receives the overrides.
        assertEquals("大学物理习题课", applied.titleForWeek(3))
        assertEquals("王老师", applied.teacherForWeek(3))
        assertEquals("教室203", applied.locationForWeek(3))

        // Week 1 keeps its original overrides.
        assertEquals("大学物理实验", applied.titleForWeek(1))
        assertEquals("李助教", applied.teacherForWeek(1))
        assertEquals("实验楼102", applied.locationForWeek(1))

        // Unmodified weeks fall back to the unchanged base values.
        assertEquals("大学物理", applied.titleForWeek(2))
        assertEquals("张教授", applied.teacherForWeek(2))
        assertEquals("理学楼101", applied.locationForWeek(2))

        assertEquals("大学物理", applied.titleForWeek(4))
        assertEquals("张教授", applied.teacherForWeek(4))
        assertEquals("理学楼101", applied.locationForWeek(4))
    }

    @Test
    fun applyDraftToTargetWeek_revertingToBaseClearsOverride() {
        // Week 1 originally has overrides. Editing week 1 to match base removes the override.
        val draft = baseCourse.copy(
            title = "大学物理", // Matches base
            teacher = "张教授", // Matches base
            location = "理学楼101", // Matches base
        )

        val applied = applyDraftToTargetWeek(baseCourse, draft, targetWeek = 1)

        assertTrue(applied.weekTitles.isEmpty())
        assertTrue(applied.weekTeachers.isEmpty())
        assertTrue(applied.weekLocations.isEmpty())

        assertEquals("大学物理", applied.titleForWeek(1))
        assertEquals("张教授", applied.teacherForWeek(1))
        assertEquals("理学楼101", applied.locationForWeek(1))
    }

    @Test
    fun resolveSaveDraft_whenSourceWeekNull_savesDraftDirectly() {
        val draft = baseCourse.copy(
            title = "高等物理",
            teacher = "赵老师",
            location = "教学楼301",
        )

        val resolved = resolveSaveDraft(
            baseCourse = baseCourse,
            draftCourse = draft,
            sourceWeekNumber = null,
            initialWeekTitle = baseCourse.title,
            initialWeekTeacher = baseCourse.teacher,
            initialWeekLocation = baseCourse.location,
        )

        assertEquals("高等物理", resolved.title)
        assertEquals("赵老师", resolved.teacher)
        assertEquals("教学楼301", resolved.location)
    }

    @Test
    fun resolveSaveDraft_whenSavingOnWeekWithExistingOverride_preservesUneditedBase() {
        // Suppose user is viewing week 1 (which had location "实验楼102").
        // User opens edit: location field initialized with "实验楼102".
        // User only edits teacher from "李助教" to "周助教", leaving location as "实验楼102".
        val draft = baseCourse.copy(
            title = "大学物理",
            teacher = "周助教",
            location = "实验楼102", // Unedited by user, matches week 1's initial value
        )

        val resolved = resolveSaveDraft(
            baseCourse = baseCourse,
            draftCourse = draft,
            sourceWeekNumber = 1,
            initialWeekTitle = "大学物理实验",
            initialWeekTeacher = "李助教",
            initialWeekLocation = "实验楼102",
        )

        // Teacher was edited -> saves as base teacher and clears stale override on week 1
        assertEquals("周助教", resolved.teacher)
        assertEquals("周助教", resolved.teacherForWeek(1))
        // Location was NOT edited -> retains baseCourse.location ("理学楼101"), NOT promoting week 1's room to all weeks
        assertEquals("理学楼101", resolved.location)
        // Week 1's override in weekLocations is preserved
        assertEquals("实验楼102", resolved.locationForWeek(1))
    }

    @Test
    fun applyDraftToTargetWeek_ensuresTargetWeekInCourseWeeks() {
        val courseOutsideTarget = baseCourse.copy(weeks = listOf(1, 2, 3))
        val draft = courseOutsideTarget.copy(location = "新实验室")

        val applied = applyDraftToTargetWeek(courseOutsideTarget, draft, targetWeek = 5)

        assertTrue(5 in applied.weeks)
        assertEquals(listOf(1, 2, 3, 5), applied.weeks)
        assertEquals("新实验室", applied.locationForWeek(5))
    }
}
