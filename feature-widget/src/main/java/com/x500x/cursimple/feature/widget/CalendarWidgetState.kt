package com.x500x.cursimple.feature.widget

import android.content.Context

/** Per-widget view persists; page offsets expire after the anchored day changes. */
internal object CalendarWidgetState {
    private const val PREFS = "calendar_widget_state"

    fun mode(context: Context, appWidgetId: Int): CalendarWidgetMode =
        if (prefs(context).getString(modeKey(appWidgetId), null) == CalendarWidgetMode.Month.name) {
            CalendarWidgetMode.Month
        } else {
            CalendarWidgetMode.Week
        }

    fun offset(context: Context, appWidgetId: Int, todayIso: String): Int {
        val prefs = prefs(context)
        if (prefs.getString(anchorKey(appWidgetId), null) != todayIso) return 0
        return prefs.getInt(offsetKey(appWidgetId), 0)
    }

    fun shift(context: Context, appWidgetId: Int, delta: Int, todayIso: String) {
        val next = offset(context, appWidgetId, todayIso) + delta
        prefs(context).edit()
            .putInt(offsetKey(appWidgetId), next.coerceIn(-MAX_OFFSET, MAX_OFFSET))
            .putString(anchorKey(appWidgetId), todayIso)
            .apply()
    }

    fun reset(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(offsetKey(appWidgetId)).remove(anchorKey(appWidgetId)).apply()
    }

    fun toggleMode(context: Context, appWidgetId: Int) {
        val next = if (mode(context, appWidgetId) == CalendarWidgetMode.Week) CalendarWidgetMode.Month else CalendarWidgetMode.Week
        prefs(context).edit()
            .putString(modeKey(appWidgetId), next.name)
            .remove(offsetKey(appWidgetId))
            .remove(anchorKey(appWidgetId))
            .apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(modeKey(appWidgetId))
            .remove(offsetKey(appWidgetId))
            .remove(anchorKey(appWidgetId))
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun modeKey(id: Int) = "mode_$id"
    private fun offsetKey(id: Int) = "offset_$id"
    private fun anchorKey(id: Int) = "anchor_$id"

    /** Bound page navigation to two years in either direction. */
    private const val MAX_OFFSET = 104
}
