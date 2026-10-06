package com.x500x.cursimple.app.notification

import android.content.Context
import androidx.work.*
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import java.util.concurrent.TimeUnit

class NotificationDeliveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? ClassScheduleApplication)?.appContainer ?: return Result.success()
        return try {
            if (container.notificationDeliveryCoordinator.drain()) Result.retry() else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            ReminderLogger.warn("notification.delivery.worker_failed", emptyMap(), error)
            Result.retry()
        }
    }
    companion object {
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<NotificationDeliveryWorker>().setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
            // New events bypass unrelated target backoff; component locks and persistent leases deduplicate delivery.
            WorkManager.getInstance(context).enqueue(work)
        }
        fun schedulePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<NotificationDeliveryWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("notification_delivery_periodic", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
