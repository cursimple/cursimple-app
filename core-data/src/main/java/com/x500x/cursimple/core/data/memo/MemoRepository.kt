package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface MemoRepository {
    val notesFlow: Flow<List<MemoNote>>

    /** Replace existing IDs, otherwise append. */
    suspend fun upsert(note: MemoNote)
    suspend fun remove(noteId: String)
    /** Append imported identities; never replace an existing note. */
    suspend fun importNotes(notes: List<MemoNote>): Int {
        val ids = notesFlow.first().mapTo(mutableSetOf()) { it.id }
        return notes.count { note -> if (ids.add(note.id)) { upsert(note); true } else false }
    }
}
