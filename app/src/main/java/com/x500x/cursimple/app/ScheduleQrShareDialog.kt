@file:Suppress("LocalContextGetResourceValueCall")
package com.x500x.cursimple.app

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.*
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ScheduleQrShareDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun perform(failureRes: Int = R.string.ie_image_save_failed, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = context.getString(failureRes) }
            finally { busy = false }
        }
    }
    fun saved() { Toast.makeText(context, context.getString(R.string.ie_image_saved), Toast.LENGTH_SHORT).show() }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) perform { writeSharePng(context, bitmap, uri); saved() }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ie_qr_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(bitmap.asImageBitmap(), stringResource(R.string.ie_qr_dialog_title),
                    Modifier.fillMaxWidth().aspectRatio(1f).testTag("schedule-qr-image"))
                Text(stringResource(R.string.ie_qr_dialog_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perform { saveQrToPictures(context, bitmap); saved() }
                    else saveDocument.launch("cursimple-qr.png")
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("schedule-qr-save")) {
                    AppAtomicText(stringResource(if (busy) R.string.ie_processing else R.string.ie_save_image))
                }
                AppOutlinedButton(onClick = { perform(R.string.ie_text_share_failed) {
                    val intent = qrImageShareIntent(context, bitmap)
                    launchScheduleShare(context, intent, context.getString(R.string.ie_qr_dialog_title))
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("schedule-qr-share")) {
                    AppAtomicText(stringResource(R.string.ie_share_image))
                }
            }
        },
        confirmButton = { AppOutlinedButton(onClick = onDismiss, enabled = !busy) {
            AppAtomicText(stringResource(R.string.ie_close))
        } },
    )
}
