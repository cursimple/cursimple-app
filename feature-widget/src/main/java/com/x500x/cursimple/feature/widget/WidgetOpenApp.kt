package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences

/** Use direct Activity PendingIntent for launcher click feedback and opening transitions. */
private fun openAppPendingIntent(context: Context, appWidgetId: Int, fillInTemplate: Boolean, extraIntent: Intent? = null): PendingIntent? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        ?: return null
    extraIntent?.extras?.let { launchIntent.putExtras(it) }
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fillInTemplate) {
        flags or PendingIntent.FLAG_MUTABLE
    } else {
        flags or PendingIntent.FLAG_IMMUTABLE
    }
    val requestCode = (if (fillInTemplate) REQUEST_CODE_TEMPLATE_BASE else REQUEST_CODE_BASE) + appWidgetId
    return PendingIntent.getActivity(context, requestCode, launchIntent, flags)
}

private const val REQUEST_CODE_BASE = 9400
private const val REQUEST_CODE_TEMPLATE_BASE = 19400

internal fun RemoteViews.applyOpenAppClick(
    context: Context,
    viewId: Int,
    appWidgetId: Int,
    theme: WidgetThemePreferences,
    extraIntent: Intent? = null,
) {
    if (!theme.openAppOnDoubleClickEnabled) return
    val pendingIntent = openAppPendingIntent(context, appWidgetId, fillInTemplate = false, extraIntent = extraIntent) ?: return
    setOnClickPendingIntent(viewId, pendingIntent)
}

internal fun RemoteViews.applyOpenAppListTemplate(
    context: Context,
    listId: Int,
    appWidgetId: Int,
    theme: WidgetThemePreferences,
) {
    if (!theme.openAppOnDoubleClickEnabled) return
    val pendingIntent = openAppPendingIntent(context, appWidgetId, fillInTemplate = true) ?: return
    setPendingIntentTemplate(listId, pendingIntent)
}

internal fun RemoteViews.applyOpenAppFillInIntent(
    viewId: Int,
    theme: WidgetThemePreferences,
) {
    if (!theme.openAppOnDoubleClickEnabled) return
    setOnClickFillInIntent(viewId, Intent())
}
