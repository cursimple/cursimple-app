package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
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
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.teacherForWeek
import com.x500x.cursimple.core.kernel.model.titleForWeek
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
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

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
    sourceWeekNumber: Int? = null,
    initialCategory: CourseCategory = CourseCategory.Course,
    onCategoryChange: ((CourseCategory) -> Unit)? = null,
    onSelectEvent: (() -> Unit)? = null,
    showCategorySelector: Boolean = true,
    /** Fill picked in the course detail before colors lived on the course; shown here until the editor saves. */
    legacyColorArgb: Long? = null,
) {
    val reuse = rememberCourseReuseValues(existingCourses, initial)
    var title by rememberSaveable(initial, sourceWeekNumber) {
        mutableStateOf(sourceWeekNumber?.let { initial?.titleForWeek(it) } ?: initial?.title.orEmpty())
    }
    var teacher by rememberSaveable(initial, sourceWeekNumber) {
        mutableStateOf(sourceWeekNumber?.let { initial?.teacherForWeek(it) } ?: initial?.teacher.orEmpty())
    }
    var location by rememberSaveable(initial, sourceWeekNumber) {
        mutableStateOf(sourceWeekNumber?.let { initial?.locationForWeek(it) } ?: initial?.location.orEmpty())
    }
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
    var category by rememberSaveable(initial, initialCategory) {
        mutableStateOf(initial?.category ?: initialCategory)
    }
    LaunchedEffect(initialCategory) {
        if (initial == null) {
            category = initialCategory
        }
    }
    var fillArgb by rememberSaveable(initial) { mutableStateOf(initial?.colorArgb ?: legacyColorArgb) }
    var fillEndArgb by rememberSaveable(initial) { mutableStateOf(initial?.colorEndArgb) }
    var borderArgb by rememberSaveable(initial) { mutableStateOf(initial?.borderArgb) }
    // A new course named like an existing one starts with that course's look, until the user picks their own.
    var appearanceTouched by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(title) {
        if (initial == null && !appearanceTouched) {
            val match = existingCourses.firstOrNull { it.title.trim() == title.trim() && title.isNotBlank() && !it.appearance().isDefault }
            if (match != null) {
                fillArgb = match.colorArgb; fillEndArgb = match.colorEndArgb; borderArgb = match.borderArgb
            }
        }
    }
    var pickingWeekTitles by rememberSaveable(initial) { mutableStateOf(false) }
    var weekTitlesRaw by rememberSaveable(initial) {
        mutableStateOf(encodeWeekLocations(initial?.weekTitles.orEmpty()))
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
    val weekTitlesMap = remember(weekTitlesRaw) { decodeWeekLocations(weekTitlesRaw) }
    // Retain nonblank values in selected weeks only; disabling clears overrides.
    val effectiveWeekLocations = if (weeks != null) {
        weekLocationsMap.filterKeys { it in weeks }.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
    } else {
        emptyMap()
    }
    val effectiveWeekTeachers = if (weeks != null) {
        weekTeachersMap.filterKeys { it in weeks }.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
    } else {
        emptyMap()
    }
    val effectiveWeekTitles = if (weeks != null) {
        weekTitlesMap.filterKeys { it in weeks }.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
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
            weekTeachers = effectiveWeekTeachers,
            weekTitles = effectiveWeekTitles,
            existing = initial,
            clock = if (byClock) clockStart to clockEnd else null,
            appearance = ItemAppearance(fillArgb, fillEndArgb, borderArgb),
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
        CourseTitleField(
            value = title,
            onChange = { title = it },
            courses = reuse.courses,
            onReuse = { course ->
                title = course.title.trim()
                teacher = course.teacher.trim()
                location = course.location.trim()
            },
            weekTitles = weekTitlesMap,
            onPickWeekTitles = { pickingWeekTitles = true },
        )

        if (showCategorySelector) {
            FlowChipRow {
                AppFilterChip(
                    selected = category == CourseCategory.Course,
                    onClick = {
                        category = CourseCategory.Course
                        onCategoryChange?.invoke(CourseCategory.Course)
                    },
                    label = { Text(stringResource(R.string.schedule_category_course)) },
                )
                AppFilterChip(
                    selected = category == CourseCategory.Exam,
                    onClick = {
                        category = CourseCategory.Exam
                        onCategoryChange?.invoke(CourseCategory.Exam)
                    },
                    label = { Text(stringResource(R.string.schedule_category_exam)) },
                )
                if (onSelectEvent != null) {
                    AppFilterChip(
                        selected = false,
                        onClick = onSelectEvent,
                        label = { Text(stringResource(R.string.schedule_category_event)) },
                    )
                }
            }
        }

        CourseWeeksSection(
            parity = parity,
            onParityChange = { p ->
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
            startWeekText = startWeekText,
            onStartWeekChange = { startWeekText = it },
            endWeekText = endWeekText,
            onEndWeekChange = { endWeekText = it },
            customWeeks = customWeeks,
            onEditCustomWeeks = { pickingWeeks = true },
            weeks = weeks,
            maxWeekCount = maxWeekCount,
        )

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

        CourseFormSection(title = stringResource(R.string.schedule_add_course_time_label)) {
        WeekdaySelector(selectedDay = dayOfWeek, onSelect = { dayOfWeek = it })
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
        }

        CourseFormSection(title = stringResource(R.string.schedule_course_place_section)) {
            CourseLocationField(
                location = location,
                onLocationChange = { location = it },
                weekLocations = weekLocationsMap,
                onPickWeekLocations = { pickingWeekLocations = true },
                suggestions = reuse.locations,
            )

            CourseTeacherField(
                teacher = teacher,
                onTeacherChange = { teacher = it },
                weekTeachers = weekTeachersMap,
                onPickWeekTeachers = { pickingWeekTeachers = true },
                suggestions = reuse.teachers,
            )
        }

        ItemAppearanceSection(
            appearance = ItemAppearance(fillArgb, fillEndArgb, borderArgb),
            onChange = { appearanceTouched = true; fillArgb = it.fill; fillEndArgb = it.fillEnd; borderArgb = it.border },
            autoColor = courseColor(title.ifBlank { initial?.title.orEmpty() }, LocalScheduleAccents.current.coursePalette).container,
            previewTitle = title,
        )

        if (pickingWeekTitles) {
            WeekTitleDialog(
                weeks = weeks?.takeIf { it.isNotEmpty() } ?: (1..weekLimit).toList(),
                courseTitle = titleTrimmed,
                baseTitle = title.trim(),
                weekTitles = weekTitlesMap,
                suggestions = reuse.courses.map { it.title }.distinct(),
                onConfirm = { values ->
                    weekTitlesRaw = encodeWeekLocations(values)
                    pickingWeekTitles = false
                },
                onDismiss = { pickingWeekTitles = false },
            )
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppOutlinedButton(onClick = {
                        picked.clear()
                        picked.addAll(1..maxWeekCount)
                    }) { Text(stringResource(R.string.schedule_week_custom_all)) }
                    AppOutlinedButton(onClick = {
                        picked.clear()
                        picked.addAll((1..maxWeekCount).filter { it % 2 != 0 })
                    }) { Text(stringResource(R.string.schedule_week_batch_odd)) }
                    AppOutlinedButton(onClick = {
                        picked.clear()
                        picked.addAll((1..maxWeekCount).filter { it % 2 == 0 })
                    }) { Text(stringResource(R.string.schedule_week_batch_even)) }
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
internal fun WeekTitleDialog(
    weeks: List<Int>, courseTitle: String, baseTitle: String, weekTitles: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String> = emptyList(),
) {
    WeekCourseValueDialog(weeks, courseTitle, baseTitle, weekTitles, onConfirm, onDismiss, suggestions,
        R.string.schedule_week_title_dialog_title, R.string.schedule_week_title_default,
        R.string.schedule_week_title_no_default, R.string.schedule_week_title_value_label)
}

@Composable
internal fun WeekLocationDialog(
    weeks: List<Int>, courseTitle: String, baseLocation: String, weekLocations: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String> = emptyList(),
) {
    WeekCourseValueDialog(weeks, courseTitle, baseLocation, weekLocations, onConfirm, onDismiss, suggestions,
        R.string.schedule_week_location_title, R.string.schedule_week_location_default,
        R.string.schedule_week_location_no_default, R.string.schedule_week_location_value_label)
}

@Composable
internal fun WeekTeacherDialog(
    weeks: List<Int>, courseTitle: String, baseTeacher: String, weekTeachers: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String> = emptyList(),
) {
    WeekCourseValueDialog(weeks, courseTitle, baseTeacher, weekTeachers, onConfirm, onDismiss, suggestions,
        R.string.schedule_week_teacher_title, R.string.schedule_week_teacher_default,
        R.string.schedule_week_teacher_no_default, R.string.schedule_week_teacher_value_label)
}

/**
 * Pick weeks first, then say what they should show. Changes stay local until Save; cancelling never
 * edits the parent course draft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCourseValueDialog(
    weeks: List<Int>, courseTitle: String, defaultValue: String, overrides: Map<Int, String>,
    onConfirm: (Map<Int, String>) -> Unit, onDismiss: () -> Unit, suggestions: List<String>,
    @StringRes titleRes: Int, @StringRes defaultRes: Int, @StringRes noDefaultRes: Int,
    @StringRes valueLabelRes: Int,
) {
    val context = LocalContext.current
    val sorted = remember(weeks) { weeks.filter { it > 0 }.distinct().sorted() }
    var draftRaw by rememberSaveable { mutableStateOf(encodeWeekLocations(overrides)) }
    val draft = remember(draftRaw) { decodeWeekLocations(draftRaw) }
    val defaultTrimmed = defaultValue.trim()
    var selectedRaw by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val selected = selectedRaw.toSet()
    var value by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(WeekValueFilter.All) }
    val visible = sorted.filter { filter.shows(it, draft) }
    val defaultLabel = stringResource(R.string.schedule_week_values_default)
    val blankLabel = stringResource(R.string.schedule_week_values_blank)
    val options = remember(suggestions, defaultValue, draft) {
        (suggestions + defaultValue + draft.values).map(String::trim).filter(String::isNotEmpty).distinct().map { CourseReuseOption(it) }
    }
    // Selecting loads what those weeks show now, so the field always describes the selection.
    fun select(next: Set<Int>) {
        selectedRaw = sorted.filter { it in next }
        value = commonWeekValue(draft, next, defaultTrimmed).orEmpty()
    }
    fun commit(next: Map<Int, String>) {
        draftRaw = encodeWeekLocations(next)
        select(emptySet())
    }
    val applied = batchWeekValues(draft, sorted, selected, value, defaultTrimmed)
    val odd = visible.filter { it % 2 == 1 }.toSet()
    val even = visible.filter { it % 2 == 0 }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("course-week-values-dialog"),
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = listOf(
                        courseTitle.ifBlank { stringResource(R.string.schedule_week_location_untitled_course) },
                        if (defaultTrimmed.isEmpty()) stringResource(noDefaultRes) else stringResource(defaultRes, defaultTrimmed),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WeekValueFilter.entries.forEachIndexed { index, option ->
                        val count = sorted.count { option.shows(it, draft) }
                        SegmentedButton(
                            selected = filter == option,
                            onClick = {
                                filter = option
                                // Hidden weeks drop out of the selection so edits only touch what is on screen.
                                select(selected.filterTo(mutableSetOf()) { option.shows(it, draft) })
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, WeekValueFilter.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            icon = {},
                            modifier = Modifier.testTag("week-filter-${option.name.lowercase()}"),
                        ) {
                            Text(stringResource(option.labelRes, count), maxLines = 1)
                        }
                    }
                }
                Text(
                    stringResource(R.string.schedule_week_values_pick_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(selected = selected.isNotEmpty() && selected == visible.toSet(), onClick = { select(visible.toSet()) },
                        label = { Text(stringResource(R.string.schedule_week_values_select_all)) }, modifier = Modifier.testTag("week-pick-all"))
                    if (odd.isNotEmpty() && even.isNotEmpty()) {
                        AppFilterChip(selected = selected.isNotEmpty() && selected == odd, onClick = { select(odd) },
                            label = { Text(stringResource(R.string.schedule_week_batch_odd)) }, modifier = Modifier.testTag("week-pick-odd"))
                        AppFilterChip(selected = selected.isNotEmpty() && selected == even, onClick = { select(even) },
                            label = { Text(stringResource(R.string.schedule_week_batch_even)) }, modifier = Modifier.testTag("week-pick-even"))
                    }
                    if (selected.isNotEmpty()) {
                        AppFilterChip(selected = false, onClick = { select(emptySet()) },
                            label = { Text(stringResource(R.string.schedule_week_values_select_none)) }, modifier = Modifier.testTag("week-pick-none"))
                    }
                }
                if (visible.isEmpty()) {
                    Text(
                        stringResource(
                            if (filter == WeekValueFilter.Custom) R.string.schedule_week_values_none_custom
                            else R.string.schedule_week_values_none_default,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                } else WeekSelectionGrid(
                    weeks = visible,
                    selected = selected,
                    onChange = ::select,
                    customized = draft.keys,
                    cellLabel = { week -> draft[week]?.trim()?.takeIf(String::isNotEmpty) ?: defaultLabel },
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    if (selected.isEmpty()) {
                        Text(
                            stringResource(R.string.schedule_week_values_nothing_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
                        )
                    } else {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.schedule_week_values_selected, context.weekRangesText(compactWeekRanges(selectedRaw))),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val mixed = commonWeekValue(draft, selected, defaultTrimmed) == null
                            CourseReuseTextField(
                                value = value,
                                onChange = { value = it },
                                label = stringResource(valueLabelRes),
                                options = options,
                                tag = "week-batch-value",
                                placeholder = if (mixed) stringResource(R.string.schedule_week_values_mixed) else null,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                                AppOutlinedButton(
                                    onClick = { commit(batchWeekValues(draft, sorted, selected, "", defaultTrimmed)) },
                                    enabled = selected.any { it in draft },
                                    modifier = Modifier.testTag("week-batch-default"),
                                ) { Text(stringResource(R.string.schedule_week_values_reset)) }
                                androidx.compose.material3.Button(
                                    onClick = { commit(applied) },
                                    enabled = applied != draft,
                                    modifier = Modifier.testTag("week-batch-apply"),
                                ) { Text(stringResource(R.string.schedule_week_values_apply)) }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.schedule_week_values_overview),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                groupWeeksByValue(draft, sorted, defaultTrimmed).forEach { (shown, groupWeeks) ->
                    val isDefault = groupWeeks.none { it in draft }
                    Surface(
                        onClick = {
                            filter = WeekValueFilter.All
                            select(groupWeeks.toSet())
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDefault) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().testTag("week-value-group-${groupWeeks.first()}"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    shown.ifEmpty { blankLabel },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    context.weekRangesText(compactWeekRanges(groupWeeks)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isDefault) {
                                Text(
                                    defaultLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onConfirm(draft) }, modifier = Modifier.testTag("week-batch-save")) {
                Text(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

/** Which weeks the per-week grid lists; every week of the course stays one tap away under All. */
private enum class WeekValueFilter(@StringRes val labelRes: Int) {
    All(R.string.schedule_week_values_filter_all),
    Custom(R.string.schedule_week_values_filter_custom),
    Default(R.string.schedule_week_values_filter_default);

    fun shows(week: Int, overrides: Map<Int, String>): Boolean = when (this) {
        All -> true
        Custom -> week in overrides
        Default -> week !in overrides
    }
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
    weekTitles: Map<Int, String> = emptyMap(),
    existing: CourseItem? = null,
    clock: Pair<Int, Int>? = null,
    appearance: ItemAppearance = ItemAppearance(),
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
            weekTitles = weekTitles,
            reminderStartTime = keptClock(existing, clockStart, existing.reminderStartTime),
            reminderEndTime = keptClock(existing, clockEnd, existing.reminderEndTime),
            colorArgb = appearance.fill,
            colorEndArgb = appearance.fillEnd,
            borderArgb = appearance.border,
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
        weekTitles = weekTitles,
        reminderStartTime = clockStart,
        reminderEndTime = clockEnd,
        colorArgb = appearance.fill,
        colorEndArgb = appearance.fillEnd,
        borderArgb = appearance.border,
    )
}

/** Placeholders keep their times; leaving clock mode clears typed times but not lone imported ones. */
private fun keptClock(existing: CourseItem, typed: String?, stored: String?): String? = when {
    existing.reminderOnly -> stored
    typed != null -> typed
    existing.typedClock() != null -> null
    else -> stored
}
