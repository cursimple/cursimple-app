package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动静音巡检 Worker。
 *
 * 每 15 分钟复查一次手机状态，边界闹钟被系统丢弃、进程被杀或设备重启时由它把手机调回来，
 * 最坏情况下手机多静音一个巡检周期，不会一直静音下去。
 */
class AutoSilenceGuardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AutoSilenceController.evaluate(applicationContext, reason = WORKER_NAME)
            Result.success()
        } catch (error: Exception) {
            ReminderLogger.warn(
                "reminder.auto_silence.worker.failure",
                mapOf("worker" to WORKER_NAME),
                error,
            )
            Result.retry()
        }
    }

    companion object {
        const val WORKER_NAME = "AutoSilenceGuardWorker"
    }
}
