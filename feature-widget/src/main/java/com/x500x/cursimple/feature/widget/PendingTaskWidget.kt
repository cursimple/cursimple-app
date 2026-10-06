package com.x500x.cursimple.feature.widget

import android.content.Context

/** Legacy provider names preserve desktop placements; rendering is entirely component-owned. */
open class PendingTaskWidgetReceiver : ComponentWidgetReceiver() {
    companion object {
        internal const val CATALOG_ID = "tasks"
        suspend fun updateWidgets(context: Context, ids: IntArray? = null) = ComponentWidgetReceiver.updateWidgets(context, ids)
    }
}
class PendingTaskWidgetReceiverMIUI : PendingTaskWidgetReceiver()
