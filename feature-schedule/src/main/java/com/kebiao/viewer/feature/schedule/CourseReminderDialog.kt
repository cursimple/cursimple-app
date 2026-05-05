package com.kebiao.viewer.feature.schedule

import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.kernel.model.CourseItem

@Composable
fun CourseReminderDialog(
    course: CourseItem,
    onDismiss: () -> Unit,
    onConfirm: (advanceMinutes: Int, ringtoneUri: String?) -> Unit,
) {
    var advanceMinutesText by rememberSaveable(course.id) { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable(course.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtoneUri = uri?.toString()
    }
    val advance = advanceMinutesText.toIntOrNull()
    val canSave = advance != null && advance in 0..720

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("设置提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "第 ${course.time.startNode}-${course.time.endNode} 节 · ${course.location.ifBlank { "地点待定" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = advanceMinutesText,
                    onValueChange = { advanceMinutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("提前分钟数") },
                    singleLine = true,
                    isError = advanceMinutesText.isNotBlank() && !canSave,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (advanceMinutesText.isNotBlank() && !canSave) {
                            Text("请输入 0 到 720 分钟")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            launchAlarmRingtonePicker(context) { intent ->
                                ringtoneLauncher.launch(intent)
                            }
                        },
                    ) {
                        Text("选择铃声")
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (ringtoneUri.isNullOrBlank()) "系统默认铃声" else "已选择铃声",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(advance ?: 20, ringtoneUri) },
                enabled = canSave,
            ) {
                Text("保存提醒")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
