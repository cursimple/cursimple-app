package com.x500x.cursimple.app.reminder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * Inspect new USER_REQUESTED exit records at startup. Force-stop prevents background wake-ups
 * until reopening; alarm rebuilding is handled separately.
 */
object ForceStopMonitor {
    private const val PREFS = "force_stop_monitor"
    private const val KEY_LAST_SEEN = "last_seen_exit_at"
    private const val KEY_LAST_FORCE_STOP = "last_force_stop_at"
    private const val KEY_PROMPT_PENDING = "prompt_pending"
    private const val KEY_LAST_PROMPT_AT = "last_prompt_at"

    /** Limit repeated prompts to once per three days. */
    private const val PROMPT_COOLDOWN_MILLIS = 3 * 24 * 60 * 60 * 1000L

    fun onProcessStart(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
            .takeIf { it <= System.currentTimeMillis() + 60_000L } ?: 0L
        val exits = runCatching {
            app.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(app.packageName, 0, 8)
                .orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.timestamp <= System.currentTimeMillis() + 60_000L }
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        // Initialize the baseline without counting pre-install exit history.
        if (lastSeen == 0L) {
            prefs.edit().putLong(KEY_LAST_SEEN, newest).apply()
            return
        }
        // Both explicit and vendor-triggered force-stops may be USER_REQUESTED.
        val forceStop = exits
            .filter { it.timestamp > lastSeen && it.reason == ApplicationExitInfo.REASON_USER_REQUESTED }
            .maxByOrNull { it.timestamp }
        val editor = prefs.edit().putLong(KEY_LAST_SEEN, maxOf(newest, lastSeen))
        if (forceStop != null) {
            ReminderLogger.warn(
                "reminder.force_stop.detected",
                mapOf("at" to forceStop.timestamp, "description" to forceStop.description.orEmpty()),
            )
            editor.putLong(KEY_LAST_FORCE_STOP, forceStop.timestamp)
            val lastPrompt = prefs.getLong(KEY_LAST_PROMPT_AT, 0L)
            if (System.currentTimeMillis() - lastPrompt > PROMPT_COOLDOWN_MILLIS) {
                editor.putBoolean(KEY_PROMPT_PENDING, true)
            }
        }
        editor.apply()
    }

    fun lastForceStopAtMillis(context: Context): Long? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FORCE_STOP, 0L)
            .takeIf { it > 0L }

    fun promptPending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPT_PENDING, false)

    fun markPrompted(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PROMPT_PENDING, false)
            .putLong(KEY_LAST_PROMPT_AT, System.currentTimeMillis())
            .apply()
    }
}
