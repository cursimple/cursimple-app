package com.x500x.cursimple.app.reminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.*

/** Explicit, user-visible guard; alarms and jobs remain the recovery fallback. */
class ReminderGuardForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inspection: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) setEnabled(this, false)
        if (!isEnabled(this) || !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            state.value = if (isEnabled(this)) "notification_blocked" else "disabled"
            stopSelf(); return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.reminder_guard_channel), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        val open = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, ID, Intent(this, ReminderGuardForegroundService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.reminder_guard_title)).setContentText(getString(R.string.reminder_guard_running))
            .setPriority(NotificationCompat.PRIORITY_LOW).setSilent(true)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setContentIntent(open)
            .addAction(R.drawable.ic_notification, getString(R.string.reminder_guard_stop), stop)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification)
        } catch (error: Exception) {
            ReminderLogger.warn("reminder.foreground_guard.start_failed", emptyMap(), error)
            state.value = "deferred"; stopSelf();return START_NOT_STICKY
        }
        state.value = "running"
        if (inspection?.isActive != true) inspection = scope.launch {
            while (isActive && isEnabled(this@ReminderGuardForegroundService)) {
                try { withTimeout(20_000) { ReminderGuardMaintenance.run(this@ReminderGuardForegroundService, "foreground_guard") } }
                catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled }
                catch (error: Exception) { ReminderLogger.warn("reminder.foreground_guard.repair_failed", emptyMap(), error) }
                delay(15*60_000L)
            }
        }
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        ReminderGuardMaintenance.enqueue(this)
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() { scope.cancel(); if (state.value == "running") state.value = "stopped"; super.onDestroy() }
    companion object {
        val state = kotlinx.coroutines.flow.MutableStateFlow("stopped")
        val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
        private const val CHANNEL = "reminder_guard_foreground"
        private const val ID = 0x0C26
        private const val STOP = "com.x500x.cursimple.action.STOP_REMINDER_GUARD"
        fun isEnabled(context: Context) = context.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).getBoolean("foreground_enabled", false)
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).edit().putBoolean("foreground_enabled", enabled).apply()
            changes.value++
            if (!enabled) state.value = "disabled"
            if (!enabled) context.stopService(Intent(context, ReminderGuardForegroundService::class.java))
        }
        fun startIfEnabled(context: Context) {
            if (!isEnabled(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ReminderGuardForegroundService::class.java)) }
                .onFailure { state.value = "deferred"; ReminderLogger.warn("reminder.foreground_guard.deferred", emptyMap(), it) }
        }
    }
}
