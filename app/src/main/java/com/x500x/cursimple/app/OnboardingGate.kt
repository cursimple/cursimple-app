package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.x500x.cursimple.R
import com.x500x.cursimple.app.notice.ClassNoticeNotifier
import com.x500x.cursimple.core.data.ClassNoticePreferences

/**
 * 首次启动时展示阻断式的免责声明对话框；接受后申请应用唯一需要的运行时权限
 * （POST_NOTIFICATIONS，API 33+）。不申请可选权限。
 */
@Composable
fun OnboardingGate(
    disclaimerAccepted: Boolean,
    notificationPermissionAskedBefore: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onNotificationAsked: () -> Unit,
) {
    val context = LocalContext.current
    var requestNotification by rememberSaveable { mutableStateOf(false) }
    var notificationAsked by rememberSaveable { mutableStateOf(notificationPermissionAskedBefore) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        notificationAsked = true
        requestNotification = false
        onNotificationAsked()
    }

    LaunchedEffect(requestNotification) {
        if (!requestNotification || notificationAsked) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationAsked = true
            requestNotification = false
            return@LaunchedEffect
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            notificationAsked = true
            requestNotification = false
        } else {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 免责过了但权限这批没问过（老用户从没机会问过）：启动补问一次，拒绝过也绝不再问
    LaunchedEffect(disclaimerAccepted, notificationPermissionAskedBefore) {
        if (!disclaimerAccepted || notificationAsked) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        notificationAsked = true
        if (!granted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        onNotificationAsked()
    }

    if (!disclaimerAccepted) {
        DisclaimerDialog(
            onAccept = {
                onAccept()
                requestNotification = true
            },
            onReject = onReject,
        )
    }
}

/**
 * 启动时提一次「打开状态栏胶囊」。
 *
 * 胶囊默认就开着，但小米的焦点通知、部分系统的实时活动得用户到系统里放行，应用自己开不了。
 * 等通知权限那一步走完、通知确实能发了才提；系统已经放行、机型压根没有胶囊的都不提。
 * 只提这一次，之后交给设置页那行提示和通知引导。
 */
@Composable
fun IslandStartupPrompt(
    classNotice: ClassNoticePreferences,
    notificationPermissionAsked: Boolean,
    promptShown: Boolean,
    onShown: () -> Unit,
) {
    val context = LocalContext.current
    // 系统授权框、系统设置页回来都会 resume，这时重新查一遍
    var checks by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val show = remember(checks, classNotice, notificationPermissionAsked, promptShown) {
        !promptShown &&
            notificationPermissionAsked &&
            classNotice.enabled &&
            classNotice.focusNotificationEnabled &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ClassNoticeNotifier.islandAvailable(context) &&
            ClassNoticeNotifier.islandBlocked(context)
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = onShown,
        title = { Text(stringResource(R.string.island_prompt_title)) },
        text = {
            Text(
                stringResource(R.string.island_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            AppOutlinedButton(
                onClick = {
                    onShown()
                    context.openIslandSettings(ClassNoticeNotifier.channelIdFor(classNotice))
                },
            ) { Text(stringResource(R.string.island_prompt_open)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onShown) { Text(stringResource(R.string.island_prompt_later)) }
        },
    )
}

@Composable
private fun DisclaimerDialog(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* blocking */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.onboarding_disclaimer_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_disclaimer_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.onboarding_disclaimer_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.onboarding_disclaimer_reminder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.onboarding_disclaimer_accept_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onAccept) { Text(stringResource(R.string.onboarding_accept)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onReject) { Text(stringResource(R.string.onboarding_reject)) }
        },
    )
}
