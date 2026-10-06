@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
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
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.x500x.cursimple.app.update.UpdateAutoCheckSession
import com.x500x.cursimple.app.update.UpdatePollStore
import com.x500x.cursimple.app.update.autoUpdateCheckDue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.repeatOnLifecycle
import com.x500x.cursimple.app.update.UpdatePanelStatus
import com.x500x.cursimple.app.update.releasePrefetcher
import com.x500x.cursimple.app.update.shouldShowUpdateBadge
import com.x500x.cursimple.app.update.updateStatusText
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import com.x500x.cursimple.app.download.DownloadSourceIds
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import com.x500x.cursimple.app.update.AppReleaseSummary
import androidx.compose.foundation.layout.height

/** Drawer update dialog shares the About page's check and download controls. */

/** Release history follows the user's stable or beta selection. */
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
            loading -> UpdateHistoryPlaceholder(
                text = stringResource(R.string.update_history_loading),
            )

            releases == null -> UpdateHistoryPlaceholder(
                text = stringResource(R.string.update_history_failed),
                color = MaterialTheme.colorScheme.error,
            )

            releases.orEmpty().isEmpty() -> UpdateHistoryPlaceholder(
                text = stringResource(R.string.update_history_empty),
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

/** Scrollable columns lack viewport height; reserve space to center the empty state. */
@Composable
private fun UpdateHistoryPlaceholder(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            textAlign = TextAlign.Center,
        )
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
                if (release.notes.isBlank()) {
                    Text(
                        text = stringResource(R.string.update_history_no_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Render release descriptions as Markdown.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReleaseNotesBody(release.notes)
                    }
                }
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
    onBetaUpdatesEnabledChange: (Boolean) -> Unit,
    onIgnoreUpdateVersion: (Int?) -> Unit,
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
                    onBetaUpdatesEnabledChange = onBetaUpdatesEnabledChange,
                    onIgnoreUpdateVersion = onIgnoreUpdateVersion,
                    onUpdateFound = onUpdateFound,
                    onUpdateNoticeCleared = onUpdateNoticeCleared,
                    checkOnOpen = true,
                )
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
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
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
    modifier: Modifier = Modifier,
    checkOnOpen: Boolean = false,
    onBetaUpdatesEnabledChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker(
                downloaderLabels = context.mirrorDownloaderLabels(),
                mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
            ) }
    val prefetcher = remember(checker) { releasePrefetcher(context, checker) }
    var checkJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Keep operation flags composition-local because their coroutines are cancelled on recreation.
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var status by remember { mutableStateOf<UpdatePanelStatus>(UpdatePanelStatus.Idle) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var pendingRollback by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var autoCheckedForCurrentEntry by rememberSaveable { mutableStateOf(false) }
    // Retain the discovered release for direct download and release-note access.
    var foundUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    fun dismissPendingUpdate() {
        pendingUpdate = null
        pendingRollback = null
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
        checkJob = scope.launch {
            checking = true
            status = UpdatePanelStatus.Checking
            dismissPendingUpdate()
            val result = checker.check(includePrerelease = betaUpdatesEnabled)
            val found = (result as? AppUpdateCheckResult.Available)?.info
            if (found?.versionCode != foundUpdate?.versionCode) downloadedApk = null
            foundUpdate = found
            when (result) {
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
                    if (!ignored) scope.launch { prefetcher.prefetch(result.info.tagName, result.info.releaseNotes) }
                    when {
                        manual -> {
                            pendingUpdate = result.info
                            status = UpdatePanelStatus.Available(result.info.versionName)
                        }
                        ignored -> status = UpdatePanelStatus.Ignored(result.info.versionName)
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

    // Cancel stale checks and discard their results when switching release channels.
    var seenBetaChannel by rememberSaveable { mutableStateOf(betaUpdatesEnabled) }
    LaunchedEffect(betaUpdatesEnabled) {
        if (seenBetaChannel == betaUpdatesEnabled) return@LaunchedEffect
        seenBetaChannel = betaUpdatesEnabled
        checkJob?.cancelAndJoin()
        checking = false
        foundUpdate = null
        downloadedApk = null
        dismissPendingUpdate()
        checkUpdate(manual = checkOnOpen)
    }

    LaunchedEffect(autoCheckEnabled) {
        if (checkOnOpen) {
            if (!autoCheckedForCurrentEntry) {
                autoCheckedForCurrentEntry = true
                checkUpdate(manual = true)
            }
            return@LaunchedEffect
        }
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
        if (onBetaUpdatesEnabledChange != null) {
            var confirmBeta by rememberSaveable { mutableStateOf(false) }
            if (confirmBeta) {
                BetaUpdatesConfirmDialog(
                    onConfirm = {
                        confirmBeta = false
                        onBetaUpdatesEnabledChange(true)
                    },
                    onDismiss = { confirmBeta = false },
                )
            }
            UpdateSwitchRow(
                icon = Icons.Rounded.Science,
                title = stringResource(R.string.settings_beta_updates_title),
                subtitle = if (betaUpdatesEnabled) {
                    stringResource(R.string.settings_beta_updates_on)
                } else {
                    stringResource(R.string.settings_beta_updates_off)
                },
                checked = betaUpdatesEnabled,
                onCheckedChange = { next ->
                    if (next) confirmBeta = true else onBetaUpdatesEnabledChange(false)
                },
            )
        }
        val update = foundUpdate
        UpdateActionRow(
            icon = Icons.Rounded.SystemUpdate,
            title = stringResource(R.string.update_check_title),
            subtitle = if (update != null && status is UpdatePanelStatus.Available) {
                stringResource(R.string.update_status_available_tap, update.versionName)
            } else {
                updatePanelStatusText(status)
            },
            badge = shouldShowUpdateBadge(updateNotice, BuildConfig.VERSION_CODE),
            enabled = !checking && !downloading,
            buttonText = when {
                checking -> stringResource(R.string.update_check_checking)
                downloading -> stringResource(R.string.update_dialog_downloading)
                update == null -> stringResource(R.string.update_check_button)
                downloadedApk?.exists() == true -> stringResource(R.string.update_dialog_install)
                else -> stringResource(R.string.update_dialog_update)
            },
            highlighted = update != null,
            onClick = { if (update != null) downloadAndInstall(update) else checkUpdate(manual = true) },
            onRowClick = update?.let { { pendingUpdate = it } },
        )
        if (downloading && pendingUpdate == null) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                UpdateDownloadProgressRow(downloadProgress)
            }
        }
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
                foundUpdate = null
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

/**
 * Check silently at startup and after the recheck interval; retry failures after five minutes.
 * Channel changes recheck immediately. Prefetch available release notes in the background.
 */
@Composable
fun AutomaticUpdateCheck(
    autoCheckEnabled: Boolean,
    betaUpdatesEnabled: Boolean,
    updateNotice: UpdateNoticeState,
    onUpdateFound: (Int, String) -> Unit,
    onUpdateNoticeCleared: () -> Unit,
) {
    val context = LocalContext.current
    val checker = remember { AppUpdateChecker(
                downloaderLabels = context.mirrorDownloaderLabels(),
                mirrorStore = SharedPrefsMirrorPreferenceStore(context.applicationContext),
            ) }
    val prefetcher = remember(checker) { releasePrefetcher(context, checker) }
    val pollStore = remember(context) { UpdatePollStore(context) }
    val latestNotice by rememberUpdatedState(updateNotice)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(autoCheckEnabled, betaUpdatesEnabled, lifecycleOwner) {
        if (!autoCheckEnabled) return@LaunchedEffect
        pollStore.invalidateFor(BuildConfig.VERSION_CODE)
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                val due = autoUpdateCheckDue(
                    nowMillis = System.currentTimeMillis(),
                    checkedThisLaunch = UpdateAutoCheckSession.checkedThisLaunch,
                    lastCheckAtMillis = pollStore.lastCheckAtMillis(),
                    lastFailureAtMillis = pollStore.lastFailureAtMillis(),
                    channelChanged = pollStore.lastCheckIncludedPrerelease()
                        ?.let { it != betaUpdatesEnabled } ?: false,
                )
                if (due) {
                    val result = checker.check(includePrerelease = betaUpdatesEnabled)
                    UpdateAutoCheckSession.checkedThisLaunch = true
                    pollStore.setLastCheckIncludedPrerelease(betaUpdatesEnabled)
                    when (result) {
                        is AppUpdateCheckResult.Available -> {
                            pollStore.setLastCheckAtMillis(System.currentTimeMillis())
                            onUpdateFound(result.info.versionCode, result.info.versionName)
                            val found = latestNotice.copy(
                                versionCode = result.info.versionCode,
                                versionName = result.info.versionName,
                            )
                            // Do not prefetch ignored versions.
                            if (shouldShowUpdateBadge(found, BuildConfig.VERSION_CODE)) {
                                launch { prefetcher.prefetch(result.info.tagName, result.info.releaseNotes) }
                            }
                        }
                        AppUpdateCheckResult.UpToDate, AppUpdateCheckResult.NoRelease -> {
                            pollStore.setLastCheckAtMillis(System.currentTimeMillis())
                            onUpdateNoticeCleared()
                        }
                        // Offer downgrades only during manual checks; failed automatic checks retry later.
                        is AppUpdateCheckResult.Rollback -> pollStore.setLastCheckAtMillis(System.currentTimeMillis())
                        else -> pollStore.setLastFailureAtMillis(System.currentTimeMillis())
                    }
                }
                kotlinx.coroutines.delay(UPDATE_POLL_TICK_MILLIS)
            }
        }
    }
}

/** Show notes after an upgrade; [lastSeenVersionCode] zero denotes a fresh install. */
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
    val prefetcher = remember(checker) { releasePrefetcher(context, checker) }
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
        val text = prefetcher.notes(releaseTagName())
        notes = if (text.isNullOrBlank()) ReleaseNotesState.Unavailable else ReleaseNotesState.Loaded(text)
        // Prefetch remaining images after notes load when no prepared cache exists.
        if (!text.isNullOrBlank()) prefetcher.prefetchImages(text)
    }

    if (!visible) return
    (notes as? ReleaseNotesState.Loaded)?.let { loaded ->
        ReleaseAnnouncementDialog(
            versionName = releaseVersionName(),
            markdown = loaded.text,
            imageLoader = com.x500x.cursimple.app.update.rememberReleaseImageLoader(),
            onDismiss = { visible = false },
        )
        return
    }
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
                        AppOutlinedButton(onClick = { attempt++ }) {
                            Text(stringResource(R.string.update_announcement_retry))
                        }
                        AppOutlinedButton(onClick = { uriHandler.openUri(releaseUrl()) }) {
                            Text(stringResource(R.string.update_announcement_open_release))
                        }
                    }
                }

                is ReleaseNotesState.Loaded -> ReleaseNotesCard(markdown = state.text, maxHeight = 300.dp)
            }
        },
        confirmButton = {
            Button(onClick = { visible = false }) {
                Text(stringResource(R.string.update_announcement_dismiss))
            }
        },
    )
}

private sealed interface ReleaseNotesState {
    data object Loading : ReleaseNotesState

    data object Unavailable : ReleaseNotesState

    data class Loaded(val text: String) : ReleaseNotesState
}

private fun releaseVersionName(): String = BuildConfig.VERSION_NAME.substringBefore("-ci")

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
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
            AppOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}

/**
 * Report downloaded bytes and known totals; use indeterminate progress without Content-Length.
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

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
    highlighted: Boolean = false,
    onRowClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier),
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
            if (highlighted) {
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Text(buttonText)
                }
            } else {
                AppOutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

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

/** Localize symbolic source IDs at display time; mirror hostnames remain unchanged. */
@Composable
private fun downloadSourceLabel(sourceName: String): String = when (sourceName) {
    DownloadSourceIds.LOCAL_FILE -> stringResource(R.string.download_source_local_file)
    DownloadSourceIds.ORIGIN -> stringResource(R.string.download_source_origin)
    DownloadSourceIds.GITHUB_ORIGIN -> stringResource(R.string.download_source_github)
    else -> sourceName
}
