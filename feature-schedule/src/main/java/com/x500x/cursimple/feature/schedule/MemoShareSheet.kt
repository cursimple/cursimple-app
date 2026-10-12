package com.x500x.cursimple.feature.schedule

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class MemoShareOption(
    val format: MemoShareFormat,
    val icon: ImageVector,
    val label: Int,
    val description: Int,
)

private val SHARE_OPTIONS = listOf(
    MemoShareOption(MemoShareFormat.ShareCode, Icons.Rounded.ContentCopy, R.string.memo_share_format_code, R.string.memo_share_format_code_desc),
    MemoShareOption(MemoShareFormat.QrCode, Icons.Rounded.QrCode, R.string.memo_share_format_qr, R.string.memo_share_format_qr_desc),
    MemoShareOption(MemoShareFormat.CurSimple, Icons.Rounded.SwapHoriz, R.string.memo_share_format_cursimple, R.string.memo_share_format_cursimple_desc),
    MemoShareOption(MemoShareFormat.Image, Icons.Rounded.Image, R.string.memo_share_format_image, R.string.memo_share_format_image_desc),
    MemoShareOption(MemoShareFormat.Word, Icons.Rounded.Description, R.string.memo_share_format_word, R.string.memo_share_format_word_desc),
    MemoShareOption(MemoShareFormat.Markdown, Icons.Rounded.Code, R.string.memo_share_format_markdown, R.string.memo_share_format_markdown_desc),
    MemoShareOption(MemoShareFormat.PlainText, Icons.Rounded.TextFields, R.string.memo_share_format_text, R.string.memo_share_format_text_desc),
)

/**
 * Format picker for sharing notes. [scopeLabel] names the list being shared; null shares the
 * single note in [notes].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoShareSheet(
    notes: List<MemoNote>,
    notebookOf: Map<String?, MemoNotebook>,
    scopeLabel: String?,
    onDismiss: () -> Unit,
    onImport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val zone = LocalAppZone.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var running by remember { mutableStateOf<MemoShareFormat?>(null) }
    var failure by remember { mutableStateOf<Pair<MemoShareFormat, String>?>(null) }
    var codeFormat by remember { mutableStateOf<MemoShareFormat?>(null) }
    val chooserTitle = stringResource(R.string.memo_share_chooser)
    val genericFailure = stringResource(R.string.memo_share_failed)
    val launchFailure = stringResource(R.string.memo_share_launch_failed)

    fun start(format: MemoShareFormat) {
        if (running != null || notes.isEmpty()) return
        if (format == MemoShareFormat.ShareCode || format == MemoShareFormat.QrCode) {
            codeFormat = format
            return
        }
        failure = null
        running = format
        scope.launch {
            val result = try {
                val doc = buildMemoShareDocument(context, notes, notebookOf, scopeLabel, BeijingTime.nowDateTimeIn(zone))
                Result.success(MemoShareExporter.export(context, doc, format, accent, notes))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                // OutOfMemoryError from very long images lands here too.
                Result.failure(error)
            }
            running = null
            result.onSuccess { intent ->
                val chooser = Intent.createChooser(intent, chooserTitle).apply {
                    clipData = intent.clipData
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(chooser) }
                    .onSuccess { onDismiss() }
                    .onFailure { failure = format to launchFailure }
            }.onFailure { failure = format to genericFailure }
        }
    }

    if (codeFormat != null) {
        MemoCodeShareSheet(notes, initiallyQr = codeFormat == MemoShareFormat.QrCode,
            onBack = { codeFormat = null }, onDismiss = onDismiss)
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("memo-share-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(if (onImport == null) R.string.memo_share_title else R.string.memo_transfer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (scopeLabel == null) {
                        notes.firstOrNull()?.title?.trim()?.ifBlank { null } ?: stringResource(R.string.memo_untitled)
                    } else {
                        stringResource(
                            R.string.memo_share_scope_summary,
                            scopeLabel,
                            pluralStringResource(R.plurals.memo_share_count, notes.size, notes.size),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.memo_share_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp).testTag("memo-share-empty"),
                )
            }
            if (onImport != null) AppOutlinedButton(
                onClick = { onDismiss(); onImport() },
                enabled = running == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memo-import-action"),
            ) { Text(stringResource(R.string.memo_import_action), maxLines = 1, softWrap = false) }

            SHARE_OPTIONS.forEach { option ->
                MemoShareOptionRow(
                    option = option,
                    running = running == option.format,
                    enabled = notes.isNotEmpty() && (running == null || running == option.format),
                    onClick = { start(option.format) },
                )
            }
            failure?.let { (format, message) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().testTag("memo-share-failure"),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        AppOutlinedButton(onClick = { start(format) }) {
                            AppAtomicText(stringResource(R.string.memo_share_retry))
                        }
                    }
                }
            }
            AppOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memo-share-cancel"),
            ) {
                AppAtomicText(stringResource(R.string.schedule_action_cancel))
            }
        }
    }
}

@Composable
private fun MemoShareOptionRow(
    option: MemoShareOption,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !running,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .semantics { role = Role.Button }
            .testTag("memo-share-${option.format.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(option.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(if (running) R.string.memo_share_working else option.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}
