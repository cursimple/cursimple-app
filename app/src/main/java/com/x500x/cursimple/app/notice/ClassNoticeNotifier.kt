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

/**
 * 上课通知的投递。
 *
 * 只发一条通知——悬浮横幅、锁屏显示、原子岛都是同一条通知的不同呈现方式，
 * 不响铃、不接管屏幕，和提醒规则那套响铃闹钟（AlarmRingingService）完全分开。
 */
object ClassNoticeNotifier {

    /**
     * 悬浮那一档的渠道（IMPORTANCE_HIGH）。
     *
     * 为什么要两个渠道：API 26 起「要不要从顶部滑出来」只看渠道的 importance，
     * setPriority 从那时候起就被忽略了。想让「悬浮通知」开关真的生效，
     * 就只能备两个 importance 不同的渠道，发的时候挑一个。
     */
    const val CHANNEL_ID = "class_notice"

    /** 不悬浮那一档的渠道（IMPORTANCE_DEFAULT）：通知照进通知栏和锁屏，只是不弹横幅。 */
    const val CHANNEL_ID_QUIET = "class_notice_quiet"

    /** 高级测试用的有声渠道，见 [notifySoundTest] */
    private const val CHANNEL_ID_TEST_SOUND = "class_notice_test_sound"

    internal const val NOTIFICATION_ID = 0x0C1A

    /** 闹钟预告单独一个 id：和上课通知同时挂着时互不覆盖。 */
    internal const val ALARM_NOTIFICATION_ID = 0x0C20

    private const val SAMSUNG_AUTOMATION_EXTRA = "android.ongoingActivityNoti.automation"

    private val CLOCK_PATTERN = Regex("\\d{1,2}:\\d{2}")

    /** 这条通知在提醒什么。 */
    enum class Kind {
        /** 快上课了 */
        Class,

        /** 闹钟快响了：[Content.courseTitle] 是闹钟名，[Content.startAtMillis] 是响铃时刻 */
        AlarmPreview,
    }

