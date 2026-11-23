package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
                ScheduleWidgetUpdater.rescheduleBoundaryRefresh(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ScheduleWidgetUpdater.rescheduleBoundaryRefresh(context.applicationContext)
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
            updateWidgets(context, appWidgetIds, sharedData = null)
        }

        internal suspend fun updateWidgets(
            context: Context,
            appWidgetIds: IntArray? = null,
            sharedData: NextCourseWidgetData?,
        ) {
            val appContext = context.widgetLocaleContext()
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetIds ?: collectIds(appContext, manager)
            if (ids.isEmpty()) return
            val data = sharedData ?: NextCourseDataSource.load(appContext)
            val state = resolveCourseCapsuleState(data.rows, BeijingTime.nowDateTime())
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId, data, state))
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
            state: CourseCapsuleState?,
        ): RemoteViews {
            val widthDp = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val compact = widthDp < 240
            val views = RemoteViews(context.packageName, if (compact) {
                R.layout.widget_course_capsule_compact_surface
            } else R.layout.widget_course_capsule_surface)
            val isDark = resolveWidgetIsDark(context, data.widgetTheme.widgetThemeMode)
            views.applyWidgetBackground(context, data.widgetTheme)
            views.applyOpenAppClick(context, R.id.capsule_root, appWidgetId, data.widgetTheme)
            val urgent = state?.urgent == true
            views.setViewVisibility(R.id.capsule_notification_icon, if (urgent) View.VISIBLE else View.GONE)
            views.setInt(R.id.capsule_badge, "setBackgroundResource", if (urgent) {
                R.drawable.widget_bg_badge_alert
            } else R.drawable.widget_bg_badge)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.capsule_badge, "setBackgroundTintList", null)
            }

            if (state != null) {
                val current = state.course
                val startClock = current.time.substringBefore("–").substringBefore("-").trim()
                val isUrgent = state.urgent

                val tagText = when {
                    state.inProgress && state.elapsedMinutes > 0 ->
                        "[${context.getString(R.string.widget_status_live)} ${context.getString(R.string.widget_reminder_countdown_minutes, state.elapsedMinutes)}]"
                    state.inProgress -> "[${context.getString(R.string.widget_status_live)}]"
                    isUrgent && state.remainingMinutes > 0 ->
                        context.getString(R.string.widget_countdown_minutes, state.remainingMinutes)
                    isUrgent -> context.getString(R.string.widget_capsule_tag_urgent)
                    else -> "[${context.getString(R.string.widget_label_next)}]"
                }
                val detail = "$startClock ${current.sub.ifBlank { context.getString(R.string.widget_course_subtitle_placeholder) }}"
                val contentText = if (compact) current.title else "${current.title} $detail"
                views.setTextViewText(R.id.capsule_tag, tagText)
                views.setTextViewText(R.id.capsule_text, contentText)
                views.setContentDescription(R.id.capsule_root, "$tagText ${current.title}, $detail")
                if (compact) {
                    views.setTextViewText(R.id.capsule_detail, detail)
                    views.setTextColor(R.id.capsule_detail, if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt())
                }
                if (isUrgent) {
                    views.setTextColor(R.id.capsule_tag, 0xFFBA1A1A.toInt())
                    views.setTextColor(R.id.capsule_text, if (isDark) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt())
                } else {
                    // The badge keeps a light surface even when the widget itself is dark.
                    val accentColor = widgetAccentTextColor(data.widgetTheme, isDark = false).takeIf {
                        androidx.core.graphics.ColorUtils.calculateContrast(it, 0xFFD3E3FB.toInt()) >= 4.5
                    } ?: 0xFF244E72.toInt()
                    views.setTextColor(R.id.capsule_tag, accentColor)
                    views.setTextColor(R.id.capsule_text, if (isDark) 0xFFE6E1E5.toInt() else 0xFF20242A.toInt())
                }
            } else {
                views.setTextViewText(R.id.capsule_tag, context.getString(R.string.widget_capsule_tag_empty))
                views.setTextViewText(R.id.capsule_text, context.getString(R.string.widget_empty_other_day))
                if (compact) views.setTextViewText(R.id.capsule_detail, "")
                views.setContentDescription(R.id.capsule_root, context.getString(R.string.widget_empty_other_day))
                views.setTextColor(R.id.capsule_tag, 0xFF49454F.toInt())
                views.setTextColor(R.id.capsule_text, if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt())
            }
            return views
        }

    }
}
