package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.core.content.edit

/**
 * 到达去重与世代号。
 *
 * 双通道会让同一条闹钟到达两次，先到的那条把 alarmKey 记进本次触发的名册，
 * 后到的直接被判为重复。名册落在 SharedPreferences 里，进程被杀后重启仍然有效。
 *
 * 世代号在每次重排前自增，触发时携带的世代号比当前小就说明排程已被改过，
 * 这条在途广播作废。
 */
object AlarmArrivalLedger {
    private const val PREFS_NAME = "alarm_arrival_ledger"
    private const val KEY_GENERATION = "generation"
    private const val KEY_HANDLED_PREFIX = "handled:"

    /** 名册条目保留这么久，够覆盖一次响铃的全过程，之后随清理丢弃。 */
    private const val ENTRY_TTL_MILLIS = 30 * 60 * 1000L

    /** 名册超过这个条数就清一次过期项，避免无限增长。 */
    private const val CLEANUP_THRESHOLD = 64

    fun currentGeneration(context: Context): Long =
        prefs(context).getLong(KEY_GENERATION, 0L)

    /** 重排前调用，让所有在途广播立刻变旧。 */
    fun bumpGeneration(context: Context): Long {
        val store = prefs(context)
        val next = store.getLong(KEY_GENERATION, 0L) + 1
        store.edit { putLong(KEY_GENERATION, next) }
        return next
    }

    /**
     * 把这次触发标记为已处理，返回 true 表示本次调用是先到的那条。
     * [triggerAtMillis] 参与键名，同一条闹钟的不同次触发互不干扰。
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

    /** 撤销标记，供响铃启动失败后让另一条通道还有机会接手。 */
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
