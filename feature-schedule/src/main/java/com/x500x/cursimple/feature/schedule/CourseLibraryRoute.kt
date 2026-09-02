package com.x500x.cursimple.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences

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
        modifier = modifier,
    )
}
