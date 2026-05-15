package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AppAlarmEditorDialog(
    record: SystemAlarmRecord,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EditableAppAlarmSettings) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val trigger = Instant.ofEpochMilli(record.triggerAtMillis).atZone(zone).toLocalDateTime()
    AlarmSettingsDialogContent(
        title = "编辑闹钟",
        initialDate = trigger.toLocalDate().toString(),
        initialTime = DateTimeFormatter.ofPattern("HH:mm").format(trigger.toLocalTime()),
        initialRingtone = record.ringtoneUriOverride,
        initialAlertMode = record.alertModeOverride,
        initialDuration = (record.ringDurationSeconds ?: DEFAULT_APP_ALARM_RING_DURATION_SECONDS).toString(),
        initialInterval = (record.repeatIntervalSeconds ?: DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS).toString(),
        initialCount = (record.repeatCount ?: DEFAULT_APP_ALARM_REPEAT_COUNT).toString(),
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
internal fun ManualAppAlarmDialog(
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Long, String, String, EditableAppAlarmSettings) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var timeText by rememberSaveable { mutableStateOf(LocalTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var title by rememberSaveable { mutableStateOf("手动闹钟") }
    var message by rememberSaveable { mutableStateOf("手动创建的提醒") }
    var ringtone by rememberSaveable { mutableStateOf<String?>(null) }
    var alertMode by rememberSaveable { mutableStateOf<AlarmAlertMode?>(null) }
    var duration by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_RING_DURATION_SECONDS.toString()) }
    var interval by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS.toString()) }
    var count by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_COUNT.toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val settings = editableSettings(ringtone, alertMode, duration, interval, count)
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
                    alertMode = alertMode,
                    duration = duration,
                    interval = interval,
                    count = count,
                    onUseDefaultRingtone = { ringtone = null },
                    onPickSystemRingtone = { onPickSystemRingtone { ringtone = it } },
                    onPickLocalAudio = { onPickLocalAudio { ringtone = it } },
                    onAlertMode = { alertMode = it },
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
    initialDate: String,
    initialTime: String,
    initialRingtone: String?,
    initialAlertMode: AlarmAlertMode?,
    initialDuration: String,
    initialInterval: String,
    initialCount: String,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EditableAppAlarmSettings) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf(initialDate) }
    var timeText by rememberSaveable { mutableStateOf(initialTime) }
    var ringtone by rememberSaveable { mutableStateOf(initialRingtone) }
    var alertMode by rememberSaveable { mutableStateOf(initialAlertMode) }
    var duration by rememberSaveable { mutableStateOf(initialDuration) }
    var interval by rememberSaveable { mutableStateOf(initialInterval) }
    var count by rememberSaveable { mutableStateOf(initialCount) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val settings = editableSettings(ringtone, alertMode, duration, interval, count)
    val canSave = date != null && time != null && settings != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                AlarmSettingsFields(
                    ringtone = ringtone,
                    alertMode = alertMode,
                    duration = duration,
                    interval = interval,
                    count = count,
                    onUseDefaultRingtone = { ringtone = null },
                    onPickSystemRingtone = { onPickSystemRingtone { ringtone = it } },
                    onPickLocalAudio = { onPickLocalAudio { ringtone = it } },
                    onAlertMode = { alertMode = it },
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
                    onSave(settings?.copy(triggerAtMillis = millis) ?: EditableAppAlarmSettings())
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AlarmSettingsFields(
    ringtone: String?,
    alertMode: AlarmAlertMode?,
    duration: String,
    interval: String,
    count: String,
    onUseDefaultRingtone: () -> Unit,
    onPickSystemRingtone: () -> Unit,
    onPickLocalAudio: () -> Unit,
    onAlertMode: (AlarmAlertMode?) -> Unit,
    onDuration: (String) -> Unit,
    onInterval: (String) -> Unit,
    onCount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AlarmRingtoneSelector(
            ringtoneUri = ringtone,
            onUseDefault = onUseDefaultRingtone,
            onPickSystem = onPickSystemRingtone,
            onPickLocal = onPickLocalAudio,
        )
        AlarmAlertModeSelector(
            selected = alertMode,
            includeDefault = true,
            onSelect = onAlertMode,
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
    ringtone: String?,
    alertMode: AlarmAlertMode?,
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
        ringtoneUriOverride = ringtone?.takeIf { it.isNotBlank() },
        alertModeOverride = alertMode,
        ringDurationSeconds = durationValue,
        repeatIntervalSeconds = intervalValue,
        repeatCount = countValue,
    )
}

@Composable
internal fun AlarmRingtoneSelector(
    ringtoneUri: String?,
    onUseDefault: () -> Unit,
    onPickSystem: () -> Unit,
    onPickLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("铃声", fontWeight = FontWeight.SemiBold)
                Text(
                    alarmRingtoneLabel(ringtoneUri),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUseDefault) { Text("默认铃声") }
                TextButton(onClick = onPickSystem) { Text("系统铃声") }
                TextButton(onClick = onPickLocal) { Text("本地音频") }
            }
        }
    }
}

@Composable
internal fun AlarmAlertModeSelector(
    selected: AlarmAlertMode?,
    includeDefault: Boolean,
    onSelect: (AlarmAlertMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList {
        if (includeDefault) add(null)
        addAll(AlarmAlertMode.entries)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("提醒方式", fontWeight = FontWeight.SemiBold)
            options.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                    Spacer(Modifier.width(6.dp))
                    Text(alarmAlertModeLabel(mode))
                }
            }
        }
    }
}

internal fun alarmRingtoneLabel(ringtoneUri: String?): String {
    val uri = ringtoneUri?.takeIf { it.isNotBlank() } ?: return "默认铃声"
    return if (isLocalAudioRingtoneUri(uri)) {
        "本地音频"
    } else {
        "系统铃声"
    }
}

internal fun alarmAlertModeLabel(mode: AlarmAlertMode?): String = when (mode) {
    null -> "跟随默认"
    AlarmAlertMode.RingOnly -> "仅响铃"
    AlarmAlertMode.VibrateOnly -> "仅震动"
    AlarmAlertMode.RingAndVibrate -> "响铃并震动"
}
