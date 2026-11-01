package com.x500x.cursimple.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.ImportDiagnosis
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

/** Says why an import failed and what to try, with the raw message one tap from the clipboard. */
@Composable
fun ImportFailureDialog(diagnosis: ImportDiagnosis, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val reason = stringResource(diagnosis.reason)
    val hint = stringResource(diagnosis.hint)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("import-failure-dialog"),
        title = { Text(stringResource(R.string.import_diag_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("import-failure-reason"),
                )
                Text(stringResource(R.string.import_diag_try), style = MaterialTheme.typography.labelLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("import-failure-hint"))
                if (diagnosis.detail.isNotBlank()) {
                    Text(
                        stringResource(R.string.import_diag_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    SelectionContainer {
                        Text(
                            diagnosis.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("import-failure-close")) {
                Text(stringResource(R.string.import_diag_close))
            }
        },
        dismissButton = {
            if (diagnosis.detail.isNotBlank()) {
                AppOutlinedButton(
                    onClick = { copyImportDetails(context, "$reason\n${diagnosis.detail}") },
                    modifier = Modifier.testTag("import-failure-copy"),
                ) { Text(stringResource(R.string.import_diag_copy)) }
            }
        },
    )
}

private fun copyImportDetails(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("import", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, com.x500x.cursimple.feature.schedule.R.string.memo_note_copied, Toast.LENGTH_SHORT).show()
    }
}
