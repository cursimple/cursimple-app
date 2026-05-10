package com.kebiao.viewer.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.app.update.AppUpdateCheckResult
import com.kebiao.viewer.app.update.AppUpdateChecker
import com.kebiao.viewer.app.update.AppUpdateDownloadResult
import com.kebiao.viewer.app.update.AppUpdateInfo
import com.kebiao.viewer.app.update.AppUpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateCheckSection(
    autoCheckEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    onAutoCheckEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker() }
    var checking by rememberSaveable { mutableStateOf(false) }
    var downloading by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("从 GitHub Release 检查新版本。") }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var autoCheckedForCurrentEntry by rememberSaveable { mutableStateOf(false) }

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
            statusMessage = "正在下载 ${info.asset.fileName}..."
            when (val result = checker.download(context, info)) {
                is AppUpdateDownloadResult.Success -> {
                    downloadedApk = result.file
                    statusMessage = "已从 ${result.sourceName} 下载，正在打开安装确认。"
                    AppUpdateInstaller.openInstall(context, result.file)
                }
                is AppUpdateDownloadResult.Failure -> {
                    statusMessage = result.message
                }
            }
            downloading = false
        }
    }

    fun checkUpdate(manual: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            statusMessage = "正在检查 Release..."
            dismissPendingUpdate()
            when (val result = checker.check()) {
                AppUpdateCheckResult.NoRelease -> statusMessage = "暂无发布版本。"
                AppUpdateCheckResult.ManifestMissing -> statusMessage = "最新 Release 缺少 update.json。"
                AppUpdateCheckResult.UpToDate -> statusMessage = "当前已经是最新版本。"
                is AppUpdateCheckResult.Available -> {
                    val ignored = !manual && ignoredUpdateVersionCode == result.info.versionCode
                    if (ignored) {
                        statusMessage = "v${result.info.versionName} 已被忽略，本次自动检查不再提示。"
                    } else {
                        pendingUpdate = result.info
                        statusMessage = "发现新版本 v${result.info.versionName}。"
                    }
                }
                is AppUpdateCheckResult.Failure -> statusMessage = result.message
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "应用更新",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        UpdateSwitchRow(
            title = "自动检查更新",
            subtitle = if (autoCheckEnabled) "发现新版本后询问是否更新" else "仅手动检查更新",
            checked = autoCheckEnabled,
            onCheckedChange = onAutoCheckEnabledChange,
        )
        UpdateActionRow(
            title = "检查更新",
            subtitle = statusMessage,
            enabled = !checking && !downloading,
            buttonText = if (checking) "检查中..." else "检查",
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
                statusMessage = "已忽略 v${info.versionName}。手动检查仍可更新。"
                dismissPendingUpdate()
            },
            onDismiss = { dismissPendingUpdate() },
        )
    }
}

@Composable
fun AutomaticUpdateCheckPrompt(
    autoCheckEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    onIgnoreUpdateVersion: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker() }
    var checkedThisSession by rememberSaveable { mutableStateOf(false) }
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
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
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
        when (val result = checker.check()) {
            is AppUpdateCheckResult.Available -> {
                if (ignoredUpdateVersionCode != result.info.versionCode) {
                    pendingUpdate = result.info
                }
            }
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
            onDismiss = { dismissPendingUpdate() },
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: AppUpdateInfo,
    downloading: Boolean,
    downloadedApk: File?,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "版本号：${info.versionCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "变更内容",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = releaseNotesText(info),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading,
            ) {
                Text(
                    when {
                        downloading -> "下载中..."
                        downloadedApk?.exists() == true -> "安装"
                        else -> "更新"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) {
                Text("忽略本次更新")
            }
        },
    )
}

@Composable
private fun UpdateSwitchRow(
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
    title: String,
    subtitle: String,
    enabled: Boolean,
    buttonText: String,
    onClick: () -> Unit,
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

private fun releaseNotesText(info: AppUpdateInfo): String =
    info.releaseNotes.ifBlank { "暂无变更说明。" }
