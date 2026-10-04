package com.x500x.cursimple.app.extension

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.feature.plugin.extension.ExtensionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 扩展组件的后台同步。每 30 分钟醒一次，各组件按自己的间隔决定这次跑不跑。
 *
 * 只在联网时跑：没网跑了也是失败，还白白唤醒一次。
 */
class ExtensionSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ClassScheduleApplication)?.appContainer ?: return Result.success()
        runCatching { container.extensionCoordinator.syncDue() }
            .onFailure { ReminderLogger.warn("extension.worker.failed", emptyMap(), it) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "extension_feed_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExtensionSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * 截止前提醒的闹钟：所有组件、所有条目里最早的那个提醒时刻挂一个，响了发完再挂下一个。
 *
 * 用不精确闹钟就够了：「截止前 24 小时」早几分钟晚几分钟都无所谓，
 * 也不必为这个去要精确闹钟权限。
 */
object ExtensionDueScheduler {

    fun reschedule(context: Context, all: Collection<ExtensionData>, now: Long) {
        val next = all.asSequence()
            .filter { it.loggedIn && it.host.dueReminderHours > 0 }
            .flatMap { data ->
                data.items.asSequence()
                    .filter { !it.done && "${it.id}@${it.dueAt}" !in data.remindedKeys }
                    .mapNotNull { item -> item.dueAt?.takeIf { it > now } }
                    .map { due -> (due - data.host.dueReminderHours * 3_600_000L).coerceAtLeast(now + 60_000L) }
            }
            .minOrNull()
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, next, pending)
            }
        }.onFailure { ReminderLogger.warn("extension.due.schedule_failed", emptyMap(), it) }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ExtensionDueReceiver::class.java).setAction(ACTION_DUE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal const val ACTION_DUE = "com.x500x.cursimple.action.EXTENSION_DUE"
    private const val REQUEST_CODE = 0x0E71
}

class ExtensionDueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExtensionDueScheduler.ACTION_DUE) return
        val container = (context.applicationContext as? ClassScheduleApplication)?.appContainer ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.extensionCoordinator.fireDueReminders()
            } catch (error: Throwable) {
                ReminderLogger.warn("extension.due.fire_failed", emptyMap(), error)
            } finally {
                pending.finish()
            }
        }
    }
}
