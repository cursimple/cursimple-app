package com.x500x.cursimple.app.notice

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.NoticeDisplayTestEntries
import com.x500x.cursimple.app.NoticeDisplayTestSection
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

class NoticeDisplayTestIntegration {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val repository get() = DataStoreUserPreferencesRepository(context)
    private var original: UserPreferences? = null
    private val selected = NoticeDisplayTarget.Center

    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("noticeDisplayQa") == "true")
        original = repository.preferencesFlow.first()
        repository.setDisclaimerAccepted(true)
        repository.setFirstRunGuideCompleted(true)
        repository.setAdvancedToolsEnabled(true)
        assertTrue(manager.areNotificationsEnabled())
        NoticeDisplayTarget.entries.forEach {
            NoticeDisplayTests.clear(context, it)
            NoticeDisplayTests.selectStyle(context, it, NoticeLiveStyle.Automatic)
        }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    @After fun clean() = runBlocking<Unit> {
        val saved = original ?: return@runBlocking
        NoticeDisplayTests.clearAll(context)
        ClassNoticeNotifier.cancel(context)
        repository.setAdvancedToolsEnabled(saved.advancedToolsEnabled)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    @Test fun standardTestIsIsolatedAndDoesNotAlterPreferences() = runBlocking {
        val before = repository.preferencesFlow.first().classNotice
        val now = System.currentTimeMillis()
        inst.runOnMainSync { ClassNoticeNotifier.notify(context,
            ClassNoticeNotifier.Content("Real reminder", "Room 1", "20:00–21:00", 1, startAtMillis = now + 60_000, endAtMillis = now + 300_000),
            before, NoticeTheme.of(ThemeAccent.Green, false)) }
        waitFor { manager.activeNotifications.any { it.id == ClassNoticeNotifier.NOTIFICATION_ID } }
        NoticeDisplayTests.schedule(context, selected, 0)
        waitFor { NoticeDisplayTests.observe(context, selected).active }
        val notification = active(selected)
        assertEquals(ClassNoticeNotifier.channelIdFor(selected.preferences(before)), notification.channelId)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertNull(notification.fullScreenIntent)
        assertEquals(2, notification.actions.size)
        assertTrue(notification.timeoutAfter in 1..NoticeDisplayTests.LIFETIME_MS)
        NoticeDisplayTests.clear(context, selected)
        waitFor { manager.activeNotifications.none { it.id == selected.notificationId } }
        assertTrue(manager.activeNotifications.any { it.id == ClassNoticeNotifier.NOTIFICATION_ID })
        assertEquals(before, repository.preferencesFlow.first().classNotice)
    }

    @Test fun cancellationReplacementAndDuplicateCallbacksCannotPostStaleTests() = runBlocking {
        val cancelled = NoticeDisplayTests.schedule(context, selected, 1_000)
        NoticeDisplayTests.clear(context, selected)
        Thread.sleep(1_200)
        NoticeDisplayTests.deliver(context, selected, cancelled.token)
        assertFalse(NoticeDisplayTests.observe(context, selected).active)
        assertEquals(NoticeDisplayPhase.Cancelled, NoticeDisplayTests.observe(context, selected).run?.phase)
        val current = NoticeDisplayTests.schedule(context, selected, 0)
        NoticeDisplayTests.deliver(context, selected, cancelled.token)
        waitFor { NoticeDisplayTests.observe(context, selected).active }
        val sentAt = NoticeDisplayTests.observe(context, selected).run?.sentAt
        NoticeDisplayTests.deliver(context, selected, current.token)
        assertEquals(sentAt, NoticeDisplayTests.observe(context, selected).run?.sentAt)
        assertEquals(current.token, NoticeDisplayTests.observe(context, selected).run?.token)
    }

    @Test fun aodAlarmDeliversWhileTheScreenIsOffWithoutStartingAnOverlay() {
        val target = NoticeDisplayTarget.Aod
        val run = NoticeDisplayTests.schedule(context, target, 5_000)
        assertTrue("QA device requires exact-alarm access", run.exact)
        shell("input keyevent KEYCODE_SLEEP")
        waitFor { !context.getSystemService(PowerManager::class.java).isInteractive }
        waitFor { NoticeDisplayTests.observe(context, target).active }
        val observed = NoticeDisplayTests.observe(context, target)
        assertEquals(false, observed.run?.interactive)
        val notification = active(target)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertNull(notification.fullScreenIntent)
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
    }

    @Test fun islandRequestsNativePromotionWhereThePlatformSupportsIt() {
        val now = System.currentTimeMillis()
        val prefs = runBlocking { repository.preferencesFlow.first().classNotice }
        val content = ClassNoticeNotifier.Content("正在上课", "教学楼 201", "08:00–09:40", 0,
            startAtMillis = now - 60_000, endAtMillis = now + 300_000,
            notificationIdOverride = NoticeDisplayTarget.Island.notificationId)
        inst.runOnMainSync {
            ClassNoticeNotifier.notify(context, content,
                NoticeDisplayTarget.Island.preferences(prefs).copy(focusNotificationEnabled = false),
                NoticeTheme.of(ThemeAccent.Green, false))
        }
        waitFor { manager.activeNotifications.any { it.id == NoticeDisplayTarget.Island.notificationId } }
        inst.runOnMainSync {
            ClassNoticeNotifier.notify(context,
                content.copy(inProgress = true),
                NoticeDisplayTarget.Island.preferences(prefs), NoticeTheme.of(ThemeAccent.Green, false),
                liveStyle = NoticeLiveStyle.NativeText)
        }
        waitFor {
            manager.activeNotifications.any { it.id == NoticeDisplayTarget.Island.notificationId &&
                (Build.VERSION.SDK_INT < 36 || it.notification.extras.getBoolean("android.requestPromotedOngoing")) }
        }
        val notification = active(NoticeDisplayTarget.Island)
        assertNull(notification.fullScreenIntent)
        if (Build.VERSION.SDK_INT >= 36) {
            assertTrue(notification.extras.getBoolean("android.requestPromotedOngoing"))
            assertTrue(notification.hasPromotableCharacteristics())
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(!standardPromotionSupported(), notification.extras.getBoolean("android.colorized"))
            assertNull(notification.contentView)
            assertNull(notification.bigContentView)
        } else assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
    }

    @Test fun disablingHiddenToolsStopsAPendingSend() = runBlocking {
        NoticeDisplayTests.schedule(context, selected, 1_000)
        repository.setAdvancedToolsEnabled(false)
        waitFor { NoticeDisplayTests.observe(context, selected).run?.phase == NoticeDisplayPhase.Disabled }
        assertFalse(NoticeDisplayTests.observe(context, selected).active)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    @Test fun nativeProgressUpdatesInPlaceWhenTheSampleClassStarts() {
        val target = NoticeDisplayTarget.Island
        NoticeDisplayTests.schedule(context, target, 0, NoticeLiveStyle.NativeProgress)
        waitFor { NoticeDisplayTests.observe(context, target).active }
        val first = active(target)
        assertFalse(first.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(0, first.flags and Notification.FLAG_ONGOING_EVENT)
        assertFalse(first.extras.containsKey(VivoAtomicNotice.OPERATION))
        assertFalse(first.extras.containsKey("miui.focus.param"))
        val deadline = SystemClock.elapsedRealtime()+75_000
        while (SystemClock.elapsedRealtime()<deadline && NoticeDisplayTests.observe(context, target).run?.step != NoticeDisplayStep.InProgress) Thread.sleep(100)
        assertEquals(NoticeDisplayStep.InProgress, NoticeDisplayTests.observe(context, target).run?.step)
        waitFor { active(target).extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("ProgressStyle") }
        val updated = active(target)
        assertTrue(updated.hasPromotableCharacteristics())
        assertEquals(!standardPromotionSupported(), updated.extras.getBoolean("android.colorized"))
        assertTrue(updated.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(com.x500x.cursimple.core.data.AppLocale.wrap(context).getString(R.string.notice_test_in_class), updated.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(1, manager.activeNotifications.count { it.id == target.notificationId })
        assertTrue(updated.extras.getInt(Notification.EXTRA_PROGRESS) >= 490)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    @Test fun recordedVisualResultsStaySeparateFromFrameworkFlagsAndStaleTokens() {
        val target = NoticeDisplayTarget.Island
        val first = NoticeDisplayTests.schedule(context, target, 0, NoticeLiveStyle.NativeText)
        waitFor { NoticeDisplayTests.observe(context, target).active }
        assertEquals(NoticeDisplayFeedback.Unchecked, NoticeDisplayTests.observe(context, target).run?.feedback)
        assertTrue(NoticeDisplayTests.recordFeedback(context, target, first.token, NoticeDisplayFeedback.IconOnly))
        val second = NoticeDisplayTests.schedule(context, target, 0, NoticeLiveStyle.NativeProgress)
        waitFor { NoticeDisplayTests.observe(context, target).active }
        assertFalse(NoticeDisplayTests.recordFeedback(context, target, first.token, NoticeDisplayFeedback.CourseContent))
        assertEquals(second.token, NoticeDisplayTests.observe(context, target).run?.token)
        assertEquals(NoticeDisplayFeedback.Unchecked, NoticeDisplayTests.observe(context, target).run?.feedback)
        val history = NoticeDisplayTests.history(context)
        assertEquals(NoticeDisplayFeedback.IconOnly, history.first { it.token == first.token }.feedback)
        assertTrue(history.size <= 12)
        NoticeDisplayTests.schedule(context, NoticeDisplayTarget.Center, 0)
        waitFor { NoticeDisplayTests.observe(context, NoticeDisplayTarget.Center).active }
        assertTrue(manager.activeNotifications.none { it.id == target.notificationId })
        assertTrue(NoticeDisplayTests.report(context, target).contains("NativeProgress"))
    }

    @Test fun legacyPreviewsAndClearAllLeaveTheRealCourseNotificationIntact() {
        val now = System.currentTimeMillis()
        val real = ClassNoticeNotifier.Content("Real class", "A-201", "09:00–10:00", 1, startAtMillis = now+60_000, endAtMillis = now+300_000)
        val prefs = ClassNoticePreferences(focusNotificationEnabled = false)
        val theme = NoticeTheme.of(ThemeAccent.Green, false)
        inst.runOnMainSync { ClassNoticeNotifier.notify(context, real, prefs, theme) }
        waitFor { manager.activeNotifications.any { it.id == real.notificationId } }
        inst.runOnMainSync { ClassNoticeNotifier.notifyPreview(context, prefs, theme) }
        waitFor { manager.activeNotifications.any { it.id == ClassNoticeNotifier.PREVIEW_NOTIFICATION_ID } }
        assertTrue(manager.activeNotifications.any { it.id == real.notificationId })
        NoticeDisplayTests.clearAll(context)
        waitFor { manager.activeNotifications.none { it.id == ClassNoticeNotifier.PREVIEW_NOTIFICATION_ID } }
        assertTrue(manager.activeNotifications.any { it.id == real.notificationId })
        inst.runOnMainSync { ClassNoticeNotifier.notify(context, real.copy(notificationIdOverride = ClassNoticeNotifier.PREVIEW_NOTIFICATION_ID, inProgress = true), prefs, theme) }
        assertTrue(manager.activeNotifications.none { it.id == ClassNoticeNotifier.PREVIEW_NOTIFICATION_ID })
    }

    @Test fun testPagesNavigateAndRenderInBothThemesAtLargeFontSize() {
        val dark = mutableStateOf(false)
        val target = mutableStateOf<NoticeDisplayTarget?>(null)
        compose.activity.setContent {
            ClassScheduleTheme(if (dark.value) ThemeMode.Dark else ThemeMode.Light, if (dark.value) ThemeAccent.Purple else ThemeAccent.Green) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.widthIn(max = 320.dp).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val page = target.value
                            if (page == null) NoticeDisplayTestEntries { target.value = it }
                            else {
                                OutlinedButton(onClick = { target.value = null }) { Text("Back to tests") }
                                NoticeDisplayTestSection(page, ClassNoticePreferences())
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.notice_test_aod)).performClick()
        if (Build.VERSION.SDK_INT >= 36) {
            compose.onNodeWithText(context.getString(R.string.notice_test_style_select, context.getString(R.string.notice_test_style_auto)))
                .performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.notice_test_style_progress)).performClick()
            assertEquals(NoticeLiveStyle.NativeProgress, NoticeDisplayTests.selectedStyle(context, NoticeDisplayTarget.Aod))
        } else {
            compose.onNodeWithText(context.getString(R.string.notice_test_style_select, context.getString(R.string.notice_test_style_auto)))
                .performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.notice_test_style_progress)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.notice_test_style_text)).assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.notice_test_style_auto)).performClick()
        }
        compose.onNodeWithText(context.getString(R.string.notice_test_send_delayed)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { NoticeDisplayTests.observe(context, NoticeDisplayTarget.Aod).run?.phase == NoticeDisplayPhase.Queued }
        compose.onNodeWithText(context.getString(R.string.notice_test_clear)).performScrollTo().performClick()
        compose.waitUntil { NoticeDisplayTests.observe(context, NoticeDisplayTarget.Aod).run?.phase == NoticeDisplayPhase.Cancelled }
        compose.waitUntil { compose.onAllNodesWithText(context.getString(R.string.notice_test_cancelled)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.notice_test_clear)).performScrollTo()
        capture("display-test-light.png")
        dark.value = true
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.notice_test_clear)).performScrollTo()
        capture("display-test-dark.png")
        compose.onNodeWithText("Back to tests").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.notice_test_center)).performClick()
        compose.onNodeWithText(context.getString(R.string.notice_test_send_now)).performScrollTo().performClick()
        compose.waitUntil(8_000) { NoticeDisplayTests.observe(context, selected).active }
        compose.onNodeWithText(context.getString(R.string.notice_test_clear)).performScrollTo().performClick()
        compose.waitUntil { !NoticeDisplayTests.observe(context, selected).active }
        compose.onNode(hasText(context.getString(R.string.notice_test_feedback_center)) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil { NoticeDisplayTests.observe(context, selected).run?.feedback == NoticeDisplayFeedback.CenterOnly }
        compose.onNodeWithText(context.getString(R.string.notice_test_copy_result)).performScrollTo().performClick()
        compose.waitUntil {
            context.getSystemService(android.content.ClipboardManager::class.java).primaryClip
                ?.getItemAt(0)?.text?.contains("user.observed=CenterOnly") == true
        }
        capture("notice-comparison-result-dark.png")
        dark.value = false
        compose.waitForIdle()
        capture("notice-comparison-result-light.png")
    }

    private fun active(target: NoticeDisplayTarget) = manager.activeNotifications.first { it.id == target.notificationId }.notification
    private fun standardPromotionSupported(): Boolean = Notification.Builder(context, ClassNoticeNotifier.channelIdFor(NoticeDisplayTarget.Island.preferences(ClassNoticePreferences())))
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Platform eligibility probe")
        .setOngoing(true).addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        .setColorized(false).build().hasPromotableCharacteristics()
    private fun shell(command: String) = inst.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; Thread.sleep(80) }
        assertTrue("Expected system result was not observed", condition())
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
}
