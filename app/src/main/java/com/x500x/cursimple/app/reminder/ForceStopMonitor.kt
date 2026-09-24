package com.x500x.cursimple.app.reminder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * 发现课简上次是被「强行停止」的。
 *
 * 强行停止会清掉本应用挂着的全部闹钟、任务和广播，之后什么都叫不醒它，
 * 直到用户自己再打开——这中间的闹钟和上课提醒一个都不会响。小米国行不开「自启动」时，
 * 从最近任务划掉应用走的就是这条路，用户往往根本不知道。
 *
 * 启动时翻一下系统记的退出原因，查到新的强行停止就记下来，界面据此提醒去开自启动。
 * 打开应用时闹钟会整体重挂一遍（见 AppContainer.ensureAlarmRuntimeHealth），这里只负责说清楚。
 */
object ForceStopMonitor {
    private const val PREFS = "force_stop_monitor"
    private const val KEY_LAST_SEEN = "last_seen_exit_at"
    private const val KEY_LAST_FORCE_STOP = "last_force_stop_at"
    private const val KEY_PROMPT_PENDING = "prompt_pending"
    private const val KEY_LAST_PROMPT_AT = "last_prompt_at"

    /** 同一件事别天天弹：三天内提示过就只在权限页里留一行字。 */
    private const val PROMPT_COOLDOWN_MILLIS = 3 * 24 * 60 * 60 * 1000L

    /** 进程启动时调一次。 */
    fun onProcessStart(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 记下的时间比现在还晚，说明系统时间被往回调过；按第一次跑处理，别把之后的记录全当旧的
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
            .takeIf { it <= System.currentTimeMillis() + 60_000L } ?: 0L
        val exits = runCatching {
            app.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(app.packageName, 0, 8)
                .orEmpty()
        }.getOrDefault(emptyList())
            // 时间被往回调过时，系统记录里会有「未来」的条目，拿它当起点会把之后的全挡掉
            .filter { it.timestamp <= System.currentTimeMillis() + 60_000L }
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        // 第一次跑只记个起点：装上之前的退出记录不算数
        if (lastSeen == 0L) {
            prefs.edit().putLong(KEY_LAST_SEEN, newest).apply()
            return
        }
        // 用户在系统设置里点「强行停止」、厂商把划掉任务当强停，记下的原因都是 USER_REQUESTED
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

    /** 有一条还没给用户看过的强行停止提醒。 */
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
