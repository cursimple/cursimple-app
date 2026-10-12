package com.x500x.cursimple.app.vendor

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.work.*
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.app.notice.NoticeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.*

internal object BoundedVendorCall {
    private val executor = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(),
        ThreadFactory { task -> Thread(task, "vendor-course-ipc").apply { isDaemon = true } })
    fun call(context: Context, authority: String, allowed: Set<String>, method: String, arg: String?, extras: Bundle): Bundle? {
        check(VendorCallerTrust.trustedAuthority(context, authority, allowed)) { "System provider unavailable" }
        val future = executor.submit<Bundle?> {
            context.contentResolver.acquireUnstableContentProviderClient(Uri.parse("content://$authority"))?.use { it.call(method, arg, extras) }
        }
        return try { future.get(3, TimeUnit.SECONDS) } catch (failure: ExecutionException) { throw failure.cause ?: failure } finally { future.cancel(true) }
    }
}

object VendorCourseSync {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    fun enqueue(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val config = VendorCourseStore.config(app)
        if (!force && !config.enabled && !VendorCourseStore.hasSubmitted(app) && VendorCourseStore.vivoSubmitted(app).isEmpty()) return
        WorkManager.getInstance(app).enqueueUniqueWork("vendor-course-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<VendorCourseSyncWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    fun defer(context: Context, delayMillis: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork("vendor-course-deferred", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<VendorCourseSyncWorker>().setInitialDelay(delayMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS).build())
    }
    suspend fun refresh(context: Context): Boolean = mutex.withLock { refreshLocked(context) }
    private suspend fun refreshLocked(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val config = VendorCourseStore.config(app)
        VendorCourseStore.status(app, "android", if (Build.VERSION.SDK_INT >= 36) "platform" else "standard")
        if (!config.enabled) {
            VendorCourseStore.writeSnapshot(app, VendorCourseSnapshot())
            notifyOppo(app)
            VendorCourseStore.status(app, "oppo", "disabled")
            val vivoCleared = attempt(app, "vivo", clearing = true) { VivoCourseTransport.clearIfNeeded(app) }
            val honorCleared = attempt(app, "honor", clearing = true) { HonorCourseTransport.clearIfNeeded(app) }
            return@withContext vivoCleared && honorCleared
        }
        (app as? com.x500x.cursimple.app.ClassScheduleApplication)?.appContainer?.bootstrapJob?.join()
        if (VendorCourseStore.config(app) != config) return@withContext false
        val terms = DataStoreTermProfileRepository(app)
        val settings = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
        val schedule = DataStoreScheduleRepository(app, terms).scheduleFlow.first()
        val manual = DataStoreManualCourseRepository(app, terms).manualCoursesFlow.first()
        val timing = DataStoreWidgetPreferencesRepository(app).timingProfileFlow.first()
        val now = System.currentTimeMillis()
        val snapshot = VendorCourseSnapshot(generatedAt = now, advanceMinutes = settings.classNotice.advanceMinutes,
            accentColor = "#%06X".format(NoticeTheme.resolve(app, settings.themeAccent, settings.themeMode, settings.themeCustomColorArgb).primary and 0xffffff),
            courses = vendorCourses(Instant.ofEpochMilli(now).atZone(BeijingTime.zone).toLocalDate(), schedule.allCoursesWith(manual),
                timing, settings.termStartDate, settings.temporaryScheduleOverrides, settings.holidayCalendar, BeijingTime.zone))
        if (VendorCourseStore.config(app) != config) return@withContext false
        val changed = VendorCourseStore.snapshot(app).copy(generatedAt = 0) != snapshot.copy(generatedAt = 0)
        VendorCourseStore.writeSnapshot(app, snapshot)
        val vivo = attempt(app, "vivo") { VivoCourseTransport.synchronize(app, config, snapshot) }
        val oppo = VendorCallerTrust.oppo.any { VendorCallerTrust.isSystemPackage(app, it) }
        if (changed || VendorCourseStore.statuses(app).none { it.first == "oppo" && it.second == "queried" }) {
            VendorCourseStore.status(app, "oppo", if (oppo) "ready" else "unavailable")
        }
        notifyOppo(app)
        val honor = attempt(app, "honor") { HonorCourseTransport.synchronize(app, config, snapshot) }
        vivo && honor
    }

    private suspend fun attempt(context: Context, vendor: String, clearing: Boolean = false, block: suspend () -> Boolean): Boolean = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        VendorCourseStore.status(context, vendor, if (clearing) "cleanup_pending" else "failed")
        com.x500x.cursimple.core.reminder.logging.ReminderLogger.warn("vendor.$vendor.failure", emptyMap(), failure)
        false
    }

    private fun notifyOppo(context: Context) {
        listOf("refresh", "course_list", "next_course_list").forEach {
            context.contentResolver.notifyChange(Uri.parse("content://${context.packageName}.provider.oppodonateprovider/$it"), null)
        }
    }

}

class VendorCourseSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (VendorCourseSync.refresh(applicationContext)) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        com.x500x.cursimple.core.reminder.logging.ReminderLogger.warn("vendor.course.refresh.failure", emptyMap(), failure)
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}
