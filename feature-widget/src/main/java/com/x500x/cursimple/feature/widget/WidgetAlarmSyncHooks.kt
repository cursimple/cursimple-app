package com.x500x.cursimple.feature.widget

import android.content.Context
internal fun reconcileSystemAlarmsFromWidget(context: Context) {
    WidgetLifecycleRefresher.onWidgetUpdated(context, reason = "widget_update")
}
