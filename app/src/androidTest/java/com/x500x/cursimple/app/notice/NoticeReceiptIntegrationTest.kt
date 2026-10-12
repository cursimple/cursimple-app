package com.x500x.cursimple.app.notice

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.util.LogExporter
import com.x500x.cursimple.core.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.StringWriter

class NoticeReceiptIntegrationTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val target = NoticeDisplayTarget.Island
    private val prefs get() = DataStoreUserPreferencesRepository(context)
    private var original: UserPreferences? = null
    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("noticeReceiptQa") == "true")
        original = prefs.preferencesFlow.first()
        prefs.setAdvancedToolsEnabled(true)
        NoticeDisplayTests.clearAll(context)
    }
    @After fun clean() = runBlocking<Unit> {
        val previous = original ?: return@runBlocking
        NoticeDisplayTests.clearAll(context)
        prefs.setAdvancedToolsEnabled(previous.advancedToolsEnabled)
    }

    @Test fun systemRemovedIdentityExtrasDoNotHideTheRecordOrPreventCourseUpdates() {
        NoticeDisplayTests.schedule(context, target, 0)
        waitFor { NoticeDisplayTests.observe(context, target).active }
        val run = NoticeDisplayTests.observe(context, target).run!!
        val stripped = active().clone().apply {
            extras.remove(ClassNoticeNotifier.EXTRA_COURSE_START)
            extras.remove(ClassNoticeNotifier.EXTRA_COURSE_TITLE)
        }
        manager.notify(target.notificationId, stripped)
        waitFor { !active().extras.containsKey(ClassNoticeNotifier.EXTRA_COURSE_START) }
        assertTrue(NoticeDisplayTests.observe(context, target).active)
        val content = ClassNoticeNotifier.Content(run.courseTitle, run.courseLocation, run.courseTimeRange, 0,
            startAtMillis = run.courseStartAt, endAtMillis = run.courseEndAt, inProgress = true, notificationIdOverride = target.notificationId)
        assertTrue(ClassNoticeNotifier.isActive(context, content))
        ClassNoticeNotifier.cancelIfCurrent(context, content.copy(startAtMillis = run.courseStartAt-60_000))
        assertTrue(NoticeDisplayTests.observe(context, target).active)
        ClassNoticeNotifier.notify(context, content, ClassNoticePreferences(headsUpEnabled = false), NoticeTheme.of(ThemeAccent.Green, false))
        waitFor { active().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 }
        ClassNoticeNotifier.cancelIfCurrent(context, content)
        waitFor { manager.activeNotifications.none { it.id == target.notificationId } }
    }

    @Test fun requestEvidenceSurvivesFilteredExtrasAndNotificationRemoval() {
        NoticeDisplayTests.schedule(context, target, 0)
        waitFor { NoticeDisplayTests.observe(context, target).run?.receipt != null }
        val run = NoticeDisplayTests.observe(context, target).run!!
        val content = ClassNoticeNotifier.Content(run.courseTitle, run.courseLocation, run.courseTimeRange, 1,
            startAtMillis = run.courseStartAt, endAtMillis = run.courseEndAt, notificationIdOverride = target.notificationId)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, ClassNoticeNotifier.CHANNEL_ID_QUIET)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(run.courseTitle).setContentText(run.courseLocation)
            .setStyle(NotificationCompat.BigTextStyle().bigText(run.courseLocation))
            .addExtras(VivoAtomicNotice.extras(context, content, NoticeTheme.of(ThemeAccent.Green, false), open, true, true, run.courseEndAt)).build()
        val requested = ClassNoticeDiagnostics.requestFields(notification)
        assertEquals(true, requested["request.vivo_atomic"])
        assertEquals(true, requested["request.vivo_small_card"])
        notification.extras.keySet().filter { it.startsWith("notification.superx.") }.forEach { notification.extras.remove(it) }
        manager.notify(target.notificationId, notification)
        ClassNoticeDiagnostics.logPosted(context, target.notificationId, requested, run.courseStartAt)
        waitFor { NoticeDisplayTests.observe(context, target).run?.requestedVivo == true }
        val observed = NoticeDisplayTests.observe(context, target)
        assertTrue(observed.active)
        assertTrue(observed.vivoRequested)
        assertEquals(false, observed.run?.receipt?.vivoExtrasPresent)
        NoticeDisplayTests.clear(context, target)
        val report = NoticeDisplayTests.report(context, target)
        assertTrue(report.contains("request.vivo_atomic=true"))
        assertTrue(report.contains("system.active_now=false"))
        assertTrue(report.contains("system.seen_in_checks=true"))
        val export = StringWriter().also { LogExporter.appendNoticeTests(it, context) }.toString()
        assertTrue(export.contains("Notification display snapshot"))
        assertTrue(export.contains("vivo_extras_retained=false"))
        NoticeDisplayTests.schedule(context, target, 0)
        waitFor { NoticeDisplayTests.observe(context, target).run?.receipt != null }
        NoticeDisplayTests.recordReceipt(context, target.notificationId, run.courseStartAt, true, false, observed.run!!.receipt!!)
        assertEquals(false, NoticeDisplayTests.observe(context, target).run?.requestedVivo)
    }

    private fun active(): Notification = manager.activeNotifications.first { it.id == target.notificationId }.notification
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis()+12_000
        while (SystemClock.uptimeMillis()<end) { if (condition()) return; Thread.sleep(60) }
        assertTrue("Expected notification evidence was not observed", condition())
    }
}
