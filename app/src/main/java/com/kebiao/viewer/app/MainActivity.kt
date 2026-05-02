package com.kebiao.viewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kebiao.viewer.app.theme.ClassScheduleTheme
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.feature.plugin.PluginMarketRoute
import com.kebiao.viewer.feature.schedule.AddCourseDialog
import com.kebiao.viewer.feature.schedule.ManageScheduleSheet
import com.kebiao.viewer.feature.schedule.ScheduleRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewMode
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.feature.schedule.ScheduleViewModelFactory
import com.kebiao.viewer.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClassScheduleApplication).appContainer
        setContent {
            val prefsViewModel: AppPreferencesViewModel = viewModel(
                factory = AppPreferencesViewModelFactory(container.userPreferencesRepository),
            )
            val prefs by prefsViewModel.state.collectAsStateWithLifecycle()

            ClassScheduleTheme(themeMode = prefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val appZone = remember(prefs.timeZoneId) { BeijingTime.resolveZone(prefs.timeZoneId) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalAppZone provides appZone) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            scheduleRepository = container.scheduleRepository,
                            pluginManager = container.pluginManager,
                            reminderCoordinator = container.reminderCoordinator,
                            manualCourseRepository = container.manualCourseRepository,
                            onSyncCompleted = { container.refreshWidgets() },
                        ),
                    )
                    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

                    androidx.compose.runtime.LaunchedEffect(scheduleState.installedPlugins, prefs.pluginsSeeded) {
                        if (!prefs.pluginsSeeded && scheduleState.installedPlugins.isNotEmpty()) {
                            prefsViewModel.seedEnabledPlugins(
                                scheduleState.installedPlugins.map { it.pluginId }.toSet(),
                            )
                        }
                    }

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    var showDatePicker by rememberSaveable { mutableStateOf(false) }
                    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var showManageSheet by rememberSaveable { mutableStateOf(false) }
                    var showTimeZoneDialog by rememberSaveable { mutableStateOf(false) }
                    var showClearTermStartConfirm by rememberSaveable { mutableStateOf(false) }
                    var showClearEverythingConfirm by rememberSaveable { mutableStateOf(false) }
                    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
                    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
                    var scheduleViewMode by rememberSaveable { mutableStateOf(ScheduleViewMode.Week) }
                    var showWeekMenu by remember { mutableStateOf(false) }

                    val effectiveTermStart = prefs.termStartDate
                        ?: scheduleState.timingProfile?.termStartLocalDate()
                    val today = LocalDate.now(appZone)
                    val currentWeekIndex = effectiveTermStart?.let { start ->
                        val termStartMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val todayMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        max(1, ChronoUnit.WEEKS.between(termStartMonday, todayMonday).toInt() + 1)
                    } ?: 1
                    val dayWeekIndex = effectiveTermStart?.let { start ->
                        val termStartMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val targetMonday = today.plusDays(dayOffset.toLong())
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        max(1, ChronoUnit.WEEKS.between(termStartMonday, targetMonday).toInt() + 1)
                    } ?: 1
                    val displayedWeekIndex = when (scheduleViewMode) {
                        ScheduleViewMode.Week -> (currentWeekIndex + weekOffset).coerceAtLeast(1)
                        ScheduleViewMode.Day -> dayWeekIndex
                    }
                    val weekPickerTotalWeeks = remember(
                        scheduleState.schedule,
                        scheduleState.manualCourses,
                        currentWeekIndex,
                        displayedWeekIndex,
                    ) {
                        resolveWeekPickerTotalWeeks(
                            schedule = scheduleState.schedule,
                            manualCourses = scheduleState.manualCourses,
                            currentWeek = currentWeekIndex,
                            selectedWeek = displayedWeekIndex,
                        )
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = scheduleState.pendingWebSession == null,
                        drawerContent = {
                            AppDrawer(
                                currentScreen = currentScreen,
                                themeMode = prefs.themeMode,
                                termStartDate = prefs.termStartDate,
                                timeZoneId = prefs.timeZoneId,
                                currentWeekIndex = currentWeekIndex,
                                onSelectScreen = {
                                    currentScreen = it
                                    scope.launch { drawerState.close() }
                                },
                                onPickThemeMode = { showThemeSheet = true },
                                onPickTermStartDate = { showDatePicker = true },
                                onClearTermStartDate = { showClearTermStartConfirm = true },
                                onPickTimeZone = { showTimeZoneDialog = true },
                            )
                        },
                    ) {
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        if (currentScreen == AppScreen.Schedule) {
                                            Column(
                                                modifier = Modifier.clickable { showWeekMenu = true },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "第 $displayedWeekIndex 周",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowDropDown,
                                                        contentDescription = "切换周次",
                                                    )
                                                }
                                                val termLabel = remember(effectiveTermStart) {
                                                    formatTermLabel(effectiveTermStart)
                                                }
                                                if (termLabel.isNotBlank()) {
                                                    Text(
                                                        text = termLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = currentScreen.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Menu,
                                                contentDescription = "打开侧边栏",
                                            )
                                        }
                                    },
                                    actions = {
                                        if (currentScreen == AppScreen.Schedule) {
                                            IconButton(
                                                onClick = {
                                                    scheduleViewMode = if (scheduleViewMode == ScheduleViewMode.Week) {
                                                        dayOffset = 0
                                                        ScheduleViewMode.Day
                                                    } else {
                                                        ScheduleViewMode.Week
                                                    }
                                                },
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = if (scheduleViewMode == ScheduleViewMode.Week) "日" else "周",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { showManageSheet = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = "管理课表",
                                                )
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background,
                                    ),
                                )
                            },
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                            ) {
                                when (currentScreen) {
                                    AppScreen.Schedule -> ScheduleRoute(
                                        viewModel = scheduleViewModel,
                                        overrideTermStart = prefs.termStartDate,
                                        weekOffset = weekOffset,
                                        onPrevWeek = { weekOffset -= 1 },
                                        onNextWeek = { weekOffset += 1 },
                                        onWeekOffsetChange = { weekOffset = it },
                                        viewMode = scheduleViewMode,
                                        dayOffset = dayOffset,
                                        onPrevDay = { dayOffset -= 1 },
                                        onNextDay = { dayOffset += 1 },
                                        onResetDay = { dayOffset = 0 },
                                        onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> PluginMarketRoute(
                                        installedPlugins = scheduleState.installedPlugins,
                                        enabledPluginIds = prefs.enabledPluginIds,
                                        syncingPluginId = if (scheduleState.isSyncing) scheduleState.pluginId else null,
                                        syncStatusMessage = scheduleState.statusMessage,
                                        pendingWebSession = scheduleState.pendingWebSession,
                                        onSetPluginEnabled = prefsViewModel::setPluginEnabled,
                                        onSyncPlugin = scheduleViewModel::syncSchedule,
                                        onCompleteWebSession = scheduleViewModel::completeWebSession,
                                        onCancelWebSession = scheduleViewModel::cancelWebSession,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Reminders -> SettingsRoute(
                                        viewModel = scheduleViewModel,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.About -> AboutScreen(
                                        developerModeEnabled = prefs.developerModeEnabled,
                                        onSetDeveloperMode = prefsViewModel::setDeveloperModeEnabled,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    if (showDatePicker) {
                        TermStartDatePicker(
                            initial = prefs.termStartDate,
                            onDismiss = { showDatePicker = false },
                            onConfirm = { date ->
                                prefsViewModel.setTermStartDate(date)
                                showDatePicker = false
                            },
                        )
                    }

                    if (showThemeSheet) {
                        ThemeModeDialog(
                            current = prefs.themeMode,
                            onDismiss = { showThemeSheet = false },
                            onSelect = {
                                prefsViewModel.setThemeMode(it)
                                showThemeSheet = false
                            },
                        )
                    }

                    if (showTimeZoneDialog) {
                        TimeZoneDialog(
                            current = prefs.timeZoneId,
                            onDismiss = { showTimeZoneDialog = false },
                            onSelect = {
                                prefsViewModel.setTimeZoneId(it)
                                showTimeZoneDialog = false
                            },
                        )
                    }

                    if (showClearTermStartConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearTermStartConfirm = false },
                            title = { Text("清除开学日期") },
                            text = { Text("清除后将无法计算当前周次，需要重新设置。确定继续？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    prefsViewModel.setTermStartDate(null)
                                    showClearTermStartConfirm = false
                                }) { Text("清除") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearTermStartConfirm = false }) { Text("取消") }
                            },
                        )
                    }

                    if (showClearEverythingConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearEverythingConfirm = false },
                            title = { Text("清空全部课表") },
                            text = {
                                Text("将删除手动添加的课程、插件同步的课表，以及全部提醒规则。此操作不可恢复，确定继续？")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scheduleViewModel.clearAllSchedules()
                                    showClearEverythingConfirm = false
                                }) { Text("清空") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearEverythingConfirm = false }) { Text("取消") }
                            },
                        )
                    }

                    if (showAddCourseDialog) {
                        AddCourseDialog(
                            onDismiss = { showAddCourseDialog = false },
                            onConfirm = { course ->
                                scheduleViewModel.addManualCourse(course)
                                showAddCourseDialog = false
                            },
                        )
                    }

                    if (showWeekMenu) {
                        WeekPickerSheet(
                            currentWeek = currentWeekIndex,
                            selectedWeek = displayedWeekIndex,
                            totalWeeks = weekPickerTotalWeeks,
                            onSelectWeek = { week ->
                                if (scheduleViewMode == ScheduleViewMode.Day) {
                                    dayOffset = resolveDayOffsetForSelectedWeek(
                                        today = today,
                                        currentDayOffset = dayOffset,
                                        selectedWeek = week,
                                        termStart = effectiveTermStart,
                                        currentWeek = currentWeekIndex,
                                    )
                                } else {
                                    weekOffset = week - currentWeekIndex
                                }
                                showWeekMenu = false
                            },
                            onSetSelectedAsCurrent = {
                                showWeekMenu = false
                                showDatePicker = true
                            },
                            onDismiss = { showWeekMenu = false },
                        )
                    }

                    if (showManageSheet) {
                        ManageScheduleSheet(
                            manualCourses = scheduleState.manualCourses,
                            onDismiss = { showManageSheet = false },
                            onAddSingleCourse = {
                                showManageSheet = false
                                showAddCourseDialog = true
                            },
                            onLoadSample = {
                                scheduleViewModel.loadSampleCourses()
                                showManageSheet = false
                            },
                            onClearAll = {
                                scheduleViewModel.clearManualCourses()
                                showManageSheet = false
                            },
                            onClearEverything = {
                                showManageSheet = false
                                showClearEverythingConfirm = true
                            },
                            onRemoveCourse = scheduleViewModel::removeManualCourse,
                        )
                    }
                    }
                }
            }
        }
    }

    enum class AppScreen(
        val label: String,
        val icon: ImageVector,
    ) {
        Schedule("课表", Icons.AutoMirrored.Rounded.MenuBook),
        Plugins("插件", Icons.Rounded.Extension),
        Reminders("提醒", Icons.Rounded.Notifications),
        About("关于", Icons.Rounded.Info),
    }
}

