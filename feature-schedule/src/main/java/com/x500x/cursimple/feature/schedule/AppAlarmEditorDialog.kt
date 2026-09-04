package com.x500x.cursimple.feature.schedule

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_COUNT
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS
import com.x500x.cursimple.core.reminder.model.DEFAULT_APP_ALARM_RING_DURATION_SECONDS
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private val RING_DURATION_RANGE = 5..600
private val RING_INTERVAL_RANGE = 5..3600
private val RING_COUNT_RANGE = 1..10

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
    AlarmEditor(
        title = stringResource(R.string.schedule_app_alarm_edit_title),
        confirmLabel = stringResource(R.string.schedule_action_save),
        initialDate = trigger.toLocalDate(),
        initialTime = trigger.toLocalTime(),
        initialRingtone = record.ringtoneUriOverride,
        initialAlertMode = record.alertModeOverride,
        initialDuration = record.ringDurationSeconds ?: DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
        initialInterval = record.repeatIntervalSeconds ?: DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
        initialCount = record.repeatCount ?: DEFAULT_APP_ALARM_REPEAT_COUNT,
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        onDismiss = onDismiss,
        onConfirm = { millis, _, _, settings -> onSave(settings.copy(triggerAtMillis = millis)) },
    )
}

@Composable
internal fun ManualAppAlarmDialog(
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Long, String, String, EditableAppAlarmSettings) -> Unit,
) {
    AlarmEditor(
        title = stringResource(R.string.schedule_manual_alarm_title),
        confirmLabel = stringResource(R.string.schedule_action_create),
        initialDate = LocalDate.now(),
        initialTime = LocalTime.now().plusHours(1).withSecond(0).withNano(0),
        initialRingtone = null,
        initialAlertMode = null,
        initialDuration = DEFAULT_APP_ALARM_RING_DURATION_SECONDS,
        initialInterval = DEFAULT_APP_ALARM_REPEAT_INTERVAL_SECONDS,
        initialCount = DEFAULT_APP_ALARM_REPEAT_COUNT,
        editableContent = true,
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        onDismiss = onDismiss,
        onConfirm = onCreate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    title: String,
    confirmLabel: String,
    initialDate: LocalDate,
    initialTime: LocalTime,
    initialRingtone: String?,
    initialAlertMode: AlarmAlertMode?,
    initialDuration: Int,
    initialInterval: Int,
    initialCount: Int,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String, EditableAppAlarmSettings) -> Unit,
    editableContent: Boolean = false,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    var date by rememberSaveable { mutableStateOf(initialDate) }
    val defaultTitle = stringResource(R.string.schedule_manual_alarm_default_title)
    val defaultMessage = stringResource(R.string.schedule_manual_alarm_default_message)
    var alarmTitle by rememberSaveable { mutableStateOf(defaultTitle) }
    var alarmMessage by rememberSaveable { mutableStateOf(defaultMessage) }
    var ringtone by rememberSaveable { mutableStateOf(initialRingtone) }
    var alertMode by rememberSaveable { mutableStateOf(initialAlertMode) }
    var duration by rememberSaveable { mutableStateOf(initialDuration) }
    var interval by rememberSaveable { mutableStateOf(initialInterval) }
    var count by rememberSaveable { mutableStateOf(initialCount) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showRingtoneChooser by rememberSaveable { mutableStateOf(false) }
    var showAlertModeChooser by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        AlarmDatePickerDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPick = {
                date = it
                showDatePicker = false
            },
        )
    }
    if (showRingtoneChooser) {
        AlarmRingtoneChooserDialog(
            current = ringtone,
            onDismiss = { showRingtoneChooser = false },
            onUseDefault = {
                ringtone = null
                showRingtoneChooser = false
            },
            onPickSystem = {
                showRingtoneChooser = false
                onPickSystemRingtone { ringtone = it }
            },
            onPickLocal = {
                showRingtoneChooser = false
                onPickLocalAudio { ringtone = it }
            },
        )
    }
    if (showAlertModeChooser) {
        AlarmAlertModeChooserDialog(
            current = alertMode,
            onDismiss = { showAlertModeChooser = false },
            onSelect = {
                alertMode = it
                showAlertModeChooser = false
            },
        )
    }

    val canConfirm = !editableContent || alarmTitle.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlarmEditorSection(stringResource(R.string.schedule_alarm_section_when)) {
                    // 键盘录入的时分框，不用表盘，直接敲两个数字
                    TimeInput(state = timeState, modifier = Modifier.fillMaxWidth())
                    AlarmValueRow(
                        label = stringResource(R.string.schedule_alarm_date_label),
                        value = formatPickerDate(date),
                        onClick = { showDatePicker = true },
                    )
                }

                if (editableContent) {
                    AlarmEditorSection(stringResource(R.string.schedule_alarm_section_content)) {
                        OutlinedTextField(
                            value = alarmTitle,
                            onValueChange = { alarmTitle = it.take(40) },
                            label = { Text(stringResource(R.string.schedule_alarm_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = alarmMessage,
                            onValueChange = { alarmMessage = it.take(80) },
                            label = { Text(stringResource(R.string.schedule_alarm_message_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                AlarmEditorSection(stringResource(R.string.schedule_alarm_section_ring)) {
                    AlarmValueRow(
                        label = stringResource(R.string.schedule_ringtone_section),
                        value = stringResource(alarmRingtoneLabelRes(ringtone)),
                        onClick = { showRingtoneChooser = true },
                    )
                    AlarmValueRow(
                        label = stringResource(R.string.schedule_alert_mode_section),
                        value = stringResource(alarmAlertModeLabelRes(alertMode)),
                        onClick = { showAlertModeChooser = true },
                    )
                    AlarmStepperRow(
                        label = stringResource(R.string.schedule_alarm_duration_row),
                        value = stringResource(R.string.schedule_alarm_unit_seconds, duration),
                        canDecrease = duration > RING_DURATION_RANGE.first,
                        canIncrease = duration < RING_DURATION_RANGE.last,
                        onDecrease = { duration = (duration - 10).coerceAtLeast(RING_DURATION_RANGE.first) },
                        onIncrease = { duration = (duration + 10).coerceAtMost(RING_DURATION_RANGE.last) },
                    )
                    AlarmStepperRow(
                        label = stringResource(R.string.schedule_alarm_interval_row),
                        value = stringResource(R.string.schedule_alarm_unit_seconds, interval),
                        canDecrease = interval > RING_INTERVAL_RANGE.first,
                        canIncrease = interval < RING_INTERVAL_RANGE.last,
                        onDecrease = { interval = (interval - 30).coerceAtLeast(RING_INTERVAL_RANGE.first) },
                        onIncrease = { interval = (interval + 30).coerceAtMost(RING_INTERVAL_RANGE.last) },
                    )
                    AlarmStepperRow(
                        label = stringResource(R.string.schedule_alarm_count_row),
                        value = stringResource(R.string.schedule_alarm_unit_times, count),
                        canDecrease = count > RING_COUNT_RANGE.first,
                        canIncrease = count < RING_COUNT_RANGE.last,
                        onDecrease = { count = (count - 1).coerceAtLeast(RING_COUNT_RANGE.first) },
                        onIncrease = { count = (count + 1).coerceAtMost(RING_COUNT_RANGE.last) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    val millis = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    val settings = EditableAppAlarmSettings(
                        ringtoneUriOverride = ringtone?.takeIf { it.isNotBlank() },
                        alertModeOverride = alertMode,
                        ringDurationSeconds = duration,
                        repeatIntervalSeconds = interval,
                        repeatCount = count,
                    )
                    onConfirm(millis, alarmTitle.trim(), alarmMessage.trim(), settings)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

@Composable
private fun AlarmEditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

/** 名称在左、当前值在右，同一行显示，点整行打开选择器。 */
@Composable
private fun AlarmValueRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 名称、数值和加减挤在同一行，加减用图标按钮，避免行高翻倍。 */
@Composable
private fun AlarmStepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            IconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.schedule_alarm_decrease),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.schedule_alarm_increase),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AlarmRingtoneChooserDialog(
    current: String?,
    onDismiss: () -> Unit,
    onUseDefault: () -> Unit,
    onPickSystem: () -> Unit,
    onPickLocal: () -> Unit,
) {
    val selected = alarmRingtoneLabelRes(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_ringtone_section)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AlarmChoiceRow(
                    label = stringResource(R.string.schedule_ringtone_default),
                    selected = selected == R.string.schedule_ringtone_default,
                    onClick = onUseDefault,
                )
                AlarmChoiceRow(
                    label = stringResource(R.string.schedule_ringtone_system),
                    selected = selected == R.string.schedule_ringtone_system,
                    onClick = onPickSystem,
                )
                AlarmChoiceRow(
                    label = stringResource(R.string.schedule_ringtone_local),
                    selected = selected == R.string.schedule_ringtone_local,
                    onClick = onPickLocal,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

@Composable
private fun AlarmAlertModeChooserDialog(
    current: AlarmAlertMode?,
    onDismiss: () -> Unit,
    onSelect: (AlarmAlertMode?) -> Unit,
) {
    val options = buildList<AlarmAlertMode?> {
        add(null)
        addAll(AlarmAlertMode.entries)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_alert_mode_section)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { mode ->
                    AlarmChoiceRow(
                        label = stringResource(alarmAlertModeLabelRes(mode)),
                        selected = current == mode,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

@Composable
private fun AlarmChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun AlarmAlertModeSelector(
    selected: AlarmAlertMode?,
    includeDefault: Boolean,
    onSelect: (AlarmAlertMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList<AlarmAlertMode?> {
        if (includeDefault) add(null)
        addAll(AlarmAlertMode.entries)
    }
    var showChooser by rememberSaveable { mutableStateOf(false) }
    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text(stringResource(R.string.schedule_alert_mode_section)) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { mode ->
                        AlarmChoiceRow(
                            label = stringResource(alarmAlertModeLabelRes(mode)),
                            selected = selected == mode,
                            onClick = {
                                onSelect(mode)
                                showChooser = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChooser = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showChooser = true },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_alert_mode_section),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(alarmAlertModeLabelRes(selected)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun AlarmRingtoneSelector(
    ringtoneUri: String?,
    onUseDefault: () -> Unit,
    onPickSystem: () -> Unit,
    onPickLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChooser by rememberSaveable { mutableStateOf(false) }
    if (showChooser) {
        AlarmRingtoneChooserDialog(
            current = ringtoneUri,
            onDismiss = { showChooser = false },
            onUseDefault = {
                onUseDefault()
                showChooser = false
            },
            onPickSystem = {
                showChooser = false
                onPickSystem()
            },
            onPickLocal = {
                showChooser = false
                onPickLocal()
            },
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showChooser = true },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_ringtone_section),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(alarmRingtoneLabelRes(ringtoneUri)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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

/** 日期跟随界面语言，日期与星期都取当前区域的写法。 */
@Composable
private fun formatPickerDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$day $weekday"
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
