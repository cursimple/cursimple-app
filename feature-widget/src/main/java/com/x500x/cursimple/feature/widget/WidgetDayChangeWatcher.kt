package com.x500x.cursimple.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runtime date, clock and unlock broadcasts supplement midnight refresh. Unlock refreshes only
 * stale rendering; Android 8+ requires dynamic registration.
 */
object WidgetDayChangeWatcher {

    @Volatile
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(app, Receiver(), filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { error ->
            registered = false
            ReminderLogger.warn("widget.day_change_watcher.register_failure", emptyMap(), error)
        }
    }

    private class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext
            val dayChanged = intent.action == Intent.ACTION_DATE_CHANGED ||
                intent.action == Intent.ACTION_TIME_CHANGED ||
                intent.action == Intent.ACTION_TIMEZONE_CHANGED
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    val stale = ScheduleWidgetDataSource.lastRenderedTodayIso
                        ?.let { it != widgetTodayIso(app) }
                        ?: false
                    if (dayChanged || stale) {
                        ScheduleWidgetUpdater.refreshAll(app)
                    }
                } catch (error: Throwable) {
                    ReminderLogger.warn(
                        "widget.day_change_watcher.refresh_failure",
                        mapOf("action" to intent.action.orEmpty()),
                        error,
                    )
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
