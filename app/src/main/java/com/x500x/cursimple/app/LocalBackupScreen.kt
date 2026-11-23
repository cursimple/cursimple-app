@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared local ZIP backup entry and dialogs used by Settings and Import/Export. */
@Composable
fun LocalBackupSettingsSection(
    onCreateAppBackup: suspend () -> AppBackupPayload,
    onRestoreAppBackup: suspend (AppBackupPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showExportConfirmation by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<LocalBackupSelection?>(null) }
    var payload by remember { mutableStateOf<AppBackupPayload?>(null) }
    var exportSummary by remember { mutableStateOf<List<LocalBackupExportSummary>>(emptyList()) }
    var pendingBackup by remember { mutableStateOf<LocalBackupImportPreview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadPayload() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { onCreateAppBackup() } }
                .onSuccess { nextPayload ->
                    payload = nextPayload
                    selection = LocalBackupSelection.all(nextPayload.stores.map { it.storeName })
                    showBackupDialog = true
                }
                .onFailure { errorMessage = it.message ?: context.getString(R.string.ie_unknown_error) }
            busy = false
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chosen = selection ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                val source = payload ?: withContext(Dispatchers.IO) { onCreateAppBackup() }
                val bytes = withContext(Dispatchers.Default) {
                    LocalBackupArchive.encode(source, chosen.selectedStoreNames.toSet())
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("backup_file_open_failed")
                }
            }.onSuccess {
                showBackupDialog = false
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.ie_local_backup_export_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.onFailure {
                errorMessage = it.message ?: context.getString(R.string.ie_unknown_error)
            }
            busy = false
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("backup_file_open_failed")
                }
                prepareLocalBackupImport(bytes, onCreateAppBackup)
            }.onSuccess { preview ->
                showBackupDialog = false
                pendingBackup = preview
            }.onFailure {
                errorMessage = it.message ?: context.getString(R.string.ie_unknown_error)
            }
            busy = false
        }
    }

    Column(modifier = modifier) {
        SettingsCard {
            SettingsActionRow(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.ie_local_backup_import_action),
                subtitle = stringResource(R.string.ie_local_backup_import_action_body),
                onClick = {
                    if (!busy) openLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                quickId = null,
            )
            SettingsActionRow(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.ie_local_backup_export_action),
                subtitle = stringResource(R.string.ie_local_backup_export_action_body),
                onClick = ::loadPayload,
                quickId = null,
            )
        }
    }

    if (showBackupDialog && !showExportConfirmation) {
        selection?.let { current ->
            AppAlertDialog(
                onDismissRequest = { if (!busy) showBackupDialog = false },
                icon = { Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(22.dp)) },
                title = { Text(stringResource(R.string.ie_local_backup_title)) },
                titleTrailingContent = {
                    AppCircleIconButton(
                        onClick = { if (!busy) showBackupDialog = false },
                        enabled = !busy,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ie_local_backup_close),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.ie_local_backup_select_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        current.storeNames.forEach { storeName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { selection = current.toggle(storeName) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = storeName in current.selectedStoreNames,
                                    onCheckedChange = { selection = current.toggle(storeName) },
                                    enabled = !busy,
                                )
                                Text(localBackupStoreLabel(storeName), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (current.selectedStoreNames.isEmpty()) {
                            Text(
                                stringResource(R.string.ie_local_backup_select_required),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            stringResource(R.string.ie_local_backup_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    AppDialogPrimaryButton(
                        enabled = current.selectedStoreNames.isNotEmpty() && !busy,
                        onClick = {
                            val source = payload
                            if (source != null) {
                                exportSummary = LocalBackupDiff.exportSummary(
                                    payload = source,
                                    selectedStoreNames = current.selectedStoreNames,
                                )
                                showExportConfirmation = true
                            }
                        },
                    ) {
                        Text(if (busy) stringResource(R.string.ie_local_backup_processing) else stringResource(R.string.ie_local_backup_export))
                    }
                },
            )
        }
    }

    if (showExportConfirmation) {
        LocalBackupExportConfirmationDialog(
            summaries = exportSummary,
            busy = busy,
            onDismiss = { if (!busy) showExportConfirmation = false },
            onConfirm = {
                if (showExportConfirmation && !busy) {
                    val name = "cursimple-backup-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())}.zip"
                    showExportConfirmation = false
                    createLauncher.launch(name)
                }
            },
        )
    }

    errorMessage?.let { message ->
        AppAlertDialog(
            onDismissRequest = { errorMessage = null },
            tone = AppDialogTone.Warning,
            icon = { Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.ie_local_backup_failed_title)) },
            text = { Text(stringResource(R.string.ie_local_backup_failed, message)) },
            confirmButton = {
                AppDialogPrimaryButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.ie_close)) }
            },
        )
    }

    pendingBackup?.let { preview ->
        LocalBackupImportDiffDialog(
            diff = preview.diff,
            busy = busy,
            onDismiss = { if (!busy) pendingBackup = null },
            onConfirm = {
                if (!busy && pendingBackup === preview) {
                    busy = true
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { onRestoreAppBackup(preview.payload) } }
                        pendingBackup = null
                        result.onSuccess {
                            withContext(Dispatchers.Main.immediate) {
                                Toast.makeText(context, context.getString(R.string.ie_local_backup_restore_success), Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            errorMessage = it.message ?: context.getString(R.string.ie_unknown_error)
                        }
                        busy = false
                    }
                }
            },
        )
    }
}

internal data class LocalBackupImportPreview(val payload: AppBackupPayload, val diff: LocalBackupDiffResult)

