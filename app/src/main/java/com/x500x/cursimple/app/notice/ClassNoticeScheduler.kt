package com.x500x.cursimple.app.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.widget.slotLabelText
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * 上课通知的定时投递。
 *
 * 一次只挂一个闹钟——下一节课的那一个；发完再算下一节重新挂。
 * 这里用 AlarmManager 只是为了「到点醒一下」，发出去的是普通通知，不响铃。
 */
object ClassNoticeScheduler {

    private const val ACTION_NOTICE = "com.x500x.cursimple.action.CLASS_NOTICE"
    internal const val ACTION_STARTED = "com.x500x.cursimple.action.CLASS_NOTICE_STARTED"
    internal const val ACTION_DISMISS = "com.x500x.cursimple.action.CLASS_NOTICE_DISMISS"
    private const val REQUEST_CODE = 0x0C1B
    private const val REQUEST_CODE_STARTED = 0x0C1C
    private const val REQUEST_CODE_DISMISS = 0x0C1D
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_LOCATION = "location"
    private const val EXTRA_TIME_RANGE = "timeRange"
    private const val EXTRA_SLOT_LABEL = "slotLabel"
    private const val EXTRA_MINUTES = "minutes"
    private const val EXTRA_START_AT = "startAt"
    private const val EXTRA_END_AT = "endAt"
    internal const val EXTRA_NOTIFICATION_ID = "notificationId"

    /** 上课提醒自己的一点状态，目前只有下面这条「最近弹过哪条」。 */
    private const val STATE_PREFS = "class_notice_schedule"

    /** 最近一次已经弹过的上课通知的身份，打开应用补发时用它挡重复弹。 */
    private const val KEY_LAST_POSTED = "last_posted"

