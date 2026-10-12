package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses

/** Merge each term independently: course IDs may be reused by different timetables. */
fun courseReuseCatalog(
    schedules: Map<String, TermSchedule?>,
    manualCourses: Map<String, List<CourseItem>>,
): List<CourseItem> = (schedules.keys + manualCourses.keys).flatMap { term ->
    schedules[term].allCoursesWith(manualCourses[term].orEmpty()).visibleScheduleCourses()
}