    /** 一条上课通知要显示的内容。 */
    data class Content(
        val courseTitle: String,
        val location: String,
        val timeRange: String,
        val minutesUntilStart: Int,
        /** 上课时刻，用来做倒计时和到点自动消失；拿不到就传 0 */
        val startAtMillis: Long = 0L,
        /** 节次名字，如「第一节」「午间课」；作息表里没有时为空 */
        val slotLabel: String = "",
        /** 下课时刻：通知挂到这时才自动收走；拿不到就传 0 */
        val endAtMillis: Long = 0L,
        /** 已经上课了：把「还有多久」换成「上课中」，只更新不再提醒 */
        val inProgress: Boolean = false,
        /**
         * 头部那句小字换掉，默认空 = 按 [kind] 和 [inProgress] 自己算。
         *
         * 扩展组件的通知借这条横幅显示时（见 `ExtensionNotifier`）用不到「还有多久」，
         * 这个位置放组件名更合适。
         */
        val headline: String = "",
        val kind: Kind = Kind.Class,
    ) {
        val notificationId: Int
            get() = if (kind == Kind.AlarmPreview) ALARM_NOTIFICATION_ID else NOTIFICATION_ID

        /** 头部那句「还有多久」；悬浮窗和通知共用 */
        fun subText(context: Context): String = when {
            headline.isNotBlank() -> headline
            kind == Kind.AlarmPreview -> context.getString(R.string.alarm_pre_notice_subtext, minutesUntilStart)
            inProgress -> context.getString(R.string.class_notice_subtext_in_progress)
            else -> context.getString(R.string.class_notice_subtext, minutesUntilStart)
        }

        /** 「08:00」：状态栏胶囊只放得下这么几个字 */
        val startClock: String
            get() = CLOCK_PATTERN.find(timeRange)?.value ?: timeRange.substringBefore('-').trim()

        /** 「第一节 08:00-09:35」：节次名字比钟点更好认，放在前面 */
        val whenText: String
            get() = listOf(slotLabel, timeRange).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * @param plain 高级测试用：不常驻、不申请胶囊，发成一条最普通的通知。
     *   用来确认某台机器不弹横幅，是不是因为它不给常驻 / 实时活动类的通知弹横幅
     * @param channelOverride 高级测试用：换一个渠道发，比如有声的那个测试渠道
     */
    fun notify(
        context: Context,
        content: Content,
        preferences: ClassNoticePreferences,
        theme: NoticeTheme,
        plain: Boolean = false,
        channelOverride: String? = null,
    ) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        // 悬浮窗皮肤是「系统通知照发 + 解锁时额外画一层」：悬浮窗盖不住锁屏，
        // 通知栏和锁屏还得靠这条通知。此时把系统横幅压下去，免得两个横幅一起弹。
        // 系统横幅指望不上的手机（见 SelfDrawnNotice），要横幅就只剩自己画这一条路：
        // 不管选的哪套皮肤，开着悬浮通知就由悬浮窗顶上。高级那两条测试是专门看系统横幅的，不顶。
        // 放在通知那几道关前面：自己画的不靠通知权限，通知被关了照样弹
        val overlayFallback = preferences.headsUpEnabled && SelfDrawnNotice.only() &&
            !plain && channelOverride == null
        // 熄屏时悬浮窗会在无人看到的情况下超时；只在亮屏时让它接管
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        val overlayTakesOver = !content.inProgress &&
            preferences.headsUpEnabled && !plain && channelOverride == null &&
            (preferences.skin == ClassNoticeSkin.Overlay || overlayFallback) &&
            screenOn &&
            ClassNoticeOverlay.canShowNow(context)
        if (overlayTakesOver) {
            ClassNoticeOverlay.show(context, content, preferences, theme)
        } else if (overlayFallback && !content.inProgress && ClassNoticeOverlay.canDraw(context)) {
            // 锁着屏或熄着屏时悬浮窗画不出来，这类手机的锁屏通知又指望不上：换成压在锁屏上的那一版，
            // 熄屏时它静静挂着，下次亮屏一眼看到
            ClassNoticeLockActivity.show(context, content, preferences, theme)
        }

        // 下面几处不发都是静悄悄的，用户那头只看到「没弹」，不留一笔就分不清是哪一关没过
        fun skip(reason: String) = ReminderLogger.info(
            "class_notice.post.skip",
            mapOf("reason" to reason, "kind" to content.kind, "inProgress" to content.inProgress),
        )
        if (!manager.areNotificationsEnabled()) {
            skip("notifications_disabled")
            return
        }
        // 上课那一刻的更新只改还挂着的那条：用户已经划掉的就别再冒出来
        val activeChannel = if (content.inProgress) {
            activeNoticeChannel(context) ?: run {
                skip("in_progress_dismissed")
                return
            }
        } else {
            null
        }
        // Android 13+ 的运行时权限：引导页申请过，但用户随时可以撤销
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            skip("no_post_permission")
            return
        }

        // 一行版，给 MIUI 焦点通知的 ticker 和不支持副标题的老系统兜底
        val title = when {
            content.kind == Kind.AlarmPreview ->
                context.getString(R.string.alarm_pre_notice_title, content.minutesUntilStart, content.courseTitle)
            content.inProgress -> context.getString(R.string.class_notice_title_in_progress, content.courseTitle)
            else -> context.getString(R.string.class_notice_title, content.minutesUntilStart, content.courseTitle)
        }
        // 正文分三层：头部说「还有多久」，标题给课名，正文给时间地点。
        // 全堆在标题里会被截断，分开之后课名才是最显眼的那一行
        val subText = content.subText(context)
        val body = listOf(content.whenText, content.location)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        // 展开后一行时间一行地点，地点长也不会被挤没
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

