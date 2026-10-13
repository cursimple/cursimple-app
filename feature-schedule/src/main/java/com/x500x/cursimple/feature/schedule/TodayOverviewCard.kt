package com.x500x.cursimple.feature.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.LocalTime

internal enum class TodayItemStatus { Done, Current, Pending, Unknown }

internal fun TodayOverviewState.statusOf(item: TodayOverviewItem): TodayItemStatus = when {
    !item.hasTime -> TodayItemStatus.Unknown
    now.isBefore(item.start) -> TodayItemStatus.Pending
    now.isBefore(item.end) -> TodayItemStatus.Current
    else -> TodayItemStatus.Done
}

internal val TodayOverviewState.doneCount: Int get() = courses.count { statusOf(it) == TodayItemStatus.Done }

@Composable
internal fun countdownText(countdown: TodayCountdown): String {
    val seconds = countdown.secondsLeft
    val minutes = (seconds + 59L) / 60L
    return when {
        seconds < 3_600L -> stringResource(
            if (countdown.ending) R.string.schedule_today_seconds_to_end else R.string.schedule_today_seconds_to_start,
            (seconds / 60L).toInt(),
            (seconds % 60L).toInt(),
        )
        countdown.ending -> stringResource(R.string.schedule_today_minutes_to_end, minutes)
        else -> stringResource(R.string.schedule_today_hours_to_start, (minutes / 60L).toInt(), (minutes % 60L).toInt())
    }
}

/** Second-level clock scoped to the countdown chip; the rest of the overview stays minute-level. */
@Composable
private fun rememberSecondClock(): LocalTime {
    val zone = LocalAppZone.current
    val now by remember(zone) {
        flow {
            while (true) {
                val current = BeijingTime.nowTimeIn(zone)
                emit(current)
                delay(1_000L - current.nano / 1_000_000L)
            }
        }
    }.collectAsStateWithLifecycle(initialValue = BeijingTime.nowTimeIn(zone))
    return now
}

@Composable
private fun TodayCountdownChip(state: TodayOverviewState) {
    val countdown = state.countdownAt(rememberSecondClock()) ?: return
    TodayChip(countdownText(countdown), MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.primary)
}

@Composable
internal fun todayStatusTitle(state: TodayOverviewState): String = stringResource(
    when {
        state.current != null -> R.string.schedule_today_current
        state.next != null -> R.string.schedule_today_next
        state.unknownTimeCount > 0 -> R.string.schedule_today_need_timing
        state.holiday && state.total == 0 -> R.string.schedule_today_rest
        state.total == 0 -> R.string.schedule_today_empty
        else -> R.string.schedule_today_finished
    },
)

/** Today's day-view overview; null [onClick] makes it a noninteractive sheet header. */
@Composable
internal fun TodayOverviewCard(
    state: TodayOverviewState,
    display: ScheduleDisplayPreferences,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val active = state.current != null
    val container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val muted = content.copy(alpha = 0.72f)
    val focus = state.current ?: state.next
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TodayProgressRing(done = state.doneCount, total = state.total, color = MaterialTheme.colorScheme.primary, track = content.copy(alpha = 0.12f), textColor = content)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(todayStatusTitle(state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    val mood = if (focus == null) todayMoodLine(state) else null
                    Text(
                        text = focus?.course?.title ?: mood ?: stringResource(R.string.schedule_today_count, state.total, state.remaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                        maxLines = if (mood != null) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (focus != null) {
                        Text(occurrenceInfo(focus, display), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
                }
            }
            if (state.current != null) {
                val progress by animateFloatAsState(state.currentProgress, tween(600), label = "today-progress")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = content.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            val hasCountdown = state.countdownAt(state.now) != null
            val nextBrief = if (state.current != null) state.next?.let { stringResource(R.string.schedule_today_next_brief, it.course.title, clockText(it.start)) } else null
            if (hasCountdown || state.conflicts.isNotEmpty() || nextBrief != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasCountdown) TodayCountdownChip(state)
                    if (state.conflicts.isNotEmpty()) {
                        TodayChip(stringResource(R.string.schedule_today_conflict_short, state.conflicts.size), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                    nextBrief?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
    val cardModifier = modifier.fillMaxWidth().testTag("today-overview-card")
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = container, modifier = cardModifier) { body() }
    } else {
        Surface(shape = RoundedCornerShape(24.dp), color = container, modifier = cardModifier) { body() }
    }
}

@Composable
internal fun TodayChip(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        // Tabular digits keep the ticking countdown from shifting width.
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = content, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun TodayProgressRing(done: Int, total: Int, color: Color, track: Color, textColor: Color) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val progress by animateFloatAsState(target, tween(700), label = "today-ring")
    val description = stringResource(R.string.schedule_today_progress_desc, done, total)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).semantics { contentDescription = description }) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(56.dp),
            color = color,
            trackColor = track,
            strokeWidth = 5.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
            Text("/$total", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
