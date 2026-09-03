package com.x500x.cursimple.app.util

import android.content.Context
import android.content.SharedPreferences

/** 上一次写进系统日历的记录。 */
data class SystemCalendarExportRecord(
    val calendarId: Long,
    val eventIds: List<Long>,
    val exportedAt: Long,
)

/**
 * 记住写进系统日历的事件 id，撤销时照着删。
 *
 * 只在本机有意义，换机或清数据后这份记录消失，此时不再提供撤销，
 * 也不去按标题之类的特征猜测哪些事件该删。
 */
object SystemCalendarExportStore {

    private const val FILE = "system_calendar_export"
    private const val KEY_CALENDAR_ID = "calendar_id"
    private const val KEY_EVENT_IDS = "event_ids"
    private const val KEY_EXPORTED_AT = "exported_at"

    fun record(context: Context, calendarId: Long, eventIds: List<Long>) {
        prefs(context).edit()
            .putLong(KEY_CALENDAR_ID, calendarId)
            .putString(KEY_EVENT_IDS, eventIds.joinToString(","))
            .putLong(KEY_EXPORTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun read(context: Context): SystemCalendarExportRecord? {
        val store = prefs(context)
        if (!store.contains(KEY_CALENDAR_ID)) return null
        val ids = store.getString(KEY_EVENT_IDS, "").orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
        return SystemCalendarExportRecord(
            calendarId = store.getLong(KEY_CALENDAR_ID, 0L),
            eventIds = ids,
            exportedAt = store.getLong(KEY_EXPORTED_AT, 0L),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
