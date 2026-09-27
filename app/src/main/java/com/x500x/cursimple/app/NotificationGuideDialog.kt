package com.x500x.cursimple.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
import com.x500x.cursimple.core.reminder.permission.MiuiPermissions
import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * 「通知弹出引导」弹窗。
 *
 * 通知要能弹出来，每个品牌手机要放行的项不一样（小米要焦点通知、悬浮窗、后台弹出；
 * Android 16 要实时活动；老系统只有总开关和渠道）。这份清单按这台机型和当前皮肤现场
 * 组装，逐项带进度的状态点未完成项就跳到对应的系统设置页；从系统设置回来（ON_RESUME）
 * 自动重查。
 */

/** 引导里的一项：标题、说明、现在放行了没有，以及点了跳到哪。 */
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
    ) { /* 结果以重查为准，别信返回的 Boolean——之前拒过再回来的状态而已 */ }

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

/** 一键放行项：未完成的可点，点完直接跳系统页；完成的只亮对勾。 */
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

/**
 * 按机型与当前皮肤组装这张清单。条件和跳转哪都写在代码里，别藏布局：
 * 查得到的状态才进清单（小米的几项不开 DevOps 查询接口时直接不出现，编造状态只会误事）。
 */
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
                    // 运行时权限还没给就弹申请；权限给了但用户后来在系统里关了总开关的，
                    // 再弹申请框什么都没用，只能去系统通知设置里手动开回来
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
        // 悬浮窗皮肤才需要悬浮窗权限，其余皮肤用不上这份提示
        if (preferences.skin == ClassNoticeSkin.Overlay) {
            add(
                NotificationGuideStep(
                    title = context.getString(R.string.notice_guide_step_overlay_title),
                    subtitle = context.getString(R.string.notice_guide_step_overlay_subtitle),
                    isDone = ClassNoticeOverlay.canDraw(context),
                    onJump = { context.openOverlayPermissionSettings() },
                ),
            )
        }
        // 状态栏胶囊：小米焦点通知或 Android 16 实时活动能出的机型才有这项（One UI 8.5 前没有）
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
        // 小米的「后台弹出界面」能查到真实状态，只有小米才露这一项
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

/** 有没有还没放行的一步；总开关打开时用它在要不要自动弹之间取舍。 */
internal fun hasPendingNoticeGuideStep(
    context: Context,
    preferences: ClassNoticePreferences,
): Boolean = buildNotificationGuideSteps(context, preferences, requestNotifications = {})
    .any { !it.isDone }

/** 候选 Intent 逐个试，第一个打得开的就用；都不存在就提示用户自己动手。 */
private fun Context.launchFirstSetting(intents: List<Intent>) {
    for (candidate in intents) {
        candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 不做 resolveActivity 预检：部分厂商把设置页藏起来，包过滤会漏掉真存在的页面；
        // 跟权限页一样直接试，起不来才换下一个
        if (runCatching { startActivity(candidate) }.isSuccess) return
    }
    Toast.makeText(
        this,
        getString(R.string.settings_toast_open_settings_manually),
        Toast.LENGTH_LONG,
    ).show()
}
