package com.kebiao.viewer.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetCatalog.ACTION_WIDGET_PINNED) return
        WidgetLifecycleRefresher.onWidgetSetChanged(context, reason = "widget_pinned")
    }
}
