package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.feature.plugin.ui.AppAssistChip

/**
 * Week picker shared by the course editors: parity chips first, then either a start/end range or the
 * custom week summary, under a one-line summary of what is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseWeeksSection(
    parity: WeekParity,
    onParityChange: (WeekParity) -> Unit,
    startWeekText: String,
    onStartWeekChange: (String) -> Unit,
    endWeekText: String,
    onEndWeekChange: (String) -> Unit,
    customWeeks: List<Int>,
    onEditCustomWeeks: () -> Unit,
    /** The weeks the course would run in right now; null while the selection is incomplete. */
    weeks: List<Int>?,
    maxWeekCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("course-weeks-section"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val summary = if (weeks.isNullOrEmpty()) {
                stringResource(R.string.schedule_weeks_unset)
            } else {
                stringResource(
                    R.string.schedule_weeks_summary,
                    LocalContext.current.weekRangesText(compactWeekRanges(weeks)),
                    weeks.size,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.schedule_weeks_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (weeks.isNullOrEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("course-weeks-summary"),
                    )
                }
                if (parity != WeekParity.Custom) {
                    AppAssistChip(
                        onClick = {
                            onStartWeekChange("1")
                            onEndWeekChange(maxWeekCount.toString())
                        },
                        label = { Text(stringResource(R.string.schedule_weeks_fill_term)) },
                        modifier = Modifier.testTag("course-weeks-fill-term"),
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WeekParity.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = parity == option,
                        onClick = { onParityChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, WeekParity.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("course-weeks-parity-${option.name}"),
                        // No check mark and slim padding: the label needs the room on narrow screens.
                        icon = {},
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (parity == WeekParity.Custom) {
                SelectedWeeksRow(weeks = customWeeks, onEdit = onEditCustomWeeks)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = startWeekText,
                        onValueChange = { onStartWeekChange(it.filter(Char::isDigit).take(2)) },
                        label = { Text(stringResource(R.string.schedule_week_start_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endWeekText,
                        onValueChange = { onEndWeekChange(it.filter(Char::isDigit).take(2)) },
                        label = { Text(stringResource(R.string.schedule_week_end_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
