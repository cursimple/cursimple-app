package com.kebiao.viewer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule

private const val DefaultWeekPickerTotalWeeks = 25

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPickerSheet(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int = DefaultWeekPickerTotalWeeks,
    onSelectWeek: (Int) -> Unit,
    onSetSelectedAsCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "查看周课表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSetSelectedAsCurrent) {
                    Text("修改开学日期")
                }
            }

            val rows = (1..totalWeeks).chunked(5)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowWeeks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowWeeks.forEach { week ->
                            WeekCell(
                                week = week,
                                isCurrent = week == currentWeek,
                                isSelected = week == selectedWeek,
                                onClick = { onSelectWeek(week) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(5 - rowWeeks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

internal fun resolveWeekPickerTotalWeeks(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    currentWeek: Int,
    selectedWeek: Int,
    fallbackWeeks: Int = DefaultWeekPickerTotalWeeks,
): Int {
    val explicitMaxWeek = (schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses)
        .flatMap { it.weeks }
        .maxOrNull()
    val baseWeeks = explicitMaxWeek ?: fallbackWeeks
    return maxOf(1, baseWeeks, currentWeek, selectedWeek)
}

@Composable
private fun WeekCell(
    week: Int,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimary
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isCurrent) "本周" else week.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = onContainer,
            )
        }
    }
}
