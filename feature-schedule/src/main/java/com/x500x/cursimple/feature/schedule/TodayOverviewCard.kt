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
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences

/** 一节课在「今天」这条时间线上的位置 */
internal enum class TodayItemStatus { Done, Current, Pending, Unknown }

internal fun TodayOverviewState.statusOf(item: TodayOverviewItem): TodayItemStatus = when {
    !item.hasTime -> TodayItemStatus.Unknown
    now.isBefore(item.start) -> TodayItemStatus.Pending
    now.isBefore(item.end) -> TodayItemStatus.Current
    else -> TodayItemStatus.Done
}

internal val TodayOverviewState.doneCount: Int get() = courses.count { statusOf(it) == TodayItemStatus.Done }

/** 倒计时文案：一小时以内按分钟，再长就拆成小时和分钟 */
@Composable
internal fun countdownText(state: TodayOverviewState): String? = when {
    state.current != null -> stringResource(R.string.schedule_today_minutes_to_end, state.currentMinutesLeft ?: 0L)
    state.minutesUntilNext != null -> {
        val minutes = state.minutesUntilNext
        if (minutes >= 60) stringResource(R.string.schedule_today_hours_to_start, (minutes / 60).toInt(), (minutes % 60).toInt())
        else stringResource(R.string.schedule_today_minutes_to_start, minutes)
    }
    else -> null
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

/**
 * 日视图看今天时，课程上方的那张卡片：左边今日进度环，右边正在上 / 下一节，
 * 底下是本节进度或上课倒计时。[onClick] 为空时作为面板里的头图，不带箭头。
 */
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
                        // 课名一行就够；那句闲话可能稍长，给它两行别截断
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
            val countdown = countdownText(state)
            val nextBrief = if (state.current != null) state.next?.let { stringResource(R.string.schedule_today_next_brief, it.course.title, clockText(it.start)) } else null
            if (countdown != null || state.conflicts.isNotEmpty() || nextBrief != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    countdown?.let { TodayChip(it, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.primary) }
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
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** 今日进度环：已上完几门 / 今天共几门 */
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
