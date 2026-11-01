package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import java.time.LocalDate

/** One day's courses for both people: the course name on each period, or null where that period is free. */
internal data class DayBusy(val date: LocalDate, val mine: List<String?>, val theirs: List<String?>) {
    /** Course name on period [period] (from 1) for me, or null. */
    fun mineAt(period: Int): String? = mine[period - 1]

    fun theirsAt(period: Int): String? = theirs[period - 1]

    /** Both people have no class in period [period]. */
    fun bothFree(period: Int): Boolean = mineAt(period) == null && theirsAt(period) == null
}

/** Seven days of both timetables, shared by the week view and the summary. */
internal fun weekBusy(
    weekMonday: LocalDate,
    mine: List<CourseItem>,
    myTermStart: LocalDate?,
    theirs: List<CourseItem>,
    theirTermStart: LocalDate?,
    periodCount: Int,
): List<DayBusy> = (0L until 7L).map { offset ->
    val date = weekMonday.plusDays(offset)
    DayBusy(
        date,
        busyTitlesOn(date, mine, myTermStart, periodCount),
        busyTitlesOn(date, theirs, theirTermStart ?: myTermStart, periodCount),
    )
}

private val LABEL_WIDTH = 22.dp
private val PAIR_GAP = 8.dp
private val TITLE_HEIGHT = 20.dp
private val WEEKDAY_HEIGHT = 18.dp
private val CELL_HEIGHT = 30.dp

/** A course block: periods [startNode]..[endNode] (from 1) of one course. */
private data class CourseBlock(val startNode: Int, val endNode: Int, val name: String)

/** Merge consecutive periods that carry the same course into one block, the way the timetable draws it. */
private fun blocksOf(names: List<String?>): List<CourseBlock> {
    val blocks = mutableListOf<CourseBlock>()
    var index = 0
    while (index < names.size) {
        val name = names[index]
        if (name == null) {
            index++
            continue
        }
        var last = index
        while (last + 1 < names.size && names[last + 1] == name) last++
        blocks += CourseBlock(index + 1, last + 1, name)
        index = last + 1
    }
    return blocks
}

/** A tapped course block: the course if it can be matched, and where it sits. */
private data class PickedBlock(val course: CourseItem?, val name: String, val date: LocalDate, val startNode: Int, val endNode: Int)

/** Outline colour around the periods both people are free in. */
private val SHARED_FREE_FRAME = Color(0xFFFFC107)

/**
 * Two ordinary week timetables side by side, mine on the left (always "我") and theirs on the right, drawn with the
 * same course blocks as the schedule. A dashed line separates the two. Periods both people are free in get a yellow
 * outline, not a fill. Tapping a course block opens its details.
 */
