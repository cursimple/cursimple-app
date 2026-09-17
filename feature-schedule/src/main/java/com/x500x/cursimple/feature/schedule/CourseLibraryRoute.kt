package com.x500x.cursimple.feature.schedule

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

/** 课程管理页与 ScheduleViewModel 的接线。 */
@Composable
fun CourseLibraryRoute(
    viewModel: ScheduleViewModel,
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    maxWeekCount: Int = 30,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries = remember(state.schedule, state.manualCourses) {
        buildCourseLibrary(
            pluginCourses = state.schedule?.dailySchedules.orEmpty().flatMap { it.courses },
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
    // 节次上限跟随当前作息，作息未设置时退回表单默认值
    val maxNodeCount = state.timingProfile?.slotTimes?.maxOfOrNull { it.endNode } ?: 12

    CourseLibraryScreen(
        entries = entries,
        columnDayOfWeeks = columnDayOfWeeks,
        onAddCourse = viewModel::addManualCourse,
        onUpdateCourse = viewModel::updateManualCourse,
        onRemoveCourse = viewModel::removeManualCourse,
        maxNodeCount = maxNodeCount,
        maxWeekCount = maxWeekCount,
        hiddenCourses = hiddenCourses,
        onRestoreCourse = viewModel::restoreHiddenCourse,
        onSetReminder = { reminderTarget = it },
        modifier = modifier,
    )
}
