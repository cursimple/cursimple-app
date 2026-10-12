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
            add("app.package" to app.packageName)
            val guard = app.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE)
            add("guard.foreground_enabled" to com.x500x.cursimple.app.reminder.ReminderGuardForegroundService.isEnabled(app).toString())
            add("guard.foreground_state" to com.x500x.cursimple.app.reminder.ReminderGuardForegroundService.state.value)
            add("guard.last_success" to guard.getLong("last_success", 0).toString())
            add("guard.next_elapsed" to guard.getLong("next_elapsed", 0).toString())
            addAll(com.x500x.cursimple.app.vendor.VendorCourseStore.statuses(app).map { "vendor_service.${it.first}" to it.second })
            addAll(com.x500x.cursimple.app.vendor.VendorCourseStore.diagnosticDetails(app).map { "vendor_service.${it.first}" to it.second })
            val vendorConfig = com.x500x.cursimple.app.vendor.VendorCourseStore.config(app)
            add("vendor_service.enabled" to vendorConfig.enabled.toString())
            add("vendor_service.aod" to vendorConfig.aodEnabled.toString())
            runCatching {
                val signatures = if (Build.VERSION.SDK_INT >= 28) app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
                    else { @Suppress("DEPRECATION") app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty() }
                signatures.forEachIndexed { index, signature ->
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02X".format(it) }
                    add("app.certificate_sha256.$index" to digest)
                }
            }


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
            NoticeDisplayTarget.entries.forEach { target ->
                val observation = NoticeDisplayTests.observe(app, target)
                val prefix = "test.${target.name.lowercase()}"
                add("$prefix.state" to (observation.run?.phase?.name ?: "idle"))
                observation.run?.let { run ->
                    add("$prefix.style" to run.style.name)
                    add("$prefix.step" to run.step.name)
                    add("$prefix.user_observed" to run.feedback.name)
                    add("$prefix.request.vivo_atomic" to run.requestedVivo.toString())
                    add("$prefix.request.native_live" to run.requestedNative.toString())
                    add("$prefix.system.first_seen" to run.firstRegisteredAt.toString())
                    run.receipt?.let { receipt ->
                        add("$prefix.last_receipt" to "at=${receipt.checkedAt},active=${receipt.active},promoted=${receipt.promoted},vivo_fields=${receipt.vivoExtrasPresent},vivo_tag=${receipt.vivoTagged},identity=${receipt.identityPresent}")
                    }
                    add("$prefix.sent_at" to run.sentAt.toString())
                    add("$prefix.interactive_at_send" to run.interactive.toString())
                    add("$prefix.locked_at_send" to run.locked.toString())
                    add("$prefix.exact" to run.exact.toString())
                }
                findActive(manager, target.notificationId)?.let { notification ->
                    summarize(notification).forEach { (key, value) -> add("$prefix.active.$key" to value) }
                }
            }
            NoticeDisplayTests.history(app).forEachIndexed { index, run ->
                add("test.history.$index" to "${run.target}/${run.style}/${run.sentAt}/user:${run.feedback}")
            }
        }
    }

    fun asText(lines: List<Pair<String, String>>): String =
        lines.joinToString("\n") { (key, value) -> "$key: $value" }

    /** Capture request fields before system normalization. */
    fun requestFields(notification: Notification): Map<String, Any?> {
        val extra = notification.extras
        return mapOf(
            "request.native_live" to extra.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING),
            "request.vivo_atomic" to extra.containsKey(VivoAtomicNotice.OPERATION),
            "request.vivo_small_card" to (extra.getBundle(VivoAtomicNotice.SHORT_INFO) != null),
            "request.vivo_island" to (extra.getBundle(VivoAtomicNotice.ISLAND) != null),
            "request.vivo_capsule" to (extra.getBundle(VivoAtomicNotice.CAPSULE) != null),
            "request.vivo_displays" to "0x${Integer.toHexString(extra.getInt(VivoAtomicNotice.DISPLAYS))}",
            "request.course_identity" to (extra.containsKey(ClassNoticeNotifier.EXTRA_COURSE_START) && extra.containsKey(ClassNoticeNotifier.EXTRA_COURSE_TITLE)),
        )
    }

    /** Inspect the actual system record separately from the request. */
    fun logPosted(context: Context, notificationId: Int, extra: Map<String, Any?>, ownerStartAt: Long) {
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
                    val stale = posted != null && (posted.postTime < postedAt - STALE_SLACK_MS ||
                        (posted.notification.extras.containsKey(ClassNoticeNotifier.EXTRA_COURSE_START) &&
                            posted.notification.extras.getLong(ClassNoticeNotifier.EXTRA_COURSE_START) != ownerStartAt))
                    val accepted = posted?.takeUnless { stale }
                    val notification = accepted?.notification
                    NoticeDisplayTests.recordReceipt(app, notificationId, ownerStartAt,
                        extra["request.vivo_atomic"] == true, extra["request.native_live"] == true,
                        NoticeSystemReceipt(System.currentTimeMillis(), accepted != null,
                            Build.VERSION.SDK_INT >= 36 && notification != null && notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0,
                            notification?.extras?.containsKey(VivoAtomicNotice.OPERATION) == true,
                            accepted?.tag == VivoAtomicNotice.TAG,
                            notification?.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty(),
                            notification?.extras?.containsKey(ClassNoticeNotifier.EXTRA_COURSE_START) == true &&
                                notification.extras.containsKey(ClassNoticeNotifier.EXTRA_COURSE_TITLE)))
                    if (posted == null || stale) {
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
            add("tag" to sbn.tag.orEmpty().ifBlank { "none" })
            add("course_identity_retained" to (notification.extras.containsKey(ClassNoticeNotifier.EXTRA_COURSE_START) && notification.extras.containsKey(ClassNoticeNotifier.EXTRA_COURSE_TITLE)).toString())
            add("vivo.atomic_tag" to (sbn.tag == VivoAtomicNotice.TAG).toString())
            if (notification.extras.containsKey(VivoAtomicNotice.OPERATION)) {
                add("vivo.atomic_operation" to notification.extras.getInt(VivoAtomicNotice.OPERATION).toString())
                add("vivo.atomic_displays" to "0x${Integer.toHexString(notification.extras.getInt(VivoAtomicNotice.DISPLAYS))}")
                add("vivo.atomic_scene" to notification.extras.getString("notification.superx.scene").orEmpty())
                val short = notification.extras.getBundle(VivoAtomicNotice.SHORT_INFO)
                add("vivo.aod_has_course" to (!short?.getString("notification.superx.shortInfos.coreInfoShort").isNullOrBlank()).toString())
                add("vivo.has_island" to (notification.extras.getBundle(VivoAtomicNotice.ISLAND) != null).toString())
                add("vivo.has_capsule" to (notification.extras.getBundle(VivoAtomicNotice.CAPSULE) != null).toString())
            }
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
