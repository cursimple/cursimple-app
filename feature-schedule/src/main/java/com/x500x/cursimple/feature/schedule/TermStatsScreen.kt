package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.time.DayOfWeek

/** The term at a glance, with course load peaks and actionable detail. */
@Composable
fun TermStatsContent(stats: TermStats?, modifier: Modifier = Modifier) {
    if (stats == null) {
        Text(
            text = stringResource(R.string.stats_no_term),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp).testTag("term-stats-empty"),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("term-stats"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(stringResource(R.string.stats_total_sessions), stats.totalSessions.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_done_sessions), stats.doneSessions.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_left_sessions), (stats.totalSessions - stats.doneSessions).toString(), Modifier.weight(1f))
        }

        StatsHighlights(stats)

        SectionTitle(stringResource(R.string.stats_per_week))
        WeekBars(stats.perWeek, stats.weekCourses)

        SectionTitle(stringResource(R.string.stats_by_course))
        if (stats.courses.isEmpty()) {
            Text(stringResource(R.string.stats_no_courses), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val biggest = stats.courses.maxOf { it.total }.coerceAtLeast(1)
            stats.courses.forEach { course ->
                CourseShareRow(course, biggest)
            }
        }

        SectionTitle(stringResource(R.string.stats_attendance))
        val rate = stats.attendanceRate
        Text(
            text = if (rate == null) {
                stringResource(R.string.stats_attendance_none)
            } else {
                stringResource(R.string.stats_attendance_rate, (rate * 100).roundToInt())
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("term-stats-rate"),
        )
        Text(
            text = stringResource(R.string.stats_attendance_counts, stats.attended, stats.missed, stats.leave),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsHighlights(stats: TermStats) {
    val peakWeek = stats.peakWeekIndex
    val peakDay = stats.peakDay
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HighlightTile(
                icon = Icons.Rounded.AccessTime,
                label = stringResource(R.string.stats_early_morning),
                value = stringResource(R.string.stats_count_sessions, stats.earlyMorningSessions),
                modifier = Modifier.weight(1f),
                onClick = { selected = if (selected == "early") null else "early" },
            )
            HighlightTile(
                icon = Icons.Rounded.Event,
                label = stringResource(R.string.stats_evening),
                value = stringResource(R.string.stats_count_sessions, stats.eveningSessions),
                modifier = Modifier.weight(1f),
                onClick = { selected = if (selected == "evening") null else "evening" },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HighlightTile(
                icon = Icons.Rounded.BarChart,
                label = stringResource(R.string.stats_peak_week),
                value = peakWeek?.let { stringResource(R.string.stats_week_value, it + 1, stats.perWeek[it]) }
                    ?: stringResource(R.string.stats_none),
                modifier = Modifier.weight(1f),
                onClick = { selected = if (selected == "week") null else "week" },
            )
            HighlightTile(
                icon = Icons.Rounded.CalendarMonth,
                label = stringResource(R.string.stats_peak_day),
                value = peakDay?.let { stringResource(weekdayStringRes(it), stats.perDay[it.value - 1]) }
                    ?: stringResource(R.string.stats_none),
                modifier = Modifier.weight(1f),
                onClick = { selected = if (selected == "day") null else "day" },
            )
        }
        selected?.let { kind ->
            val titles = when (kind) {
                "week" -> peakWeek?.let { stats.weekCourses[it] }.orEmpty()
                "day" -> peakDay?.let { stats.dayCourses[it.value - 1] }.orEmpty()
                "early" -> stats.courses.map { it.title }
                else -> stats.courses.map { it.title }
            }
            val title = when (kind) {
                "week" -> stringResource(R.string.stats_peak_week_detail)
                "day" -> stringResource(R.string.stats_peak_day_detail)
                "early" -> stringResource(R.string.stats_early_morning_detail)
                else -> stringResource(R.string.stats_evening_detail)
            }
            HighlightDetail(title, titles)
        }
    }
}

@Composable
private fun HighlightTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HighlightDetail(title: String, titles: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("term-stats-highlight-detail"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            if (titles.isEmpty()) {
                Text(stringResource(R.string.stats_no_courses), style = MaterialTheme.typography.bodySmall)
            } else {
                titles.forEach { titleText ->
                    Text(titleText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun weekdayStringRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.stats_monday
    DayOfWeek.TUESDAY -> R.string.stats_tuesday
    DayOfWeek.WEDNESDAY -> R.string.stats_wednesday
    DayOfWeek.THURSDAY -> R.string.stats_thursday
    DayOfWeek.FRIDAY -> R.string.stats_friday
    DayOfWeek.SATURDAY -> R.string.stats_saturday
    DayOfWeek.SUNDAY -> R.string.stats_sunday
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

/**
 * One bar per teaching week, scaled to the busiest week. Tapping or long-pressing a bar selects it and
 * shows its exact count below; tapping the selected bar again clears the selection.
 */
@Composable
private fun WeekBars(perWeek: List<Int>, perWeekCourses: List<List<String>>) {
    val peak = perWeek.maxOrNull()?.coerceAtLeast(1) ?: 1
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().height(116.dp).testTag("term-stats-weeks"),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        perWeek.forEachIndexed { index, count ->
            val isSelected = selected == index
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                // The exact count sits directly above its bar; empty weeks show nothing.
                Text(
                    text = if (count > 0) count.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("term-stats-week-count-$index"),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((84 * count / peak).coerceAtLeast(if (count > 0) 4 else 0).dp)
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.tertiary
                                count > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(4.dp),
                        )
                        .testTag("term-stats-week-$index")
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { selected = if (selected == index) null else index },
                                onLongPress = { selected = index },
                            )
                        },
                )
            }
        }
    }
    selected?.let { index ->
        Text(
            text = stringResource(R.string.stats_week_detail, index + 1, perWeek[index]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().testTag("term-stats-week-detail"),
        )
        if (perWeek[index] > 0) {
            Text(
                text = perWeekCourses[index].joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag("term-stats-week-courses"),
            )
        }
    }
}

@Composable
private fun CourseShareRow(course: CourseTermStat, biggest: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(course.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.stats_course_count, course.done, course.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(course.total.toFloat() / biggest)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}

