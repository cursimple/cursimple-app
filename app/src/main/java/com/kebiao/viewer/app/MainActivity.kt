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
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import com.kebiao.viewer.feature.schedule.ScheduleViewModelFactory
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    var showDatePicker by rememberSaveable { mutableStateOf(false) }
                    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var showManageSheet by rememberSaveable { mutableStateOf(false) }
                    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
                    var showWeekMenu by remember { mutableStateOf(false) }

                    val effectiveTermStart = prefs.termStartDate
                        ?: scheduleState.timingProfile?.termStartLocalDate()
                    val today = LocalDate.now()
                    val currentWeekIndex = effectiveTermStart?.let { start ->
                        val termStartMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val todayMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        max(1, ChronoUnit.WEEKS.between(termStartMonday, todayMonday).toInt() + 1)
                    } ?: 1

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            AppDrawer(
                                currentScreen = currentScreen,
                                themeMode = prefs.themeMode,
                                termStartDate = prefs.termStartDate,
                                currentWeekIndex = currentWeekIndex,
                                onSelectScreen = {
                                    currentScreen = it
                                    scope.launch { drawerState.close() }
                                },
                                onPickThemeMode = { showThemeSheet = true },
                                onPickTermStartDate = { showDatePicker = true },
                                onClearTermStartDate = { prefsViewModel.setTermStartDate(null) },
                            )
                        },
                    ) {
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                val displayedWeekIndex = (currentWeekIndex + weekOffset).coerceAtLeast(1)
                                CenterAlignedTopAppBar(
                                    title = {
                                        if (currentScreen == AppScreen.Schedule) {
                                            Row(
                                                modifier = Modifier.clickable { showWeekMenu = true },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
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
                                        onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> PluginMarketRoute(
                                        installedPlugins = scheduleState.installedPlugins,
                                        activePluginId = scheduleState.pluginId,
                                        syncStatusMessage = scheduleState.statusMessage,
                                        isSyncing = scheduleState.isSyncing,
                                        pendingWebSession = scheduleState.pendingWebSession,
                                        onSelectInstalledPlugin = scheduleViewModel::onPluginIdChange,
                                        onSyncInstalledPlugin = scheduleViewModel::syncSchedule,
                                        onCompleteWebSession = scheduleViewModel::completeWebSession,
                                        onCancelWebSession = scheduleViewModel::cancelWebSession,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Reminders -> SettingsRoute(
                                        viewModel = scheduleViewModel,
                                        onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
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
                        val displayedWeekIndex = (currentWeekIndex + weekOffset).coerceAtLeast(1)
                        WeekPickerSheet(
                            currentWeek = currentWeekIndex,
                            selectedWeek = displayedWeekIndex,
                            onSelectWeek = { week ->
                                weekOffset = week - currentWeekIndex
                                showWeekMenu = false
                            },
                            onSetSelectedAsCurrent = {
                                val newTermStart = LocalDate.now()
                                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    .minusWeeks((displayedWeekIndex - 1).toLong())
                                prefsViewModel.setTermStartDate(newTermStart)
                                weekOffset = 0
                                showWeekMenu = false
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
                            onRemoveCourse = scheduleViewModel::removeManualCourse,
                        )
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
    currentWeekIndex: Int,
    onSelectScreen: (MainActivity.AppScreen) -> Unit,
    onPickThemeMode: () -> Unit,
    onPickTermStartDate: () -> Unit,
    onClearTermStartDate: () -> Unit,
) {
    ModalDrawerSheet(
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
    val initialMillis = (initial ?: LocalDate.now())
        .atStartOfDay(ZoneId.systemDefault())
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
                            .atZone(ZoneId.systemDefault())
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