        // 更新时沿用原来那条的渠道，换渠道等于另发一条
        val channelId = channelOverride ?: activeChannel ?: channelIdFor(preferences, overlayTakesOver)
        val builder = NotificationCompat.Builder(context, channelId)
            // 专画的白色剪影 logo：Android 12+ 会把它放进一个用 setColor 上色的圆里，
            // 圆用主题色，和 App 里看到的是同一个颜色
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(theme.primary)
            // 状态栏小图标只能是白色剪影，认不出是哪家的；整张彩色 logo 得挂在这里。
            // 品牌卡片皮肤的卡片里自带一张，再挂一张就是同一个 logo 出现三次，还把卡片挤窄
            .setLargeIcon(
                if (preferences.skin == ClassNoticeSkin.Card) null else brandLogo(context),
            )
            .setSubText(subText)
            // 老 vivo / OPPO 上横幅要有 ticker 才弹；新系统拿它给无障碍读，带着没坏处
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
            // 点开看课表不算「知道了」：通知得一直挂到下课，除非自己划掉或点「知道了」
            .setAutoCancel(false)
            // 上课那一刻会原地更新成「上课中」，更新不再响、不再弹横幅
            .setOnlyAlertOnce(true)
            // 事件类通知：系统据此决定摆放位置，也让免打扰放行更合理
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // 只对 Android 7 及以下有意义；8 以上看的是上面挑的那个渠道的 importance
            .setPriority(
                if (preferences.headsUpEnabled && !overlayTakesOver) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            // 锁屏：开着就把课名地点直接显示出来，关掉只留一条「有通知」
            .setVisibility(
                if (preferences.lockScreenEnabled) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
        // 不出声由渠道的 setSound(null)/enableVibration(false) 保证，这里不能用 setSilent(true)：
        // 它的实现会把通知塞进一个叫 silent 的通知组并设成 GROUP_ALERT_SUMMARY，
        // 而这个组没有 summary，SystemUI 会据此判定「因分组而压制提醒」，悬浮横幅就再也不弹了

        // 不显示时间戳。这里试过挂 setChronometerCountDown 的走字倒计时，但它紧挨着
        // 「19:00-20:35」这种真实时间段，「19:59」会被当成一个钟点读；而且走到一半
        // 还会和「20 分钟后上课」这句静态文案对不上。静态文案更准也更好懂。
        builder.setShowWhen(false)
        // when 改成上课时刻。AOSP 的胶囊文字先用 shortCriticalText，没有才拿 when 倒计时，
        // 而 setShowWhen(false) 时 when 根本不进胶囊——眼下胶囊显示的是下面那句钟点。
        // 留着它给不读 shortCriticalText、只认 when 的厂商胶囊兜底
        if (!content.inProgress && content.startAtMillis > System.currentTimeMillis()) {
            builder.setWhen(content.startAtMillis)
        }
        // 挂到下课才自动收走；上课时会被更新成「上课中」，不会留着过期的「20 分钟后上课」。
        // 闹钟预告挂到响铃那一刻：响起来之后有响铃界面，不用它了
        val expireAt = content.endAtMillis.takeIf { it > 0L && content.kind == Kind.Class }
            ?: content.startAtMillis
        if (expireAt > 0L) {
            val remaining = expireAt - System.currentTimeMillis()
            if (remaining > 0L) builder.setTimeoutAfter(remaining)
        }
        // 常驻：「清除全部」清不掉，只有自己划掉或点「知道了」才走。
        // Android 14 起常驻通知照样能划掉；更早的系统上常驻就划不掉了，那边只保留不自动消失。
        // 只用自己画的手机上不要常驻：vivo 上 ongoing 通知一律不悬浮、不做锁屏提醒（OriginOS 通知规范 6.1）
        val selfDrawn = SelfDrawnNotice.only()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !plain && !selfDrawn) {
            builder.setOngoing(true)
        }

        // 实时活动（状态栏胶囊 + 通知栏/锁屏置顶那张卡）只认系统模板，挂了自定义布局就不给上岛。
        // ColorOS 流体云、HyperOS、荣耀、Pixel 都走这一套；系统放行时胶囊比卡片配色要紧，
        // 品牌卡片这一档就让位给系统模板，没放行的手机上照旧画卡片
        val promoted = preferences.focusNotificationEnabled && StatusBarChipSupport.allowed(context)

        // 品牌卡片皮肤：换掉内容区的底色与排版。Android 12 起系统还会在外面套一层
        // 它自己的头部，所以这一档能换的只有这一块，动效和毛玻璃通知里做不到。
        if (preferences.skin == ClassNoticeSkin.Card && !promoted) {
            // 折叠态（含悬浮横幅）系统只给约 48dp 高，得用单独那份紧凑布局；
            // 「还有多久」并进正文排成两行，展开后才回到三行的宽松版
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

        // 只用自己画的手机上胶囊出不来，这些字段带了也白带
        if (preferences.focusNotificationEnabled && !plain && !selfDrawn) {
            val chipText = chipText(context, content)
            // Android 16 的实时活动：通知栏、锁屏置顶，状态栏挂一个小胶囊。
            // 用户在系统里关着也照样申请：之后打开时下一次更新就能上岛，不用等重新排
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setRequestPromotedOngoing(true)
                    .setShortCriticalText(chipText)
                // One UI 8.5 起实时活动还要过三星自己的白名单，带上这个标记就按标准实时活动放行
                // （反编译 SystemUI 得来，不需要任何权限）。别的系统不认这个键，带着也无妨。
                // 千万别加 ongoingActivityNoti.style：那会把通知送进三星私有卡片那条路，反而不上岛
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

        // 记下这次想怎么发，和发完系统实际收下的那条（post.result）对照，才看得出是哪一步走样
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
     * 胶囊里那几个字：Android 16 实时活动的 shortCriticalText 和小米焦点通知的 ticker 共用。
     *
     * 胶囊最宽 96dp，文字不到 7 个字符才保证整段显示，塞不下就只剩图标：
     * 「08:00上课」正好 7 个、中文又宽，好几台机器上只看到图标。只放钟点，前面的课表图标已经说明是上课
     */
    fun chipText(context: Context, content: Content): String = when {
        content.kind == Kind.AlarmPreview -> context.getString(R.string.alarm_pre_notice_chip, content.startClock)
        content.inProgress -> context.getString(R.string.class_notice_chip_in_progress)
        else -> context.getString(R.string.class_notice_chip_upcoming, content.startClock)
    }

    /** 高级测试：发一条不常驻、不申请胶囊的普通通知，和正常那条对照着看横幅弹不弹。 */
    fun notifyPlainTest(context: Context, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        cancel(context)
        notify(context, previewContent(context, preferences), preferences, theme, plain = true)
    }

    /**
     * 高级测试：走一个带默认铃声和振动的 HIGH 渠道，其余和普通测试一样。
     *
     * 上课通知的渠道是静音的。有的 ROM 把没声没振动的通知当成「静默」，横幅就不弹了；
     * 这一条能弹、静音那条不能，就是这个原因。渠道只给测试用，不影响正式提醒
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

    /** 示例通知的内容；设置页预览与高级设置里的测试共用这一份。 */
    fun previewContent(context: Context, preferences: ClassNoticePreferences): Content = Content(
        courseTitle = context.getString(R.string.class_notice_preview_course),
        location = context.getString(R.string.class_notice_preview_location),
        timeRange = "19:00-20:35",
        minutesUntilStart = preferences.advanceMinutes,
        slotLabel = context.getString(R.string.class_notice_preview_slot),
    )

    /**
     * 按当前设置弹一条示例，供设置页「预览当前样式」用。
     *
     * 走的是和真实通知完全相同的那条路径，皮肤、动效、毛玻璃所见即所得；
     * startAtMillis 给 0，免得预览的那条被 setTimeoutAfter 立刻收走。
     */
    fun notifyPreview(context: Context, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        // 同一条通知只提醒一次，不先收掉的话连点两次预览第二次就不弹了
        cancel(context)
        notify(
            context = context,
            content = previewContent(context, preferences),
            preferences = preferences,
            theme = theme,
        )
        hintChipHiddenInForeground(context, preferences)
    }

    /** 指定内容的预览：设置页拿到「下一节课」的真实内容时用这个。 */
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

    /**
     * 在应用里点预览时提醒一句「回桌面看胶囊」。
     *
     * AOSP 在发通知的应用正显示着时会把它的胶囊藏起来（NotifChipsViewModel 的 isAppVisible），
     * 锁屏时也不画胶囊。不说一声，人在设置页点完预览只会觉得胶囊坏了。
     */
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

    /** 闹钟响起来了，预告就不用挂着了。 */
    fun cancelAlarmPreview(context: Context) = cancel(context, ALARM_NOTIFICATION_ID)

    /** 设置页「预览闹钟预告」用的示例。 */
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
            // 预览不让它到点自己消失，和上课通知预览一样
            startAtMillis = 0L,
            kind = Kind.AlarmPreview,
        )
    }

    /** 上课通知还挂着的话，它走的是哪个渠道；已经被划掉就是 null。 */
    private fun activeNoticeChannel(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return null
        val notice = active.firstOrNull { it.id == NOTIFICATION_ID } ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notice.notification.channelId else CHANNEL_ID
    }

    /** 品牌卡片皮肤的展开态：三行，宽松。 */
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

    /** 品牌卡片皮肤的折叠态：两行，塞得进系统给的那约 48dp。 */
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
     * 品牌卡片的底：内置主题换对应的渐变图；自选色在 Android 12 起换成白底圆角图再着色，
     * 更早的系统没法给 RemoteViews 着色，用 [NoticeTheme.cardBackgroundRes] 给的最接近的那张。
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

    /** 裁好的品牌图只跟资源走，一个进程算一次就够 */
    @Volatile
    private var brandLogoCache: Bitmap? = null

    /**
     * 通知右侧那块彩色品牌图。
     *
     * 用 ic_launcher_foreground——它是透明底的整张彩色 logo（蓝圆、日历、书本、
     * 学位帽、时钟）。但它按自适应图标的规矩留了一大圈透明边，实际内容只占画布
     * 四成多，直接拿来当 largeIcon 会缩成中间小小一团，所以先按不透明像素的
     * 包围盒裁一刀。不写死裁剪比例，以后换图也不用回来改这里。
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

    /** 裁掉四周完全透明的边；整张都是透明的就原样返回。 */
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
                // 边缘抗锯齿会留一圈几乎看不见的半透明像素，太淡的不算数
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
     * 小米焦点通知 / 超级岛的附加字段，按澎湃OS高级平台《超级岛开发指南》的 param_v2 协议。
     *
     * 这是厂商扩展，不在 AOSP 里；别的机型读不到这些键，会当普通通知照常显示，
     * 所以不必按厂商分支，附上就好。ticker 必须带：OS2 上没有它状态栏不显示。
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
     * 系统那一层有没有放行「岛」式展示。
     *
     * 小米要用户在系统里给这个应用开焦点通知；Android 16 的实时活动也能被用户按应用关掉。
     * 两边都查不到（别的厂商、老系统）时算放行，反正会按普通通知显示。
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

    /** 这台手机有没有任何一种「岛」：小米焦点通知或 Android 16 实时活动。都没有就不必引导用户去开。 */
    fun islandAvailable(context: Context): Boolean =
        miuiFocusProtocol(context) > 0 ||
            StatusBarChipSupport.level() != StatusBarChipSupport.Level.Unsupported

    internal fun miuiFocusProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    }.getOrDefault(0)

