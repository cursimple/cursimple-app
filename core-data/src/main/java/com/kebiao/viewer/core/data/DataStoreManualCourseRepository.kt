package com.kebiao.viewer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.data.term.TermProfileRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.manualCoursesDataStore: DataStore<Preferences> by preferencesDataStore(name = "manual_courses_store")

class DataStoreManualCourseRepository(
    context: Context,
    private val termProfileRepository: TermProfileRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ManualCourseRepository {

    private val store = context.applicationContext.manualCoursesDataStore
    private val serializer = ListSerializer(CourseItem.serializer())

    override val manualCoursesFlow: Flow<List<CourseItem>> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.activeTermIdFlow) { prefs, termId ->
            decode(prefs, termId)
        }
    } else {
        store.data.map { decode(it, "") }
    }

    override suspend fun addCourse(course: CourseItem) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            prefs[coursesKey(termId)] = json.encodeToString(serializer, current + course)
        }
    }

    override suspend fun removeCourse(courseId: String) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            prefs[coursesKey(termId)] = json.encodeToString(
                serializer,
                current.filterNot { it.id == courseId },
            )
        }
    }

    override suspend fun replaceAll(courses: List<CourseItem>) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            prefs[coursesKey(termId)] = json.encodeToString(serializer, courses)
        }
    }

    /** Migrate legacy single-bucket manual courses into the given term. */
    suspend fun migrateLegacyManualIfNeeded(targetTermId: String) {
        if (targetTermId.isBlank()) return
        store.edit { prefs ->
            val legacy = prefs[KEY_LEGACY_COURSES_JSON] ?: return@edit
            val perTerm = prefs[coursesKey(targetTermId)]
            if (perTerm.isNullOrBlank()) {
                prefs[coursesKey(targetTermId)] = legacy
            }
            prefs.remove(KEY_LEGACY_COURSES_JSON)
        }
    }

    private fun decode(prefs: Preferences, termId: String): List<CourseItem> {
        val raw = prefs[coursesKey(termId)] ?: prefs[KEY_LEGACY_COURSES_JSON]
        return raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()
    }

    private fun coursesKey(termId: String) =
        stringPreferencesKey(if (termId.isBlank()) "manual_courses_json" else "manual_courses_json__$termId")

    private companion object {
        val KEY_LEGACY_COURSES_JSON = stringPreferencesKey("manual_courses_json")
    }
}
