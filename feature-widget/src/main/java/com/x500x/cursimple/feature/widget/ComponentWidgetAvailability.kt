package com.x500x.cursimple.feature.widget

import android.content.Context

/**
 * Enable component task providers only while an extension is enabled; app-owned widgets remain
 * independent.
 */
object ComponentWidgetAvailability {
    private const val PREFS = "component_widget_availability"
    private const val KEY_AVAILABLE = "available"

    fun isAvailable(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AVAILABLE, false)

    fun update(context: Context, available: Boolean): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val changed = prefs.getBoolean(KEY_AVAILABLE, false) != available || !prefs.contains(KEY_AVAILABLE)
        if (changed) prefs.edit().putBoolean(KEY_AVAILABLE, available).apply()
        applyWidgetProviderVisibility(context)
        if (changed) WidgetCatalog.notifyInstalledChanged(context)
        return changed
    }
}
