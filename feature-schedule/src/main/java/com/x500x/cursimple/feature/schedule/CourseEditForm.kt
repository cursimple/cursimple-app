package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.typedClock
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf

internal enum class WeekParity(@param:StringRes val labelRes: Int) {
    All(R.string.schedule_week_parity_all),
    Odd(R.string.schedule_week_parity_odd),
    Even(R.string.schedule_week_parity_even),

    Custom(R.string.schedule_week_parity_custom),
}

/**
 * Null means invalid or empty selection; do not confuse it with the model's every-week empty
 * list.
 */
internal fun manualCourseWeeksOrNull(
    startWeek: Int?,
    endWeek: Int?,
    parity: WeekParity,
    maxWeekCount: Int,
): List<Int>? {
    if (startWeek == null || endWeek == null) return null
    if (startWeek !in 1..maxWeekCount) return null
    if (endWeek !in startWeek..maxWeekCount) return null
    return (startWeek..endWeek).filter { week ->
        when (parity) {
            WeekParity.All -> true
            WeekParity.Odd -> week % 2 == 1
            WeekParity.Even -> week % 2 == 0
            WeekParity.Custom -> true
        }
    }.takeIf { it.isNotEmpty() }
}

/**
 * Infer a week preset only if it reproduces the exact stored week set; otherwise preserve
 * custom selection.
 */
internal fun weekParityOf(weeks: List<Int>?): WeekParity {
    if (weeks.isNullOrEmpty()) return WeekParity.All
    val sorted = weeks.distinct().sorted()
    val first = sorted.first()
    val last = sorted.last()
    for (parity in listOf(WeekParity.All, WeekParity.Odd, WeekParity.Even)) {
        val expanded = (first..last).filter { week ->
            when (parity) {
                WeekParity.All -> true
                WeekParity.Odd -> week % 2 == 1
                WeekParity.Even -> week % 2 == 0
                WeekParity.Custom -> false
            }
        }
        if (expanded == sorted) return parity
    }
    return WeekParity.Custom
}

/** Include stored weeks beyond the configured term so they remain editable. */
internal fun customWeekLimit(maxWeekCount: Int, weeks: List<Int>?): Int =
    maxOf(maxWeekCount, weeks?.maxOrNull() ?: 0).coerceAtLeast(1)

internal fun encodeCustomWeeks(weeks: Collection<Int>): String = weeks.distinct().sorted().joinToString(",")

/** Use expanded [maxWeekCount] from [customWeekLimit] to preserve imported weeks. */
internal fun decodeCustomWeeks(raw: String, maxWeekCount: Int): List<Int> = raw
    .split(',')
    .mapNotNull { it.trim().toIntOrNull() }
    .filter { it in 1..maxWeekCount }
    .distinct()
    .sorted()

// Encode week locations with control-character separators to avoid collisions with visible room text.
private const val WEEK_LOC_ENTRY_SEP = "\u0002"
private const val WEEK_LOC_KV_SEP = "\u0001"

internal fun encodeWeekLocations(map: Map<Int, String>): String =
    map.entries
        .filter { it.value.isNotBlank() }
        .sortedBy { it.key }
        .joinToString(WEEK_LOC_ENTRY_SEP) { "${it.key}$WEEK_LOC_KV_SEP${it.value}" }

internal fun decodeWeekLocations(raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(WEEK_LOC_ENTRY_SEP).mapNotNull { entry ->
        val parts = entry.split(WEEK_LOC_KV_SEP, limit = 2)
        val week = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val loc = parts.getOrNull(1).orEmpty()
        if (loc.isBlank()) null else week to loc
    }.toMap()
}

