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
 * 上课通知的诊断。
 *
 * 「不弹横幅 / 没有胶囊 / 锁屏不置顶」在国产 ROM 上能叠好几层原因：渠道被调低、
 * 实时活动没放行、厂商自己的焦点通知开关、系统干脆没收下这条通知……设置页上每一项都是绿的，
 * 现象照样出现。这里把系统那头能读到的状态一次性摊开。
 *
 * 键名用英文短名而不是翻译过的标签：它们同时是日志字段名，
 * 用户复制弹窗内容发过来，能直接和日志里那几行对上。
 */
object ClassNoticeDiagnostics {

    /** 各家 ROM 自己的版本号属性；判断「是不是那一版才开始支持」全靠它们 */
    private val ROM_PROPS = listOf(
        "ro.build.version.oplusrom",
        "ro.build.version.opporom",
        "ro.miui.ui.version.name",
        "ro.mi.os.version.name",
        // HyperOS 3 起超级岛才接标准实时活动，name 里常带前缀不好比，code 是纯数字
        "ro.mi.os.version.code",
        "ro.vivo.os.version",
        "ro.build.version.magic",
        "ro.build.version.oneui",
    )

    // android-36 的 SDK 里这两个键还是 @hide（EXTRA_REQUEST_PROMOTED_ONGOING 36.1 才公开），只能写字面量
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** 发通知时塞进 extras、决定能不能上岛的那几个键；值原样列出，小米那段 JSON 太长只看有没有 */
    private val EXTRAS_OF_INTEREST = listOf(
        EXTRA_SHORT_CRITICAL_TEXT,
        EXTRA_REQUEST_PROMOTED_ONGOING,
        "android.ongoingActivityNoti.automation",
    )
    private const val EXTRA_MIUI_FOCUS = "miui.focus.param"

    /** 渠道的 lockscreenVisibility 没被改过时是这个值；NotificationManager 上那个常量是 @hide */
    private const val VISIBILITY_NO_OVERRIDE = -1000

    /**
     * 发完隔一会儿再查：notify() 只是把通知交给系统排队，系统在自己的线程里才真正挂上去，
     * 立刻去查常常还查不到，会被误报成「被拦了」。
     */
    private const val POST_CHECK_DELAY_MS = 1_000L

    /**
     * 查到的那条比这次发送早这么多，就当是上一次留下的：更新被系统丢掉了（比如发得太密被限流），
     * 通知栏里挂着的还是旧的。留几秒余量是因为 postTime 是系统收下时记的，比这边调用返回稍早。
     */
    private const val STALE_SLACK_MS = 5_000L

