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
 * Translate vendor update actions into a full refresh because AppWidgetProvider ignores them by
 * default.
 */
internal val VENDOR_WIDGET_UPDATE_ACTIONS: Set<String> = setOf(
    "miui.appwidget.action.APPWIDGET_UPDATE",
    "com.vivo.widget.action.APPWIDGET_UPDATE",
    "com.huawei.android.launcher.action.APPWIDGET_UPDATE",
    "com.hihonor.android.launcher.action.APPWIDGET_UPDATE",
    "com.oppo.launcher.action.APPWIDGET_UPDATE",
)

/** Return false for nonvendor actions so normal provider handling continues. */
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
