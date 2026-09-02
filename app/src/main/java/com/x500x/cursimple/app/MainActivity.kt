package com.x500x.cursimple.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.ai.AiImportConfig
import com.x500x.cursimple.app.ai.AiScheduleImportClient
import com.x500x.cursimple.app.util.ScheduleMetadataExportSnapshot
import com.x500x.cursimple.app.util.ScheduleMetadataExporter
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.app.webdav.WebDavClient
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.isCurrentTermWeek
import com.x500x.cursimple.core.kernel.model.termWeekLabel
import com.x500x.cursimple.core.kernel.model.termWeekText
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.plugin.ComponentMarketViewModel
import com.x500x.cursimple.feature.plugin.ComponentMarketViewModelFactory
import com.x500x.cursimple.feature.plugin.PluginMarketRoute
import com.x500x.cursimple.feature.plugin.PluginMarketViewModel
import com.x500x.cursimple.feature.plugin.PluginMarketViewModelFactory
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import com.x500x.cursimple.feature.schedule.ManageScheduleSheet
import com.x500x.cursimple.feature.schedule.ScheduleRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleViewModelFactory
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import com.x500x.cursimple.core.kernel.time.datePickerMillisToLocalDate
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
            val widgetPrefsViewModel: WidgetPreferencesViewModel = viewModel(
                factory = WidgetPreferencesViewModelFactory(
                    container.widgetPreferencesRepository,
                    refreshWidgets = { container.refreshWidgets() },
                ),
            )
            val widgetPrefs by widgetPrefsViewModel.state.collectAsStateWithLifecycle()

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
                    val appZone = remember { BeijingTime.zone }
                    androidx.compose.runtime.CompositionLocalProvider(LocalAppZone provides appZone) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    var subScreen by rememberSaveable { mutableStateOf<MainActivity.SubScreen?>(null) }
                    var openSettingsDestination by rememberSaveable { mutableStateOf<SettingsDestinationKey?>(null) }
                    var settingsReturnTarget by rememberSaveable { mutableStateOf<SettingsReturnTargetKey?>(null) }
                    var showAddMenu by remember { mutableStateOf(false) }
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            appContext = applicationContext,
                            scheduleRepository = container.scheduleRepository,
                            pluginManager = container.pluginManager,
                            reminderCoordinator = container.reminderCoordinator,
                            manualCourseRepository = container.manualCourseRepository,
                            courseNoteRepository = container.courseNoteRepository,
                            normalizeTimingProfile = { profile ->
                                container.normalizeTimingProfileForActiveTerm(profile)
                            },
                            onSyncCompleted = { profile -> container.refreshWidgets(profile) },
                            onAlarmSyncChecked = {
                                container.userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
                            },
                            resolveTimingProfile = { container.widgetPreferencesRepository.timingProfileFlow.first() },
                            timingProfileFlow = container.widgetPreferencesRepository.timingProfileFlow,
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
                    val pluginMarketViewModel: PluginMarketViewModel = viewModel(
                        factory = PluginMarketViewModelFactory(
                            pluginManager = container.pluginManager,
                            gitHubRegistryRepository = container.gitHubRegistryRepository,
                            userPreferencesRepository = container.userPreferencesRepository,
                        ),
                    )
                    val componentMarketViewModel: ComponentMarketViewModel = viewModel(
                        factory = ComponentMarketViewModelFactory(
                            repository = container.pluginComponentRepository,
                            installer = container.pluginComponentInstaller,
                            downloadPackage = container::downloadPluginComponentPackage,
                            fetchComponentIndex = container::fetchPluginComponentMarket,
                        ),
                    )
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
                        // 每次启动应用检查一次开学日期，缺失时弹出可关闭的提醒，而不是直接打开日期选择器。
                        if (prefs.loaded && prefs.disclaimerAccepted && prefs.termStartDate == null && !autoPromptedThisSession) {
                            autoPromptedThisSession = true
                            showTermStartReminder = true
                        }
                    }
                    if (showTermStartReminder) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showTermStartReminder = false },
                            title = { Text(stringResource(R.string.main_term_start_missing_title)) },
                            text = {
                                Text(stringResource(R.string.main_term_start_missing_body))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showTermStartReminder = false
                                    showDatePicker = true
                                }) { Text(stringResource(R.string.main_go_to_settings)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTermStartReminder = false }) {
                                    Text(stringResource(R.string.main_later))
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
                    var showAppLanguageDialog by rememberSaveable { mutableStateOf(false) }
                    var showWidgetThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var showManageSheet by rememberSaveable { mutableStateOf(false) }
                    var showClearTermStartConfirm by rememberSaveable { mutableStateOf(false) }
                    var showClearEverythingConfirm by rememberSaveable { mutableStateOf(false) }
                    var showClearManualConfirm by rememberSaveable { mutableStateOf(false) }
                    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
                    var showClearSheet by rememberSaveable { mutableStateOf(false) }
                    val webDavClient = remember { WebDavClient() }
                    val aiImportClient = remember { AiScheduleImportClient() }
                    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
                    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
                    var scheduleViewMode by rememberSaveable { mutableStateOf(ScheduleViewMode.Week) }
                    var showWeekMenu by remember { mutableStateOf(false) }
                    var syncWasActive by rememberSaveable { mutableStateOf(false) }
                    var lastNavigatedSyncCount by rememberSaveable { mutableIntStateOf(0) }
                    var pendingSystemRingtoneResult by remember {
                        mutableStateOf<((String?) -> Unit)?>(null)
                    }
                    var pendingLocalAudioResult by remember {
                        mutableStateOf<((String?) -> Unit)?>(null)
                    }
                    val systemRingtoneLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) { result ->
                        val callback = pendingSystemRingtoneResult
                        pendingSystemRingtoneResult = null
                        if (result.resultCode == Activity.RESULT_OK) {
                            val uri = result.data?.pickedRingtoneUri()
                            callback?.invoke(uri?.toString())
                        }
                    }
                    val localAudioLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        val callback = pendingLocalAudioResult
                        pendingLocalAudioResult = null
                        if (uri != null) {
                            val persisted = runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }.isSuccess
                            if (persisted) {
                                callback?.invoke(uri.toString())
                            } else {
                                Toast.makeText(this, getString(R.string.main_audio_grant_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    fun pickSystemRingtone(onPicked: (String?) -> Unit) {
                        pendingSystemRingtoneResult = onPicked
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            prefs.alarmRingtoneUri?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(it))
                            }
                        }
                        systemRingtoneLauncher.launch(intent)
                    }
                    fun pickLocalAudio(onPicked: (String?) -> Unit) {
                        pendingLocalAudioResult = onPicked
                        localAudioLauncher.launch(arrayOf("audio/*"))
                    }
                    // 课表域的成功与失败反馈统一走 Snackbar；同步完成另有专门提示，此处跳过
                    var lastShownStatusMessage by remember { mutableStateOf<String?>(null) }
                    var lastSuppressedSyncCount by remember { mutableIntStateOf(0) }
                    androidx.compose.runtime.LaunchedEffect(scheduleState.statusMessage) {
                        val message = scheduleState.statusMessage
                        val isSyncCompletion = scheduleState.syncCompletedCount != lastSuppressedSyncCount
                        if (isSyncCompletion) {
                            lastSuppressedSyncCount = scheduleState.syncCompletedCount
                            lastShownStatusMessage = message
                            return@LaunchedEffect
                        }
                        if (message != null && message != lastShownStatusMessage) {
                            lastShownStatusMessage = message
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                    androidx.compose.runtime.LaunchedEffect(
                        scheduleState.isSyncing,
                        scheduleState.pendingWebSession,
                        scheduleState.schedule,
                        scheduleState.syncCompletedCount,
                    ) {
                        val syncActive = scheduleState.isSyncing || scheduleState.pendingWebSession != null
                        val justCompleted = syncWasActive &&
                            !syncActive &&
                            scheduleState.schedule != null &&
                            scheduleState.syncCompletedCount != lastNavigatedSyncCount
                        if (justCompleted) {
                            lastNavigatedSyncCount = scheduleState.syncCompletedCount
                            currentScreen = AppScreen.Schedule
                            subScreen = null
                            weekOffset = 0
                            dayOffset = 0
                            scheduleViewMode = ScheduleViewMode.Week
                            snackbarHostState.showSnackbar(getString(R.string.sync_back_to_schedule))
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
                    // 周次小于 1 表示尚未开学；未设置开学日期时回退到第 1 周。
                    val currentWeekIndex = resolveWeekIndexForDate(effectiveTermStart, today)
                    val dayWeekIndex = resolveWeekIndexForDate(
                        effectiveTermStart,
                        today.plusDays(dayOffset.toLong()),
                    )
                    val displayedWeekIndex = when (scheduleViewMode) {
                        ScheduleViewMode.Week -> currentWeekIndex + weekOffset
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
                                appVersionName = BuildConfig.VERSION_NAME,
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
                                                // 未设置开学日期或尚未开学时都不存在“当前周”，底色与徽章都不应出现
                                                val isCurrentWeek = isCurrentTermWeek(
                                                    termStart = effectiveTermStart,
                                                    displayedWeekIndex = displayedWeekIndex,
                                                    currentWeekIndex = currentWeekIndex,
                                                )
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
                                                            text = LocalContext.current.termWeekText(
                                                                termWeekLabel(effectiveTermStart, displayedWeekIndex),
                                                            ),
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
                                                                    text = stringResource(R.string.main_this_week),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Rounded.ArrowDropDown,
                                                            contentDescription = stringResource(R.string.main_switch_week),
                                                            tint = if (isCurrentWeek) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onBackground,
                                                        )
                                                    }
                                                }
                                                val termLabel = formatTermLabel(effectiveTermStart)
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
                                                text = stringResource(currentScreen.labelRes),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Menu,
                                                contentDescription = stringResource(R.string.main_open_drawer),
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
                                                            contentDescription = stringResource(R.string.main_set_term_start),
                                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (currentScreen == AppScreen.Schedule) {
                                            // 标签宽度随语言变化，按内容伸展，中文时仍是 32dp 方块
                                            Surface(
                                                onClick = {
                                                    scheduleViewMode = if (scheduleViewMode == ScheduleViewMode.Week) {
                                                        dayOffset = 0
                                                        ScheduleViewMode.Day
                                                    } else {
                                                        ScheduleViewMode.Week
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .height(32.dp)
                                                    .defaultMinSize(minWidth = 32.dp),
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            if (scheduleViewMode == ScheduleViewMode.Week) {
                                                                R.string.schedule_view_mode_week
                                                            } else {
                                                                R.string.schedule_view_mode_day
                                                            },
                                                        ),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                    )
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
                                                                contentDescription = stringResource(R.string.main_add_to_schedule),
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
                                                        text = { Text(stringResource(R.string.main_manage_terms)) },
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
                                                        text = { Text(stringResource(R.string.main_add_course_manually)) },
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
                                                        text = { Text(stringResource(R.string.main_clear_schedule)) },
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
                                                        text = { Text(stringResource(R.string.main_import_export)) },
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
                                        // 开学前最早只翻到当前这个未开学的周，开学后最早翻到第 1 周。
                                        minWeekOffset = (1 - currentWeekIndex).coerceAtMost(0),
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
                                        scheduleTextStyle = prefs.scheduleTextStyle,
                                        scheduleCardStyle = prefs.scheduleCardStyle,
                                        scheduleBackground = prefs.scheduleBackground,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        customColorsAdaptToTheme = prefs.scheduleCustomColorsAdaptToTheme,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        holidayCalendar = prefs.holidayCalendar,
                                        onUpsertTemporaryScheduleOverride = prefsViewModel::upsertTemporaryScheduleOverride,
                                        onRemoveTemporaryScheduleOverride = prefsViewModel::removeTemporaryScheduleOverride,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> PluginMarketRoute(
                                        pluginMarketViewModel = pluginMarketViewModel,
                                        componentMarketViewModel = componentMarketViewModel,
                                        pluginRegistryRepo = prefs.pluginRegistryRepo,
                                        componentMarketIndexUrl = prefs.componentMarketIndexUrl,
                                        enabledPluginIds = prefs.enabledPluginIds,
                                        syncingPluginId = if (scheduleState.isSyncing) scheduleState.pluginId else null,
                                        syncStatusMessage = scheduleState.statusMessage,
                                        missingComponents = scheduleState.missingComponents,
                                        pendingWebSession = scheduleState.pendingWebSession,
                                        onSetPluginEnabled = prefsViewModel::setPluginEnabled,
                                        onSyncPlugin = scheduleViewModel::syncSchedule,
                                        onCompleteWebSession = scheduleViewModel::completeWebSession,
                                        onCancelWebSession = scheduleViewModel::cancelWebSession,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Reminders -> SettingsRoute(
                                        viewModel = scheduleViewModel,
                                        alarmBackend = prefs.alarmBackend,
                                        alarmRingtoneUri = prefs.alarmRingtoneUri,
                                        alarmAlertMode = prefs.alarmAlertMode,
                                        alarmRingDurationSeconds = prefs.alarmRingDurationSeconds,
                                        alarmRepeatIntervalSeconds = prefs.alarmRepeatIntervalSeconds,
                                        alarmRepeatCount = prefs.alarmRepeatCount,
                                        onAlarmBackendChange = prefsViewModel::setAlarmBackend,
                                        onAlarmRingtoneUriChange = prefsViewModel::setAlarmRingtoneUri,
                                        onAlarmAlertModeChange = prefsViewModel::setAlarmAlertMode,
                                        onAlarmRingDurationSecondsChange = prefsViewModel::setAlarmRingDurationSeconds,
                                        onAlarmRepeatIntervalSecondsChange = prefsViewModel::setAlarmRepeatIntervalSeconds,
                                        onAlarmRepeatCountChange = prefsViewModel::setAlarmRepeatCount,
                                        onPickSystemRingtone = ::pickSystemRingtone,
                                        onPickLocalAudio = ::pickLocalAudio,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Settings -> AppSettingsRoute(
                                        themeMode = prefs.themeMode,
                                        themeAccentLabel = themeAccentLabel(prefs.themeAccent),
                                        termStartDate = prefs.termStartDate,
                                        scheduleTextStyle = prefs.scheduleTextStyle,
                                        scheduleCardStyle = prefs.scheduleCardStyle,
                                        scheduleBackground = prefs.scheduleBackground,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        scheduleCustomColorsAdaptToTheme =
                                            prefs.scheduleCustomColorsAdaptToTheme,
                                        widgetThemePreferences = widgetPrefs,
                                        currentWeekIndex = currentWeekIndex,
                                        alarmBackend = prefs.alarmBackend,
                                        alarmRingDurationSeconds = prefs.alarmRingDurationSeconds,
                                        alarmRepeatIntervalSeconds = prefs.alarmRepeatIntervalSeconds,
                                        alarmRepeatCount = prefs.alarmRepeatCount,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        autoUpdateEnabled = prefs.autoUpdateEnabled,
                                        ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                                        pluginRegistryRepo = prefs.pluginRegistryRepo,
                                        componentMarketIndexUrl = prefs.componentMarketIndexUrl,
                                        privateFilesProviderEnabled = prefs.privateFilesProviderEnabled,
                                        webDavUrl = prefs.webDavUrl,
                                        webDavUsername = prefs.webDavUsername,
                                        webDavPassword = prefs.webDavPassword,
                                        aiImportApiUrl = prefs.aiImportApiUrl,
                                        aiImportApiKey = prefs.aiImportApiKey,
                                        aiImportModel = prefs.aiImportModel,
                                        aiImportTimeoutSeconds = prefs.aiImportTimeoutSeconds,
                                        developerModeEnabled = prefs.developerModeEnabled,
                                        debugForcedDateTime = prefs.debugForcedDateTime,
                                        onPickThemeMode = { showThemeSheet = true },
                                        onPickThemeAccent = { showThemeAccentDialog = true },
                                        appLanguage = prefs.appLanguage,
                                        onPickAppLanguage = { showAppLanguageDialog = true },
                                        onPickTermStartDate = { showDatePicker = true },
                                        onPickCurrentWeek = { showCurrentWeekDialog = true },
                                        onClearTermStartDate = { showClearTermStartConfirm = true },
                                        onScheduleCourseTextSizeSpChange = prefsViewModel::setScheduleCourseTextSizeSp,
                                        onScheduleCourseTextColorArgbChange = prefsViewModel::setScheduleCourseTextColorArgb,
                                        onScheduleExamTextSizeSpChange = prefsViewModel::setScheduleExamTextSizeSp,
                                        onScheduleExamTextColorArgbChange = prefsViewModel::setScheduleExamTextColorArgb,
                                        onScheduleHeaderTextSizeSpChange = prefsViewModel::setScheduleHeaderTextSizeSp,
                                        onScheduleHeaderTextColorArgbChange = prefsViewModel::setScheduleHeaderTextColorArgb,
                                        onScheduleTodayHeaderBackgroundColorArgbChange =
                                            prefsViewModel::setScheduleTodayHeaderBackgroundColorArgb,
                                        onScheduleTextHorizontalCenterChange = prefsViewModel::setScheduleTextHorizontalCenter,
                                        onScheduleTextVerticalCenterChange = prefsViewModel::setScheduleTextVerticalCenter,
                                        onScheduleTextFullCenterChange = prefsViewModel::setScheduleTextFullCenter,
                                        onScheduleCourseCornerRadiusDpChange = prefsViewModel::setScheduleCourseCornerRadiusDp,
                                        onScheduleCourseCardHeightDpChange = prefsViewModel::setScheduleCourseCardHeightDp,
                                        onScheduleOpacityPercentChange = prefsViewModel::setScheduleOpacityPercent,
                                        onScheduleInactiveCourseOpacityPercentChange = prefsViewModel::setScheduleInactiveCourseOpacityPercent,
                                        onScheduleGridBorderColorArgbChange = prefsViewModel::setScheduleGridBorderColorArgb,
                                        onScheduleGridBorderOpacityPercentChange = prefsViewModel::setScheduleGridBorderOpacityPercent,
                                        onScheduleGridBorderWidthDpChange = prefsViewModel::setScheduleGridBorderWidthDp,
                                        onScheduleGridBorderDashedChange = prefsViewModel::setScheduleGridBorderDashed,
                                        onScheduleBackgroundColorArgbChange = prefsViewModel::setScheduleBackgroundColorArgb,
                                        onScheduleBackgroundImageUriChange = prefsViewModel::setScheduleBackgroundImageUri,
                                        onClearScheduleBackgroundImage = prefsViewModel::clearScheduleBackgroundImage,
                                        onScheduleBackgroundUseHeaderColor =
                                            prefsViewModel::setScheduleBackgroundUseHeaderColor,
                                        onScheduleCustomColorsAdaptToThemeChange =
                                            prefsViewModel::setScheduleCustomColorsAdaptToTheme,
                                        onScheduleNodeColumnTimeEnabledChange = prefsViewModel::setScheduleNodeColumnTimeEnabled,
                                        onScheduleSaturdayVisibleChange = prefsViewModel::setScheduleSaturdayVisible,
                                        onScheduleWeekendVisibleChange = prefsViewModel::setScheduleWeekendVisible,
                                        onScheduleLocationVisibleChange = prefsViewModel::setScheduleLocationVisible,
                                        onScheduleLocationPrefixAtEnabledChange = prefsViewModel::setScheduleLocationPrefixAtEnabled,
                                        onScheduleTeacherVisibleChange = prefsViewModel::setScheduleTeacherVisible,
                                        onTotalScheduleDisplayChange = prefsViewModel::setTotalScheduleDisplayEnabled,
                                        onAlarmBackendChange = prefsViewModel::setAlarmBackend,
                                        onAlarmRingDurationSecondsChange = prefsViewModel::setAlarmRingDurationSeconds,
                                        onAlarmRepeatIntervalSecondsChange = prefsViewModel::setAlarmRepeatIntervalSeconds,
                                        onAlarmRepeatCountChange = prefsViewModel::setAlarmRepeatCount,
                                        onUpsertTemporaryScheduleOverride = prefsViewModel::upsertTemporaryScheduleOverride,
                                        onRemoveTemporaryScheduleOverride = prefsViewModel::removeTemporaryScheduleOverride,
                                        onClearTemporaryScheduleOverrides = prefsViewModel::clearTemporaryScheduleOverrides,
                                        holidayCalendar = prefs.holidayCalendar,
                                        onUpsertHolidayCalendarEntry = prefsViewModel::upsertHolidayCalendarEntry,
                                        onRemoveHolidayCalendarEntry = prefsViewModel::removeHolidayCalendarEntry,
                                        onClearHolidayCalendarEntries = prefsViewModel::clearHolidayCalendarEntries,
                                        onHolidayCalendarBuiltInEnabledChange =
                                            prefsViewModel::setHolidayCalendarBuiltInEnabled,
                                        skipRemindersOnHoliday = prefs.skipRemindersOnHoliday,
                                        onSkipRemindersOnHolidayChange =
                                            prefsViewModel::setSkipRemindersOnHoliday,
                                        onOpenWidgetPicker = { showWidgetPicker = true },
                                        onPickWidgetThemeAccent = { showWidgetThemeAccentDialog = true },
                                        onWidgetBackgroundImageUriChange = widgetPrefsViewModel::setWidgetBackgroundImageUri,
                                        onClearWidgetBackgroundImage = widgetPrefsViewModel::clearWidgetBackgroundImage,
                                        onWidgetOpenAppOnDoubleClickChange =
                                            widgetPrefsViewModel::setWidgetOpenAppOnDoubleClickEnabled,
                                        onAutoUpdateEnabledChange = prefsViewModel::setAutoUpdateEnabled,
                                        onIgnoreUpdateVersion = prefsViewModel::setIgnoredUpdateVersionCode,
                                        onPluginRegistryRepoChange = prefsViewModel::setPluginRegistryRepo,
                                        onComponentMarketIndexUrlChange = prefsViewModel::setComponentMarketIndexUrl,
                                        onPrivateFilesProviderEnabledChange =
                                            prefsViewModel::setPrivateFilesProviderEnabled,
                                        onWebDavSettingsChange = prefsViewModel::setWebDavSettings,
                                        onTestWebDavSettings = { config ->
                                            withContext(Dispatchers.IO) {
                                                runCatching { webDavClient.test(config) }
                                            }
                                        },
                                        onAiImportSettingsChange = prefsViewModel::setAiImportSettings,
                                        onSetDeveloperMode = prefsViewModel::setDeveloperModeEnabled,
                                        onSetDebugForcedDateTime = prefsViewModel::setDebugForcedDateTime,
                                        onResetScheduleAppearanceAndDisplay =
                                            prefsViewModel::resetScheduleAppearanceAndDisplay,
                                        onResetAllSettings = {
                                            prefsViewModel.resetAllSettings()
                                            widgetPrefsViewModel.resetWidgetThemePreferences()
                                        },
                                        openDestination = openSettingsDestination,
                                        onOpenDestinationConsumed = { openSettingsDestination = null },
                                        returnTarget = settingsReturnTarget,
                                        onReturnTargetReady = {
                                            currentScreen = AppScreen.Schedule
                                            subScreen = MainActivity.SubScreen.ImportExport
                                            settingsReturnTarget = null
                                            openSettingsDestination = null
                                        },
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
                                                    currentWeekIndex = currentWeekIndex,
                                                    displayedWeekIndex = displayedWeekIndex,
                                                    isSyncing = scheduleState.isSyncing,
                                                    statusMessage = scheduleState.statusMessage,
                                                    messages = scheduleState.messages,
                                                )
                                                val intent = ScheduleMetadataExporter.export(this@MainActivity, snapshot)
                                                if (intent != null) {
                                                    runCatching {
                                                        val chooser = android.content.Intent.createChooser(intent, getString(R.string.main_export_metadata_chooser)).apply {
                                                            clipData = intent.clipData
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        startActivity(chooser)
                                                    }.onFailure {
                                                        android.widget.Toast.makeText(
                                                            this@MainActivity,
                                                            getString(R.string.main_share_failed, it.message.orEmpty()),
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        this@MainActivity,
                                                        getString(R.string.main_export_failed),
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
                                timingProfile = scheduleState.timingProfile,
                                temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                holidayCalendar = prefs.holidayCalendar,
                                webDavConfig = WebDavConfig(
                                    url = prefs.webDavUrl,
                                    username = prefs.webDavUsername,
                                    password = prefs.webDavPassword,
                                ),
                                webDavClient = webDavClient,
                                aiImportConfig = AiImportConfig(
                                    apiUrl = prefs.aiImportApiUrl,
                                    apiKey = prefs.aiImportApiKey,
                                    model = prefs.aiImportModel,
                                    timeoutSeconds = prefs.aiImportTimeoutSeconds,
                                ),
                                aiImportClient = aiImportClient,
                                onApplyImport = scheduleViewModel::applyImportedSchedule,
                                onApplyTermStartDate = { date ->
                                    setActiveTermStartDate(date)
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                onCreateAppBackup = container::exportAppBackup,
                                onRestoreAppBackup = container::restoreAppBackup,
                                onOpenWebDavSettings = {
                                    currentScreen = AppScreen.Settings
                                    subScreen = null
                                    openSettingsDestination = SettingsDestinationKey.WebDav
                                    settingsReturnTarget = SettingsReturnTargetKey.ImportExport
                                },
                                onOpenAiImportSettings = {
                                    currentScreen = AppScreen.Settings
                                    subScreen = null
                                    openSettingsDestination = SettingsDestinationKey.AiImport
                                    settingsReturnTarget = SettingsReturnTargetKey.ImportExport
                                },
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
                                // 设置开学日期后把视图跳回今天所在的周。
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

                    if (showAppLanguageDialog) {
                        AppLanguageDialog(
                            current = prefs.appLanguage,
                            onDismiss = { showAppLanguageDialog = false },
                            onSelect = { language ->
                                showAppLanguageDialog = false
                                if (language != prefs.appLanguage) {
                                    // 语言在附着基础上下文时读取，改完必须重建界面才能生效
                                    AppLocale.cache(this@MainActivity, language)
                                    prefsViewModel.setAppLanguage(language)
                                    recreate()
                                }
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

                    if (showWidgetThemeAccentDialog) {
                        ThemeAccentDialog(
                            current = widgetPrefs.themeAccent,
                            onDismiss = { showWidgetThemeAccentDialog = false },
                            onSelect = {
                                widgetPrefsViewModel.setWidgetThemeAccent(it)
                                showWidgetThemeAccentDialog = false
                            },
                        )
                    }

                    if (showClearTermStartConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearTermStartConfirm = false },
                            title = { Text(stringResource(R.string.main_clear_term_start_title)) },
                            text = { Text(stringResource(R.string.main_clear_term_start_body)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    setActiveTermStartDate(null)
                                    showClearTermStartConfirm = false
                                }) { Text(stringResource(R.string.main_clear)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearTermStartConfirm = false }) { Text(stringResource(R.string.main_cancel)) }
                            },
                        )
                    }

                    if (showClearEverythingConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearEverythingConfirm = false },
                            title = { Text(stringResource(R.string.main_clear_everything_title)) },
                            text = {
                                Text(stringResource(R.string.main_clear_everything_body))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scheduleViewModel.clearAllSchedules()
                                    showClearEverythingConfirm = false
                                }) { Text(stringResource(R.string.main_clear_all)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearEverythingConfirm = false }) { Text(stringResource(R.string.main_cancel)) }
                            },
                        )
                    }

                    if (showClearManualConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showClearManualConfirm = false },
                            title = { Text(stringResource(R.string.main_clear_manual_title)) },
                            text = {
                                Text(stringResource(R.string.main_clear_manual_body))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scheduleViewModel.clearManualCourses()
                                    showClearManualConfirm = false
                                }) { Text(stringResource(R.string.main_clear_all)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearManualConfirm = false }) { Text(stringResource(R.string.main_cancel)) }
                            },
                        )
                    }

                    if (showAddCourseDialog) {
                        AddCourseDialog(
                            existingCourses = scheduleState.manualCourses +
                                scheduleState.schedule?.dailySchedules.orEmpty().flatMap { it.courses },
                            onDismiss = { showAddCourseDialog = false },
                            onConfirm = { course ->
                                scheduleViewModel.addManualCourse(course)
                                showAddCourseDialog = false
                            },
                        )
                    }

                    if (showWeekMenu) {
                        WeekPickerSheet(
                            termStart = effectiveTermStart,
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
                            importedCourses = scheduleState.schedule?.dailySchedules.orEmpty()
                                .flatMap { it.courses },
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
        val labelRes: Int,
        val icon: ImageVector,
    ) {
        Schedule(R.string.screen_schedule, Icons.AutoMirrored.Rounded.MenuBook),
        Plugins(R.string.screen_plugins, Icons.Rounded.Extension),
        Reminders(R.string.screen_reminders, Icons.Rounded.Notifications),
        Settings(R.string.screen_settings, Icons.Rounded.Settings),
        About(R.string.screen_about, Icons.Rounded.Info),
    }

    enum class SubScreen { TermManagement, ImportExport }
}

private fun Intent.pickedRingtoneUri(): Uri? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

@Composable
private fun AppDrawer(
    currentScreen: MainActivity.AppScreen,
    termStartDate: LocalDate?,
    currentWeekIndex: Int,
    appVersionName: String,
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
                text = stringResource(R.string.main_app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    termStartDate == null -> stringResource(R.string.main_drawer_term_start_unset)
                    currentWeekIndex >= 1 ->
                        stringResource(R.string.main_drawer_current_week, currentWeekIndex)
                    else ->
                        stringResource(R.string.main_drawer_before_term, 1 - currentWeekIndex)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            MainActivity.AppScreen.entries.forEach { screen ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(screen.labelRes),
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
                text = stringResource(R.string.main_drawer_version, appVersionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val initialMillis = (initial ?: LocalDate.now(zone)).toDatePickerMillis()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(datePickerMillisToLocalDate(millis))
                    }
                },
            ) { Text(stringResource(R.string.main_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
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
                        text = stringResource(R.string.main_term_start_prompt),
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
                    text = stringResource(R.string.main_pick_term_start),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                )
            },
            headline = {
                val selectedDate = state.selectedDateMillis?.let(::datePickerMillisToLocalDate)
                val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.main_date_pattern))
                Text(
                    text = selectedDate?.let { stringResource(R.string.main_term_start_value, fmt.format(it)) }
                        ?: stringResource(R.string.main_pick_first_monday),
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
        title = { Text(stringResource(R.string.main_set_current_week_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.main_set_current_week_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = weekText,
                    onValueChange = { weekText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.main_current_week_label)) },
                    singleLine = true,
                    isError = weekText.isNotBlank() && !weekValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (weekText.isNotBlank() && !weekValid) {
                            Text(stringResource(R.string.main_week_must_be_positive))
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedWeek?.let(onConfirm) },
                enabled = weekValid,
            ) { Text(stringResource(R.string.main_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
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
        title = { Text(stringResource(R.string.main_appearance)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.System -> stringResource(R.string.main_theme_system)
                        ThemeMode.Light -> stringResource(R.string.main_theme_light)
                        ThemeMode.Dark -> stringResource(R.string.main_theme_dark)
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
        },
    )
}

private data class ThemeAccentOption(
    val accent: ThemeAccent,
    val labelRes: Int,
    val swatch: androidx.compose.ui.graphics.Color,
)

private val themeAccentOptions = listOf(
    ThemeAccentOption(ThemeAccent.Green, R.string.main_accent_green, androidx.compose.ui.graphics.Color(0xFF3FA277)),
    ThemeAccentOption(ThemeAccent.Blue, R.string.main_accent_blue, androidx.compose.ui.graphics.Color(0xFF3F6FB5)),
    ThemeAccentOption(ThemeAccent.Purple, R.string.main_accent_purple, androidx.compose.ui.graphics.Color(0xFF7259B5)),
    ThemeAccentOption(ThemeAccent.Orange, R.string.main_accent_orange, androidx.compose.ui.graphics.Color(0xFFD0763B)),
    ThemeAccentOption(ThemeAccent.Pink, R.string.main_accent_pink, androidx.compose.ui.graphics.Color(0xFFC25B7D)),
)

@Composable
private fun themeAccentLabel(accent: ThemeAccent): String =
    themeAccentOptions.firstOrNull { it.accent == accent }?.let { stringResource(it.labelRes) }
        ?: accent.name

@Composable
private fun AppLanguageDialog(
    current: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(language) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = language == current,
                            onClick = { onSelect(language) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(appLanguageLabel(language), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
        },
    )
}

@Composable
private fun ThemeAccentDialog(
    current: ThemeAccent,
    onDismiss: () -> Unit,
    onSelect: (ThemeAccent) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_theme)) },
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
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
        },
    )
}

@Composable
private fun formatTermLabel(termStart: LocalDate?): String {
    if (termStart == null) return ""
    val month = termStart.monthValue
    val year = termStart.year
    return if (month >= 7) {
        stringResource(R.string.main_term_first, year, year + 1)
    } else {
        stringResource(R.string.main_term_second, year - 1, year)
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