    fun report(context: Context, preferences: ClassNoticePreferences): List<Pair<String, String>> {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        return buildList {
            add("device" to "${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL}")
            add("android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            add("build.display" to Build.DISPLAY.orEmpty())
            // BP2A 开头是 16.0、BP3A 起是 QPR1：两版判定「能不能当实时活动」的规则正好相反（着色与否）
            add("build.id" to Build.ID.orEmpty())
            ROM_PROPS.forEach { key -> StatusBarChipSupport.prop(key)?.let { add(key to it) } }
            add("vendor" to VendorRom.current().name)

            add("notifications.enabled" to NotificationManagerCompat.from(app).areNotificationsEnabled().toString())
            add("permission.post_notifications" to postPermissionState(app))
            listOf(ClassNoticeNotifier.CHANNEL_ID, ClassNoticeNotifier.CHANNEL_ID_QUIET).forEach { id ->
                addAll(channelState(manager, id))
            }
            add("channel.selected" to ClassNoticeNotifier.channelIdFor(preferences))
            // 悬浮窗接管那一刻会换成安静渠道，横幅本来就不该弹，别当成故障
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
            // 系统 API 的原话，和上面按厂商修正过的 chip.allowed 对照着看
            canPostPromoted(manager)?.let { add("promoted.can_post" to it.toString()) }
            add("miui.focus_protocol" to ClassNoticeNotifier.miuiFocusProtocol(app).toString())
            add("island.available" to ClassNoticeNotifier.islandAvailable(app).toString())
            add("island.blocked" to ClassNoticeNotifier.islandBlocked(app).toString())

            add("overlay.can_draw" to Settings.canDrawOverlays(app).toString())
            add("self_drawn.reason" to (SelfDrawnNotice.reason()?.name ?: "none"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && VendorRom.current() == VendorRom.Vivo) {
                // vivo 把渠道判成「运营消息」后，公开 API 读到的 importance 照样是 HIGH；
                // 原样把渠道对象打出来，看它有没有在 AOSP 之外多塞字段，将来好据此判断
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
            // 没设锁屏密码时「锁屏」只是一层滑动解锁，很多 ROM 上锁屏通知的排法都不一样
            add("keyguard.secure" to (keyguard?.isDeviceSecure?.toString() ?: "unknown"))
            add("keyguard.locked" to (keyguard?.isKeyguardLocked?.toString() ?: "unknown"))
            // 系统级的「锁屏显示通知 / 显示敏感内容」：关着时应用自己设成 PUBLIC 也没用。读不到就不列
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
            // 闹钟预告只在快响铃时才有，没挂着就不占行
            findActive(manager, ClassNoticeNotifier.ALARM_NOTIFICATION_ID)?.let { alarm ->
                summarize(alarm).forEach { (key, value) -> add("active.alarm_preview.$key" to value) }
            }
        }
    }

    fun asText(lines: List<Pair<String, String>>): String =
        lines.joinToString("\n") { (key, value) -> "$key: $value" }

    /**
     * 发完之后回头看系统到底收下了什么，压成一行日志。
     *
     * 被划掉、渠道被关、被限流时系统不会告诉应用，notify() 照样正常返回；
     * 只有去 activeNotifications 里找一遍，才知道这条到底挂没挂上、有没有被标成实时活动。
     */
    fun logPosted(context: Context, notificationId: Int, extra: Map<String, Any?>) {
        val app = context.applicationContext
        val postedAt = System.currentTimeMillis()
        // 发出那一刻的处境要当场记：亮屏没有、锁没锁、应用在不在前台，都会让系统合理地不弹横幅、不给胶囊
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

    /**
     * 应用自己是不是正显示在前台。AOSP 在发通知的应用可见时会把它的胶囊藏起来，
     * 在设置页点预览时看不到胶囊是正常的，回到桌面才出来。
     */
    fun isForeground(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    private fun findActive(manager: NotificationManager?, id: Int): StatusBarNotification? =
        runCatching { manager?.activeNotifications }.getOrNull()?.firstOrNull { it.id == id }

    /** 一条挂着的通知在系统眼里是什么样：系统改过的 flags、有没有被标成实时活动都在这里 */
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
                // 这个 flag 是系统收下通知时按「放没放行 + 长得像不像实时活动」替应用打上的，
                // 有它才会上胶囊；没有时看 promotable 就知道是通知本身不合格还是系统没放行
                add("promoted_flag" to ((flags and Notification.FLAG_PROMOTED_ONGOING) != 0).toString())
                add("promotable" to runCatching { notification.hasPromotableCharacteristics() }.getOrNull().toString())
                // promotable 为 false 时逐条看是哪一关没过，条件照 android-16.0.0_r3 的 hasPromotableCharacteristics
                val extras = notification.extras
                add("promo.requested" to (extras?.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING) == true).toString())
                add("promo.has_title" to (!extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty()).toString())
                add("promo.colorized" to (extras?.getBoolean(Notification.EXTRA_COLORIZED) == true).toString())
                add("promo.group_summary" to ((flags and Notification.FLAG_GROUP_SUMMARY) != 0).toString())
            }
            // 挂了自定义布局就不合实时活动的格，品牌卡片皮肤最容易踩这个
            val customViews = listOfNotNull(
                "content".takeIf { notification.contentView != null },
                "big".takeIf { notification.bigContentView != null },
                "heads_up".takeIf { notification.headsUpContentView != null },
                // 锁屏用的公开版本挂了自定义布局也算，系统一并检查
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
            // 进了没有 summary 的组会被当成「因分组压制提醒」，横幅就不弹了
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
        // 渠道是发第一条时才建的，没建过属于正常
        val channel = runCatching { manager?.getNotificationChannel(id) }.getOrNull()
            ?: return listOf(prefix to "absent")
        return listOf(
            "$prefix.importance" to importanceLabel(channel.importance),
            "$prefix.lockscreen" to visibilityLabel(channel.lockscreenVisibility),
            "$prefix.group_blocked" to groupBlocked(manager, channel),
        )
    }

    /** 整组被关时渠道自己的 importance 看着还正常，所以单独查一遍 */
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

    /** 读勿扰要通知策略访问权，没有时系统回 UNKNOWN，不能把它当成「没开勿扰」 */
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
