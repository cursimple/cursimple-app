package com.x500x.cursimple.core.data.term

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.termProfileStore: DataStore<Preferences> by preferencesDataStore(name = "term_profiles")

class DataStoreTermProfileRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : TermProfileRepository {

    private val store = context.applicationContext.termProfileStore
    private val listSerializer = ListSerializer(TermProfile.serializer())

    override val termsFlow: Flow<List<TermProfile>> = store.data.map { prefs ->
        prefs[KEY_TERMS_JSON]?.let { raw ->
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull()
        }.orEmpty()
    }

    override val activeTermIdFlow: Flow<String> = store.data.map { prefs ->
        prefs[KEY_ACTIVE_TERM_ID].orEmpty()
    }

    override suspend fun activeTermId(): String = activeTermIdFlow.first()

    override suspend fun createTerm(name: String, termStartDateIso: String?): TermProfile {
        var created: TermProfile? = null
        store.edit { prefs ->
            val list = readTerms(prefs).toMutableList()
            val term = TermProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "新学期" },
                termStartDate = termStartDateIso,
            )
            list += term
            prefs[KEY_TERMS_JSON] = json.encodeToString(listSerializer, list)
            if (prefs[KEY_ACTIVE_TERM_ID].isNullOrBlank()) {
                prefs[KEY_ACTIVE_TERM_ID] = term.id
            }
            created = term
        }
        return created!!
    }

    override suspend fun renameTerm(id: String, name: String) {
        store.edit { prefs ->
            val list = readTerms(prefs).map {
                if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it
            }
            prefs[KEY_TERMS_JSON] = json.encodeToString(listSerializer, list)
        }
    }

    override suspend fun setTermStartDate(id: String, dateIso: String?) {
        store.edit { prefs ->
            val list = readTerms(prefs).map {
                if (it.id == id) it.copy(termStartDate = dateIso) else it
            }
            prefs[KEY_TERMS_JSON] = json.encodeToString(listSerializer, list)
        }
    }

    override suspend fun deleteTerm(id: String) {
        store.edit { prefs ->
            val list = readTerms(prefs).filterNot { it.id == id }
            prefs[KEY_TERMS_JSON] = json.encodeToString(listSerializer, list)
            if (prefs[KEY_ACTIVE_TERM_ID] == id) {
                prefs[KEY_ACTIVE_TERM_ID] = list.firstOrNull()?.id.orEmpty()
            }
        }
    }

    override suspend fun setActiveTerm(id: String) {
        store.edit { prefs ->
            val exists = readTerms(prefs).any { it.id == id }
            if (exists) {
                prefs[KEY_ACTIVE_TERM_ID] = id
            }
        }
    }

    override suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?): String {
        var resolved: String = ""
        store.edit { prefs ->
            val list = readTerms(prefs).toMutableList()
            if (list.isEmpty()) {
                val term = TermProfile(
                    id = UUID.randomUUID().toString(),
                    name = defaultName.ifBlank { "默认学期" },
                    termStartDate = legacyTermStartDateIso,
                )
                list += term
                prefs[KEY_TERMS_JSON] = json.encodeToString(listSerializer, list)
                prefs[KEY_ACTIVE_TERM_ID] = term.id
                resolved = term.id
            } else {
                val active = prefs[KEY_ACTIVE_TERM_ID]
                resolved = if (!active.isNullOrBlank() && list.any { it.id == active }) {
                    active
                } else {
                    val first = list.first().id
                    prefs[KEY_ACTIVE_TERM_ID] = first
                    first
                }
            }
        }
        return resolved
    }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot =
        store.exportSnapshot(AppBackupStores.TERM_PROFILES)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private fun readTerms(prefs: Preferences): List<TermProfile> =
        prefs[KEY_TERMS_JSON]?.let { raw ->
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull()
        }.orEmpty()

    private companion object {
        val KEY_TERMS_JSON = stringPreferencesKey("term_profiles_json")
        val KEY_ACTIVE_TERM_ID = stringPreferencesKey("active_term_id")
    }
}
