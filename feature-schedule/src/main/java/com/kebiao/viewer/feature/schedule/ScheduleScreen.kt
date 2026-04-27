package com.kebiao.viewer.feature.schedule

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.plugin.ui.BannerContribution
import com.kebiao.viewer.core.plugin.ui.CourseBadgeRule
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.feature.plugin.PluginWebSessionScreen
import java.util.Calendar

@Composable
fun ScheduleRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
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
        onSelectCourse = viewModel::selectCourse,
        onSelectTimeSlot = viewModel::selectTimeSlot,
        onClearSelection = viewModel::clearSelection,
        onCreateReminder = viewModel::createReminderForSelection,
        onRemoveReminderRule = viewModel::removeReminderRule,
        onOpenPluginMarket = onOpenPluginMarket,
        onCompleteWebSession = viewModel::completeWebSession,
        onCancelWebSession = viewModel::cancelWebSession,
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
    onSelectCourse: (String) -> Unit,
    onSelectTimeSlot: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onCreateReminder: (Int, String?) -> Unit,
    onRemoveReminderRule: (String) -> Unit,
    onOpenPluginMarket: () -> Unit,
    onCompleteWebSession: (com.kebiao.viewer.core.plugin.web.WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayCourses = state.schedule?.coursesOfDay(todayDayOfWeek()).orEmpty()
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtoneUri = uri?.toString()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("课表查看 V2", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onOpenPluginMarket) {
                        Text("插件市场")
                    }
                }
            }
            item {
                Text("已安装插件", style = MaterialTheme.typography.titleMedium)
            }
            items(state.installedPlugins, key = { it.pluginId }) { plugin ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (plugin.pluginId == state.pluginId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("${plugin.name} (${plugin.version})", style = MaterialTheme.typography.titleSmall)
                        Text("发布者：${plugin.publisher}")
                        Button(onClick = { onPluginIdChange(plugin.pluginId) }) {
                            Text(if (plugin.pluginId == state.pluginId) "当前插件" else "切换到此插件")
                        }
                    }
                }
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
                Button(
                    onClick = onSyncClick,
                    enabled = !state.isSyncing,
                ) {
                    Text(if (state.isSyncing) "同步中..." else "同步课表")
                }
            }
            item {
                Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            item {
                PluginBannerSection(state.uiSchema)
            }
            if (state.messages.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            state.messages.forEach { Text(it) }
                        }
                    }
                }
            }
            if (state.alarmRecommendations.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("插件提醒建议", style = MaterialTheme.typography.titleSmall)
                            state.alarmRecommendations.forEach { recommendation ->
                                Text("建议提前 ${recommendation.advanceMinutes} 分钟：${recommendation.note}")
                            }
                        }
                    }
                }
            }
            if (state.selectionState != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("创建提醒", style = MaterialTheme.typography.titleSmall)
                            Text(selectionSummary(state.selectionState))
                            OutlinedTextField(
                                value = advanceMinutesText,
                                onValueChange = { advanceMinutesText = it.filter(Char::isDigit) },
                                label = { Text("提前分钟数") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        ringtoneLauncher.launch(
                                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    RingtoneManager.TYPE_ALARM,
                                                )
                                            },
                                        )
                                    },
                                ) {
                                    Text("选择铃声")
                                }
                                Button(
                                    onClick = {
                                        onCreateReminder(advanceMinutesText.toIntOrNull() ?: 20, ringtoneUri)
                                    },
                                ) {
                                    Text("保存提醒规则")
                                }
                                TextButton(onClick = onClearSelection) {
                                    Text("取消")
                                }
                            }
                            if (!ringtoneUri.isNullOrBlank()) {
                                Text("已选择铃声：$ringtoneUri", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Text("今日课程", style = MaterialTheme.typography.titleMedium)
                TodayCourseSection(
                    todayCourses = todayCourses,
                    uiSchema = state.uiSchema,
                    reminderRules = state.reminderRules,
                    onSelectCourse = onSelectCourse,
                    onSelectTimeSlot = onSelectTimeSlot,
                )
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
                                    CourseCard(
                                        course = course,
                                        uiSchema = state.uiSchema,
                                        hasReminder = hasReminderForCourse(course, state.reminderRules),
                                        onSelectCourse = onSelectCourse,
                                        onSelectTimeSlot = onSelectTimeSlot,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.reminderRules.isNotEmpty()) {
                item {
                    Text("提醒规则", style = MaterialTheme.typography.titleMedium)
                }
                items(state.reminderRules, key = { it.ruleId }) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("规则：${rule.scopeType.name}")
                                Text("提前：${rule.advanceMinutes} 分钟")
                                Text("铃声：${rule.ringtoneUri ?: "系统默认"}")
                            }
                            TextButton(onClick = { onRemoveReminderRule(rule.ruleId) }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        }

        state.pendingWebSession?.let { request ->
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                PluginWebSessionScreen(
                    request = request,
                    onFinish = onCompleteWebSession,
                    onCancel = onCancelWebSession,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PluginBannerSection(uiSchema: PluginUiSchema) {
    if (uiSchema.banners.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiSchema.banners.forEach { banner ->
            BannerCard(banner)
        }
    }
}

@Composable
private fun BannerCard(banner: BannerContribution) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(banner.title, style = MaterialTheme.typography.titleSmall)
            Text(banner.message)
        }
    }
}

@Composable
private fun TodayCourseSection(
    todayCourses: List<CourseItem>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    onSelectCourse: (String) -> Unit,
    onSelectTimeSlot: (Int, Int) -> Unit,
) {
    if (todayCourses.isEmpty()) {
        Text("今天没有课程安排。")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        todayCourses.forEach { course ->
            CourseCard(
                course = course,
                uiSchema = uiSchema,
                hasReminder = hasReminderForCourse(course, reminderRules),
                onSelectCourse = onSelectCourse,
                onSelectTimeSlot = onSelectTimeSlot,
            )
        }
    }
}

@Composable
private fun CourseCard(
    course: CourseItem,
    uiSchema: PluginUiSchema,
    hasReminder: Boolean,
    onSelectCourse: (String) -> Unit,
    onSelectTimeSlot: (Int, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(course.title, style = MaterialTheme.typography.titleSmall)
            Text(courseLine(course), style = MaterialTheme.typography.bodySmall)
            val badges = badgesForCourse(course, uiSchema.courseBadges)
            if (badges.isNotEmpty()) {
                Text("标签：${badges.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (hasReminder) "已存在提醒规则" else "未设置提醒", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSelectCourse(course.id) }) {
                    Text("本课提醒")
                }
                TextButton(onClick = { onSelectTimeSlot(course.time.startNode, course.time.endNode) }) {
                    Text("同节次提醒")
                }
            }
        }
    }
}

