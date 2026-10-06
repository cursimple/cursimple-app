package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** Gate first launch on acceptance, then request POST_NOTIFICATIONS on API 33+. */
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

    // Request notification permission once; do not repeat a denied request.
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

/** Offer chip setup once after notifications become available, only on supported devices. */
@Composable
fun IslandStartupPrompt(
    classNotice: ClassNoticePreferences,
    notificationPermissionAsked: Boolean,
    promptShown: Boolean,
    onShown: () -> Unit,
) {
    val context = LocalContext.current
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
