package com.x500x.cursimple.core.reminder.dispatch

/** 一条闹钟到达时该怎么处理。 */
sealed interface AlarmArrivalOutcome {
    /** 正常响铃。 */
    data object Ring : AlarmArrivalOutcome

    /** 过了应响时刻但仍在容忍窗内，补响。 */
    data class RingLate(val delayMillis: Long) : AlarmArrivalOutcome

    /** 超出容忍窗，只告知用户错过了。 */
    data class Missed(val delayMillis: Long) : AlarmArrivalOutcome

    /** 另一条通道已经处理过同一条闹钟，直接丢弃。 */
    data object Duplicate : AlarmArrivalOutcome

    /** 排程已被更新，这是旧世代的在途广播。 */
    data object Outdated : AlarmArrivalOutcome
}

/** 过了应响时刻仍补响的容忍窗。超过这个跨度补响只会让人困惑。 */
const val ALARM_MISSED_TTL_MILLIS: Long = 10 * 60 * 1000L

/**
 * 判定一条到达的闹钟该怎么处理。
 *
 * [triggerAtMillis] 为 0 表示排程时没带触发时刻，无从判断迟到，按正常响铃处理。
 * [intentGeneration] 小于 [currentGeneration] 说明排程在这条广播在途期间被改过，
 * 用小于而非不等于，是因为同一世代下贪睡与补响都是合法的重复到达。
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

/** 这个跨度内的迟到属于正常调度抖动，不必单独标记。 */
private const val LATE_TOLERANCE_MILLIS = 30 * 1000L
