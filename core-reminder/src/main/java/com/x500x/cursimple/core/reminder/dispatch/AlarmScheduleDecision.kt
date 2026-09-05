package com.x500x.cursimple.core.reminder.dispatch

/** 主通道的排程方式。 */
enum class AlarmPrimaryChannel {
    /** 系统承认的用户闹钟，精确且不受休眠影响。 */
    AlarmClock,

    /** 精确闹钟权限被收回后的降级，只能保证落在窗口内。 */
    Window,
}

/**
 * 一次排程要用的通道组合。
 * [backup] 为 true 时额外挂一条允许在休眠中触发的精确闹钟，两条同一时刻到期，谁先到算谁的。
 */
data class AlarmScheduleDecision(
    val primary: AlarmPrimaryChannel,
    val backup: Boolean,
)

/**
 * 决定这次排程走哪些通道。
 *
 * setAlarmClock 由系统按用户闹钟对待，不需要精确闹钟权限，任何时候都作为主通道。
 * 备通道用的 setExactAndAllowWhileIdle 受权限约束，拿不到权限时只挂主通道，
 * 而不是整条排程放弃。
 */
fun alarmScheduleDecision(
    canScheduleExact: Boolean,
    alarmClockAvailable: Boolean = true,
): AlarmScheduleDecision = when {
    alarmClockAvailable -> AlarmScheduleDecision(AlarmPrimaryChannel.AlarmClock, backup = canScheduleExact)
    canScheduleExact -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = true)
    else -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = false)
}

/** 备通道的 requestCode 必须与主通道不同，否则两条 PendingIntent 互相覆盖。 */
fun backupRequestCode(primaryRequestCode: Int): Int =
    (primaryRequestCode xor BACKUP_REQUEST_CODE_MASK) and Int.MAX_VALUE

private const val BACKUP_REQUEST_CODE_MASK = 0x5A5A5A5
