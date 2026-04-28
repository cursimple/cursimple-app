package com.kebiao.viewer.feature.schedule

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.plugin.ui.BannerContribution
import com.kebiao.viewer.core.plugin.ui.CourseBadgeRule
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.feature.plugin.PluginWebSessionScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

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
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSyncSettings by rememberSaveable { mutableStateOf(state.schedule == null) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedWeek = remember(state.timingProfile, weekOffset) { buildWeekModel(state.timingProfile, weekOffset) }
    val visibleWeekNumber = displayedWeek.weekIndex
    val horizontalScrollState = rememberScrollState()
    val scrollState = rememberScrollState()
    val selectedCourse = remember(state.selectionState, state.schedule) {
        selectedCourseFromState(state.selectionState, state.schedule)
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtoneUri = uri?.toString()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScheduleHeroSection(
                week = displayedWeek,
                schedule = state.schedule,
                statusMessage = state.statusMessage,
                onPreviousWeek = { weekOffset -= 1 },
                onNextWeek = { weekOffset += 1 },
                onResetWeek = { weekOffset = 0 },
            )

            if (state.installedPlugins.isEmpty()) {
                EmptyPluginCard(onOpenPluginMarket = onOpenPluginMarket)
            } else {
                PluginSelectorCard(
                    plugins = state.installedPlugins,
                    selectedPluginId = state.pluginId,
                    onPluginIdChange = onPluginIdChange,
                    onOpenPluginMarket = onOpenPluginMarket,
                )
            }

            SyncEntryCard(
                showSyncSettings = showSyncSettings,
                username = state.username,
                termId = state.termId,
                pluginId = state.pluginId,
                isSyncing = state.isSyncing,
                onToggle = { showSyncSettings = !showSyncSettings },
            )

            if (showSyncSettings) {
                SyncSettingsCard(
                    baseUrl = state.baseUrl,
                    termId = state.termId,
                    username = state.username,
                    password = state.password,
                    isSyncing = state.isSyncing,
                    onBaseUrlChange = onBaseUrlChange,
                    onTermIdChange = onTermIdChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onSyncClick = onSyncClick,
                    onOpenPluginMarket = onOpenPluginMarket,
                )
            }

            PluginBannerSection(state.uiSchema)

            if (state.messages.isNotEmpty()) {
                MessageCard(
                    title = "插件消息",
                    lines = state.messages,
                )
            }

            if (state.alarmRecommendations.isNotEmpty()) {
                MessageCard(
                    title = "提醒建议",
                    lines = state.alarmRecommendations.map { "建议提前 ${it.advanceMinutes} 分钟：${it.note}" },
                )
            }

            WeeklyScheduleSection(
                schedule = state.schedule,
                timingProfile = state.timingProfile,
                uiSchema = state.uiSchema,
                reminderRules = state.reminderRules,
                week = displayedWeek,
                visibleWeekNumber = visibleWeekNumber,
                horizontalScrollState = horizontalScrollState,
                onSelectCourse = onSelectCourse,
            )

            if (state.selectionState != null) {
                ReminderComposerCard(
                    selectedCourse = selectedCourse,
                    selectionState = state.selectionState,
                    advanceMinutesText = advanceMinutesText,
                    ringtoneUri = ringtoneUri,
                    onAdvanceMinutesChange = { advanceMinutesText = it.filter(Char::isDigit) },
                    onPickRingtone = {
                        ringtoneLauncher.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_ALARM,
                                )
                            },
                        )
                    },
                    onCreateReminder = {
                        onCreateReminder(advanceMinutesText.toIntOrNull() ?: 20, ringtoneUri)
                    },
                    onSelectSameSlot = {
                        selectedCourse?.let { course ->
                            onSelectTimeSlot(course.time.startNode, course.time.endNode)
                        }
                    },
                    onClearSelection = onClearSelection,
                )
            }

            if (state.reminderRules.isNotEmpty()) {
                ReminderRulesSection(
                    reminderRules = state.reminderRules,
                    onRemoveReminderRule = onRemoveReminderRule,
                )
            }
        }

        state.pendingWebSession?.let { request ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9000000))
                    .padding(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
}

