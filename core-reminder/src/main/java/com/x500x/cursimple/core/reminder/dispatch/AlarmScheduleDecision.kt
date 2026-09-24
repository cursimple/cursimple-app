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
 * setAlarmClock 是最硬的一档：不受休眠限流、不受待机分组配额、用户把应用设成「限制后台」也照响。
 * 但它和精确闹钟一样要权限（AlarmManagerService 里没有权限会直接抛 SecurityException），
 * 以前以为它不用权限，Android 12 上权限被收回时整条排程失败，连降级的窗口闹钟都没挂上。
 * 所以有权限时主通道用它、再挂一条精确备通道；没权限时退到窗口闹钟，晚几分钟也比不响强。
 */
fun alarmScheduleDecision(
    canScheduleExact: Boolean,
    alarmClockAvailable: Boolean = true,
): AlarmScheduleDecision = when {
    alarmClockAvailable && canScheduleExact -> AlarmScheduleDecision(AlarmPrimaryChannel.AlarmClock, backup = true)
    canScheduleExact -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = true)
    else -> AlarmScheduleDecision(AlarmPrimaryChannel.Window, backup = false)
}

/** 备通道的 requestCode 必须与主通道不同，否则两条 PendingIntent 互相覆盖。 */
fun backupRequestCode(primaryRequestCode: Int): Int =
    (primaryRequestCode xor BACKUP_REQUEST_CODE_MASK) and Int.MAX_VALUE

private const val BACKUP_REQUEST_CODE_MASK = 0x5A5A5A5
