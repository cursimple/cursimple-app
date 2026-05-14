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
        title = { Text(if (containsExam) "批量设置考试提醒" else "批量设置提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (containsExam) {
                        "将为已选中的 $selectedCount 项内容统一创建考试/课程提醒。"
                    } else {
                        "将为已选中的 $selectedCount 门课程统一创建上课前提醒。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = advanceMinutesText,
                    onValueChange = { advanceMinutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("提前分钟数") },
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
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
