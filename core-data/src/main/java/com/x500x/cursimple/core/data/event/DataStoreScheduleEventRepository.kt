package com.x500x.cursimple.core.data.event

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
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.scheduleEventsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppBackupStores.SCHEDULE_EVENTS,
)

/**
 * 事务跟随学期分区，键与课表、占位课一致；空学期 id 只在迁移前回落到旧键。
 */
internal fun scheduleEventsPreferenceKey(termId: String): String =
    if (termId.isBlank()) LEGACY_EVENTS_KEY else "${LEGACY_EVENTS_KEY}__$termId"

/** 先读当前学期的存储，再回落旧的无学期键。 */
internal fun <T> resolveStoredEvents(read: (String) -> T?, termId: String): T? =
    read(scheduleEventsPreferenceKey(termId)) ?: read(LEGACY_EVENTS_KEY)

private const val LEGACY_EVENTS_KEY = "schedule_events_json"

class DataStoreScheduleEventRepository(
    context: Context,
    private val termProfileRepository: TermProfileRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ScheduleEventRepository {

    private val store = context.applicationContext.scheduleEventsDataStore
    private val serializer = ListSerializer(ScheduleEvent.serializer())

    override val eventsFlow: Flow<List<ScheduleEvent>> = if (termProfileRepository != null) {
        combine(store.data, termProfileRepository.activeTermIdFlow) { prefs, termId ->
            decode(prefs, termId)
        }
    } else {
        store.data.map { decode(it, "") }
    }

    override suspend fun upsert(event: ScheduleEvent) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            val current = decode(prefs, termId)
            val next = if (current.any { it.id == event.id }) {
                current.map { if (it.id == event.id) event else it }
            } else {
                current + event
            }
            prefs[eventsKey(termId)] = json.encodeToString(serializer, next)
        }
    }

    override suspend fun remove(eventId: String) {
        val termId = termProfileRepository?.activeTermId().orEmpty()
        store.edit { prefs ->
            prefs[eventsKey(termId)] = json.encodeToString(serializer, decode(prefs, termId).filterNot { it.id == eventId })
        }
    }

    /** Migrate legacy single-bucket events into the given term. */
    suspend fun migrateLegacyEventsIfNeeded(targetTermId: String) {
        if (targetTermId.isBlank()) return
        store.edit { prefs ->
            val legacy = prefs[LEGACY_KEY] ?: return@edit
            val perTerm = prefs[eventsKey(targetTermId)]
            if (perTerm.isNullOrBlank()) {
                prefs[eventsKey(targetTermId)] = legacy
            }
            prefs.remove(LEGACY_KEY)
        }
    }

    /** Read one specific term's events (merging terms). */
    suspend fun eventsFor(termId: String): List<ScheduleEvent> = decode(store.data.first(), termId)

    /** Write one specific term's events (merging terms); a blank id has no bucket. */
    suspend fun replaceAllFor(termId: String, events: List<ScheduleEvent>) {
        if (termId.isBlank()) return
        store.edit { prefs ->
            prefs[eventsKey(termId)] = json.encodeToString(serializer, events)
        }
    }

    /**
     * Every stored event regardless of term. Extension lifecycle checks ask whether a component
     * still owns any event anywhere, so they must not be limited to the active term.
     */
    override suspend fun allEvents(): List<ScheduleEvent> =
        store.data.first().asMap()
            .filterKeys { it.name.isEventKey() }
            .values.filterIsInstance<String>()
            .flatMap(::decodeList)

    private fun String.isEventKey(): Boolean = this == LEGACY_EVENTS_KEY || startsWith("${LEGACY_EVENTS_KEY}__")

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.SCHEDULE_EVENTS)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun decode(prefs: Preferences, termId: String): List<ScheduleEvent> =
        resolveStoredEvents({ prefs[stringPreferencesKey(it)] }, termId)?.let(::decodeList).orEmpty()

    private fun decodeList(raw: String): List<ScheduleEvent> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    private fun eventsKey(termId: String) = stringPreferencesKey(scheduleEventsPreferenceKey(termId))

    private companion object {
        val LEGACY_KEY = stringPreferencesKey(LEGACY_EVENTS_KEY)
    }
}
