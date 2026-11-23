package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A removed course plus when it was removed, so the recycle bin can show and restore it. */
@Serializable
data class DeletedCourse(
    val course: CourseItem,
    val deletedAt: Long,
    /** Term the course belonged to, so restoring puts it back where it was. */
    val termId: String,
)

interface ManualCourseRepository {
    val manualCoursesFlow: Flow<List<CourseItem>>
    suspend fun addCourse(course: CourseItem)
    suspend fun addCourses(courses: List<CourseItem>)

    /** Replace by ID only; editing a missing record must not insert it. */
    suspend fun updateCourse(course: CourseItem)
    suspend fun removeCourse(courseId: String)
    suspend fun removeFromLibrary(termId: String, selectedIds: Set<String>, syncedCourses: List<CourseItem>)

    /** Deleted courses kept for recovery, newest first. */
    val deletedCoursesFlow: Flow<List<DeletedCourse>>

    suspend fun restoreCourse(courseId: String)
    suspend fun clearDeletedCourses()
    suspend fun replaceAll(courses: List<CourseItem>)
}
