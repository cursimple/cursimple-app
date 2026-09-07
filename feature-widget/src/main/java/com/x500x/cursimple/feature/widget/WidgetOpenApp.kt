package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences

/**
 * 点小组件打开应用。
 *
 * 直接给启动 Activity 的 PendingIntent，由系统接管点击反馈与启动动画：
 * 中间过一道广播再 startActivity 会丢掉从被点位置展开的转场，
 * 桌面上看到的就是一层突兀的遮罩。
 */
private fun openAppPendingIntent(context: Context, appWidgetId: Int, fillInTemplate: Boolean): PendingIntent? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        ?: return null
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

/** 关掉「点按打开应用」时不挂点击目标，点上去就没有任何反馈。 */
internal fun RemoteViews.applyOpenAppClick(
    context: Context,
    viewId: Int,
    appWidgetId: Int,
    theme: WidgetThemePreferences,
) {
    if (!theme.openAppOnDoubleClickEnabled) return
    val pendingIntent = openAppPendingIntent(context, appWidgetId, fillInTemplate = false) ?: return
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
