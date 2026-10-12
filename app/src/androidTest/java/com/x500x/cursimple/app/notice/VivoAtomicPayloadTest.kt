package com.x500x.cursimple.app.notice

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.ThemeAccent
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

/** Protocol and lifecycle checks; AOSP cannot render vivo surfaces. */
class VivoAtomicPayloadTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val id = 0x0D51
    private val sample get() = ClassNoticeNotifier.Content("Math", "Building A-301", "09:00–10:00", 1,
        startAtMillis = System.currentTimeMillis() + 60_000, endAtMillis = System.currentTimeMillis() + 120_000, notificationIdOverride = id)

    @Before fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vivoAtomicQa") == "true")
        ClassNoticeNotifier.ensureChannel(context)
        ClassNoticeNotifier.cancel(context, id)
    }
    @After fun clear() {
        if (InstrumentationRegistry.getArguments().getString("vivoAtomicQa") == "true") ClassNoticeNotifier.cancel(context, id)
    }
    private fun notification() = NotificationCompat.Builder(context, ClassNoticeNotifier.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Fixture").setTimeoutAfter(30_000).build()

    @Test fun parcelledPayloadContainsCourseContentForAllVendorSurfaces() {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val original = VivoAtomicNotice.extras(context, sample, NoticeTheme.of(ThemeAccent.Green, false), open, true, true, System.currentTimeMillis()+120_000)
        val parcel = Parcel.obtain()
        val extras = try { parcel.writeBundle(original); parcel.setDataPosition(0); parcel.readBundle(context.classLoader)!! } finally { parcel.recycle() }
        assertEquals(0, extras.getInt("notification.superx.operation"))
        assertEquals("METTING", extras.getString("notification.superx.scene"))
        assertEquals(0x10111, extras.getInt("notification.superx.displays"))
        assertTrue(extras.getBoolean("notification.superx.showNotify"))
        assertFalse(extras.getBoolean("android.requestPromotedOngoing"))
        assertFalse(extras.getBoolean("notification.superx.sound"))
        val small = extras.getBundle("notification.superx.shortInfos")!!
        assertEquals("Math", small.getString("notification.superx.shortInfos.coreInfoShort"))
        assertTrue(small.getString("notification.superx.shortInfos.describeShort")!!.contains("Building A-301"))
        assertNotNull(small.getParcelable<PendingIntent>("notification.superx.shortInfos.imageClickResp"))
        assertEquals(1, extras.getBundle("notification.superx.capsule")!!.getInt("notification.superx.capsule.state"))
        val island = extras.getBundle("notification.superx.island")!!
        assertEquals("Math", island.getBundle("island.superx.leftInfo")!!.getCharSequence("island.superx.leftInfo.content").toString())
        assertEquals("09:00", island.getBundle("island.superx.rightInfo")!!.getCharSequence("island.superx.rightInfo.content").toString())
        assertEquals(1, VivoAtomicNotice.extras(context, sample.copy(inProgress = true), NoticeTheme.of(ThemeAccent.Green, false), open, true, true, System.currentTimeMillis()+120_000).getInt("notification.superx.operation"))
    }

    @Test fun clearingANotificationRemovesBothOrdinaryAndVendorTaggedRecords() {
        manager.notify(id, notification())
        manager.notify(VivoAtomicNotice.TAG, id, notification())
        waitFor { manager.activeNotifications.count { it.id == id } == 2 }
        ClassNoticeNotifier.cancel(context, id)
        waitFor { manager.activeNotifications.none { it.id == id } }
    }

    @Test fun expirySurvivesADelayedCallbackWithoutRemovingTheNextCourse() {
        val old = sample
        val oldToken = NativeNoticeExpiry.register(context, old, System.currentTimeMillis() + 200)
        val next = old.copy(courseTitle = "Next", startAtMillis = old.startAtMillis + 60_000)
        manager.notify(VivoAtomicNotice.TAG, id, notification())
        NativeNoticeExpiry.register(context, next, System.currentTimeMillis() + 2_000)
        Thread.sleep(350)
        ClassNoticeNotifier.cancelExpiredNative(context, id, oldToken)
        assertTrue(NativeNoticeExpiry.owns(context, next))
        assertTrue(manager.activeNotifications.any { it.id == id })
        waitFor { manager.activeNotifications.none { it.id == id } }
        assertFalse(NativeNoticeExpiry.owns(context, next))
    }
    private fun waitFor(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis()+12_000
        while (SystemClock.uptimeMillis()<end) { if (condition()) return; Thread.sleep(60) }
        assertTrue("Expected system record state was not observed", condition())
    }
}
