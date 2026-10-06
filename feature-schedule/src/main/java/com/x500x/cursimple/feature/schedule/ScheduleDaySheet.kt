package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal enum class ScheduleDayChoice { Default, Workday, Holiday }

/**
 * Day settings override unsuitable calendar declarations with normal classes or whole-day
 * cancellation.
 */

/** Mirror every header status with complete text in the expanded day sheet. */
internal enum class ScheduleDayStatus {
    Today,
    Holiday,
    MakeUpWorkday,
    FollowsOtherDay,

    Normal,
}

/** Today is a position marker; also report the day's actual class status. */
internal fun scheduleDayStatuses(
    isToday: Boolean,
    hasHoliday: Boolean,
    makeUpWorkday: Boolean,
    followsOtherDay: Boolean,
): List<ScheduleDayStatus> = buildList {
    if (isToday) add(ScheduleDayStatus.Today)
    if (hasHoliday) add(ScheduleDayStatus.Holiday)
    if (makeUpWorkday) add(ScheduleDayStatus.MakeUpWorkday)
    if (followsOtherDay) add(ScheduleDayStatus.FollowsOtherDay)
    if (none { it != ScheduleDayStatus.Today }) add(ScheduleDayStatus.Normal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDaySheet(
    date: LocalDate,
    weekdayLabel: String,
    effectiveHolidayName: String?,
    makeUpWorkday: Boolean = false,
    sourceDate: LocalDate? = null,
    sourceWeekdayLabel: String? = null,
    isToday: Boolean = false,
    initialChoice: ScheduleDayChoice,
    initialHolidayName: String,
    onDismiss: () -> Unit,
    onApply: (ScheduleDayChoice, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choice by remember(date) { mutableStateOf(initialChoice) }
    var holidayName by remember(date) { mutableStateOf(initialHolidayName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.schedule_day_sheet_title,
                    date.monthValue,
                    date.dayOfMonth,
                    weekdayLabel,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // Expose full holiday and source-day labels that narrow headers truncate.
            val statusLines = scheduleDayStatuses(
                isToday = isToday,
                hasHoliday = effectiveHolidayName != null,
                makeUpWorkday = makeUpWorkday,
                followsOtherDay = sourceDate != null,
            ).map { status ->
                when (status) {
                    ScheduleDayStatus.Today -> stringResource(R.string.schedule_day_sheet_current_today)
                    ScheduleDayStatus.Holiday -> stringResource(
                        R.string.schedule_day_sheet_current_holiday,
                        effectiveHolidayName.orEmpty(),
                    )
                    ScheduleDayStatus.MakeUpWorkday ->
                        stringResource(R.string.schedule_day_sheet_current_makeup)
                    ScheduleDayStatus.FollowsOtherDay -> stringResource(
                        R.string.schedule_day_sheet_current_source,
                        sourceDate?.monthValue ?: 0,
                        sourceDate?.dayOfMonth ?: 0,
                        sourceWeekdayLabel.orEmpty(),
                    )
                    ScheduleDayStatus.Normal ->
                        stringResource(R.string.schedule_day_sheet_current_normal)
                }
            }
            statusLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(2.dp))

            ScheduleDayChoiceRow(
                selected = choice == ScheduleDayChoice.Default,
                title = stringResource(R.string.schedule_day_sheet_default),
                subtitle = stringResource(R.string.schedule_day_sheet_default_desc),
                onClick = { choice = ScheduleDayChoice.Default },
            )
            ScheduleDayChoiceRow(
                selected = choice == ScheduleDayChoice.Workday,
                title = stringResource(R.string.schedule_day_sheet_workday),
                subtitle = stringResource(R.string.schedule_day_sheet_workday_desc),
                onClick = { choice = ScheduleDayChoice.Workday },
            )
            ScheduleDayChoiceRow(
                selected = choice == ScheduleDayChoice.Holiday,
                title = stringResource(R.string.schedule_day_sheet_holiday),
                subtitle = stringResource(R.string.schedule_day_sheet_holiday_desc),
                onClick = { choice = ScheduleDayChoice.Holiday },
            )

            if (choice == ScheduleDayChoice.Holiday) {
                OutlinedTextField(
                    value = holidayName,
                    onValueChange = { holidayName = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.schedule_day_sheet_holiday_name)) },
                    placeholder = { Text(stringResource(R.string.schedule_day_sheet_holiday_name_hint)) },
                )
            }

            Spacer(Modifier.size(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onApply(choice, holidayName.trim()) }) {
                    Text(stringResource(R.string.schedule_action_save))
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ScheduleDayChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
