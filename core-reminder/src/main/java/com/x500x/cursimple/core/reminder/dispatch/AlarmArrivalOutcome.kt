package com.x500x.cursimple.core.reminder.dispatch

sealed interface AlarmArrivalOutcome {
    data object Ring : AlarmArrivalOutcome

    data class RingLate(val delayMillis: Long) : AlarmArrivalOutcome

    /** Outside the tolerated delay, report a missed alarm only. */
    data class Missed(val delayMillis: Long) : AlarmArrivalOutcome

    data object Duplicate : AlarmArrivalOutcome

    data object Outdated : AlarmArrivalOutcome
}

/** Maximum accepted late-ring delay. */
const val ALARM_MISSED_TTL_MILLIS: Long = 10 * 60 * 1000L

/**
 * Zero trigger time preserves normal delivery; reject only generations older than current
 * scheduling.
 */
fun alarmArrivalOutcome(
    triggerAtMillis: Long,
    nowMillis: Long,
    alreadyHandled: Boolean,
    intentGeneration: Long = 0L,
    currentGeneration: Long = 0L,
    missedTtlMillis: Long = ALARM_MISSED_TTL_MILLIS,
): AlarmArrivalOutcome {
    if (intentGeneration < currentGeneration) return AlarmArrivalOutcome.Outdated
    if (alreadyHandled) return AlarmArrivalOutcome.Duplicate
    if (triggerAtMillis <= 0L) return AlarmArrivalOutcome.Ring
    val delay = nowMillis - triggerAtMillis
    return when {
        delay <= LATE_TOLERANCE_MILLIS -> AlarmArrivalOutcome.Ring
        delay < missedTtlMillis -> AlarmArrivalOutcome.RingLate(delay)
        else -> AlarmArrivalOutcome.Missed(delay)
    }
}

private const val LATE_TOLERANCE_MILLIS = 30 * 1000L
