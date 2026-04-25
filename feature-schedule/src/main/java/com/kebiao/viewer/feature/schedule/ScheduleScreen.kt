package com.kebiao.viewer.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import java.util.Calendar

@Composable
fun ScheduleRoute(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPluginIdChange = viewModel::onPluginIdChange,
        onTermIdChange = viewModel::onTermIdChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onSyncClick = viewModel::syncSchedule,
        modifier = modifier,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPluginIdChange: (String) -> Unit,
    onTermIdChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayCourses = state.schedule?.coursesOfDay(todayDayOfWeek()).orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("课表查看", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(
                value = state.pluginId,
                onValueChange = onPluginIdChange,
                label = { Text("插件 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("教务系统 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.termId,
                onValueChange = onTermIdChange,
                label = { Text("学期 ID（如 2026-spring）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text("学号 / 账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onSyncClick,
                    enabled = !state.isSyncing,
                ) {
                    Text(if (state.isSyncing) "同步中..." else "同步课表")
                }
                Text(
                    text = state.statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp),
                )
            }
        }
        item {
            Text("今日课程", style = MaterialTheme.typography.titleMedium)
            TodayCourseSection(todayCourses = todayCourses)
        }
        item {
            Text("全周课程", style = MaterialTheme.typography.titleMedium)
        }
        state.schedule?.dailySchedules.orEmpty().forEach { day ->
            item(key = day.dayOfWeek) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = dayLabel(day.dayOfWeek),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (day.courses.isEmpty()) {
                            Text("无课程")
                        } else {
                            day.courses.forEach { course ->
                                Text(text = courseLine(course))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCourseSection(todayCourses: List<CourseItem>) {
    if (todayCourses.isEmpty()) {
        Text("今天没有课程安排。")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        todayCourses.forEach { course ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(course.title, style = MaterialTheme.typography.titleSmall)
                    Text(courseLine(course), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun courseLine(course: CourseItem): String {
    return "${course.time.startNode}-${course.time.endNode}节 · ${course.location.ifBlank { "待定教室" }} · ${course.teacher.ifBlank { "任课教师待定" }}"
}

private fun todayDayOfWeek(): Int {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return when (day) {
        Calendar.SUNDAY -> 7
        else -> day - 1
    }
}

private fun dayLabel(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "未知"
    }
}

