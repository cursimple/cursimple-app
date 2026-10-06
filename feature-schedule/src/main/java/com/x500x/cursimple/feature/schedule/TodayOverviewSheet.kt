package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayOverviewSheet(
    state: TodayOverviewState,
    display: ScheduleDisplayPreferences,
    onDismiss: () -> Unit,
    onOpenCourse: (CourseItem) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("today-overview-sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 620.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            R.string.schedule_today_date_header,
                            state.date.monthValue,
                            state.date.dayOfMonth,
                            state.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.schedule_today_count, state.total, state.remaining),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { TodayOverviewCard(state = state, display = display, onClick = null) }
            if (state.conflicts.isNotEmpty()) item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.schedule_conflict_count, state.conflicts.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        state.conflicts.forEach { conflict ->
                            Text(
                                stringResource(R.string.schedule_conflict_pair_title, conflict.first.course.title, conflict.second.course.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            if (!state.weekKnown && state.total > 0) item {
                Text(stringResource(R.string.schedule_today_week_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.courses.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.schedule_today_details),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    )
                }
                itemsIndexed(state.courses, key = { _, it -> "${it.course.id}:${it.course.time.startNode}:${it.course.time.endNode}" }) { index, occurrence ->
                    TimelineRow(
                        occurrence = occurrence,
                        status = state.statusOf(occurrence),
                        progress = if (occurrence == state.current) state.currentProgress else null,
                        first = index == 0,
                        last = index == state.courses.lastIndex,
                        display = display,
                        onClick = { onOpenCourse(occurrence.course) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.schedule_today_open_course_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                item {
                    Text(
                        todayStatusTitle(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    occurrence: TodayOverviewItem,
    status: TodayItemStatus,
    progress: Float?,
    first: Boolean,
    last: Boolean,
    display: ScheduleDisplayPreferences,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dotColor = when (status) {
        TodayItemStatus.Done -> colors.outline
        TodayItemStatus.Current -> colors.primary
        TodayItemStatus.Pending -> colors.primary
        TodayItemStatus.Unknown -> colors.tertiary
    }
    val lineColor = colors.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (status == TodayItemStatus.Done) 0.62f else 1f)) {
        Column(Modifier.width(48.dp).padding(top = 12.dp), horizontalAlignment = Alignment.End) {
            Text(clockText(occurrence.start).ifBlank { "--:--" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(clockText(occurrence.end), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val x = size.width / 2
                val dotY = 21.dp.toPx()
                val stroke = 2.dp.toPx()
                if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, dotY), stroke)
                if (!last) drawLine(lineColor, Offset(x, dotY), Offset(x, size.height + 12.dp.toPx()), stroke)
                val radius = if (status == TodayItemStatus.Current) 7.dp.toPx() else 5.dp.toPx()
                if (status == TodayItemStatus.Pending) {
                    drawCircle(colors.surface, radius, Offset(x, dotY))
                    drawCircle(dotColor, radius, Offset(x, dotY), style = Stroke(stroke))
                } else {
                    if (status == TodayItemStatus.Current) drawCircle(dotColor.copy(alpha = 0.22f), radius + 5.dp.toPx(), Offset(x, dotY))
                    drawCircle(dotColor, radius, Offset(x, dotY))
                }
            }
        }
        val current = status == TodayItemStatus.Current
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = if (current) colors.primaryContainer else colors.surfaceVariant,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when (status) {
                            TodayItemStatus.Done -> R.string.schedule_today_done
                            TodayItemStatus.Current -> R.string.schedule_today_current
                            TodayItemStatus.Pending -> R.string.schedule_today_pending
                            TodayItemStatus.Unknown -> R.string.schedule_today_need_timing
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status == TodayItemStatus.Done) colors.onSurfaceVariant else dotColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    occurrence.course.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (current) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    occurrenceInfo(occurrence, display),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                        color = colors.primary,
                        trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
