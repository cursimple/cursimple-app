package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import java.time.ZoneOffset
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.FormatStyle
import java.time.format.TextStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.schedule_app_alarm_edit_title),
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
    val defaultTitle = stringResource(R.string.schedule_manual_alarm_default_title)
    val defaultMessage = stringResource(R.string.schedule_manual_alarm_default_message)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var message by rememberSaveable { mutableStateOf(defaultMessage) }
    var ringtone by rememberSaveable { mutableStateOf<String?>(null) }
    var alertMode by rememberSaveable { mutableStateOf<AlarmAlertMode?>(null) }
    var duration by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_RING_DURATION_SECONDS.toString()) }
    var interval by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS.toString()) }
    var count by rememberSaveable { mutableStateOf(DEFAULT_APP_ALARM_REPEAT_COUNT.toString()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()

    if (showDatePicker) {
        AlarmDatePickerDialog(
            initial = date ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onPick = {
                dateText = it.toString()
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        AlarmTimePickerDialog(
            initial = time ?: LocalTime.of(8, 0),
            onDismiss = { showTimePicker = false },
            onPick = {
                timeText = TIME_FORMAT.format(it)
                showTimePicker = false
            },
        )
    }
    val settings = editableSettings(ringtone, alertMode, duration, interval, count)
    val canSave = date != null && time != null && settings != null && title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_manual_alarm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AlarmWhenRow(
                    date = date,
                    time = time,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text(stringResource(R.string.schedule_alarm_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(80) },
                    label = { Text(stringResource(R.string.schedule_alarm_message_label)) },
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
            ) { Text(stringResource(R.string.schedule_action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()

    if (showDatePicker) {
        AlarmDatePickerDialog(
            initial = date ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onPick = {
                dateText = it.toString()
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        AlarmTimePickerDialog(
            initial = time ?: LocalTime.of(8, 0),
            onDismiss = { showTimePicker = false },
            onPick = {
                timeText = TIME_FORMAT.format(it)
                showTimePicker = false
            },
        )
    }
    val settings = editableSettings(ringtone, alertMode, duration, interval, count)
    val canSave = date != null && time != null && settings != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AlarmWhenRow(
                    date = date,
                    time = time,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
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
                    onSave(settings?.copy(triggerAtMillis = millis) ?: EditableAppAlarmSettings())
                },
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
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
            NumberField(stringResource(R.string.schedule_alarm_duration_label), duration, 5..600, onDuration, Modifier.weight(1f))
            NumberField(stringResource(R.string.schedule_alarm_interval_label), interval, 5..3600, onInterval, Modifier.weight(1f))
        }
        NumberField(stringResource(R.string.schedule_alarm_count_label), count, 1..10, onCount, Modifier.fillMaxWidth())
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

@OptIn(ExperimentalLayoutApi::class)
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
                Text(stringResource(R.string.schedule_ringtone_section), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(alarmRingtoneLabelRes(ringtoneUri)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onUseDefault,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_ringtone_default), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onPickSystem,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_ringtone_system), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onPickLocal,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_ringtone_local), maxLines = 2)
                }
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
            Text(stringResource(R.string.schedule_alert_mode_section), fontWeight = FontWeight.SemiBold)
            // 四个选项竖排会把弹窗撑得很高，改成一行放不下时自动折行的选项芯片
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = { Text(stringResource(alarmAlertModeLabelRes(mode)), maxLines = 1) },
                    )
                }
            }
        }
    }
}

@StringRes
internal fun alarmRingtoneLabelRes(ringtoneUri: String?): Int {
    val uri = ringtoneUri?.takeIf { it.isNotBlank() } ?: return R.string.schedule_ringtone_default
    return if (isLocalAudioRingtoneUri(uri)) {
        R.string.schedule_ringtone_local
    } else {
        R.string.schedule_ringtone_system
    }
}

@StringRes
internal fun alarmAlertModeLabelRes(mode: AlarmAlertMode?): Int = when (mode) {
    null -> R.string.schedule_alert_mode_default
    AlarmAlertMode.RingOnly -> R.string.schedule_alert_mode_ring
    AlarmAlertMode.VibrateOnly -> R.string.schedule_alert_mode_vibrate
    AlarmAlertMode.RingAndVibrate -> R.string.schedule_alert_mode_ring_vibrate
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 日期跟随界面语言，日期与星期都取当前区域的写法。 */
@Composable
private fun formatPickerDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$day $weekday"
}

/** 日期和时间各占一半，点开用系统选择器，不再让用户自己敲冒号。 */
@Composable
private fun AlarmWhenRow(
    date: LocalDate?,
    time: LocalTime?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        AlarmPickerField(
            label = stringResource(R.string.schedule_alarm_date_label),
            value = date?.let { formatPickerDate(it) }.orEmpty(),
            onClick = onPickDate,
            modifier = Modifier.weight(1f),
        )
        AlarmPickerField(
            label = stringResource(R.string.schedule_alarm_time_label),
            value = time?.format(TIME_FORMAT).orEmpty(),
            emphasize = true,
            onClick = onPickTime,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlarmPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = if (emphasize) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    // 选择器按 UTC 记日期，换算回本地日历日才不会差一天
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_alarm_time_label)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}
