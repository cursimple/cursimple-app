package com.x500x.cursimple.app

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.x500x.cursimple.R

/**
 * 首次启动时展示阻断式的免责声明对话框；接受后申请应用唯一需要的运行时权限
 * （POST_NOTIFICATIONS，API 33+）。不申请可选权限。
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
            TextButton(onClick = onAccept) { Text(stringResource(R.string.onboarding_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.onboarding_reject)) }
        },
    )
}
