package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The term at a glance: totals, the week-by-week load, each course's share and attendance. */
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

        SectionTitle(stringResource(R.string.stats_per_week))
        WeekBars(stats.perWeek)

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

/** One bar per teaching week, scaled to the busiest week. */
@Composable
private fun WeekBars(perWeek: List<Int>) {
    val peak = perWeek.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp).testTag("term-stats-weeks"),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        perWeek.forEach { count ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height((88 * count / peak).coerceAtLeast(if (count > 0) 4 else 0).dp)
                    .background(
                        if (count > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ),
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

