package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 闹钟同步调度器 - 封装 WorkManager 定期任务调度逻辑
 *
 * 提供两种定期同步机制：
 * 1. AlarmSyncWorker - 每 2 小时执行一次，用于常规闹钟同步
 * 2. DailyGuardWorker - 每天凌晨执行，用于全量闹钟重建
 */
object AlarmSyncScheduler {

    private const val SYNC_WORK_NAME = "alarm_sync_periodic"
    private const val DAILY_GUARD_WORK_NAME = "alarm_daily_guard"

    /**
     * 调度周期性闹钟同步 Worker
     * 每 2 小时执行一次，确保闹钟注册状态与数据库记录一致
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AlarmSyncWorker>(
            2, TimeUnit.HOURS,
            30, TimeUnit.MINUTES,  // 弹性延迟
        )
            .setConstraints(constraints)
            .addTag(SYNC_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    /**
     * 调度每日守护 Worker
     * 每天凌晨 2:00 执行，全量重建所有闹钟注册
     */
    fun scheduleDailyGuard(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // 计算到凌晨 2:00 的初始延迟
        val now = LocalDateTime.now()
        val targetTime = if (now.toLocalTime().isBefore(DAILY_GUARD_TIME)) {
            LocalDateTime.of(now.toLocalDate(), DAILY_GUARD_TIME)
        } else {
            LocalDateTime.of(now.toLocalDate().plusDays(1), DAILY_GUARD_TIME)
        }
        val initialDelayMinutes = Duration.between(now, targetTime).toMinutes()

        val workRequest = PeriodicWorkRequestBuilder<DailyGuardWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS,  // 弹性延迟
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .addTag(DAILY_GUARD_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_GUARD_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    /**
     * 取消所有定期同步任务
     * 通常在需要完全重置同步状态时调用
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_GUARD_WORK_NAME)
    }

    private val DAILY_GUARD_TIME: LocalTime = LocalTime.of(2, 0)
}
