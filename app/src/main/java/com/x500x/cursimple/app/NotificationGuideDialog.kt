package com.x500x.cursimple.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.x500x.cursimple.R
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.app.notice.ClassNoticeOverlay
import com.x500x.cursimple.app.notice.SelfDrawnNotice
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
import com.x500x.cursimple.core.reminder.permission.MiuiPermissions
import com.x500x.cursimple.core.reminder.permission.VendorRom

/** Build a device-specific notification setup checklist and refresh it on resume. */

internal data class NotificationGuideStep(
    val title: String,
    val subtitle: String,
    val isDone: Boolean,
    val onJump: () -> Unit,
)

@Composable
internal fun NotificationGuideDialog(
    preferences: ClassNoticePreferences,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var steps by remember { mutableStateOf(emptyList<NotificationGuideStep>()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {   }

    fun refresh() {
        steps = buildNotificationGuideSteps(
            context = context.applicationContext,
            preferences = preferences,
            requestNotifications = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(preferences) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notice_guide_close))
            }
        },
        title = { Text(stringResource(R.string.notice_guide_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.notice_guide_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (steps.isNotEmpty() && steps.all { it.isDone }) {
                    Text(
                        text = stringResource(R.string.notice_guide_all_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                steps.forEach { step ->
                    NotificationGuideStepRow(step)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        },
    )
}

/** Incomplete steps open system settings; completed steps show their status. */
@Composable
private fun NotificationGuideStepRow(step: NotificationGuideStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = !step.isDone, onClick = step.onJump),
    ) {
        if (step.isDone) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = step.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = step.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!step.isDone) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** List only permission states that can be queried on this device. */
internal fun buildNotificationGuideSteps(
    context: Context,
    preferences: ClassNoticePreferences,
    requestNotifications: () -> Unit,
): List<NotificationGuideStep> {
    val isXiaomi = VendorRom.current() == VendorRom.Xiaomi
    return buildList {
        add(
            NotificationGuideStep(
                title = context.getString(R.string.notice_guide_step_master_title),
                subtitle = context.getString(R.string.notice_guide_step_master_subtitle),
                isDone = !ClassNoticeNotifier.masterNotificationsBlocked(context),
                onJump = {
                    // A disabled notification master switch requires system settings after runtime permission is granted.
                    val needsRuntimeRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (needsRuntimeRequest) {
                        requestNotifications()
                    } else {
                        context.launchFirstSetting(AlarmSettingsIntents.notifications(context))
                    }
                },
            ),
        )
        add(
            NotificationGuideStep(
                title = context.getString(R.string.notice_guide_step_channel_title),
                subtitle = context.getString(
                    if (isXiaomi) {
                        R.string.notice_guide_step_channel_subtitle_miui
                    } else {
                        R.string.notice_guide_step_channel_subtitle
                    },
                ),
                isDone = !ClassNoticeNotifier.channelBlocked(context, preferences),
                onJump = {
                    context.openClassNoticeSettings(ClassNoticeNotifier.channelIdFor(preferences))
                },
            ),
        )
        // Overlay-only devices and overlay skins require draw-over-apps permission.
        if (preferences.skin == ClassNoticeSkin.Overlay ||
            (preferences.headsUpEnabled && SelfDrawnNotice.only())
        ) {
            add(
                NotificationGuideStep(
                    title = context.getString(R.string.notice_guide_step_overlay_title),
                    subtitle = context.getString(R.string.notice_guide_step_overlay_subtitle),
                    isDone = ClassNoticeOverlay.canDraw(context),
                    onJump = { context.openOverlayPermissionSettings() },
                ),
            )
        }
        // Show chip setup only on systems with supported focus or live-update notifications.
        if (ClassNoticeNotifier.islandAvailable(context)) {
            add(
                NotificationGuideStep(
                    title = context.getString(R.string.notice_guide_step_island_title),
                    subtitle = context.getString(
                        if (isXiaomi) {
                            R.string.notice_guide_step_island_subtitle_miui
                        } else {
                            R.string.notice_guide_step_island_subtitle
                        },
                    ),
                    isDone = !ClassNoticeNotifier.islandBlocked(context),
                    onJump = {
                        context.openIslandSettings(ClassNoticeNotifier.channelIdFor(preferences))
                    },
                ),
            )
        }
        // Background pop-up status is queryable on supported Xiaomi devices only.
        if (isXiaomi) {
            add(
                NotificationGuideStep(
                    title = context.getString(R.string.notice_guide_step_popup_title),
                    subtitle = context.getString(R.string.notice_guide_step_popup_subtitle),
                    isDone = MiuiPermissions.backgroundStartActivity(context) != false,
                    onJump = {
                        context.launchFirstSetting(AlarmSettingsIntents.backgroundPopup(context))
                    },
                ),
            )
        }
    }
}

/** Whether notification setup has any incomplete required steps. */
internal fun hasPendingNoticeGuideStep(
    context: Context,
    preferences: ClassNoticePreferences,
): Boolean = buildNotificationGuideSteps(context, preferences, requestNotifications = {})
    .any { !it.isDone }

private fun Context.launchFirstSetting(intents: List<Intent>) {
    for (candidate in intents) {
        candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Try vendor activities directly; package resolution can hide otherwise valid destinations.
        if (runCatching { startActivity(candidate) }.isSuccess) return
    }
    Toast.makeText(
        this,
        getString(R.string.settings_toast_open_settings_manually),
        Toast.LENGTH_LONG,
    ).show()
}
