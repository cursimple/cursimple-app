package com.x500x.cursimple.app.notice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    ) {
        /** 「第一节 08:00-09:35」：节次名字比钟点更好认，放在前面 */
        val whenText: String
            get() = listOf(slotLabel, timeRange).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun notify(context: Context, content: Content, preferences: ClassNoticePreferences, theme: NoticeTheme) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Android 13+ 的运行时权限：引导页申请过，但用户随时可以撤销
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 一行版，给 MIUI 焦点通知的 ticker 和不支持副标题的老系统兜底
        val title = context.getString(
            R.string.class_notice_title,
            content.minutesUntilStart,
            content.courseTitle,
        )
        // 正文分三层：头部说「还有多久」，标题给课名，正文给时间地点。
        // 全堆在标题里会被截断，分开之后课名才是最显眼的那一行
        val subText = context.getString(R.string.class_notice_subtext, content.minutesUntilStart)
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
        val overlayTakesOver = preferences.skin == ClassNoticeSkin.Overlay &&
            ClassNoticeOverlay.canShowNow(context)
        if (overlayTakesOver) {
            ClassNoticeOverlay.show(context, content, preferences, theme)
        }

        val builder = NotificationCompat.Builder(
            context,
            channelIdFor(preferences, overlayTakesOver),
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
            .setAutoCancel(true)
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
        // 上课了这条就该走人，不留一条已经过期的「20 分钟后上课」挂在通知栏
        if (content.startAtMillis > 0L) {
            val remaining = content.startAtMillis - System.currentTimeMillis()
            if (remaining > 0L) builder.setTimeoutAfter(remaining)
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
            builder.addExtras(miuiFocusExtras(title, body))
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
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
        notify(
            context = context,
            content = previewContent(context, preferences),
            preferences = preferences,
            theme = theme,
        )
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    /** 品牌卡片皮肤的展开态：三行，宽松。 */
    private fun cardRemoteViews(
        context: Context,
        theme: NoticeTheme,
        subText: String,
        title: String,
        body: String,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_class_notice_card).apply {
        setInt(R.id.class_notice_card_root, "setBackgroundResource", theme.cardBackgroundRes)
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
        setInt(R.id.class_notice_card_root, "setBackgroundResource", theme.cardBackgroundRes)
        setTextViewText(R.id.class_notice_card_title, title)
        setTextViewText(R.id.class_notice_card_body, body)
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
     * 小米原子岛（焦点通知）识别的附加字段。
     *
     * 这是厂商扩展，不在 AOSP 里；别的机型读不到这些键，会当普通通知照常显示，
     * 所以不必按厂商分支，附上就好。
     */
    private fun miuiFocusExtras(title: String, body: String): Bundle = Bundle().apply {
        putBoolean("miui.focus.enable", true)
        putString("miui.focus.ticker", title)
        putString("miui.focus.title", title)
        putString("miui.focus.content", body)
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