@Composable
private fun ScheduleHeroSection(
    week: WeekModel,
    schedule: TermSchedule?,
    statusMessage: String?,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onResetWeek: () -> Unit,
) {
    val today = LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/M/d") }
    val weekdayLabel = chineseWeekday(today.dayOfWeek)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatter.format(today),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "第 ${week.weekIndex} 周  $weekdayLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WeekNavigator(
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onResetWeek = onResetWeek,
                )
            }

            Text(
                text = statusMessage ?: if (schedule == null) "还没有课表数据，先连接插件同步一次。" else "已切换到周视图，点课程块可直接创建提醒。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekNavigator(
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onResetWeek: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniActionButton(label = "上周", onClick = onPreviousWeek)
        MiniActionButton(label = "本周", onClick = onResetWeek)
        MiniActionButton(label = "下周", onClick = onNextWeek)
    }
}

@Composable
private fun MiniActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EmptyPluginCard(
    onOpenPluginMarket: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("还没有可用插件", style = MaterialTheme.typography.titleLarge)
            Text(
                "课表同步依赖插件工作流，先去插件页安装或导入一个学校插件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenPluginMarket) {
                Text("打开插件页")
            }
        }
    }
}

@Composable
private fun PluginSelectorCard(
    plugins: List<com.kebiao.viewer.core.plugin.install.InstalledPluginRecord>,
    selectedPluginId: String,
    onPluginIdChange: (String) -> Unit,
    onOpenPluginMarket: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已安装插件", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenPluginMarket) {
                    Text("管理插件")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                plugins.forEach { plugin ->
                    SelectablePill(
                        title = plugin.name,
                        subtitle = plugin.version,
                        selected = plugin.pluginId == selectedPluginId,
                        onClick = { onPluginIdChange(plugin.pluginId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectablePill(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = contentColor, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = contentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SyncEntryCard(
    showSyncSettings: Boolean,
    username: String,
    termId: String,
    pluginId: String,
    isSyncing: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("同步设置", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = buildList {
                        add(if (username.isBlank()) "未填写账号" else username)
                        add(if (pluginId.isBlank()) "未选插件" else pluginId)
                        add(termId)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(if (isSyncing) "同步中" else if (showSyncSettings) "收起" else "展开")
            }
        }
    }
}

@Composable
private fun SyncSettingsCard(
    baseUrl: String,
    termId: String,
    username: String,
    password: String,
    isSyncing: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onTermIdChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onOpenPluginMarket: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("教务系统 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = termId,
                onValueChange = onTermIdChange,
                label = { Text("学期 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("学号 / 账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                ) {
                    Text(if (isSyncing) "同步中..." else "同步课表")
                }
                TextButton(onClick = onOpenPluginMarket) {
                    Text("去插件页")
                }
            }
        }
    }
}

@Composable
private fun PluginBannerSection(uiSchema: PluginUiSchema) {
    if (uiSchema.banners.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        uiSchema.banners.forEach { banner ->
            BannerCard(banner)
        }
    }
}

@Composable
private fun BannerCard(banner: BannerContribution) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(banner.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                banner.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            lines.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyScheduleSection(
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    week: WeekModel,
    visibleWeekNumber: Int,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onSelectCourse: (String) -> Unit,
) {
    val slots = remember(schedule, timingProfile) { displaySlots(schedule, timingProfile) }
    val courses = remember(schedule, visibleWeekNumber) {
        schedule
            ?.dailySchedules
            .orEmpty()
            .flatMap { it.courses }
            .filter { it.weeks.isEmpty() || it.weeks.contains(visibleWeekNumber) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7ECF8)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "课表",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF171A23),
                fontWeight = FontWeight.Bold,
            )
            if (slots.isEmpty() || courses.isEmpty()) {
                EmptyWeekState(schedule = schedule)
            } else {
                ScheduleGrid(
                    week = week,
                    slots = slots,
                    courses = courses,
                    uiSchema = uiSchema,
                    reminderRules = reminderRules,
                    horizontalScrollState = horizontalScrollState,
                    onSelectCourse = onSelectCourse,
                )
            }
        }
    }
}

@Composable
private fun EmptyWeekState(schedule: TermSchedule?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (schedule == null) "还没有同步到课表" else "这一周没有课程安排",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF171A23),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (schedule == null) "展开上方同步设置，连接插件后就能看到周视图。" else "可以切换其他周，或者继续同步最新学期数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5E6475),
        )
    }
}

