package com.x500x.cursimple.feature.schedule

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
import androidx.compose.material3.TextButton
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

/** 用户给某一天定下的状态。[Default] 表示不插手，跟着节假日日历走。 */
internal enum class ScheduleDayChoice { Default, Workday, Holiday }

/**
 * 双击日期栏打开的「这一天」设置。
 *
 * 内置或同步来的放假安排未必合用——调休、院系单独放假、临时停课都对不上，
 * 这里让用户就地推翻当天的判定：照常上课，或者整天停课。
 */

/**
 * 面板里要列出的状态，与表头上那行小字一一对应。
 *
 * 表头一列就那么宽，节日名、「按10/10」这些多半显示不全，双击进来必须看得到完整的。
 * 所以这张表是「表头能出现什么」的镜像，少一项就意味着有信息只在表头里被截断着。
 */
internal enum class ScheduleDayStatus {
    Today,
    Holiday,
    MakeUpWorkday,
    FollowsOtherDay,

    /** 什么特殊情况都没有。 */
    Normal,
}

/**
 * 按当前情况排出要列的状态。
 *
 * 「今天」只是个位置标记、不是课表状态，所以它单独存在时仍要补上「照常上课」，
 * 否则双击今天会只看到「当前：今天」，看不出这天到底上不上课。
 */
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
    /** 当前生效的节日名，没有节日时为 null。 */
    effectiveHolidayName: String?,
    /** 这天是调休补班日（本该休息却要上课）。 */
    makeUpWorkday: Boolean = false,
    /** 临时调课时这天实际按哪一天的课上；与本日相同或没有调课时为 null。 */
    sourceDate: LocalDate? = null,
    /** [sourceDate] 那天的星期名。 */
    sourceWeekdayLabel: String? = null,
    /** 这天就是今天（表头上是那个高亮胶囊）。 */
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
            // 表头那一列太窄，节日名和「按哪天上课」多半显示不全。
            // 表头上能出现的每一项，这里都要能看到完整的——这正是用户双击进来的目的
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
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
