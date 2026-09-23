package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.x500x.cursimple.core.reminder.permission.AlarmPermission
import com.x500x.cursimple.core.reminder.permission.AlarmSettingsIntents
import com.x500x.cursimple.core.reminder.permission.launchFirstAvailableSetting
import com.x500x.cursimple.core.reminder.permission.readAlarmPermissionState

/**
 * 建提醒之前的权限闸门。
 *
 * 没有通知权限或精确闹钟权限时，闹钟根本不会响，这时候让用户把提醒建出来
 * 只会换来一次「到点没响」。所以这一档缺失就拦住，先给授权入口；
 * 电池优化、全屏通知这类只影响可靠性的，列出来提示但不挡路。
 */
@Stable
internal class AlarmPermissionGateState {
    /** 权限补齐后要接着做的事。 */
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    var missing by mutableStateOf<List<AlarmPermission>>(emptyList())
        private set

    val visible: Boolean get() = pendingAction != null

    /** 权限齐了就直接做，缺关键项就先弹授权说明。 */
    fun require(context: Context, action: () -> Unit) {
        val state = readAlarmPermissionState(context)
        // 只差电池白名单这类可靠性项时照常建，提醒留在权限页里说
        if (state.missingBlocking.isEmpty()) {
            action()
            return
        }
        missing = state.missing
        pendingAction = action
    }

    /** 回到前台时重新看一次，补齐了就把原来要做的事接上。 */
    fun refresh(context: Context) {
        if (pendingAction == null) return
        val state = readAlarmPermissionState(context)
        missing = state.missing
        if (state.missingBlocking.isEmpty()) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        }
    }

    fun dismiss() {
        pendingAction = null
    }
}

@Composable
internal fun rememberAlarmPermissionGateState(): AlarmPermissionGateState =
    remember { AlarmPermissionGateState() }

/**
 * 闸门的界面部分：列出缺的权限，每一项点了直接进对应的系统页面。
 * 放在页面里调用一次即可，没有待办时不画任何东西。
 */
@Composable
internal fun AlarmPermissionGateHost(state: AlarmPermissionGateState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            // 系统不再弹窗时只能去设置里开
            launchFirstAvailableSetting(context, AlarmSettingsIntents.notifications(context))
        }
        state.refresh(context)
    }

    // 用户去系统页面开完权限回来，这里立刻接上刚才被拦下的操作
    DisposableEffect(lifecycleOwner, state.visible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!state.visible) return

    AlertDialog(
        onDismissRequest = { state.dismiss() },
        title = { Text(stringResource(R.string.schedule_alarm_permission_gate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.schedule_alarm_permission_gate_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.missing.forEach { permission ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(permission.titleRes()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(permission.reasonRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppOutlinedButton(
                            onClick = {
                                if (
                                    permission == AlarmPermission.Notifications &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val opened = launchFirstAvailableSetting(
                                        context,
                                        AlarmSettingsIntents.forPermission(context, permission),
                                    )
                                    if (!opened) {
                                        showOpenSettingsFailedToast(context)
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.schedule_alarm_permission_gate_grant)) }
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = { state.refresh(context) }) {
                Text(stringResource(R.string.schedule_alarm_permission_gate_recheck))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = { state.dismiss() }) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
        },
    )
}

private fun showOpenSettingsFailedToast(context: Context) {
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.schedule_open_settings_manually),
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

private fun AlarmPermission.titleRes(): Int = when (this) {
    AlarmPermission.Notifications -> R.string.schedule_alarm_permission_notifications
    AlarmPermission.ExactAlarm -> R.string.schedule_alarm_permission_exact_alarm
    AlarmPermission.FullScreenIntent -> R.string.schedule_alarm_permission_full_screen
    AlarmPermission.BatteryUnrestricted -> R.string.schedule_alarm_permission_battery
    AlarmPermission.VendorAutoStart -> R.string.schedule_alarm_permission_autostart
}

private fun AlarmPermission.reasonRes(): Int = when (this) {
    AlarmPermission.Notifications -> R.string.schedule_alarm_permission_notifications_reason
    AlarmPermission.ExactAlarm -> R.string.schedule_alarm_permission_exact_alarm_reason
    AlarmPermission.FullScreenIntent -> R.string.schedule_alarm_permission_full_screen_reason
    AlarmPermission.BatteryUnrestricted -> R.string.schedule_alarm_permission_battery_reason
    AlarmPermission.VendorAutoStart -> R.string.schedule_alarm_permission_autostart_reason
}
