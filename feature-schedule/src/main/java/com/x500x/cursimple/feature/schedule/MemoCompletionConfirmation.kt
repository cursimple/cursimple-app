package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.memo.memoTodoRevision
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.model.toggleChecklistLine
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

/** Capture the tapped task; confirmation applies only to the same unfinished content. */
class MemoCompletionRequest private constructor(
    val noteId: String,
    val lineIndex: Int?,
    val label: String,
    private val revision: String,
) {
    fun completedNote(latest: MemoNote): MemoNote? {
        if (latest.id != noteId || latest.completed || memoTodoRevision(latest.body) != revision) return null
        if (lineIndex == null) {
            if (latest.title != label) return null
            return latest.copy(completed = true)
        }
        val item = latest.body.lines().getOrNull(lineIndex)?.let(::parseChecklistLine) ?: return null
        if (item.first) return null
        return latest.copy(body = toggleChecklistLine(latest.body, lineIndex))
    }

    companion object {
        fun from(note: MemoNote, lineIndex: Int? = null): MemoCompletionRequest? {
            if (note.completed) return null
            val label = if (lineIndex == null) note.title else {
                val item = note.body.lines().getOrNull(lineIndex)?.let(::parseChecklistLine) ?: return null
                if (item.first) return null
                item.second
            }
            return MemoCompletionRequest(note.id, lineIndex, label, memoTodoRevision(note.body))
        }
    }
}

@Composable
internal fun MemoCompletionDialog(label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppConfirmationDialog(
        title = stringResource(R.string.memo_complete_confirm_title),
        message = stringResource(R.string.memo_complete_confirm_body, label.ifBlank { stringResource(R.string.memo_untitled) }),
        confirmLabel = stringResource(R.string.memo_complete_confirm_action),
        cancelLabel = stringResource(R.string.schedule_action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag("memo-complete-confirmation"),
    )
}

/** The desktop Activity hosts this compact prompt without opening a note editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoCompletionPrompt(
    label: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
    canConfirm: Boolean = true,
    error: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("memo-complete-confirmation"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.memo_complete_confirm_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.memo_complete_confirm_body, label.ifBlank { stringResource(R.string.memo_untitled) }),
            style = MaterialTheme.typography.bodyMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("memo-complete-error")) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("memo-complete-cancel")) {
                AppAtomicText(stringResource(R.string.schedule_action_cancel))
            }
            Button(onClick = onConfirm, enabled = canConfirm && !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("memo-complete-confirm")) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else AppAtomicText(stringResource(R.string.memo_complete_confirm_action))
            }
        }
    }
}
