package com.x500x.cursimple.feature.schedule

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.memo.MemoImportPlan
import com.x500x.cursimple.core.data.memo.MemoTransferCodec
import com.x500x.cursimple.core.data.memo.MemoShareCode
import com.x500x.cursimple.core.data.memo.MemoImageStore
import com.x500x.cursimple.core.data.memo.MemoTransferImage
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoImportSheet(
    uri: Uri? = null,
    code: String? = null,
    courses: List<CourseItem>,
    existing: List<MemoNote>,
    onImport: suspend (List<MemoNote>) -> Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember(uri, code) { mutableStateOf<MemoImportPlan?>(null) }
    var failure by remember(uri, code) { mutableStateOf(false) }
    var saving by remember(uri, code) { mutableStateOf(false) }
    var saved by remember(uri, code) { mutableStateOf<Int?>(null) }
    var retry by remember(uri, code) { mutableIntStateOf(0) }
    var images by remember(uri, code) { mutableStateOf<List<MemoTransferImage>>(emptyList()) }
    LaunchedEffect(uri, code, retry) {
        failure = false
        plan = null
        try {
            plan = withContext(Dispatchers.IO) {
                val bundle = if (code != null) MemoShareCode.decode(code) else {
                    val bytes = context.contentResolver.openInputStream(requireNotNull(uri))?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= MemoTransferCodec.MAX_BYTES)
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: error("cannot_read_notes")
                    val text = bytes.toString(Charsets.UTF_8)
                    if (MemoShareCode.recognizes(text)) MemoShareCode.decode(text) else MemoTransferCodec.decode(bytes)
                }
                images = bundle.images
                MemoTransferCodec.prepare(bundle, courses, existing, System.currentTimeMillis())
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failure = true }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("memo-import-sheet"),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.memo_import_action), style = MaterialTheme.typography.titleLarge)
            when {
                saved != null -> Text(stringResource(R.string.memo_import_success, saved!!), modifier = Modifier.testTag("memo-import-success"))
                failure -> {
                    Text(stringResource(R.string.memo_import_failed), color = MaterialTheme.colorScheme.error)
                    AppOutlinedButton(onClick = { retry++ }) { AppAtomicText(stringResource(R.string.memo_share_retry)) }
                }
                plan == null -> CircularProgressIndicator()
                else -> {
                    val ready = plan!!
                    Text(stringResource(R.string.memo_import_summary, ready.notes.size, ready.skipped))
                    Text(stringResource(R.string.memo_import_mapping_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ready.notes.map { it.courseTitle.ifBlank { context.getString(R.string.memo_notebook_other) } }.distinct().forEach {
                        Text(it, style = MaterialTheme.typography.titleSmall)
                    }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { MemoImageStore(context).install(images) }
                                    saved = onImport(ready.notes)
                                }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { failure = true }
                                finally { saving = false }
                            }
                        },
                        enabled = !saving && ready.notes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("memo-import-confirm"),
                    ) {
                        if (saving) CircularProgressIndicator()
                        else AppAtomicText(stringResource(R.string.memo_import_confirm))
                    }
                }
            }
            AppOutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                AppAtomicText(stringResource(if (saved == null) R.string.schedule_action_cancel else R.string.schedule_action_close))
            }
        }
    }
}
