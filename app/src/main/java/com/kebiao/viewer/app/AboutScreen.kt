package com.kebiao.viewer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.app.update.AppUpdateCheckResult
import com.kebiao.viewer.app.update.AppUpdateChecker
import com.kebiao.viewer.app.update.AppUpdateDownloadResult
import com.kebiao.viewer.app.update.AppUpdateInfo
import com.kebiao.viewer.app.update.AppUpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

private const val GITHUB_URL = "https://github.com/x500x/ClassScheduleViewer"
private const val DEV_MODE_TAP_TARGET = 5
private const val DEV_MODE_TAP_RESET_MS = 3000L

@Composable
fun AboutScreen(
    developerModeEnabled: Boolean,
    autoUpdateEnabled: Boolean,
    onSetDeveloperMode: (Boolean) -> Unit,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { resolveVersionName(context) }

    var tapCount by rememberSaveable { mutableIntStateOf(0) }
    var lastTapMs by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(developerModeEnabled) {
        if (developerModeEnabled) {
            tapCount = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(
                versionName = versionName,
                developerModeEnabled = developerModeEnabled,
                onVersionTap = {
                    if (developerModeEnabled) {
                        return@HeroCard
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastTapMs > DEV_MODE_TAP_RESET_MS) {
                        tapCount = 0
                    }
                    lastTapMs = now
                    tapCount += 1
                    if (tapCount >= DEV_MODE_TAP_TARGET) {
                        onSetDeveloperMode(true)
                        tapCount = 0
                    }
                },
            )

            ProjectCard(onOpenGithub = { openUrl(context, GITHUB_URL) })

            UpdateCard(
                autoUpdateEnabled = autoUpdateEnabled,
                onAutoUpdateEnabledChange = onAutoUpdateEnabledChange,
            )

            TechStackCard()
            if (developerModeEnabled) {
                Text(
                    text = "开发者调试已移动到设置页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(
    autoUpdateEnabled: Boolean,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker() }
    var checking by rememberSaveable { mutableStateOf(false) }
    var downloading by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("从 GitHub Release 检查新版本。") }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    fun downloadAndInstall(info: AppUpdateInfo, openInstaller: Boolean) {
        if (downloading) return
        scope.launch {
            downloading = true
            statusMessage = "正在下载 ${info.asset.fileName}..."
            when (val result = checker.download(context, info)) {
                is AppUpdateDownloadResult.Success -> {
                    downloadedApk = result.file
                    statusMessage = if (openInstaller) {
                        "已从 ${result.sourceName} 下载，正在打开安装确认。"
                    } else {
                        "已从 ${result.sourceName} 下载，安装前会再次确认。"
                    }
                    if (openInstaller) {
                        AppUpdateInstaller.openInstall(context, result.file)
                    }
                }
                is AppUpdateDownloadResult.Failure -> {
                    statusMessage = result.message
                }
            }
            downloading = false
        }
    }

    fun checkUpdate(autoDownload: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            statusMessage = "正在测速并检查 Release..."
            updateInfo = null
            downloadedApk = null
            when (val result = checker.check()) {
                AppUpdateCheckResult.NoRelease -> statusMessage = "暂无发布版本。"
                AppUpdateCheckResult.ManifestMissing -> statusMessage = "最新 Release 缺少 update.json。"
                AppUpdateCheckResult.UpToDate -> statusMessage = "当前已经是最新版本。"
                is AppUpdateCheckResult.Available -> {
                    updateInfo = result.info
                    statusMessage = "发现新版本 v${result.info.versionName}，可下载 ${result.info.asset.abi} 安装包。"
                    if (autoDownload) {
                        downloadAndInstall(result.info, openInstaller = false)
                    }
                }
                is AppUpdateCheckResult.Failure -> statusMessage = result.message
            }
            checking = false
        }
    }

    LaunchedEffect(autoUpdateEnabled) {
        if (autoUpdateEnabled) {
            checkUpdate(autoDownload = true)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "更新",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = onAutoUpdateEnabledChange,
                )
            }
            Text(
                text = if (autoUpdateEnabled) "自动检查并下载可用更新" else "手动检查更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { checkUpdate(autoDownload = false) },
                    enabled = !checking && !downloading,
                ) {
                    Text(if (checking) "检查中..." else "检查更新")
                }
                updateInfo?.let { info ->
                    Button(
                        onClick = {
                            val downloaded = downloadedApk
                            if (downloaded != null && downloaded.exists()) {
                                AppUpdateInstaller.openInstall(context, downloaded)
                            } else {
                                downloadAndInstall(info, openInstaller = true)
                            }
                        },
                        enabled = !checking && !downloading,
                    ) {
                        Text(
                            when {
                                downloading -> "下载中..."
                                downloadedApk?.exists() == true -> "安装已下载更新"
                                else -> "下载并安装"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    versionName: String,
    developerModeEnabled: Boolean,
    onVersionTap: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "课表查看",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Class Schedule Viewer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onVersionTap),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "版本号",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (developerModeEnabled) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = "开发者",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    onOpenGithub: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "项目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "微内核架构的 Android 课表查看器，使用 QuickJS 运行可热更新的学校插件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow(
                icon = Icons.Rounded.Link,
                title = "GitHub",
                subtitle = GITHUB_URL.removePrefix("https://"),
                onClick = onOpenGithub,
            )
        }
    }
}

@Composable
private fun TechStackCard() {
    val items = remember {
        listOf(
            "Kotlin" to "2.2.21",
            "Jetpack Compose" to "BOM 2026.04",
            "Material 3" to "Compose M3",
            "AndroidX Glance" to "1.1.1（桌面小组件）",
            "WorkManager" to "2.11.2（后台同步）",
            "DataStore" to "1.2.1（偏好/课表）",
            "Kotlinx Coroutines" to "1.10.2",
            "Kotlinx Serialization" to "1.11.0",
            "QuickJS" to "0.9.2（插件 JS 执行）",
            "OkHttp" to "5.3.2（网络）",
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "技术栈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            items.forEach { (name, version) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "minSdk 24 · targetSdk 36 · ABI v7a / v8a / universal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun resolveVersionName(context: Context): String {
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty().ifBlank { "0.0.0" }
    }.getOrDefault("0.0.0")
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            if (it is ActivityNotFoundException) {
                Toast.makeText(context, "没有可处理链接的应用", Toast.LENGTH_SHORT).show()
            }
        }
}
