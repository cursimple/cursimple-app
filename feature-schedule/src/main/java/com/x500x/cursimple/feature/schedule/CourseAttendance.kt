package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("course-attendance-section"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.attendance_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (sessionDate != null) {
                Text(
                    text = stringResource(
                        if (markable) R.string.attendance_this_session else R.string.attendance_not_yet,
                        sessionDate.monthValue,
                        sessionDate.dayOfMonth,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        AttendanceStatus.Present to R.string.attendance_present,
                        AttendanceStatus.Absent to R.string.attendance_absent,
                        AttendanceStatus.Leave to R.string.attendance_leave,
                    ).forEach { (status, label) ->
                        AppFilterChip(
                            selected = current == status,
                            enabled = markable,
                            onClick = {
                                val next = if (current == status) null else status
                                onMark(key, requireNotNull(date), next)
                                if (next == AttendanceStatus.Absent && summary.allowance != null && summary.remaining!! <= 1) onLimitReached()
                            },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("attendance-mark-${status.name}"),
                        )
                    }
                }
            }
            AttendanceTotals(summary)
            AllowanceRow(summary, onSetAllowance = { onSetAllowance(key, it) })
        }
    }
}

@Composable
private fun AttendanceTotals(summary: AttendanceSummary) {
    Text(
        text = stringResource(R.string.attendance_totals, summary.present, summary.absent, summary.leave),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("attendance-totals"),
    )
    val remaining = summary.remaining
    if (remaining != null) {
        Text(
            text = when {
                remaining > 0 -> stringResource(R.string.attendance_remaining, remaining)
                remaining == 0 -> stringResource(R.string.attendance_at_limit)
                else -> stringResource(R.string.attendance_over_limit, -remaining)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (remaining <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("attendance-remaining"),
        )
    }
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
