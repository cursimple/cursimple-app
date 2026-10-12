package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.x500x.cursimple.core.kernel.model.weekdayNarrowRes
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private const val MONTH_PAGE_ORIGIN = Int.MAX_VALUE / 2

internal fun calendarMonthAtPage(origin: YearMonth, page: Int): YearMonth =
    origin.plusMonths(page.toLong() - MONTH_PAGE_ORIGIN)

internal fun calendarPageOfMonth(origin: YearMonth, month: YearMonth): Int =
    (MONTH_PAGE_ORIGIN.toLong() + java.time.temporal.ChronoUnit.MONTHS.between(origin, month))
        .coerceIn(0, Int.MAX_VALUE.toLong() - 1).toInt()

/** Custom calendar labels support localized weekdays and Monday- or Sunday-first display. */
@Composable
fun CalendarMonthPicker(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    weekStartDayOfWeek: Int = 1,
    title: (@Composable () -> Unit)? = null,
    headline: (@Composable () -> Unit)? = null,
) {
    // Paging months does not change the selected date.
    val originMonth = rememberSaveable { YearMonth.from(selected).toString() }
    val origin = remember(originMonth) { YearMonth.parse(originMonth) }
    val pager = rememberPagerState(initialPage = MONTH_PAGE_ORIGIN, pageCount = { Int.MAX_VALUE })
    val visibleMonth = calendarMonthAtPage(origin, pager.currentPage)
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) {
        val page = calendarPageOfMonth(origin, YearMonth.from(selected))
        if (pager.currentPage != page) pager.scrollToPage(page)
    }
    fun moveMonth(delta: Int) {
        val page = (pager.currentPage.toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong() - 1).toInt()
        scope.launch { pager.animateScrollToPage(page) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val columnDays = remember(weekStartDayOfWeek) {
        val start = weekStartDayOfWeek.coerceIn(1, 7)
        (0 until 7).map { (start - 1 + it) % 7 + 1 }
    }

    Column(modifier = modifier.fillMaxWidth().testTag("calendar-month-picker")) {
        Column(Modifier.fillMaxWidth()
        .pointerInput(selected, rtl) {
            var distance = 0f
            val threshold = 48.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                onDragCancel = { distance = 0f },
                onDragEnd = {
                    val delta = calendarMonthSwipeDelta(distance, threshold, rtl)
                    if (delta != 0) moveMonth(delta)
                    distance = 0f
                },
            )
        }) {
        if (title != null || headline != null) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                title?.invoke()
                headline?.invoke()
            }
        } else {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.schedule_date_picker_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.schedule_date_picker_headline,
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
                    R.string.schedule_date_picker_month,
                    visibleMonth.year,
                    visibleMonth.monthValue,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("calendar-visible-month"),
            )
            IconButton(onClick = { moveMonth(-1) }, modifier = Modifier.testTag("calendar-prev-month")) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.schedule_date_picker_prev_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { moveMonth(1) }, modifier = Modifier.testTag("calendar-next-month")) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.schedule_date_picker_next_month),
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
        }

        // A native pager owns drags starting on day cells, preventing a swipe from becoming a tap.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().clipToBounds().testTag("calendar-month-grid"),
            verticalAlignment = Alignment.Top,
            key = { it },
        ) { page ->
            val month = calendarMonthAtPage(origin, page)
            val cells = remember(month, columnDays) {
                val days = monthGridCells(month, columnDays.first())
                days + List(42 - days.size) { null }
            }
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
                                enabled = !pager.isScrollInProgress,
                                onClick = { date?.let(onSelect) },
                                modifier = Modifier.weight(1f),
                            )
                        }
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
    enabled: Boolean,
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
                .testTag("calendar-day-$date")
                .selectable(selected = selected, enabled = enabled, onClick = onClick),
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

/** A completed drag pages once; short/cancelled drags keep the date and month intact. */
internal fun calendarMonthSwipeDelta(distance: Float, threshold: Float, rtl: Boolean = false): Int {
    if (!distance.isFinite() || !threshold.isFinite() || threshold <= 0f || kotlin.math.abs(distance) < threshold) return 0
    val delta = if (distance < 0f) 1 else -1
    return if (rtl) -delta else delta
}

/** Pad a month into week-aligned cells using [weekStartDayOfWeek]. */
fun monthGridCells(month: YearMonth, weekStartDayOfWeek: Int): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = ((first.dayOfWeek.value - weekStartDayOfWeek) + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}
