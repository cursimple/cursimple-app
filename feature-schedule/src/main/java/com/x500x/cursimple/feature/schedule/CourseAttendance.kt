package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.attendance.AttendanceData
import com.x500x.cursimple.core.data.attendance.AttendanceStatus
import com.x500x.cursimple.core.data.attendance.AttendanceSummary
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.feature.plugin.ui.AppAssistChip
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import java.time.LocalDate

/** `(course notebook key, ISO date)` to the mark made for that session; provided once by the schedule screen. */
internal val LocalAttendanceMarks =
    androidx.compose.runtime.compositionLocalOf<Map<Pair<String, String>, AttendanceStatus>> { emptyMap() }

/**
 * Attendance of one course: mark the session being viewed as attended, missed or on leave, see the totals and an
 * optional limit of absences ("you can still miss 1"). Marks are by course name, so every slot of a course counts together.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseAttendanceSection(
    course: CourseItem,
    sessionDate: LocalDate?,
    today: LocalDate,
    attendance: AttendanceData,
    onMark: (courseKey: String, date: String, status: AttendanceStatus?) -> Unit,
    onSetAllowance: (courseKey: String, allowance: Int?) -> Unit,
    onLimitReached: () -> Unit = {},
) {
    val key = memoCourseKey(course.title)
    val summary = attendance.summaryOf(key)
    val date = sessionDate?.toString()
    val current = date?.let { attendance.statusOf(key, it) }
    val markable = sessionDate != null && !sessionDate.isAfter(today)
    // Collapsed by default so the detail page is not crowded; the summary stays visible on the header.
    var expanded by rememberSaveable(course.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("course-attendance-section"),
    ) {
        Column(modifier = Modifier.padding(bottom = if (expanded) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("course-attendance-toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                // The title keeps its natural width; the summary takes the rest, so the title never wraps per character.
                Text(
                    text = stringResource(R.string.attendance_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
                val remaining = summary.remaining
                Text(
                    text = when {
                        expanded && sessionDate != null -> "· " + stringResource(R.string.attendance_this_session, sessionDate.monthValue, sessionDate.dayOfMonth)
                        remaining != null && remaining > 0 -> "· " + stringResource(R.string.attendance_remaining, remaining)
                        else -> "· " + stringResource(R.string.attendance_totals, summary.present, summary.absent, summary.leave)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (expanded && remaining != null) {
                    Text(
                        text = when {
                            remaining > 0 -> stringResource(R.string.attendance_remaining, remaining)
                            remaining == 0 -> stringResource(R.string.attendance_at_limit)
                            else -> stringResource(R.string.attendance_over_limit, -remaining)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (remaining <= 0) scheme.error else scheme.primary,
                        maxLines = 1,
                        modifier = Modifier.testTag("attendance-remaining"),
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    tint = scheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sessionDate != null) {
                        if (!markable) {
                            Text(
                                text = stringResource(R.string.attendance_not_yet, sessionDate.monthValue, sessionDate.dayOfMonth),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AttendanceStatus.Present to R.string.attendance_present,
                                AttendanceStatus.Absent to R.string.attendance_absent,
                                AttendanceStatus.Leave to R.string.attendance_leave,
                            ).forEach { (status, label) ->
                                AttendanceChoice(
                                    label = stringResource(label),
                                    selected = current == status,
                                    enabled = markable,
                                    modifier = Modifier.weight(1f).testTag("attendance-mark-${status.name}"),
                                    onClick = {
                                        val next = if (current == status) null else status
                                        onMark(key, requireNotNull(date), next)
                                        if (next == AttendanceStatus.Absent && summary.allowance != null && summary.remaining!! <= 1) onLimitReached()
                                    },
                                )
                            }
                        }
                    }
                    AttendanceTotals(summary)
                    AllowanceRow(summary, onSetAllowance = { onSetAllowance(key, it) })
                }
            }
        }
    }
}

/** One of the three marks; the selected one is filled with the primary container and a 1.5dp primary border. */
@Composable
private fun AttendanceChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun AttendanceTotals(summary: AttendanceSummary) {
    Text(
        text = stringResource(R.string.attendance_totals, summary.present, summary.absent, summary.leave),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("attendance-totals"),
    )
}

@Composable
private fun AllowanceRow(summary: AttendanceSummary, onSetAllowance: (Int?) -> Unit) {
    val allowance = summary.allowance
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.attendance_allowance_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (allowance == null) {
            AppAssistChip(
                onClick = { onSetAllowance(3) },
                label = { Text(stringResource(R.string.attendance_set_allowance)) },
                modifier = Modifier.testTag("attendance-allowance-set"),
            )
        } else {
            AppAssistChip(
                onClick = { if (allowance > 0) onSetAllowance(allowance - 1) },
                label = { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.attendance_allowance_less), modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("attendance-allowance-less"),
            )
            Text(
                text = stringResource(R.string.attendance_allowance_value, allowance),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("attendance-allowance-value"),
            )
            AppAssistChip(
                onClick = { onSetAllowance(allowance + 1) },
                label = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.attendance_allowance_more), modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("attendance-allowance-more"),
            )
            AppAssistChip(
                onClick = { onSetAllowance(null) },
                label = { Text(stringResource(R.string.attendance_clear_allowance)) },
                modifier = Modifier.testTag("attendance-allowance-clear"),
            )
        }
    }
}
