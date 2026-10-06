package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow

interface ManualCourseRepository {
    val manualCoursesFlow: Flow<List<CourseItem>>
    suspend fun addCourse(course: CourseItem)

    /** Replace by ID only; editing a missing record must not insert it. */
    suspend fun updateCourse(course: CourseItem)
    suspend fun removeCourse(courseId: String)
    suspend fun replaceAll(courses: List<CourseItem>)
}
