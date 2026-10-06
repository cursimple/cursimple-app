package com.x500x.cursimple.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetCatalog.ACTION_WIDGET_PINNED) return
        val key = intent.data?.lastPathSegment
        val id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (key != null && id >= 0) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                try {
                    runCatching {
                        com.x500x.cursimple.core.data.widget.ComponentWidgetBindings.set(context, id, key)
                        ComponentWidgetReceiver.updateWidgets(context, intArrayOf(id))
                    }
                } finally { pending.finish() }
            }
        }
        WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "widget_pinned")
    }
}
