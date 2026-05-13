package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetOpenAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OPEN_APP_DOUBLE_CLICK) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val preferences = DataStoreWidgetPreferencesRepository(appContext)
                    .themePreferencesFlow
                    .first()
                if (!preferences.openAppOnDoubleClickEnabled) return@launch

                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (recordAndCheckDoubleClick(appContext, appWidgetId)) {
                    openLaunchActivity(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_OPEN_APP_DOUBLE_CLICK =
            "com.x500x.cursimple.feature.widget.action.OPEN_APP_DOUBLE_CLICK"
        private const val REQUEST_CODE_BASE = 9400

        fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetOpenAppReceiver::class.java).apply {
                action = ACTION_OPEN_APP_DOUBLE_CLICK
                setPackage(context.packageName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

internal fun RemoteViews.applyOpenAppOnDoubleClick(
    context: Context,
    rootId: Int,
    appWidgetId: Int,
    theme: WidgetThemePreferences,
) {
    setOnClickPendingIntent(
        rootId,
        if (theme.openAppOnDoubleClickEnabled) {
            WidgetOpenAppReceiver.pendingIntent(context, appWidgetId)
        } else {
            null
        },
    )
}

private fun recordAndCheckDoubleClick(context: Context, appWidgetId: Int): Boolean {
    val prefs = context.getSharedPreferences(WidgetOpenAppReceiverPrefs.name, Context.MODE_PRIVATE)
    val key = WidgetOpenAppReceiverPrefs.key(appWidgetId)
    val now = SystemClock.elapsedRealtime()
    val last = prefs.getLong(key, 0L)
    val doubleClick = last > 0L && now - last in 1L..WidgetOpenAppReceiverPrefs.windowMillis
    prefs.edit().apply {
        if (doubleClick) {
            remove(key)
        } else {
            putLong(key, now)
        }
    }.apply()
    return doubleClick
}

private fun openLaunchActivity(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    context.startActivity(launchIntent)
}

private object WidgetOpenAppReceiverPrefs {
    const val name = "widget_open_app_clicks"
    const val windowMillis = 650L

    fun key(appWidgetId: Int): String = "last_click_$appWidgetId"
}
