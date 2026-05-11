package com.kebiao.viewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kebiao.viewer.app.theme.ClassScheduleTheme
import com.kebiao.viewer.app.util.ScheduleMetadataExportSnapshot
import com.kebiao.viewer.app.util.ScheduleMetadataExporter
import com.kebiao.viewer.core.data.ThemeAccent
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.feature.plugin.BundledPluginCatalogEntry
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
import kotlinx.coroutines.flow.first
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
                factory = AppPreferencesViewModelFactory(
                    container.userPreferencesRepository,
                    refreshScheduleOutputs = { container.refreshScheduleOutputs() },
                ),
            )
            val prefs by prefsViewModel.state.collectAsStateWithLifecycle()

            ClassScheduleTheme(themeMode = prefs.themeMode, themeAccent = prefs.themeAccent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (prefs.loaded) {
                        OnboardingGate(
                            disclaimerAccepted = prefs.disclaimerAccepted,
                            onAccept = { prefsViewModel.setDisclaimerAccepted(true) },
                            onReject = { finishAndRemoveTask() },
                        )
                    }
                    if (prefs.loaded && prefs.disclaimerAccepted) {
                    val appZone = remember(prefs.timeZoneId) { BeijingTime.resolveZone(prefs.timeZoneId) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalAppZone provides appZone) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    var subScreen by rememberSaveable { mutableStateOf<MainActivity.SubScreen?>(null) }
                    var showAddMenu by remember { mutableStateOf(false) }
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            scheduleRepository = container.scheduleRepository,
                            pluginManager = container.pluginManager,
                            reminderCoordinator = container.reminderCoordinator,
                            manualCourseRepository = container.manualCourseRepository,
                            normalizeTimingProfile = { profile ->
                                container.normalizeTimingProfileForActiveTerm(profile)
                            },
                            onSyncCompleted = { profile -> container.refreshWidgets(profile) },
                            onAlarmSyncChecked = {
                                container.userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
                            },
                            resolveTimingProfile = { container.widgetPreferencesRepository.timingProfileFlow.first() },
                        ),
                    )
                    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

                    val termProfileViewModel: TermProfileViewModel = viewModel(
                        factory = TermProfileViewModelFactory(
                            termRepo = container.termProfileRepository,
                            userPrefs = container.userPreferencesRepository,
                            onActiveTermChanged = { container.refreshWidgets() },
                        ),
                    )
                    val termProfileState by termProfileViewModel.state.collectAsStateWithLifecycle()
                    fun setActiveTermStartDate(date: LocalDate?) {
                        val activeTermId = termProfileState.activeTermId
                        if (activeTermId.isNotBlank()) {
                            termProfileViewModel.setStartDate(activeTermId, date)
                        } else {
                            prefsViewModel.setTermStartDate(date)
                        }
                    }

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val drawerGesturesEnabled = !scheduleState.isSyncing && scheduleState.pendingWebSession == null
                    var showDatePicker by rememberSaveable { mutableStateOf(false) }
                    var showCurrentWeekDialog by rememberSaveable { mutableStateOf(false) }
                    var showTermStartReminder by rememberSaveable { mutableStateOf(false) }
                    var autoPromptedThisSession by rememberSaveable { mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(prefs.loaded, prefs.termStartDate, prefs.disclaimerAccepted) {
                        // Once per app launch, gently remind the user to set the term start date
                        // if it's missing. We surface a dismissible reminder instead of forcing
                        // the date picker open.
                        if (prefs.loaded && prefs.disclaimerAccepted && prefs.termStartDate == null && !autoPromptedThisSession) {
                            autoPromptedThisSession = true
                            showTermStartReminder = true
                        }
                    }
                    if (showTermStartReminder) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showTermStartReminder = false },
                            title = { Text("还没有设置开学日期") },
                            text = {
                                Text("没有开学日期就无法计算当前周次。可以稍后在课表页右上角的提示按钮，或抽屉里随时设置。")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showTermStartReminder = false
                                    showDatePicker = true
                                }) { Text("去设置") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTermStartReminder = false }) {
                                    Text("稍后再说")
                                }
                            },
                        )
                    }
                    AutomaticUpdateCheckPrompt(
                        autoCheckEnabled = prefs.autoUpdateEnabled,
                        ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                        onIgnoreUpdateVersion = prefsViewModel::setIgnoredUpdateVersionCode,
                    )
                    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
                    var showThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var showManageSheet by rememberSaveable { mutableStateOf(false) }
                    var showTimeZoneDialog by rememberSaveable { mutableStateOf(false) }
                    var showClearTermStartConfirm by rememberSaveable { mutableStateOf(false) }
                    var showClearEverythingConfirm by rememberSaveable { mutableStateOf(false) }
                    var showClearManualConfirm by rememberSaveable { mutableStateOf(false) }
                    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
                    var showClearSheet by rememberSaveable { mutableStateOf(false) }
                    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
                    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
                    var scheduleViewMode by rememberSaveable { mutableStateOf(ScheduleViewMode.Week) }
                    var showWeekMenu by remember { mutableStateOf(false) }
                    var syncWasActive by rememberSaveable { mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(
                        scheduleState.isSyncing,
                        scheduleState.pendingWebSession,
                        scheduleState.schedule,
                        scheduleState.statusMessage,
                    ) {
                        val syncActive = scheduleState.isSyncing || scheduleState.pendingWebSession != null
                        val justCompleted = syncWasActive &&
                            !syncActive &&
                            scheduleState.schedule != null &&
                            scheduleState.statusMessage == "同步完成，已更新课表"
                        if (justCompleted) {
                            currentScreen = AppScreen.Schedule
                            subScreen = null
                            weekOffset = 0
                            dayOffset = 0
                            scheduleViewMode = ScheduleViewMode.Week
                            snackbarHostState.showSnackbar("同步完成，已回到课表")
                        }
                        syncWasActive = syncActive
                    }
                    androidx.compose.runtime.LaunchedEffect(
                        scheduleState.initialized,
                        scheduleState.schedule,
                        scheduleState.manualCourses,
                        scheduleState.reminderRules,
                        scheduleState.timingProfile,
                        prefs.termStartDate,
                        prefs.timeZoneId,
                        prefs.debugForcedDateTime,
                        prefs.themeAccent,
                    ) {
                        if (scheduleState.initialized) {
                            container.refreshWidgets(scheduleState.timingProfile)
                        }
                    }

                    val effectiveTermStart = prefs.termStartDate
                    val today = remember(prefs.debugForcedDateTime, appZone) {
                        prefs.debugForcedDateTime?.toLocalDate() ?: LocalDate.now(appZone)
                    }
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
                        gesturesEnabled = drawerGesturesEnabled,
                        drawerContent = {
                            AppDrawer(
                                currentScreen = currentScreen,
                                termStartDate = prefs.termStartDate,
                                currentWeekIndex = currentWeekIndex,
                                onSelectScreen = {
                                    currentScreen = it
                                    scope.launch { drawerState.close() }
                                },
                            )
                        },
                    ) {
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        if (currentScreen == AppScreen.Schedule) {
                                            Column(
                                                modifier = Modifier.clickable { showWeekMenu = true },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                val isCurrentWeek = displayedWeekIndex == currentWeekIndex
                                                Surface(
                                                    color = if (isCurrentWeek) MaterialTheme.colorScheme.primaryContainer
                                                    else androidx.compose.ui.graphics.Color.Transparent,
                                                    shape = RoundedCornerShape(50),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(
                                                            horizontal = if (isCurrentWeek) 12.dp else 0.dp,
                                                            vertical = if (isCurrentWeek) 2.dp else 0.dp,
                                                        ),
                                                    ) {
                                                        Text(
                                                            text = "第 $displayedWeekIndex 周",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isCurrentWeek) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onBackground,
                                                        )
                                                        if (isCurrentWeek) {
                                                            Spacer(Modifier.width(4.dp))
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(50),
                                                            ) {
                                                                Text(
                                                                    text = "本周",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Rounded.ArrowDropDown,
                                                            contentDescription = "切换周次",
                                                            tint = if (isCurrentWeek) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onBackground,
                                                        )
                                                    }
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
                                        if (prefs.loaded && prefs.termStartDate == null && currentScreen == AppScreen.Schedule) {
                                            IconButton(onClick = { showTermStartReminder = true }) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PriorityHigh,
                                                            contentDescription = "请设置开学日期",
                                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
                                            Box {
                                                IconButton(onClick = { showAddMenu = true }) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.size(32.dp),
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Add,
                                                                contentDescription = "添加课表",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = showAddMenu,
                                                    onDismissRequest = { showAddMenu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("切换 / 管理学期") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.CalendarMonth,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            subScreen = MainActivity.SubScreen.TermManagement
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("手动添加课程") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Add,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            showAddCourseDialog = true
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("清空课表") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.CleaningServices,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            showClearSheet = true
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("导入 / 导出课程") },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.SwapHoriz,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            subScreen = MainActivity.SubScreen.ImportExport
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
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
                                        minWeekOffset = 1 - currentWeekIndex,
                                        maxWeekOffset = weekPickerTotalWeeks - currentWeekIndex,
                                        onPrevWeek = { weekOffset -= 1 },
                                        onNextWeek = { weekOffset += 1 },
                                        onWeekOffsetChange = { weekOffset = it },
                                        viewMode = scheduleViewMode,
                                        dayOffset = dayOffset,
                                        onPrevDay = { dayOffset -= 1 },
                                        onNextDay = { dayOffset += 1 },
                                        onResetDay = { dayOffset = 0 },
                                        onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                        totalScheduleDisplayEnabled = prefs.totalScheduleDisplayEnabled,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        onUpsertTemporaryScheduleOverride = prefsViewModel::upsertTemporaryScheduleOverride,
                                        onRemoveTemporaryScheduleOverride = prefsViewModel::removeTemporaryScheduleOverride,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> PluginMarketRoute(
                                        installedPlugins = scheduleState.installedPlugins,
                                        enabledPluginIds = prefs.enabledPluginIds,
                                        syncingPluginId = if (scheduleState.isSyncing) scheduleState.pluginId else null,
                                        syncStatusMessage = scheduleState.statusMessage,
                                        pendingWebSession = scheduleState.pendingWebSession,
                                        bundledCatalog = container.bundledPluginCatalog.map {
                                            BundledPluginCatalogEntry(
                                                pluginId = it.pluginId,
                                                name = it.name,
                                                description = it.description,
                                            )
                                        },
                                        onSetPluginEnabled = prefsViewModel::setPluginEnabled,
                                        onSyncPlugin = scheduleViewModel::syncSchedule,
                                        onAddBundledPlugin = { id ->
                                            scope.launch { container.installBundledPlugin(id) }
                                        },
                                        onCompleteWebSession = scheduleViewModel::completeWebSession,
                                        onCancelWebSession = scheduleViewModel::cancelWebSession,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Reminders -> SettingsRoute(
                                        viewModel = scheduleViewModel,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Settings -> AppSettingsRoute(
                                        themeMode = prefs.themeMode,
                                        themeAccentLabel = themeAccentLabel(prefs.themeAccent),
                                        termStartDate = prefs.termStartDate,
                                        timeZoneLabel = timeZoneLabel(prefs.timeZoneId),
                                        currentWeekIndex = currentWeekIndex,
                                        totalScheduleDisplayEnabled = prefs.totalScheduleDisplayEnabled,
                                        alarmBackend = prefs.alarmBackend,
                                        alarmRingDurationSeconds = prefs.alarmRingDurationSeconds,
                                        alarmRepeatIntervalSeconds = prefs.alarmRepeatIntervalSeconds,
                                        alarmRepeatCount = prefs.alarmRepeatCount,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        autoUpdateEnabled = prefs.autoUpdateEnabled,
                                        ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                                        developerModeEnabled = prefs.developerModeEnabled,
                                        debugForcedDateTime = prefs.debugForcedDateTime,
                                        onPickThemeMode = { showThemeSheet = true },
                                        onPickThemeAccent = { showThemeAccentDialog = true },
                                        onPickTermStartDate = { showDatePicker = true },
                                        onPickCurrentWeek = { showCurrentWeekDialog = true },
                                        onClearTermStartDate = { showClearTermStartConfirm = true },
                                        onPickTimeZone = { showTimeZoneDialog = true },
                                        onTotalScheduleDisplayChange = prefsViewModel::setTotalScheduleDisplayEnabled,
                                        onAlarmBackendChange = prefsViewModel::setAlarmBackend,
                                        onAlarmRingDurationSecondsChange = prefsViewModel::setAlarmRingDurationSeconds,
                                        onAlarmRepeatIntervalSecondsChange = prefsViewModel::setAlarmRepeatIntervalSeconds,
                                        onAlarmRepeatCountChange = prefsViewModel::setAlarmRepeatCount,
                                        onUpsertTemporaryScheduleOverride = prefsViewModel::upsertTemporaryScheduleOverride,
                                        onRemoveTemporaryScheduleOverride = prefsViewModel::removeTemporaryScheduleOverride,
                                        onClearTemporaryScheduleOverrides = prefsViewModel::clearTemporaryScheduleOverrides,
                                        onOpenWidgetPicker = { showWidgetPicker = true },
                                        onAutoUpdateEnabledChange = prefsViewModel::setAutoUpdateEnabled,
                                        onIgnoreUpdateVersion = prefsViewModel::setIgnoredUpdateVersionCode,
                                        onSetDeveloperMode = prefsViewModel::setDeveloperModeEnabled,
                                        onSetDebugForcedDateTime = prefsViewModel::setDebugForcedDateTime,
                                        onExportScheduleMetadata = {
                                            scope.launch {
                                                val snapshot = ScheduleMetadataExportSnapshot(
                                                    schedule = scheduleState.schedule,
                                                    manualCourses = scheduleState.manualCourses,
                                                    timingProfile = scheduleState.timingProfile,
                                                    installedPlugins = scheduleState.installedPlugins,
                                                    enabledPluginIds = prefs.enabledPluginIds,
                                                    selectedPluginId = scheduleState.pluginId,
                                                    termStartDate = prefs.termStartDate,
                                                    timeZoneId = prefs.timeZoneId,
                                                    currentWeekIndex = currentWeekIndex,
                                                    displayedWeekIndex = displayedWeekIndex,
                                                    isSyncing = scheduleState.isSyncing,
                                                    statusMessage = scheduleState.statusMessage,
                                                    messages = scheduleState.messages,
                                                )
                                                val intent = ScheduleMetadataExporter.export(this@MainActivity, snapshot)
                                                if (intent != null) {
                                                    runCatching {
                                                        val chooser = android.content.Intent.createChooser(intent, "导出课表元数据").apply {
                                                            clipData = intent.clipData
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        startActivity(chooser)
                                                    }.onFailure {
                                                        android.widget.Toast.makeText(
                                                            this@MainActivity,
                                                            "无法启动分享：${it.message}",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        this@MainActivity,
                                                        "导出失败，请稍后重试",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        },
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

                    when (subScreen) {
                        MainActivity.SubScreen.TermManagement -> {
                            TermManagementScreen(
                                state = termProfileState,
                                onBack = { subScreen = null },
                                onCreate = { name, date ->
                                    termProfileViewModel.createTerm(name, date)
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                onRename = termProfileViewModel::renameTerm,
                                onSetStartDate = { id, date ->
                                    termProfileViewModel.setStartDate(id, date)
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                onActivate = { id ->
                                    termProfileViewModel.activate(id)
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                onDelete = termProfileViewModel::delete,
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.activity.compose.BackHandler { subScreen = null }
                        }
                        MainActivity.SubScreen.ImportExport -> {
                            val activeTermProfile = termProfileState.terms
                                .firstOrNull { it.id == termProfileState.activeTermId }
                            ImportExportScreen(
                                schedule = scheduleState.schedule,
                                manualCourses = scheduleState.manualCourses,
                                termName = activeTermProfile?.name,
                                termStartDate = prefs.termStartDate,
                                onApplyImport = scheduleViewModel::applyImportedSchedule,
                                onBack = { subScreen = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.activity.compose.BackHandler { subScreen = null }
                        }
                        null -> Unit
                    }

                    if (showDatePicker) {
                        TermStartDatePicker(
                            initial = prefs.termStartDate,
                            onDismiss = { showDatePicker = false },
                            onConfirm = { date ->
                                setActiveTermStartDate(date)
                                // After (re)setting term start, snap views back to today / current week.
                                weekOffset = 0
                                dayOffset = 0
                                showDatePicker = false
                            },
                            showHint = prefs.termStartDate == null,
                        )
                    }

                    if (showCurrentWeekDialog) {
                        CurrentWeekDialog(
                            initialWeek = currentWeekIndex,
                            onDismiss = { showCurrentWeekDialog = false },
                            onConfirm = { week ->
                                setActiveTermStartDate(
                                    deriveTermStartForCurrentWeek(today = today, currentWeek = week),
                                )
                                weekOffset = 0
                                dayOffset = 0
                                showCurrentWeekDialog = false
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

                    if (showThemeAccentDialog) {
                        ThemeAccentDialog(
                            current = prefs.themeAccent,
                            onDismiss = { showThemeAccentDialog = false },
                            onSelect = {
                                prefsViewModel.setThemeAccent(it)
                                showThemeAccentDialog = false
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
                                    setActiveTermStartDate(null)
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

                    if (showClearManualConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearManualConfirm = false },
                            title = { Text("清空手动课表") },
                            text = {
                                Text("将删除所有手动添加的课程，不影响插件同步的课表。此操作不可恢复，确定继续？")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scheduleViewModel.clearManualCourses()
                                    showClearManualConfirm = false
                                }) { Text("清空") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearManualConfirm = false }) { Text("取消") }
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
                            onSetSelectedAsCurrent = { selectedWeek ->
                                setActiveTermStartDate(
                                    deriveTermStartForCurrentWeek(today = today, currentWeek = selectedWeek),
                                )
                                weekOffset = 0
                                dayOffset = 0
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
                                showManageSheet = false
                                showClearManualConfirm = true
                            },
                            onClearEverything = {
                                showManageSheet = false
                                showClearEverythingConfirm = true
                            },
                            onRemoveCourse = scheduleViewModel::removeManualCourse,
                        )
                    }

                    if (showWidgetPicker) {
                        WidgetPickerSheet(
                            onDismiss = { showWidgetPicker = false },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                        )
                    }

                    if (showClearSheet) {
                        val importedCourses = remember(scheduleState.schedule) {
                            scheduleState.schedule?.dailySchedules
                                ?.flatMap { it.courses }
                                ?.distinctBy { it.id }
                                .orEmpty()
                        }
                        ClearScheduleSheet(
                            manualCourses = scheduleState.manualCourses,
                            importedCourses = importedCourses,
                            onDismiss = { showClearSheet = false },
                            onConfirm = { selected ->
                                when (selected) {
                                    ClearScope.ManualOnly -> scheduleViewModel.clearManualCourses()
                                    ClearScope.ImportedOnly -> scheduleViewModel.clearImportedSchedule()
                                    ClearScope.Everything -> scheduleViewModel.clearAllSchedules()
                                }
                                showClearSheet = false
                            },
                        )
                    }
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
        Settings("设置", Icons.Rounded.Settings),
        About("关于", Icons.Rounded.Info),
    }

    enum class SubScreen { TermManagement, ImportExport }
}

@Composable
private fun AppDrawer(
    currentScreen: MainActivity.AppScreen,
    termStartDate: LocalDate?,
    currentWeekIndex: Int,
    onSelectScreen: (MainActivity.AppScreen) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.68f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "课表查看",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (termStartDate != null) "当前 第 $currentWeekIndex 周" else "未设置开学日期",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            MainActivity.AppScreen.entries.forEach { screen ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = screen.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    selected = screen == currentScreen,
                    onClick = { onSelectScreen(screen) },
                    modifier = Modifier.height(44.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(8.dp))

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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
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
    showHint: Boolean = false,
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
        if (showHint) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PriorityHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "请设置开学日期，用于计算当前周次和正确显示课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        DatePicker(
            state = state,
            title = {
                Text(
                    text = "选择开学日期",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                )
            },
            headline = {
                val zoneLocal = LocalAppZone.current
                val selectedDate = state.selectedDateMillis?.let { millis ->
                    Instant.ofEpochMilli(millis).atZone(zoneLocal).toLocalDate()
                }
                val fmt = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
                Text(
                    text = selectedDate?.let { "开学日期：${fmt.format(it)}" } ?: "选择第 1 周的周一",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
        )
    }
}

@Composable
private fun CurrentWeekDialog(
    initialWeek: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var weekText by rememberSaveable(initialWeek) { mutableStateOf(initialWeek.coerceAtLeast(1).toString()) }
    val parsedWeek = weekText.toIntOrNull()
    val weekValid = parsedWeek != null && parsedWeek >= 1
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置当前周") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "输入今天所在的教学周，应用会用今天日期反推开学日期。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = weekText,
                    onValueChange = { weekText = it.filter(Char::isDigit).take(3) },
                    label = { Text("当前周") },
                    singleLine = true,
                    isError = weekText.isNotBlank() && !weekValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (weekText.isNotBlank() && !weekValid) {
                            Text("请输入大于 0 的周数")
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedWeek?.let(onConfirm) },
                enabled = weekValid,
            ) { Text("设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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

private data class ThemeAccentOption(val accent: ThemeAccent, val label: String, val swatch: androidx.compose.ui.graphics.Color)

private val themeAccentOptions = listOf(
    ThemeAccentOption(ThemeAccent.Green, "薄荷绿", androidx.compose.ui.graphics.Color(0xFF3FA277)),
    ThemeAccentOption(ThemeAccent.Blue, "海岸蓝", androidx.compose.ui.graphics.Color(0xFF3F6FB5)),
    ThemeAccentOption(ThemeAccent.Purple, "暮霭紫", androidx.compose.ui.graphics.Color(0xFF7259B5)),
    ThemeAccentOption(ThemeAccent.Orange, "暖陶橙", androidx.compose.ui.graphics.Color(0xFFD0763B)),
    ThemeAccentOption(ThemeAccent.Pink, "樱花粉", androidx.compose.ui.graphics.Color(0xFFC25B7D)),
)

private fun themeAccentLabel(accent: ThemeAccent): String =
    themeAccentOptions.firstOrNull { it.accent == accent }?.label ?: accent.name

@Composable
private fun ThemeAccentDialog(
    current: ThemeAccent,
    onDismiss: () -> Unit,
    onSelect: (ThemeAccent) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题") },
        text = {
            Column {
                themeAccentOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.accent) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = option.accent == current,
                            onClick = { onSelect(option.accent) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(option.swatch),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
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
