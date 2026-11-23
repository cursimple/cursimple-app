package com.x500x.cursimple.core.data

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.manualCoursesDataStore: DataStore<Preferences> by preferencesDataStore(name = "manual_courses_store")

class DataStoreManualCourseRepository(
    context: Context,
    private val termProfileRepository: TermProfileRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ManualCourseRepository {

    private val store = context.applicationContext.manualCoursesDataStore
    private val serializer = ListSerializer(CourseItem.serializer())
    private val deletedSerializer = ListSerializer(DeletedCourse.serializer())

    override val manualCoursesFlow: Flow<List<CourseItem>> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.activeTermIdFlow) { prefs, termId ->
            decode(prefs, termId)
        }
    } else {
        store.data.map { decode(it, "") }
    }

    override suspend fun addCourse(course: CourseItem) = addCourses(listOf(course))

    override suspend fun addCourses(courses: List<CourseItem>) {
        if (courses.isEmpty()) return
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            prefs[coursesKey(termId)] = json.encodeToString(serializer, current + courses)
        }
    }

    /** Catalog reads never activate a term or change its saved courses. */
    val savedCoursesFlow: Flow<Map<String, List<CourseItem>>> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.termsFlow) { prefs, terms ->
            terms.associate { term -> term.id to decode(prefs, term.id) }
        }
    } else store.data.map { mapOf("" to decode(it, "")) }

    override suspend fun updateCourse(course: CourseItem) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            if (current.none { it.id == course.id }) return@edit
            val next = current.map { if (it.id == course.id) course else it }
            prefs[coursesKey(termId)] = json.encodeToString(serializer, next)
        }
    }

    override suspend fun removeCourse(courseId: String) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            val removed = current.filter { it.id == courseId }
            prefs[coursesKey(termId)] = json.encodeToString(
                serializer,
                current.filterNot { it.id == courseId },
            )
            // Deleted manual courses go to the recycle bin so they can be restored; tombstones of synced
            // courses keep their own "hidden" flag.
            if (removed.isNotEmpty()) prefs[KEY_DELETED_COURSES] = json.encodeToString(
                deletedSerializer,
                decodeDeleted(prefs) + removed.map { DeletedCourse(it, System.currentTimeMillis(), termId) },
            )
        }
    }

    override val deletedCoursesFlow: Flow<List<DeletedCourse>> = store.data.map { prefs ->
        decodeDeleted(prefs).sortedByDescending { it.deletedAt }
    }

    override suspend fun restoreCourse(courseId: String) {
        store.edit { prefs ->
            val deleted = decodeDeleted(prefs)
            val restored = deleted.firstOrNull { it.course.id == courseId } ?: return@edit
            prefs[KEY_DELETED_COURSES] = json.encodeToString(
                deletedSerializer,
                deleted.filterNot { it.course.id == courseId },
            )
            val key = coursesKey(restored.termId)
            val current = decode(prefs, restored.termId)
            if (current.none { it.id == restored.course.id }) {
                prefs[key] = json.encodeToString(serializer, current + restored.course)
            }
        }
    }

    override suspend fun clearDeletedCourses() {
        store.edit { prefs -> prefs.remove(KEY_DELETED_COURSES) }
    }

    private fun decodeDeleted(prefs: androidx.datastore.preferences.core.Preferences): List<DeletedCourse> =
        runCatching {
            prefs[KEY_DELETED_COURSES]?.let { json.decodeFromString(deletedSerializer, it) }.orEmpty()
        }.getOrDefault(emptyList())

    override suspend fun removeFromLibrary(termId: String, selectedIds: Set<String>, syncedCourses: List<CourseItem>) {
        if (selectedIds.isEmpty()) return
        store.edit { prefs ->
            val before = decode(prefs, termId)
            val remaining = applyCourseLibraryDeletion(before, selectedIds, syncedCourses)
            prefs[coursesKey(termId)] = json.encodeToString(serializer, remaining)
            // Manual courses are deleted outright, so keep them in the recycle bin; synced ones keep tombstones.
            val syncedIds = syncedCourses.mapTo(hashSetOf()) { it.id }
            val gone = before.filter { it.id in selectedIds && it.id !in syncedIds }
            if (gone.isNotEmpty()) prefs[KEY_DELETED_COURSES] = json.encodeToString(
                deletedSerializer,
                decodeDeleted(prefs) + gone.map { DeletedCourse(it, System.currentTimeMillis(), termId) },
            )
        }
    }

    override suspend fun replaceAll(courses: List<CourseItem>) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            prefs[coursesKey(termId)] = json.encodeToString(serializer, courses)
        }
    }

    /** Read one specific term's manual courses (merging terms). */
    suspend fun coursesFor(termId: String): List<CourseItem> = decode(store.data.first(), termId)

    /** Write a specific term (merging terms); a blank id must not fall back to the legacy bucket. */
    suspend fun replaceAllFor(termId: String, courses: List<CourseItem>) {
        val key = writableCoursesKey(termId) ?: return
        store.edit { prefs ->
            prefs[stringPreferencesKey(key)] = json.encodeToString(serializer, courses)
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

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.MANUAL_COURSES)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun decode(prefs: Preferences, termId: String): List<CourseItem> {
        val raw = prefs[coursesKey(termId)] ?: prefs[KEY_LEGACY_COURSES_JSON]
        return raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()
    }

    private fun coursesKey(termId: String) = stringPreferencesKey(coursesBucketKey(termId))

    private companion object {
        val KEY_LEGACY_COURSES_JSON = stringPreferencesKey("manual_courses_json")
        val KEY_DELETED_COURSES = stringPreferencesKey("manual_courses_deleted")
    }
}

internal fun applyCourseLibraryDeletion(
    current: List<CourseItem>,
    selectedIds: Set<String>,
    syncedCourses: List<CourseItem>,
): List<CourseItem> {
    val remaining = current.filterNot { it.id in selectedIds && !it.hidden }
    val existingIds = remaining.mapTo(hashSetOf()) { it.id }
    return remaining + syncedCourses
        .filter { it.id in selectedIds && existingIds.add(it.id) }
        .map { it.copy(hidden = true) }

}

/** 空学期 id 读取时回落到旧的无学期桶。 */
internal fun coursesBucketKey(termId: String): String =
    if (termId.isBlank()) "manual_courses_json" else "manual_courses_json__$termId"

/** 写入指定学期时使用；空 id 没有归属的学期，返回 null 表示拒绝写入。 */
internal fun writableCoursesKey(termId: String): String? =
    if (termId.isBlank()) null else "manual_courses_json__$termId"
