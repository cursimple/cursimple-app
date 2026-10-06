package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.flow.Flow

interface MemoRepository {
    val notesFlow: Flow<List<MemoNote>>

    /** Replace existing IDs, otherwise append. */
    suspend fun upsert(note: MemoNote)
    suspend fun remove(noteId: String)
}
