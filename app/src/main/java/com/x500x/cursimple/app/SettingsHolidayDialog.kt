package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.builtInHolidayYears
import com.x500x.cursimple.core.kernel.model.entryOn
import com.x500x.cursimple.core.kernel.model.localDate
import com.x500x.cursimple.core.kernel.model.sortedUserEntries
import com.x500x.cursimple.core.kernel.model.userEntryOn
import java.time.LocalDate
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

/** 节假日与调休的查看与编辑。 */

@Composable
internal fun HolidayCalendarDialog(
    settings: HolidayCalendarSettings,
    onUpsert: (HolidayCalendarEntry) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var targetDate by rememberSaveable { mutableStateOf(today) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val userEntry = settings.userEntryOn(targetDate)
    val effectiveEntry = settings.entryOn(targetDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dest_holidays)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateChoiceButton(
                    label = stringResource(R.string.settings_date),
                    date = targetDate,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickDate = true },
                )
                Text(
                    text = holidayDateStatusText(targetDate, effectiveEntry, userEntry != null),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text(stringResource(R.string.settings_holiday_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onUpsert(
                            HolidayCalendarEntry(
                                date = targetDate.toString(),
                                kind = HolidayEntryKind.Holiday,
                                name = name.trim(),
                            ),
                        )
                        name = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_holiday_set_holiday))
                }
                OutlinedButton(
                    onClick = {
                        onUpsert(
                            HolidayCalendarEntry(
                                date = targetDate.toString(),
                                kind = HolidayEntryKind.Workday,
                                name = name.trim(),
                            ),
                        )
                        name = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_holiday_set_workday))
                }
                if (userEntry != null) {
                    OutlinedButton(
                        onClick = { onRemove(targetDate.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_holiday_remove_manual))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val userEntries = settings.sortedUserEntries()
                if (userEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_holiday_no_manual_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    userEntries.forEach { entry ->
                        HolidayEntryRow(
                            entry = entry,
                            onRemove = { onRemove(entry.date) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
        dismissButton = if (settings.entries.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear_all)) }
            }
        } else null,
    )

    if (pickDate) {
        SettingsDatePickerDialog(
            initial = targetDate,
            onConfirm = {
                targetDate = it
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

@Composable
internal fun HolidayEntryRow(
    entry: HolidayCalendarEntry,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holidayEntryTitle(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = holidayEntrySubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.settings_delete))
            }
        }
    }
}

@Composable
internal fun holidayCalendarSubtitle(settings: HolidayCalendarSettings): String {
    val manual = settings.entries.size
    val builtIn = if (settings.builtInEnabled) {
        stringResource(R.string.settings_holiday_builtin_on)
    } else {
        stringResource(R.string.settings_holiday_builtin_off)
    }
    return if (manual == 0) {
        builtIn
    } else {
        stringResource(R.string.settings_holiday_subtitle_with_manual, builtIn, manual)
    }
}

@Composable
internal fun builtInHolidayCoverageSubtitle(): String {
    val years = builtInHolidayYears
    return if (years.isEmpty()) {
        stringResource(R.string.settings_holiday_builtin_none)
    } else {
        stringResource(
            R.string.settings_holiday_builtin_years,
            years.joinToString(stringResource(R.string.settings_holiday_year_separator)),
        )
    }
}

@Composable
internal fun holidayEntryTitle(entry: HolidayCalendarEntry): String {
    val date = entry.localDate() ?: return stringResource(R.string.settings_invalid_date)
    return formatLongDate(date)
}

@Composable
internal fun holidayEntrySubtitle(entry: HolidayCalendarEntry): String {
    val kind = when (entry.kind) {
        HolidayEntryKind.Holiday -> stringResource(R.string.settings_holiday_kind_holiday)
        HolidayEntryKind.Workday -> stringResource(R.string.settings_holiday_kind_workday)
    }
    val note = entry.name.trim()
    return if (note.isBlank()) kind else "$kind · $note"
}

@Composable
internal fun holidayDateStatusText(
    date: LocalDate,
    entry: HolidayCalendarEntry?,
    manual: Boolean,
): String {
    val note = entry?.name?.trim().orEmpty()
    val suffix = if (note.isBlank()) {
        ""
    } else {
        stringResource(R.string.settings_holiday_status_note_suffix, note)
    }
    val dateText = formatLongDate(date)
    return when (entry?.kind) {
        HolidayEntryKind.Holiday -> if (manual) {
            stringResource(R.string.settings_holiday_status_holiday_manual, dateText, suffix)
        } else {
            stringResource(R.string.settings_holiday_status_holiday_builtin, dateText, suffix)
        }
        HolidayEntryKind.Workday -> if (manual) {
            stringResource(R.string.settings_holiday_status_workday_manual, dateText, suffix)
        } else {
            stringResource(R.string.settings_holiday_status_workday_builtin, dateText, suffix)
        }
        null -> stringResource(R.string.settings_holiday_status_normal, dateText)
    }
}
