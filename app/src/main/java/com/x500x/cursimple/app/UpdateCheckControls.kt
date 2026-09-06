@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.R
import com.x500x.cursimple.app.update.AppUpdateCheckResult
import com.x500x.cursimple.app.update.AppUpdateChecker
import com.x500x.cursimple.app.update.AppUpdateDownloadResult
import com.x500x.cursimple.app.update.AppUpdateInfo
import com.x500x.cursimple.app.update.AppUpdateInstaller
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.update.UpdateNoticeState
import com.x500x.cursimple.app.update.UpdatePanelStatus
import com.x500x.cursimple.app.update.shouldPromptUpdate
import com.x500x.cursimple.app.update.shouldShowUpdateBadge
import com.x500x.cursimple.app.update.updateStatusText
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateCheckSection(
    autoCheckEnabled: Boolean,
    betaUpdatesEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    updateNotice: UpdateNoticeState,
    onAutoCheckEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onMuteUpdateVersion: (Int?) -> Unit,
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker(downloaderLabels = context.mirrorDownloaderLabels()) }
    var checking by rememberSaveable { mutableStateOf(false) }
    var downloading by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<UpdatePanelStatus>(UpdatePanelStatus.Idle) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var pendingRollback by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var autoCheckedForCurrentEntry by rememberSaveable { mutableStateOf(false) }

    fun dismissPendingUpdate() {
        pendingUpdate = null
        pendingRollback = null
        downloadedApk = null
    }

    fun downloadAndInstall(info: AppUpdateInfo) {
        if (downloading) return
        val downloaded = downloadedApk
        if (downloaded != null && downloaded.exists()) {
            AppUpdateInstaller.openInstall(context, downloaded)
            return
        }
        scope.launch {
            downloading = true
            status = UpdatePanelStatus.Downloading(info.asset.fileName)
            when (val result = checker.download(context, info)) {
                is AppUpdateDownloadResult.Success -> {
                    downloadedApk = result.file
                    status = UpdatePanelStatus.Downloaded(result.sourceName)
                    AppUpdateInstaller.openInstall(context, result.file)
                }
                is AppUpdateDownloadResult.Failure -> {
                    status = UpdatePanelStatus.Failed(result.reason)
                }
            }
            downloading = false
        }
    }

    fun checkUpdate(manual: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            status = UpdatePanelStatus.Checking
            dismissPendingUpdate()
            when (val result = checker.check(includePrerelease = betaUpdatesEnabled)) {
                AppUpdateCheckResult.NoRelease -> {
                    onUpdateNoticeCleared()
                    status = UpdatePanelStatus.NoRelease
                }
                AppUpdateCheckResult.ManifestMissing -> status = UpdatePanelStatus.ManifestMissing
                AppUpdateCheckResult.UpToDate -> {
                    onUpdateNoticeCleared()
                    status = UpdatePanelStatus.UpToDate
                }
                is AppUpdateCheckResult.Available -> {
                    onUpdateFound(result.info.versionCode, result.info.versionName)
                    val ignored = ignoredUpdateVersionCode == result.info.versionCode
                    val muted = updateNotice.mutedVersionCode == result.info.versionCode
                    when {
                        manual -> {
                            pendingUpdate = result.info
                            status = UpdatePanelStatus.Available(result.info.versionName)
                        }
                        ignored -> status = UpdatePanelStatus.Ignored(result.info.versionName)
                        muted -> status = UpdatePanelStatus.Muted(result.info.versionName)
                        else -> status = UpdatePanelStatus.Available(result.info.versionName)
                    }
                }
                is AppUpdateCheckResult.Rollback -> {
                    pendingRollback = result.info
                    status = UpdatePanelStatus.Rollback(result.info.versionName)
                }
                is AppUpdateCheckResult.Failure -> status = UpdatePanelStatus.Failed(result.reason)
            }
            checking = false
        }
    }

    LaunchedEffect(autoCheckEnabled) {
        if (!autoCheckEnabled) {
            autoCheckedForCurrentEntry = false
            return@LaunchedEffect
        }
        if (autoCheckEnabled && !autoCheckedForCurrentEntry) {
            autoCheckedForCurrentEntry = true
            checkUpdate(manual = false)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UpdateSwitchRow(
            icon = Icons.Rounded.Autorenew,
            title = stringResource(R.string.update_auto_check_title),
            subtitle = if (autoCheckEnabled) stringResource(R.string.update_auto_check_on) else stringResource(R.string.update_auto_check_off),
            checked = autoCheckEnabled,
            onCheckedChange = onAutoCheckEnabledChange,
        )
        UpdateActionRow(
            icon = Icons.Rounded.SystemUpdate,
            title = stringResource(R.string.update_check_title),
            subtitle = updatePanelStatusText(status),
            badge = shouldShowUpdateBadge(updateNotice, BuildConfig.VERSION_CODE),
            enabled = !checking && !downloading,
            buttonText = if (checking) stringResource(R.string.update_check_checking) else stringResource(R.string.update_check_button),
            onClick = { checkUpdate(manual = true) },
        )
    }

    pendingUpdate?.let { info ->
        UpdateAvailableDialog(
            info = info,
            downloading = downloading,
            downloadedApk = downloadedApk,
            onUpdate = { downloadAndInstall(info) },
            onIgnore = {
                onIgnoreUpdateVersion(info.versionCode)
                status = UpdatePanelStatus.IgnoredManual(info.versionName)
                dismissPendingUpdate()
            },
            onMute = {
                onMuteUpdateVersion(info.versionCode)
                status = UpdatePanelStatus.Muted(info.versionName)
                dismissPendingUpdate()
            },
            onDismiss = { dismissPendingUpdate() },
        )
    }

    pendingRollback?.let { info ->
        UpdateRollbackDialog(
            info = info,
            downloading = downloading,
            downloadedApk = downloadedApk,
            onDownload = { downloadAndInstall(info) },
            onDismiss = { dismissPendingUpdate() },
        )
    }
}

