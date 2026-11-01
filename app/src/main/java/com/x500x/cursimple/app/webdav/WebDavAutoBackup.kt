package com.x500x.cursimple.app.webdav

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.data.AppBackupPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Weekly backup to the user's own WebDAV. Device-local on purpose: it is not part of the backup it makes. */
data class WebDavAutoBackupSettings(
    val enabled: Boolean = false,
    /** How many automatic backups stay on the server; older ones are removed after each run. */
    val keep: Int = DEFAULT_KEEP,
    val lastSuccessAt: Long = 0L,
    val lastError: String = "",
) {
    companion object {
        const val DEFAULT_KEEP = 5
        const val MIN_KEEP = 1
        const val MAX_KEEP = 30
    }
}

private val Context.webDavAutoBackupDataStore: DataStore<Preferences> by preferencesDataStore(name = "webdav_auto_backup")

class WebDavAutoBackupStore(context: Context) {
    private val store = context.applicationContext.webDavAutoBackupDataStore

    val settingsFlow: Flow<WebDavAutoBackupSettings> = store.data.map { prefs ->
        WebDavAutoBackupSettings(
            enabled = prefs[ENABLED] ?: false,
            keep = (prefs[KEEP] ?: WebDavAutoBackupSettings.DEFAULT_KEEP)
                .coerceIn(WebDavAutoBackupSettings.MIN_KEEP, WebDavAutoBackupSettings.MAX_KEEP),
            lastSuccessAt = prefs[LAST_SUCCESS] ?: 0L,
            lastError = prefs[LAST_ERROR].orEmpty(),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    suspend fun setKeep(keep: Int) {
        store.edit { it[KEEP] = keep.coerceIn(WebDavAutoBackupSettings.MIN_KEEP, WebDavAutoBackupSettings.MAX_KEEP) }
    }

    suspend fun recordSuccess(at: Long) {
        store.edit {
            it[LAST_SUCCESS] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordFailure(message: String) {
        store.edit { it[LAST_ERROR] = message.take(300) }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val KEEP = intPreferencesKey("keep")
        val LAST_SUCCESS = longPreferencesKey("last_success_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}

/** Automatic backups carry their own prefix, so pruning never touches ones the user made by hand. */
const val AUTO_BACKUP_PREFIX = "cursimple-backup-auto-"

fun autoBackupFileName(now: LocalDateTime): String =
    AUTO_BACKUP_PREFIX + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(now) + AppBackupPayload.FILE_EXTENSION

/** Automatic backups beyond the newest [keep], oldest last; manual ones are never listed. */
fun automaticBackupsToDelete(files: List<WebDavBackupFile>, keep: Int): List<WebDavBackupFile> =
    files.filter { it.name.startsWith(AUTO_BACKUP_PREFIX) }
        .sortedByDescending { it.name }
        .drop(keep.coerceAtLeast(1))

private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Create the backup, upload it and trim old automatic copies. Shared by the weekly job and "back up now". */
suspend fun runWebDavBackupNow(app: ClassScheduleApplication, client: WebDavClient = WebDavClient()): WebDavBackupFile {
    val prefs = app.appContainer.userPreferencesRepository.preferencesFlow.first()
    val config = WebDavConfig(prefs.webDavUrl, prefs.webDavUsername, prefs.webDavPassword)
    if (!config.isComplete) throw IOException("WebDAV is not configured")
    val keep = WebDavAutoBackupStore(app).settingsFlow.first().keep
    val payload = app.appContainer.exportAppBackup()
    val bytes = backupJson.encodeToString(AppBackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
    val uploaded = client.uploadBackup(config, autoBackupFileName(LocalDateTime.now()), bytes)
    // Trimming is best effort: the new backup is already safe on the server.
    runCatching {
        automaticBackupsToDelete(client.listBackups(config), keep).forEach { client.delete(config, it.href) }
    }
    return uploaded
}

class WebDavAutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ClassScheduleApplication ?: return Result.success()
        val store = WebDavAutoBackupStore(app)
        if (!store.settingsFlow.first().enabled) return Result.success()
        return try {
            runWebDavBackupNow(app)
            store.recordSuccess(System.currentTimeMillis())
            Result.success()
        } catch (error: Exception) {
            store.recordFailure(error.message ?: error.javaClass.simpleName)
            // A flaky connection is retried with backoff; anything else waits for the next weekly run.
            if (error is IOException && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "webdav_auto_backup"
        private const val NOW_WORK_NAME = "webdav_auto_backup_now"
        private const val MAX_ATTEMPTS = 3
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        /** Keep the weekly job in step with the switch; also run once right away when it was just turned on. */
        fun apply(context: Context, enabled: Boolean, runNow: Boolean = false) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                work.cancelUniqueWork(NOW_WORK_NAME)
                return
            }
            val weekly = PeriodicWorkRequestBuilder<WebDavAutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .setInitialDelay(7, TimeUnit.DAYS)
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, weekly)
            if (runNow) {
                val once = OneTimeWorkRequestBuilder<WebDavAutoBackupWorker>().setConstraints(constraints).build()
                work.enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, once)
            }
        }

        /** At startup: re-assert the weekly job from the saved switch (WorkManager keeps it, this repairs a wiped queue). */
        suspend fun syncFromSettings(context: Context) {
            apply(context, WebDavAutoBackupStore(context).settingsFlow.first().enabled)
        }
    }
}