    /**
     * 通知渠道。
     *
     * 渠道重要性只在创建那一刻生效，之后改不动——所以这里一律建成 HIGH，
     * 「要不要悬浮」交给通知本身的优先级和用户在系统里的设置决定。
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

    /** 这次该走哪个渠道：要悬浮走 HIGH，悬浮关掉或由悬浮窗接管就走 DEFAULT。 */
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
            // 不出声不震动：上课通知只负责把信息送到眼前
            setSound(null, null)
            enableVibration(false)
            // 锁屏可见性不写在渠道上：渠道一旦建好就改不动，
            // 用户后来把「锁屏显示」打开也会被这里钉死的旧值盖掉。
            // 留空（VISIBILITY_NO_OVERRIDE）才轮得到每条通知自己的 setVisibility 生效
        }
        manager.createNotificationChannel(channel)
    }

    /** 系统层面是否还拦着：通知总开关关了，或当前在用的那个渠道被用户调低/关掉。 */
    fun systemBlocked(context: Context, preferences: ClassNoticePreferences): Boolean =
        masterNotificationsBlocked(context) || channelBlocked(context, preferences)

    /** 通知总开关被关掉了。 */
    fun masterNotificationsBlocked(context: Context): Boolean =
        !NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 当前在用的那个渠道被用户调低或关掉；渠道还没建出来时按没被拦算，发第一条时会先建。 */
    fun channelBlocked(context: Context, preferences: ClassNoticePreferences): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelIdFor(preferences))
            ?: return false
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT
    }
}