@Composable
fun AutomaticUpdateCheckPrompt(
    autoCheckEnabled: Boolean,
    betaUpdatesEnabled: Boolean,
    updateNotice: UpdateNoticeState,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onMuteUpdateVersion: (Int?) -> Unit,
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker(downloaderLabels = context.mirrorDownloaderLabels()) }
    var checkedThisSession by rememberSaveable { mutableStateOf(false) }
    var promptedThisSession by rememberSaveable { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloading by rememberSaveable { mutableStateOf(false) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    fun dismissPendingUpdate() {
        pendingUpdate = null
        downloadedApk = null
    }

    fun downloadAndInstall(info: AppUpdateInfo) {
        if (downloading) return
        val downloaded = downloadedApk
        if (downloaded != null && downloaded.exists()) {
            AppUpdateInstaller.openInstall(context, downloaded)
            return
        }
        scope.launch {
            downloading = true
            when (val result = checker.download(context, info)) {
                is AppUpdateDownloadResult.Success -> {
                    downloadedApk = result.file
                    AppUpdateInstaller.openInstall(context, result.file)
                }
                is AppUpdateDownloadResult.Failure -> {
                    Toast.makeText(context, context.updateStatusText(result.reason), Toast.LENGTH_SHORT).show()
                }
            }
            downloading = false
        }
    }

    LaunchedEffect(autoCheckEnabled) {
        if (!autoCheckEnabled) {
            checkedThisSession = false
            return@LaunchedEffect
        }
        if (checkedThisSession) return@LaunchedEffect
        checkedThisSession = true
        when (val result = checker.check(includePrerelease = betaUpdatesEnabled)) {
            is AppUpdateCheckResult.Available -> {
                onUpdateFound(result.info.versionCode, result.info.versionName)
                val found = updateNotice.copy(
                    versionCode = result.info.versionCode,
                    versionName = result.info.versionName,
                )
                if (shouldPromptUpdate(found, BuildConfig.VERSION_CODE, promptedThisSession)) {
                    promptedThisSession = true
                    pendingUpdate = result.info
                }
            }
            AppUpdateCheckResult.UpToDate, AppUpdateCheckResult.NoRelease -> onUpdateNoticeCleared()
            else -> Unit
        }
    }

    pendingUpdate?.let { info ->
        UpdateAvailableDialog(
            info = info,
            downloading = downloading,
            downloadedApk = downloadedApk,
            onUpdate = { downloadAndInstall(info) },
            onIgnore = {
                onIgnoreUpdateVersion(info.versionCode)
                dismissPendingUpdate()
            },
            onMute = {
                onMuteUpdateVersion(info.versionCode)
                dismissPendingUpdate()
            },
            onDismiss = { dismissPendingUpdate() },
        )
    }
}

/**
 * 安装完新版本后首次进入时展示本次更新内容。
 * [lastSeenVersionCode] 为 0 时视作全新安装，不弹公告。
 */
@Composable
fun ReleaseAnnouncementGate(
    lastSeenVersionCode: Int,
    onSeen: (Int) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val checker = remember { AppUpdateChecker(downloaderLabels = context.mirrorDownloaderLabels()) }
    var notes by remember { mutableStateOf<ReleaseNotesState>(ReleaseNotesState.Loading) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(lastSeenVersionCode) {
        if (lastSeenVersionCode == 0) {
            onSeen(BuildConfig.VERSION_CODE)
            return@LaunchedEffect
        }
        if (lastSeenVersionCode >= BuildConfig.VERSION_CODE) return@LaunchedEffect
        visible = true
        onSeen(BuildConfig.VERSION_CODE)
    }

    LaunchedEffect(visible, attempt) {
        if (!visible) return@LaunchedEffect
        notes = ReleaseNotesState.Loading
        val text = checker.releaseNotes(releaseTagName())
        notes = if (text.isNullOrBlank()) ReleaseNotesState.Unavailable else ReleaseNotesState.Loaded(text)
    }

    if (!visible) return
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text(stringResource(R.string.update_announcement_title, releaseVersionName())) },
        text = {
            when (val state = notes) {
                ReleaseNotesState.Loading -> Text(
                    text = stringResource(R.string.update_announcement_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ReleaseNotesState.Unavailable -> Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_announcement_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { attempt++ }) {
                            Text(stringResource(R.string.update_announcement_retry))
                        }
                        TextButton(onClick = { uriHandler.openUri(releaseUrl()) }) {
                            Text(stringResource(R.string.update_announcement_open_release))
                        }
                    }
                }

                is ReleaseNotesState.Loaded -> ReleaseNotesCard(
                    markdown = state.text,
                    maxHeight = 300.dp,
                )
            }
        },
        confirmButton = {
            Button(onClick = { visible = false }) {
                Text(stringResource(R.string.update_announcement_dismiss))
            }
        },
    )
}

