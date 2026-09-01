package com.x500x.cursimple.core.data.note

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow

interface CourseNoteRepository {
    val courseNotesFlow: Flow<List<CourseNote>>

    /** 写入或删除一门课的备注，[text] 去掉首尾空白后为空表示删除。 */
    suspend fun setNote(courses: List<CourseItem>, course: CourseItem, text: String)

    /** 课表变化后刷新备注锚点，让下次查找能走 id 快路径。 */
    suspend fun reconcile(courses: List<CourseItem>)
}
