package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

open class ScheduleGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetCatalog.notifyInstalledChanged(context)
    }
}

/**
 * Vendor-aware twin of [ScheduleGlanceWidgetReceiver]. Registered separately so MIUI / vivo /
 * HONOR launchers can pick it up via their custom widget actions and meta-data, while still
 * using the same Glance widget implementation in the main process.
 */
class ScheduleGlanceWidgetReceiverMIUI : ScheduleGlanceWidgetReceiver()