/** 更新公告的正文状态。 */
private sealed interface ReleaseNotesState {
    data object Loading : ReleaseNotesState

    data object Unavailable : ReleaseNotesState

    data class Loaded(val text: String) : ReleaseNotesState
}

/** 去掉构建类型给版本名加的后缀。 */
private fun releaseVersionName(): String = BuildConfig.VERSION_NAME.substringBefore("-ci")

/** 当前构建对应的 Release 标签。 */
private fun releaseTagName(): String = "v" + releaseVersionName()

private fun releaseUrl(): String = AppUpdateChecker.releasePageUrl(releaseTagName())

@Composable
private fun UpdateRollbackDialog(
    info: AppUpdateInfo,
    downloading: Boolean,
    downloadedApk: File?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_rollback_title, info.versionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.update_rollback_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.update_dialog_changelog),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ReleaseNotesCard(
                    markdown = info.releaseNotes.ifBlank { stringResource(R.string.update_no_release_notes) },
                    maxHeight = 180.dp,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDownload, enabled = !downloading) {
                Text(
                    when {
                        downloading -> stringResource(R.string.update_dialog_downloading)
                        downloadedApk?.exists() == true -> stringResource(R.string.update_dialog_install)
                        else -> stringResource(R.string.update_rollback_action)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun UpdateAvailableDialog(
    info: AppUpdateInfo,
    downloading: Boolean,
    downloadedApk: File?,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title, info.versionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.update_dialog_version_code, info.versionCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.update_dialog_changelog),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ReleaseNotesCard(
                    markdown = info.releaseNotes.ifBlank { stringResource(R.string.update_no_release_notes) },
                    maxHeight = 220.dp,
                )
                UpdateSecondaryChoice(
                    title = stringResource(R.string.update_dialog_mute),
                    hint = stringResource(R.string.update_dialog_mute_hint),
                    onClick = onMute,
                )
                UpdateSecondaryChoice(
                    title = stringResource(R.string.update_dialog_ignore),
                    hint = stringResource(R.string.update_dialog_ignore_hint),
                    onClick = onIgnore,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading,
            ) {
                Text(
                    when {
                        downloading -> stringResource(R.string.update_dialog_downloading)
                        downloadedApk?.exists() == true -> stringResource(R.string.update_dialog_install)
                        else -> stringResource(R.string.update_dialog_update)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}

/** 更新弹窗里的次要选项：一行标题加一行说明，整行可点。 */
@Composable
private fun UpdateSecondaryChoice(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 状态种类到当前语言文字的渲染，随应用内语言切换重算。 */
@Composable
private fun updatePanelStatusText(status: UpdatePanelStatus): String = when (status) {
    UpdatePanelStatus.Idle -> stringResource(R.string.update_status_default)
    UpdatePanelStatus.Checking -> stringResource(R.string.update_status_checking)
    UpdatePanelStatus.NoRelease -> stringResource(R.string.update_status_no_release)
    UpdatePanelStatus.ManifestMissing -> stringResource(R.string.update_status_manifest_missing)
    UpdatePanelStatus.UpToDate -> stringResource(R.string.update_status_up_to_date)
    is UpdatePanelStatus.Available -> stringResource(R.string.update_status_available, status.versionName)
    is UpdatePanelStatus.Rollback -> stringResource(R.string.update_status_rollback, status.versionName)
    is UpdatePanelStatus.Ignored -> stringResource(R.string.update_status_ignored, status.versionName)
    is UpdatePanelStatus.IgnoredManual ->
        stringResource(R.string.update_status_ignored_manual, status.versionName)
    is UpdatePanelStatus.Muted -> stringResource(R.string.update_status_muted, status.versionName)
    is UpdatePanelStatus.Downloading -> stringResource(R.string.update_status_downloading, status.fileName)
    is UpdatePanelStatus.Downloaded -> stringResource(R.string.update_status_downloaded, status.sourceName)
    is UpdatePanelStatus.Failed -> LocalContext.current.updateStatusText(status.reason)
}

@Composable
private fun UpdateSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun UpdateActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (badge) {
                UpdateBadgeDot()
                Spacer(modifier = Modifier.width(10.dp))
            }
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(buttonText)
            }
        }
    }
}

/** 有新版本时的红点角标。 */
@Composable
fun UpdateBadgeDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.update_badge_desc)
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .semantics { contentDescription = description },
    )
}
