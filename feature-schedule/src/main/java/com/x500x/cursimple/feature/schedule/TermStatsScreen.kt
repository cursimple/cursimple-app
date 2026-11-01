package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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

/**
 * One bar per teaching week, scaled to the busiest week. Tapping or long-pressing a bar selects it and
 * shows its exact count below; tapping the selected bar again clears the selection.
 */
@Composable
private fun WeekBars(perWeek: List<Int>) {
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

