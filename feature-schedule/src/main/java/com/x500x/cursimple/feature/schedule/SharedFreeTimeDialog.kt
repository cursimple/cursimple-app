package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What a pasted share code decodes to: the other person's courses and their term start, when shared. */
data class SharedTimetable(val courses: List<CourseItem>, val termStart: LocalDate?, val termName: String? = null)

/**
 * Compare my timetable with someone else's shared code: two week timetables side by side, with the periods
 * both people are free highlighted. A full page like the course editor, so both grids have room.
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("shared-free-dialog"),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppCircleIconButton(onClick = onDismiss, modifier = Modifier.testTag("shared-free-close")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.schedule_action_close))
                    }
                    Text(
                        text = stringResource(R.string.free_time_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                        val days = remember(myCourses, myTermStart, other, weekMonday, periods) {
                            weekBusy(weekMonday, myCourses, myTermStart, other.courses, other.termStart, periods)
                        }
                        val runs = remember(myCourses, myTermStart, other, weekMonday, periods) {
                            sharedFreeRuns(myCourses, myTermStart, other.courses, other.termStart, weekMonday, periods)
                        }
                        SideBySideFreeWeek(
                            days = days,
                            periods = periods,
                            mine = myCourses,
                            theirs = other.courses,
                            runs = runs,
                            theirsTitle = other.termName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.free_time_theirs),
                        )
                        SharedFreeSummary(runs)
                    }
                }
            }
        }
    }
}
