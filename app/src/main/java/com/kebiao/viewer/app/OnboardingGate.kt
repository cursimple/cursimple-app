package com.kebiao.viewer.app

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

/**
 * Renders a blocking disclaimer dialog on first launch; once accepted, requests the
 * single runtime permission this app actually needs (POST_NOTIFICATIONS, API 33+).
 * Optional permissions are intentionally not requested.
 */
@Composable
fun OnboardingGate(
    disclaimerAccepted: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val context = LocalContext.current
    var requestNotification by rememberSaveable { mutableStateOf(false) }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        notificationAsked = true
        requestNotification = false
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
        title = { Text("使用须知") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "本应用为课表查看与提醒工具，仅在你的设备本地运行。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "课表数据由你导入或通过你授权的教务插件获取，应用不会上传到任何服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "课程提醒触发时间依赖系统闹钟与通知机制，可能受到电池优化、勿扰模式或厂商后台限制的影响，作者不对因此造成的迟到、缺席或其它损失承担责任。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "继续使用即表示你已了解并接受以上内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝并退出") }
        },
    )
}