@Composable
private fun ScheduleGrid(
    week: WeekModel,
    slots: List<DisplaySlot>,
    courses: List<CourseItem>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onSelectCourse: (String) -> Unit,
) {
    val timeColumnWidth = 70.dp
    val dayColumnWidth = 102.dp
    val slotHeight = 126.dp
    val gridWidth = dayColumnWidth * 7
    val gridHeight = slotHeight * slots.size

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Spacer(modifier = Modifier.width(timeColumnWidth))
            Row(
                modifier = Modifier.horizontalScroll(horizontalScrollState),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                week.days.forEach { day ->
                    DayHeader(day = day, width = dayColumnWidth)
                }
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(timeColumnWidth),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                slots.forEachIndexed { index, slot ->
                    TimeCell(
                        slot = slot,
                        height = if (index == 0) slotHeight - 4.dp else slotHeight,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFFDDE5F6)),
            ) {
                Box(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight),
                ) {
                    Column {
                        slots.forEach {
                            Row {
                                repeat(7) {
                                    Box(
                                        modifier = Modifier
                                            .width(dayColumnWidth)
                                            .height(slotHeight)
                                            .border(
                                                BorderStroke(1.dp, Color(0xFFC9D4EA)),
                                                RoundedCornerShape(0.dp),
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    courses.forEach { course ->
                        val placement = remember(course, slots) { coursePlacement(course, slots) } ?: return@forEach
                        CourseBlock(
                            course = course,
                            badges = badgesForCourse(course, uiSchema.courseBadges),
                            hasReminder = hasReminderForCourse(course, reminderRules),
                            width = dayColumnWidth - 10.dp,
                            height = max(slotHeight.value.toInt() * placement.rowSpan - 10, 68).dp,
                            offsetX = dayColumnWidth * placement.dayIndex + 5.dp,
                            offsetY = slotHeight * placement.rowIndex + 5.dp,
                            onClick = { onSelectCourse(course.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: DayHeaderModel,
    width: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = day.monthLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF767D92),
        )
        Text(
            text = day.weekdayLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) Color(0xFF111318) else Color(0xFF656C7E),
        )
        Text(
            text = day.dateLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.isToday) Color(0xFF111318) else Color(0xFF8A91A4),
        )
    }
}

@Composable
private fun TimeCell(
    slot: DisplaySlot,
    height: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .height(height)
            .padding(top = 4.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = slot.indexLabel,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF151821),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = slot.startTime,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF646C7E),
            textAlign = TextAlign.Center,
        )
        Text(
            text = slot.endTime,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF646C7E),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CourseBlock(
    course: CourseItem,
    badges: List<String>,
    hasReminder: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val containerColor = remember(course.title) { courseColor(course.title) }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(0.dp)
            .offset(offsetX, offsetY)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = course.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = course.location.ifBlank { "待定教室" },
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (hasReminder) "已设提醒" else course.teacher.ifBlank { "点击设置提醒" },
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badges.isNotEmpty()) {
                Text(
                    text = badges.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReminderComposerCard(
    selectedCourse: CourseItem?,
    selectionState: ScheduleSelectionState,
    advanceMinutesText: String,
    ringtoneUri: String?,
    onAdvanceMinutesChange: (String) -> Unit,
    onPickRingtone: () -> Unit,
    onCreateReminder: () -> Unit,
    onSelectSameSlot: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("创建提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = when (selectionState) {
                    is ScheduleSelectionState.SingleCourse -> selectedCourse?.let { course ->
                        "${course.title} · 第 ${course.time.startNode}-${course.time.endNode} 节 · ${course.location.ifBlank { "待定教室" }}"
                    } ?: "单课提醒"

                    is ScheduleSelectionState.TimeSlot -> "同节次提醒：第 ${selectionState.startNode}-${selectionState.endNode} 节"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectionState is ScheduleSelectionState.SingleCourse && selectedCourse != null) {
                TextButton(onClick = onSelectSameSlot) {
                    Text("改为同节次提醒")
                }
            }
            OutlinedTextField(
                value = advanceMinutesText,
                onValueChange = onAdvanceMinutesChange,
                label = { Text("提前分钟数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPickRingtone) {
                    Text("选择铃声")
                }
                Button(onClick = onCreateReminder) {
                    Text("保存提醒")
                }
                TextButton(onClick = onClearSelection) {
                    Text("取消")
                }
            }
            if (!ringtoneUri.isNullOrBlank()) {
                Text(
                    text = "已选择铃声：$ringtoneUri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReminderRulesSection(
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    onRemoveReminderRule: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("提醒规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            reminderRules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("规则：${rule.scopeType.name}", fontWeight = FontWeight.Medium)
                        Text(
                            "提前 ${rule.advanceMinutes} 分钟 · ${rule.ringtoneUri ?: "系统默认铃声"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRemoveReminderRule(rule.ruleId) }) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

private data class DayHeaderModel(
    val monthLabel: String,
    val weekdayLabel: String,
    val dateLabel: String,
    val isToday: Boolean,
)

private data class WeekModel(
    val weekIndex: Int,
    val weekStart: LocalDate,
    val days: List<DayHeaderModel>,
)

private data class DisplaySlot(
    val startNode: Int,
    val endNode: Int,
    val indexLabel: String,
    val startTime: String,
    val endTime: String,
)

private data class CoursePlacement(
    val dayIndex: Int,
    val rowIndex: Int,
    val rowSpan: Int,
)

private fun buildWeekModel(
    timingProfile: TermTimingProfile?,
    weekOffset: Int,
): WeekModel {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
    val termStart = timingProfile?.termStartLocalDate()
    val termStartWeek = termStart?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekIndex = if (termStartWeek != null) {
        max(1, ChronoUnit.WEEKS.between(termStartWeek, weekStart).toInt() + 1)
    } else {
        1
    }
    val days = (0..6).map { index ->
        val date = weekStart.plusDays(index.toLong())
        DayHeaderModel(
            monthLabel = if (index == 0) "${date.monthValue}月" else "",
            weekdayLabel = chineseShortWeekday(date.dayOfWeek),
            dateLabel = date.dayOfMonth.toString(),
            isToday = date == today,
        )
    }
    return WeekModel(
        weekIndex = weekIndex,
        weekStart = weekStart,
        days = days,
    )
}

private fun displaySlots(
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
): List<DisplaySlot> {
    val profileSlots = timingProfile?.slotTimes.orEmpty().sortedBy { it.startNode }
    if (profileSlots.isNotEmpty()) {
        return profileSlots.map { it.toDisplaySlot() }
    }
    val derived = schedule?.dailySchedules.orEmpty()
        .flatMap { it.courses }
        .map { it.time.startNode to it.time.endNode }
        .distinct()
        .sortedBy { it.first }
    return derived.map { (startNode, endNode) ->
        DisplaySlot(
            startNode = startNode,
            endNode = endNode,
            indexLabel = if (startNode == endNode) "$startNode" else "$startNode-$endNode",
            startTime = "--:--",
            endTime = "--:--",
        )
    }
}

private fun ClassSlotTime.toDisplaySlot(): DisplaySlot {
    return DisplaySlot(
        startNode = startNode,
        endNode = endNode,
        indexLabel = if (startNode == endNode) "$startNode" else "$startNode-$endNode",
        startTime = startTime,
        endTime = endTime,
    )
}

private fun coursePlacement(
    course: CourseItem,
    slots: List<DisplaySlot>,
): CoursePlacement? {
    val dayIndex = course.time.dayOfWeek - 1
    if (dayIndex !in 0..6) {
        return null
    }
    val startIndex = slots.indexOfFirst { course.time.startNode in it.startNode..it.endNode }
    val endIndex = slots.indexOfFirst { course.time.endNode in it.startNode..it.endNode }
    if (startIndex == -1 || endIndex == -1) {
        return null
    }
    return CoursePlacement(
        dayIndex = dayIndex,
        rowIndex = startIndex,
        rowSpan = max(1, endIndex - startIndex + 1),
    )
}

private fun courseColor(seed: String): Color {
    val palette = listOf(
        Color(0xFFF07CA4),
        Color(0xFF74D8D0),
        Color(0xFFA88CFF),
        Color(0xFF6FA7E8),
        Color(0xFFF0B36C),
        Color(0xFFEF8D83),
        Color(0xFF77C57D),
    )
    return palette[seed.hashCode().mod(palette.size)]
}

private fun badgesForCourse(course: CourseItem, rules: List<CourseBadgeRule>): List<String> {
    return rules.filter { rule ->
        ((rule.titleContains?.let { titleContains ->
            course.title.contains(titleContains, ignoreCase = true)
        } ?: true)) &&
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
            com.kebiao.viewer.core.reminder.model.ReminderScopeType.TimeSlot ->
                rule.startNode == course.time.startNode && rule.endNode == course.time.endNode
        }
    }
}

private fun selectedCourseFromState(
    selectionState: ScheduleSelectionState?,
    schedule: TermSchedule?,
): CourseItem? {
    val singleCourseId = (selectionState as? ScheduleSelectionState.SingleCourse)?.courseId ?: return null
    return schedule?.dailySchedules.orEmpty()
        .flatMap { it.courses }
        .firstOrNull { it.id == singleCourseId }
}

private fun chineseShortWeekday(dayOfWeek: DayOfWeek): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }
}

private fun chineseWeekday(dayOfWeek: DayOfWeek): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
