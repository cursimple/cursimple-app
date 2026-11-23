package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.x500x.cursimple.feature.plugin.ui.AppModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import com.x500x.cursimple.feature.plugin.ui.AppRadioRow
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

    AppModalBottomSheet(
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

            AppRadioRow(
                selected = choice == ScheduleDayChoice.Default,
                title = stringResource(R.string.schedule_day_sheet_default),
                subtitle = stringResource(R.string.schedule_day_sheet_default_desc),
                onClick = { choice = ScheduleDayChoice.Default },
            )
            AppRadioRow(
                selected = choice == ScheduleDayChoice.Workday,
                title = stringResource(R.string.schedule_day_sheet_workday),
                subtitle = stringResource(R.string.schedule_day_sheet_workday_desc),
                onClick = { choice = ScheduleDayChoice.Workday },
            )
            AppRadioRow(
                selected = choice == ScheduleDayChoice.Holiday,
                title = stringResource(R.string.schedule_day_sheet_holiday),
                subtitle = stringResource(R.string.schedule_day_sheet_holiday_desc),
                onClick = { choice = ScheduleDayChoice.Holiday },
            )

            if (choice == ScheduleDayChoice.Holiday) {
                AppOutlinedTextField(
                    value = holidayName,
                    onValueChange = { holidayName = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    label = { Text(stringResource(R.string.schedule_day_sheet_holiday_name)) },
                    placeholder = { Text(stringResource(R.string.schedule_day_sheet_holiday_name_hint)) },
                )
            }

            Spacer(Modifier.size(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                Spacer(Modifier.size(8.dp))
                AppDialogPrimaryButton(onClick = { onApply(choice, holidayName.trim()) }) {
                    Text(stringResource(R.string.schedule_action_save))
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
