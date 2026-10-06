package com.x500x.cursimple.app.notice

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * Inspect channels, promotion and vendor status together. Stable English field names keep
 * copied reports comparable to logs.
 */
object ClassNoticeDiagnostics {

    private val ROM_PROPS = listOf(
        "ro.build.version.oplusrom",
        "ro.build.version.opporom",
        "ro.miui.ui.version.name",
        "ro.mi.os.version.name",
        // Use numeric HyperOS version codes because version names contain prefixes.
        "ro.mi.os.version.code",
        "ro.vivo.os.version",
        "ro.build.version.magic",
        "ro.build.version.oneui",
    )

    // Some Android 36 SDK extras remain hidden; use their documented string keys.
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Report promotion extras, abbreviating large vendor payloads to presence checks. */
    private val EXTRAS_OF_INTEREST = listOf(
        EXTRA_SHORT_CRITICAL_TEXT,
        EXTRA_REQUEST_PROMOTED_ONGOING,
        "android.ongoingActivityNoti.automation",
    )
    private const val EXTRA_MIUI_FOCUS = "miui.focus.param"

    /** Default channel visibility sentinel is hidden in NotificationManager. */
    private const val VISIBILITY_NO_OVERRIDE = -1000

    /** Wait after notify because system registration happens asynchronously. */
    private const val POST_CHECK_DELAY_MS = 1_000L

    /**
     * Allow timestamp tolerance when distinguishing current delivery from a stale active
     * notification.
     */
    private const val STALE_SLACK_MS = 5_000L

