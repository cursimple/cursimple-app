package com.kebiao.viewer.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class WidgetCatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val provider: ComponentName,
    /** Vendor-aware twin receivers (MIUI/vivo/HONOR). Empty when not applicable. */
    val vendorProviders: List<ComponentName> = emptyList(),
)

object WidgetCatalog {
    fun entries(context: Context): List<WidgetCatalogEntry> {
        val pkg = context.packageName
        return listOf(
            WidgetCatalogEntry(
                id = "next",
                title = "下一节课",
                description = "紧凑显示当前与下一节课程，附时间和地点",
                provider = ComponentName(pkg, NextCourseGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.kebiao.viewer.feature.widget.NextCourseGlanceWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = "today",
                title = "今日课程",
                description = "今天/明天的全部课程列表，可一键切换",
                provider = ComponentName(pkg, ScheduleGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.kebiao.viewer.feature.widget.ScheduleGlanceWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = "reminder",
                title = "课程提醒",
                description = "未来即将触发的课程提醒清单",
                provider = ComponentName(pkg, ReminderGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.kebiao.viewer.feature.widget.ReminderGlanceWidgetReceiverMIUI"),
                ),
            ),
        )
    }

    fun installedCount(context: Context, entry: WidgetCatalogEntry): Int {
        val manager = AppWidgetManager.getInstance(context)
        val all = listOf(entry.provider) + entry.vendorProviders
        return all.sumOf { component ->
            runCatching { manager.getAppWidgetIds(component).size }.getOrDefault(0)
        }
    }

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    fun requestPin(context: Context, entry: WidgetCatalogEntry): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(),
            Intent(ACTION_WIDGET_PINNED).setPackage(context.packageName),
            pendingIntentFlags(),
        )
        return manager.requestPinAppWidget(entry.provider, null, callback)
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    /** Action for an app-local broadcast emitted whenever the installed-widget set may have changed. */
    const val ACTION_WIDGET_INSTALLED_CHANGED: String = "com.kebiao.viewer.WIDGET_INSTALLED_CHANGED"

    fun notifyInstalledChanged(context: Context) {
        val intent = Intent(ACTION_WIDGET_INSTALLED_CHANGED).setPackage(context.packageName)
        context.applicationContext.sendBroadcast(intent)
    }

    enum class LauncherVendor {
        Miui,
        Huawei,
        Oppo,
        Vivo,
        Samsung,
        Other,
    }

    /** Detects the foreground launcher's vendor so we can give targeted instructions. */
    fun detectLauncherVendor(context: Context): LauncherVendor {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = pm.resolveActivity(intent, 0)
        val pkg = resolved?.activityInfo?.packageName.orEmpty()
        return when {
            pkg.contains("miui", ignoreCase = true) ||
                pkg.startsWith("com.mi", ignoreCase = true) -> LauncherVendor.Miui
            pkg.contains("huawei", ignoreCase = true) ||
                pkg.contains("honor", ignoreCase = true) -> LauncherVendor.Huawei
            pkg.contains("oppo", ignoreCase = true) ||
                pkg.contains("oplus", ignoreCase = true) ||
                pkg.contains("realme", ignoreCase = true) -> LauncherVendor.Oppo
            pkg.contains("vivo", ignoreCase = true) ||
                pkg.contains("bbk", ignoreCase = true) ||
                pkg.contains("iqoo", ignoreCase = true) -> LauncherVendor.Vivo
            pkg.contains("samsung", ignoreCase = true) ||
                pkg.contains("sec.android", ignoreCase = true) -> LauncherVendor.Samsung
            else -> LauncherVendor.Other
        }
    }

    /** Opens the system "App Info" page so the user can grant background-popup / floating-window permissions. */
    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private const val ACTION_WIDGET_PINNED = "com.kebiao.viewer.WIDGET_PINNED"
}
