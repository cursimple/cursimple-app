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

/** Gate creation on blocking reminder access; present reliability-only access as advice. */
@Stable
internal class AlarmPermissionGateState {
    /** Continuation after required access is granted. */
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        private set

    var missing by mutableStateOf<List<AlarmPermission>>(emptyList())
        private set

    val visible: Boolean get() = pendingAction != null

    /** Proceed with access or defer the action through permission guidance. */
    fun require(context: Context, action: () -> Unit) {
        val state = readAlarmPermissionState(context)
        // Advisory battery access does not block creation.
        if (state.missingBlocking.isEmpty()) {
            action()
            return
        }
        missing = state.missing
        pendingAction = action
    }

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

/** Render missing-access destinations only while an action is pending. */
@Composable
internal fun AlarmPermissionGateHost(state: AlarmPermissionGateState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            // Permanent denial requires settings rather than another runtime prompt.
            launchFirstAvailableSetting(context, AlarmSettingsIntents.notifications(context))
        }
        state.refresh(context)
    }

    // Resume the deferred action when returning with permission.
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