@Composable
private fun AppDrawer(
    currentScreen: MainActivity.AppScreen,
    themeMode: ThemeMode,
    termStartDate: LocalDate?,
    timeZoneId: String,
    currentWeekIndex: Int,
    onSelectScreen: (MainActivity.AppScreen) -> Unit,
    onPickThemeMode: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onClearTermStartDate: () -> Unit,
    onPickTimeZone: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.72f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "课表查看",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (termStartDate != null) "当前 第 $currentWeekIndex 周" else "未设置开学日期",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            MainActivity.AppScreen.entries.forEach { screen ->
                NavigationDrawerItem(
                    label = { Text(screen.label) },
                    icon = { Icon(screen.icon, contentDescription = null) },
                    selected = screen == currentScreen,
                    onClick = { onSelectScreen(screen) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "偏好",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DrawerActionRow(
                icon = when (themeMode) {
                    ThemeMode.Dark -> Icons.Rounded.Brightness4
                    else -> Icons.Rounded.Brightness7
                },
                title = "外观",
                subtitle = when (themeMode) {
                    ThemeMode.System -> "跟随系统"
                    ThemeMode.Light -> "亮色"
                    ThemeMode.Dark -> "暗色"
                },
                onClick = onPickThemeMode,
            )

            DrawerActionRow(
                icon = Icons.Rounded.CalendarMonth,
                title = "开学日期",
                subtitle = termStartDate?.let {
                    val fmt = DateTimeFormatter.ofPattern("yyyy/M/d")
                    "${fmt.format(it)} · 第 $currentWeekIndex 周"
                } ?: "点击设置（用于计算当前周次）",
                onClick = onPickTermStartDate,
                trailing = if (termStartDate != null) {
                    {
                        TextButton(onClick = onClearTermStartDate) { Text("清除") }
                    }
                } else null,
            )

            DrawerActionRow(
                icon = Icons.Rounded.Public,
                title = "时区",
                subtitle = timeZoneLabel(timeZoneId),
                onClick = onPickTimeZone,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "课表查看 · v0.1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermStartDatePicker(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val zone = LocalAppZone.current
    val initialMillis = (initial ?: LocalDate.now(zone))
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(zone)
                            .toLocalDate()
                        onConfirm(date)
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.System -> "跟随系统"
                        ThemeMode.Light -> "亮色"
                        ThemeMode.Dark -> "暗色"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private data class TimeZoneOption(val id: String, val label: String)

private val timeZonePresets = listOf(
    TimeZoneOption("Asia/Shanghai", "北京时间 (UTC+8)"),
    TimeZoneOption("Asia/Tokyo", "东京 (UTC+9)"),
    TimeZoneOption("Asia/Singapore", "新加坡 (UTC+8)"),
    TimeZoneOption("Asia/Kolkata", "印度 (UTC+5:30)"),
    TimeZoneOption("Europe/London", "伦敦 (UTC+0/+1)"),
    TimeZoneOption("Europe/Paris", "巴黎 (UTC+1/+2)"),
    TimeZoneOption("America/New_York", "纽约 (UTC-5/-4)"),
    TimeZoneOption("America/Los_Angeles", "洛杉矶 (UTC-8/-7)"),
)

private fun timeZoneLabel(id: String): String =
    timeZonePresets.firstOrNull { it.id == id }?.label ?: id

@Composable
private fun TimeZoneDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = remember(current) {
        if (timeZonePresets.any { it.id == current }) timeZonePresets
        else timeZonePresets + TimeZoneOption(current, current)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时区") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = option.id == current,
                            onClick = { onSelect(option.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            if (option.label != option.id) {
                                Text(
                                    text = option.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatTermLabel(termStart: LocalDate?): String {
    if (termStart == null) return ""
    val month = termStart.monthValue
    val year = termStart.year
    return if (month >= 7) {
        "$year-${year + 1} 第1学期"
    } else {
        "${year - 1}-$year 第2学期"
    }
}

internal fun resolveDayOffsetForSelectedWeek(
    today: LocalDate,
    currentDayOffset: Int,
    selectedWeek: Int,
    termStart: LocalDate?,
    currentWeek: Int,
): Int {
    val currentTargetDate = today.plusDays(currentDayOffset.toLong())
    val currentTargetMonday = currentTargetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetWeek = selectedWeek.coerceAtLeast(1)
    val targetMonday = if (termStart != null) {
        termStart
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((targetWeek - 1).toLong())
    } else {
        today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((targetWeek - currentWeek).toLong())
    }
    val weekdayOffset = ChronoUnit.DAYS.between(currentTargetMonday, currentTargetDate)
    val targetDate = targetMonday.plusDays(weekdayOffset)
    return ChronoUnit.DAYS.between(today, targetDate).toInt()
}
