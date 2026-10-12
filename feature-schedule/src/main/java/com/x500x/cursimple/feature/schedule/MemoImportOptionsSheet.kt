package com.x500x.cursimple.feature.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.memo.MemoShareCode
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.sharing.QrCodeCodec
import com.x500x.cursimple.feature.schedule.sharing.QrScannerOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoImportOptionsSheet(onCode: (String) -> Unit, onFile: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    fun accept(value: String?) {
        scanning = false
        if (value != null && MemoShareCode.recognizes(value)) onCode(value)
        else failure = R.string.memo_code_invalid
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else failure = R.string.memo_code_camera_permission
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            reading = true; failure = null
            scope.launch {
                try {
                    val value = withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768)
                        val options = BitmapFactory.Options()
                        options.inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                            ?: error("cannot_read_image")
                        try { QrCodeCodec.decodeBitmap(bitmap) } finally { bitmap.recycle() }
                    }
                    accept(value)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failure = R.string.memo_code_image_failed }
                finally { reading = false }
            }
        }
    }
    if (scanning) {
        Dialog(onDismissRequest = { scanning = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            QrScannerOverlay(::accept, { scanning = false }, stringResource(R.string.memo_code_scan_hint),
                stringResource(R.string.memo_code_camera_failed), stringResource(R.string.memo_share_retry),
                stringResource(R.string.schedule_action_cancel), tagPrefix = "memo")
        }
        return
    }
    ModalBottomSheet(onDismissRequest = { if (!reading) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("memo-import-options")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.memo_import_action), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(text, { if (it.length <= MemoShareCode.MAX_CHARS * 2) { text = it; failure = null } },
                label = { Text(stringResource(R.string.memo_code_input_label)) }, minLines = 3, maxLines = 5,
                enabled = !reading, modifier = Modifier.fillMaxWidth().testTag("memo-import-code-input"))
            Button(onClick = { accept(text) }, enabled = text.isNotBlank() && !reading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memo-import-code-preview")) {
                AppAtomicText(stringResource(R.string.memo_code_preview_import))
            }
            AppOutlinedButton(onClick = {
                failure = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }, enabled = !reading, modifier = Modifier.fillMaxWidth().testTag("memo-import-scan")) {
                AppAtomicText(stringResource(R.string.memo_code_scan))
            }
            AppOutlinedButton(onClick = { photo.launch("image/*") }, enabled = !reading,
                modifier = Modifier.fillMaxWidth().testTag("memo-import-photo")) {
                AppAtomicText(stringResource(R.string.memo_code_from_photo))
            }
            AppOutlinedButton(onClick = onFile, enabled = !reading, modifier = Modifier.fillMaxWidth().testTag("memo-import-file")) {
                AppAtomicText(stringResource(R.string.memo_code_from_file))
            }
            if (reading) CircularProgressIndicator()
            failure?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            AppOutlinedButton(onClick = onDismiss, enabled = !reading, modifier = Modifier.fillMaxWidth()) {
                AppAtomicText(stringResource(R.string.schedule_action_cancel))
            }
        }
    }
}