internal suspend fun prepareLocalBackupImport(
    bytes: ByteArray,
    onCreateAppBackup: suspend () -> AppBackupPayload,
): LocalBackupImportPreview {
    val decoded = withContext(Dispatchers.Default) { LocalBackupArchive.decode(bytes) }
    val current = withContext(Dispatchers.IO) { onCreateAppBackup() }
    return withContext(Dispatchers.Default) {
        LocalBackupImportPreview(decoded.payload, LocalBackupDiff.compare(current, decoded.payload))
    }
}

@Composable
internal fun LocalBackupExportConfirmationDialog(
    summaries: List<LocalBackupExportSummary>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.ie_local_backup_export_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.ie_local_backup_export_confirm_body))
                summaries.forEach { summary ->
                    Text(
                        text = "• ${localBackupStoreLabel(summary.storeName)} · " +
                            stringResource(R.string.ie_local_backup_export_entries, summary.entryCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (summary.storeName == AppBackupStores.MEMOS) {
                        Text(
                            text = stringResource(R.string.ie_local_backup_export_images, summary.memoImageCount),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppDialogPrimaryButton(enabled = !busy, onClick = onConfirm) {
                Text(stringResource(R.string.ie_local_backup_export))
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.ie_cancel))
            }
        },
    )
}

@Composable
internal fun LocalBackupImportDiffDialog(
    diff: LocalBackupDiffResult,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // The restore action is intentionally stacked. Its label describes a destructive overwrite,
    // and keeping it on its own full-width row prevents translations and large text from being
    // clipped by the neighbouring cancel button.
    val stackActions = true
    AppAlertDialog(
        onDismissRequest = onDismiss,
        stackActions = stackActions,
        tone = AppDialogTone.Danger,
        icon = { Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.ie_local_backup_import_changes_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.ie_local_backup_import_changes_body))
                if (diff.isEmpty) {
                    Text(
                        stringResource(R.string.ie_local_backup_no_changes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LocalBackupDiffSection(
                        title = stringResource(R.string.ie_local_backup_added),
                        stores = diff.stores,
                        entries = { it.added },
                    )
                    LocalBackupDiffSection(
                        title = stringResource(R.string.ie_local_backup_modified),
                        stores = diff.stores,
                        entries = { it.modified },
                    )
                    LocalBackupDiffSection(
                        title = stringResource(R.string.ie_local_backup_deleted),
                        stores = diff.stores,
                        entries = { it.removed },
                    )
                    if (diff.memoImagesChanged) {
                        Text(
                            text = stringResource(
                                R.string.ie_local_backup_import_images,
                                diff.currentMemoImageCount,
                                diff.incomingMemoImageCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    stringResource(R.string.ie_local_backup_restore_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            AppDialogDangerButton(
                enabled = !busy,
                onClick = onConfirm,
                modifier = if (stackActions) Modifier.fillMaxWidth() else Modifier,
                shape = CircleShape,
                adaptiveHeight = true,
            ) {
                Text(
                    text = if (busy) stringResource(R.string.ie_local_backup_processing)
                    else stringResource(R.string.ie_local_backup_restore_confirm),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.ie_cancel))
            }
        },
    )
}

@Composable
private fun LocalBackupDiffSection(
    title: String,
    stores: List<LocalBackupStoreDiff>,
    entries: (LocalBackupStoreDiff) -> List<LocalBackupChange>,
) {
    val changedStores = stores.mapNotNull { store ->
        entries(store).takeIf { it.isNotEmpty() }?.let { store to it }
    }
    if (changedStores.isNotEmpty()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        changedStores.forEach { (store, storeEntries) ->
            storeEntries.forEach { entry ->
                Text(
                    text = stringResource(
                        R.string.ie_local_backup_change_store,
                        localBackupStoreLabel(store.storeName),
                        entry.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun localBackupStoreLabel(storeName: String): String = when (storeName) {
    com.x500x.cursimple.core.data.AppBackupStores.USER_PREFERENCES -> stringResource(R.string.ie_local_backup_store_settings)
    com.x500x.cursimple.core.data.AppBackupStores.SCHEDULE -> stringResource(R.string.ie_local_backup_store_schedule)
    com.x500x.cursimple.core.data.AppBackupStores.MANUAL_COURSES -> stringResource(R.string.ie_local_backup_store_manual)
    com.x500x.cursimple.core.data.AppBackupStores.COURSE_NOTES -> stringResource(R.string.ie_local_backup_store_notes)
    com.x500x.cursimple.core.data.AppBackupStores.TERM_PROFILES -> stringResource(R.string.ie_local_backup_store_terms)
    com.x500x.cursimple.core.data.AppBackupStores.WIDGET_PREFERENCES -> stringResource(R.string.ie_local_backup_store_widgets)
    com.x500x.cursimple.core.data.AppBackupStores.REMINDERS -> stringResource(R.string.ie_local_backup_store_reminders)
    com.x500x.cursimple.core.data.AppBackupStores.PLUGIN_REGISTRY -> stringResource(R.string.ie_local_backup_store_plugins)
    com.x500x.cursimple.core.data.AppBackupStores.PLUGIN_COMPONENTS -> stringResource(R.string.ie_local_backup_store_components)
    com.x500x.cursimple.core.data.AppBackupStores.SCHEDULE_EVENTS -> stringResource(R.string.ie_local_backup_store_events)
    com.x500x.cursimple.core.data.AppBackupStores.MEMOS -> stringResource(R.string.ie_local_backup_store_memos)
    com.x500x.cursimple.core.data.AppBackupStores.ATTENDANCE -> stringResource(R.string.ie_local_backup_store_attendance)
    else -> storeName
}
