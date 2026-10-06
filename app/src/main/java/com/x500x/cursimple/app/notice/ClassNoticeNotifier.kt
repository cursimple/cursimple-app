package com.x500x.cursimple.app.notice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import org.json.JSONObject

/** Deliver one non-ringing class notice across banner, lock-screen and chip presentations. */
object ClassNoticeNotifier {

    /**
     * Android 8+ heads-up behavior follows immutable channel importance; use separate high and
     * default channels.
     */
    const val CHANNEL_ID = "class_notice"

    /** Default-importance notices remain in the shade without a heads-up banner. */
    const val CHANNEL_ID_QUIET = "class_notice_quiet"

    private const val CHANNEL_ID_TEST_SOUND = "class_notice_test_sound"

    internal const val NOTIFICATION_ID = 0x0C1A

    internal const val ALARM_NOTIFICATION_ID = 0x0C20

    private const val SAMSUNG_AUTOMATION_EXTRA = "android.ongoingActivityNoti.automation"

    private val CLOCK_PATTERN = Regex("\\d{1,2}:\\d{2}")

    enum class Kind {
        Class,

        /** For pre-alarm content, title names the alarm and startAtMillis is its ring time. */
        AlarmPreview,
    }

    data class Content(
        val courseTitle: String,
        val location: String,
        val timeRange: String,
        val minutesUntilStart: Int,
        val startAtMillis: Long = 0L,
        val slotLabel: String = "",
        val endAtMillis: Long = 0L,
        /** Update to in-progress status without alerting again. */
        val inProgress: Boolean = false,
        /**
         * Optional headline override; components use their name instead of a course countdown.
         */
        val headline: String = "",
        val kind: Kind = Kind.Class,
    ) {
        val notificationId: Int
            get() = if (kind == Kind.AlarmPreview) ALARM_NOTIFICATION_ID else NOTIFICATION_ID

        fun subText(context: Context): String = when {
            headline.isNotBlank() -> headline
            kind == Kind.AlarmPreview -> context.getString(R.string.alarm_pre_notice_subtext, minutesUntilStart)
            inProgress -> context.getString(R.string.class_notice_subtext_in_progress)
            else -> context.getString(R.string.class_notice_subtext, minutesUntilStart)
        }

        /** Compact clock text for the status chip. */
        val startClock: String
            get() = CLOCK_PATTERN.find(timeRange)?.value ?: timeRange.substringBefore('-').trim()

        val whenText: String
            get() = listOf(slotLabel, timeRange).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** @param plain Developer preview without ongoing or promoted flags. */
    fun notify(
        context: Context,
        content: Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
        plain: Boolean = false,
        channelOverride: String? = null,
        forward: Boolean = false,
    ) {
        if (forward && !plain && channelOverride == null && !content.inProgress && content.headline.isBlank() && content.startAtMillis > 0 && content.kind == Kind.Class) {
            com.x500x.cursimple.core.data.notification.OutboundNotificationHooks.emit(
                com.x500x.cursimple.core.data.notification.OutboundNotification(
                    id = "class/${content.courseTitle}@${content.startAtMillis}", kind = "class",
                    title = content.courseTitle,
                    body = listOf(content.whenText, content.location, content.subText(context)).filter(String::isNotBlank).joinToString("\n"),
                    sourceId = "schedule", sourceName = context.getString(R.string.screen_schedule),
                    expiresAt = content.startAtMillis + 5 * 60_000L,
                ),
            )
        }
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        // Custom overlays supplement system delivery; suppress duplicate system banners. Overlay-only fallback is independent of notification permission; system-path diagnostics bypass it.
        val overlayFallback = preferences.headsUpEnabled && SelfDrawnNotice.only() &&
            !plain && channelOverride == null
        // Only use overlays while the screen is on to avoid expiring unseen banners.
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        val overlayTakesOver = !content.inProgress &&
            preferences.headsUpEnabled && !plain && channelOverride == null &&
            (preferences.skin == ClassNoticeSkin.Overlay || overlayFallback) &&
            screenOn &&
            ClassNoticeOverlay.canShowNow(context)
        if (overlayTakesOver) {
            ClassNoticeOverlay.show(context, content, preferences, theme)
        } else if (overlayFallback && !content.inProgress && ClassNoticeOverlay.canDraw(context)) {
            // Use the lock-screen Activity when overlays cannot be seen.
            ClassNoticeLockActivity.show(context, content, preferences, theme)
        }

        // Log delivery suppression with its cause.
        fun skip(reason: String) = ReminderLogger.info(
            "class_notice.post.skip",
            mapOf("reason" to reason, "kind" to content.kind, "inProgress" to content.inProgress),
        )
        if (!manager.areNotificationsEnabled()) {
            skip("notifications_disabled")
            return
        }
        // In-progress updates modify only an existing notification; never resurrect a dismissed one.
        val activeChannel = if (content.inProgress) {
            activeNoticeChannel(context) ?: run {
                skip("in_progress_dismissed")
                return
            }
        } else {
            null
        }
        // Recheck runtime notification permission because users can revoke it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            skip("no_post_permission")
            return
        }

        // Single-line fallback for vendor tickers and older layouts.
        val title = when {
            content.kind == Kind.AlarmPreview ->
                context.getString(R.string.alarm_pre_notice_title, content.minutesUntilStart, content.courseTitle)
            content.inProgress -> context.getString(R.string.class_notice_title_in_progress, content.courseTitle)
            else -> context.getString(R.string.class_notice_title, content.minutesUntilStart, content.courseTitle)
        }
        // Separate headline, course title and time/location to preserve title space.
        val subText = content.subText(context)
        val body = listOf(content.whenText, content.location)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val expanded = listOf(content.whenText, content.location)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val channelId = channelOverride ?: activeChannel ?: channelIdFor(preferences, overlayTakesOver)
        val builder = NotificationCompat.Builder(context, channelId)
            // Use a monochrome small icon with the app's theme color.
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(theme.primary)
            // Avoid repeating the large icon when the branded card already contains it.
            .setLargeIcon(
                if (preferences.skin == ClassNoticeSkin.Card) null else brandLogo(context),
            )
            .setSubText(subText)
            // Ticker text supports older vendor banners and accessibility announcements.
            .setTicker(title)
            .setContentTitle(content.courseTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(openApp)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.class_notice_action_open),
                openApp,
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.class_notice_action_dismiss),
                ClassNoticeScheduler.dismissIntent(context, content.notificationId),
            )
            .apply {
                if (content.kind == Kind.Class) {
                    addAction(
                        R.drawable.ic_notification,
                        context.getString(R.string.class_notice_action_snooze),
                        ClassNoticeScheduler.snoozeIntent(context, content),
                    )
                    addAction(
                        R.drawable.ic_notification,
                        context.getString(R.string.class_notice_action_skip),
                        ClassNoticeScheduler.skipIntent(context, content.notificationId),
                    )
                }
            }
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // Pre-Android-8 priority; newer systems use channel importance.
            .setPriority(
                if (preferences.headsUpEnabled && !overlayTakesOver) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            // Lock-screen visibility follows the user's content preference.
            .setVisibility(
                if (preferences.lockScreenEnabled) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
        // Mute via channel settings, not setSilent grouping, which can suppress heads-up alerts. Omit chronometers whose countdown could be confused with class times.
        builder.setShowWhen(false)
        // Keep when as a fallback for vendor chips that do not read shortCriticalText.
        if (!content.inProgress && content.startAtMillis > System.currentTimeMillis()) {
            builder.setWhen(content.startAtMillis)
        }
        // Expire class notices at class end and pre-alarm notices at ring time.
        val expireAt = content.endAtMillis.takeIf { it > 0L && content.kind == Kind.Class }
            ?: content.startAtMillis
        if (expireAt > 0L) {
            val remaining = expireAt - System.currentTimeMillis()
            if (remaining > 0L) builder.setTimeoutAfter(remaining)
        }
        // Use ongoing only where dismissible and supported; overlay-only vendor systems may suppress ongoing banners.
        val selfDrawn = SelfDrawnNotice.only()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !plain && !selfDrawn) {
            builder.setOngoing(true)
        }

        // Promotion requires system templates, taking priority over custom branded layouts.
        val promoted = preferences.focusNotificationEnabled && StatusBarChipSupport.allowed(context)

        // Android 12+ wraps custom notification content in a system header; blur and animation require overlays.
        if (preferences.skin == ClassNoticeSkin.Card && !promoted) {
            // Use a compact layout within the heads-up height budget.
            val compact = compactCardRemoteViews(
                context = context,
                theme = theme,
                title = content.courseTitle,
                body = listOf(subText, body).filter { it.isNotBlank() }.joinToString(" · "),
            )
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(compact)
                .setCustomHeadsUpContentView(compact)
                .setCustomBigContentView(
                    cardRemoteViews(context, theme, subText, content.courseTitle, expanded),
                )
        }

        // Skip promotion fields on overlay-only devices.
        if (preferences.focusNotificationEnabled && !plain && !selfDrawn) {
            val chipText = chipText(context, content)
            // Request live-update promotion even when disabled so later permission changes apply on refresh.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setRequestPromotedOngoing(true)
                    .setShortCriticalText(chipText)
                // Use Samsung's standard promotion marker; its private style field selects a different, non-promoted path.
                builder.addExtras(Bundle().apply { putBoolean(SAMSUNG_AUTOMATION_EXTRA, true) })
            }
            builder.addExtras(
                miuiFocusExtras(
                    context = context,
                    ticker = "$chipText ${content.courseTitle}",
                    frontTitle = if (content.inProgress) chipText else subText,
                    content = content,
                    body = body,
                    float = preferences.headsUpEnabled && !overlayTakesOver && !content.inProgress,
                ),
            )
        }

        // Compare requested delivery characteristics with the post-delivery system result.
        val request = mapOf(
            "kind" to content.kind,
            "inProgress" to content.inProgress,
            "channel" to channelId,
            "overlayTakesOver" to overlayTakesOver,
            "overlayFallback" to overlayFallback,
            "promoted" to promoted,
            "skin" to preferences.skin,
            "headsUp" to preferences.headsUpEnabled,
        )
        ReminderLogger.info("class_notice.post.request", request)
        val notification = builder.build()
        runCatching { manager.notify(content.notificationId, notification) }
            .onSuccess { ClassNoticeDiagnostics.logPosted(context, content.notificationId, request) }
            .onFailure { ReminderLogger.warn("class_notice.post.failure", request, it) }
    }

    /**
     * Use short clock text within the chip's 96dp budget; longer labels may collapse to an
     * icon.
     */
    fun chipText(context: Context, content: Content): String = when {
        content.kind == Kind.AlarmPreview -> context.getString(R.string.alarm_pre_notice_chip, content.startClock)
        content.inProgress -> context.getString(R.string.class_notice_chip_in_progress)
        else -> context.getString(R.string.class_notice_chip_upcoming, content.startClock)
    }

    fun notifyPlainTest(context: Context, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        cancel(context)
        notify(context, previewContent(context, preferences), preferences, theme, plain = true)
    }

    /**
     * Diagnostic high-importance channel with sound and vibration isolates silent-channel
     * suppression.
     */
    fun notifySoundTest(context: Context, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID_TEST_SOUND) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_TEST_SOUND,
                        context.getString(R.string.class_notice_channel_test_sound_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { enableVibration(true) },
                )
            }
        }
        cancel(context)
        notify(
            context,
            previewContent(context, preferences),
            preferences,
            theme,
            plain = true,
            channelOverride = CHANNEL_ID_TEST_SOUND,
        )
    }

    fun previewContent(context: Context, preferences: ClassNoticePreferences): Content = Content(
        courseTitle = context.getString(R.string.class_notice_preview_course),
        location = context.getString(R.string.class_notice_preview_location),
        timeRange = "19:00-20:35",
        minutesUntilStart = preferences.advanceMinutes,
        slotLabel = context.getString(R.string.class_notice_preview_slot),
    )

    /** Preview through the real delivery path; zero start time disables immediate expiry. */
    fun notifyPreview(context: Context, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        // Remove the previous preview so repeated taps can alert again.
        cancel(context)
        notify(
            context = context,
            content = previewContent(context, preferences),
            preferences = preferences,
            theme = theme,
        )
        hintChipHiddenInForeground(context, preferences)
    }

    fun notifyPreview(
        context: Context,
        content: Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
    ) {
        cancel(context)
        notify(context = context, content = content, preferences = preferences, theme = theme)
        hintChipHiddenInForeground(context, preferences)
    }

    /** Explain that AOSP hides the chip while this app is visible or the screen is locked. */
    private fun hintChipHiddenInForeground(context: Context, preferences: ClassNoticePreferences) {
        if (!preferences.focusNotificationEnabled) return
        if (StatusBarChipSupport.level() == StatusBarChipSupport.Level.Unsupported) return
        if (!ClassNoticeDiagnostics.isForeground()) return
        Toast.makeText(
            context,
            context.getString(R.string.class_notice_preview_chip_hint),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun cancel(context: Context, notificationId: Int = NOTIFICATION_ID) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    fun cancelAlarmPreview(context: Context) = cancel(context, ALARM_NOTIFICATION_ID)

    fun alarmPreviewSample(context: Context, minutes: Int): Content {
        val ringAt = System.currentTimeMillis() + minutes * 60_000L
        val clock = java.time.Instant.ofEpochMilli(ringAt)
            .atZone(com.x500x.cursimple.core.kernel.time.BeijingTime.zone)
            .toLocalTime()
        val text = "%02d:%02d".format(clock.hour, clock.minute)
        return Content(
            courseTitle = context.getString(R.string.alarm_pre_notice_preview_title),
            location = "",
            timeRange = context.getString(R.string.alarm_pre_notice_ring_at, text),
            minutesUntilStart = minutes,
            startAtMillis = 0L,
            kind = Kind.AlarmPreview,
        )
    }

    private fun activeNoticeChannel(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return null
        val notice = active.firstOrNull { it.id == NOTIFICATION_ID } ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notice.notification.channelId else CHANNEL_ID
    }

    private fun cardRemoteViews(
        context: Context,
        theme: NoticeTheme,
        subText: String,
        title: String,
        body: String,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_class_notice_card).apply {
        applyCardBackground(theme)
        setTextViewText(R.id.class_notice_card_subtext, subText)
        setTextViewText(R.id.class_notice_card_title, title)
        setTextViewText(R.id.class_notice_card_body, body)
    }

    private fun compactCardRemoteViews(
        context: Context,
        theme: NoticeTheme,
        title: String,
        body: String,
    ): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.notification_class_notice_card_compact,
    ).apply {
        applyCardBackground(theme)
        setTextViewText(R.id.class_notice_card_title, title)
        setTextViewText(R.id.class_notice_card_body, body)
    }

    /**
     * Tint rounded card backgrounds on Android 12+; use the closest resource color on older
     * systems.
     */
    private fun RemoteViews.applyCardBackground(theme: NoticeTheme) {
        val tint = theme.cardTintArgb
        if (tint != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setInt(R.id.class_notice_card_root, "setBackgroundResource", R.drawable.bg_class_notice_card_tintable)
            setColorStateList(R.id.class_notice_card_root, "setBackgroundTintList", ColorStateList.valueOf(tint))
        } else {
            setInt(R.id.class_notice_card_root, "setBackgroundResource", theme.cardBackgroundRes)
        }
    }

    /** Cache resource-derived logo cropping once per process. */
    @Volatile
    private var brandLogoCache: Bitmap? = null

    /**
     * Crop the adaptive foreground to its nontransparent bounds before using it as a large
     * icon.
     */
    private fun brandLogo(context: Context): Bitmap? {
        brandLogoCache?.let { return it }
        return runCatching {
            val source = BitmapFactory.decodeResource(
                context.resources,
                R.mipmap.ic_launcher_foreground,
            ) ?: return null
            source.cropToOpaqueBounds().also { brandLogoCache = it }
        }.getOrNull()
    }

    private fun Bitmap.cropToOpaqueBounds(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) <= 24) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return this
        return Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * Vendor param_v2 promotion extras; unrecognized keys are ignored elsewhere. Include ticker
     * for older supported systems.
     */
    private fun miuiFocusExtras(
        context: Context,
        ticker: String,
        frontTitle: String,
        content: Content,
        body: String,
        float: Boolean,
    ): Bundle {
        val pic = "miui.focus.pic_app"
        val picInfo = JSONObject().put("type", 1).put("pic", pic)
        val param = JSONObject()
            .put("protocol", 1)
            .put("business", "class_notice")
            .put("enableFloat", float)
            .put("updatable", true)
            .put("ticker", ticker)
            .put("tickerPic", pic)
            .put("aodTitle", ticker)
            .put("aodPic", pic)
            .put(
                "param_island",
                JSONObject()
                    .put("islandProperty", 1)
                    .put(
                        "bigIslandArea",
                        JSONObject().put(
                            "imageTextInfoLeft",
                            JSONObject()
                                .put("type", 1)
                                .put("picInfo", picInfo)
                                .put(
                                    "textInfo",
                                    JSONObject()
                                        .put("frontTitle", frontTitle)
                                        .put("title", content.courseTitle)
                                        .put("content", content.location.ifBlank { content.whenText }),
                                ),
                        ),
                    )
                    .put("smallIslandArea", JSONObject().put("picInfo", picInfo)),
            )
            .put(
                "baseInfo",
                JSONObject()
                    .put("title", content.courseTitle)
                    .put("content", body)
                    .put("type", 2),
            )
        return Bundle().apply {
            putString("miui.focus.param", JSONObject().put("param_v2", param).toString())
            putBundle(
                "miui.focus.pics",
                Bundle().apply {
                    putParcelable(pic, Icon.createWithResource(context, R.mipmap.ic_launcher))
                },
            )
        }
    }

    /**
     * Query focus or live-update permission when available; otherwise allow ordinary
     * notification fallback.
     */
    fun islandBlocked(context: Context): Boolean {
        if (miuiFocusProtocol(context) > 0) {
            val allowed = runCatching {
                context.contentResolver.call(
                    Uri.parse("content://miui.statusbar.notification.public"),
                    "canShowFocus",
                    null,
                    Bundle().apply { putString("package", context.packageName) },
                )?.getBoolean("canShowFocus", false)
            }.getOrNull()
            if (allowed == false) return true
        }
        if (StatusBarChipSupport.level() == StatusBarChipSupport.Level.PlatformDecides) {
            return !StatusBarChipSupport.allowed(context)
        }
        return false
    }

    /** Whether the device supports focus or live-update chips. */
    fun islandAvailable(context: Context): Boolean =
        miuiFocusProtocol(context) > 0 ||
            StatusBarChipSupport.level() != StatusBarChipSupport.Level.Unsupported

    internal fun miuiFocusProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    }.getOrDefault(0)

    /**
     * Channel importance is immutable after creation; delivery uses configured channels and
     * user settings.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannel(
            context,
            manager,
            CHANNEL_ID,
            R.string.class_notice_channel_name,
            NotificationManager.IMPORTANCE_HIGH,
        )
        createChannel(
            context,
            manager,
            CHANNEL_ID_QUIET,
            R.string.class_notice_channel_quiet_name,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    }

    fun channelIdFor(
        preferences: ClassNoticePreferences,
        overlayTakesOver: Boolean = false,
    ): String = if (preferences.headsUpEnabled && !overlayTakesOver) CHANNEL_ID else CHANNEL_ID_QUIET

    private fun createChannel(
        context: Context,
        manager: NotificationManager,
        id: String,
        nameRes: Int,
        importance: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(
            id,
            context.getString(nameRes),
            importance,
        ).apply {
            description = context.getString(R.string.class_notice_channel_desc)
            setShowBadge(false)
            // Class notices use no sound or vibration.
            setSound(null, null)
            enableVibration(false)
            // Leave channel visibility unspecified so per-notification privacy changes remain effective.
        }
        manager.createNotificationChannel(channel)
    }

    fun systemBlocked(context: Context, preferences: ClassNoticePreferences): Boolean =
        masterNotificationsBlocked(context) || channelBlocked(context, preferences)

    fun masterNotificationsBlocked(context: Context): Boolean =
        !NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channelBlocked(context: Context, preferences: ClassNoticePreferences): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelIdFor(preferences))
            ?: return false
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT
    }
}