    fun report(context: Context, preferences: ClassNoticePreferences): List<Pair<String, String>> {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        return buildList {
            add("device" to "${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL}")
            add("android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            add("build.display" to Build.DISPLAY.orEmpty())
            // Android 16.0 and QPR1 use different colorization rules for promotion.
            add("build.id" to Build.ID.orEmpty())
            ROM_PROPS.forEach { key -> StatusBarChipSupport.prop(key)?.let { add(key to it) } }
            add("vendor" to VendorRom.current().name)

            add("notifications.enabled" to NotificationManagerCompat.from(app).areNotificationsEnabled().toString())
            add("permission.post_notifications" to postPermissionState(app))
            listOf(ClassNoticeNotifier.CHANNEL_ID, ClassNoticeNotifier.CHANNEL_ID_QUIET).forEach { id ->
                addAll(channelState(manager, id))
            }
            add("channel.selected" to ClassNoticeNotifier.channelIdFor(preferences))
            if (preferences.skin == ClassNoticeSkin.Overlay) {
                add("channel.selected_overlay" to ClassNoticeNotifier.channelIdFor(preferences, overlayTakesOver = true))
            }

            add("pref.enabled" to preferences.enabled.toString())
            add("pref.heads_up" to preferences.headsUpEnabled.toString())
            add("pref.lock_screen" to preferences.lockScreenEnabled.toString())
            add("pref.focus" to preferences.focusNotificationEnabled.toString())
            add("pref.skin" to preferences.skin.name)

            add("chip.level" to StatusBarChipSupport.level().name)
            add("chip.allowed" to StatusBarChipSupport.allowed(app).toString())
            canPostPromoted(manager)?.let { add("promoted.can_post" to it.toString()) }
            add("miui.focus_protocol" to ClassNoticeNotifier.miuiFocusProtocol(app).toString())
            add("island.available" to ClassNoticeNotifier.islandAvailable(app).toString())
            add("island.blocked" to ClassNoticeNotifier.islandBlocked(app).toString())

            add("overlay.can_draw" to Settings.canDrawOverlays(app).toString())
            add("self_drawn.reason" to (SelfDrawnNotice.reason()?.name ?: "none"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && VendorRom.current() == VendorRom.Vivo) {
                // Include raw vendor channel fields when public importance alone cannot explain suppression.
                listOf(ClassNoticeNotifier.CHANNEL_ID, ClassNoticeNotifier.CHANNEL_ID_QUIET).forEach { id ->
                    runCatching { manager?.getNotificationChannel(id) }.getOrNull()?.let { channel ->
                        add("vivo.channel.$id.raw" to channel.toString())
                    }
                }
            }
            add(
                "dnd.filter" to interruptionFilterLabel(
                    runCatching { manager?.currentInterruptionFilter }.getOrNull(),
                ),
            )
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            // Record secure-lock state because swipe-only locks can display notifications differently.
            add("keyguard.secure" to (keyguard?.isDeviceSecure?.toString() ?: "unknown"))
            add("keyguard.locked" to (keyguard?.isKeyguardLocked?.toString() ?: "unknown"))
            // System lock-screen privacy overrides per-notification visibility; omit unavailable status.
            secureInt(app, "lock_screen_show_notifications")?.let { add("lockscreen.show_notifications" to it.toString()) }
            secureInt(app, "lock_screen_allow_private_notifications")?.let {
                add("lockscreen.allow_private" to it.toString())
            }

            val classNotice = findActive(manager, ClassNoticeNotifier.NOTIFICATION_ID)
            if (classNotice == null) {
                add("active.class" to "none")
            } else {
                summarize(classNotice).forEach { (key, value) -> add("active.class.$key" to value) }
            }
            // Include pre-alarm state only while its notice is active.
            findActive(manager, ClassNoticeNotifier.ALARM_NOTIFICATION_ID)?.let { alarm ->
                summarize(alarm).forEach { (key, value) -> add("active.alarm_preview.$key" to value) }
            }
        }
    }

    fun asText(lines: List<Pair<String, String>>): String =
        lines.joinToString("\n") { (key, value) -> "$key: $value" }

    /**
     * Inspect activeNotifications after delivery; notify returning normally does not confirm
     * visible registration.
     */
    fun logPosted(context: Context, notificationId: Int, extra: Map<String, Any?>) {
        val app = context.applicationContext
        val postedAt = System.currentTimeMillis()
        // Capture screen, lock and foreground state at send time for diagnostics.
        val moment = mapOf(
            "at.interactive" to runCatching { app.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull(),
            "at.locked" to runCatching { app.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked }.getOrNull(),
            "at.foreground" to isForeground(),
        )
        Handler(Looper.getMainLooper()).postDelayed(
            {
                runCatching {
                    val manager = app.getSystemService(NotificationManager::class.java)
                    val common = extra + moment + mapOf(
                        "id" to notificationId,
                        "importance.${ClassNoticeNotifier.CHANNEL_ID}" to
                            channelImportance(manager, ClassNoticeNotifier.CHANNEL_ID),
                        "importance.${ClassNoticeNotifier.CHANNEL_ID_QUIET}" to
                            channelImportance(manager, ClassNoticeNotifier.CHANNEL_ID_QUIET),
                        "promotedAllowed" to StatusBarChipSupport.allowed(app),
                        "canPostPromoted" to canPostPromoted(manager),
                    )
                    val posted = findActive(manager, notificationId)
                    if (posted == null || posted.postTime < postedAt - STALE_SLACK_MS) {
                        ReminderLogger.warn("class_notice.post.missing", common + mapOf("stale" to (posted != null)))
                    } else {
                        val summary = summarize(posted).associate { (key, value) -> "active.$key" to value }
                        ReminderLogger.info("class_notice.post.result", common + summary)
                    }
                }.onFailure { ReminderLogger.warn("class_notice.post.check_failed", emptyMap(), it) }
            },
            POST_CHECK_DELAY_MS,
        )
    }

    /** AOSP hides the chip while its source app is visible; preview it from the launcher. */
    fun isForeground(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    private fun findActive(manager: NotificationManager?, id: Int): StatusBarNotification? =
        runCatching { manager?.activeNotifications }.getOrNull()?.firstOrNull { it.id == id }

    @Suppress("DEPRECATION")
    private fun summarize(sbn: StatusBarNotification): List<Pair<String, String>> {
        val notification = sbn.notification
        val flags = notification.flags
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add("channel" to notification.channelId.orEmpty())
            }
            add("flags" to flagsHex(flags))
            add("ongoing" to ((flags and Notification.FLAG_ONGOING_EVENT) != 0).toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                // System promotion flags confirm eligibility and permission together.
                add("promoted_flag" to ((flags and Notification.FLAG_PROMOTED_ONGOING) != 0).toString())
                add("promotable" to runCatching { notification.hasPromotableCharacteristics() }.getOrNull().toString())
                // Report failed promotion characteristics using the platform's eligibility rules.
                val extras = notification.extras
                add("promo.requested" to (extras?.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING) == true).toString())
                add("promo.has_title" to (!extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty()).toString())
                add("promo.colorized" to (extras?.getBoolean(Notification.EXTRA_COLORIZED) == true).toString())
                add("promo.group_summary" to ((flags and Notification.FLAG_GROUP_SUMMARY) != 0).toString())
            }
            val customViews = listOfNotNull(
                "content".takeIf { notification.contentView != null },
                "big".takeIf { notification.bigContentView != null },
                "heads_up".takeIf { notification.headsUpContentView != null },
                "public".takeIf {
                    notification.publicVersion?.let { it.contentView ?: it.bigContentView ?: it.headsUpContentView } != null
                },
            )
            add("custom_views" to customViews.joinToString(",").ifEmpty { "none" })
            add(
                "template" to notification.extras?.getString(Notification.EXTRA_TEMPLATE)
                    ?.substringAfterLast('$').orEmpty().ifEmpty { "none" },
            )
            add("visibility" to visibilityLabel(notification.visibility))
            add("category" to notification.category.orEmpty().ifEmpty { "none" })
            add("group" to notification.group.orEmpty().ifEmpty { "none" })
            val extras = notification.extras
            EXTRAS_OF_INTEREST.forEach { key ->
                val value = extras?.get(key)
                add("extra.$key" to (value?.toString() ?: "absent"))
            }
            add("extra.$EXTRA_MIUI_FOCUS" to if (extras?.containsKey(EXTRA_MIUI_FOCUS) == true) "present" else "absent")
            add("post_age_ms" to (System.currentTimeMillis() - sbn.postTime).toString())
        }
    }

    private fun channelState(manager: NotificationManager?, id: String): List<Pair<String, String>> {
        val prefix = "channel.$id"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return listOf(prefix to "n/a (<26)")
        val channel = runCatching { manager?.getNotificationChannel(id) }.getOrNull()
            ?: return listOf(prefix to "absent")
        return listOf(
            "$prefix.importance" to importanceLabel(channel.importance),
            "$prefix.lockscreen" to visibilityLabel(channel.lockscreenVisibility),
            "$prefix.group_blocked" to groupBlocked(manager, channel),
        )
    }

    private fun groupBlocked(manager: NotificationManager?, channel: NotificationChannel): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "n/a"
        val group = channel.group ?: return "no_group"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a (<28)"
        return runCatching { manager?.getNotificationChannelGroup(group)?.isBlocked }
            .getOrNull()?.toString() ?: "unknown"
    }

