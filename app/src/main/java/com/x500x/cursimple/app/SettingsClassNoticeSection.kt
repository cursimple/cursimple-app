package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.app.notice.SelfDrawnNotice
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin

@Composable
internal fun classNoticeSubtitle(preferences: ClassNoticePreferences): String =
    if (!preferences.enabled) {
        stringResource(R.string.settings_class_notice_off)
    } else {
        stringResource(R.string.settings_class_notice_on, preferences.advanceMinutes)
    }

/** Class notices and ringing alarms have independent settings. */
@Composable
internal fun ClassNoticeSettingsSection(
    preferences: ClassNoticePreferences,
    onEnabledChange: (Boolean) -> Unit,
    onAdvanceMinutesChange: (Int) -> Unit,
    onHeadsUpChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit = {},
    onLockScreenChange: (Boolean) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSkinChange: (ClassNoticeSkin) -> Unit,
    onAnimationChange: (ClassNoticeAnimation) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurStrengthChange: (Int) -> Unit,
    onBannerDurationChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    // Create the notification channel before opening its system settings page.
    LaunchedEffect(Unit) { ClassNoticeNotifier.ensureChannel(context) }
    var blocked by remember { mutableStateOf(ClassNoticeNotifier.systemBlocked(context, preferences)) }
    var islandBlocked by remember { mutableStateOf(ClassNoticeNotifier.islandBlocked(context)) }
    var canDrawOverlay by remember { mutableStateOf(ClassNoticeOverlay.canDraw(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blocked = ClassNoticeNotifier.systemBlocked(context, preferences)
                islandBlocked = ClassNoticeNotifier.islandBlocked(context)
                canDrawOverlay = ClassNoticeOverlay.canDraw(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showGuide by remember { mutableStateOf(false) }
    var guideAutoShown by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    SettingsSwitchRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.settings_class_notice_enable_title),
        subtitle = stringResource(R.string.settings_class_notice_enable_subtitle),
        checked = preferences.enabled,
        onCheckedChange = { enabled ->
            onEnabledChange(enabled)
            if (enabled && !guideAutoShown &&
                hasPendingNoticeGuideStep(context.applicationContext, preferences)
            ) {
                guideAutoShown = true
                showGuide = true
            }
        },
    )
    if (showGuide) {
        NotificationGuideDialog(preferences = preferences, onDismiss = { showGuide = false })
    }
    SettingsActionRow(
        icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.settings_dev_notice_diagnostics_title),
        subtitle = stringResource(R.string.settings_dev_notice_diagnostics_subtitle),
        onClick = { showDiagnostics = true },
    )
    if (showDiagnostics) {
        ClassNoticeDiagnosticsDialog(preferences, onDismiss = { showDiagnostics = false })
    }

    if (preferences.enabled) {
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
        // Explain vendor registration requirements even when system permissions appear granted.
        val selfDrawnReason = SelfDrawnNotice.reason()
        if (!blocked && selfDrawnReason != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_class_notice_self_drawn_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = stringResource(selfDrawnReason.bodyRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            SettingsActionRow(
                icon = Icons.Rounded.Layers,
                title = stringResource(
                    if (canDrawOverlay) {
                        R.string.settings_class_notice_overlay_granted
                    } else {
                        R.string.settings_class_notice_overlay_permission
                    },
                ),
                subtitle = stringResource(
                    if (canDrawOverlay) {
                        R.string.settings_class_notice_self_drawn_overlay_on
                    } else {
                        R.string.settings_class_notice_self_drawn_overlay_off
                    },
                ),
                onClick = { context.openOverlayPermissionSettings() },
                trailing = {
                    Text(
                        text = stringResource(
                            if (canDrawOverlay) {
                                R.string.settings_class_notice_permission_badge_on
                            } else {
                                R.string.settings_class_notice_permission_badge_off
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canDrawOverlay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                },
            )
            // Offer direct settings links for vendor permissions whose state cannot be queried.
            SettingsActionRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.settings_class_notice_self_drawn_lock_title),
                subtitle = stringResource(selfDrawnReason.lockSubtitleRes()),
                onClick = {
                    launchSettingsIntents(
                        context,
                        com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents.backgroundPopup(context),
                    )
                },
            )
        }
        // Show notification permission status before the dependent controls.
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
        // Open the device-specific setup checklist.
        SettingsActionRow(
            icon = Icons.Rounded.Route,
            title = stringResource(R.string.settings_class_notice_guide_row_title),
            subtitle = stringResource(R.string.settings_class_notice_guide_row_subtitle),
            onClick = { showGuide = true },
        )

        AlarmNumberSettingRow(
            title = stringResource(R.string.settings_class_notice_advance),
            value = preferences.advanceMinutes,
            unit = stringResource(R.string.settings_class_notice_minute_unit),
            min = ClassNoticePreferences.MIN_ADVANCE_MINUTES,
            max = ClassNoticePreferences.MAX_ADVANCE_MINUTES,
            step = 1,
            onValueChange = onAdvanceMinutesChange,
            // Allow direct minute entry alongside step controls.
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
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.settings_class_notice_vibration_title),
            subtitle = stringResource(R.string.settings_class_notice_vibration_subtitle),
            checked = preferences.vibrationEnabled,
            onCheckedChange = onVibrationChange,
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
        // System approval is required independently of the in-app chip switch.
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
            onBannerDurationChange = onBannerDurationChange,
        )
    }

    // Guard and pre-alarm settings are independent of class-notice enablement.
    ReminderGuardAndAlarmPreNoticeSettings(noticePreferences = preferences)
}

/** Read preferences directly because this section is shared by Settings and Reminders. */
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

    // Silent guard alarms and jobs recover registrations without a persistent service notification.
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
                if (!warnIfPreviewBlocked(context, noticePreferences)) {
                    ClassNoticeNotifier.cancelAlarmPreview(app)
                    ClassNoticeNotifier.notify(
                        app,
                        ClassNoticeNotifier.alarmPreviewSample(app, preNotice.advanceMinutes),
                        noticePreferences,
                        noticeTheme,
                    )
                }
            },
        )
    }
}

