package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.flow.Flow

/** 备忘录。不分学期：笔记按课名归属，换课表、重新导课都不影响。 */
interface MemoRepository {
    val notesFlow: Flow<List<MemoNote>>

    /** id 已存在就原地替换，否则追加。 */
    suspend fun upsert(note: MemoNote)
    suspend fun remove(noteId: String)
}
