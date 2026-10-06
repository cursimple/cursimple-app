package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.core.content.edit

/**
 * Persist arrival deduplication across restarts. Increment generations on reschedule so stale
 * in-flight broadcasts are rejected.
 */
object AlarmArrivalLedger {
    private const val PREFS_NAME = "alarm_arrival_ledger"
    private const val KEY_GENERATION = "generation"
    private const val KEY_HANDLED_PREFIX = "handled:"

    private const val ENTRY_TTL_MILLIS = 30 * 60 * 1000L

    /** Prune expired ledger entries after this size threshold. */
    private const val CLEANUP_THRESHOLD = 64

    fun currentGeneration(context: Context): Long =
        prefs(context).getLong(KEY_GENERATION, 0L)

    /**
     * Return true for the first arrival; [triggerAtMillis] distinguishes occurrences of the
     * same alarm.
     */
    fun claim(context: Context, alarmKey: String, triggerAtMillis: Long): Boolean {
        val store = prefs(context)
        val key = entryKey(alarmKey, triggerAtMillis)
        synchronized(this) {
            if (store.contains(key)) return false
            store.edit { putLong(key, System.currentTimeMillis()) }
        }
        if (store.all.size > CLEANUP_THRESHOLD) {
            cleanup(context)
        }
        return true
    }

    /**
     * Release the arrival claim when startup fails so the alternate delivery path can retry.
     */
    fun release(context: Context, alarmKey: String, triggerAtMillis: Long) {
        prefs(context).edit { remove(entryKey(alarmKey, triggerAtMillis)) }
    }

    private fun cleanup(context: Context) {
        val store = prefs(context)
        val now = System.currentTimeMillis()
        val stale = store.all
            .filterKeys { it.startsWith(KEY_HANDLED_PREFIX) }
            .filterValues { it is Long && now - it > ENTRY_TTL_MILLIS }
            .keys
        if (stale.isEmpty()) return
        store.edit { stale.forEach(::remove) }
    }

    private fun entryKey(alarmKey: String, triggerAtMillis: Long): String =
        "$KEY_HANDLED_PREFIX$alarmKey@$triggerAtMillis"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
