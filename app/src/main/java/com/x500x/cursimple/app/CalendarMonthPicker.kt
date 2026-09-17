package com.x500x.cursimple.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.weekdayNarrowRes
import java.time.LocalDate
import java.time.YearMonth

/**
 * 自己画的月历选择器，替掉 Material3 的 DatePicker。
 *
 * 换掉它只为表头那一行：M3 的星期缩写取自系统的 narrow 名字，中文环境下
 * 七列全是"星"，完全看不出是周几，而 DatePicker 没有任何参数能改这一行。
 * 这里的表头直接用应用自己的单字星期文案，顺带能跟课表一样支持周一/周日起始。
 */
@Composable
internal fun CalendarMonthPicker(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** 一周从周几排起，1 为周一、7 为周日，跟课表的列序保持一致。 */
    weekStartDayOfWeek: Int = 1,
    /** 顶部的小标题与大字日期，传 null 用默认的"选择日期 + 年月日"。 */
    title: (@Composable () -> Unit)? = null,
    headline: (@Composable () -> Unit)? = null,
) {
    // 翻月只影响正在看的页面，选中的日期不跟着动
    var visibleMonth by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val columnDays = remember(weekStartDayOfWeek) {
        val start = weekStartDayOfWeek.coerceIn(1, 7)
        (0 until 7).map { (start - 1 + it) % 7 + 1 }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null || headline != null) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                title?.invoke()
                headline?.invoke()
            }
        } else {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.main_date_picker_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.main_date_picker_headline,
                        selected.year,
                        selected.monthValue,
                        selected.dayOfMonth,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.main_date_picker_month,
                    visibleMonth.year,
                    visibleMonth.monthValue,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.main_date_picker_prev_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.main_date_picker_next_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            columnDays.forEach { dayOfWeek ->
                Text(
                    text = stringResource(weekdayNarrowRes(dayOfWeek)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val cells = remember(visibleMonth, columnDays) { monthGridCells(visibleMonth, columnDays.first()) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            selected = date == selected,
                            onClick = { date?.let(onSelect) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(selected = selected, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * 把一个月摊成整周对齐的格子，月初月末不足一周的位置留空。
 * [weekStartDayOfWeek] 为这一周的第一列是周几（1 为周一）。
 */
internal fun monthGridCells(month: YearMonth, weekStartDayOfWeek: Int): List<LocalDate?> {
    val first = month.atDay(1)
    // 1 号落在这一周的第几列，据此在前面补空格
    val leading = ((first.dayOfWeek.value - weekStartDayOfWeek) + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}
