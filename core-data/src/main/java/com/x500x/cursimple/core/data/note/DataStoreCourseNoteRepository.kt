package com.x500x.cursimple.core.data.note

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import com.x500x.cursimple.core.data.exportSnapshot
import com.x500x.cursimple.core.data.restoreSnapshot
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.courseNotesDataStore: DataStore<Preferences> by preferencesDataStore(name = "course_notes_store")

class DataStoreCourseNoteRepository(
    context: Context,
    private val termProfileRepository: TermProfileRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val now: () -> Long = System::currentTimeMillis,
) : CourseNoteRepository {

    private val store = context.applicationContext.courseNotesDataStore
    private val serializer = ListSerializer(CourseNote.serializer())

    override val courseNotesFlow: Flow<List<CourseNote>> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.activeTermIdFlow) { prefs, termId ->
            decode(prefs, termId)
        }
    } else {
        store.data.map { decode(it, "") }
    }

    override suspend fun setNote(courses: List<CourseItem>, course: CourseItem, text: String) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        val timestamp = now()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            val updated = upsertCourseNote(
                courses = courses,
                notes = current,
                course = course,
                text = text,
                updatedAt = timestamp,
            )
            if (updated == current) return@edit
            prefs[notesKey(termId)] = json.encodeToString(serializer, updated)
        }
    }

    override suspend fun reconcile(courses: List<CourseItem>) {
        if (courses.isEmpty()) return
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            if (current.isEmpty()) return@edit
            val updated = reconcileCourseNotes(courses, current)
            if (updated == current) return@edit
            prefs[notesKey(termId)] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.COURSE_NOTES)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun decode(prefs: Preferences, termId: String): List<CourseNote> {
        val raw = prefs[notesKey(termId)] ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: emptyList()
    }

    private fun notesKey(termId: String) =
        stringPreferencesKey(if (termId.isBlank()) "course_notes_json" else "course_notes_json__$termId")
}