/**
 * Shared field editor emits [onDraftChange] with null for invalid input; callers place save
 * controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseEditFormFields(
    initial: CourseItem?,
    existingCourses: List<CourseItem>,
    maxNodeCount: Int,
    maxWeekCount: Int,
    onDraftChange: (CourseItem?) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    slotTimes: List<ClassSlotTime> = emptyList(),
) {
    val reuse = rememberCourseReuseValues(existingCourses, initial)
    var title by rememberSaveable(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var teacher by rememberSaveable(initial) { mutableStateOf(initial?.teacher.orEmpty()) }
    var location by rememberSaveable(initial) { mutableStateOf(initial?.location.orEmpty()) }
    var dayOfWeek by rememberSaveable(initial) { mutableStateOf(initial?.time?.dayOfWeek ?: 1) }
    var startNodeText by rememberSaveable(initial) {
        mutableStateOf(initial?.time?.startNode?.toString() ?: "1")
    }
    var endNodeText by rememberSaveable(initial) {
        mutableStateOf(initial?.time?.endNode?.toString() ?: "2")
    }
    // Placeholders keep their own times; only real courses switch between periods and clock.
    val initialClock = initial?.takeUnless { it.reminderOnly }?.typedClock()
    var byClock by rememberSaveable(initial) { mutableStateOf(initialClock != null) }
    var clockStart by rememberSaveable(initial) {
        mutableIntStateOf(initialClock?.first?.let { it.hour * 60 + it.minute } ?: DEFAULT_CLOCK_START)
    }
    var clockEnd by rememberSaveable(initial) {
        mutableIntStateOf(initialClock?.second?.let { it.hour * 60 + it.minute } ?: (DEFAULT_CLOCK_START + DEFAULT_CLOCK_LENGTH))
    }
    // Leave new-course weeks unset rather than assuming a misleading default range.
    var startWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.minOrNull()?.toString().orEmpty())
    }
    var endWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.maxOrNull()?.toString().orEmpty())
    }
    var parity by rememberSaveable(initial) { mutableStateOf(weekParityOf(initial?.weeks)) }
    var customWeeksRaw by rememberSaveable(initial) {
        mutableStateOf(encodeCustomWeeks(initial?.weeks.orEmpty()))
    }
    var pickingWeeks by rememberSaveable(initial) { mutableStateOf(false) }
    var category by rememberSaveable(initial) {
        mutableStateOf(initial?.category ?: CourseCategory.Course)
    }
    var pickingWeekLocations by rememberSaveable(initial) { mutableStateOf(false) }
    var weekLocationsRaw by rememberSaveable(initial) {
        mutableStateOf(encodeWeekLocations(initial?.weekLocations.orEmpty()))
    }
    var pickingWeekTeachers by rememberSaveable(initial) { mutableStateOf(false) }
    var weekTeachersRaw by rememberSaveable(initial) {
        mutableStateOf(encodeWeekLocations(initial?.weekTeachers.orEmpty()))
    }

    val titleTrimmed = title.trim()
    val clockValid = clockEnd > clockStart
    val clockNodes = if (byClock && clockValid) nodesForClock(slotTimes, clockStart, clockEnd) else null
    val startNode = clockNodes?.first ?: startNodeText.toIntOrNull()
    val endNode = clockNodes?.second ?: endNodeText.toIntOrNull()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val rangeValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    // Use expanded bounds for both week selection and decoding.
    val weekLimit = remember(maxWeekCount, initial) {
        customWeekLimit(maxWeekCount, initial?.weeks)
    }
    val customWeeks = remember(customWeeksRaw, weekLimit) {
        decodeCustomWeeks(customWeeksRaw, weekLimit)
    }
    val weeks = if (parity == WeekParity.Custom) {
        customWeeks.takeIf { it.isNotEmpty() }
    } else {
        manualCourseWeeksOrNull(startWeek, endWeek, parity, maxWeekCount)
    }
    val nodesValid = if (byClock) {
        clockValid && startNode != null && endNode != null && endNode >= startNode
    } else {
        startNode != null && endNode != null && startNode in 1..maxNodeCount && endNode in startNode..maxNodeCount
    }
    val weekLocationsMap = remember(weekLocationsRaw) { decodeWeekLocations(weekLocationsRaw) }
    val weekTeachersMap = remember(weekTeachersRaw) { decodeWeekLocations(weekTeachersRaw) }
    // Retain nonblank locations in selected weeks only; disabling clears overrides.
    val effectiveWeekLocations = if (weeks != null) {
        weekLocationsMap.filterKeys { it in weeks }.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
    } else {
        emptyMap()
    }
    val draft = if (titleTrimmed.isNotBlank() && nodesValid && weeks != null) {
        buildCourse(
            title = titleTrimmed,
            teacher = teacher.trim(),
            location = location.trim(),
            dayOfWeek = dayOfWeek,
            startNode = startNode!!,
            endNode = endNode!!,
            weeks = weeks,
            category = category,
            weekLocations = effectiveWeekLocations,
            weekTeachers = weekTeachersMap.filterKeys { weeks != null && it in weeks }
                .mapValues { it.value.trim() }.filterValues { it.isNotBlank() },
            existing = initial,
            clock = if (byClock) clockStart to clockEnd else null,
        )
    } else {
        null
    }
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)
    LaunchedEffect(draft) { currentOnDraftChange(draft) }

    val conflictWarning = remember(
        existingCourses, dayOfWeek, startNode, endNode, weeks, category, maxNodeCount, maxWeekCount,
    ) {
        addCourseConflictWarning(
            draftCourseConflicts(
                existingCourses = existingCourses,
                dayOfWeek = dayOfWeek,
                startNode = startNode,
                endNode = endNode,
                weeks = weeks,
                category = category,
                maxNodeCount = maxNodeCount,
                maxWeekCount = maxWeekCount,
            ),
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        CourseTitleField(title, onChange = { title = it }, courses = reuse.courses, onReuse = { course ->
            title = course.title.trim()
            teacher = course.teacher.trim()
            location = course.location.trim()
        })

        CourseTeacherField(
            teacher = teacher,
            onTeacherChange = { teacher = it },
            weekTeachers = weekTeachersMap,
            onPickWeekTeachers = { pickingWeekTeachers = true },
            suggestions = reuse.teachers,
        )

        CourseLocationField(
            location = location,
            onLocationChange = { location = it },
            weekLocations = weekLocationsMap,
            onPickWeekLocations = { pickingWeekLocations = true },
            suggestions = reuse.locations,
        )

        CourseFormLabel(stringResource(R.string.schedule_course_category_label))
        FlowChipRow {
            AppFilterChip(
                selected = category == CourseCategory.Course,
                onClick = { category = CourseCategory.Course },
                label = { Text(stringResource(R.string.schedule_category_course)) },
            )
            AppFilterChip(
                selected = category == CourseCategory.Exam,
                onClick = { category = CourseCategory.Exam },
                label = { Text(stringResource(R.string.schedule_category_exam)) },
            )
        }

        CourseFormLabel(stringResource(R.string.schedule_add_course_time_label))
        FlowChipRow {
            (1..7).forEach { day ->
                AppFilterChip(
                    selected = dayOfWeek == day,
                    onClick = { dayOfWeek = day },
                    label = { Text(stringResource(scheduleWeekdayFullRes(day))) },
                )
            }
        }

        if (initial?.reminderOnly != true) {
            CourseTimeModeChips(byClock = byClock, onByClock = { clock ->
                if (clock && !byClock) {
                    val nodes = startNodeText.toIntOrNull()?.let { s -> endNodeText.toIntOrNull()?.let { s to it } }
                    nodes?.let { (s, e) -> clockForNodes(slotTimes, s, e) }?.let { (s, e) ->
                        clockStart = s
                        clockEnd = e
                    }
                }
                if (!clock && byClock) clockNodes?.let { (s, e) ->
                    startNodeText = s.toString()
                    endNodeText = e.toString()
                }
                byClock = clock
            })
        }

        if (byClock) CourseClockFields(clockStart, clockEnd, onStartChange = { clockStart = it }, onEndChange = { clockEnd = it })
        else Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = startNodeText,
                onValueChange = { startNodeText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.schedule_node_start_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = endNodeText,
                onValueChange = { endNodeText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.schedule_node_end_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        CourseFormLabel(stringResource(R.string.schedule_weeks_label))
        // Custom selection replaces start/end inputs with a selected-week summary.
        if (parity == WeekParity.Custom) {
            SelectedWeeksRow(
                weeks = customWeeks,
                onEdit = { pickingWeeks = true },
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = startWeekText,
                    onValueChange = { startWeekText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.schedule_week_start_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endWeekText,
                    onValueChange = { endWeekText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.schedule_week_end_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FlowChipRow {
            WeekParity.entries.forEach { p ->
                AppFilterChip(
                    selected = parity == p,
                    onClick = {
                        parity = p
                        if (p == WeekParity.Custom) {
                            if (customWeeksRaw.isBlank()) {
                                customWeeksRaw = encodeCustomWeeks(
                                    manualCourseWeeksOrNull(startWeek, endWeek, WeekParity.All, weekLimit).orEmpty(),
                                )
                            }
                            pickingWeeks = true
                        }
                    },
                    label = { Text(stringResource(p.labelRes)) },
                )
            }
        }

        if (pickingWeekTeachers) {
            WeekTeacherDialog(
                weeks = weeks?.takeIf { it.isNotEmpty() } ?: (1..weekLimit).toList(),
                courseTitle = titleTrimmed,
                baseTeacher = teacher.trim(),
                weekTeachers = weekTeachersMap,
                suggestions = reuse.teachers,
                onConfirm = { values ->
                    weekTeachersRaw = encodeWeekLocations(values)
                    pickingWeekTeachers = false
                },
                onDismiss = { pickingWeekTeachers = false },
            )
        }

        if (pickingWeekLocations) {
            WeekLocationDialog(
                weeks = weeks?.takeIf { it.isNotEmpty() } ?: (1..weekLimit).toList(),
                courseTitle = titleTrimmed,
                baseLocation = location.trim(),
                weekLocations = weekLocationsMap,
                suggestions = reuse.locations,
                onConfirm = { values ->
                    weekLocationsRaw = encodeWeekLocations(values)
                    pickingWeekLocations = false
                },
                onDismiss = { pickingWeekLocations = false },
            )
        }

        if (pickingWeeks) {
            WeekMatrixDialog(
                maxWeekCount = weekLimit,
                selected = customWeeks.toSet(),
                onConfirm = { picked ->
                    customWeeksRaw = encodeCustomWeeks(picked)
                    pickingWeeks = false
                },
                onDismiss = { pickingWeeks = false },
            )
        }

        if (rangeValid && parity != WeekParity.Custom && weeks == null) {
            Text(
                text = stringResource(
                    R.string.schedule_add_course_parity_empty,
                    stringResource(parity.labelRes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        conflictWarning?.let { CourseConflictWarning(warning = it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectedWeeksRow(
    weeks: List<Int>,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (weeks.isEmpty()) {
                        stringResource(R.string.schedule_week_custom_empty)
                    } else {
                        stringResource(R.string.schedule_week_custom_summary, weeks.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (weeks.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (weeks.isNotEmpty()) {
                    Text(
                        text = weeks.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppOutlinedButton(onClick = onEdit) {
                Text(stringResource(R.string.schedule_week_custom_edit))
            }
        }
    }
}

/** Week-grid edits remain local until confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekMatrixDialog(
    maxWeekCount: Int,
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val picked = remember(selected) { mutableStateListOf<Int>().apply { addAll(selected.sorted()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_week_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.schedule_week_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekSelectionGrid((1..maxWeekCount).toList(), picked.toSet(), onChange = { selected ->
                    picked.clear()
                    picked.addAll(selected.sorted())
                }, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(onClick = {
                        picked.clear()
                        picked.addAll(1..maxWeekCount)
                    }) { Text(stringResource(R.string.schedule_week_custom_all)) }
                    AppOutlinedButton(onClick = { picked.clear() }) {
                        Text(stringResource(R.string.schedule_action_clear))
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(picked.toSet()) },
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}

/** Shared metadata fields keep weekly settings beside the editable value. */
@Composable
internal fun CourseLocationField(
    location: String,
    onLocationChange: (String) -> Unit,
    weekLocations: Map<Int, String>,
    onPickWeekLocations: () -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
) {
    CourseWeeklyField(location, onLocationChange, weekLocations, onPickWeekLocations,
        R.string.schedule_course_location_label, R.string.schedule_week_location_title,
        R.string.schedule_week_location_summary, "course-location", modifier, suggestions)
}

