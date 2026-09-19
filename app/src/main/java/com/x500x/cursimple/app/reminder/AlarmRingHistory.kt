package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.core.content.edit

/** 一次闹钟到达的结果。 */
internal data class AlarmRingEvent(
    val atMillis: Long,
    val outcome: AlarmRingOutcome,
    val label: String,
)

internal enum class AlarmRingOutcome {
    /** 正常响了。 */
    Rang,

    /** 到得太晚，只补了一条错过通知。 */
    Missed,
}

/**
 * 最近几次响铃的流水账。
 *
 * 用户报「闹钟没响」时，第一件要分清的事是「系统压根没把闹钟送到」还是「送到了但没出声」，
 * 这两种的排查方向完全相反。日志在用户手机上取不到，所以把到达记录落到本地，
 * 自检页直接读出来。只留最近几条，够判断就行。
 */
internal object AlarmRingHistory {
    private const val PREFS_NAME = "alarm_ring_history"
    private const val KEY_EVENTS = "events"
    private const val MAX_ENTRIES = 5

    private const val RECORD_SEPARATOR = '\n'
    private const val FIELD_SEPARATOR = '\u001F'

    /** 记一次到达，最新的排在最前。 */
    fun record(
        context: Context,
        outcome: AlarmRingOutcome,
        label: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val store = prefs(context)
        val event = AlarmRingEvent(atMillis = atMillis, outcome = outcome, label = label)
        synchronized(this) {
            val next = (listOf(event) + decode(store.getString(KEY_EVENTS, null))).take(MAX_ENTRIES)
            store.edit { putString(KEY_EVENTS, encode(next)) }
        }
    }

    /** 最近的若干次到达，最新的在前。 */
    fun recent(context: Context): List<AlarmRingEvent> =
        decode(prefs(context).getString(KEY_EVENTS, null))

    /** 分隔符出现在课程名里会把记录切坏，写入前先换成空格。 */
    internal fun encode(events: List<AlarmRingEvent>): String =
        events.joinToString(RECORD_SEPARATOR.toString()) { event ->
            listOf(
                event.atMillis.toString(),
                event.outcome.name,
                event.label.map { if (it == RECORD_SEPARATOR || it == FIELD_SEPARATOR) ' ' else it }
                    .joinToString(""),
            ).joinToString(FIELD_SEPARATOR.toString())
        }

    /** 认不出来的行整条丢掉：这只是排查用的记录，缺一条远好过读崩。 */
    internal fun decode(raw: String?): List<AlarmRingEvent> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size < 3) return@mapNotNull null
            val atMillis = fields[0].toLongOrNull() ?: return@mapNotNull null
            val outcome = AlarmRingOutcome.entries.firstOrNull { it.name == fields[1] }
                ?: return@mapNotNull null
            AlarmRingEvent(atMillis = atMillis, outcome = outcome, label = fields[2])
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
