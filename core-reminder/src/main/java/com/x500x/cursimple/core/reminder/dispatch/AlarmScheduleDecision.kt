package com.x500x.cursimple.core.reminder.dispatch

enum class AlarmPrimaryChannel {
    AlarmClock,

    /** Windowed fallback after exact-alarm access loss. */
    Window,
}

/** Optional [backup] registers another channel for the same trigger time. */
data class AlarmScheduleDecision(
    val primary: AlarmPrimaryChannel,
    val backup: Boolean,
)

/**
 * Use alarm-clock plus exact backup with permission; otherwise fall back to windowed scheduling
 * rather than losing the request.
 */
fun alarmScheduleDecision(
    canScheduleExact: Boolean,
    alarmClockAvailable: Boolean = true,
): AlarmScheduleDecision = when {
    alarmClockAvailable && canScheduleExact -> AlarmScheduleDecision(AlarmPrimaryChannel.AlarmClock, backup = true)
    canScheduleExact -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = true)
    else -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = false)
}

/** Backup request codes must differ from primary codes. */
fun backupRequestCode(primaryRequestCode: Int): Int =
    (primaryRequestCode xor BACKUP_REQUEST_CODE_MASK) and Int.MAX_VALUE

private const val BACKUP_REQUEST_CODE_MASK = 0x5A5A5A5
