package com.x500x.cursimple.feature.widget

import android.content.Context
internal fun reconcileSystemAlarmsFromWidget(context: Context) {
    WidgetLifecycleRefresher.onWidgetUpdated(context, reason = "widget_update")
}

/**
 * App-registered five-minute guard hooks must return promptly and move heavy work off the
 * receiver.
 */
object WidgetGuardHooks {
    @Volatile
    var onGuardTick: ((Context) -> Unit)? = null
}
