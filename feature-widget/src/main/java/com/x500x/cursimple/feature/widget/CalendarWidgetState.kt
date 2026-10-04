package com.x500x.cursimple.feature.widget

import android.content.Context

/**
 * 每个课程日历小组件自己的视图与翻页位置。
 *
 * 翻页偏移锚在按下的那一天：跨过零点后自动回到本周 / 本月，和「每日课程」的翻页一致。
 * 视图（周 / 月）一直保留，直到用户再切回来。
 */
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

    /** 切换视图时回到本周 / 本月：「第 3 周往后」换成月视图后没有对应的意思 */
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

    /** 往前往后最多翻两年，防止连点出离谱的日期 */
    private const val MAX_OFFSET = 104
}
