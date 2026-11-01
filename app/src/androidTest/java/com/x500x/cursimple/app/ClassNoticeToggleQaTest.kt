package com.x500x.cursimple.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.notice.ClassNoticeReceiver
import com.x500x.cursimple.app.notice.ClassNoticeScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Replays an already queued alarm after changing its preferences. */
class ClassNoticeToggleQaTest {
    @Test fun disabledSwitchesBlockQueuedAlarmsButEnabledExamCanNotify() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("preNoticeQa") == "true")
        val context = instrumentation.targetContext
        assumeTrue(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val container = (context.applicationContext as ClassScheduleApplication).appContainer
        container.bootstrapJob.join()
        val repo = container.userPreferencesRepository
        val old = repo.preferencesFlow.first().classNotice
        val manager = context.getSystemService(NotificationManager::class.java)
        fun replay(actionName: String) {
            val finished = CountDownLatch(1)
            val intent = Intent(context, ClassNoticeReceiver::class.java)
                .setAction("com.x500x.cursimple.action.CLASS_NOTICE_$actionName")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("examTitle", "QA 考试")
                .putExtra("examLocation", "一教101")
                .putExtra("examSeat", "12")
                .putExtra("examMinutes", 45)
                .putExtra("transitTitle", "QA 下一节")
                .putExtra("transitLocation", "西区实验楼")
            context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { finished.countDown() }
            }, Handler(Looper.getMainLooper()), Activity.RESULT_OK, null, null)
            assertTrue("Alarm receiver did not finish", finished.await(20, TimeUnit.SECONDS))
        }
        fun active(id: Int) = manager.activeNotifications.any { it.id == id }
        fun eventually(timeout: Long = 5_000, condition: () -> Boolean): Boolean {
            val until = android.os.SystemClock.uptimeMillis() + timeout
            while (android.os.SystemClock.uptimeMillis() < until) {
                if (condition()) return true
                Thread.sleep(50)
            }
            return condition()
        }
        fun staysAbsent(id: Int) = !eventually(750) { active(id) }
        try {
            manager.cancel(ClassNoticeScheduler.NOTIFICATION_ID_EXAM)
            manager.cancel(ClassNoticeScheduler.NOTIFICATION_ID_TRANSIT)
            repo.setClassNoticeEnabled(true)
            repo.setExamAlertEnabled(true)
            replay("EXAM")
            assertTrue("Positive control: enabled exam alarm must reach the system notification service",
                eventually { active(ClassNoticeScheduler.NOTIFICATION_ID_EXAM) })
            manager.cancel(ClassNoticeScheduler.NOTIFICATION_ID_EXAM)
            assertTrue(eventually { !active(ClassNoticeScheduler.NOTIFICATION_ID_EXAM) })

            repo.setExamAlertEnabled(false)
            repo.setTransitReminderEnabled(false)
            replay("EXAM")
            replay("TRANSIT")
            assertTrue("A queued exam must respect the latest disabled switch", staysAbsent(ClassNoticeScheduler.NOTIFICATION_ID_EXAM))
            assertTrue("A queued transit reminder must respect the latest disabled switch", staysAbsent(ClassNoticeScheduler.NOTIFICATION_ID_TRANSIT))

            repo.setExamAlertEnabled(true)
            repo.setTransitReminderEnabled(true)
            repo.setClassNoticeEnabled(false)
            replay("EXAM")
            replay("TRANSIT")
            assertTrue("The master switch must block exam delivery", staysAbsent(ClassNoticeScheduler.NOTIFICATION_ID_EXAM))
            assertTrue("The master switch must block transit delivery", staysAbsent(ClassNoticeScheduler.NOTIFICATION_ID_TRANSIT))
        } finally {
            manager.cancel(ClassNoticeScheduler.NOTIFICATION_ID_EXAM)
            manager.cancel(ClassNoticeScheduler.NOTIFICATION_ID_TRANSIT)
            repo.setClassNoticeEnabled(old.enabled)
            repo.setExamAlertEnabled(old.examAlertEnabled)
            repo.setTransitReminderEnabled(old.transitReminderEnabled)
        }
    }
}
