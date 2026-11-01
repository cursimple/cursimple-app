package com.x500x.cursimple.core.data.attendance

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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface AttendanceRepository {
    val attendanceFlow: Flow<AttendanceData>

    /** Mark one session; a null [status] clears it. */
    suspend fun mark(courseKey: String, date: String, status: AttendanceStatus?)

    suspend fun setAllowance(courseKey: String, allowance: Int?)
}

private val Context.attendanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "attendance_store")

class DataStoreAttendanceRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val now: () -> Long = System::currentTimeMillis,
) : AttendanceRepository {
    private val store = context.applicationContext.attendanceDataStore
    private val key = stringPreferencesKey("attendance_json")

    override val attendanceFlow: Flow<AttendanceData> = store.data.map { decode(it) }

    override suspend fun mark(courseKey: String, date: String, status: AttendanceStatus?) = update { it.withMark(courseKey, date, status, now()) }

    override suspend fun setAllowance(courseKey: String, allowance: Int?) = update { it.withAllowance(courseKey, allowance) }

    suspend fun exportBackupSnapshot(): PreferencesStoreSnapshot = store.exportSnapshot(AppBackupStores.ATTENDANCE)

    suspend fun restoreBackupSnapshot(snapshot: PreferencesStoreSnapshot) {
        store.restoreSnapshot(snapshot)
    }

    private suspend fun update(change: (AttendanceData) -> AttendanceData) {
        store.edit { prefs ->
            val current = decode(prefs)
            val updated = change(current)
            if (updated != current) prefs[key] = json.encodeToString(AttendanceData.serializer(), updated)
        }
    }

    private fun decode(prefs: Preferences): AttendanceData {
        val raw = prefs[key] ?: return AttendanceData()
        return runCatching { json.decodeFromString(AttendanceData.serializer(), raw) }.getOrNull() ?: AttendanceData()
    }
}
