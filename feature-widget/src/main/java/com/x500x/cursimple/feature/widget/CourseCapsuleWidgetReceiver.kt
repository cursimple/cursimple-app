package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

open class CourseCapsuleWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (handleVendorWidgetUpdate(context, intent) { updateWidgets(it) }) return
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        reconcileSystemAlarmsFromWidget(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CATALOG_ID = "capsule"

        suspend fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.widgetLocaleContext()
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetIds ?: collectIds(appContext, manager)
            if (ids.isEmpty()) return
            val data = NextCourseDataSource.load(appContext)
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId, data))
            }
        }

        private fun collectIds(context: Context, manager: AppWidgetManager): IntArray {
            val ids = WidgetCatalog.entries(context)
                .firstOrNull { it.id == CATALOG_ID }
                ?.let { entry ->
                    (listOf(entry.provider) + entry.vendorProviders)
                        .flatMap { component ->
                            runCatching { manager.getAppWidgetIds(component).toList() }.getOrDefault(emptyList())
                        }
                }
                .orEmpty()
            return ids.toIntArray()
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
            data: NextCourseWidgetData,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_course_capsule)
            val isDark = resolveWidgetIsDark(context, data.widgetTheme.widgetThemeMode)
            views.applyWidgetBackground(context, R.id.capsule_root, data.widgetTheme)
            views.applyOpenAppClick(context, R.id.capsule_root, appWidgetId, data.widgetTheme)

            val current = data.rows.firstOrNull { it.isFocus || !it.isPast }
            if (current != null) {
                val now = BeijingTime.nowTimeIn(BeijingTime.zone)
                val startClock = current.time.substringBefore("–").substringBefore("-").trim()
                val minutesUntilStart = parseClockMinutes(startClock, now)
                val isUrgent = minutesUntilStart in 0..10

                val tagText = when {
                    isUrgent -> context.getString(R.string.widget_capsule_tag_urgent)
                    else -> "[下一节]"
                }
                val contentText = "${current.title} · $startClock · ${current.sub.ifBlank { "待定" }}"
                views.setTextViewText(R.id.capsule_tag, tagText)
                views.setTextViewText(R.id.capsule_text, contentText)

                if (isUrgent) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        views.setColorStateList(R.id.capsule_tag, "setBackgroundTintList", ColorStateList.valueOf(0xFFE53935.toInt()))
                    }
                    views.setTextColor(R.id.capsule_tag, 0xFFFFFFFF.toInt())
                    views.setTextColor(R.id.capsule_text, if (isDark) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt())
                } else {
                    val accentColor = widgetAccentTextColor(data.widgetTheme, isDark)
                    views.setTextColor(R.id.capsule_tag, accentColor)
                    views.setTextColor(R.id.capsule_text, if (isDark) 0xFFE6E1E5.toInt() else 0xFF20242A.toInt())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        views.setColorStateList(R.id.capsule_tag, "setBackgroundTintList", null)
                    }
                }
            } else {
                views.setTextViewText(R.id.capsule_tag, "[无课程]")
                views.setTextViewText(R.id.capsule_text, context.getString(R.string.widget_empty_other_day))
                views.setTextColor(R.id.capsule_tag, if (isDark) 0xFF938F99.toInt() else 0xFF79747E.toInt())
                views.setTextColor(R.id.capsule_text, if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(R.id.capsule_tag, "setBackgroundTintList", null)
                }
            }
            return views
        }

        private fun parseClockMinutes(clock: String, now: LocalTime): Int? {
            val parts = clock.split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val target = LocalTime.of(h, m)
            val diff = Duration.between(now, target).toMinutes()
            return diff.toInt()
        }
    }
}
