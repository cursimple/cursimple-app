package com.x500x.cursimple.core.data.note

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow

interface CourseNoteRepository {
    val courseNotesFlow: Flow<List<CourseNote>>

    suspend fun setNote(courses: List<CourseItem>, course: CourseItem, text: String)

    suspend fun reconcile(courses: List<CourseItem>)
}
