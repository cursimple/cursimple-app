package com.x500x.cursimple.app.vendor

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.VendorCourseSettingsSection
import com.x500x.cursimple.app.notice.*
import com.x500x.cursimple.app.reminder.ReminderGuardForegroundService
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

class VendorNoticeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    @Before fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vendorNoticeQa") == "true")
        DataStoreUserPreferencesRepository(context).apply { setDisclaimerAccepted(true); setFirstRunGuideCompleted(true); setAlarmKeepAliveEnabled(true) }
        assertTrue(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        VendorCourseStore.save(context, VendorCourseConfig())
        ClassNoticeNotifier.cancel(context)
    }
    @After fun clean() {
        if (InstrumentationRegistry.getArguments().getString("vendorNoticeQa") != "true") return
        ReminderGuardForegroundService.setEnabled(context, false)
        VendorCourseStore.save(context, VendorCourseConfig())
        ClassNoticeScheduler.cancelAll(context)
    }
    @Test fun providersRejectAnUntrustedCallerEvenWithSharingEnabled() {
        VendorCourseStore.save(context, VendorCourseConfig(enabled = true))
        assertThrows(SecurityException::class.java) {
            context.contentResolver.call(Uri.parse("content://${context.packageName}.provider.vivodonateprovider"), "syncQuerySuperClassData", null, Bundle())
        }
        assertThrows(SecurityException::class.java) {
            context.contentResolver.query(Uri.parse("content://${context.packageName}.provider.oppodonateprovider/course_list"), null, null, null, null)?.close()
        }
        assertThrows(SecurityException::class.java) {
            context.contentResolver.call(Uri.parse("content://${context.packageName}.provider.honordonateprovider"), "feedback", null, Bundle())
        }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    @Test fun platformLiveUpdateUsesPromotableSystemTemplates() {
        val now = System.currentTimeMillis()
        val content = ClassNoticeNotifier.Content("QA course", "Room 301", "08:00–09:00", 1, startAtMillis = now + 60_000, endAtMillis = now + 120_000)
        inst.runOnMainSync { ClassNoticeNotifier.notify(context, content, ClassNoticePreferences(skin = ClassNoticeSkin.Card), NoticeTheme.of(ThemeAccent.Green, false)) }
        waitFor { manager.activeNotifications.any { it.id == content.notificationId } }
        val notification = manager.activeNotifications.first { it.id == content.notificationId }.notification
        assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.hasPromotableCharacteristics())
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
    }
    @Test fun staleCourseUpdatesAndEndEventsCannotReplaceTheNextCourse() {
        val now = System.currentTimeMillis()
        val first = ClassNoticeNotifier.Content("First", "A", "08:00–09:00", 1, startAtMillis = now+60_000, endAtMillis = now+120_000)
        val second = first.copy(courseTitle = "Second", startAtMillis = now+180_000, endAtMillis = now+240_000)
        val prefs = ClassNoticePreferences(focusNotificationEnabled = false)
        inst.runOnMainSync { ClassNoticeNotifier.notify(context, first, prefs, NoticeTheme.of(ThemeAccent.Green, false)) }
        waitFor { manager.activeNotifications.any { it.notification.extras.getString(Notification.EXTRA_TITLE) == "First" } }
        inst.runOnMainSync {
            ClassNoticeNotifier.notify(context, second, prefs, NoticeTheme.of(ThemeAccent.Green, false))
            ClassNoticeNotifier.notify(context, first.copy(inProgress = true), prefs, NoticeTheme.of(ThemeAccent.Green, false))
            ClassNoticeNotifier.cancelIfCurrent(context, first)
        }
        waitFor { manager.activeNotifications.any { it.notification.extras.getString(Notification.EXTRA_TITLE) == "Second" } }
        inst.runOnMainSync { ClassNoticeNotifier.cancelIfCurrent(context, second) }
        waitFor { manager.activeNotifications.none { it.id == first.notificationId } }
    }
    @Test fun enhancedGuardHasAQuietNotificationAndAWorkingStopAction() {
        inst.runOnMainSync {
            ReminderGuardForegroundService.setEnabled(context, true)
            ReminderGuardForegroundService.startIfEnabled(context)
        }
        waitFor { manager.activeNotifications.any { it.notification.channelId == "reminder_guard_foreground" } }
        val notification = manager.activeNotifications.first { it.notification.channelId == "reminder_guard_foreground" }.notification
        assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        assertFalse(manager.getNotificationChannel("reminder_guard_foreground").shouldVibrate())
        notification.actions.single().actionIntent.send()
        waitFor { !ReminderGuardForegroundService.isEnabled(context) && manager.activeNotifications.none { it.notification.channelId == "reminder_guard_foreground" } }
    }
    @Test fun settingsReportUnsupportedServicesAndFollowTheAppTheme() {
        val dark = mutableStateOf(false)
        compose.activity.setContent {
            ClassScheduleTheme(if (dark.value) ThemeMode.Dark else ThemeMode.Light, ThemeAccent.Green) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) { VendorCourseSettingsSection() }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.vendor_course_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.vendor_course_enable)).performClick()
        compose.onNodeWithText(context.getString(R.string.vendor_course_refresh)).performClick()
        compose.waitUntil(15_000) { VendorCourseStore.statuses(context).filter { it.first != "android" }.all { it.second == "unavailable" } }
        capture("vendor-settings-light.png")
        dark.value = true
        compose.waitForIdle()
        capture("vendor-settings-dark.png")
        compose.onNodeWithText(context.getString(R.string.notice_guide_close)).performClick()
    }
    private fun capture(name: String) {
        inst.runOnMainSync {
            val root = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.isAttachedToWindow && it.width > 0 }
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            File(context.filesDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis()+12_000
        while (SystemClock.uptimeMillis()<end) { if (condition()) return; Thread.sleep(80) }
        assertTrue("System result did not arrive", condition())
    }
}
