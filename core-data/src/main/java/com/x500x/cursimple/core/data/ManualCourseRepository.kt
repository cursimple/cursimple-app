package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow

interface ManualCourseRepository {
    val manualCoursesFlow: Flow<List<CourseItem>>
    suspend fun addCourse(course: CourseItem)

    /** 按 id 原地替换，id 不存在时不做任何事，避免把编辑当成新增。 */
    suspend fun updateCourse(course: CourseItem)
    suspend fun removeCourse(courseId: String)
    suspend fun replaceAll(courses: List<CourseItem>)
}
