package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.core.content.edit

internal data class AlarmRingEvent(
    val atMillis: Long,
    val outcome: AlarmRingOutcome,
    val label: String,
)

internal enum class AlarmRingOutcome {
    Rang,

    /** Late arrival recorded as a missed notice only. */
    Missed,

    Skipped,
}

/**
 * Persist a bounded arrival history to distinguish missing delivery from delivered-but-silent
 * alarms.
 */
internal object AlarmRingHistory {
    private const val PREFS_NAME = "alarm_ring_history"
    private const val KEY_EVENTS = "events"
    private const val MAX_ENTRIES = 5

    private const val RECORD_SEPARATOR = '\n'
    private const val FIELD_SEPARATOR = '\u001F'

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

    fun recent(context: Context): List<AlarmRingEvent> =
        decode(prefs(context).getString(KEY_EVENTS, null))

    internal fun encode(events: List<AlarmRingEvent>): String =
        events.joinToString(RECORD_SEPARATOR.toString()) { event ->
            listOf(
                event.atMillis.toString(),
                event.outcome.name,
                event.label.map { if (it == RECORD_SEPARATOR || it == FIELD_SEPARATOR) ' ' else it }
                    .joinToString(""),
            ).joinToString(FIELD_SEPARATOR.toString())
        }

    /** Discard malformed diagnostic rows instead of failing the whole history. */
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
