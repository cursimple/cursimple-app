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

/** Schedule one wake-up for the next class notice, then continue the chain. */
object ClassNoticeScheduler {

    private const val ACTION_NOTICE = "com.x500x.cursimple.action.CLASS_NOTICE"
    internal const val ACTION_ENDED = "com.x500x.cursimple.action.CLASS_NOTICE_ENDED"
    private const val REQUEST_CODE_ENDED = 0x0C22
    internal const val ACTION_STARTED = "com.x500x.cursimple.action.CLASS_NOTICE_STARTED"
    internal const val ACTION_DISMISS = "com.x500x.cursimple.action.CLASS_NOTICE_DISMISS"
    internal const val ACTION_SNOOZE = "com.x500x.cursimple.action.CLASS_NOTICE_SNOOZE"
    internal const val ACTION_SKIP = "com.x500x.cursimple.action.CLASS_NOTICE_SKIP"
    internal const val ACTION_EVENING_BRIEFING = "com.x500x.cursimple.action.CLASS_NOTICE_EVENING_BRIEFING"
    internal const val ACTION_TRANSIT = "com.x500x.cursimple.action.CLASS_NOTICE_TRANSIT"
    internal const val ACTION_EXAM = "com.x500x.cursimple.action.CLASS_NOTICE_EXAM"

    const val NOTIFICATION_ID_EVENING_BRIEFING = 0x0E01
    const val NOTIFICATION_ID_TRANSIT = 0x0E02
    const val NOTIFICATION_ID_EXAM = 0x0E03

    private const val REQUEST_CODE = 0x0C1B
    private const val REQUEST_CODE_STARTED = 0x0C1C
    private const val REQUEST_CODE_DISMISS = 0x0C1D
    private const val REQUEST_CODE_SNOOZE = 0x0C1E
    private const val REQUEST_CODE_SKIP = 0x0C1F
    private const val REQUEST_CODE_EVENING_BRIEFING = 0x0E11
    private const val REQUEST_CODE_TRANSIT = 0x0E12
    private const val REQUEST_CODE_EXAM = 0x0E13

    private const val EXTRA_TITLE = "title"
    private const val EXTRA_LOCATION = "location"
    private const val EXTRA_TIME_RANGE = "timeRange"
    private const val EXTRA_SLOT_LABEL = "slotLabel"
    private const val EXTRA_MINUTES = "minutes"
    private const val EXTRA_START_AT = "startAt"
    private const val EXTRA_END_AT = "endAt"
    internal const val EXTRA_TRANSIT_TITLE = "transitTitle"
    internal const val EXTRA_TRANSIT_LOCATION = "transitLocation"
    internal const val EXTRA_EXAM_TITLE = "examTitle"
    internal const val EXTRA_EXAM_LOCATION = "examLocation"
    internal const val EXTRA_EXAM_SEAT = "examSeat"
    internal const val EXTRA_EXAM_MINUTES = "examMinutes"
    internal const val EXTRA_NOTIFICATION_ID = "notificationId"
    internal const val EXTRA_SNOOZE_FIRE = "snoozeFire"
    private const val SNOOZE_MINUTES = 10L

    /** Persistent class-notice delivery state. */
    private const val STATE_PREFS = "class_notice_schedule"

    /** Last delivered notice identity for foreground catch-up deduplication. */
    private const val KEY_LAST_POSTED = "last_posted"

