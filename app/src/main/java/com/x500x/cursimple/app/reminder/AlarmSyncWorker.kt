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
 * 闹钟同步 Worker - WorkManager 定期任务
 *
 * 每 2 小时执行一次，主要职责：
 * 1. 验证现有闹钟注册状态
 * 2. 重建失效的闹钟
 * 3. 同步未来 7 天的课程提醒
 *
 * 这是闹钟可靠性的兜底机制，确保即使某些同步事件被跳过，
 * 闹钟也能在定期巡检中得到修复。
 */
class AlarmSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            ReminderLogger.info(
                "reminder.worker.sync.start",
                mapOf("worker" to WORKER_NAME),
            )

            // 获取 AppContainer（手动依赖注入）
            val appContainer = getAppContainer()

            // 执行共享闹钟完整性检查
            val summaries = appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.WorkerBackgroundSync,
                includeTomorrow = true,
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // 记录同步结果
            val totalCreated = summaries.sumOf { it.createdCount }
            val totalSubmitted = summaries.sumOf { it.submittedCount }
            val totalFailed = summaries.sumOf { it.failedCount }

            ReminderLogger.info(
                "reminder.worker.sync.finish",
                mapOf(
                    "worker" to WORKER_NAME,
                    "durationMs" to duration,
                    "totalSubmitted" to totalSubmitted,
                    "totalCreated" to totalCreated,
                    "totalFailed" to totalFailed,
                    "summaryCount" to summaries.size,
                ),
            )

            // 如果有失败的重试一次
            if (totalFailed > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            ReminderLogger.warn(
                "reminder.worker.sync.failure",
                mapOf("worker" to WORKER_NAME),
                e,
            )
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
        const val WORKER_NAME = "AlarmSyncWorker"
    }
}
