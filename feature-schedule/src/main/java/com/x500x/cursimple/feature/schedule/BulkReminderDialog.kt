package com.x500x.cursimple.feature.schedule

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun BulkReminderDialog(
    selectedCount: Int,
    defaultAdvanceMinutes: Int = 20,
    containsExam: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (advanceMinutes: Int, ringtoneUri: String?) -> Unit,
) {
    var advanceMinutesText by rememberSaveable(defaultAdvanceMinutes) { mutableStateOf(defaultAdvanceMinutes.toString()) }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val systemRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.pickedAlarmRingtoneUri()?.let { ringtoneUri = it.toString() }
    }
    val localAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (takePersistableAudioReadPermission(context, uri)) {
                ringtoneUri = uri.toString()
            } else {
                showAudioPermissionFailedToast(context)
            }
        }
    }
    val advance = advanceMinutesText.toIntOrNull()
    val canSave = advance != null && advance in 0..720

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(if (containsExam) R.string.schedule_bulk_reminder_exam_title else R.string.schedule_bulk_reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (containsExam) {
                        stringResource(R.string.schedule_bulk_reminder_body_exam, selectedCount)
                    } else {
                        stringResource(R.string.schedule_bulk_reminder_body, selectedCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = advanceMinutesText,
                    onValueChange = { advanceMinutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.schedule_advance_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                AlarmRingtoneSelector(
                    ringtoneUri = ringtoneUri,
                    onUseDefault = { ringtoneUri = null },
                    onPickSystem = {
                        launchAlarmRingtonePicker(context, ringtoneUri) { intent ->
                            systemRingtoneLauncher.launch(intent)
                        }
                    },
                    onPickLocal = { localAudioLauncher.launch(arrayOf("audio/*")) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(advance ?: 20, ringtoneUri) },
                enabled = canSave,
            ) { Text(stringResource(R.string.schedule_action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}