    private fun channelImportance(manager: NotificationManager?, id: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "n/a"
        val channel = runCatching { manager?.getNotificationChannel(id) }.getOrNull() ?: return "absent"
        return importanceLabel(channel.importance)
    }

    private fun canPostPromoted(manager: NotificationManager?): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return runCatching { manager?.canPostPromotedNotifications() }.getOrNull()
    }

    private fun postPermissionState(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "n/a (<33)"
        return (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ).toString()
    }

    private fun secureInt(context: Context, key: String): Int? = runCatching {
        Settings.Secure.getInt(context.contentResolver, key)
    }.getOrNull()

    internal fun flagsHex(flags: Int): String = "0x" + Integer.toHexString(flags)

    internal fun importanceLabel(importance: Int): String {
        val name = when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "NONE"
            NotificationManager.IMPORTANCE_MIN -> "MIN"
            NotificationManager.IMPORTANCE_LOW -> "LOW"
            NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
            NotificationManager.IMPORTANCE_HIGH -> "HIGH"
            NotificationManager.IMPORTANCE_MAX -> "MAX"
            NotificationManager.IMPORTANCE_UNSPECIFIED -> "UNSPECIFIED"
            else -> return importance.toString()
        }
        return "$importance($name)"
    }

    internal fun visibilityLabel(visibility: Int): String {
        val name = when (visibility) {
            Notification.VISIBILITY_PUBLIC -> "PUBLIC"
            Notification.VISIBILITY_PRIVATE -> "PRIVATE"
            Notification.VISIBILITY_SECRET -> "SECRET"
            VISIBILITY_NO_OVERRIDE -> "NO_OVERRIDE"
            else -> return visibility.toString()
        }
        return "$visibility($name)"
    }

    /** Without policy access, DND status is unknown rather than disabled. */
    internal fun interruptionFilterLabel(filter: Int?): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE"
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "UNKNOWN"
        null -> "unreadable"
        else -> filter.toString()
    }
}
