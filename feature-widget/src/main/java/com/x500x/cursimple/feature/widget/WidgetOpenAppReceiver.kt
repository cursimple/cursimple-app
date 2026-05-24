package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        if (intent.action !in setOf(ACTION_OPEN_APP, ACTION_OPEN_APP_DOUBLE_CLICK)) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val preferences = DataStoreWidgetPreferencesRepository(appContext)
                    .themePreferencesFlow
                    .first()
                if (!preferences.openAppOnDoubleClickEnabled) return@launch

                openLaunchActivity(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_OPEN_APP =
            "com.x500x.cursimple.feature.widget.action.OPEN_APP"
        private const val ACTION_OPEN_APP_DOUBLE_CLICK =
            "com.x500x.cursimple.feature.widget.action.OPEN_APP_DOUBLE_CLICK"
        private const val REQUEST_CODE_BASE = 9400
        private const val REQUEST_CODE_TEMPLATE_BASE = 19400

        fun pendingIntent(
            context: Context,
            appWidgetId: Int,
            fillInTemplate: Boolean = false,
        ): PendingIntent {
            val intent = Intent(context, WidgetOpenAppReceiver::class.java).apply {
                action = ACTION_OPEN_APP
                setPackage(context.packageName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                (if (fillInTemplate) REQUEST_CODE_TEMPLATE_BASE else REQUEST_CODE_BASE) + appWidgetId,
                intent,
                pendingIntentFlags(fillInTemplate),
            )
        }

        private fun pendingIntentFlags(fillInTemplate: Boolean): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fillInTemplate) {
                flags or PendingIntent.FLAG_MUTABLE
            } else {
                flags or PendingIntent.FLAG_IMMUTABLE
            }
            return flags
        }
    }
}

internal fun RemoteViews.applyOpenAppClick(
    context: Context,
    viewId: Int,
    appWidgetId: Int,
    @Suppress("UNUSED_PARAMETER")
    theme: WidgetThemePreferences,
) {
    setOnClickPendingIntent(viewId, WidgetOpenAppReceiver.pendingIntent(context, appWidgetId))
}

internal fun RemoteViews.applyOpenAppListTemplate(
    context: Context,
    listId: Int,
    appWidgetId: Int,
    @Suppress("UNUSED_PARAMETER")
    theme: WidgetThemePreferences,
) {
    setPendingIntentTemplate(
        listId,
        WidgetOpenAppReceiver.pendingIntent(context, appWidgetId, fillInTemplate = true),
    )
}

internal fun RemoteViews.applyOpenAppFillInIntent(
    viewId: Int,
    @Suppress("UNUSED_PARAMETER")
    theme: WidgetThemePreferences,
) {
    setOnClickFillInIntent(viewId, Intent())
}

private fun openLaunchActivity(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    context.startActivity(launchIntent)
}