/** System skins control different layers; animation and blur require custom overlays. */
@Composable
private fun ClassNoticeSkinSettings(
    preferences: ClassNoticePreferences,
    onSkinChange: (ClassNoticeSkin) -> Unit,
    onAnimationChange: (ClassNoticeAnimation) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurStrengthChange: (Int) -> Unit,
    onBannerDurationChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noticeTheme = com.x500x.cursimple.app.notice.NoticeTheme.current()
    SettingsSectionHeader(stringResource(R.string.settings_class_notice_skin_header))

    // Vendor enhancements supplement the system notification.
    val selfDrawnOnly = SelfDrawnNotice.only()
    ClassNoticeSkinOptions(preferences, onSkinChange)

    if (preferences.skin == ClassNoticeSkin.Overlay || selfDrawnOnly) {
        if (!selfDrawnOnly) OverlayPermissionRow()

        AlarmNumberSettingRow(
            title = stringResource(R.string.settings_class_notice_banner_duration),
            value = preferences.bannerDurationSeconds,
            unit = stringResource(R.string.settings_class_notice_seconds_unit),
            min = ClassNoticePreferences.MIN_BANNER_DURATION_SECONDS,
            max = ClassNoticePreferences.MAX_BANNER_DURATION_SECONDS,
            step = 5,
            onValueChange = onBannerDurationChange,
            editable = true,
        )
        Text(
            text = stringResource(R.string.settings_class_notice_banner_duration_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
        onClick = {
            if (!warnIfPreviewBlocked(context, preferences)) {
                val app = context.applicationContext
                scope.launch {
                    ClassNoticeNotifier.notifyPreview(
                        context = app,
                        content = ClassNoticeGateway.previewContent(app, preferences),
                        preferences = preferences,
                        theme = noticeTheme,
                    )
                }
            }
        },
    )

    Text(
        text = stringResource(R.string.settings_class_notice_island_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

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

internal fun android.content.Context.openOverlayPermissionSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(android.net.Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (candidate in listOf(intent, fallback)) {
        if (packageManager.resolveActivity(candidate, 0) == null) continue
        if (runCatching { startActivity(candidate) }.isSuccess) return
    }
    toastSettingsGoneSilently()
}

/** Open live-update settings when available, otherwise notification settings. */
internal fun android.content.Context.openIslandSettings(channelId: String) {
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

internal fun android.content.Context.openClassNoticeSettings(channelId: String) {
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
        // Check resolvable activities to avoid vendor destinations that open blank pages.
        if (packageManager.resolveActivity(intent, 0) == null) continue
        val launched = runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return
    }
    toastSettingsGoneSilently()
}

private fun android.content.Context.toastSettingsGoneSilently() {
    Toast.makeText(
        this,
        getString(R.string.settings_toast_open_settings_manually),
        Toast.LENGTH_LONG,
    ).show()
}

/** Return true when missing permission intercepts the preview action. */
private fun warnIfPreviewBlocked(
    context: android.content.Context,
    preferences: ClassNoticePreferences,
): Boolean {
    if (ClassNoticeNotifier.systemBlocked(context, preferences)) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_toast_preview_blocked),
            Toast.LENGTH_SHORT,
        ).show()
        context.openClassNoticeSettings(ClassNoticeNotifier.channelIdFor(preferences))
        return true
    }
    // Check overlay permission before a preview can silently fall back to system banners.
    if (preferences.skin == ClassNoticeSkin.Overlay && !ClassNoticeOverlay.canDraw(context)) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_toast_preview_no_overlay),
            Toast.LENGTH_SHORT,
        ).show()
        context.openOverlayPermissionSettings()
        return true
    }
    // Explain unavailable system banners on overlay-only devices before previewing.
    if (preferences.headsUpEnabled && SelfDrawnNotice.only() && !ClassNoticeOverlay.canDraw(context)) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_toast_preview_self_drawn_no_overlay),
            Toast.LENGTH_LONG,
        ).show()
    }
    return false
}

@Composable
private fun ClassNoticeSkinOptions(
    preferences: ClassNoticePreferences,
    onSkinChange: (ClassNoticeSkin) -> Unit,
) {
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
}

/** Refresh overlay permission when returning from system settings. */
@Composable
private fun OverlayPermissionRow() {
    val context = LocalContext.current
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
}

private fun SelfDrawnNotice.Reason.bodyRes(): Int = when (this) {
    SelfDrawnNotice.Reason.Vivo -> R.string.settings_class_notice_self_drawn_body_vivo
    SelfDrawnNotice.Reason.Huawei -> R.string.settings_class_notice_self_drawn_body_huawei
}

private fun SelfDrawnNotice.Reason.lockSubtitleRes(): Int = when (this) {
    SelfDrawnNotice.Reason.Vivo -> R.string.settings_class_notice_self_drawn_lock_subtitle_vivo
    SelfDrawnNotice.Reason.Huawei -> R.string.settings_class_notice_self_drawn_lock_subtitle_other
}
