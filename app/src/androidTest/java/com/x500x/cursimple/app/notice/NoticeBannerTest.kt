package com.x500x.cursimple.app.notice

import android.app.NotificationManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.extension.ExtensionNotifier
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in system notification and overlay checks; dedicated emulator only. */
@RunWith(AndroidJUnit4::class)
class NoticeBannerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("noticeBannerQa") == "true")
        assertTrue("Grant overlay permission on the isolated QA device", ClassNoticeOverlay.canDraw(context))
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        instrumentation.runOnMainSync { ClassNoticeOverlay.dismiss(); ClassNoticeNotifier.cancel(context) }
        waitFor { card() == null }
    }

    @After fun cleanup() {
        if (InstrumentationRegistry.getArguments().getString("noticeBannerQa") != "true") return
        instrumentation.runOnMainSync { ClassNoticeOverlay.dismiss(); ClassNoticeNotifier.cancel(context) }
    }

    @Test fun defaultBannerLastsFifteenSecondsAndSystemNotificationSurvivesTimeout() {
        val started = SystemClock.uptimeMillis()
        show()
        SystemClock.sleep(6_500)
        assertNotNull("Must outlast the former six-second timeout", card())
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.filesDir, "notice-banner-qa.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        waitFor(timeout = 11_000) { card() == null }
        val elapsed = SystemClock.uptimeMillis() - started
        assertTrue("Expected about 15 seconds, got $elapsed ms", elapsed in 14_500..17_500)
        assertSystemNotice()
    }

    @Test fun horizontalAndUpwardSwipesDismissPromptlyWithoutRemovingSystemNotification() {
        for ((dx, dy) in listOf(-110f to 0f, 110f to 0f, 0f to -80f)) {
            show()
            drag(dx, dy, duration = 160)
            waitFor(timeout = 1_000) { card() == null }
            assertSystemNotice()
        }
    }

    @Test fun quickShortFlickDismissesAndSmallSlowDragReturnsHome() {
        show()
        val origin = position()
        drag(12f, 0f, duration = 280)
        SystemClock.sleep(300)
        assertNotNull(card())
        val returned = position()
        assertTrue(kotlin.math.abs(origin[0] - returned[0]) <= 2)
        assertTrue(kotlin.math.abs(origin[1] - returned[1]) <= 2)
        drag(30f, 0f, duration = 25)
        waitFor(timeout = 1_000) { card() == null }
        assertSystemNotice()
    }

    @Test fun holdingPausesConfiguredTimeoutAndCancelRestartsIt() {
        show(seconds = 5)
        val origin = position()
        val card = card()!!
        val x = origin[0] + card.width / 2f
        val y = origin[1] + card.height / 2f
        val down = SystemClock.uptimeMillis()
        input(MotionEvent.ACTION_DOWN, down, x, y)
        SystemClock.sleep(5_500)
        assertNotNull("Holding must pause automatic dismissal", card())
        input(MotionEvent.ACTION_CANCEL, down, x, y)
        SystemClock.sleep(1_000)
        assertNotNull("Cancel should restart the configured duration", card())
        waitFor(timeout = 5_500) { card() == null }
        assertSystemNotice()
    }

    @Test fun bannerDurationPersistsAndExplicitSystemSelectionRemainsUnchanged() = runBlocking {
        val repository = DataStoreUserPreferencesRepository(context)
        val before = repository.preferencesFlow.first().classNotice
        try {
            repository.setClassNoticeSkin(ClassNoticeSkin.System)
            repository.setClassNoticeBannerDurationSeconds(23)
            val reloaded = DataStoreUserPreferencesRepository(context).preferencesFlow.first().classNotice
            assertEquals(23_000L, reloaded.bannerDurationMillis)
            assertEquals(ClassNoticeSkin.System, reloaded.skin)
            repository.setClassNoticeBannerDurationSeconds(999)
            assertEquals(60, repository.preferencesFlow.first().classNotice.bannerDurationSeconds)
            repository.setClassNoticeBannerDurationSeconds(-1)
            assertEquals(5, repository.preferencesFlow.first().classNotice.bannerDurationSeconds)
        } finally {
            repository.setClassNoticeSkin(before.skin)
            repository.setClassNoticeBannerDurationSeconds(before.bannerDurationSeconds)
        }
    }

    @Test fun disablingHeadsUpKeepsSystemNotificationAndShowsNoCustomBanner() {
        show(headsUp = false)
        SystemClock.sleep(500)
        assertNull(card())
        assertSystemNotice()
    }

    @Test fun componentNotificationUsesSharedDurationAndKeepsSystemRecord() = runBlocking {
        val repository = DataStoreUserPreferencesRepository(context)
        val before = repository.preferencesFlow.first().classNotice
        val pluginId = "banner-qa"
        try {
            repository.setClassNoticeSkin(ClassNoticeSkin.Overlay)
            repository.setClassNoticeHeadsUpEnabled(true)
            repository.setClassNoticeBannerDurationSeconds(5)
            ExtensionNotifier.notifyLoginExpired(context, pluginId, "组件弹窗测试")
            waitFor { card() != null }
            val notice = manager.activeNotifications.first { it.notification.extras.getString("cursimple.extension.plugin") == pluginId }
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(notice.notification.channelId).importance)
            waitFor(timeout = 6_000) { card() == null }
            assertTrue(manager.activeNotifications.any { it.id == notice.id })
        } finally {
            ExtensionNotifier.cancelAll(context, pluginId)
            repository.setClassNoticeSkin(before.skin)
            repository.setClassNoticeHeadsUpEnabled(before.headsUpEnabled)
            repository.setClassNoticeBannerDurationSeconds(before.bannerDurationSeconds)
        }
    }

    private fun show(seconds: Int = 15, headsUp: Boolean = true) {
        instrumentation.runOnMainSync {
            ClassNoticeOverlay.dismiss()
            ClassNoticeNotifier.cancel(context)
            ClassNoticeNotifier.notify(
                context,
                ClassNoticeNotifier.Content("弹窗体验测试", "通知栏继续保留", "19:00-20:35", 15),
                ClassNoticePreferences(
                    skin = ClassNoticeSkin.Overlay,
                    animation = ClassNoticeAnimation.None,
                    blurEnabled = false,
                    focusNotificationEnabled = false,
                    bannerDurationSeconds = seconds,
                    headsUpEnabled = headsUp,
                ),
                NoticeTheme.of(ThemeAccent.Green, dark = false),
            )
        }
        runBlocking { ClassNoticeOverlay.awaitShown() }
        if (headsUp) waitFor { card()?.width?.let { it > 0 } == true }
    }

    private fun card(): View? {
        var result: View? = null
        instrumentation.runOnMainSync {
            result = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { root ->
                root.findViewById<View>(R.id.overlay_title)?.takeIf { it.isAttachedToWindow }?.parent?.parent as? View
            }
        }
        return result
    }

    private fun position(): IntArray = IntArray(2).also { location ->
        val card = card()!!
        instrumentation.runOnMainSync { card.getLocationOnScreen(location) }
    }

    private fun drag(dxDp: Float, dyDp: Float, duration: Long) {
        val origin = position()
        val card = card()!!
        val density = context.resources.displayMetrics.density
        val x = origin[0] + card.width / 2f
        val y = origin[1] + card.height / 2f
        val down = SystemClock.uptimeMillis()
        input(MotionEvent.ACTION_DOWN, down, x, y)
        for (step in 1..5) {
            SystemClock.sleep(duration / 5)
            input(MotionEvent.ACTION_MOVE, down, x + dxDp * density * step / 5, y + dyDp * density * step / 5)
        }
        input(MotionEvent.ACTION_UP, down, x + dxDp * density, y + dyDp * density)
    }

    private fun input(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        event.recycle()
    }

    private fun assertSystemNotice() {
        assertTrue("System notification must remain after custom banner dismissal", manager.activeNotifications.any {
            it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "弹窗体验测试"
        })
    }

    private fun waitFor(timeout: Long = 2_500, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) fail("Timed out waiting for banner state")
            SystemClock.sleep(25)
        }
    }
}
