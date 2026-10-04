package com.x500x.cursimple.feature.widget

import android.content.Context
internal fun reconcileSystemAlarmsFromWidget(context: Context) {
    WidgetLifecycleRefresher.onWidgetUpdated(context, reason = "widget_update")
}

/**
 * 静默守护闹钟每次唤醒时顺带要做的事，由 App 注册。
 *
 * 守护链是课表小组件的保活手段（每 5 分钟一次，比 WorkManager 准时得多）；
 * 组件内容的定时同步挂在这里，就和课表用同一套保活。实现方要自己快速返回、把耗时活放到后台。
 */
object WidgetGuardHooks {
    @Volatile
    var onGuardTick: ((Context) -> Unit)? = null
}
