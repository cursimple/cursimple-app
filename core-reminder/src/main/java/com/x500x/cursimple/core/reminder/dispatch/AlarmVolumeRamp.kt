package com.x500x.cursimple.core.reminder.dispatch

const val ALARM_VOLUME_RAMP_MILLIS: Long = 20 * 1000L

const val ALARM_VOLUME_RAMP_START: Float = 0.25f

/**
 * Ramp from [ALARM_VOLUME_RAMP_START] to full volume; nonpositive [rampMillis] gives full
 * volume immediately.
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
