package com.x500x.cursimple.feature.schedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.x500x.cursimple.core.data.memo.MemoShareCode
import com.x500x.cursimple.core.data.memo.MemoImageStore
import com.x500x.cursimple.core.data.memo.MemoShareCodeException
import com.x500x.cursimple.core.data.memo.MemoShareCodeFailure
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.sharing.QrCodeCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoCodeShareSheet(notes: List<MemoNote>, initiallyQr: Boolean,
    onBack: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember(notes) { mutableStateOf<String?>(null) }
    var qr by remember(notes) { mutableStateOf<Bitmap?>(null) }
    var showQr by remember { mutableStateOf(initiallyQr) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // Resolved at composition level so a locale change is picked up; the callbacks below are not
    // recomposed and cannot read resources themselves.
    val chooserTitle = stringResource(R.string.memo_share_chooser)
    val codeLabel = stringResource(R.string.memo_share_format_code)
    LaunchedEffect(notes, retry) {
        loading = true; failure = null
        try {
            val result = withContext(Dispatchers.Default) {
                val images = withContext(Dispatchers.IO) { MemoImageStore(context).exportImages(notes) }
                val value = MemoShareCode.encode(notes, images)
                value to if (value.length <= MemoShareCode.MAX_QR_CHARS) QrCodeCodec.encodeToBitmap(value, 1024) else null
            }
            code = result.first; qr = result.second
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            failure = if (error is MemoShareCodeException && error.reason == MemoShareCodeFailure.TooLarge)
                R.string.memo_code_too_large else R.string.memo_share_failed
        } finally { loading = false }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failure = null
        scope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = R.string.memo_share_launch_failed }
            finally { busy = false }
        }
    }
    val saveQr = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = qr
        if (uri != null && bitmap != null) perform {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            saved = true
        }
    }
    fun share(intent: Intent) {
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
            clipData = intent.clipData
            if (intent.clipData != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("memo-code-share-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCircleIconButton(onClick = onBack, enabled = !busy, modifier = Modifier.testTag("memo-code-back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.memo_code_formats))
                }
                Text(stringResource(R.string.memo_share_title), style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showQr, { showQr = false }, enabled = !busy,
                    label = { AppAtomicText(stringResource(R.string.memo_share_format_code)) })
                FilterChip(showQr, { showQr = true }, enabled = !busy,
                    label = { AppAtomicText(stringResource(R.string.memo_share_format_qr)) })
            }
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            if (showQr && code != null) {
                val bitmap = qr
                if (bitmap == null) Text(stringResource(R.string.memo_qr_too_large), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Image(bitmap.asImageBitmap(), stringResource(R.string.memo_share_format_qr),
                        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
                            .testTag("memo-share-qr-image"))
                    Button(onClick = { saveQr.launch("cursimple-notes-qr.png") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memo-share-qr-save")) {
                        AppAtomicText(stringResource(R.string.memo_qr_save))
                    }
                    AppOutlinedButton(onClick = { perform {
                        val intent = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "memos").apply { mkdirs() }
                            val file = File.createTempFile("notes-qr-", ".png", dir)
                            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        share(intent)
                    } }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("memo-share-qr-send")) {
                        AppAtomicText(stringResource(R.string.memo_qr_share))
                    }
                }
            } else if (code != null) {
                SelectionContainer {
                    Text(code!!, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                            .testTag("memo-share-code-value"))
                }
                Button(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(codeLabel, code!!))
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memo-share-code-copy")) {
                    AppAtomicText(stringResource(R.string.memo_code_copy))
                }
                AppOutlinedButton(onClick = { perform {
                    share(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code!!) })
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("memo-share-code-send")) {
                    AppAtomicText(stringResource(R.string.memo_code_share))
                }
            }
            if (saved) Text(stringResource(R.string.memo_qr_saved), color = MaterialTheme.colorScheme.primary)
            failure?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (!loading && code == null) AppOutlinedButton(onClick = { retry++ }, enabled = !busy) {
                AppAtomicText(stringResource(R.string.memo_share_retry))
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            AppOutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                AppAtomicText(stringResource(R.string.schedule_action_close))
            }
        }
    }
}
