package com.x500x.cursimple.app

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.isCurrentTermWeek
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val DefaultWeekPickerTotalWeeks = 25

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WeekPickerSheet(
    termStart: LocalDate?,
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int = DefaultWeekPickerTotalWeeks,
    /** 课程推出来的周数；超出这条线的都是用户自己加的空白周，可以删。 */
    derivedWeeks: Int = totalWeeks,
    onSelectWeek: (Int) -> Unit,
    onSetSelectedAsCurrent: (Int) -> Unit,
    onAddWeek: () -> Unit = {},
    onDeleteWeek: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var pendingDeleteWeek by remember { mutableStateOf<Int?>(null) }
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
                    text = stringResource(R.string.week_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSetSelectedAsCurrent(selectedWeek) },
                    enabled = selectedWeek >= 1,
                ) {
                    Text(stringResource(R.string.week_picker_set_current))
                }
            }

            if (termStart == null) {
                Text(
                    text = stringResource(R.string.week_picker_no_term_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (currentWeek < 1) {
                Text(
                    text = pluralStringResource(R.plurals.week_picker_before_term, 1 - currentWeek, 1 - currentWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 末尾多摆一格加号，和周次格同样大小，翻到底就能接着加
            val cells: List<Int?> = (1..totalWeeks).toList<Int?>() + listOf<Int?>(null)
            val rows = cells.chunked(5)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowCells ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowCells.forEach { week ->
                            if (week == null) {
                                AddWeekCell(onClick = onAddWeek, modifier = Modifier.weight(1f))
                            } else {
                                WeekCell(
                                    week = week,
                                    isCurrent = isCurrentTermWeek(termStart, week, currentWeek),
                                    isSelected = week == selectedWeek,
                                    onClick = { onSelectWeek(week) },
                                    // 只有自己加出来的空白周能删；课程推出来的那些删了也会被算回来
                                    onLongClick = if (week > derivedWeeks) {
                                        { pendingDeleteWeek = week }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(5 - rowCells.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    pendingDeleteWeek?.let { week ->
        DeleteWeekConfirmDialog(
            week = week,
            onConfirm = {
                pendingDeleteWeek = null
                onDeleteWeek(week)
            },
            onDismiss = { pendingDeleteWeek = null },
        )
    }
}

/**
 * 按开学日期所在周的周一起算 [date] 落在第几周。
 * 开学前返回 0 或负数；未设置开学日期时回退到第 1 周。
 */
internal fun resolveWeekIndexForDate(termStart: LocalDate?, date: LocalDate): Int {
    if (termStart == null) return 1
    return resolveTermWeekNumber(termStart, date)
}

/**
 * 课程本身推出来的周数，不含用户自己加的空白周。
 *
 * 这条线同时也是「哪些周能删」的分界：它以内的周由课程决定，删不掉；
 * 超出去的都是用户自己加的空白周，可以一周周撤掉。
 */
internal fun derivedWeekCount(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    currentWeek: Int,
    selectedWeek: Int,
    fallbackWeeks: Int = DefaultWeekPickerTotalWeeks,
): Int {
    val explicitMaxWeek = schedule.allCoursesWith(manualCourses)
        .flatMap { it.weeks }
        .maxOrNull()
    val baseWeeks = explicitMaxWeek ?: fallbackWeeks
    return maxOf(1, baseWeeks, currentWeek, selectedWeek)
}

internal fun resolveWeekPickerTotalWeeks(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    currentWeek: Int,
    selectedWeek: Int,
    extraWeekCount: Int = 0,
    fallbackWeeks: Int = DefaultWeekPickerTotalWeeks,
): Int = derivedWeekCount(
    schedule = schedule,
    manualCourses = manualCourses,
    currentWeek = currentWeek,
    selectedWeek = selectedWeek,
    fallbackWeeks = fallbackWeeks,
) + extraWeekCount.coerceAtLeast(0)

internal fun deriveTermStartForCurrentWeek(today: LocalDate, currentWeek: Int): LocalDate {
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return currentMonday.minusWeeks((currentWeek.coerceAtLeast(1) - 1).toLong())
}


/** 周次网格末尾那一格加号，点了就接上一个空白周。 */
@Composable
private fun AddWeekCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.week_picker_add_week),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 删除某一周的确认框。
 *
 * 删周是抹掉一整周，点错的代价不小，所以不给「一点就删」的按钮：
 * 必须把「确认」两个字打出来，删除按钮才会亮。
 */
@Composable
private fun DeleteWeekConfirmDialog(
    week: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val keyword = stringResource(R.string.week_picker_delete_keyword)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.week_picker_delete_title, week)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.week_picker_delete_body, keyword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text(keyword) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.trim() == keyword,
                onClick = onConfirm,
            ) {
                Text(
                    text = stringResource(R.string.week_picker_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.week_picker_delete_cancel)) }
        },
    )
}

@Composable
private fun WeekCell(
    week: Int,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isCurrent) stringResource(R.string.week_picker_current_week) else week.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = onContainer,
            )
        }
    }
}
