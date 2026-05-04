package com.kebiao.viewer.feature.schedule

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.plugin.ui.BannerContribution
import com.kebiao.viewer.core.plugin.ui.CourseBadgeRule
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.feature.schedule.time.LocalAppZone
import com.kebiao.viewer.feature.schedule.time.today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

enum class ScheduleViewMode { Week, Day }

@Composable
fun ScheduleRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
    weekOffset: Int,
    minWeekOffset: Int,
    maxWeekOffset: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overrideTermStart: LocalDate? = null,
    viewMode: ScheduleViewMode = ScheduleViewMode.Week,
    dayOffset: Int = 0,
    onPrevDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onResetDay: () -> Unit = {},
    totalScheduleDisplayEnabled: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(
        state = state,
        weekOffset = weekOffset,
        minWeekOffset = minWeekOffset,
        maxWeekOffset = maxWeekOffset,
        overrideTermStart = overrideTermStart,
        viewMode = viewMode,
        dayOffset = dayOffset,
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
        onRemoveManualCourse = viewModel::removeManualCourse,
        onAddManualCourse = viewModel::addManualCourse,
        onCreateBulkReminder = viewModel::createReminderForCourses,
        onPrevWeek = onPrevWeek,
        onNextWeek = onNextWeek,
        onWeekOffsetChange = onWeekOffsetChange,
        onPrevDay = onPrevDay,
        onNextDay = onNextDay,
        onResetDay = onResetDay,
        onOpenPluginMarket = onOpenPluginMarket,
        totalScheduleDisplayEnabled = totalScheduleDisplayEnabled,
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
    onRemoveManualCourse: (String) -> Unit,
    onAddManualCourse: (CourseItem) -> Unit = {},
    onCreateBulkReminder: (Set<String>, Int, String?) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetDay: () -> Unit,
    onOpenPluginMarket: () -> Unit,
    onWeekOffsetChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    weekOffset: Int = 0,
    minWeekOffset: Int = Int.MIN_VALUE / 2,
    maxWeekOffset: Int = Int.MAX_VALUE / 2,
    overrideTermStart: LocalDate? = null,
    viewMode: ScheduleViewMode = ScheduleViewMode.Week,
    dayOffset: Int = 0,
    totalScheduleDisplayEnabled: Boolean = true,
) {
    var showSyncSettings by rememberSaveable { mutableStateOf(state.schedule == null) }
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    var detailCourses by remember { mutableStateOf<List<CourseItem>>(emptyList()) }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkReminder by rememberSaveable { mutableStateOf(false) }
    val zone = LocalAppZone.current
    val displayedWeek = remember(state.timingProfile, weekOffset, overrideTermStart, zone) {
        buildWeekModel(weekOffset, overrideTermStart, zone)
    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            if (!state.initialized) {
                ScheduleInitializingState(modifier = Modifier.fillMaxSize())
            } else {
            val onCellClickHandler: (List<CourseItem>) -> Unit = { coursesAtCell ->
                if (multiSelectMode) {
                    val id = coursesAtCell.firstOrNull()?.id
                    if (id != null) {
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        if (selectedIds.isEmpty()) multiSelectMode = false
                    }
                } else {
                    detailCourses = coursesAtCell
                }
            }
            val onLongClickHandler: (String) -> Unit = { id ->
                multiSelectMode = true
                selectedIds = selectedIds + id
            }

            when (viewMode) {
                ScheduleViewMode.Week -> WeeklyScheduleSection(
                    modifier = Modifier.fillMaxSize(),
                    schedule = state.schedule,
                    manualCourses = state.manualCourses,
                    timingProfile = state.timingProfile,
                    uiSchema = state.uiSchema,
                    reminderRules = state.reminderRules,
                    weekOffset = weekOffset,
                    minWeekOffset = minWeekOffset,
                    maxWeekOffset = maxWeekOffset,
                    overrideTermStart = overrideTermStart,
                    zone = zone,
                    horizontalScrollState = horizontalScrollState,
                    selectedCourseId = (state.selectionState as? ScheduleSelectionState.SingleCourse)?.courseId,
                    multiSelectMode = multiSelectMode,
                    multiSelectedIds = selectedIds,
                    onCellClick = onCellClickHandler,
                    onCourseLongClick = onLongClickHandler,
                    onWeekOffsetChange = onWeekOffsetChange,
                    onAddManualCourse = onAddManualCourse,
                    totalScheduleDisplayEnabled = totalScheduleDisplayEnabled,
                )

                ScheduleViewMode.Day -> DailyScheduleSection(
                    modifier = Modifier.fillMaxSize(),
                    schedule = state.schedule,
                    manualCourses = state.manualCourses,
                    timingProfile = state.timingProfile,
                    uiSchema = state.uiSchema,
                    reminderRules = state.reminderRules,
                    targetDate = zone.today().plusDays(dayOffset.toLong()),
                    targetWeekNumber = computeWeekNumber(overrideTermStart, dayOffset, zone),
                    selectedCourseId = (state.selectionState as? ScheduleSelectionState.SingleCourse)?.courseId,
                    multiSelectMode = multiSelectMode,
                    multiSelectedIds = selectedIds,
                    dayOffset = dayOffset,
                    onCellClick = onCellClickHandler,
                    onCourseLongClick = onLongClickHandler,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                )
            }
            }
        }

        if (viewMode == ScheduleViewMode.Day && dayOffset != 0) {
            BackToTodayButton(
                onClick = onResetDay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
            )
        }

        if (multiSelectMode) {
            MultiSelectActionBar(
                selectedCount = selectedIds.size,
                onSetReminder = { showBulkReminder = true },
                onClear = {
                    multiSelectMode = false
                    selectedIds = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        if (showBulkReminder) {
            BulkReminderDialog(
                selectedCount = selectedIds.size,
                onDismiss = { showBulkReminder = false },
                onConfirm = { advance, ringtone ->
                    onCreateBulkReminder(selectedIds, advance, ringtone)
                    showBulkReminder = false
                    multiSelectMode = false
                    selectedIds = emptySet()
                },
            )
        }

        if (detailCourses.isNotEmpty()) {
            CourseDetailDialog(
                courses = detailCourses,
                timingProfile = state.timingProfile,
                visibleWeekNumber = visibleWeekNumber,
                isManual = { c -> state.manualCourses.any { it.id == c.id } },
                onDismiss = { detailCourses = emptyList() },
                onSetReminder = { c ->
                    onSelectCourse(c.id)
                    detailCourses = emptyList()
                },
                onDelete = { c ->
                    onRemoveManualCourse(c.id)
                    detailCourses = detailCourses.filterNot { it.id == c.id }
                },
            )
        }

    }
}

@Composable
private fun ScheduleHeroSection(
    week: WeekModel,
    schedule: TermSchedule?,
    hasPlugins: Boolean,
    selectedCourseTitle: String?,
    statusMessage: String?,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onResetWeek: () -> Unit,
) {
    val today = LocalAppZone.current.today()
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
                text = statusMessage ?: when {
                    !selectedCourseTitle.isNullOrBlank() -> "已选中 $selectedCourseTitle，去设置页创建提醒。"
                    schedule == null && !hasPlugins -> "还没有课表数据，先去插件页安装学校插件。"
                    schedule == null -> "还没有课表数据，去插件页的当前插件卡片里同步课表。"
                    else -> "这里现在只保留课表内容，点课程块后可去设置页创建提醒。"
                },
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
internal fun SyncSettingsCard(
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
internal fun PluginBannerSection(uiSchema: PluginUiSchema) {
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
internal fun MessageCard(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyScheduleSection(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    weekOffset: Int,
    minWeekOffset: Int,
    maxWeekOffset: Int,
    overrideTermStart: LocalDate?,
    zone: java.time.ZoneId,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    onAddManualCourse: (CourseItem) -> Unit = {},
    totalScheduleDisplayEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val slots = remember(schedule, timingProfile, manualCourses) {
        displaySlots(schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (slots.isEmpty() || allCourses.isEmpty()) {
                EmptyWeekState(schedule = schedule)
            } else {
                val safeMin = minWeekOffset.coerceAtMost(weekOffset)
                val safeMax = maxWeekOffset.coerceAtLeast(weekOffset)
                val pageCount = safeMax - safeMin + 1
                val initialPage = (weekOffset - safeMin).coerceIn(0, pageCount - 1)
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { pageCount },
                )
                val context = androidx.compose.ui.platform.LocalContext.current
                val lastEdgeToastAt = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                val edgeNestedScroll = androidx.compose.runtime.remember(pagerState, pageCount) {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: androidx.compose.ui.geometry.Offset,
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                        ): androidx.compose.ui.geometry.Offset {
                            if (kotlin.math.abs(available.x) < 0.5f) return androidx.compose.ui.geometry.Offset.Zero
                            val atStart = pagerState.currentPage == 0 && available.x > 0f
                            val atEnd = pagerState.currentPage == pageCount - 1 && available.x < 0f
                            if (atStart || atEnd) {
                                val now = System.currentTimeMillis()
                                if (now - lastEdgeToastAt.longValue > 1500L) {
                                    lastEdgeToastAt.longValue = now
                                    android.widget.Toast.makeText(
                                        context,
                                        if (atStart) "已经是最早一周" else "已经是最后一周",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                val pagerLatestRequest = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableIntStateOf(weekOffset)
                }
                androidx.compose.runtime.LaunchedEffect(weekOffset, safeMin, pageCount) {
                    if (weekOffset == pagerLatestRequest.intValue) return@LaunchedEffect
                    pagerLatestRequest.intValue = weekOffset
                    val target = (weekOffset - safeMin).coerceIn(0, pageCount - 1)
                    if (pagerState.currentPage != target) {
                        pagerState.animateScrollToPage(target)
                    }
                }
                androidx.compose.runtime.LaunchedEffect(pagerState, safeMin) {
                    androidx.compose.runtime.snapshotFlow {
                        if (pagerState.isScrollInProgress) pagerState.targetPage
                        else pagerState.currentPage
                    }.collect { page ->
                        val newOffset = page + safeMin
                        if (newOffset != pagerLatestRequest.intValue) {
                            pagerLatestRequest.intValue = newOffset
                            onWeekOffsetChange(newOffset)
                        }
                    }
                }

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(edgeNestedScroll),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val pageOffset = page + safeMin
                    val pageWeek = remember(timingProfile, pageOffset, overrideTermStart, zone) {
                        buildWeekModel(pageOffset, overrideTermStart, zone)
                    }
                    val active = remember(allCourses, slots, pageWeek.weekIndex, totalScheduleDisplayEnabled) {
                        buildWeekRenderEntries(
                            allCourses = allCourses,
                            slots = slots,
                            weekIndex = pageWeek.weekIndex,
                            totalScheduleDisplayEnabled = totalScheduleDisplayEnabled,
                        )
                    }
                    if (active.isEmpty()) {
                        EmptyWeekState(schedule = schedule)
                    } else {
                        ScheduleGrid(
                            modifier = Modifier.fillMaxSize(),
                            week = pageWeek,
                            slots = slots,
                            activeEntries = active,
                            uiSchema = uiSchema,
                            reminderRules = reminderRules,
                            horizontalScrollState = horizontalScrollState,
                            selectedCourseId = selectedCourseId,
                            multiSelectMode = multiSelectMode,
                            multiSelectedIds = multiSelectedIds,
                            onCellClick = onCellClick,
                            onCourseLongClick = onCourseLongClick,
                            currentWeekIndex = pageWeek.weekIndex,
                            onAddManualCourse = onAddManualCourse,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyScheduleSection(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    targetDate: LocalDate,
    targetWeekNumber: Int,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    dayOffset: Int,
    onCellClick: (List<CourseItem>) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(schedule, timingProfile, manualCourses) {
        displaySlots(schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    }
    val today = LocalAppZone.current.today()
    val targetDayOfWeek = targetDate.dayOfWeek.value

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DailyHeaderRow(date = targetDate, isToday = targetDate == today)

            if (slots.isEmpty() || allCourses.isEmpty()) {
                EmptyWeekState(schedule = schedule)
                return@Column
            }

            AnimatedContent(
                targetState = dayOffset,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = tween(260)) { full -> full * direction } +
                        fadeIn(animationSpec = tween(260)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(260)) { full -> -full * direction } +
                                fadeOut(animationSpec = tween(260))
                        )
                },
                label = "day-list",
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                val active = allCourses
                    .filter { it.time.dayOfWeek == targetDayOfWeek }
                    .filter { it.isActiveInWeek(targetWeekNumber) }
                    .sortedBy { it.time.startNode }
                DayList(
                    slots = slots,
                    courses = active,
                    uiSchema = uiSchema,
                    reminderRules = reminderRules,
                    selectedCourseId = selectedCourseId,
                    multiSelectMode = multiSelectMode,
                    multiSelectedIds = multiSelectedIds,
                    onCellClick = onCellClick,
                    onCourseLongClick = onCourseLongClick,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                )
            }
        }
    }
}

@Composable
private fun DailyHeaderRow(date: LocalDate, isToday: Boolean) {
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accents.todayContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日",
                    color = accents.todayOnContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = chineseWeekday(date.dayOfWeek),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayList(
    slots: List<DisplaySlot>,
    courses: List<CourseItem>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    var dragAccumulated by remember { mutableStateOf(0f) }
    val swipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { dragAccumulated = 0f },
            onDragEnd = {
                when {
                    dragAccumulated > swipeThresholdPx -> onPrevDay()
                    dragAccumulated < -swipeThresholdPx -> onNextDay()
                }
                dragAccumulated = 0f
            },
            onDragCancel = { dragAccumulated = 0f },
        ) { _, delta -> dragAccumulated += delta }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(swipeModifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slots.forEach { slot ->
            val coursesInSlot = courses.filter { course ->
                course.time.startNode <= slot.endNode && course.time.endNode >= slot.startNode
            }
            // 只渲染从此 slot 起始的课程，避免跨节多次绘制
            val starting = coursesInSlot.filter { it.time.startNode in slot.startNode..slot.endNode }
            if (starting.isEmpty()) {
                return@forEach
            }
            DayRow(
                slot = slot,
                courses = starting,
                uiSchema = uiSchema,
                reminderRules = reminderRules,
                selectedCourseId = selectedCourseId,
                multiSelectMode = multiSelectMode,
                multiSelectedIds = multiSelectedIds,
                onCellClick = onCellClick,
                onCourseLongClick = onCourseLongClick,
            )
        }
        if (courses.isEmpty()) {
            Text(
                text = "今天没有安排的课程",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DayRow(
    slot: DisplaySlot,
    courses: List<CourseItem>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>) -> Unit,
    onCourseLongClick: (String) -> Unit,
) {
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = slot.label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = slotTimeRange(slot),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            courses.forEach { course ->
                val palette = courseColor(course.title, accents.coursePalette)
                val containerColor = palette.container
                val onColor = palette.onContainer
                val isSelected = course.id == selectedCourseId
                val isMultiSelected = course.id in multiSelectedIds
                val highlight = isSelected || isMultiSelected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(containerColor)
                        .border(
                            BorderStroke(if (highlight) 2.dp else 0.dp,
                                if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent),
                            RoundedCornerShape(10.dp),
                        )
                        .combinedClickable(
                            onClick = { onCellClick(listOf(course)) },
                            onLongClick = { onCourseLongClick(course.id) },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(onColor.copy(alpha = 0.9f)),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = course.title,
                            color = onColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (course.location.isNotBlank()) {
                            Text(
                                text = course.location,
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(end = 14.dp, top = 12.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "第${course.time.startNode}节",
                            color = onColor,
                            fontSize = 12.sp,
                        )
                        if (hasReminderForCourse(course, reminderRules)) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleInitializingState(modifier: Modifier = Modifier) {
    val tips = remember {
        listOf(
            "马力全开中…",
            "正在全力加载课表与提醒～",
            "嘿，先喝口水，马上就好",
            "悄悄告诉你：侧滑课表可以飞快翻周",
            "长按课程能批量加提醒，超方便",
            "侧边栏里有主题、时区、开学日期，去逛逛",
            "再等等，正在把课塞进格子里…",
        )
    }
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "init-loader")
    val tipIndex = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(tips.indices.random())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            tipIndex.intValue = (tipIndex.intValue + 1) % tips.size
        }
    }

    val cubeCount = 3
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(cubeCount) { index ->
                val phase = index * 0.18f
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = 800,
                            delayMillis = (phase * 800).toInt(),
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    ),
                    label = "cube-$index",
                )
                val translation = -16.dp * offset
                Box(
                    modifier = Modifier
                        .offset(y = translation)
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.5f * offset)),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = tipIndex.intValue,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(400),
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(400),
                ) { it / 4 }).togetherWith(
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) { -it / 4 },
                )
            },
            label = "init-tip",
        ) { idx ->
            Text(
                text = tips[idx],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun BackToTodayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = "切回今天",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun computeWeekNumber(
    termStart: LocalDate?,
    dayOffset: Int,
    zone: ZoneId,
): Int {
    val target = BeijingTime.todayIn(zone).plusDays(dayOffset.toLong())
    val termStartMonday = termStart?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetMonday = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return if (termStartMonday != null) {
        max(1, ChronoUnit.WEEKS.between(termStartMonday, targetMonday).toInt() + 1)
    } else {
        1
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
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (schedule == null) "去插件页同步课表，或去设置页管理提醒。" else "可以切换其他周，或者继续在插件页同步最新数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleGrid(
    week: WeekModel,
    slots: List<DisplaySlot>,
    activeEntries: List<CourseRenderEntry>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>) -> Unit,
    onCourseLongClick: (String) -> Unit,
    currentWeekIndex: Int = 1,
    onAddManualCourse: (CourseItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cellGroups = remember(activeEntries) {
        activeEntries
            .groupBy { it.placement.dayIndex to it.placement.rowIndex }
            .map { (_, list) ->
                val main = list.first()
                val sorted = list.map { it.course }.distinctBy { it.id }
                // 角标数字：以去重后的本周课程数为准，点击可展开查看同格课程
                Triple(main, sorted, sorted.size)
            }
    }
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current

    // Empty-cell tap-to-add overlay state. Hoisted to grid scope so the dialog can read it
    // outside the inner positional Box. Hint cell auto-clears after 2.5s.
    var hintCell by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null) }
    var addRequest by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Triple<Int, Int, Int>?>(null)
    }
    val occupiedCells = androidx.compose.runtime.remember(cellGroups) {
        buildSet {
            cellGroups.forEach { (entry, _, _) ->
                val p = entry.placement
                for (r in 0 until p.rowSpan) {
                    add(p.dayIndex to (p.rowIndex + r))
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(hintCell) {
        if (hintCell != null) {
            kotlinx.coroutines.delay(2500)
            hintCell = null
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        val timeColumnWidth = 60.dp
        val dayHeaderHeight = 52.dp
        val totalWidth = maxWidth
        val dayColumnWidth = ((totalWidth - timeColumnWidth) / 7).coerceAtLeast(36.dp)
        val gridWidth = dayColumnWidth * 7
        val slotHeight = 100.dp
        val gridHeight = slotHeight * slots.size

        Column {
            // 顶部周日期头
            Row(
                modifier = Modifier.height(dayHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthCornerCell(
                    monthLabel = week.days.firstOrNull()?.monthLabel.orEmpty(),
                    width = timeColumnWidth,
                )
                week.days.forEach { day ->
                    DayHeader(day = day, width = dayColumnWidth)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                ) {
                    slots.forEach { slot ->
                        TimeCell(slot = slot, height = slotHeight)
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = accents.gridBackground.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Box(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(gridHeight)
                            .drawBehind {
                                val lineColor = accents.gridLine.copy(alpha = 0.45f)
                                val strokeWidth = 0.5.dp.toPx()
                                for (i in 1 until slots.size) {
                                    val y = (slotHeight.toPx() * i)
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                    )
                                }
                            }
                            .pointerInput(slots.size, dayColumnWidth, slotHeight, occupiedCells) {
                                detectTapGestures(
                                    onTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = (offset.x / dayWidthPx).toInt().coerceIn(0, 6)
                                        val slot = (offset.y / slotHeightPx).toInt().coerceIn(0, slots.size - 1)
                                        if ((day to slot) !in occupiedCells) {
                                            hintCell = day to slot
                                        }
                                    },
                                )
                            },
                    ) {

                        cellGroups.forEach { (mainEntry, sortedCourses, count) ->
                            val placement = mainEntry.placement
                            val course = mainEntry.course
                            val isMultiSelected = course.id in multiSelectedIds
                            val courseHeight = (slotHeight * placement.rowSpan) - 3.dp
                            CourseBlock(
                                course = course,
                                badges = badgesForCourse(course, uiSchema.courseBadges),
                                hasReminder = hasReminderForCourse(course, reminderRules),
                                selected = course.id == selectedCourseId,
                                inactive = mainEntry.inactive,
                                cellCount = count,
                                multiSelectMode = multiSelectMode,
                                multiSelected = isMultiSelected,
                                width = dayColumnWidth - 3.dp,
                                height = courseHeight,
                                offsetX = dayColumnWidth * placement.dayIndex + 1.5.dp,
                                offsetY = slotHeight * placement.rowIndex + 1.5.dp,
                                onClick = { onCellClick(sortedCourses) },
                                onLongClick = { onCourseLongClick(course.id) },
                            )
                        }

                        // Tap-hint overlay: translucent tint + central plus button. Keep the
                        // last cell around so we can animate it out via alpha rather than
                        // popping off the tree the moment the timer clears hintCell.
                        val lastHintCell = androidx.compose.runtime.remember { mutableStateOf<Pair<Int, Int>?>(null) }
                        androidx.compose.runtime.LaunchedEffect(hintCell) {
                            if (hintCell != null) lastHintCell.value = hintCell
                        }
                        val hintAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (hintCell != null) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                            label = "hintCellAlpha",
                        )
                        if (hintAlpha > 0.01f) {
                            lastHintCell.value?.let { (day, slotIdx) ->
                                val slot = slots.getOrNull(slotIdx)
                                if (slot != null) {
                                    Box(
                                        modifier = Modifier
                                            .width(dayColumnWidth - 3.dp)
                                            .height(slotHeight - 3.dp)
                                            .offset(
                                                x = dayColumnWidth * day + 1.5.dp,
                                                y = slotHeight * slotIdx + 1.5.dp,
                                            )
                                            .alpha(hintAlpha)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                shape = RoundedCornerShape(8.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable(enabled = hintCell != null) {
                                                    addRequest = Triple(day + 1, slot.startNode, slot.endNode)
                                                    hintCell = null
                                                },
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = "添加课程",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(22.dp),
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

        addRequest?.let { (day, startNode, endNode) ->
            QuickAddCourseDialog(
                dayOfWeek = day,
                startNode = startNode,
                endNode = endNode,
                initialWeek = currentWeekIndex,
                onDismiss = { addRequest = null },
                onConfirm = { course ->
                    onAddManualCourse(course)
                    addRequest = null
                },
            )
        }
    }
}

@Composable
private fun DayHeader(
    day: DayHeaderModel,
    width: androidx.compose.ui.unit.Dp,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current
    val columnModifier = Modifier
        .width(width)
        .padding(horizontal = 2.dp)
        .let {
            if (day.isToday) {
                it.clip(RoundedCornerShape(10.dp))
                    .background(accents.todayContainer)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            } else {
                it
            }
        }
    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = day.weekdayLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (day.isToday) accents.todayOnContainer else muted,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = day.dateLabel,
            fontSize = 12.sp,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) {
                accents.todayOnContainer
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun MonthCornerCell(
    monthLabel: String,
    width: androidx.compose.ui.unit.Dp,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(width)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (monthLabel.isNotBlank()) {
            val month = monthLabel.removeSuffix("月")
            Text(
                text = month,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "月",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = muted,
                maxLines = 1,
                softWrap = false,
            )
        }
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
            .padding(top = 4.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = slot.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = slotTimeRange(slot),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun slotTimeRange(slot: DisplaySlot): String {
    return if (slot.startTime.isBlank() && slot.endTime.isBlank()) {
        ""
    } else {
        "${slot.startTime}~${slot.endTime}"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CourseBlock(
    course: CourseItem,
    badges: List<String>,
    hasReminder: Boolean,
    selected: Boolean,
    inactive: Boolean,
    cellCount: Int,
    multiSelectMode: Boolean,
    multiSelected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, accents) { courseColor(course.title, accents.coursePalette) }
    val containerColor = if (inactive) accents.inactiveContainer else palette.container
    val onColor = if (inactive) accents.inactiveOnContainer else palette.onContainer
    val highlight = multiSelected || selected
    val borderColor = when {
        multiSelected -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderWidth = if (highlight) 2.dp else 0.dp

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .width(width)
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            // 左侧深色竖条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(onColor.copy(alpha = if (inactive) 0.4f else 0.9f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (inactive) {
                    Text(
                        text = "非本周",
                        color = onColor,
                        fontSize = 9.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = course.title,
                    color = onColor,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (course.location.isNotBlank()) {
                    Text(
                        text = "@${course.location}",
                        color = onColor.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 左下角响铃标识
        if (hasReminder && !inactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(13.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(onColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(9.dp),
                )
            }
        }

        // 左上角课程数角标（多个课程占同一格时显示，向外凸出避免遮挡课程名）
        if (cellCount > 1 && !(multiSelectMode && multiSelected)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cellCount.toString(),
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }

        // 多选选中标识：右上角
        if (multiSelectMode && multiSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
internal fun ReminderComposerCard(
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
internal fun ReminderRulesSection(
    reminderRules: List<com.kebiao.viewer.core.reminder.model.ReminderRule>,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem>,
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
                val display = remember(rule, schedule, timingProfile, manualCourses) {
                    describeReminderRule(rule, schedule, timingProfile, manualCourses)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(display.title, fontWeight = FontWeight.Medium)
                        Text(
                            display.timing,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (display.nextTrigger != null) {
                            Text(
                                display.nextTrigger,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            display.options,
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

private data class ReminderRuleDisplay(
    val title: String,
    val timing: String,
    val options: String,
    val nextTrigger: String?,
)

private fun describeReminderRule(
    rule: com.kebiao.viewer.core.reminder.model.ReminderRule,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem>,
): ReminderRuleDisplay {
    val course = rule.courseId?.let { id ->
        schedule?.dailySchedules?.flatMap { it.courses }?.firstOrNull { it.id == id }
            ?: manualCourses.firstOrNull { it.id == id }
    }
    val nextTrigger = computeNextTrigger(rule, schedule, timingProfile, manualCourses)
    val scope = rule.scopeType
    val title: String
    val timing: String
    when (scope) {
        com.kebiao.viewer.core.reminder.model.ReminderScopeType.SingleCourse -> {
            title = course?.title ?: "（已删除的课程）"
            val day = course?.time?.dayOfWeek?.let(::weekdayLabel)
            val nodeRange = course?.time?.let { "第${it.startNode}-${it.endNode}节" }
            val slot = course?.time?.let { timingProfile?.findSlot(it.startNode, it.endNode) }
            val timeRange = slot?.let { "${it.startTime}-${it.endTime}" }
            val location = course?.location?.takeIf(String::isNotBlank)
            timing = listOfNotNull(day, timeRange, nodeRange, location).joinToString(" · ")
                .ifBlank { "时间未知" }
        }
        com.kebiao.viewer.core.reminder.model.ReminderScopeType.TimeSlot -> {
            val day = rule.dayOfWeek?.let(::weekdayLabel)
            val startNode = rule.startNode
            val endNode = rule.endNode
            val nodeRange = if (startNode != null && endNode != null) {
                "第$startNode-${endNode}节"
            } else null
            val slot = if (startNode != null && endNode != null) {
                timingProfile?.findSlot(startNode, endNode)
            } else null
            val timeRange = slot?.let { "${it.startTime}-${it.endTime}" }
            title = listOfNotNull(day, nodeRange).joinToString(" ").ifBlank { "时间段提醒" }
            timing = listOfNotNull(timeRange, "每周重复").joinToString(" · ")
        }
    }
    val ringtone = if (rule.ringtoneUri.isNullOrBlank()) "系统默认铃声" else "自定义铃声"
    val options = "提前 ${rule.advanceMinutes} 分钟 · $ringtone"
    return ReminderRuleDisplay(title = title, timing = timing, options = options, nextTrigger = nextTrigger)
}

private fun computeNextTrigger(
    rule: com.kebiao.viewer.core.reminder.model.ReminderRule,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem>,
): String? {
    val profile = timingProfile ?: return null
    val mergedSchedule = mergeManualCourses(schedule, manualCourses) ?: return null
    val plans = runCatching {
        com.kebiao.viewer.core.reminder.ReminderPlanner().expandRule(rule, mergedSchedule, profile)
    }.getOrNull().orEmpty()
    val nowMs = System.currentTimeMillis()
    val nextPlan = plans.firstOrNull { it.triggerAtMillis >= nowMs } ?: return null
    val zone = runCatching { java.time.ZoneId.of(profile.timezone) }
        .getOrDefault(java.time.ZoneId.systemDefault())
    val trigger = java.time.Instant.ofEpochMilli(nextPlan.triggerAtMillis).atZone(zone)
    val date = "${trigger.monthValue}月${trigger.dayOfMonth}日"
    val weekday = weekdayLabel(trigger.dayOfWeek.value)
    val time = String.format("%02d:%02d", trigger.hour, trigger.minute)
    return "下次提醒：$date $weekday $time"
}

private fun mergeManualCourses(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
): TermSchedule? {
    if (schedule == null && manualCourses.isEmpty()) return null
    val base = schedule ?: TermSchedule(
        termId = "manual-only",
        updatedAt = java.time.OffsetDateTime.now().toString(),
        dailySchedules = emptyList(),
    )
    if (manualCourses.isEmpty()) return base
    val grouped = manualCourses.groupBy { it.time.dayOfWeek }
    val merged = (1..7).map { day ->
        val existing = base.dailySchedules.firstOrNull { it.dayOfWeek == day }
        val extras = grouped[day].orEmpty()
        val combined = (existing?.courses.orEmpty() + extras)
        com.kebiao.viewer.core.kernel.model.DailySchedule(dayOfWeek = day, courses = combined)
    }
    return base.copy(dailySchedules = merged)
}

private fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
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

internal data class DisplaySlot(
    val startNode: Int,
    val endNode: Int,
    val label: String,
    val startTime: String,
    val endTime: String,
)

internal data class CoursePlacement(
    val dayIndex: Int,
    val rowIndex: Int,
    val rowSpan: Int,
)

internal data class CourseRenderEntry(
    val course: CourseItem,
    val placement: CoursePlacement,
    val inactive: Boolean,
)

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onSetReminder: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "已选 $selectedCount",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onSetReminder,
                enabled = selectedCount > 0,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("设提醒")
            }
            TextButton(onClick = onClear) {
                Text("取消")
            }
        }
    }
}

private fun buildWeekModel(
    weekOffset: Int,
    termStart: LocalDate? = null,
    zone: ZoneId = ZoneId.of("Asia/Shanghai"),
): WeekModel {
    val today = BeijingTime.todayIn(zone)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
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
            dateLabel = if (date.dayOfMonth == 1) "${date.monthValue}月" else date.dayOfMonth.toString(),
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
    manualCourses: List<CourseItem> = emptyList(),
): List<DisplaySlot> {
    val profileSlots = timingProfile?.slotTimes.orEmpty().sortedWith(
        compareBy<ClassSlotTime>({ it.startLocalTime() }, { it.startNode }, { it.endNode }),
    )
    val allCoursesForExtras = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    if (profileSlots.isNotEmpty()) {
        val coveredMax = profileSlots.maxOf { it.endNode }
        // 课程节号超出 timing 配置范围时，按顺次补无时间的大节占位（避免课丢失）
        val extraNodes = allCoursesForExtras
            .flatMap { listOf(it.time.startNode, it.time.endNode) }
            .filter { it > coveredMax }
            .distinct()
            .sorted()
        val baseSlots = profileSlots.mapIndexed { index, slot ->
            DisplaySlot(
                startNode = slot.startNode,
                endNode = slot.endNode,
                label = slot.label.ifBlank { "第${index + 1}节" },
                startTime = slot.startTime,
                endTime = slot.endTime,
            )
        }
        val extraSlots = extraNodes.mapIndexed { offset, node ->
            DisplaySlot(
                startNode = node,
                endNode = node,
                label = "第${profileSlots.size + offset + 1}节",
                startTime = "",
                endTime = "",
            )
        }
        // 即使没有课时数据也补到至少 8 节，方便用户在下半段加课。
        val combined = baseSlots + extraSlots
        val padded = padToMinimumSlots(combined, minimum = 8)
        return padded
    }
    val allCourses = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    val derived = allCourses
        .map { it.time.startNode to it.time.endNode }
        .distinct()
        .sortedBy { it.first }
    val derivedSlots = derived.mapIndexed { index, (startNode, endNode) ->
        DisplaySlot(
            startNode = startNode,
            endNode = endNode,
            label = "第${index + 1}节",
            startTime = "--:--",
            endTime = "--:--",
        )
    }
    return padToMinimumSlots(derivedSlots, minimum = 8)
}

private fun padToMinimumSlots(slots: List<DisplaySlot>, minimum: Int): List<DisplaySlot> {
    if (slots.size >= minimum) return slots
    val lastEnd = slots.maxOfOrNull { it.endNode } ?: 0
    val pads = (slots.size until minimum).mapIndexed { offset, _ ->
        val node = lastEnd + offset + 1
        DisplaySlot(
            startNode = node,
            endNode = node,
            label = "第${slots.size + offset + 1}节",
            startTime = "",
            endTime = "",
        )
    }
    return slots + pads
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

internal fun courseColor(
    seed: String,
    palette: List<com.kebiao.viewer.feature.schedule.theme.CoursePaletteEntry>,
): com.kebiao.viewer.feature.schedule.theme.CoursePaletteEntry {
    if (palette.isEmpty()) {
        return com.kebiao.viewer.feature.schedule.theme.CoursePaletteEntry(
            container = Color(0xFFE2EEE3),
            onContainer = Color(0xFF1F2A24),
        )
    }
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

internal fun selectedCourseFromState(
    selectionState: ScheduleSelectionState?,
    schedule: TermSchedule?,
): CourseItem? {
    val singleCourseId = (selectionState as? ScheduleSelectionState.SingleCourse)?.courseId ?: return null
    return schedule?.dailySchedules.orEmpty()
        .flatMap { it.courses }
        .firstOrNull { it.id == singleCourseId }
}

internal fun CourseItem.isActiveInWeek(weekNumber: Int): Boolean {
    return weeks.isEmpty() || weekNumber in weeks
}

internal fun activeCoursesForWeek(courses: List<CourseItem>, weekNumber: Int): List<CourseItem> {
    return courses.filter { it.isActiveInWeek(weekNumber) }
}

internal fun buildWeekRenderEntries(
    allCourses: List<CourseItem>,
    slots: List<DisplaySlot>,
    weekIndex: Int,
    totalScheduleDisplayEnabled: Boolean = false,
): List<CourseRenderEntry> {
    data class Resolved(val course: CourseItem, val placement: CoursePlacement)

    val source = if (totalScheduleDisplayEnabled) {
        allCourses
    } else {
        activeCoursesForWeek(allCourses, weekIndex)
    }
    val resolved = source
        .mapNotNull { course ->
            val placement = coursePlacement(course, slots) ?: return@mapNotNull null
            Resolved(course, placement)
        }
    val grouped = resolved.groupBy { it.placement.dayIndex to it.placement.rowIndex }
    val entries = mutableListOf<CourseRenderEntry>()
    for ((_, list) in grouped) {
        list.distinctBy { it.course.id }
            .sortedWith(
                compareBy<Resolved>(
                    { !it.course.isActiveInWeek(weekIndex) },
                    { it.course.time.startNode },
                    { it.course.time.endNode },
                    { it.course.title },
                    { it.course.id },
                ),
            )
            .forEach {
                entries += CourseRenderEntry(
                    course = it.course,
                    placement = it.placement,
                    inactive = !it.course.isActiveInWeek(weekIndex),
                )
        }
    }
    return entries
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
