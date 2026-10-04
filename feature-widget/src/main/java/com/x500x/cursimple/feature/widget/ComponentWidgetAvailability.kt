package com.x500x.cursimple.feature.widget

import android.content.Context

/**
 * 组件小组件能不能用：有启用中的扩展组件时才上架。
 *
 * 由 App 在组件装上、启用、停用、移除时告知；这里只记一个开关，
 * 再交给 [WidgetProviderVisibility] 去启用或禁用对应的 receiver。
 */
object ComponentWidgetAvailability {
    private const val PREFS = "component_widget_availability"
    private const val KEY_AVAILABLE = "available"

    fun isAvailable(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AVAILABLE, false)

    /** 状态变了才去动 receiver；返回是否有变化 */
    fun update(context: Context, available: Boolean): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val changed = prefs.getBoolean(KEY_AVAILABLE, false) != available || !prefs.contains(KEY_AVAILABLE)
        if (changed) prefs.edit().putBoolean(KEY_AVAILABLE, available).apply()
        applyWidgetProviderVisibility(context)
        if (changed) WidgetCatalog.notifyInstalledChanged(context)
        return changed
    }
}