@Composable
internal fun SideBySideFreeWeek(
    days: List<DayBusy>,
    periods: Int,
    mine: List<CourseItem>,
    theirs: List<CourseItem>,
    runs: List<FreeRun>,
    theirsTitle: String,
) {
    val sharedSlots = remember(runs) { runs.flatMap { run -> (run.startNode..run.endNode).map { run.date to it } }.toSet() }
    var picked by remember { mutableStateOf<PickedBlock?>(null) }
    Column(Modifier.fillMaxWidth().testTag("shared-free-week-grid")) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Period numbers once, on the left; both grids share these rows.
            Column(Modifier.width(LABEL_WIDTH)) {
                Spacer(Modifier.height(TITLE_HEIGHT + WEEKDAY_HEIGHT))
                for (period in 1..periods) {
                    Box(Modifier.height(CELL_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("$period", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TimetableGrid(
                title = stringResource(R.string.free_time_mine),
                days = days,
                periods = periods,
                namesOf = { it.mine },
                courses = mine,
                sharedSlots = sharedSlots,
                onBlockClick = { picked = it },
                modifier = Modifier.weight(1f).testTag("shared-free-mine"),
            )
            PairDivider()
            TimetableGrid(
                title = theirsTitle,
                days = days,
                periods = periods,
                namesOf = { it.theirs },
                courses = theirs,
                sharedSlots = sharedSlots,
                onBlockClick = { picked = it },
                modifier = Modifier.weight(1f).testTag("shared-free-theirs"),
            )
        }
    }
    picked?.let { block -> BlockDetailDialog(block, onDismiss = { picked = null }) }
}

/** A dashed line between the two timetables, so it is clear which classes belong to whom. */
@Composable
private fun PairDivider() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.width(PAIR_GAP).fillMaxHeight().testTag("shared-free-divider")) {
        val x = size.width / 2f
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun TimetableGrid(
    title: String,
    days: List<DayBusy>,
    periods: Int,
    namesOf: (DayBusy) -> List<String?>,
    courses: List<CourseItem>,
    sharedSlots: Set<Pair<LocalDate, Int>>,
    onBlockClick: (PickedBlock) -> Unit,
    modifier: Modifier,
) {
    val palette = LocalScheduleAccents.current.coursePalette
    val byTitle = remember(courses) { courses.associateBy { it.title } }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(TITLE_HEIGHT), contentAlignment = Alignment.Center) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().height(WEEKDAY_HEIGHT)) {
            days.forEach { day ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(shortWeekdayRes(day.date.dayOfWeek)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(CELL_HEIGHT * periods)) {
            days.forEach { day ->
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    // Shared free periods: a yellow outline only, so the course blocks stay readable.
                    (1..periods).forEach { period ->
                        if ((day.date to period) in sharedSlots) {
                            Box(
                                Modifier
                                    .offset(y = CELL_HEIGHT * (period - 1))
                                    .fillMaxWidth()
                                    .height(CELL_HEIGHT)
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                    .border(2.dp, SHARED_FREE_FRAME, RoundedCornerShape(6.dp)),
                            )
                        }
                    }
                    blocksOf(namesOf(day)).forEach { block ->
                        val course = byTitle[block.name]
                        val fill = courseColor(block.name, palette, course?.colorArgb, course?.colorEndArgb).container
                        Box(
                            Modifier
                                .offset(y = CELL_HEIGHT * (block.startNode - 1))
                                .fillMaxWidth()
                                .height(CELL_HEIGHT * (block.endNode - block.startNode + 1))
                                .padding(horizontal = 1.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fill)
                                .clickable {
                                    onBlockClick(PickedBlock(course, block.name, day.date, block.startNode, block.endNode))
                                }
                                .padding(horizontal = 2.dp, vertical = 3.dp)
                                .clipToBounds(),
                        ) {
                            Text(
                                text = block.name,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                color = readableOn(fill),
                                textAlign = TextAlign.Start,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Details of a tapped block: name, day and periods, and the room and teacher when the course has them. */
@Composable
private fun BlockDetailDialog(block: PickedBlock, onDismiss: () -> Unit) {
    val periods = if (block.startNode == block.endNode) {
        stringResource(R.string.free_time_one_period, block.startNode)
    } else {
        stringResource(R.string.free_time_periods, block.startNode, block.endNode)
    }
    val dayLine = "${stringResource(shortWeekdayRes(block.date.dayOfWeek))} ${block.date.monthValue}/${block.date.dayOfMonth} · $periods"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shared-free-block-detail"),
        title = { Text(block.name, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dayLine, style = MaterialTheme.typography.bodyMedium)
                block.course?.location?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                block.course?.teacher?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_close)) }
        },
    )
}

/**
 * The shared free periods as cards, one per day: the weekday and date on the left, each free stretch as a
 * capsule on the right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SharedFreeSummary(runs: List<FreeRun>) {
    Column(Modifier.fillMaxWidth().testTag("shared-free-summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.free_time_shared_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (runs.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().testTag("shared-free-none"),
            ) {
                Text(
                    stringResource(R.string.free_time_none),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            runs.groupBy { it.date }.forEach { (date, dayRuns) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(shortWeekdayRes(date.dayOfWeek)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            dayRuns.forEach { run ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.testTag("shared-free-run"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = if (run.startNode == run.endNode) {
                                                stringResource(R.string.free_time_one_period, run.startNode)
                                            } else {
                                                stringResource(R.string.free_time_periods, run.startNode, run.endNode)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
