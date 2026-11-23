package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.hiddenCourses
import java.time.LocalDate

@Composable
fun CourseLibraryRoute(
    viewModel: ScheduleViewModel,
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    termStartDate: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries = remember(state.schedule, state.manualCourses) {
        val hiddenIds = state.manualCourses.filter { it.hidden }.mapTo(hashSetOf()) { it.id }
        buildCourseLibrary(
            pluginCourses = state.schedule?.dailySchedules.orEmpty().flatMap { day ->
                day.courses.filterNot { it.id in hiddenIds }
            },
            manualCourses = state.manualCourses,
        )
    }
    val hiddenCourses = remember(state.manualCourses) { state.manualCourses.hiddenCourses() }
    var reminderTarget by remember { mutableStateOf<CourseItem?>(null) }

    reminderTarget?.let { course ->
        CourseReminderDialog(
            course = course,
            defaultAdvanceMinutes = if (course.category == CourseCategory.Exam) 40 else 20,
            onDismiss = { reminderTarget = null },
            onConfirm = { advance, ringtone ->
                viewModel.createReminderForCourse(course.id, advance, ringtone)
                reminderTarget = null
            },
        )
    }
    val columnDayOfWeeks = remember(
        scheduleDisplay.weekStartDay,
        scheduleDisplay.weekendVisible,
        scheduleDisplay.saturdayVisible,
    ) {
        visibleColumnDayOfWeeks(scheduleDisplay)
    }
    val maxNodeCount = state.timingProfile?.slotTimes?.maxOfOrNull { it.endNode }
        ?: maxOf(8, entries.maxOfOrNull { it.course.time.endNode } ?: 0)

    CourseLibraryScreen(
        entries = entries,
        columnDayOfWeeks = columnDayOfWeeks,
        onAddCourses = viewModel::addManualCourses,
        onUpdateCourse = viewModel::updateManualCourse,
        onRemoveCourse = viewModel::removeManualCourse,
        onRemoveCourses = { viewModel.removeLibraryCourses(state.eventLibrary.termId, it) },
        maxNodeCount = maxNodeCount,
        maxWeekCount = maxWeekCount,
        termStartDate = termStartDate,
        slotTimes = state.timingProfile?.slotTimes.orEmpty(),
        hiddenCourses = hiddenCourses,
        onRestoreCourse = viewModel::restoreHiddenCourse,
        onPurgeCourse = viewModel::purgeHiddenCourse,
        deletedCourses = state.deletedCourses,
        onRestoreDeleted = viewModel::restoreDeletedCourse,
        onClearRecycleBin = viewModel::clearDeletedCourses,
        onSetReminder = { reminderTarget = it },
        events = state.events,
        onSaveEvent = viewModel::saveEvent,
        onRemoveEvent = viewModel::removeEvent,
        onRemoveEvents = { viewModel.removeLibraryEvents(state.eventLibrary.termId, it) },
        onReorderEvents = { viewModel.reorderLibraryEvents(state.eventLibrary.termId, it) },
        eventCustomOrder = state.eventLibrary.hasCustomOrder,
        eventOrderSaving = state.eventLibraryReordering,
        libraryTermId = state.eventLibrary.termId,
        modifier = modifier,
    )
}
