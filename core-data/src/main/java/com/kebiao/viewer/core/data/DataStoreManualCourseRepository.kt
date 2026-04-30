package com.kebiao.viewer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kebiao.viewer.core.kernel.model.CourseItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.manualCoursesDataStore: DataStore<Preferences> by preferencesDataStore(name = "manual_courses_store")

class DataStoreManualCourseRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ManualCourseRepository {

    private val store = context.applicationContext.manualCoursesDataStore
    private val serializer = ListSerializer(CourseItem.serializer())

    override val manualCoursesFlow: Flow<List<CourseItem>> = store.data.map { prefs ->
        prefs[KEY_COURSES_JSON]?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    override suspend fun addCourse(course: CourseItem) {
        store.edit { prefs ->
            val current = prefs[KEY_COURSES_JSON]?.let { raw ->
                runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
            } ?: emptyList()
            prefs[KEY_COURSES_JSON] = json.encodeToString(serializer, current + course)
        }
    }

    override suspend fun removeCourse(courseId: String) {
        store.edit { prefs ->
            val current = prefs[KEY_COURSES_JSON]?.let { raw ->
                runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
            } ?: emptyList()
            prefs[KEY_COURSES_JSON] = json.encodeToString(
                serializer,
                current.filterNot { it.id == courseId },
            )
        }
    }

    override suspend fun replaceAll(courses: List<CourseItem>) {
        store.edit { prefs ->
            prefs[KEY_COURSES_JSON] = json.encodeToString(serializer, courses)
        }
    }

    private companion object {
        val KEY_COURSES_JSON = stringPreferencesKey("manual_courses_json")
    }
}
