package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val DefaultWeekPickerTotalWeeks = 20

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WeekPickerSheet(
    termStart: LocalDate?,
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int = DefaultWeekPickerTotalWeeks,
    derivedWeeks: Int = totalWeeks,
    onSelectWeek: (Int) -> Unit,
    onSetSelectedAsCurrent: (Int) -> Unit,
    onAddWeek: () -> Unit = {},
    onDeleteWeek: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val haptics = rememberAppHaptics()
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
                AppOutlinedButton(
                    onClick = {
                        haptics.click()
                        onSetSelectedAsCurrent(selectedWeek)
                    },
                    enabled = selectedWeek >= 1,
                    shape = RoundedCornerShape(50),
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
                                AddWeekCell(
                                    onClick = {
                                        haptics.click()
                                        onAddWeek()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                WeekCell(
                                    week = week,
                                    isCurrent = isCurrentTermWeek(termStart, week, currentWeek),
                                    isSelected = week == selectedWeek,
                                    onClick = {
                                        haptics.tick()
                                        onSelectWeek(week)
                                    },
                                    // Only user-added blank weeks are removable.
                                    onDelete = if (week > derivedWeeks) {
                                        {
                                            haptics.heavy()
                                            pendingDeleteWeek = week
                                        }
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
 * Count from the Monday of the term's first week; pre-term weeks are nonpositive, and unset
 * terms use week one.
 */
internal fun resolveWeekIndexForDate(termStart: LocalDate?, date: LocalDate): Int {
    if (termStart == null) return 1
    return resolveTermWeekNumber(termStart, date)
}

/** Course-derived weeks define the nonremovable range; extra blank weeks are user-managed. */
internal fun derivedWeekCount(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    currentWeek: Int,
    fallbackWeeks: Int = DefaultWeekPickerTotalWeeks,
): Int {
    val explicitMaxWeek = schedule.allCoursesWith(manualCourses)
        .flatMap { it.weeks }
        .maxOrNull()
    val baseWeeks = explicitMaxWeek ?: fallbackWeeks
    return maxOf(1, baseWeeks, currentWeek)
}

/** Derive totals only from courses and explicit extra weeks, never the viewed week. */
internal fun resolveWeekPickerTotalWeeks(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    currentWeek: Int,
    extraWeekCount: Int = 0,
    fallbackWeeks: Int = DefaultWeekPickerTotalWeeks,
): Int = derivedWeekCount(
    schedule = schedule,
    manualCourses = manualCourses,
    currentWeek = currentWeek,
    fallbackWeeks = fallbackWeeks,
) + extraWeekCount.coerceAtLeast(0)

internal fun deriveTermStartForCurrentWeek(today: LocalDate, currentWeek: Int): LocalDate {
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return currentMonday.minusWeeks((currentWeek.coerceAtLeast(1) - 1).toLong())
}

@Composable
private fun AddWeekCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.week_picker_add_week),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DeleteWeekConfirmDialog(
    week: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.week_picker_delete_title, week)) },
        text = { Text(stringResource(R.string.week_picker_delete_body)) },
        confirmButton = {
            AppOutlinedButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.week_picker_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.week_picker_delete_cancel)) }
        },
    )
}

/**
 * Non-null [onDelete] exposes removal for user-added weeks; both entry points require
 * confirmation.
 */
@Composable
private fun WeekCell(
    week: Int,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val targetContainer = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val targetOnContainer = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimary
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "WeekCellBg",
    )
    val onContainer by animateColorAsState(
        targetValue = targetOnContainer,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "WeekCellContent",
    )
    val targetBorderColor = when {
        isSelected && !isCurrent -> MaterialTheme.colorScheme.primary
        isSelected && isCurrent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "WeekCellBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "WeekCellBorderWidth",
    )
    val border = BorderStroke(borderWidth, borderColor)
    Box(modifier = modifier.aspectRatio(1f)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = container,
            border = border,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onDelete),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = week.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onContainer,
                        )
                        Text(
                            text = stringResource(R.string.week_picker_current_week),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = onContainer.copy(alpha = 0.85f),
                        )
                    }
                } else {
                    Text(
                        text = week.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = onContainer,
                    )
                }
            }
        }
        onDelete?.let { delete ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = delete),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.week_picker_delete_week, week),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
