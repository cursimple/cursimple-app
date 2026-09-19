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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.x500x.cursimple.app.update.AppUpdateDownloadProgress
import com.x500x.cursimple.app.update.AppUpdateDownloadResult
import com.x500x.cursimple.app.update.AppUpdateInfo
import com.x500x.cursimple.app.update.AppUpdateInstaller
import com.x500x.cursimple.app.download.mirrorDownloaderLabels
import com.x500x.cursimple.app.download.SharedPrefsMirrorPreferenceStore
import com.x500x.cursimple.app.update.UPDATE_POLL_TICK_MILLIS
import com.x500x.cursimple.app.update.UpdateNoticeState
import com.x500x.cursimple.app.update.UpdatePeekResult
import com.x500x.cursimple.app.update.UpdatePollAction
import com.x500x.cursimple.app.update.UpdatePollStore
import com.x500x.cursimple.app.update.updatePollAction
import com.x500x.cursimple.app.update.UpdatePanelStatus
import com.x500x.cursimple.app.update.shouldPromptUpdate
import com.x500x.cursimple.app.update.shouldShowUpdateBadge
import com.x500x.cursimple.app.update.updateStatusText
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import com.x500x.cursimple.app.download.DownloadSourceIds
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import com.x500x.cursimple.app.update.AppReleaseSummary
import androidx.compose.foundation.layout.height


/**
 * 侧边栏「检查更新」弹的那个框。
 *
 * 只为看一眼有没有新版就被丢进设置页、还得自己退出来，太绕。
 * 这里把检查与下载整套直接摆在弹窗里，用的是设置页同一份实现。
 */

/**
 * 版本历史。
 *
 * 列出线上发过的版本，点开看那一版的更新内容。列哪些版本跟着「接收测试版更新」走：
 * 关着的人装不到 beta，把它们列出来只会让人以为漏了更新。
 */
