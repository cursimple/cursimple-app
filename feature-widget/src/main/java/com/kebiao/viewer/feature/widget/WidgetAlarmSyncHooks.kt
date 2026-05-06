package com.kebiao.viewer.feature.widget

import android.content.Context
internal fun reconcileSystemAlarmsFromWidget(context: Context) {
    WidgetLifecycleRefresher.onWidgetUpdated(context, reason = "widget_update")
}
