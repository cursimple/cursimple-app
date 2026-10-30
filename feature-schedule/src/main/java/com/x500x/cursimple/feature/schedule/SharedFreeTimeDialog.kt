package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What a pasted share code decodes to: the other person's courses and their term start, when shared. */
data class SharedTimetable(val courses: List<CourseItem>, val termStart: LocalDate?)

/**
 * Compare my timetable with someone else's shared code: the periods where both are free, week by week.
 * [decode] turns the pasted text into their timetable; it lives with the QR/share code in the app.
 */
@Composable
fun SharedFreeTimeDialog(
    myCourses: List<CourseItem>,
    myTermStart: LocalDate?,
    timingSlots: List<ClassSlotTime>,
    decode: suspend (String) -> Result<SharedTimetable>,
    onDismiss: () -> Unit,
) {
    val zone = LocalAppZone.current
    val thisMonday = remember(zone) { zone.today().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) } }
    var weekOffset by rememberSaveable { mutableStateOf(0) }
    val weekMonday = thisMonday.plusWeeks(weekOffset.toLong())
    var code by rememberSaveable { mutableStateOf("") }
    var theirs by remember { mutableStateOf<SharedTimetable?>(null) }
    var decodeFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val periods = periodCountOf(timingSlots.maxOfOrNull { it.endNode })

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shared-free-dialog"),
        title = { Text(stringResource(R.string.free_time_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        decodeFailed = false
                    },
                    label = { Text(stringResource(R.string.free_time_code_label)) },
                    minLines = 2,
                    maxLines = 4,
                    supportingText = { Text(stringResource(R.string.free_time_code_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("shared-free-code"),
                )
                AppOutlinedButton(
                    enabled = code.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = decode(code)
                            theirs = result.getOrNull()
                            decodeFailed = result.isFailure
                            busy = false
                        }
                    },
                    modifier = Modifier.testTag("shared-free-decode"),
                ) { Text(stringResource(R.string.free_time_compare)) }

                if (decodeFailed) {
                    Text(
                        text = stringResource(R.string.free_time_decode_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("shared-free-error"),
                    )
                }

                theirs?.let { other ->
                    // The range gets its own full-width line so it never wraps into a narrow column; the buttons sit under it.
                    Text(
                        text = stringResource(R.string.free_time_week_range, weekMonday.monthValue, weekMonday.dayOfMonth, weekMonday.plusDays(6).monthValue, weekMonday.plusDays(6).dayOfMonth),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().testTag("shared-free-week"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppOutlinedButton(onClick = { weekOffset-- }) { Text(stringResource(R.string.free_time_prev_week)) }
                        AppOutlinedButton(onClick = { weekOffset++ }) { Text(stringResource(R.string.free_time_next_week)) }
                    }
                    Text(
                        text = stringResource(R.string.free_time_their_count, other.courses.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val runs = remember(myCourses, myTermStart, other, weekMonday, periods) {
                        sharedFreeRuns(myCourses, myTermStart, other.courses, other.termStart, weekMonday, periods)
                    }
                    if (runs.isEmpty()) {
                        Text(stringResource(R.string.free_time_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("shared-free-none"))
                    } else {
                        runs.groupBy { it.date }.forEach { (date, dayRuns) ->
                            Text(
                                text = stringResource(R.string.free_time_day, stringResource(shortWeekdayRes(date.dayOfWeek)), date.monthValue, date.dayOfMonth),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            dayRuns.forEach { run ->
                                Text(
                                    text = if (run.startNode == run.endNode) {
                                        stringResource(R.string.free_time_one_period, run.startNode)
                                    } else {
                                        stringResource(R.string.free_time_periods, run.startNode, run.endNode)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp).testTag("shared-free-run"),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_close)) } },
    )
}
