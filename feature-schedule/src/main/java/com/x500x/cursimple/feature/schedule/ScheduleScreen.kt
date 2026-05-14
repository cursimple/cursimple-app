package com.x500x.cursimple.feature.schedule

import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.x500x.cursimple.core.data.ScheduleBackgroundPreferences
import com.x500x.cursimple.core.data.ScheduleBackgroundType
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ScheduleTextStylePreferences
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.cancelsCourseOn
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.isTemporaryScheduleOverridden
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.plugin.ui.BannerContribution
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderAction
import com.x500x.cursimple.core.reminder.model.ReminderActionType
import com.x500x.cursimple.core.reminder.model.ReminderCondition
import com.x500x.cursimple.core.reminder.model.ReminderConditionMode
import com.x500x.cursimple.core.reminder.model.ReminderConditionType
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.ReminderTimeRange
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
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
    scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit = {},
    onRemoveTemporaryScheduleOverride: (String) -> Unit = {},
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
        onCreateCourseReminder = viewModel::createReminderForCourse,
        onMuteExamReminder = viewModel::muteExamReminder,
        onRestoreExamReminder = viewModel::restoreExamReminder,
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
        scheduleTextStyle = scheduleTextStyle,
        scheduleCardStyle = scheduleCardStyle,
        scheduleBackground = scheduleBackground,
        scheduleDisplay = scheduleDisplay,
        temporaryScheduleOverrides = temporaryScheduleOverrides,
        onUpsertTemporaryScheduleOverride = onUpsertTemporaryScheduleOverride,
        onRemoveTemporaryScheduleOverride = onRemoveTemporaryScheduleOverride,
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
    onCreateCourseReminder: (String, Int, String?) -> Unit,
    onMuteExamReminder: (String) -> Unit,
    onRestoreExamReminder: (String) -> Unit,
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
    scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit = {},
    onRemoveTemporaryScheduleOverride: (String) -> Unit = {},
) {
    var showSyncSettings by rememberSaveable { mutableStateOf(state.schedule == null) }
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    var detailRequest by remember { mutableStateOf<CourseDetailRequest?>(null) }
    var pendingReminderCourse by remember { mutableStateOf<CourseItem?>(null) }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkReminder by rememberSaveable { mutableStateOf(false) }
    val zone = LocalAppZone.current
    val displayedWeek = remember(state.timingProfile, weekOffset, overrideTermStart, zone, temporaryScheduleOverrides) {
        buildWeekModel(weekOffset, overrideTermStart, zone, temporaryScheduleOverrides)
    }
    val visibleWeekNumber = displayedWeek.weekIndex
    val horizontalScrollState = rememberScrollState()
    val scrollState = rememberScrollState()
    val selectedCourse = remember(state.selectionState, state.schedule) {
        selectedCourseFromState(state.selectionState, state.schedule)
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
        }
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
                val onCellClickHandler: (List<CourseItem>, LocalDate) -> Unit = { coursesAtCell, targetDate ->
                    if (multiSelectMode) {
                        val id = coursesAtCell.firstOrNull()?.id
                        if (id != null) {
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            if (selectedIds.isEmpty()) multiSelectMode = false
                        }
                    } else {
                        detailRequest = CourseDetailRequest(coursesAtCell, targetDate)
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
                        scheduleTextStyle = scheduleTextStyle,
                        scheduleCardStyle = scheduleCardStyle,
                        scheduleBackground = scheduleBackground,
                        scheduleDisplay = scheduleDisplay,
                        temporaryScheduleOverrides = temporaryScheduleOverrides,
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
                        termStartDate = overrideTermStart,
                        temporaryScheduleOverrides = temporaryScheduleOverrides,
                        selectedCourseId = (state.selectionState as? ScheduleSelectionState.SingleCourse)?.courseId,
                        multiSelectMode = multiSelectMode,
                        multiSelectedIds = selectedIds,
                        dayOffset = dayOffset,
                        onCellClick = onCellClickHandler,
                        onCourseLongClick = onLongClickHandler,
                        onPrevDay = onPrevDay,
                        onNextDay = onNextDay,
                        scheduleTextStyle = scheduleTextStyle,
                        scheduleCardStyle = scheduleCardStyle,
                        scheduleDisplay = scheduleDisplay,
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
            val selectedCourses = remember(selectedIds, state.schedule, state.manualCourses) {
                (state.schedule?.dailySchedules.orEmpty().flatMap { it.courses } + state.manualCourses)
                    .filter { it.id in selectedIds }
            }
            val containsExam = selectedCourses.any { it.category == CourseCategory.Exam }
            BulkReminderDialog(
                selectedCount = selectedIds.size,
                defaultAdvanceMinutes = if (containsExam) 40 else 20,
                containsExam = containsExam,
                onDismiss = { showBulkReminder = false },
                onConfirm = { advance, ringtone ->
                    onCreateBulkReminder(selectedIds, advance, ringtone)
                    showBulkReminder = false
                    multiSelectMode = false
                    selectedIds = emptySet()
                },
            )
        }

        detailRequest?.let { request ->
            val examRule = state.reminderRules.firstOrNull {
                it.pluginId == state.pluginId && it.scopeType == ReminderScopeType.Exam
            }
            CourseDetailDialog(
                courses = request.courses,
                timingProfile = state.timingProfile,
                visibleWeekNumber = visibleWeekNumber,
                isManual = { c -> state.manualCourses.any { it.id == c.id } },
                examReminderEnabled = examRule?.enabled == true,
                mutedExamCourseIds = examRule?.mutedCourseIds.orEmpty().toSet(),
                targetDate = request.targetDate,
                isTemporarilyCancelled = { c ->
                    matchingTemporaryCancelRule(c, request.targetDate, temporaryScheduleOverrides) != null
                },
                onTemporaryCancel = { c ->
                    onUpsertTemporaryScheduleOverride(
                        TemporaryScheduleOverride(
                            id = UUID.randomUUID().toString(),
                            type = TemporaryScheduleOverrideType.CancelCourse,
                            targetDate = request.targetDate.toString(),
                            cancelStartNode = c.time.startNode,
                            cancelEndNode = c.time.endNode,
                            cancelCourseId = c.id,
                        ),
                    )
                    detailRequest = null
                },
                onRestoreTemporaryCancel = { c ->
                    matchingTemporaryCancelRule(c, request.targetDate, temporaryScheduleOverrides)?.let {
                        onRemoveTemporaryScheduleOverride(it.id)
                    }
                    detailRequest = null
                },
                onDismiss = { detailRequest = null },
                onSetReminder = { c ->
                    pendingReminderCourse = c
                    detailRequest = null
                },
                onMuteExamReminder = { c -> onMuteExamReminder(c.id) },
                onRestoreExamReminder = { c -> onRestoreExamReminder(c.id) },
                onDelete = { c ->
                    onRemoveManualCourse(c.id)
                    val remaining = request.courses.filterNot { it.id == c.id }
                    detailRequest = remaining.takeIf { it.isNotEmpty() }?.let {
                        request.copy(courses = it)
                    }
                },
            )
        }

        pendingReminderCourse?.let { course ->
            CourseReminderDialog(
                course = course,
                defaultAdvanceMinutes = if (course.category == CourseCategory.Exam) 40 else 20,
                onDismiss = { pendingReminderCourse = null },
                onConfirm = { advance, ringtone ->
                    onCreateCourseReminder(course.id, advance, ringtone)
                    pendingReminderCourse = null
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
    plugins: List<com.x500x.cursimple.core.plugin.install.InstalledPluginRecord>,
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
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    weekOffset: Int,
    minWeekOffset: Int,
    maxWeekOffset: Int,
    overrideTermStart: LocalDate?,
    zone: java.time.ZoneId,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    onAddManualCourse: (CourseItem) -> Unit = {},
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val slots = remember(schedule, timingProfile, manualCourses) {
        displaySlots(schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    }
    val visibleDayIndices = remember(scheduleDisplay.saturdayVisible, scheduleDisplay.weekendVisible) {
        visibleDayIndices(scheduleDisplay)
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
                val isReconciling = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                androidx.compose.runtime.LaunchedEffect(weekOffset, safeMin, pageCount) {
                    val target = (weekOffset - safeMin).coerceIn(0, pageCount - 1)
                    if (pagerState.currentPage == target && pagerLatestRequest.intValue == weekOffset) {
                        return@LaunchedEffect
                    }
                    pagerLatestRequest.intValue = weekOffset
                    if (pagerState.currentPage != target) {
                        isReconciling.value = true
                        try {
                            pagerState.animateScrollToPage(target)
                        } finally {
                            isReconciling.value = false
                        }
                    }
                }
                androidx.compose.runtime.LaunchedEffect(pagerState, safeMin) {
                    androidx.compose.runtime.snapshotFlow {
                        if (pagerState.isScrollInProgress) pagerState.targetPage
                        else pagerState.currentPage
                    }
                        .drop(1)
                        .collect { page ->
                            if (isReconciling.value) return@collect
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
                    val pageWeek = remember(timingProfile, pageOffset, overrideTermStart, zone, temporaryScheduleOverrides) {
                        buildWeekModel(pageOffset, overrideTermStart, zone, temporaryScheduleOverrides)
                    }
                    val active = remember(
                        allCourses,
                        slots,
                        pageWeek.weekIndex,
                        pageWeek.weekStart,
                        scheduleDisplay.totalScheduleDisplayEnabled,
                        visibleDayIndices,
                        temporaryScheduleOverrides,
                        overrideTermStart,
                    ) {
                        buildWeekRenderEntries(
                            allCourses = allCourses,
                            slots = slots,
                            weekIndex = pageWeek.weekIndex,
                            totalScheduleDisplayEnabled = scheduleDisplay.totalScheduleDisplayEnabled,
                            weekStart = pageWeek.weekStart,
                            termStart = overrideTermStart,
                            temporaryScheduleOverrides = temporaryScheduleOverrides,
                            visibleDayIndices = visibleDayIndices,
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
                            visibleDayIndices = visibleDayIndices,
                            scheduleTextStyle = scheduleTextStyle,
                            scheduleCardStyle = scheduleCardStyle,
                            scheduleBackground = scheduleBackground,
                            scheduleDisplay = scheduleDisplay,
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
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    targetDate: LocalDate,
    targetWeekNumber: Int,
    termStartDate: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    dayOffset: Int,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    modifier: Modifier = Modifier,
) {
    val slots = remember(schedule, timingProfile, manualCourses) {
        displaySlots(schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    }
    val today = LocalAppZone.current.today()
    val sourceDate = resolveTemporaryScheduleSourceDate(targetDate, temporaryScheduleOverrides)
    val targetDayOfWeek = sourceDate.dayOfWeek.value
    val sourceWeekNumber = computeWeekNumberForDate(termStartDate, sourceDate).takeIf {
        sourceDate != targetDate
    } ?: targetWeekNumber
    val overrideLabel = sourceDate.takeIf { it != targetDate }?.let(::formatSourceDateLabel)

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
            DailyHeaderRow(
                date = targetDate,
                isToday = targetDate == today,
                overrideLabel = overrideLabel,
            )

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
                    .filter { it.isActiveInWeek(sourceWeekNumber) }
                    .sortedBy { it.time.startNode }
                DayList(
                    slots = slots,
                    courses = active,
                    targetDate = targetDate,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                    uiSchema = uiSchema,
                    reminderRules = reminderRules,
                    selectedCourseId = selectedCourseId,
                    multiSelectMode = multiSelectMode,
                    multiSelectedIds = multiSelectedIds,
                    onCellClick = onCellClick,
                    onCourseLongClick = onCourseLongClick,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                    scheduleTextStyle = scheduleTextStyle,
                    scheduleCardStyle = scheduleCardStyle,
                    scheduleDisplay = scheduleDisplay,
                )
            }
        }
    }
}

@Composable
private fun DailyHeaderRow(
    date: LocalDate,
    isToday: Boolean,
    overrideLabel: String?,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
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
        if (overrideLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "按${overrideLabel}课",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayList(
    slots: List<DisplaySlot>,
    courses: List<CourseItem>,
    targetDate: LocalDate,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
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
                targetDate = targetDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                uiSchema = uiSchema,
                reminderRules = reminderRules,
                selectedCourseId = selectedCourseId,
                multiSelectMode = multiSelectMode,
                multiSelectedIds = multiSelectedIds,
                onCellClick = onCellClick,
                onCourseLongClick = onCourseLongClick,
                scheduleTextStyle = scheduleTextStyle,
                scheduleCardStyle = scheduleCardStyle,
                scheduleDisplay = scheduleDisplay,
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
    targetDate: LocalDate,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 38.dp, max = 52.dp),
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
                lineHeight = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
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
                val isExam = course.category == CourseCategory.Exam
                val temporarilyCancelled = isCourseTemporarilyCancelled(
                    date = targetDate,
                    course = course,
                    overrides = temporaryScheduleOverrides,
                )
                val containerColor = if (isExam) MaterialTheme.colorScheme.errorContainer else palette.container
                val onColor = if (isExam) MaterialTheme.colorScheme.onErrorContainer else palette.onContainer
                val isSelected = course.id == selectedCourseId
                val isMultiSelected = course.id in multiSelectedIds
                val highlight = isSelected || isMultiSelected
                val shape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp)
                val titleTextSize = if (isExam) {
                    scheduleTextStyle.examTextSizeSp
                } else {
                    scheduleTextStyle.courseTextSizeSp
                }
                val titleTextColor = colorFromArgb(
                    if (isExam) scheduleTextStyle.examTextColorArgb else scheduleTextStyle.courseTextColorArgb,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(containerColor.withOpacityPercent(scheduleCardStyle.scheduleOpacityPercent))
                        .border(
                            BorderStroke(
                                when {
                                    highlight -> 2.dp
                                    isExam -> 1.5.dp
                                    else -> 0.dp
                                },
                                when {
                                    highlight -> MaterialTheme.colorScheme.primary
                                    isExam -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                            ),
                            shape,
                        )
                        .drawWithContent {
                            drawContent()
                            if (temporarilyCancelled) {
                                val strokeWidth = 2.dp.toPx()
                                drawLine(
                                    color = onColor.copy(alpha = 0.78f),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = onColor.copy(alpha = 0.78f),
                                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        .combinedClickable(
                            onClick = { onCellClick(listOf(course), targetDate) },
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
                            color = titleTextColor,
                            fontSize = titleTextSize.sp,
                            lineHeight = (titleTextSize + 2).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isExam) {
                            Text(
                                text = "考试",
                                color = onColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        if (scheduleDisplay.locationVisible && course.location.isNotBlank()) {
                            Text(
                                text = formatCourseLocation(course.location, scheduleDisplay),
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (scheduleDisplay.teacherVisible && course.teacher.isNotBlank()) {
                            Text(
                                text = course.teacher,
                                color = onColor.copy(alpha = 0.82f),
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
            "侧边栏里有主题、开学日期，去逛逛",
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
    return computeWeekNumberForDate(termStart, target)
}

private fun computeWeekNumberForDate(
    termStart: LocalDate?,
    target: LocalDate,
): Int {
    val termStartMonday = termStart?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetMonday = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return if (termStartMonday != null) {
        max(1, ChronoUnit.WEEKS.between(termStartMonday, targetMonday).toInt() + 1)
    } else {
        1
    }
}

private fun formatSourceDateLabel(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}${weekdayLabel(date.dayOfWeek.value)}"

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
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    visibleDayIndices: List<Int>,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
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
    val visibleDays = remember(week.days, visibleDayIndices) {
        visibleDayIndices.mapNotNull { week.days.getOrNull(it) }
    }
    val dayColumnCount = visibleDays.size.coerceAtLeast(1)

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
        val timeColumnWidth = when {
            maxWidth < 360.dp -> 44.dp
            maxWidth < 420.dp -> 48.dp
            else -> 52.dp
        }
        val dayHeaderHeight = if (visibleDays.any { it.overrideLabel != null }) 66.dp else 52.dp
        val totalWidth = maxWidth
        val dayColumnWidth = ((totalWidth - timeColumnWidth) / dayColumnCount).coerceAtLeast(36.dp)
        val gridWidth = dayColumnWidth * dayColumnCount
        val slotHeight = scheduleCardStyle.courseCardHeightDp.dp
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
                    scheduleTextStyle = scheduleTextStyle,
                )
                visibleDays.forEach { day ->
                    DayHeader(day = day, width = dayColumnWidth, scheduleTextStyle = scheduleTextStyle)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                ) {
                    slots.forEach { slot ->
                        TimeCell(
                            slot = slot,
                            height = slotHeight,
                            showTime = scheduleDisplay.nodeColumnTimeEnabled,
                            scheduleTextStyle = scheduleTextStyle,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    ScheduleGridBackground(
                        scheduleBackground = scheduleBackground,
                        scheduleCardStyle = scheduleCardStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lineColor = colorFromArgb(scheduleCardStyle.gridBorderColorArgb)
                                    .withOpacityPercent(scheduleCardStyle.gridBorderOpacityPercent)
                                val strokeWidth = scheduleCardStyle.gridBorderWidthDp.dp.toPx()
                                if (strokeWidth <= 0f) return@drawBehind
                                val pathEffect = if (scheduleCardStyle.gridBorderDashed) {
                                    PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                                } else {
                                    null
                                }
                                for (i in 1 until slots.size) {
                                    val y = (slotHeight.toPx() * i)
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                                for (i in 1 until dayColumnCount) {
                                    val x = dayColumnWidth.toPx() * i
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                            }
                            .pointerInput(slots.size, dayColumnWidth, slotHeight, occupiedCells, dayColumnCount) {
                                detectTapGestures(
                                    onTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = (offset.x / dayWidthPx).toInt().coerceIn(0, dayColumnCount - 1)
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
                                temporarilyCancelled = mainEntry.temporarilyCancelled,
                                cellCount = count,
                                multiSelectMode = multiSelectMode,
                                multiSelected = isMultiSelected,
                                scheduleTextStyle = scheduleTextStyle,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleDisplay = scheduleDisplay,
                                width = dayColumnWidth - 3.dp,
                                height = courseHeight,
                                offsetX = dayColumnWidth * placement.dayIndex + 1.5.dp,
                                offsetY = slotHeight * placement.rowIndex + 1.5.dp,
                                onClick = {
                                    val actualDayIndex = visibleDayIndices.getOrElse(placement.dayIndex) {
                                        placement.dayIndex
                                    }
                                    onCellClick(
                                        sortedCourses,
                                        week.weekStart.plusDays(actualDayIndex.toLong()),
                                    )
                                },
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
                                                    val actualDayIndex = visibleDayIndices.getOrElse(day) { day }
                                                    addRequest = Triple(actualDayIndex + 1, slot.startNode, slot.endNode)
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
    scheduleTextStyle: ScheduleTextStylePreferences,
) {
    val darkTheme = isDarkColorScheme()
    val headerColor = scheduleTextStyle.resolvedHeaderTextColor(darkTheme)
    val todayContainer = scheduleTextStyle.resolvedTodayHeaderBackgroundColor(darkTheme)
    val todayContent = readableContentColor(todayContainer)
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
    val columnModifier = Modifier
        .width(width)
        .padding(horizontal = 2.dp)
        .let {
            if (day.isToday) {
                it.clip(RoundedCornerShape(10.dp))
                    .background(todayContainer)
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
            fontSize = headerSize,
            fontWeight = FontWeight.SemiBold,
            color = if (day.isToday) todayContent else headerColor.copy(alpha = 0.88f),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = day.dateLabel,
            fontSize = headerSize,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) todayContent else headerColor,
            maxLines = 1,
            softWrap = false,
        )
        if (day.overrideLabel != null) {
            Text(
                text = day.overrideLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (day.isToday) todayContent else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MonthCornerCell(
    monthLabel: String,
    width: androidx.compose.ui.unit.Dp,
    scheduleTextStyle: ScheduleTextStylePreferences,
) {
    val muted = scheduleTextStyle.resolvedHeaderTextColor(isDarkColorScheme()).copy(alpha = 0.82f)
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
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
                fontSize = headerSize,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "月",
                fontSize = headerSize,
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
    showTime: Boolean,
    scheduleTextStyle: ScheduleTextStylePreferences,
) {
    val headerColor = scheduleTextStyle.resolvedHeaderTextColor(isDarkColorScheme())
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
    Column(
        modifier = Modifier
            .height(height)
            .padding(top = 4.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = slot.label,
            color = headerColor,
            fontSize = headerSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (showTime) {
            Text(
                text = slotTimeRange(slot),
                color = headerColor.copy(alpha = 0.72f),
                fontSize = (scheduleTextStyle.headerTextSizeSp - 3).coerceAtLeast(8).sp,
                lineHeight = (scheduleTextStyle.headerTextSizeSp - 3).coerceAtLeast(8).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun slotTimeRange(slot: DisplaySlot): String {
    return if (slot.startTime.isBlank() && slot.endTime.isBlank()) {
        ""
    } else {
        "${slot.startTime}\n${slot.endTime}"
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
    temporarilyCancelled: Boolean,
    cellCount: Int,
    multiSelectMode: Boolean,
    multiSelected: Boolean,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, accents) { courseColor(course.title, accents.coursePalette) }
    val isExam = course.category == CourseCategory.Exam
    val shape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp)
    val titleSizeSp = if (isExam) {
        scheduleTextStyle.examTextSizeSp
    } else {
        scheduleTextStyle.courseTextSizeSp
    }
    val titleColor = colorFromArgb(
        if (isExam) scheduleTextStyle.examTextColorArgb else scheduleTextStyle.courseTextColorArgb,
    )
    val horizontalCentered = scheduleTextStyle.fullCenter || scheduleTextStyle.horizontalCenter
    val verticalCentered = scheduleTextStyle.fullCenter || scheduleTextStyle.verticalCenter
    val containerColor = when {
        inactive -> accents.inactiveContainer
        isExam -> MaterialTheme.colorScheme.errorContainer
        else -> palette.container
    }
    val onColor = when {
        inactive -> accents.inactiveOnContainer
        isExam -> MaterialTheme.colorScheme.onErrorContainer
        else -> palette.onContainer
    }
    val highlight = multiSelected || selected
    val borderColor = when {
        multiSelected -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        isExam && !inactive -> MaterialTheme.colorScheme.error
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderWidth = when {
        highlight -> 2.dp
        isExam && !inactive -> 1.5.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .width(width)
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    containerColor.withOpacityPercent(
                        if (inactive) {
                            scheduleCardStyle.inactiveCourseOpacityPercent
                        } else {
                            scheduleCardStyle.scheduleOpacityPercent
                        },
                    ),
                )
                .border(BorderStroke(borderWidth, borderColor), shape)
                .drawWithContent {
                    drawContent()
                    if (temporarilyCancelled) {
                        val strokeWidth = 2.dp.toPx()
                        drawLine(
                            color = onColor.copy(alpha = 0.78f),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = onColor.copy(alpha = 0.78f),
                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
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
                verticalArrangement = if (verticalCentered) Arrangement.Center else Arrangement.spacedBy(1.dp),
                horizontalAlignment = if (horizontalCentered) Alignment.CenterHorizontally else Alignment.Start,
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
                if (isExam && !inactive) {
                    Text(
                        text = "考试",
                        color = onColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = course.title,
                    color = titleColor,
                    fontSize = titleSizeSp.sp,
                    lineHeight = (titleSizeSp + 2).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (scheduleDisplay.locationVisible && course.location.isNotBlank()) {
                    Text(
                        text = formatCourseLocation(course.location, scheduleDisplay),
                        color = onColor.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (scheduleDisplay.teacherVisible && course.teacher.isNotBlank()) {
                    Text(
                        text = course.teacher,
                        color = onColor.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
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
internal fun FirstCourseReminderSettingsCard(
    reminderRules: List<ReminderRule>,
    customOccupancies: List<ReminderCustomOccupancy>,
    pluginId: String,
    onSaveRule: (
        String?,
        String,
        Boolean,
        Int,
        String?,
        FirstCourseCandidateScope,
        ReminderConditionMode,
        List<ReminderCondition>,
        List<ReminderAction>,
    ) -> Unit,
    onSaveOccupancy: (
        String?,
        String,
        ReminderTimeRange,
        List<Int>,
        List<Int>,
        List<String>,
        List<String>,
        ReminderNodeRange?,
    ) -> Unit,
    onRemoveOccupancy: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
) {
    val firstCourseRules = reminderRules.filter {
        it.pluginId == pluginId && it.scopeType == ReminderScopeType.FirstCourseOfPeriod
    }
    val visibleOccupancies = customOccupancies.filter { it.pluginId == pluginId }
    var showRuleEditor by rememberSaveable { mutableStateOf(false) }
    var editingOccupancy by remember { mutableStateOf<ReminderCustomOccupancy?>(null) }
    var showOccupancyEditor by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("首次课提醒规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = "按候选课程、条件和动作决定当天提醒哪一门课。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FirstCourseOccupancySection(
                occupancies = visibleOccupancies,
                editingOccupancy = editingOccupancy,
                showEditor = showOccupancyEditor,
                onAdd = {
                    editingOccupancy = null
                    showOccupancyEditor = true
                },
                onEdit = {
                    editingOccupancy = it
                    showOccupancyEditor = true
                },
                onCancelEdit = {
                    editingOccupancy = null
                    showOccupancyEditor = false
                },
                onSave = { occupancyId, name, timeRange, days, weeks, includeDates, excludeDates, linkedNodes ->
                    onSaveOccupancy(occupancyId, name, timeRange, days, weeks, includeDates, excludeDates, linkedNodes)
                    editingOccupancy = null
                    showOccupancyEditor = false
                },
                onRemove = onRemoveOccupancy,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("规则列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (firstCourseRules.isEmpty()) {
                    Text(
                        text = "还没有首课提醒规则，可从模板开始。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                firstCourseRules.forEach { rule ->
                    FirstCourseRuleRow(
                        rule = rule,
                        customOccupancies = visibleOccupancies,
                        onToggle = { checked ->
                            onSaveRule(
                                rule.ruleId,
                                rule.firstCourseDisplayName(),
                                checked,
                                rule.advanceMinutes,
                                rule.ringtoneUri,
                                rule.flexibleCandidateScope(),
                                rule.conditionMode,
                                rule.conditions,
                                rule.actions,
                            )
                        },
                        onRemove = { onRemoveRule(rule.ruleId) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onSaveRule(
                            null,
                            "上午首课提醒",
                            true,
                            20,
                            null,
                            FirstCourseCandidateScope(nodeRange = ReminderNodeRange(1, 4)),
                            ReminderConditionMode.All,
                            emptyList(),
                            listOf(ReminderAction(ReminderActionType.RemindFirstCandidate)),
                        )
                        android.widget.Toast.makeText(context, "已生成标准上午首课提醒", android.widget.Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("标准上午")
                }
                Button(
                    onClick = {
                        val occupancy = visibleOccupancies.firstOrNull { it.name.contains("早自习") }
                            ?: visibleOccupancies.firstOrNull()
                        if (occupancy == null) {
                            showOccupancyEditor = true
                            android.widget.Toast.makeText(context, "请先创建一个早自习占用，时间段可自定义。", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val candidate = FirstCourseCandidateScope(nodeRange = ReminderNodeRange(1, 4))
                        val existingByName = firstCourseRules.associateBy { it.displayName }
                        onSaveRule(
                            existingByName["早自习且第一节有课"]?.ruleId,
                            "早自习且第一节有课",
                            true,
                            20,
                            null,
                            candidate,
                            ReminderConditionMode.All,
                            listOf(
                                ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = occupancy.occupancyId),
                                ReminderCondition(ReminderConditionType.CourseExistsInNodes, nodeRange = ReminderNodeRange(1, 1)),
                            ),
                            listOf(ReminderAction(ReminderActionType.Skip)),
                        )
                        onSaveRule(
                            existingByName["早自习且第一节无课"]?.ruleId,
                            "早自习且第一节无课",
                            true,
                            20,
                            null,
                            candidate,
                            ReminderConditionMode.All,
                            listOf(
                                ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = occupancy.occupancyId),
                                ReminderCondition(ReminderConditionType.CourseAbsentInNodes, nodeRange = ReminderNodeRange(1, 1)),
                            ),
                            listOf(ReminderAction(ReminderActionType.ContinueAfterNode, afterNode = 1)),
                        )
                        onSaveRule(
                            existingByName["无早自习"]?.ruleId,
                            "无早自习",
                            true,
                            20,
                            null,
                            candidate,
                            ReminderConditionMode.All,
                            listOf(ReminderCondition(ReminderConditionType.OccupancyAbsent, occupancyId = occupancy.occupancyId)),
                            listOf(ReminderAction(ReminderActionType.RemindFirstCandidate)),
                        )
                        android.widget.Toast.makeText(context, "已生成早自习智能上午规则", android.widget.Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("早自习智能")
                }
                TextButton(onClick = { showRuleEditor = !showRuleEditor }) {
                    Text(if (showRuleEditor) "收起" else "新建规则")
                }
            }

            if (showRuleEditor) {
                FirstCourseRuleEditor(
                    occupancies = visibleOccupancies,
                    onSave = { name, enabled, advance, candidate, conditionMode, conditions, actions ->
                        onSaveRule(null, name, enabled, advance, null, candidate, conditionMode, conditions, actions)
                        showRuleEditor = false
                    },
                    onCancel = { showRuleEditor = false },
                )
            }
        }
    }
}

@Composable
private fun FirstCourseOccupancySection(
    occupancies: List<ReminderCustomOccupancy>,
    editingOccupancy: ReminderCustomOccupancy?,
    showEditor: Boolean,
    onAdd: () -> Unit,
    onEdit: (ReminderCustomOccupancy) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (String?, String, ReminderTimeRange, List<Int>, List<Int>, List<String>, List<String>, ReminderNodeRange?) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义占用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "早自习、午休等不显示在课表里，但可参与提醒判断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) {
                Text("新增")
            }
        }
        if (occupancies.isEmpty()) {
            Text(
                text = "暂无自定义占用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        occupancies.forEach { occupancy ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(occupancy.name, fontWeight = FontWeight.Medium)
                        Text(
                            text = occupancySummary(occupancy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onEdit(occupancy) }) {
                        Text("编辑")
                    }
                    TextButton(onClick = { onRemove(occupancy.occupancyId) }) {
                        Text("删除")
                    }
                }
            }
        }
        if (showEditor) {
            CustomOccupancyEditor(
                occupancy = editingOccupancy,
                onSave = onSave,
                onCancel = onCancelEdit,
            )
        }
    }
}

@Composable
private fun CustomOccupancyEditor(
    occupancy: ReminderCustomOccupancy?,
    onSave: (String?, String, ReminderTimeRange, List<Int>, List<Int>, List<String>, List<String>, ReminderNodeRange?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(occupancy?.occupancyId) { mutableStateOf(occupancy?.name ?: "早自习") }
    var startTime by remember(occupancy?.occupancyId) { mutableStateOf(occupancy?.timeRange?.startTime ?: "07:10") }
    var endTime by remember(occupancy?.occupancyId) { mutableStateOf(occupancy?.timeRange?.endTime ?: "07:50") }
    var selectedDays by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.daysOfWeek?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(1, 2, 3, 4, 5))
    }
    var weeksText by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.weeks.orEmpty().joinToString(","))
    }
    var includeDatesText by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.includeDates.orEmpty().joinToString(","))
    }
    var excludeDatesText by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.excludeDates.orEmpty().joinToString(","))
    }
    var linkedStartText by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.linkedNodeRange?.startNode?.toString().orEmpty())
    }
    var linkedEndText by remember(occupancy?.occupancyId) {
        mutableStateOf(occupancy?.linkedNodeRange?.endNode?.toString().orEmpty())
    }
    val parsedStart = startTime.parseUiTimeOrNull()
    val parsedEnd = endTime.parseUiTimeOrNull()
    val linkedStart = linkedStartText.toIntOrNull()
    val linkedEnd = linkedEndText.toIntOrNull()
    val linkedRangeValid = (linkedStartText.isBlank() && linkedEndText.isBlank()) ||
        (linkedStart != null && linkedEnd != null && linkedStart in 1..32 && linkedEnd in linkedStart..32)
    val canSave = name.isNotBlank() && parsedStart != null && parsedEnd != null &&
        parsedEnd.isAfter(parsedStart) && linkedRangeValid

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("占用时间段", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                    label = { Text("开始时间") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    isError = startTime.isNotBlank() && parsedStart == null,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                    label = { Text("结束时间") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    isError = endTime.isNotBlank() && (parsedEnd == null || (parsedStart != null && !parsedEnd.isAfter(parsedStart))),
                    modifier = Modifier.weight(1f),
                )
            }
            Text("生效星期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeekdayToggleRow(
                selectedDays = selectedDays,
                onToggle = { day ->
                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                },
            )
            OutlinedTextField(
                value = weeksText,
                onValueChange = { weeksText = it.filter { c -> c.isDigit() || c == ',' || c == '，' } },
                label = { Text("生效周次") },
                placeholder = { Text("可空，例 1,3-5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = includeDatesText,
                onValueChange = { includeDatesText = it.take(120) },
                label = { Text("指定日期") },
                placeholder = { Text("可空，YYYY-MM-DD 逗号分隔") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = excludeDatesText,
                onValueChange = { excludeDatesText = it.take(120) },
                label = { Text("排除日期") },
                placeholder = { Text("可空，YYYY-MM-DD 逗号分隔") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = linkedStartText,
                    onValueChange = { linkedStartText = it.filter(Char::isDigit).take(2) },
                    label = { Text("关联起始节") },
                    placeholder = { Text("可空") },
                    singleLine = true,
                    isError = linkedStartText.isNotBlank() && !linkedRangeValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = linkedEndText,
                    onValueChange = { linkedEndText = it.filter(Char::isDigit).take(2) },
                    label = { Text("关联结束节") },
                    placeholder = { Text("可空") },
                    singleLine = true,
                    isError = linkedEndText.isNotBlank() && !linkedRangeValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val linkedRange = if (linkedStart != null && linkedEnd != null) {
                            ReminderNodeRange(linkedStart, linkedEnd)
                        } else {
                            null
                        }
                        onSave(
                            occupancy?.occupancyId,
                            name.trim(),
                            ReminderTimeRange(startTime, endTime),
                            selectedDays.sorted(),
                            parseUiIntList(weeksText),
                            parseUiDateList(includeDatesText),
                            parseUiDateList(excludeDatesText),
                            linkedRange,
                        )
                    },
                    enabled = canSave,
                ) {
                    Text("保存占用")
                }
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun WeekdayToggleRow(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..7).forEach { day ->
            val selected = day in selectedDays
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .clickable { onToggle(day) },
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shortWeekdayLabel(day),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstCourseRuleRow(
    rule: ReminderRule,
    customOccupancies: List<ReminderCustomOccupancy>,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rule.firstCourseDisplayName(), fontWeight = FontWeight.Medium)
                Text(
                    text = listOf(
                        candidateScopeSummary(rule.flexibleCandidateScope()),
                        conditionSummary(rule, customOccupancies),
                        actionSummary(rule),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "提前 ${rule.advanceMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            TextButton(onClick = onRemove) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun FirstCourseRuleEditor(
    occupancies: List<ReminderCustomOccupancy>,
    onSave: (
        String,
        Boolean,
        Int,
        FirstCourseCandidateScope,
        ReminderConditionMode,
        List<ReminderCondition>,
        List<ReminderAction>,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("自定义首课提醒") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var advanceText by rememberSaveable { mutableStateOf("20") }
    var candidateStartText by rememberSaveable { mutableStateOf("1") }
    var candidateEndText by rememberSaveable { mutableStateOf("4") }
    var occupancyMode by rememberSaveable { mutableStateOf("none") }
    var selectedOccupancyId by remember(occupancies) {
        mutableStateOf(occupancies.firstOrNull()?.occupancyId.orEmpty())
    }
    var nodeMode by rememberSaveable { mutableStateOf("none") }
    var conditionStartText by rememberSaveable { mutableStateOf("1") }
    var conditionEndText by rememberSaveable { mutableStateOf("1") }
    var actionMode by rememberSaveable { mutableStateOf("remind") }
    var continueAfterText by rememberSaveable { mutableStateOf("1") }

    val advance = advanceText.toIntOrNull()
    val candidateStart = candidateStartText.toIntOrNull()
    val candidateEnd = candidateEndText.toIntOrNull()
    val conditionStart = conditionStartText.toIntOrNull()
    val conditionEnd = conditionEndText.toIntOrNull()
    val continueAfter = continueAfterText.toIntOrNull()
    val candidateValid = candidateStart != null && candidateEnd != null &&
        candidateStart in 1..32 && candidateEnd in candidateStart..32
    val conditionNodeValid = nodeMode == "none" ||
        (conditionStart != null && conditionEnd != null && conditionStart in 1..32 && conditionEnd in conditionStart..32)
    val actionValid = actionMode != "continue" || continueAfter != null
    val occupancyValid = occupancyMode == "none" || selectedOccupancyId.isNotBlank()
    val canSave = name.isNotBlank() && advance != null && advance in 0..720 &&
        candidateValid && conditionNodeValid && actionValid && occupancyValid

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("新建首课规则", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text("规则名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("启用规则", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = candidateStartText,
                    onValueChange = { candidateStartText = it.filter(Char::isDigit).take(2) },
                    label = { Text("候选起始节") },
                    singleLine = true,
                    isError = candidateStartText.isNotBlank() && !candidateValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = candidateEndText,
                    onValueChange = { candidateEndText = it.filter(Char::isDigit).take(2) },
                    label = { Text("候选结束节") },
                    singleLine = true,
                    isError = candidateEndText.isNotBlank() && !candidateValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = advanceText,
                onValueChange = { advanceText = it.filter(Char::isDigit).take(4) },
                label = { Text("提前分钟数") },
                singleLine = true,
                isError = advanceText.isNotBlank() && (advance == null || advance !in 0..720),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("占用条件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OptionButtonRow(
                options = listOf("none" to "不限", "exists" to "占用存在", "absent" to "占用不存在"),
                selected = occupancyMode,
                onSelected = { occupancyMode = it },
            )
            if (occupancyMode != "none") {
                OptionButtonRow(
                    options = occupancies.map { it.occupancyId to it.name }.ifEmpty { listOf("" to "先新增占用") },
                    selected = selectedOccupancyId,
                    onSelected = { selectedOccupancyId = it },
                )
            }
            Text("课程条件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OptionButtonRow(
                options = listOf("none" to "不限", "exists" to "节次有课", "absent" to "节次无课"),
                selected = nodeMode,
                onSelected = { nodeMode = it },
            )
            if (nodeMode != "none") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = conditionStartText,
                        onValueChange = { conditionStartText = it.filter(Char::isDigit).take(2) },
                        label = { Text("条件起始节") },
                        singleLine = true,
                        isError = conditionStartText.isNotBlank() && !conditionNodeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = conditionEndText,
                        onValueChange = { conditionEndText = it.filter(Char::isDigit).take(2) },
                        label = { Text("条件结束节") },
                        singleLine = true,
                        isError = conditionEndText.isNotBlank() && !conditionNodeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("动作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OptionButtonRow(
                options = listOf("remind" to "提醒首课", "skip" to "跳过", "continue" to "从某节后找"),
                selected = actionMode,
                onSelected = { actionMode = it },
            )
            if (actionMode == "continue") {
                OutlinedTextField(
                    value = continueAfterText,
                    onValueChange = { continueAfterText = it.filter(Char::isDigit).take(2) },
                    label = { Text("从第几节之后继续") },
                    singleLine = true,
                    isError = continueAfterText.isNotBlank() && continueAfter == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val conditions = buildList {
                            when (occupancyMode) {
                                "exists" -> add(ReminderCondition(ReminderConditionType.OccupancyExists, occupancyId = selectedOccupancyId))
                                "absent" -> add(ReminderCondition(ReminderConditionType.OccupancyAbsent, occupancyId = selectedOccupancyId))
                            }
                            if (nodeMode != "none" && conditionStart != null && conditionEnd != null) {
                                val range = ReminderNodeRange(conditionStart, conditionEnd)
                                add(
                                    ReminderCondition(
                                        type = if (nodeMode == "exists") {
                                            ReminderConditionType.CourseExistsInNodes
                                        } else {
                                            ReminderConditionType.CourseAbsentInNodes
                                        },
                                        nodeRange = range,
                                    ),
                                )
                            }
                        }
                        val actions = listOf(
                            when (actionMode) {
                                "skip" -> ReminderAction(ReminderActionType.Skip)
                                "continue" -> ReminderAction(ReminderActionType.ContinueAfterNode, afterNode = continueAfter)
                                else -> ReminderAction(ReminderActionType.RemindFirstCandidate)
                            },
                        )
                        onSave(
                            name.trim(),
                            enabled,
                            advance ?: 20,
                            FirstCourseCandidateScope(nodeRange = ReminderNodeRange(candidateStart ?: 1, candidateEnd ?: 4)),
                            ReminderConditionMode.All,
                            conditions,
                            actions,
                        )
                    },
                    enabled = canSave,
                ) {
                    Text("保存规则")
                }
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun OptionButtonRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Button(
                onClick = { onSelected(value) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ExamReminderSettingsCard(
    reminderRules: List<ReminderRule>,
    pluginId: String,
    onSave: (Boolean, Int, String?) -> Unit,
) {
    val rule = reminderRules.firstOrNull {
        it.pluginId == pluginId && it.scopeType == ReminderScopeType.Exam
    }
    var enabled by rememberSaveable(rule?.ruleId, rule?.updatedAt) { mutableStateOf(rule?.enabled == true) }
    var advanceMinutesText by rememberSaveable(rule?.ruleId, rule?.updatedAt) {
        mutableStateOf((rule?.advanceMinutes ?: 40).toString())
    }
    var ringtoneUri by rememberSaveable(rule?.ruleId, rule?.updatedAt) { mutableStateOf(rule?.ringtoneUri) }
    val context = LocalContext.current
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
        }
        ringtoneUri = uri?.toString()
    }
    val advance = advanceMinutesText.toIntOrNull()
    val canSave = advance != null && advance in 0..720
    val mutedCount = rule?.mutedCourseIds.orEmpty().size
    fun save(checked: Boolean = enabled) {
        onSave(checked, advance ?: 40, ringtoneUri)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("考试提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (enabled) "已开启 · 自动提醒全部考试" else "默认关闭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        if (canSave) {
                            save(checked)
                        }
                    },
                )
            }
            if (mutedCount > 0) {
                Text(
                    text = "已临时取消 $mutedCount 场，考试日期过去后会自动清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = advanceMinutesText,
                onValueChange = { advanceMinutesText = it.filter(Char::isDigit).take(4) },
                label = { Text("提前分钟数") },
                singleLine = true,
                isError = advanceMinutesText.isNotBlank() && !canSave,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    if (advanceMinutesText.isNotBlank() && !canSave) {
                        Text("请输入 0 到 720 分钟")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        launchAlarmRingtonePicker(context) { intent ->
                            ringtoneLauncher.launch(intent)
                        }
                    },
                ) {
                    Text("选择铃声")
                }
                Text(
                    text = if (ringtoneUri.isNullOrBlank()) "系统默认铃声" else "已选择铃声",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { save() },
                    enabled = canSave,
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
internal fun ReminderRulesSection(
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem>,
    onRemoveReminderRule: (String) -> Unit,
) {
    val visibleRules = reminderRules.filter { it.scopeType != ReminderScopeType.FirstCourseOfPeriod }
    if (visibleRules.isEmpty()) return
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
            visibleRules.forEach { rule ->
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

private fun ReminderRule.firstCourseDisplayName(): String = displayName?.takeIf { it.isNotBlank() } ?: when (period) {
    ReminderDayPeriod.Morning -> "上午首次课提醒"
    ReminderDayPeriod.Afternoon -> "下午首次课提醒"
    ReminderDayPeriod.Evening -> "晚上首次课提醒"
    null -> "首次课提醒"
}

private fun ReminderRule.flexibleCandidateScope(): FirstCourseCandidateScope {
    val startNode = periodStartNode
    val endNode = periodEndNode
    return firstCourseCandidate ?: FirstCourseCandidateScope(
        nodeRange = if (startNode != null && endNode != null) {
            ReminderNodeRange(startNode, endNode)
        } else {
            null
        },
        categories = emptyList(),
    )
}

private fun candidateScopeSummary(scope: FirstCourseCandidateScope): String {
    val node = scope.nodeRange?.let { "候选第${it.startNode}-${it.endNode}节" }
    val time = scope.timeRange?.let { "${it.startTime}-${it.endTime}" }
    val days = scope.daysOfWeek.takeIf { it.isNotEmpty() }?.joinToString("") { shortWeekdayLabel(it) }
    val categories = when {
        scope.categories.isEmpty() -> null
        scope.categories == listOf(CourseCategory.Course) -> "普通课程"
        scope.categories == listOf(CourseCategory.Exam) -> "考试"
        else -> "全部类别"
    }
    return listOfNotNull(node, time, days, categories).joinToString(" · ").ifBlank { "默认候选范围" }
}

private fun conditionSummary(
    rule: ReminderRule,
    occupancies: List<ReminderCustomOccupancy>,
): String {
    if (rule.conditions.isEmpty()) return "无额外条件"
    val labels = rule.conditions.take(3).map { it.summaryLabel(occupancies) }
    val suffix = if (rule.conditions.size > labels.size) "等 ${rule.conditions.size} 条" else null
    val mode = when (rule.conditionMode) {
        ReminderConditionMode.All -> "全部满足"
        ReminderConditionMode.Any -> "任一满足"
    }
    return listOf(labels.joinToString("，"), suffix, mode).filterNotNull().joinToString(" · ")
}

private fun ReminderCondition.summaryLabel(occupancies: List<ReminderCustomOccupancy>): String {
    val occupancyName = occupancyId?.let { id -> occupancies.firstOrNull { it.occupancyId == id }?.name ?: "指定占用" }
    return when (type) {
        ReminderConditionType.CourseExistsInNodes -> nodeRange?.let { "第${it.startNode}-${it.endNode}节有课" } ?: "指定节次有课"
        ReminderConditionType.CourseAbsentInNodes -> nodeRange?.let { "第${it.startNode}-${it.endNode}节无课" } ?: "指定节次无课"
        ReminderConditionType.CourseExistsInTime -> timeRange?.let { "${it.startTime}-${it.endTime}有课" } ?: "指定时间有课"
        ReminderConditionType.CourseAbsentInTime -> timeRange?.let { "${it.startTime}-${it.endTime}无课" } ?: "指定时间无课"
        ReminderConditionType.OccupancyExists -> "${occupancyName ?: "自定义占用"}存在"
        ReminderConditionType.OccupancyAbsent -> "${occupancyName ?: "自定义占用"}不存在"
        ReminderConditionType.OccupancyOverlapsCourse -> "${occupancyName ?: "自定义占用"}与课程重叠"
        ReminderConditionType.OccupancyBeforeCourse -> "${occupancyName ?: "自定义占用"}早于课程"
        ReminderConditionType.WeekdayMatches -> "星期匹配"
        ReminderConditionType.WeekMatches -> "周次匹配"
        ReminderConditionType.DateMatches -> "日期匹配"
        ReminderConditionType.CourseTextMatches -> "课程文本匹配"
    }
}

private fun actionSummary(rule: ReminderRule): String {
    if (rule.actions.isEmpty()) return "提醒第一门候选课"
    return rule.actions.joinToString("，") { action ->
        when (action.type) {
            ReminderActionType.RemindFirstCandidate -> "提醒第一门候选课"
            ReminderActionType.Skip -> "跳过本规则"
            ReminderActionType.ContinueAfterNode -> "从第${action.afterNode ?: "?"}节后继续找"
            ReminderActionType.ContinueAfterTime -> "从${action.afterTime ?: "指定时间"}后继续找"
            ReminderActionType.UseCandidateScope -> "改用另一候选范围"
        }
    }
}

private fun occupancySummary(occupancy: ReminderCustomOccupancy): String {
    val days = occupancy.daysOfWeek.takeIf { it.isNotEmpty() }?.joinToString("") { shortWeekdayLabel(it) } ?: "全部星期"
    val weeks = occupancy.weeks.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = "第", postfix = "周")
    val dates = buildList {
        if (occupancy.includeDates.isNotEmpty()) add("指定 ${occupancy.includeDates.size} 天")
        if (occupancy.excludeDates.isNotEmpty()) add("排除 ${occupancy.excludeDates.size} 天")
    }.joinToString("，").ifBlank { null }
    val nodes = occupancy.linkedNodeRange?.let { "关联第${it.startNode}-${it.endNode}节" }
    return listOfNotNull("${occupancy.timeRange.startTime}-${occupancy.timeRange.endTime}", days, weeks, dates, nodes)
        .joinToString(" · ")
}

private fun shortWeekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> dayOfWeek.toString()
}

private fun String.parseUiTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this) }.getOrNull()

private fun parseUiIntList(value: String): List<Int> =
    value.split(',', '，', ' ')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()

private fun parseUiDateList(value: String): List<String> =
    value.split(',', '，', ' ')
        .map { it.trim() }
        .filter { it.length == 10 && runCatching { LocalDate.parse(it) }.isSuccess }
        .distinct()
        .sorted()

private fun describeReminderRule(
    rule: com.x500x.cursimple.core.reminder.model.ReminderRule,
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
        ReminderScopeType.SingleCourse -> {
            title = when {
                course?.category == CourseCategory.Exam -> "考试：${course.title}"
                course != null -> course.title
                else -> "（已删除的课程）"
            }
            val day = course?.time?.dayOfWeek?.let(::weekdayLabel)
            val nodeRange = course?.time?.let { "第${it.startNode}-${it.endNode}节" }
            val slot = course?.time?.let { timingProfile?.findSlot(it.startNode, it.endNode) }
            val timeRange = slot?.let { "${it.startTime}-${it.endTime}" }
            val location = course?.location?.takeIf(String::isNotBlank)
            timing = listOfNotNull(day, timeRange, nodeRange, location).joinToString(" · ")
                .ifBlank { "时间未知" }
        }
        ReminderScopeType.TimeSlot -> {
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
        ReminderScopeType.Exam -> {
            title = "考试提醒"
            val muted = rule.mutedCourseIds.takeIf { it.isNotEmpty() }?.let { "已临时取消 ${it.size} 场" }
            timing = listOfNotNull("自动提醒全部考试", muted).joinToString(" · ")
        }
        ReminderScopeType.FirstCourseOfPeriod -> {
            title = rule.firstCourseDisplayName()
            timing = listOf(
                candidateScopeSummary(rule.flexibleCandidateScope()),
                conditionSummary(rule, emptyList()),
                actionSummary(rule),
            ).joinToString(" · ")
        }
    }
    val ringtone = if (rule.ringtoneUri.isNullOrBlank()) "系统默认铃声" else "自定义铃声"
    val options = "提前 ${rule.advanceMinutes} 分钟 · $ringtone"
    return ReminderRuleDisplay(title = title, timing = timing, options = options, nextTrigger = nextTrigger)
}

private fun computeNextTrigger(
    rule: com.x500x.cursimple.core.reminder.model.ReminderRule,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem>,
): String? {
    val profile = timingProfile ?: return null
    val mergedSchedule = mergeManualCourses(schedule, manualCourses) ?: return null
    val plans = runCatching {
        com.x500x.cursimple.core.reminder.ReminderPlanner().expandRule(rule, mergedSchedule, profile)
    }.getOrNull().orEmpty()
    val nowMs = System.currentTimeMillis()
    val nextPlan = plans.firstOrNull { it.triggerAtMillis >= nowMs } ?: return null
    val zone = java.time.ZoneId.systemDefault()
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
        com.x500x.cursimple.core.kernel.model.DailySchedule(dayOfWeek = day, courses = combined)
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
    val overrideLabel: String? = null,
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
    val temporarilyCancelled: Boolean = false,
)

internal fun visibleDayIndices(display: ScheduleDisplayPreferences): List<Int> = when {
    display.weekendVisible -> (0..6).toList()
    display.saturdayVisible -> (0..5).toList()
    else -> (0..4).toList()
}

private data class ScheduleBackgroundImageState(
    val image: ImageBitmap? = null,
    val errorMessage: String? = null,
)

@Composable
private fun ScheduleGridBackground(
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    modifier: Modifier = Modifier,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val baseBackgroundColor = when (scheduleBackground.type) {
        ScheduleBackgroundType.Header -> accents.gridBackground
        ScheduleBackgroundType.Color,
        ScheduleBackgroundType.Image -> colorFromArgb(scheduleBackground.colorArgb)
    }
    val backgroundColor = baseBackgroundColor.withOpacityPercent(scheduleCardStyle.scheduleOpacityPercent)
    Box(modifier = modifier.background(backgroundColor)) {
        val imageUri = scheduleBackground.imageUri?.takeIf(String::isNotBlank)
        if (scheduleBackground.type == ScheduleBackgroundType.Image && imageUri != null) {
            val context = LocalContext.current
            val imageState by androidx.compose.runtime.produceState(
                initialValue = ScheduleBackgroundImageState(),
                key1 = imageUri,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(Uri.parse(imageUri)).use { input ->
                            requireNotNull(input) { "无法打开图片文件" }
                            requireNotNull(BitmapFactory.decodeStream(input)) { "图片格式无法解析" }
                                .asImageBitmap()
                        }
                    }.fold(
                        onSuccess = { ScheduleBackgroundImageState(image = it) },
                        onFailure = { ScheduleBackgroundImageState(errorMessage = it.message ?: "背景图片无法读取") },
                    )
                }
            }
            imageState.image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scheduleCardStyle.scheduleOpacityPercent.asAlpha()),
                )
            }
            imageState.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "背景图片无法读取：$message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun colorFromArgb(argb: Long): Color = Color(argb and 0xFFFF_FFFFL)

@Composable
private fun isDarkColorScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun ScheduleTextStylePreferences.resolvedHeaderTextColor(darkTheme: Boolean): Color =
    colorFromArgb(
        if (headerTextColorCustomized) {
            headerTextColorArgb
        } else if (darkTheme) {
            ScheduleTextStylePreferences.DEFAULT_DARK_HEADER_TEXT_COLOR_ARGB
        } else {
            ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_COLOR_ARGB
        },
    )

private fun ScheduleTextStylePreferences.resolvedTodayHeaderBackgroundColor(darkTheme: Boolean): Color =
    colorFromArgb(
        if (todayHeaderBackgroundColorCustomized) {
            todayHeaderBackgroundColorArgb
        } else if (darkTheme) {
            ScheduleTextStylePreferences.DEFAULT_DARK_TODAY_HEADER_BACKGROUND_COLOR_ARGB
        } else {
            ScheduleTextStylePreferences.DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB
        },
    )

private fun readableContentColor(background: Color): Color =
    if (background.luminance() < 0.5f) Color.White else Color.Black

private fun Int.asAlpha(): Float = 1f - (coerceIn(0, 100) / 100f)

private fun Color.withOpacityPercent(percent: Int): Color = copy(alpha = alpha * percent.asAlpha())

private fun formatCourseLocation(
    location: String,
    scheduleDisplay: ScheduleDisplayPreferences,
): String = if (scheduleDisplay.locationPrefixAtEnabled) "@$location" else location

private data class CourseDetailRequest(
    val courses: List<CourseItem>,
    val targetDate: LocalDate,
)

private fun matchingTemporaryCancelRule(
    course: CourseItem,
    targetDate: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): TemporaryScheduleOverride? {
    return overrides.asReversed().firstOrNull { it.cancelsCourseOn(targetDate, course) }
}

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
    zone: ZoneId = ZoneId.systemDefault(),
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
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
            overrideLabel = if (isTemporaryScheduleOverridden(date, temporaryScheduleOverrides)) {
                "按${formatSourceDateLabel(resolveTemporaryScheduleSourceDate(date, temporaryScheduleOverrides))}"
            } else null,
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
    dayIndexOverride: Int? = null,
): CoursePlacement? {
    val dayIndex = dayIndexOverride ?: (course.time.dayOfWeek - 1)
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
    palette: List<com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry>,
): com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry {
    if (palette.isEmpty()) {
        return com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry(
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
    rules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
): Boolean {
    return rules.any { rule ->
        when (rule.scopeType) {
            ReminderScopeType.SingleCourse -> rule.enabled && rule.courseId == course.id
            ReminderScopeType.TimeSlot ->
                rule.enabled && rule.startNode == course.time.startNode && rule.endNode == course.time.endNode
            ReminderScopeType.Exam ->
                rule.enabled && course.category == CourseCategory.Exam && course.id !in rule.mutedCourseIds
            ReminderScopeType.FirstCourseOfPeriod -> false
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
    weekStart: LocalDate? = null,
    termStart: LocalDate? = null,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    visibleDayIndices: List<Int> = (0..6).toList(),
): List<CourseRenderEntry> {
    data class Resolved(
        val course: CourseItem,
        val placement: CoursePlacement,
        val sourceWeekIndex: Int,
        val temporarilyCancelled: Boolean,
    )
    val visibleColumns = visibleDayIndices
        .filter { it in 0..6 }
        .distinct()
        .mapIndexed { columnIndex, dayIndex -> dayIndex to columnIndex }
        .toMap()

    val resolved = if (weekStart != null && temporaryScheduleOverrides.isNotEmpty()) {
        visibleColumns.keys.sorted().flatMap { dayIndex ->
            val actualDate = weekStart.plusDays(dayIndex.toLong())
            val sourceDate = resolveTemporaryScheduleSourceDate(actualDate, temporaryScheduleOverrides)
            val sourceDayOfWeek = sourceDate.dayOfWeek.value
            val sourceWeekIndex = termStart?.let { computeWeekNumberForDate(it, sourceDate) } ?: weekIndex
            val source = if (totalScheduleDisplayEnabled) {
                allCourses
            } else {
                activeCoursesForWeek(allCourses, sourceWeekIndex)
            }
            source
                .filter { it.time.dayOfWeek == sourceDayOfWeek }
                .mapNotNull { course ->
                    val columnIndex = visibleColumns[dayIndex] ?: return@mapNotNull null
                    val placement = coursePlacement(course, slots, columnIndex) ?: return@mapNotNull null
                    Resolved(
                        course = course,
                        placement = placement,
                        sourceWeekIndex = sourceWeekIndex,
                        temporarilyCancelled = isCourseTemporarilyCancelled(
                            date = actualDate,
                            course = course,
                            overrides = temporaryScheduleOverrides,
                        ),
                    )
                }
        }
    } else {
        val source = if (totalScheduleDisplayEnabled) {
            allCourses
        } else {
            activeCoursesForWeek(allCourses, weekIndex)
        }
        source
            .mapNotNull { course ->
                val actualDayIndex = course.time.dayOfWeek - 1
                val columnIndex = visibleColumns[actualDayIndex] ?: return@mapNotNull null
                val placement = coursePlacement(course, slots, columnIndex) ?: return@mapNotNull null
                Resolved(
                    course = course,
                    placement = placement,
                    sourceWeekIndex = weekIndex,
                    temporarilyCancelled = false,
                )
            }
    }
    val grouped = resolved.groupBy { it.placement.dayIndex to it.placement.rowIndex }
    val entries = mutableListOf<CourseRenderEntry>()
    for ((_, list) in grouped) {
        list.distinctBy { it.course.id }
            .sortedWith(
                compareBy<Resolved>(
                    { !it.course.isActiveInWeek(it.sourceWeekIndex) },
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
                    inactive = !it.course.isActiveInWeek(it.sourceWeekIndex),
                    temporarilyCancelled = it.temporarilyCancelled,
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
