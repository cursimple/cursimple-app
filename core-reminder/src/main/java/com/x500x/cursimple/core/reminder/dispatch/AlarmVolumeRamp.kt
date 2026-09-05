package com.x500x.cursimple.core.reminder.dispatch

/** 渐强爬升到满音量所需的时长。 */
const val ALARM_VOLUME_RAMP_MILLIS: Long = 20 * 1000L

/** 起始音量，太低会让第一声完全听不见。 */
const val ALARM_VOLUME_RAMP_START: Float = 0.25f

/**
 * 响铃开始 [elapsedMillis] 后应有的音量，取值 [ALARM_VOLUME_RAMP_START]..1。
 * 从起点线性升到满音量，避免熟睡时被突然的满音量惊醒。
 * [rampMillis] 不为正时直接给满音量。
 */
fun alarmRampVolume(
    elapsedMillis: Long,
    rampMillis: Long = ALARM_VOLUME_RAMP_MILLIS,
    startVolume: Float = ALARM_VOLUME_RAMP_START,
): Float {
    if (rampMillis <= 0L) return 1f
    val progress = (elapsedMillis.toFloat() / rampMillis).coerceIn(0f, 1f)
    return (startVolume + (1f - startVolume) * progress).coerceIn(0f, 1f)
}
