package com.x500x.cursimple.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 厂商启动器自己定义的小组件刷新广播。
 *
 * AppWidgetProvider 只把 AOSP 的 APPWIDGET_UPDATE 转成 onUpdate，这些厂商 action
 * 走到 onReceive 就被丢掉，小组件于是一直停在「加载中」的初始布局上。
 * 收到它们时直接按一次全量刷新处理。
 */
internal val VENDOR_WIDGET_UPDATE_ACTIONS: Set<String> = setOf(
    "miui.appwidget.action.APPWIDGET_UPDATE",
    "com.vivo.widget.action.APPWIDGET_UPDATE",
    "com.huawei.android.launcher.action.APPWIDGET_UPDATE",
    "com.hihonor.android.launcher.action.APPWIDGET_UPDATE",
    "com.oppo.launcher.action.APPWIDGET_UPDATE",
)

/** 厂商刷新广播交给 [refresh]；不是这类广播时返回 false，交回 AppWidgetProvider 原有处理。 */
internal fun BroadcastReceiver.handleVendorWidgetUpdate(
    context: Context,
    intent: Intent,
    refresh: suspend (Context) -> Unit,
): Boolean {
    if (intent.action !in VENDOR_WIDGET_UPDATE_ACTIONS) return false
    val appContext = context.applicationContext
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            refresh(appContext)
        } catch (error: Throwable) {
            ReminderLogger.warn(
                "widget.vendor_update.failure",
                mapOf("action" to intent.action.orEmpty()),
                error,
            )
        } finally {
            pendingResult.finish()
        }
    }
    return true
}