private fun badgesForCourse(course: CourseItem, rules: List<CourseBadgeRule>): List<String> {
    return rules.filter { rule ->
        (run {
            val titleContains = rule.titleContains
            titleContains == null || course.title.contains(titleContains, ignoreCase = true)
        }) &&
            (rule.dayOfWeek == null || course.time.dayOfWeek == rule.dayOfWeek) &&
            (rule.startNode == null || course.time.startNode == rule.startNode) &&
            (rule.endNode == null || course.time.endNode == rule.endNode)
    }.map { it.label }
}

private fun hasReminderForCourse(
    course: CourseItem,
    rules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
): Boolean {
    return rules.any { rule ->
        when (rule.scopeType) {
            com.kebiao.viewer.core.reminder.model.ReminderScopeType.SingleCourse -> rule.courseId == course.id
            com.kebiao.viewer.core.reminder.model.ReminderScopeType.TimeSlot -> {
                rule.startNode == course.time.startNode && rule.endNode == course.time.endNode
            }
        }
    }
}

private fun selectionSummary(selectionState: ScheduleSelectionState): String {
    return when (selectionState) {
        is ScheduleSelectionState.SingleCourse -> "单课提醒：${selectionState.courseId}"
        is ScheduleSelectionState.TimeSlot -> "横向提醒：第 ${selectionState.startNode}-${selectionState.endNode} 节"
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
