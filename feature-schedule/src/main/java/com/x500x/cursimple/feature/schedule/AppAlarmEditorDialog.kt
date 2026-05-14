package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun AppAlarmEditorDialog(
    record: SystemAlarmRecord,
    onDismiss: () -> Unit,
    onSave: (EditableAppAlarmSettings) -> Unit,
) {
    AlarmSettingsDialogContent(
        title = "编辑闹钟",
        initialRingtone = record.ringtoneUriOverride.orEmpty(),
        initialDuration = (record.ringDurationSeconds ?: DEFAULT_APP_ALARM_RING_DURATION_SECONDS).toString(),
        initialInterval = (record.repeatIntervalSeconds ?: DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS).toString(),
        initialCount = (record.repeatCount ?: DEFAULT_APP_ALARM_REPEAT_COUNT).toString(),
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
internal fun ManualAppAlarmDialog(
    onDismiss: () -> Unit,
    onCreate: (Long, String, String, EditableAppAlarmSettings) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var timeText by rememberSaveable { mutableStateOf(LocalTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var title by rememberSaveable { mutableStateOf("手动闹钟") }
    var message by rememberSaveable { mutableStateOf("手动创建的提醒") }
    var ringtone by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_RING_DURATION_SECONDS.toString()) }
    var interval by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS.toString()) }
    var count by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_COUNT.toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val settings = editableSettings(ringtone, duration, interval, count)
    val canSave = date != null && time != null && settings != null && title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建 APP 自管闹钟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it.take(10) },
                        label = { Text("日期") },
                        modifier = Modifier.weight(1f),
                        isError = dateText.isNotBlank() && date == null,
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("时间") },
                        modifier = Modifier.weight(1f),
                        isError = timeText.isNotBlank() && time == null,
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(80) },
                    label = { Text("消息") },
                    modifier = Modifier.fillMaxWidth(),
                )
                AlarmSettingsFields(
                    ringtone = ringtone,
                    duration = duration,
                    interval = interval,
                    count = count,
                    onRingtone = { ringtone = it },
                    onDuration = { duration = it },
                    onInterval = { interval = it },
                    onCount = { count = it },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val millis = LocalDateTime.of(date, time)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    onCreate(millis, title.trim(), message.trim(), settings ?: EditableAppAlarmSettings())
                },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AlarmSettingsDialogContent(
    title: String,
    initialRingtone: String,
    initialDuration: String,
    initialInterval: String,
    initialCount: String,
    onDismiss: () -> Unit,
    onSave: (EditableAppAlarmSettings) -> Unit,
) {
    var ringtone by rememberSaveable { mutableStateOf(initialRingtone) }
    var duration by rememberSaveable { mutableStateOf(initialDuration) }
    var interval by rememberSaveable { mutableStateOf(initialInterval) }
    var count by rememberSaveable { mutableStateOf(initialCount) }
    val settings = editableSettings(ringtone, duration, interval, count)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AlarmSettingsFields(
                ringtone = ringtone,
                duration = duration,
                interval = interval,
                count = count,
                onRingtone = { ringtone = it },
                onDuration = { duration = it },
                onInterval = { interval = it },
                onCount = { count = it },
            )
        },
        confirmButton = {
            Button(
                enabled = settings != null,
                onClick = { onSave(settings ?: EditableAppAlarmSettings()) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AlarmSettingsFields(
    ringtone: String,
    duration: String,
    interval: String,
    count: String,
    onRingtone: (String) -> Unit,
    onDuration: (String) -> Unit,
    onInterval: (String) -> Unit,
    onCount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = ringtone,
            onValueChange = { onRingtone(it.take(240)) },
            label = { Text("铃声 URI") },
            placeholder = { Text("可空，使用默认铃声") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("时长秒", duration, 5..600, onDuration, Modifier.weight(1f))
            NumberField("间隔秒", interval, 5..3600, onInterval, Modifier.weight(1f))
        }
        NumberField("响铃次数", count, 1..10, onCount, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    range: IntRange,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    val parsed = value.toIntOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotBlank() && (parsed == null || parsed !in range),
        modifier = modifier,
    )
}

private fun editableSettings(
    ringtone: String,
    duration: String,
    interval: String,
    count: String,
): EditableAppAlarmSettings? {
    val durationValue = duration.toIntOrNull()
    val intervalValue = interval.toIntOrNull()
    val countValue = count.toIntOrNull()
    if (durationValue == null || durationValue !in 5..600) return null
    if (intervalValue == null || intervalValue !in 5..3600) return null
    if (countValue == null || countValue !in 1..10) return null
    return EditableAppAlarmSettings(
        ringtoneUriOverride = ringtone.takeIf { it.isNotBlank() },
        ringDurationSeconds = durationValue,
        repeatIntervalSeconds = intervalValue,
        repeatCount = countValue,
    )
}
