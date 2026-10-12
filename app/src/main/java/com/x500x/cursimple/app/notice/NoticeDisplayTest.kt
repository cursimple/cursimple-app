package com.x500x.cursimple.app.notice

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
internal enum class NoticeDisplayTarget(val notificationId: Int, val title: Int, val description: Int) {
    Aod(0x0D41, R.string.notice_test_aod, R.string.notice_test_aod_description),
    Island(0x0D42, R.string.notice_test_island, R.string.notice_test_island_description),
    Center(0x0D43, R.string.notice_test_center, R.string.notice_test_center_description);

    fun preferences(source: ClassNoticePreferences) = source.copy(
        skin = ClassNoticeSkin.System,
        headsUpEnabled = this == Center,
        focusNotificationEnabled = this != Center,
        // The fixture contains no personal information.
        lockScreenEnabled = if (this == Aod) true else source.lockScreenEnabled,
    )
}

@Serializable
internal enum class NoticeDisplayPhase { Queued, Sent, Blocked, Cancelled, Expired, Failed, Disabled }

@Serializable
internal enum class NoticeDisplayStep { Upcoming, InProgress, Dismissed, NotApplicable }

@Serializable
internal enum class NoticeDisplayFeedback { Unchecked, CourseContent, IconOnly, CenterOnly, NotSeen, BannerAppeared }

@Serializable
internal data class NoticeSystemReceipt(
    val checkedAt: Long,
    val active: Boolean,
    val promoted: Boolean,
    val vivoExtrasPresent: Boolean,
    val vivoTagged: Boolean,
    val template: String,
    val identityPresent: Boolean,
)

@Serializable
internal data class NoticeDisplayRun(
    val token: String,
    val target: NoticeDisplayTarget,
    val dueElapsed: Long,
    val bootCount: Int,
    val exact: Boolean,
    val phase: NoticeDisplayPhase = NoticeDisplayPhase.Queued,
    val sentAt: Long = 0,
    val courseStartAt: Long = 0,
    val interactive: Boolean? = null,
    val locked: Boolean? = null,
    val style: NoticeLiveStyle = NoticeLiveStyle.Automatic,
    val step: NoticeDisplayStep = NoticeDisplayStep.Upcoming,
    val feedback: NoticeDisplayFeedback = NoticeDisplayFeedback.Unchecked,
    val feedbackAt: Long = 0,
    val courseEndAt: Long = 0,
    val startElapsed: Long = 0,
    val courseTitle: String = "",
    val courseLocation: String = "",
    val courseTimeRange: String = "",
    val requestedVivo: Boolean? = null,
    val requestedNative: Boolean? = null,
    val firstRegisteredAt: Long = 0,
    val receipt: NoticeSystemReceipt? = null,
) {
    fun normalized() = if (target == NoticeDisplayTarget.Center) copy(
        feedback = if (feedback == NoticeDisplayFeedback.CourseContent) NoticeDisplayFeedback.BannerAppeared else feedback,
        step = NoticeDisplayStep.NotApplicable,
    ) else this

    fun expired(nowElapsed: Long, currentBoot: Int) =
        bootCount != currentBoot || nowElapsed >= dueElapsed + NoticeDisplayTests.LIFETIME_MS

    fun canDeliver(token: String, nowElapsed: Long, currentBoot: Int) =
        this.token == token && phase == NoticeDisplayPhase.Queued &&
            nowElapsed >= dueElapsed && !expired(nowElapsed, currentBoot)
}

internal data class NoticeDisplayObservation(
    val run: NoticeDisplayRun?, val active: Boolean, val promoted: Boolean,
    val vivoRequested: Boolean = false, val vivoTagged: Boolean = false,
)

