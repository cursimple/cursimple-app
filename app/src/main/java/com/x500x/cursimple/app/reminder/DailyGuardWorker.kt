package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x500x.cursimple.app.AppContainer
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 每日守护 Worker - WorkManager 每日任务
 *
 * 每天凌晨 2:00 执行，主要职责：
 * 1. 全量重建所有闹钟注册
 * 2. 清理过期的闹钟记录
 * 3. 同步未来 7 天的课程提醒
 *
 * 这是闹钟可靠性的核心保障机制，确保每天都有一次完整的闹钟状态重建，
 * 防止因系统清理或其他原因导致的闹钟失效问题。
 */
class DailyGuardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            ReminderLogger.info(
                "reminder.worker.daily_guard.start",
                mapOf("worker" to WORKER_NAME),
            )

            // 获取 AppContainer（手动依赖注入）
            val appContainer = getAppContainer()

            // 1. 先执行全量闹钟重建
            appContainer.refreshScheduleOutputs(recreateAppManagedAlarms = true)

            // 2. 执行共享闹钟完整性检查（今天和明天）
            val summaries = appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.DailyNextDay,
                includeTomorrow = true,
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // 记录每日巡检结果
            val totalCreated = summaries.sumOf { it.createdCount }
            val totalSubmitted = summaries.sumOf { it.submittedCount }
            val totalFailed = summaries.sumOf { it.failedCount }
            val expiredCleared = summaries.sumOf { it.expiredRecordClearedCount }

            ReminderLogger.info(
                "reminder.worker.daily_guard.finish",
                mapOf(
                    "worker" to WORKER_NAME,
                    "durationMs" to duration,
                    "totalSubmitted" to totalSubmitted,
                    "totalCreated" to totalCreated,
                    "totalFailed" to totalFailed,
                    "expiredCleared" to expiredCleared,
                    "summaryCount" to summaries.size,
                ),
            )

            // 每日巡检无论失败与否都视为成功，下次会重试
            // 只有连续失败才会触发 WorkManager 的回退策略
            Result.success()
        } catch (e: Exception) {
            ReminderLogger.warn(
                "reminder.worker.daily_guard.failure",
                mapOf("worker" to WORKER_NAME),
                e,
            )
            // 每日守护失败也要重试
            Result.retry()
        }
    }

    private fun getAppContainer(): AppContainer {
        val application = applicationContext.applicationContext
        val appField = application.javaClass.getDeclaredField("appContainer")
        appField.isAccessible = true
        return appField.get(application) as AppContainer
    }

    companion object {
        const val WORKER_NAME = "DailyGuardWorker"
    }
}
