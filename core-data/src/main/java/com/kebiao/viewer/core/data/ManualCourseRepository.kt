package com.kebiao.viewer.core.data

import com.kebiao.viewer.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow

interface ManualCourseRepository {
    val manualCoursesFlow: Flow<List<CourseItem>>
    suspend fun addCourse(course: CourseItem)
    suspend fun removeCourse(courseId: String)
    suspend fun replaceAll(courses: List<CourseItem>)
}
