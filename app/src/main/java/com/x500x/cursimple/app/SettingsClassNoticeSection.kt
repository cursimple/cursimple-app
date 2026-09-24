package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.x500x.cursimple.R
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin

/** 设置首页那行的副标题：关着就说关着，开着就报提前多少分钟。 */
@Composable
internal fun classNoticeSubtitle(preferences: ClassNoticePreferences): String =
    if (!preferences.enabled) {
        stringResource(R.string.settings_class_notice_off)
    } else {
        stringResource(R.string.settings_class_notice_on, preferences.advanceMinutes)
    }

/**
 * 「上课通知」设置块。
 *
 * 这套只发通知，不响铃——响铃那套在提醒规则里，两边互不影响，所以单开一块。
 */
@Composable
internal fun ClassNoticeSettingsSection(
    preferences: ClassNoticePreferences,
    onEnabledChange: (Boolean) -> Unit,
    onAdvanceMinutesChange: (Int) -> Unit,
    onHeadsUpChange: (Boolean) -> Unit,
    onLockScreenChange: (Boolean) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSkinChange: (ClassNoticeSkin) -> Unit,
    onAnimationChange: (ClassNoticeAnimation) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurStrengthChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    // 渠道只在第一次发通知时才会建，但「去系统设置」要跳到渠道页——
    // 渠道不存在时系统会直接忽略这个跳转，所以进这一页就先把渠道建出来
    LaunchedEffect(Unit) { ClassNoticeNotifier.ensureChannel(context) }
    // 用户可能刚跳去系统设置改完就回来，回到前台时重新查一次拦没拦
    var blocked by remember { mutableStateOf(ClassNoticeNotifier.systemBlocked(context, preferences)) }
    var islandBlocked by remember { mutableStateOf(ClassNoticeNotifier.islandBlocked(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blocked = ClassNoticeNotifier.systemBlocked(context, preferences)
                islandBlocked = ClassNoticeNotifier.islandBlocked(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSwitchRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.settings_class_notice_enable_title),
        subtitle = stringResource(R.string.settings_class_notice_enable_subtitle),
        checked = preferences.enabled,
        onCheckedChange = onEnabledChange,
    )

    if (preferences.enabled) {
        // 系统那一层被关掉时，应用里怎么开都不会弹——先把话说清楚并给个直达入口
        if (blocked) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_class_notice_blocked_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.settings_class_notice_blocked_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        // 通知权限是这一整块的前提，状态得一眼看见，不用点进系统里才知道
        SettingsActionRow(
            icon = Icons.Rounded.OpenInNew,
            title = stringResource(R.string.settings_class_notice_open_system),
            subtitle = if (blocked) {
                stringResource(R.string.settings_class_notice_permission_off)
            } else {
                stringResource(R.string.settings_class_notice_permission_on)
            },
            onClick = { context.openClassNoticeSettings(ClassNoticeNotifier.channelIdFor(preferences)) },
            trailing = {
                Text(
                    text = if (blocked) {
                        stringResource(R.string.settings_class_notice_permission_badge_off)
                    } else {
                        stringResource(R.string.settings_class_notice_permission_badge_on)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (blocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            },
        )

        AlarmNumberSettingRow(
            title = stringResource(R.string.settings_class_notice_advance),
            value = preferences.advanceMinutes,
            unit = stringResource(R.string.settings_class_notice_minute_unit),
            min = ClassNoticePreferences.MIN_ADVANCE_MINUTES,
            max = ClassNoticePreferences.MAX_ADVANCE_MINUTES,
            step = 1,
            onValueChange = onAdvanceMinutesChange,
            // 1–60 分钟跨度不小，只靠 ±5 的步进要点很多下
            editable = true,
        )

        SettingsSwitchRow(
            icon = Icons.Rounded.VerticalAlignTop,
            title = stringResource(R.string.settings_class_notice_heads_up_title),
            subtitle = stringResource(R.string.settings_class_notice_heads_up_subtitle),
            checked = preferences.headsUpEnabled,
            onCheckedChange = onHeadsUpChange,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.settings_class_notice_lock_title),
            subtitle = stringResource(R.string.settings_class_notice_lock_subtitle),
            checked = preferences.lockScreenEnabled,
            onCheckedChange = onLockScreenChange,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.Star,
            title = stringResource(R.string.settings_class_notice_focus_title),
            subtitle = stringResource(R.string.settings_class_notice_focus_subtitle),
            checked = preferences.focusNotificationEnabled,
            onCheckedChange = onFocusChange,
        )
        // 小米的焦点通知、Android 16 的实时活动都得用户在系统里另外放行，应用里开了也不算数
        if (preferences.focusNotificationEnabled && islandBlocked) {
            SettingsActionRow(
                icon = Icons.Rounded.OpenInNew,
                title = stringResource(R.string.settings_class_notice_open_system),
                subtitle = stringResource(R.string.settings_class_notice_focus_blocked),
                onClick = { context.openIslandSettings(ClassNoticeNotifier.channelIdFor(preferences)) },
            )
        }

        ClassNoticeSkinSettings(
            preferences = preferences,
            onSkinChange = onSkinChange,
            onAnimationChange = onAnimationChange,
            onBlurChange = onBlurChange,
            onBlurStrengthChange = onBlurStrengthChange,
        )
    }

    // 守护和闹钟预告不跟着上课通知的总开关走：只用闹钟、不要上课通知的人也用得上
    ReminderGuardAndAlarmPreNoticeSettings(noticePreferences = preferences)
}

/**
 * 提醒守护与闹钟响前提醒。
 *
 * 自己读写偏好：这一页在设置和「提醒」页两处都有，两处的上层各传一遍开关太容易漏
 * （以前「提醒」页那份就没传，守护开关一直显示关、点了也没反应）。
 */
@Composable
private fun ReminderGuardAndAlarmPreNoticeSettings(noticePreferences: ClassNoticePreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) {
        com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository(context.applicationContext)
    }
    val userPreferences by repository.preferencesFlow.collectAsState(initial = null)
    val current = userPreferences ?: return
    val keepAliveEnabled = current.alarmKeepAliveEnabled
    val preNotice = current.alarmPreNotice
    val noticeTheme = com.x500x.cursimple.app.notice.NoticeTheme.current()

    // 部分手机划掉应用会连带清掉挂着的闹钟和提醒，开关放在这里才找得到
    SettingsSwitchRow(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_alarm_keep_alive_title),
        subtitle = stringResource(
            if (keepAliveEnabled) {
                R.string.settings_alarm_keep_alive_on
            } else {
                R.string.settings_alarm_keep_alive_off
            },
        ),
        checked = keepAliveEnabled,
        onCheckedChange = { enabled -> scope.launch { repository.setAlarmKeepAliveEnabled(enabled) } },
    )

    SettingsSectionHeader(stringResource(R.string.settings_alarm_pre_notice_section))
    SettingsSwitchRow(
        icon = Icons.Rounded.Alarm,
        title = stringResource(R.string.settings_alarm_pre_notice_title),
        subtitle = stringResource(R.string.settings_alarm_pre_notice_subtitle),
        checked = preNotice.enabled,
        onCheckedChange = { enabled -> scope.launch { repository.setAlarmPreNoticeEnabled(enabled) } },
    )
    if (preNotice.enabled) {
        AlarmNumberSettingRow(
            title = stringResource(R.string.settings_alarm_pre_notice_advance),
            value = preNotice.advanceMinutes,
            unit = stringResource(R.string.settings_class_notice_minute_unit),
            min = com.x500x.cursimple.core.data.AlarmPreNoticePreferences.MIN_ADVANCE_MINUTES,
            max = com.x500x.cursimple.core.data.AlarmPreNoticePreferences.MAX_ADVANCE_MINUTES,
            step = 1,
            onValueChange = { minutes -> scope.launch { repository.setAlarmPreNoticeAdvanceMinutes(minutes) } },
            editable = true,
        )
        SettingsActionRow(
            icon = Icons.Rounded.Visibility,
            title = stringResource(R.string.settings_alarm_pre_notice_preview),
            subtitle = stringResource(R.string.settings_alarm_pre_notice_preview_subtitle),
            onClick = {
                val app = context.applicationContext
                ClassNoticeNotifier.cancelAlarmPreview(app)
                ClassNoticeNotifier.notify(
                    app,
                    ClassNoticeNotifier.alarmPreviewSample(app, preNotice.advanceMinutes),
                    noticePreferences,
                    noticeTheme,
                )
            },
        )
    }
}

/**
 * 换肤那一段。
 *
 * 三档能换的东西差别很大，所以每一档都把天花板写在副标题里，别让人选完才发现动效没有：
 * 系统原生完全交给系统；品牌卡片只能换内容区的底色排版（Android 12 起自定义通知
 * 一律被套上系统头部）；动效和毛玻璃只有自绘悬浮窗做得到。
 */
@Composable
private fun ClassNoticeSkinSettings(
    preferences: ClassNoticePreferences,
    onSkinChange: (ClassNoticeSkin) -> Unit,
    onAnimationChange: (ClassNoticeAnimation) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurStrengthChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val noticeTheme = com.x500x.cursimple.app.notice.NoticeTheme.current()
    SettingsSectionHeader(stringResource(R.string.settings_class_notice_skin_header))

    ClassNoticeSkinOption(
        title = stringResource(R.string.settings_class_notice_skin_system),
        description = stringResource(R.string.settings_class_notice_skin_system_desc),
        selected = preferences.skin == ClassNoticeSkin.System,
        onClick = { onSkinChange(ClassNoticeSkin.System) },
    )
    ClassNoticeSkinOption(
        title = stringResource(R.string.settings_class_notice_skin_card),
        description = stringResource(R.string.settings_class_notice_skin_card_desc),
        selected = preferences.skin == ClassNoticeSkin.Card,
        onClick = { onSkinChange(ClassNoticeSkin.Card) },
    )
    ClassNoticeSkinOption(
        title = stringResource(R.string.settings_class_notice_skin_overlay),
        description = stringResource(R.string.settings_class_notice_skin_overlay_desc),
        selected = preferences.skin == ClassNoticeSkin.Overlay,
        onClick = { onSkinChange(ClassNoticeSkin.Overlay) },
    )

    if (preferences.skin == ClassNoticeSkin.Overlay) {
        // 用户可能刚去系统里授完权回来，回到前台重新查一次
        var canDraw by remember { mutableStateOf(ClassNoticeOverlay.canDraw(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canDraw = ClassNoticeOverlay.canDraw(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (canDraw) {
            SettingsActionRow(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.settings_class_notice_overlay_granted),
                subtitle = stringResource(R.string.settings_class_notice_overlay_granted_desc),
                onClick = { context.openOverlayPermissionSettings() },
            )
        } else {
            SettingsActionRow(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.settings_class_notice_overlay_permission),
                subtitle = stringResource(R.string.settings_class_notice_overlay_permission_desc),
                onClick = { context.openOverlayPermissionSettings() },
            )
        }

        ClassNoticeAnimationPicker(
            selected = preferences.animation,
            onSelect = onAnimationChange,
        )
        SettingsSwitchRow(
            icon = Icons.Rounded.BlurOn,
            title = stringResource(R.string.settings_class_notice_blur_title),
            subtitle = stringResource(R.string.settings_class_notice_blur_subtitle),
            checked = preferences.blurEnabled,
            onCheckedChange = onBlurChange,
        )
        if (preferences.blurEnabled) {
            AlarmNumberSettingRow(
                title = stringResource(R.string.settings_class_notice_blur_strength),
                value = preferences.blurStrength,
                unit = stringResource(R.string.settings_class_notice_percent_unit),
                min = ClassNoticePreferences.MIN_BLUR_STRENGTH,
                max = ClassNoticePreferences.MAX_BLUR_STRENGTH,
                step = 10,
                onValueChange = onBlurStrengthChange,
            )
        }
    }

    SettingsActionRow(
        icon = Icons.Rounded.Visibility,
        title = stringResource(R.string.settings_class_notice_preview),
        subtitle = stringResource(R.string.settings_class_notice_preview_desc),
        onClick = { ClassNoticeNotifier.notifyPreview(context, preferences, noticeTheme) },
    )

    // 厂商胶囊这块我们插不上手，说清楚比让人反复试要好
    Text(
        text = stringResource(R.string.settings_class_notice_island_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** 一档皮肤。整行可点，选中的描边加重。 */
@Composable
private fun ClassNoticeSkinOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 入场动效三选一。 */
@Composable
private fun ClassNoticeAnimationPicker(
    selected: ClassNoticeAnimation,
    onSelect: (ClassNoticeAnimation) -> Unit,
) {
    val options = listOf(
        ClassNoticeAnimation.None to stringResource(R.string.settings_class_notice_animation_none),
        ClassNoticeAnimation.Slide to stringResource(R.string.settings_class_notice_animation_slide),
        ClassNoticeAnimation.Spring to stringResource(R.string.settings_class_notice_animation_spring),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.settings_class_notice_animation),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    val active = value == selected
                    AppOutlinedButton(
                        onClick = { onSelect(value) },
                        border = BorderStroke(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                    ) {
                        Text(text = label)
                    }
                }
            }
        }
    }
}

/** 跳系统的「显示在其他应用上层」授权页。 */
internal fun android.content.Context.openOverlayPermissionSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(android.net.Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // 有些机型没有这个页面，退回应用详情页
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (candidate in listOf(intent, fallback)) {
        if (packageManager.resolveActivity(candidate, 0) == null) continue
        if (runCatching { startActivity(candidate) }.isSuccess) return
    }
}

/** 实时活动有单独的放行页（Android 16 QPR1 起）；没有这页就去通知设置，小米的焦点通知开关也在那里。 */
private fun android.content.Context.openIslandSettings(channelId: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val promotion = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageManager.resolveActivity(promotion, 0) != null &&
            runCatching { startActivity(promotion) }.isSuccess
        ) {
            return
        }
    }
    openClassNoticeSettings(channelId)
}

/** 直达本应用的通知设置；跳不过去就退回应用详情页。 */
private fun android.content.Context.openClassNoticeSettings(channelId: String) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
            )
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null)),
        )
    }
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // startActivity 对「能打开但页面空白」的跳转不会抛异常，
        // 所以先问 PackageManager 有没有人接，接不住就换下一个
        if (packageManager.resolveActivity(intent, 0) == null) continue
        val launched = runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return
    }
}