/** Isolated, finite system notifications; never changes saved notification preferences. */
internal object NoticeDisplayTests {
    const val LIFETIME_MS = 120_000L
    private const val ACTION_SEND = "com.x500x.cursimple.action.NOTICE_DISPLAY_TEST"
    private const val ACTION_EXPIRE = "com.x500x.cursimple.action.NOTICE_DISPLAY_TEST_EXPIRE"
    private const val ACTION_STARTED = "com.x500x.cursimple.action.NOTICE_DISPLAY_TEST_STARTED"
    private val json = Json { ignoreUnknownKeys = true }
    private fun store(context: Context) = context.getSharedPreferences("notice_display_tests", Context.MODE_PRIVATE)
    private fun bootCount(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun read(context: Context, target: NoticeDisplayTarget): NoticeDisplayRun? = runCatching {
        store(context).getString(target.name, null)?.let { json.decodeFromString<NoticeDisplayRun>(it).normalized() }
    }.getOrNull()
    private fun write(context: Context, run: NoticeDisplayRun) {
        // Persist before handing the request to a receiver in a future process.
        check(store(context).edit().putString(run.target.name, json.encodeToString(NoticeDisplayRun.serializer(), run)).commit())
    }

    fun selectedStyle(context: Context, target: NoticeDisplayTarget): NoticeLiveStyle = runCatching {
        NoticeLiveStyle.valueOf(store(context).getString("style.${target.name}", "Automatic")!!)
    }.getOrDefault(NoticeLiveStyle.Automatic).takeIf { it.supported(Build.VERSION.SDK_INT) } ?: NoticeLiveStyle.Automatic

    fun selectStyle(context: Context, target: NoticeDisplayTarget, style: NoticeLiveStyle) {
        require(style.supported(Build.VERSION.SDK_INT))
        store(context).edit().putString("style.${target.name}", style.name).apply()
    }

    @Synchronized
    fun history(context: Context): List<NoticeDisplayRun> = runCatching {
        json.decodeFromString<List<NoticeDisplayRun>>(store(context).getString("history", "[]")!!).map { it.normalized() }
    }.getOrDefault(emptyList())

    private fun archive(context: Context, run: NoticeDisplayRun) {
        if (run.sentAt == 0L) return
        val rows = (history(context).filterNot { it.token == run.token } + run).takeLast(12)
        store(context).edit().putString("history", json.encodeToString(ListSerializer(NoticeDisplayRun.serializer()), rows)).apply()
    }

    @Synchronized
    fun recordFeedback(context: Context, target: NoticeDisplayTarget, token: String, feedback: NoticeDisplayFeedback): Boolean {
        val run = read(context, target)?.takeIf { it.token == token && it.sentAt > 0 } ?: return false
        val updated = run.copy(feedback = feedback, feedbackAt = System.currentTimeMillis()).normalized()
        write(context, updated)
        archive(context, updated)
        ReminderLogger.info("notice_test.user_observed", mapOf("target" to target, "style" to run.style, "feedback" to updated.feedback))
        return true
    }

    @Synchronized
    fun recordReceipt(context: Context, id: Int, startAt: Long, requestedVivo: Boolean, requestedNative: Boolean, receipt: NoticeSystemReceipt) {
        val target = NoticeDisplayTarget.entries.firstOrNull { it.notificationId == id } ?: return
        val run = read(context, target)?.takeIf { it.sentAt > 0 && it.courseStartAt == startAt } ?: return
        val updated = run.copy(requestedVivo = requestedVivo, requestedNative = requestedNative, receipt = receipt,
            firstRegisteredAt = run.firstRegisteredAt.takeIf { it > 0 } ?: receipt.checkedAt.takeIf { receipt.active } ?: 0)
        write(context, updated)
        archive(context, updated)
    }

    @Synchronized
    fun report(context: Context, target: NoticeDisplayTarget): String {
        val current = observe(context, target)
        val run = current.run
        return buildList {
            add("device=${Build.MANUFACTURER} ${Build.MODEL}")
            add("android=${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            add("build=${Build.DISPLAY}")
            add("app=${context.packageName}")
            add("test=$target; style=${run?.style}; dispatch=${run?.phase}; step=${run?.step}")
            add("sent_at=${run?.sentAt}; screen_on=${run?.interactive}; locked=${run?.locked}")
            add("request.android_live=${run?.requestedNative ?: "unknown"}; request.vivo_atomic=${run?.requestedVivo ?: "unknown"}")
            add("system.active_now=${current.active}; system.seen_in_checks=${run?.firstRegisteredAt?.let { it > 0 } == true}; system.first_seen=${run?.firstRegisteredAt}")
            add("system.android_promoted_now=${current.promoted}; system.vivo_tagged_now=${current.vivoTagged}")
            run?.receipt?.let { r ->
                add("last_receipt: at=${r.checkedAt}; active=${r.active}; promoted=${r.promoted}; vivo_extras_retained=${r.vivoExtrasPresent}; vivo_tagged=${r.vivoTagged}; identity_retained=${r.identityPresent}; template=${r.template}")
            }
            add("user.observed=${run?.feedback}; observed_at=${run?.feedbackAt}")
            add("visual_confirmation=User observation only; framework flags do not confirm on-screen rendering.")
            history(context).filter { it.target == target }.forEach {
                add("history: sent_at=${it.sentAt}; style=${it.style}; user.observed=${it.feedback}")
            }
        }.joinToString("\n")
    }

    @Synchronized
    fun clearAll(context: Context) {
        NoticeDisplayTarget.entries.forEach { clear(context, it) }
        ClassNoticeNotifier.cancel(context, ClassNoticeNotifier.PREVIEW_NOTIFICATION_ID)
    }

    private fun intent(context: Context, target: NoticeDisplayTarget, token: String = "") =
        Intent(context, NoticeDisplayTestReceiver::class.java).setAction(ACTION_SEND)
            .putExtra("target", target.name).putExtra("token", token)

    private fun pending(context: Context, target: NoticeDisplayTarget, token: String = "", flags: Int) =
        PendingIntent.getBroadcast(context, target.notificationId, intent(context, target, token), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun expiryPending(context: Context, target: NoticeDisplayTarget, token: String = "", flags: Int) =
        PendingIntent.getBroadcast(context, target.notificationId, intent(context, target, token).setAction(ACTION_EXPIRE), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun stagePending(context: Context, target: NoticeDisplayTarget, token: String = "", flags: Int) =
        PendingIntent.getBroadcast(context, target.notificationId, intent(context, target, token).setAction(ACTION_STARTED), flags or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized
    fun schedule(context: Context, target: NoticeDisplayTarget, delayMillis: Long, style: NoticeLiveStyle = NoticeLiveStyle.Automatic): NoticeDisplayRun {
        require(delayMillis in 0..30_000)
        require(style.supported(Build.VERSION.SDK_INT))
        clearAll(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        val run = NoticeDisplayRun(UUID.randomUUID().toString(), target, SystemClock.elapsedRealtime() + delayMillis, bootCount(context), exact,
            style = if (target == NoticeDisplayTarget.Center) NoticeLiveStyle.Automatic else style,
            step = if (target == NoticeDisplayTarget.Center) NoticeDisplayStep.NotApplicable else NoticeDisplayStep.Upcoming)
        write(context, run)
        return try {
            if (delayMillis == 0L) {
                context.sendBroadcast(intent(context, target, run.token))
            } else {
                val operation = pending(context, target, run.token, PendingIntent.FLAG_UPDATE_CURRENT)!!
                if (exact) {
                    try { alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, run.dueElapsed, operation) }
                    catch (_: SecurityException) {
                        val fallback = run.copy(exact = false)
                        write(context, fallback)
                        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, run.dueElapsed, operation)
                        return fallback
                    }
                } else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, run.dueElapsed, operation)
            }
            run
        } catch (failure: Exception) {
            ReminderLogger.warn("notice_test.schedule.failed", mapOf("target" to target), failure)
            run.copy(phase = NoticeDisplayPhase.Failed).also { write(context, it) }
        }
    }

    @Synchronized
    fun clear(context: Context, target: NoticeDisplayTarget) {
        listOfNotNull(pending(context, target, flags = PendingIntent.FLAG_NO_CREATE),
            stagePending(context, target, flags = PendingIntent.FLAG_NO_CREATE),
            expiryPending(context, target, flags = PendingIntent.FLAG_NO_CREATE)).forEach {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        read(context, target)?.let {
            val cancelled = it.copy(phase = NoticeDisplayPhase.Cancelled)
            write(context, cancelled)
            archive(context, cancelled)
        }
        ClassNoticeNotifier.cancel(context, target.notificationId)
    }

    @Synchronized
    fun observe(context: Context, target: NoticeDisplayTarget): NoticeDisplayObservation {
        var run = read(context, target)
        if (run != null && run.phase in listOf(NoticeDisplayPhase.Queued, NoticeDisplayPhase.Sent) &&
            run.expired(SystemClock.elapsedRealtime(), bootCount(context))) {
            stagePending(context, target, flags = PendingIntent.FLAG_NO_CREATE)?.let {
                context.getSystemService(AlarmManager::class.java).cancel(it); it.cancel()
            }
            ClassNoticeNotifier.cancel(context, target.notificationId)
            run = run.copy(phase = NoticeDisplayPhase.Expired)
            write(context, run)
            archive(context, run)
        }
        val expected = run?.takeIf { it.sentAt > 0 }?.let {
            ClassNoticeNotifier.Content(it.courseTitle, it.courseLocation, it.courseTimeRange, 1,
                startAtMillis = it.courseStartAt, notificationIdOverride = target.notificationId)
        }
        val posted = if (run?.phase == NoticeDisplayPhase.Sent && expected != null) runCatching {
            context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull {
                it.id == target.notificationId && ClassNoticeNotifier.matchesActiveRecord(context, expected, it)
            }
        }.getOrNull() else null
        val notification = posted?.notification
        if (notification != null && run != null && run.firstRegisteredAt == 0L) {
            run = run.copy(firstRegisteredAt = System.currentTimeMillis())
            write(context, run)
        }
        val promoted = Build.VERSION.SDK_INT >= 36 && notification != null && notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
        return NoticeDisplayObservation(run, notification != null, promoted,
            run?.requestedVivo == true, posted?.tag == VivoAtomicNotice.TAG)
    }

    suspend fun deliver(context: Context, target: NoticeDisplayTarget, token: String, advance: Boolean = false) {
        val preferences = DataStoreUserPreferencesRepository(context).preferencesFlow.first()
        synchronized(this) {
            val run = read(context, target) ?: return
            if (advance) {
                if (run.token != token || run.phase != NoticeDisplayPhase.Sent || run.step != NoticeDisplayStep.Upcoming ||
                    run.expired(SystemClock.elapsedRealtime(), bootCount(context)) || SystemClock.elapsedRealtime() < run.startElapsed) return
            } else if (!run.canDeliver(token, SystemClock.elapsedRealtime(), bootCount(context))) return
            if (!preferences.advancedToolsEnabled) {
                ClassNoticeNotifier.cancel(context, target.notificationId)
                write(context, run.copy(phase = NoticeDisplayPhase.Disabled))
                return
            }
            val selected = target.preferences(preferences.classNotice)
            ClassNoticeNotifier.ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            val channelBlocked = if (Build.VERSION.SDK_INT >= 26) {
                val channel = manager.getNotificationChannel(ClassNoticeNotifier.channelIdFor(selected))
                channel?.importance == NotificationManager.IMPORTANCE_NONE ||
                    (Build.VERSION.SDK_INT >= 28 && channel?.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked } == true)
            } else false
            if (!manager.areNotificationsEnabled() || channelBlocked) {
                write(context, run.copy(phase = NoticeDisplayPhase.Blocked))
                return
            }
            val now = System.currentTimeMillis()
            val end = if (advance) run.courseEndAt else now + (run.dueElapsed + LIFETIME_MS - SystemClock.elapsedRealtime()).coerceAtLeast(1)
            val start = if (advance) run.courseStartAt else now + minOf(60_000, (end - now) / 2)
            val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
            val content = ClassNoticeNotifier.Content(
                courseTitle = if (advance) run.courseTitle else context.getString(R.string.class_notice_preview_course),
                location = if (advance) run.courseLocation else context.getString(R.string.class_notice_preview_location),
                timeRange = if (advance) run.courseTimeRange else "${clock.format(Instant.ofEpochMilli(start))}–${clock.format(Instant.ofEpochMilli(end))}",
                minutesUntilStart = if (advance) 0 else 1,
                startAtMillis = start,
                endAtMillis = end,
                headline = context.getString(if (advance) R.string.notice_test_in_class else target.title),
                inProgress = advance,
                notificationIdOverride = target.notificationId,
            )
            if (advance && !ClassNoticeNotifier.isActive(context, content)) {
                write(context, run.copy(step = NoticeDisplayStep.Dismissed))
                return
            }
            val sent = if (advance) run.copy(step = NoticeDisplayStep.InProgress) else run.copy(
                phase = NoticeDisplayPhase.Sent, sentAt = now, courseStartAt = start, courseEndAt = end,
                startElapsed = SystemClock.elapsedRealtime() + start - now,
                courseTitle = content.courseTitle, courseLocation = content.location, courseTimeRange = content.timeRange,
                interactive = context.getSystemService(PowerManager::class.java)?.isInteractive,
                locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked)
            ClassNoticeNotifier.notify(context, content, selected,
                NoticeTheme.resolve(context, preferences.themeAccent, preferences.themeMode, preferences.themeCustomColorArgb),
                plain = target == NoticeDisplayTarget.Center, aodContent = target == NoticeDisplayTarget.Aod, liveStyle = run.style)
            write(context, sent)
            if (!advance && target != NoticeDisplayTarget.Center) {
                val alarm = context.getSystemService(AlarmManager::class.java)
                val operation = stagePending(context, target, token, PendingIntent.FLAG_UPDATE_CURRENT)!!
                try {
                    if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                        try { alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, sent.startElapsed, operation) }
                        catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, sent.startElapsed, operation) }
                    } else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, sent.startElapsed, operation)
                } catch (failure: Exception) { ReminderLogger.warn("notice_test.stage.failed", mapOf("target" to target), failure) }
            }
            ReminderLogger.info("notice_test.sent", mapOf("target" to target, "style" to run.style, "step" to sent.step,
                "interactive" to sent.interactive, "locked" to sent.locked))
        }
    }

    @Synchronized
    fun fail(context: Context, target: NoticeDisplayTarget, token: String) {
        read(context, target)?.takeIf { it.token == token && it.phase == NoticeDisplayPhase.Queued }
            ?.let { write(context, it.copy(phase = NoticeDisplayPhase.Failed)) }
    }

    @Synchronized
    fun expire(context: Context, target: NoticeDisplayTarget, token: String) {
        val run = read(context, target) ?: return
        if (run.token == token && run.phase == NoticeDisplayPhase.Sent && run.expired(SystemClock.elapsedRealtime(), bootCount(context))) {
            ClassNoticeNotifier.cancel(context, target.notificationId)
            write(context, run.copy(phase = NoticeDisplayPhase.Expired))
        }
    }

    fun isExpiry(intent: Intent) = intent.action == ACTION_EXPIRE
    fun isAdvance(intent: Intent) = intent.action == ACTION_STARTED
    fun targetFrom(intent: Intent): NoticeDisplayTarget? = if (intent.action in listOf(ACTION_SEND, ACTION_EXPIRE, ACTION_STARTED))
        NoticeDisplayTarget.entries.firstOrNull { it.name == intent.getStringExtra("target") } else null
}

class NoticeDisplayTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = NoticeDisplayTests.targetFrom(intent) ?: return
        val token = intent.getStringExtra("token") ?: return
        if (NoticeDisplayTests.isExpiry(intent)) {
            NoticeDisplayTests.expire(context, target, token)
            return
        }
        val pending = goAsync()
        val app = context.applicationContext
        val lock = runCatching { app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cursimple:notice-display-test").apply { acquire(9_000) }
        }.getOrNull()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(7_000) { NoticeDisplayTests.deliver(app, target, token, advance = NoticeDisplayTests.isAdvance(intent)) } }
            catch (failure: Exception) {
                NoticeDisplayTests.fail(app, target, token)
                ReminderLogger.warn("notice_test.delivery.failed", mapOf("target" to target), failure)
            } finally {
                runCatching { if (lock?.isHeld == true) lock.release() }
                pending.finish()
            }
        }
    }
}