    /** Stable course and start-time identity for duplicate suppression. */
    fun recordLastPosted(context: Context, key: String) {
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_POSTED, key).apply()
    }

    fun postedKey(courseTitle: String, startAtMillis: Long): String = "$courseTitle|$startAtMillis"

    fun lastPostedKey(context: Context): String? =
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_POSTED, null)

    /** Cancel when disabled or empty; otherwise reschedule from current data. */
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
        if (!fireAt.isAfter(now)) return

        val minutesUntil = Duration.between(fireAt, next.startAt).toMinutes().toInt()
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_NOTICE
            setPackage(app.packageName)
            putExtra(EXTRA_TITLE, next.displayTitle())
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
        // Reject triggers in the real past even with a frozen debug clock to avoid immediate alarm loops.
        if (triggerAt <= System.currentTimeMillis()) return
        setAlarm(app, triggerAt, pendingIntent)
    }

    /** Keep transition alarms independent of the next class's reminder registration. */
    fun scheduleTransitions(context: Context, content: ClassNoticeNotifier.Content) {
        val app = context.applicationContext
        listOf(ACTION_STARTED to (REQUEST_CODE_STARTED to content.startAtMillis),
            ACTION_ENDED to (REQUEST_CODE_ENDED to content.endAtMillis)).forEach { (action, slot) ->
            if (slot.second > System.currentTimeMillis()) {
                val intent = Intent(app, ClassNoticeReceiver::class.java).setAction(action).setPackage(app.packageName)
                    .putExtra(EXTRA_TITLE, content.courseTitle).putExtra(EXTRA_LOCATION, content.location)
                    .putExtra(EXTRA_TIME_RANGE, content.timeRange).putExtra(EXTRA_SLOT_LABEL, content.slotLabel)
                    .putExtra(EXTRA_START_AT, content.startAtMillis).putExtra(EXTRA_END_AT, content.endAtMillis)
                    .putExtra(EXTRA_MINUTES, 0)
                val pending = PendingIntent.getBroadcast(app, slot.first, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setAlarm(app, slot.second, pending)
            }
        }
    }

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

    fun snoozeIntent(context: Context, content: ClassNoticeNotifier.Content): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE_SNOOZE,
        Intent(context.applicationContext, ClassNoticeReceiver::class.java).apply {
            action = ACTION_SNOOZE
            setPackage(context.packageName)
            putContentExtras(content)
            putExtra(EXTRA_SNOOZE_FIRE, false)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun skipIntent(context: Context, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE_SKIP xor notificationId,
        Intent(context.applicationContext, ClassNoticeReceiver::class.java).apply {
            action = ACTION_SKIP
            setPackage(context.packageName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun scheduleSnoozeForReceiver(context: Context, content: ClassNoticeNotifier.Content) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_SNOOZE
            setPackage(app.packageName)
            putContentExtras(content)
            putExtra(EXTRA_SNOOZE_FIRE, true)
        }
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_SNOOZE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setAlarm(app, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, pending)
    }

    private fun Intent.putContentExtras(content: ClassNoticeNotifier.Content) {
        putExtra(EXTRA_TITLE, content.courseTitle)
        putExtra(EXTRA_LOCATION, content.location)
        putExtra(EXTRA_TIME_RANGE, content.timeRange)
        putExtra(EXTRA_SLOT_LABEL, content.slotLabel)
        putExtra(EXTRA_MINUTES, content.minutesUntilStart)
        putExtra(EXTRA_START_AT, content.startAtMillis)
        putExtra(EXTRA_END_AT, content.endAtMillis)
    }

    private fun setAlarm(app: Context, triggerAt: Long, pendingIntent: PendingIntent): Boolean {
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching {
            // Fall back to inexact scheduling without exact-alarm permission.
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

    fun scheduleEveningBriefing(context: Context, triggerAtMillis: Long) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_EVENING_BRIEFING
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_EVENING_BRIEFING,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (triggerAtMillis <= System.currentTimeMillis()) return
        setAlarm(app, triggerAtMillis, pendingIntent)
    }

    fun cancelEveningBriefing(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_EVENING_BRIEFING
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_EVENING_BRIEFING,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        runCatching {
            app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleTransitReminder(context: Context, triggerAtMillis: Long, nextTitle: String, nextLocation: String) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_TRANSIT
            setPackage(app.packageName)
            putExtra(EXTRA_TRANSIT_TITLE, nextTitle)
            putExtra(EXTRA_TRANSIT_LOCATION, nextLocation)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_TRANSIT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (triggerAtMillis <= System.currentTimeMillis()) return
        setAlarm(app, triggerAtMillis, pendingIntent)
    }

    fun cancelTransitReminder(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_TRANSIT
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_TRANSIT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        runCatching {
            app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleExamAlert(
        context: Context,
        triggerAtMillis: Long,
        examTitle: String,
        location: String,
        seat: String,
        minutesUntil: Int,
    ) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_EXAM
            setPackage(app.packageName)
            putExtra(EXTRA_EXAM_TITLE, examTitle)
            putExtra(EXTRA_EXAM_LOCATION, location)
            putExtra(EXTRA_EXAM_SEAT, seat)
            putExtra(EXTRA_EXAM_MINUTES, minutesUntil)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_EXAM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (triggerAtMillis <= System.currentTimeMillis()) return
        setAlarm(app, triggerAtMillis, pendingIntent)
    }

    fun cancelExamAlert(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, ClassNoticeReceiver::class.java).apply {
            action = ACTION_EXAM
            setPackage(app.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE_EXAM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        runCatching {
            app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancelAll(context: Context) {
        cancel(context)
        cancelEveningBriefing(context)
        cancelTransitReminder(context)
        cancelExamAlert(context)
        val app = context.applicationContext
        listOf(ACTION_STARTED to REQUEST_CODE_STARTED, ACTION_ENDED to REQUEST_CODE_ENDED, ACTION_SNOOZE to REQUEST_CODE_SNOOZE).forEach { (action, request) ->
            val pending = PendingIntent.getBroadcast(app, request, Intent(app, ClassNoticeReceiver::class.java).setAction(action).setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pending != null) { app.getSystemService(AlarmManager::class.java)?.cancel(pending); pending.cancel() }
        }
        ClassNoticeNotifier.cancel(app)
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

class ClassNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClassNoticeScheduler.ACTION_DISMISS) {
            val id = intent.getIntExtra(ClassNoticeScheduler.EXTRA_NOTIFICATION_ID, 0)
            if (id != 0) ClassNoticeNotifier.cancel(context, id) else ClassNoticeNotifier.cancel(context)
            return
        }
        if (intent.action == ClassNoticeScheduler.ACTION_EVENING_BRIEFING) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    ClassNoticeGateway.deliverEveningBriefing(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == ClassNoticeScheduler.ACTION_TRANSIT) {
            val title = intent.getStringExtra(ClassNoticeScheduler.EXTRA_TRANSIT_TITLE).orEmpty()
            val location = intent.getStringExtra(ClassNoticeScheduler.EXTRA_TRANSIT_LOCATION).orEmpty()
            if (title.isNotBlank()) {
                ClassNoticeNotifier.notifyTransitReminder(context, title, location)
            }
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { ClassNoticeGateway.rescheduleTransit(context.applicationContext) }
            }
            return
        }
        if (intent.action == ClassNoticeScheduler.ACTION_EXAM) {
            val title = intent.getStringExtra(ClassNoticeScheduler.EXTRA_EXAM_TITLE).orEmpty()
            val location = intent.getStringExtra(ClassNoticeScheduler.EXTRA_EXAM_LOCATION).orEmpty()
            val seat = intent.getStringExtra(ClassNoticeScheduler.EXTRA_EXAM_SEAT).orEmpty()
            val minutes = intent.getIntExtra(ClassNoticeScheduler.EXTRA_EXAM_MINUTES, 45)
            if (title.isNotBlank()) {
                ClassNoticeNotifier.notifyExamAlert(context, title, location, seat, minutes)
            }
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { ClassNoticeGateway.rescheduleExam(context.applicationContext) }
            }
            return
        }
        if (intent.action == ClassNoticeScheduler.ACTION_SKIP) {
            val id = intent.getIntExtra(ClassNoticeScheduler.EXTRA_NOTIFICATION_ID, 0)
            if (id != 0) ClassNoticeNotifier.cancel(context, id) else ClassNoticeNotifier.cancel(context)
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { ClassNoticeGateway.reschedule(context.applicationContext) }
            }
            return
        }
        if (intent.action == ClassNoticeScheduler.ACTION_SNOOZE &&
            !intent.getBooleanExtra(ClassNoticeScheduler.EXTRA_SNOOZE_FIRE, false)
        ) {
            val content = ClassNoticeScheduler.contentFrom(intent) ?: return
            ClassNoticeNotifier.cancel(context, content.notificationId)
            ClassNoticeScheduler.scheduleSnoozeForReceiver(context, content)
            return
        }
        val scheduled = ClassNoticeScheduler.contentFrom(intent) ?: return
        if (intent.action == ClassNoticeScheduler.ACTION_ENDED) {
            ClassNoticeNotifier.cancelIfCurrent(context, scheduled)
            return
        }
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        // Recalculate remaining minutes from the actual delivery clock, rounding up.
        val content = if (scheduled.inProgress || scheduled.startAtMillis <= 0L) {
            scheduled
        } else {
            scheduled.copy(minutesUntilStart = ((scheduled.startAtMillis - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0))
        }
        val key = ClassNoticeScheduler.postedKey(content.courseTitle, content.startAtMillis)
        // Skip started or already-delivered classes and schedule the next one.
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
                ClassNoticeNotifier.notify(app, content, preferences, ClassNoticeGateway.theme(app), forward = true)
                if (!content.inProgress) {
                    ClassNoticeScheduler.recordLastPosted(app, key)
                }
                if (content.inProgress) return@launch
                ClassNoticeScheduler.scheduleTransitions(app, content)
                // Continue the one-alarm chain immediately after delivery.
                ClassNoticeGateway.reschedule(app)
                // Release the broadcast after overlay attachment; window timing is independent.
                ClassNoticeOverlay.awaitShown()
            } catch (error: Throwable) {
                ReminderLogger.warn("class_notice.deliver.failure", emptyMap(), error)
            } finally {
                pending.finish()
            }
        }
    }
}
