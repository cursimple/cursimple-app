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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
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

    private const val NOTIFICATION_ID = 0x0C1A

    /** 闹钟预告单独一个 id：和上课通知同时挂着时互不覆盖。 */
    private const val ALARM_NOTIFICATION_ID = 0x0C20

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
        val kind: Kind = Kind.Class,
    ) {
        val notificationId: Int
            get() = if (kind == Kind.AlarmPreview) ALARM_NOTIFICATION_ID else NOTIFICATION_ID

        /** 头部那句「还有多久」；悬浮窗和通知共用 */
        fun subText(context: Context): String = when {
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

    fun notify(context: Context, content: Content, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // 上课那一刻的更新只改还挂着的那条：用户已经划掉的就别再冒出来
        val activeChannel = if (content.inProgress) {
            activeNoticeChannel(context) ?: return
        } else {
            null
        }
        // Android 13+ 的运行时权限：引导页申请过，但用户随时可以撤销
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
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

        // 悬浮窗皮肤是「系统通知照发 + 解锁时额外画一层」：悬浮窗盖不住锁屏，
        // 通知栏和锁屏还得靠这条通知。此时把系统横幅压下去，免得两个横幅一起弹。
        val overlayTakesOver = !content.inProgress &&
            preferences.skin == ClassNoticeSkin.Overlay &&
            ClassNoticeOverlay.canShowNow(context)
        if (overlayTakesOver) {
            ClassNoticeOverlay.show(context, content, preferences, theme)
        }

        val builder = NotificationCompat.Builder(
            context,
            // 更新时沿用原来那条的渠道，换渠道等于另发一条
            activeChannel ?: channelIdFor(preferences, overlayTakesOver),
        )
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
        // 挂到下课才自动收走；上课时会被更新成「上课中」，不会留着过期的「20 分钟后上课」。
        // 闹钟预告挂到响铃那一刻：响起来之后有响铃界面，不用它了
        val expireAt = content.endAtMillis.takeIf { it > 0L && content.kind == Kind.Class }
            ?: content.startAtMillis
        if (expireAt > 0L) {
            val remaining = expireAt - System.currentTimeMillis()
            if (remaining > 0L) builder.setTimeoutAfter(remaining)
        }
        // 常驻：「清除全部」清不掉，只有自己划掉或点「知道了」才走。
        // Android 14 起常驻通知照样能划掉；更早的系统上常驻就划不掉了，那边只保留不自动消失
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setOngoing(true)
        }

        // 品牌卡片皮肤：换掉内容区的底色与排版。Android 12 起系统还会在外面套一层
        // 它自己的头部，所以这一档能换的只有这一块，动效和毛玻璃通知里做不到。
        if (preferences.skin == ClassNoticeSkin.Card) {
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

        if (preferences.focusNotificationEnabled) {
            val chipText = when {
                content.kind == Kind.AlarmPreview ->
                    context.getString(R.string.alarm_pre_notice_chip, content.startClock)
                content.inProgress -> context.getString(R.string.class_notice_chip_in_progress)
                else -> context.getString(R.string.class_notice_chip_upcoming, content.startClock)
            }
            // Android 16 的实时活动：锁屏、息屏置顶，状态栏挂一个小胶囊。
            // 它不收自定义布局，品牌卡片那一档只能照普通通知显示
            if (preferences.skin != ClassNoticeSkin.Card &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                builder.setRequestPromotedOngoing(true)
                    .setShortCriticalText(chipText)
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

        runCatching { manager.notify(content.notificationId, builder.build()) }
    }

    /** 示例通知的内容；设置页预览与开发者设置里的测试共用这一份。 */
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
     * 小米焦点通知 / 超级岛的附加字段，按澎湃OS开发者平台《超级岛开发指南》的 param_v2 协议。
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
        val focusProtocol = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
        if (focusProtocol > 0) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            return !NotificationManagerCompat.from(context).canPostPromotedNotifications()
        }
        return false
    }

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
    fun systemBlocked(context: Context, preferences: ClassNoticePreferences): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelIdFor(preferences))
            ?: return false
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT
    }
}
