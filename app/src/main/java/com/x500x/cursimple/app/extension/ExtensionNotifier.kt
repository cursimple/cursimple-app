package com.x500x.cursimple.app.extension

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.app.notice.ClassNoticeLockActivity
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.app.notice.SelfDrawnNotice
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 扩展组件的通知：新内容、截止前提醒、登录失效。点开都进那个组件的侧边栏日历页。
 *
 * 三类各一个渠道，用户可以只关掉其中一种（比如公告太多只想留截止提醒）。
 */
object ExtensionNotifier {

    const val CHANNEL_NEW = "extension_feed_new"
    const val CHANNEL_DUE = "extension_feed_due"
    const val CHANNEL_STATUS = "extension_feed_status"

    /** 一次新冒出来这么多条以内逐条发，再多就并成一条，免得通知栏被刷屏 */
    private const val MAX_SEPARATE = 3

    suspend fun notifyNewItems(context: Context, pluginId: String, title: String, items: List<ExtensionFeedItem>) {
        if (items.isEmpty()) return
        // 新内容：系统横幅弹不出来时顶上（见 bannerFallback）。内容取第一条代表性的，其余并入正文
        val bannerShown = bannerFallback(
            context = context,
            content = contentOf(
                head = title,
                title = context.getString(R.string.extension_notify_new_title, labelOf(items.first()), items.first().title),
                body = newItemsBody(context, items),
            ),
            onTap = openFeed(context, pluginId),
        )
        // 下面几处不发都是静悄悄的，用户那头只看到「没弹」，不留一笔就分不清是哪一关没过
        if (!canPost(context)) return
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (items.size <= MAX_SEPARATE) {
            items.forEach { item ->
                val builder = base(context, CHANNEL_NEW, pluginId, bannerShown)
                    .setSubText(title)
                    .setContentTitle(context.getString(R.string.extension_notify_new_title, labelOf(item), item.title))
                    .setContentText(detailLine(context, item))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(detailLine(context, item), item.summary)
                        .filter(String::isNotBlank).joinToString("\n")))
                    .setGroup(groupOf(pluginId))
                runCatching { manager.notify(notificationId(pluginId, item.id), builder.build()) }
            }
            return
        }
        val inbox = NotificationCompat.InboxStyle()
        items.take(6).forEach { inbox.addLine("${labelOf(item = it)}：${it.title}") }
        if (items.size > 6) inbox.setSummaryText(context.getString(R.string.extension_notify_more, items.size - 6))
        val builder = base(context, CHANNEL_NEW, pluginId, bannerShown)
            .setSubText(title)
            .setContentTitle(context.getString(R.string.extension_notify_batch_title, title, items.size))
            .setContentText(items.take(3).joinToString("、") { it.title })
            .setStyle(inbox)
            .setGroup(groupOf(pluginId))
        runCatching { manager.notify(notificationId(pluginId, "batch"), builder.build()) }
    }

    suspend fun notifyDue(context: Context, pluginId: String, title: String, item: ExtensionFeedItem, now: Long) {
        val due = item.dueAt ?: return
        val clock = Instant.ofEpochMilli(due).atZone(BeijingTime.zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        val hoursLeft = ((due - now) / 3_600_000L).toInt()
        val left = if (hoursLeft < 1) {
            context.getString(R.string.extension_notify_due_within_hour)
        } else {
            context.resources.getQuantityString(R.plurals.extension_notify_due_hours, hoursLeft, hoursLeft)
        }
        val bannerShown = bannerFallback(
            context = context,
            content = contentOf(
                head = title,
                title = context.getString(R.string.extension_notify_due_title, labelOf(item), item.title),
                body = context.getString(R.string.extension_notify_due_text, listOf(item.course, clock).filter(String::isNotBlank).joinToString(" · "), left),
            ),
            onTap = openFeed(context, pluginId),
        )
        if (!canPost(context)) return
        ensureChannels(context)
        val builder = base(context, CHANNEL_DUE, pluginId, bannerShown)
            .setSubText(title)
            .setContentTitle(context.getString(R.string.extension_notify_due_title, labelOf(item), item.title))
            .setContentText(context.getString(R.string.extension_notify_due_text, listOf(item.course, clock).filter(String::isNotBlank).joinToString(" · "), left))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(pluginId, "due:${item.id}"), builder.build()) }
    }

    /**
     * 登录失效：这条带一个「重新登录」的动作，点按钮或点横幅都直接进组件的登录页。
     * 横幅上也给同一个按钮，用户不用先进通知栏。
     */
    suspend fun notifyLoginExpired(context: Context, pluginId: String, title: String) {
        val openSettings = openSettings(context, pluginId)
        val bannerShown = bannerFallback(
            context = context,
            content = contentOf(
                head = title,
                title = context.getString(R.string.extension_notify_expired_title, title),
                body = context.getString(R.string.extension_notify_expired_text, title),
            ),
            onTap = openSettings,
            action = ClassNoticeOverlay.NoticeAction(
                label = context.getString(R.string.extension_notify_relogin_action),
            ) { runCatching { openSettings() } },
        )
        if (!canPost(context)) return
        ensureChannels(context)
        val builder = base(context, CHANNEL_STATUS, pluginId, bannerShown)
            .setContentTitle(context.getString(R.string.extension_notify_expired_title, title))
            .setContentText(context.getString(R.string.extension_notify_expired_text, title))
            .addAction(
                0,
                context.getString(R.string.extension_notify_relogin_action),
                openSettingsPending(context, pluginId),
            )
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(pluginId, "expired"), builder.build()) }
    }

    /** 头部小字放组件名，加粗标题、正文；尺寸和上课横幅一致，见 [ClassNoticeNotifier.Content.headline] */
    private fun contentOf(head: String, title: String, body: String) =
        ClassNoticeNotifier.Content(
            courseTitle = title,
            location = body,
            timeRange = "",
            minutesUntilStart = 0,
            headline = head,
        )

    private fun newItemsBody(context: Context, items: List<ExtensionFeedItem>): String =
        if (items.size == 1) {
            listOf(detailLine(context, items.first()), items.first().summary)
                .filter(String::isNotBlank)
                .joinToString(" · ")
        } else {
            items.take(3).joinToString("、") { it.title }
        }

    /**
     * 选择系统增强，或系统横幅不受支持（见 [SelfDrawnNotice]）时，组件的通知也自己画：
     * 亮屏解锁时用悬浮横幅，锁屏或熄屏时压在锁屏上。
     *
     * 系统通知照发；自绘成功时选用普通重要性的渠道，避免系统和自绘同时弹出两条横幅。
     */
    private suspend fun bannerFallback(
        context: Context,
        content: ClassNoticeNotifier.Content,
        onTap: (() -> Unit)?,
        action: ClassNoticeOverlay.NoticeAction? = null,
    ): Boolean {
        val preferences = ClassNoticeGateway.preferences(context)
        if (preferences.skin != ClassNoticeSkin.Overlay && !SelfDrawnNotice.only()) return false
        if (!ClassNoticeOverlay.canDraw(context)) return false
        if (!preferences.headsUpEnabled) return false
        val theme = ClassNoticeGateway.theme(context)
        val screenOn = context.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
        if (screenOn && ClassNoticeOverlay.canShowNow(context)) {
            ClassNoticeOverlay.show(context, content, preferences, theme, onTap = onTap, action = action)
            return ClassNoticeOverlay.awaitShown()
        } else {
            // 锁屏卡片不放按钮，点卡片本身就是「解锁并去组件那儿」
            return ClassNoticeLockActivity.show(context, content, preferences, theme, onTap = onTap, action = action)
        }
    }

    private fun openFeed(context: Context, pluginId: String): () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_EXTENSION_FEED, pluginId),
            )
        }
        Unit
    }

    private fun openSettings(context: Context, pluginId: String): () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_EXTENSION_SETTINGS, pluginId),
            )
        }
        Unit
    }

    /** 系统通知上那个「重新登录」按钮：PendingIntent 需要显式 Intent，和上一条走同一个 extra */
    private fun openSettingsPending(context: Context, pluginId: String): PendingIntent = PendingIntent.getActivity(
        context,
        "settings:$pluginId".hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_EXTENSION_SETTINGS, pluginId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 组件被移除时把它发过的通知都收掉 */
    fun cancelAll(context: Context, pluginId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val group = groupOf(pluginId)
        runCatching {
            manager.activeNotifications
                .filter { it.notification.group == group || it.notification.extras.getString(EXTRA_PLUGIN) == pluginId }
                .forEach { manager.cancel(it.id) }
        }
    }

    private fun base(context: Context, channel: String, pluginId: String, bannerShown: Boolean): NotificationCompat.Builder =
        NotificationCompat.Builder(context, notificationChannel(context, channel, bannerShown))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openFeedIntent(context, pluginId))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addExtras(android.os.Bundle().apply { putString(EXTRA_PLUGIN, pluginId) })

    private fun notificationChannel(context: Context, channel: String, bannerShown: Boolean): String {
        if (!bannerShown) return channel
        val manager = context.getSystemService(NotificationManager::class.java) ?: return channel
        // 用户关闭了原来的渠道时仍用原渠道，不借新渠道绕过系统中的开关。
        if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return channel
        return "$channel.enhanced"
    }

    private fun openFeedIntent(context: Context, pluginId: String): PendingIntent = PendingIntent.getActivity(
        context,
        pluginId.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_EXTENSION_FEED, pluginId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun detailLine(context: Context, item: ExtensionFeedItem): String {
        val due = item.dueAt?.let {
            context.getString(
                R.string.extension_notify_due_at,
                Instant.ofEpochMilli(it).atZone(BeijingTime.zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
            )
        }
        return listOfNotNull(item.course.ifBlank { null }, due, item.author.ifBlank { null }).joinToString(" · ")
    }

    private fun labelOf(item: ExtensionFeedItem): String = item.category.ifBlank { item.type }

    private fun groupOf(pluginId: String) = "extension:$pluginId"

    private fun notificationId(pluginId: String, key: String): Int = ("$pluginId|$key").hashCode()

    private fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CHANNEL_NEW, R.string.extension_channel_new, NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_DUE, R.string.extension_channel_due, NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_STATUS, R.string.extension_channel_status, NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, importance) ->
            if (manager.getNotificationChannel(id) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(id, context.getString(name), importance).apply {
                        description = context.getString(R.string.extension_channel_desc)
                    },
                )
            }
            val quietId = "$id.enhanced"
            if (manager.getNotificationChannel(quietId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        quietId,
                        context.getString(R.string.extension_channel_enhanced, context.getString(name)),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = context.getString(R.string.extension_channel_desc)
                    },
                )
            }
        }
    }

    private const val EXTRA_PLUGIN = "cursimple.extension.plugin"
}