@Composable
fun UpdateHistorySection(
    betaUpdatesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val checker = remember {
        AppUpdateChecker(
            downloaderLabels = context.mirrorDownloaderLabels(),
            mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
        )
    }
    var loading by remember { mutableStateOf(true) }
    var releases by remember { mutableStateOf<List<AppReleaseSummary>?>(null) }
    var expandedTag by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(betaUpdatesEnabled) {
        loading = true
        releases = checker.history(includePrerelease = betaUpdatesEnabled)
        loading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            loading -> Text(
                text = stringResource(R.string.update_history_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            releases == null -> Text(
                text = stringResource(R.string.update_history_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            releases.orEmpty().isEmpty() -> Text(
                // 正式版还没发过时就是空的，说清楚而不是留一片白
                text = stringResource(R.string.update_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            else -> releases.orEmpty().forEach { release ->
                UpdateHistoryRow(
                    release = release,
                    expanded = expandedTag == release.tagName,
                    onToggle = {
                        expandedTag = if (expandedTag == release.tagName) null else release.tagName
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateHistoryRow(
    release: AppReleaseSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = listOfNotNull(
                        release.publishedAt.take(10).takeIf { it.isNotBlank() },
                        if (release.prerelease) stringResource(R.string.update_history_prerelease) else null,
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = release.notes.ifBlank { stringResource(R.string.update_history_no_notes) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun UpdateCheckDialog(
    autoCheckEnabled: Boolean,
    betaUpdatesEnabled: Boolean,
    ignoredUpdateVersionCode: Int?,
    updateNotice: UpdateNoticeState,
    onAutoCheckEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
    onMuteUpdateVersion: (Int?) -> Unit,
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_check_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                UpdateCheckSection(
                    autoCheckEnabled = autoCheckEnabled,
                    betaUpdatesEnabled = betaUpdatesEnabled,
                    ignoredUpdateVersionCode = ignoredUpdateVersionCode,
                    updateNotice = updateNotice,
                    onAutoCheckEnabledChange = onAutoCheckEnabledChange,
                    onIgnoreUpdateVersion = onIgnoreUpdateVersion,
                    onMuteUpdateVersion = onMuteUpdateVersion,
                    onUpdateFound = onUpdateFound,
                    onUpdateNoticeCleared = onUpdateNoticeCleared,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
    )
}

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
    val checker = remember { AppUpdateChecker(
                downloaderLabels = context.mirrorDownloaderLabels(),
                mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
            ) }
    // 用 remember 而非 rememberSaveable：清除它们的协程绑定在组合上，旋转/切语言 recreate 时会被取消，
    // 若这两个标志跨重建存活就会永远卡在「检查中/下载中」；随重建归零后按钮恢复可用，用户可重试
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
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
            downloadProgress = AppUpdateDownloadProgress(0L, null)
            status = UpdatePanelStatus.Downloading(info.asset.fileName)
            val result = checker.download(context, info) { downloaded, total ->
                downloadProgress = AppUpdateDownloadProgress(downloaded, total.takeIf { it > 0L })
            }
            when (result) {
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
            downloadProgress = null
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
            downloadProgress = downloadProgress,
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
            downloadProgress = downloadProgress,
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
    onDialogVisibleChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker(
                downloaderLabels = context.mirrorDownloaderLabels(),
                mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
            ) }
    var promptedThisSession by rememberSaveable { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // 见上：清除标志的协程随重建取消，saveable 会卡在「下载中」，用 remember 让其归零
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    val pollStore = remember(context) { UpdatePollStore(context) }

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
            downloadProgress = AppUpdateDownloadProgress(0L, null)
            val result = checker.download(context, info) { downloaded, total ->
                downloadProgress = AppUpdateDownloadProgress(downloaded, total.takeIf { it > 0L })
            }
            when (result) {
                is AppUpdateDownloadResult.Success -> {
                    downloadedApk = result.file
                    AppUpdateInstaller.openInstall(context, result.file)
                }
                is AppUpdateDownloadResult.Failure -> {
                    Toast.makeText(context, context.updateStatusText(result.reason), Toast.LENGTH_SHORT).show()
                }
            }
            downloading = false
            downloadProgress = null
        }
    }

    // 后台轮询：以前只在启动时查一次，装了之后发新版要么等下次冷启动、
    // 要么自己点进设置，用户根本不知道有更新。
    // 轮询主体是带 ETag 的条件请求，没新版时服务端回 304、不带响应体，
    // 一次几百字节；只有探到变化才去跑流量大得多的完整检查。
    LaunchedEffect(autoCheckEnabled, betaUpdatesEnabled) {
        if (!autoCheckEnabled) return@LaunchedEffect
        pollStore.invalidateFor(BuildConfig.VERSION_CODE)

        suspend fun runFullCheck() {
            pollStore.setLastFullCheckAtMillis(System.currentTimeMillis())
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

        while (true) {
            val action = updatePollAction(
                nowMillis = System.currentTimeMillis(),
                lastPeekAtMillis = pollStore.lastPeekAtMillis(),
                lastFullCheckAtMillis = pollStore.lastFullCheckAtMillis(),
                hasEtag = pollStore.etag(betaUpdatesEnabled) != null,
            )
            when (action) {
                UpdatePollAction.FullCheck -> {
                    runFullCheck()
                    // 完整检查顺手把 ETag 种下去，后面才有条件请求可发
                    (checker.peek(betaUpdatesEnabled, knownEtag = null) as? UpdatePeekResult.Changed)
                        ?.let { pollStore.setEtag(betaUpdatesEnabled, it.etag) }
                    pollStore.setLastPeekAtMillis(System.currentTimeMillis())
                }

                UpdatePollAction.Peek -> {
                    val known = pollStore.etag(betaUpdatesEnabled)
                    when (val peeked = checker.peek(betaUpdatesEnabled, known)) {
                        UpdatePeekResult.Unchanged -> pollStore.setLastPeekAtMillis(System.currentTimeMillis())
                        is UpdatePeekResult.Changed -> {
                            pollStore.setEtag(betaUpdatesEnabled, peeked.etag)
                            pollStore.setLastPeekAtMillis(System.currentTimeMillis())
                            runFullCheck()
                        }
                        // 探不通就别改时间戳，等下一拍再试；连不上时也不去跑完整检查
                        UpdatePeekResult.Unknown -> Unit
                    }
                }

                UpdatePollAction.Skip -> Unit
            }
            kotlinx.coroutines.delay(UPDATE_POLL_TICK_MILLIS)
        }
    }

    // 向上报告弹窗是否可见，供首启引导互斥（引导不应压在更新弹窗上）
    LaunchedEffect(pendingUpdate != null) {
        onDialogVisibleChange(pendingUpdate != null)
    }
    DisposableEffect(Unit) {
        onDispose { onDialogVisibleChange(false) }
    }

    pendingUpdate?.let { info ->
        UpdateAvailableDialog(
            info = info,
            downloading = downloading,
            downloadProgress = downloadProgress,
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
    onDialogVisibleChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val checker = remember { AppUpdateChecker(
                downloaderLabels = context.mirrorDownloaderLabels(),
                mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
            ) }
    var notes by remember { mutableStateOf<ReleaseNotesState>(ReleaseNotesState.Loading) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) { onDialogVisibleChange(visible) }
    DisposableEffect(Unit) {
        onDispose { onDialogVisibleChange(false) }
    }

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
    downloadProgress: AppUpdateDownloadProgress? = null,
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
                if (downloading) {
                    UpdateDownloadProgressRow(downloadProgress)
                }
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
    downloadProgress: AppUpdateDownloadProgress? = null,
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
                if (downloading) {
                    UpdateDownloadProgressRow(downloadProgress)
                }
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

/**
 * 下载进度条。
 *
 * 安装包动辄几十兆，只显示「下载中…」的话用户既不知道还要等多久、
 * 也分不清是在下载还是卡死了。这里把百分比、已下载量和总大小都摆出来；
 * 服务端没给 Content-Length 时退回不确定进度条，至少能看出还在动。
 */
@Composable
private fun UpdateDownloadProgressRow(progress: AppUpdateDownloadProgress?) {
    val fraction = progress?.fraction
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            val animated by animateFloatAsState(targetValue = fraction, label = "updateDownloadProgress")
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = when {
                progress == null -> stringResource(R.string.update_download_starting)
                progress.totalBytes == null -> stringResource(
                    R.string.update_download_progress_unknown_total,
                    formatBytes(progress.downloadedBytes),
                )
                else -> stringResource(
                    R.string.update_download_progress,
                    formatBytes(progress.downloadedBytes),
                    formatBytes(progress.totalBytes),
                    ((fraction ?: 0f) * 100).roundToInt(),
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 字节数按 MB / KB 显示，小数点后留一位，够看出进度在动又不会跳得眼花。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
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
    is UpdatePanelStatus.Downloaded -> stringResource(
        R.string.update_status_downloaded,
        downloadSourceLabel(status.sourceName),
    )
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

/**
 * 下载源的显示名。
 *
 * 镜像候选大多直接用主机名，本身与语言无关；只有本地文件 / 源站 / GitHub 源站
 * 这三个是标识，显示时换成当前语言。
 */
@Composable
private fun downloadSourceLabel(sourceName: String): String = when (sourceName) {
    DownloadSourceIds.LOCAL_FILE -> stringResource(R.string.download_source_local_file)
    DownloadSourceIds.ORIGIN -> stringResource(R.string.download_source_origin)
    DownloadSourceIds.GITHUB_ORIGIN -> stringResource(R.string.download_source_github)
    else -> sourceName
}
