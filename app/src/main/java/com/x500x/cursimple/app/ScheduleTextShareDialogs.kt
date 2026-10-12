@file:Suppress("LocalContextGetResourceValueCall")
package com.x500x.cursimple.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.*
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun ScheduleTextShareDialog(payload: ScheduleSharePayload, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var encoded by remember(payload) { mutableStateOf<String?>(null) }
    var error by remember(payload) { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(payload) {
        try { encoded = withContext(Dispatchers.Default) { ScheduleShareCodec.encode(payload) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = context.getString(R.string.ie_export_failed_retry) }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ie_text_share_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ie_text_share_hint), style = MaterialTheme.typography.bodyMedium)
                val value = encoded
                if (value == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (value != null) {
                    // Show a bounded excerpt; actions always use the complete, unchanged code.
                    OutlinedTextField(value = value.take(360) + if (value.length > 360) "…" else "",
                        onValueChange = {}, readOnly = true, minLines = 2, maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().testTag("schedule-share-code"))
                    Button(onClick = {
                        runCatching {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ie_text_share_title), value))
                        }.onSuccess { copied = true }.onFailure { error = context.getString(R.string.ie_text_copy_failed) }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("schedule-text-copy")) {
                        AppAtomicText(stringResource(if (copied) R.string.ie_text_copied else R.string.ie_text_copy))
                    }
                    AppOutlinedButton(onClick = {
                        runCatching { launchScheduleShare(context, Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value)
                            putExtra(Intent.EXTRA_SUBJECT, payload.termName ?: context.getString(R.string.ie_text_share_title))
                        }, context.getString(R.string.ie_text_share_title)) }
                            .onFailure { error = context.getString(R.string.ie_text_share_failed) }
                    }, modifier = Modifier.fillMaxWidth().testTag("schedule-text-share")) {
                        AppAtomicText(stringResource(R.string.ie_text_send))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { AppOutlinedButton(onClick = onDismiss) { AppAtomicText(stringResource(R.string.ie_close)) } },
    )
}

@Composable
internal fun ScheduleTextImportDialog(onDecoded: (ScheduleSharePayload) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ie_text_import_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ie_text_import_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = input, onValueChange = { input = it; error = null }, enabled = !busy,
                    minLines = 3, maxLines = 6, isError = error != null,
                    label = { Text(stringResource(R.string.ie_text_code_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("schedule-text-input"))
                AppOutlinedButton(onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (pasted.isNullOrBlank()) error = context.getString(R.string.ie_text_clipboard_empty)
                    else { input = pasted; error = null }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("schedule-text-paste")) {
                    AppAtomicText(stringResource(R.string.ie_text_paste))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedule-text-error")) }
            }
        },
        confirmButton = {
            Button(onClick = {
                busy = true; error = null
                scope.launch {
                    val result = withContext(Dispatchers.Default) { ScheduleShareCodec.decode(input) }
                    busy = false
                    result.onSuccess(onDecoded).onFailure { error = context.shareDecodeMessage(it) }
                }
            }, enabled = !busy && input.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-text-import")) {
                AppAtomicText(stringResource(if (busy) R.string.ie_processing else R.string.ie_text_read))
            }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss, enabled = !busy) { AppAtomicText(stringResource(R.string.ie_cancel)) } },
    )
}
