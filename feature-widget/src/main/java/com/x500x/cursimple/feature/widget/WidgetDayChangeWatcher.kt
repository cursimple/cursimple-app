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
 * 换天时把小组件重画一遍的第二道保险。
 *
 * 第一道是零点那次精确闹钟（[WidgetBoundaryRefreshScheduler]），但不少国产系统会把
 * 后台应用的闹钟推迟甚至吞掉。零点没刷到，小组件就停在昨晚的渲染上：昨晚翻到的
 * 「明天」已经是今天了，「回今天」却还挂着，标签也还写着「明天」。
 *
 * 这里在进程活着时（应用常驻着闹钟守护前台服务，大多数时候都活着）监听系统广播：
 * - 日期变了、时间或时区被改了：直接重画；
 * - 亮屏或解锁：只在「上次渲染时的今天」已经过期时才重画，平时亮屏不多做事。
 *
 * 这些都是隐式广播，Android 8 起只能在运行时注册，清单里注册收不到。
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
