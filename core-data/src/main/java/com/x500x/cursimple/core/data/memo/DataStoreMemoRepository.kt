package com.x500x.cursimple.core.data.memo

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
import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.memoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppBackupStores.MEMOS,
)

class DataStoreMemoRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : MemoRepository {

    private val store = context.applicationContext.memoDataStore
    private val serializer = ListSerializer(MemoNote.serializer())

    override val notesFlow: Flow<List<MemoNote>> = store.data.map(::decode)

    override suspend fun upsert(note: MemoNote) {
        store.edit { prefs ->
            val current = decode(prefs)
            val next = if (current.any { it.id == note.id }) {
                current.map { if (it.id == note.id) note else it }
            } else {
                current + note
            }
            prefs[KEY_NOTES] = json.encodeToString(serializer, next)
        }
    }

    override suspend fun remove(noteId: String) {
        store.edit { prefs ->
            prefs[KEY_NOTES] = json.encodeToString(serializer, decode(prefs).filterNot { it.id == noteId })
        }
    }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.MEMOS)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun decode(prefs: Preferences): List<MemoNote> =
        prefs[KEY_NOTES]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private companion object {
        val KEY_NOTES = stringPreferencesKey("memo_notes_json")
    }
}