    /** 最近弹过的那条是「哪个课几点开始」，应用活着重开一次的重复弹就靠这句挡下。 */
    fun recordLastPosted(context: Context, key: String) {
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_POSTED, key).apply()
    }

    /** 一条上课通知的身份：哪门课、几点开始。正常弹出和打开应用补发都用它，两边才对得上。 */
    fun postedKey(courseTitle: String, startAtMillis: Long): String = "$courseTitle|$startAtMillis"

    fun lastPostedKey(context: Context): String? =
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_POSTED, null)

    /** 按当前课表与偏好重挂闹钟；关掉或算不出下一节课时只取消。 */
    fun reschedule(
        context: Context,
        preferences: ClassNoticePreferences,
        upcoming: UpcomingClass?,
        now: LocalDateTime = BeijingTime.nowDateTime(),
    ) {
        val app = context.applicationContext
        cancel(app)
        if (!preferences.enabled) return
        val next = upcoming ?: return

        val fireAt = next.startAt.minusMinutes(preferences.advanceMinutes.toLong())
        // 已经过了提醒点（比如课马上就开始）就不再补发，免得一打开应用就弹一条过期通知
        if (!fireAt.isAfter(now)) return

        val minutesUntil = Duration.between(fireAt, next.startAt).toMinutes().toInt()
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_NOTICE
            setPackage(app.packageName)
            putExtra(EXTRA_TITLE, next.course.title)
            putExtra(EXTRA_LOCATION, next.displayLocation())
            putExtra(EXTRA_TIME_RANGE, next.timeRangeText())
            putExtra(EXTRA_SLOT_LABEL, app.slotLabelText(next.slots).orEmpty())
            putExtra(EXTRA_MINUTES, minutesUntil)
            putExtra(EXTRA_START_AT, next.startAt.atZone(BeijingTime.zone).toInstant().toEpochMilli())
            putExtra(EXTRA_END_AT, next.endAt.atZone(BeijingTime.zone).toInstant().toEpochMilli())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = fireAt.atZone(BeijingTime.zone).toInstant().toEpochMilli()
        // 上面比的「现在」可能是调试固定住的时间，闹钟却按真实时钟走。挂一个已经过去的时刻，
        // 闹钟会立刻响、响完再排出同一节课，一直循环往外弹。真错过的由打开应用时的补发兜底
        if (triggerAt <= System.currentTimeMillis()) return
        setAlarm(app, triggerAt, pendingIntent)
    }

    /**
     * 提醒发出去之后，在上课那一刻把通知原地改成「上课中」。
     * 沿用提醒那条广播带的内容，只换个动作；用户已经划掉的话到时候什么也不做。
     */
    fun scheduleStartedUpdate(context: Context, noticeIntent: Intent) {
        val app = context.applicationContext
        val startAt = noticeIntent.getLongExtra(EXTRA_START_AT, 0L)
        if (startAt <= System.currentTimeMillis()) return
        val intent = Intent(noticeIntent).apply {
            action = ACTION_STARTED
            setClass(app, ClassNoticeReceiver::class.java)
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_STARTED,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setAlarm(app, startAt, pendingIntent)
    }

    /** 通知上的「知道了」：收掉这条。上课通知和闹钟预告各用各的，互不收错。 */
    fun dismissIntent(context: Context, notificationId: Int): PendingIntent {
        val app = context.applicationContext
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_DISMISS xor notificationId,
            Intent(app, ClassNoticeReceiver::class.java).apply {
                action = ACTION_DISMISS
                setPackage(app.packageName)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun setAlarm(app: Context, triggerAt: Long, pendingIntent: PendingIntent): Boolean {
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching {
            // 拿不到精确闹钟权限时退回不精确的那一档：晚几分钟也比完全不提示强
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }.onFailure { error ->
            ReminderLogger.warn("class_notice.schedule.failure", emptyMap(), error)
        }.isSuccess
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_NOTICE
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        runCatching {
            app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    internal fun contentFrom(intent: Intent): ClassNoticeNotifier.Content? {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        return ClassNoticeNotifier.Content(
            courseTitle = title,
            location = intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
            timeRange = intent.getStringExtra(EXTRA_TIME_RANGE).orEmpty(),
            slotLabel = intent.getStringExtra(EXTRA_SLOT_LABEL).orEmpty(),
            minutesUntilStart = intent.getIntExtra(EXTRA_MINUTES, 0),
            startAtMillis = intent.getLongExtra(EXTRA_START_AT, 0L),
            endAtMillis = intent.getLongExtra(EXTRA_END_AT, 0L),
            inProgress = intent.action == ACTION_STARTED,
        )
    }
}

internal fun UpcomingClass.timeRangeText(): String {
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${two(startAt.hour)}:${two(startAt.minute)}-${two(endAt.hour)}:${two(endAt.minute)}"
}

/** 到点了：发通知，然后把下一节课的闹钟接上；上课那一刻再把通知改成「上课中」。 */
class ClassNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClassNoticeScheduler.ACTION_DISMISS) {
            val id = intent.getIntExtra(ClassNoticeScheduler.EXTRA_NOTIFICATION_ID, 0)
            if (id != 0) ClassNoticeNotifier.cancel(context, id) else ClassNoticeNotifier.cancel(context)
            return
        }
        val scheduled = ClassNoticeScheduler.contentFrom(intent) ?: return
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        // 分钟数按真实时钟现算（不足一分钟按一分钟）：排闹钟时写进去的是「提前多久」，闹钟晚到、系统时间改过时就不对了
        val content = if (scheduled.inProgress || scheduled.startAtMillis <= 0L) {
            scheduled
        } else {
            scheduled.copy(minutesUntilStart = ((scheduled.startAtMillis - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0))
        }
        val key = ClassNoticeScheduler.postedKey(content.courseTitle, content.startAtMillis)
        // 课已经开始了，或者这一节已经弹过：不再弹，只把后面的课接上
        val stale = !content.inProgress && (
            (content.startAtMillis in 1..now) || ClassNoticeScheduler.lastPostedKey(app) == key
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val preferences = ClassNoticeGateway.preferences(app)
                if (!preferences.enabled) return@launch
                if (stale) {
                    ReminderLogger.info("class_notice.deliver.skip_stale", mapOf("course" to content.courseTitle))
                    ClassNoticeGateway.reschedule(app)
                    return@launch
                }
                ClassNoticeNotifier.notify(app, content, preferences, ClassNoticeGateway.theme(app))
                // 记一条已弹身份：提醒点错过了、打开应用再补发时就不再弹一次同样的
                if (!content.inProgress) {
                    ClassNoticeScheduler.recordLastPosted(app, key)
                }
                if (content.inProgress) return@launch
                ClassNoticeScheduler.scheduleStartedUpdate(app, intent)
                // 发完立刻算下一节：闹钟一次只挂一个，不续上就断了
                ClassNoticeGateway.reschedule(app)
                // 悬浮窗皮肤要等窗口收起才放手，否则应用退出后进程一冻，悬浮窗就弹不出来
                ClassNoticeOverlay.awaitGone()
            } catch (error: Throwable) {
                ReminderLogger.warn("class_notice.deliver.failure", emptyMap(), error)
            } finally {
                pending.finish()
            }
        }
    }
}