@Composable
internal fun CourseTeacherField(
    teacher: String,
    onTeacherChange: (String) -> Unit,
    weekTeachers: Map<Int, String>,
    onPickWeekTeachers: () -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
) {
    CourseWeeklyField(teacher, onTeacherChange, weekTeachers, onPickWeekTeachers,
        R.string.schedule_course_teacher_label, R.string.schedule_week_teacher_title,
        R.string.schedule_week_teacher_summary, "course-teacher", modifier, suggestions)
}

@Composable
private fun CourseWeeklyField(
    value: String,
    onChange: (String) -> Unit,
    overrides: Map<Int, String>,
    onPickWeeks: () -> Unit,
    @StringRes labelRes: Int,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    tag: String,
    modifier: Modifier,
    suggestions: List<String>,
) {
    val options = remember(suggestions) { suggestions.map { CourseReuseOption(it) } }
    CourseReuseTextField(value, onChange, stringResource(labelRes), options, modifier = modifier, tag = tag,
        extraAction = {
            AppCircleIconButton(onClick = onPickWeeks, modifier = Modifier.testTag("$tag-weeks"), size = FieldActionSize) {
                Icon(Icons.Rounded.EditCalendar, stringResource(titleRes), Modifier.size(FieldActionIconSize),
                    tint = if (overrides.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        supportingText = overrides.size.takeIf { it > 0 }?.let { count -> {
            Text(stringResource(summaryRes, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } },
    )
}

@Composable
internal fun WeekLocationDialog(
    weeks: List<Int>, courseTitle: String, baseLocation: String, weekLocations: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String> = emptyList(),
) {
    WeekCourseValueDialog(weeks, courseTitle, baseLocation, weekLocations, onConfirm, onDismiss, suggestions,
        R.string.schedule_week_location_title, R.string.schedule_week_location_default,
        R.string.schedule_week_location_no_default, R.string.schedule_week_location_counts,
        R.string.schedule_week_location_placeholder_empty)
}

@Composable
internal fun WeekTeacherDialog(
    weeks: List<Int>, courseTitle: String, baseTeacher: String, weekTeachers: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String> = emptyList(),
) {
    WeekCourseValueDialog(weeks, courseTitle, baseTeacher, weekTeachers, onConfirm, onDismiss, suggestions,
        R.string.schedule_week_teacher_title, R.string.schedule_week_teacher_default,
        R.string.schedule_week_teacher_no_default, R.string.schedule_week_teacher_counts,
        R.string.schedule_week_teacher_placeholder_empty)
}

/** Changes stay local until Save; cancelling never edits the parent course draft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCourseValueDialog(
    weeks: List<Int>, courseTitle: String, defaultValue: String, overrides: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String>,
    @StringRes titleRes: Int, @StringRes defaultRes: Int, @StringRes noDefaultRes: Int,
    @StringRes countsRes: Int, @StringRes placeholderRes: Int,
) {
    val sorted = remember(weeks) { weeks.filter { it > 0 }.distinct().sorted() }
    var selectedRaw by rememberSaveable { mutableStateOf("") }
    val selected = remember(selectedRaw) { decodeCustomWeeks(selectedRaw, sorted.maxOrNull() ?: 1).filter { it in sorted }.toSet() }
    var draftRaw by rememberSaveable { mutableStateOf(encodeWeekLocations(overrides)) }
    val draft = remember(draftRaw) { decodeWeekLocations(draftRaw) }
    var value by rememberSaveable { mutableStateOf("") }
    val options = remember(suggestions, defaultValue, draft) {
        (suggestions + defaultValue + draft.values).map(String::trim).filter(String::isNotEmpty).distinct().map { CourseReuseOption(it) }
    }
    fun select(next: Set<Int>) {
        selectedRaw = encodeCustomWeeks(next)
        value = next.map { draft[it].orEmpty() }.distinct().singleOrNull().orEmpty()
    }
    fun applyValue(newValue: String) {
        draftRaw = encodeWeekLocations(batchWeekValues(draft, sorted, selected, newValue))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("course-week-values-dialog"),
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(courseTitle.ifBlank { stringResource(R.string.schedule_week_location_untitled_course) },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (defaultValue.isBlank()) stringResource(noDefaultRes) else stringResource(defaultRes, defaultValue),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(countsRes, sorted.size, draft.count { it.key in sorted && it.value.isNotBlank() }),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.schedule_week_batch_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                WeekSelectionGrid(sorted, selected, ::select, customized = draft.keys)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppOutlinedButton(onClick = { select(sorted.toSet()) }, modifier = Modifier.testTag("week-select-all")) {
                        Text(stringResource(R.string.schedule_week_custom_all))
                    }
                    AppOutlinedButton(onClick = { select(emptySet()) }, enabled = selected.isNotEmpty()) {
                        Text(stringResource(R.string.schedule_week_batch_clear_selection))
                    }
                }
                CourseReuseTextField(value, { value = it }, stringResource(R.string.schedule_week_batch_selected, selected.size),
                    options, tag = "week-batch-value", enabled = selected.isNotEmpty(),
                    placeholder = defaultValue.ifBlank { stringResource(placeholderRes) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Button(onClick = { applyValue(value) }, enabled = selected.isNotEmpty() && value.isNotBlank(),
                        modifier = Modifier.testTag("week-batch-apply")) {
                        Text(stringResource(R.string.schedule_week_batch_apply))
                    }
                    AppOutlinedButton(onClick = { value = ""; applyValue("") }, enabled = selected.isNotEmpty(),
                        modifier = Modifier.testTag("week-batch-default")) {
                        Text(stringResource(R.string.schedule_week_batch_default))
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = {
                val values = if (selected.isNotEmpty() && value.isNotBlank()) batchWeekValues(draft, sorted, selected, value) else draft
                onConfirm(values)
            }, modifier = Modifier.testTag("week-batch-save")) {
                Text(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

@Composable
private fun CourseFormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

private fun buildCourse(
    title: String,
    teacher: String,
    location: String,
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    weeks: List<Int>,
    category: CourseCategory,
    weekLocations: Map<Int, String> = emptyMap(),
    weekTeachers: Map<Int, String> = emptyMap(),
    existing: CourseItem? = null,
    clock: Pair<Int, Int>? = null,
): CourseItem {
    val time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode)
    val clockStart = clock?.first?.let(::formatMinuteOfDay)
    val clockEnd = clock?.second?.let(::formatMinuteOfDay)
    // Edit only form fields, preserving IDs and placeholder metadata so manual overrides replace plugin originals.
    if (existing != null) {
        return existing.copy(
            title = title,
            teacher = teacher,
            location = location,
            weeks = weeks,
            category = category,
            time = time,
            weekLocations = weekLocations,
            weekTeachers = weekTeachers,
            reminderStartTime = keptClock(existing, clockStart, existing.reminderStartTime),
            reminderEndTime = keptClock(existing, clockEnd, existing.reminderEndTime),
        )
    }
    return CourseItem(
        id = "manual-" + UUID.randomUUID().toString().take(12),
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        category = category,
        time = time,
        weekLocations = weekLocations,
        weekTeachers = weekTeachers,
        reminderStartTime = clockStart,
        reminderEndTime = clockEnd,
    )
}

/** Placeholders keep their times; leaving clock mode clears typed times but not lone imported ones. */
private fun keptClock(existing: CourseItem, typed: String?, stored: String?): String? = when {
    existing.reminderOnly -> stored
    typed != null -> typed
    existing.typedClock() != null -> null
    else -> stored
}
