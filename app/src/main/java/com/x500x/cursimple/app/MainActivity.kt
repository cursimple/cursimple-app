package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppToolbarIconButton
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker
import com.x500x.cursimple.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.x500x.cursimple.app.guide.FirstRunGuideOverlay
import com.x500x.cursimple.app.guide.GuideDestination
import com.x500x.cursimple.app.guide.GuideAnchor
import com.x500x.cursimple.app.guide.LocalGuideAnchors
import com.x500x.cursimple.app.guide.guideAnchor
import com.x500x.cursimple.app.guide.rememberGuideAnchorBounds
import com.x500x.cursimple.app.guide.shouldShowFirstRunGuide
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.app.ai.AiImportConfig
import com.x500x.cursimple.app.ai.AiScheduleImportClient
import com.x500x.cursimple.app.util.ScheduleMetadataExportSnapshot
import com.x500x.cursimple.app.util.ScheduleMetadataExporter
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.app.webdav.WebDavClient
import com.x500x.cursimple.BuildConfig
import com.x500x.cursimple.app.update.UpdateNoticeState
import com.x500x.cursimple.app.update.shouldShowUpdateBadge
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.feature.schedule.CourseSwapScreen
import com.x500x.cursimple.core.kernel.model.isCurrentTermWeek
import com.x500x.cursimple.core.kernel.model.termWeekLabel
import com.x500x.cursimple.core.kernel.model.termWeekText
import com.x500x.cursimple.core.kernel.model.planCourseMove
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.feature.plugin.PluginMarketRoute
import com.x500x.cursimple.feature.plugin.availableUpdateKeys
import com.x500x.cursimple.feature.plugin.PluginUpdateAutoCheck
import com.x500x.cursimple.feature.plugin.PluginMarketViewModel
import com.x500x.cursimple.feature.plugin.PluginMarketViewModelFactory
import com.x500x.cursimple.feature.plugin.SchoolImportRoute
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import com.x500x.cursimple.feature.schedule.CourseLibraryRoute
import com.x500x.cursimple.feature.schedule.MemoRoute
import com.x500x.cursimple.feature.schedule.ScheduleRoute
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import com.x500x.cursimple.feature.schedule.ScheduleViewModel
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.schedule.ScheduleViewModelFactory
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

class MainActivity : ComponentActivity() {

    private val extensionFeedRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val scheduleDateRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val memoRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO)?.let { memoRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_EXTENSION_FEED)?.let { extensionFeedRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_EXTENSION_SETTINGS)?.let { openExtensionSettingsRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_SCHEDULE_DATE)?.let { scheduleDateRequest.value = it }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClassScheduleApplication).appContainer
        if (savedInstanceState == null) {
            intent?.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO)?.let { memoRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_EXTENSION_FEED)?.let { extensionFeedRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_EXTENSION_SETTINGS)?.let { openExtensionSettingsRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_SCHEDULE_DATE)?.let { scheduleDateRequest.value = it }
        }
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

            ClassScheduleTheme(
                themeMode = prefs.themeMode,
                themeAccent = prefs.themeAccent,
                customAccentArgb = prefs.themeCustomColorArgb,
            ) {
                val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = lightSystemBars
                    controller.isAppearanceLightNavigationBars = lightSystemBars
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (prefs.loaded) {
                        OnboardingGate(
                            disclaimerAccepted = prefs.disclaimerAccepted,
                            notificationPermissionAskedBefore = prefs.notificationPermissionStartupAsked,
                            onAccept = { prefsViewModel.setDisclaimerAccepted(true) },
                            onReject = { finishAndRemoveTask() },
                            onNotificationAsked = { prefsViewModel.markNotificationPermissionStartupAsked() },
                        )
                    }
                    if (prefs.loaded && prefs.disclaimerAccepted) {
                        IslandStartupPrompt(
                            classNotice = prefs.classNotice,
                            notificationPermissionAsked = prefs.notificationPermissionStartupAsked,
                            promptShown = prefs.islandStartupPromptShown,
                            onShown = { prefsViewModel.markIslandStartupPromptShown() },
                        )
                    }
                    if (prefs.loaded && prefs.disclaimerAccepted) {
                    val appZone = remember { BeijingTime.zone }
                    val embeddedPageGestures = remember { com.x500x.cursimple.feature.plugin.ui.EmbeddedPageGestures() }
                    val guideAnchors = rememberGuideAnchorBounds()
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalAppZone provides appZone,
                        LocalGuideAnchors provides guideAnchors,
                        com.x500x.cursimple.feature.plugin.ui.LocalEmbeddedPageGestures provides embeddedPageGestures,
                    ) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    var memoSearchOpen by rememberSaveable { mutableStateOf(false) }
                    var currentExtension by rememberSaveable { mutableStateOf<String?>(null) }
                    var linkedExtensionContent by remember {
                        mutableStateOf<Triple<com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, com.x500x.cursimple.feature.plugin.extension.ExtensionFeedItem, com.x500x.cursimple.core.kernel.model.ScheduleEvent>?>(null)
                    }
                    var openExtensionSettings by remember { mutableStateOf<String?>(null) }
                    val pendingOpenSettings by openExtensionSettingsRequest
                    LaunchedEffect(pendingOpenSettings) {
                        pendingOpenSettings?.let {
                            currentScreen = AppScreen.Plugins
                            openExtensionSettings = it
                            openExtensionSettingsRequest.value = null
                        }
                    }
                    val installedPlugins by container.pluginManager.installedPluginsFlow
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val extensionData by container.extensionCoordinator.store.all.collectAsStateWithLifecycle()
                    val extensionTitles = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
                    LaunchedEffect(installedPlugins) {
                        container.extensionCoordinator.store.ensureLoaded()
                        installedPlugins.filter { it.isExtension }.forEach { record ->
                            val manifest = runCatching { container.pluginManager.loadExtensionPackage(record).first }.getOrNull()
                            extensionTitles[record.pluginId] =
                                com.x500x.cursimple.app.extension.ExtensionCoordinator.titleOf(manifest, record)
                            extensionTitles[record.installKey] =
                                com.x500x.cursimple.app.extension.ExtensionCoordinator.titleOf(manifest, record)
                        }
                    }
                    val sidebarExtensions = com.x500x.cursimple.app.extension.activeExtensionRecords(installedPlugins, prefs.enabledPluginIds)
                        .filter { extensionData[it.pluginId]?.host?.showInSidebar != false }
                        .map { it.installKey to (extensionTitles[it.installKey] ?: it.name) }
                    var lastScreen by remember { mutableStateOf(currentScreen) }
                    LaunchedEffect(currentScreen) {
                        if (currentScreen != lastScreen) currentExtension = null
                        lastScreen = currentScreen
                    }
                    val pendingMemo by memoRequest
                    LaunchedEffect(pendingMemo) {
                        if (pendingMemo != null) {
                            currentExtension = null
                            currentScreen = AppScreen.Memos
                            if (pendingMemo!!.isEmpty()) memoRequest.value = null
                        }
                    }
                    val pendingExtensionFeed by extensionFeedRequest
                    LaunchedEffect(pendingExtensionFeed) {
                        pendingExtensionFeed?.let {
                            currentExtension = it
                            extensionFeedRequest.value = null
                        }
                    }
                    val extensionActions = remember {
                        object : com.x500x.cursimple.feature.plugin.extension.ExtensionHostActions {
                            override suspend fun loadPackage(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) =
                                container.pluginManager.loadExtensionPackage(record)

                            override suspend fun loadUi(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) =
                                container.pluginManager.loadExtensionUi(record)

                            override suspend fun loadUi(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, page: com.x500x.cursimple.core.plugin.manifest.PluginExtensionUiPage) =
                                container.pluginManager.loadExtensionUi(record, page)

                            override suspend fun isCurrent(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) =
                                container.pluginManager.getInstalledPlugins().any { it.packageRevision == record.packageRevision }

                            override suspend fun syncNow(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) =
                                container.extensionCoordinator.syncNow(record)

                            override fun onDataChanged(pluginId: String) =
                                container.extensionCoordinator.onDataChanged(pluginId)

                            override suspend fun markRead(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, itemId: String) =
                                container.extensionCoordinator.markRead(record, itemId)

                            override suspend fun setItemIgnored(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, itemId: String, ignored: Boolean) =
                                container.extensionCoordinator.setItemIgnored(record, itemId, ignored)

                            override suspend fun notificationCommand(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, command: String, payload: kotlinx.serialization.json.JsonObject) =
                                container.notificationDeliveryCoordinator.command(record, command, payload)

                            override fun openFeed(pluginId: String) {
                                currentExtension = pluginId
                            }

                            override fun openFeed(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) {
                                currentExtension = record.installKey
                            }
                        }
                    }
                    linkedExtensionContent?.let { (record, opened, event) ->
                        val item = extensionData[record.pluginId]?.items?.firstOrNull { it.id == opened.id } ?: opened
                        com.x500x.cursimple.feature.plugin.extension.ExtensionContentDetailSheet(
                            record = record,
                            item = item,
                            actions = extensionActions,
                            onDismiss = { linkedExtensionContent = null },
                            scheduleEvent = event,
                        )
                    }
                    val onScheduleScreen = currentScreen == AppScreen.Schedule && currentExtension == null
                    var subScreen by rememberSaveable { mutableStateOf<MainActivity.SubScreen?>(null) }
                    var swapTargetDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
                    var swapSourceDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
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
                            scheduleEventRepository = container.scheduleEventRepository,
                            memoRepository = container.memoRepository,
                            normalizeTimingProfile = { profile ->
                                container.normalizeTimingProfileForActiveTerm(profile)
                            },
                            onSyncCompleted = { profile -> container.refreshWidgets(profile) },
                            onAlarmSyncChecked = {
                                container.userPreferencesRepository.markAlarmPollAt(System.currentTimeMillis())
                            },
                            resolveTimingProfile = { container.widgetPreferencesRepository.timingProfileFlow.first() },
                            timingProfileFlow = container.widgetPreferencesRepository.timingProfileFlow,
                            createTermAndActivate = { name ->
                                val term = container.termProfileRepository.createTerm(name, null)
                                container.termProfileRepository.setActiveTerm(term.id)
                            },
                        ),
                    )
                    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

                    val termProfileViewModel: TermProfileViewModel = viewModel(
                        factory = TermProfileViewModelFactory(
                            termRepo = container.termProfileRepository,
                            userPrefs = container.userPreferencesRepository,
                            onActiveTermChanged = {
                                container.applyActiveTermTimingProfile()
                                container.refreshWidgets()
                            },
                        ),
                    )
                    val termProfileState by termProfileViewModel.state.collectAsStateWithLifecycle()
                    val gitHubSessionKey by container.gitHubSessionKeys.collectAsStateWithLifecycle()
                    val pluginMarketViewModel: PluginMarketViewModel = viewModel(
                        factory = PluginMarketViewModelFactory(
                            pluginManager = container.pluginManager,
                            gitHubRegistryRepository = container.gitHubRegistryRepository,
                            userPreferencesRepository = container.userPreferencesRepository,
                            accountKeyFlow = container.gitHubSessionKeys,
                        ),
                    )
                    val pluginUpdateState by pluginMarketViewModel.uiState.collectAsStateWithLifecycle()
                    val pluginUpdateBadgeVisible = pluginUpdateState.showUpdateBadge && pluginUpdateState.availableUpdateKeys().isNotEmpty()
                    PluginUpdateAutoCheck(pluginMarketViewModel)
                    // Refresh plugin and component versions at startup rather than displaying cached releases.
                    androidx.compose.runtime.LaunchedEffect(prefs.loaded, prefs.pluginSources, prefs.componentSources) {
                        if (!prefs.loaded) return@LaunchedEffect
                        pluginMarketViewModel.setSources(prefs.pluginSources, prefs.componentSources)
                        kotlinx.coroutines.delay(PLUGIN_PREFETCH_DELAY_MILLIS)
                        pluginMarketViewModel.refreshOnEnter()
                    }
                    fun setActiveTermStartDate(date: LocalDate?) {
                        prefsViewModel.markTermStartUserDecided()
                        val activeTermId = termProfileState.activeTermId
                        if (activeTermId.isNotBlank()) {
                            termProfileViewModel.setStartDate(activeTermId, date)
                        } else {
                            prefsViewModel.setTermStartDate(date)
                        }
                    }

                    val updateNotice = UpdateNoticeState(
                        versionCode = prefs.updateNoticeVersionCode,
                        versionName = prefs.updateNoticeVersionName,
                        ignoredVersionCode = prefs.ignoredUpdateVersionCode,
                    )
                    val updateBadgeVisible = shouldShowUpdateBadge(updateNotice, BuildConfig.VERSION_CODE)

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()
                    val drawerGesturesEnabled = !scheduleState.isSyncing && scheduleState.pendingWebSession == null &&
                        (drawerState.isOpen || (currentExtension == null && !embeddedPageGestures.ownsContentGestures))
                    var showDatePicker by rememberSaveable { mutableStateOf(false) }
                    var showCurrentWeekDialog by rememberSaveable { mutableStateOf(false) }
                    var pendingCurrentWeek by rememberSaveable { mutableStateOf<Int?>(null) }
                    var showTermStartReminder by rememberSaveable { mutableStateOf(false) }
                    var autoPromptedThisSession by rememberSaveable { mutableStateOf(false) }
                    var announcementVisible by remember { mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(
                        prefs.loaded, prefs.termStartDate, prefs.disclaimerAccepted,
                        announcementVisible,
                    ) {
                        // Release announcements take priority over the missing term-date prompt.
                        val blocked = announcementVisible
                        if (blocked && showTermStartReminder) {
                            showTermStartReminder = false
                            autoPromptedThisSession = false
                        }
                        if (prefs.loaded && prefs.disclaimerAccepted && prefs.termStartDate == null &&
                            !autoPromptedThisSession && !blocked
                        ) {
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
                                AppOutlinedButton(onClick = {
                                    showTermStartReminder = false
                                    showDatePicker = true
                                }) { Text(stringResource(R.string.main_go_to_settings)) }
                            },
                            dismissButton = {
                                AppOutlinedButton(onClick = { showTermStartReminder = false }) {
                                    Text(stringResource(R.string.main_later))
                                }
                            },
                        )
                    }
                    // Explain missed alarms after a force-stop.
                    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                    var showForceStopPrompt by remember {
                        mutableStateOf(com.x500x.cursimple.app.reminder.ForceStopMonitor.promptPending(appContext))
                    }
                    if (showForceStopPrompt) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {
                                showForceStopPrompt = false
                                com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(appContext)
                            },
                            title = { Text(stringResource(R.string.force_stop_prompt_title)) },
                            text = { Text(stringResource(R.string.force_stop_prompt_body)) },
                            confirmButton = {
                                androidx.compose.material3.Button(onClick = {
                                    showForceStopPrompt = false
                                    com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(appContext)
                                    currentScreen = AppScreen.Settings
                                    subScreen = null
                                    openSettingsDestination = SettingsDestinationKey.Permissions
                                }) { Text(stringResource(R.string.main_go_to_settings)) }
                            },
                            dismissButton = {
                                AppOutlinedButton(onClick = {
                                    showForceStopPrompt = false
                                    com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(appContext)
                                }) { Text(stringResource(R.string.main_later)) }
                            },
                        )
                    }
                    var showUpdateCheckDialog by rememberSaveable { mutableStateOf(false) }
                    // Automatic checks report updates through badges only.
                    AutomaticUpdateCheck(
                        autoCheckEnabled = prefs.autoUpdateEnabled,
                        betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                        updateNotice = updateNotice,
                        onUpdateFound = prefsViewModel::setUpdateNotice,
                        onUpdateNoticeCleared = prefsViewModel::clearUpdateNotice,
                    )
                    if (showUpdateCheckDialog) {
                        UpdateCheckDialog(
                            autoCheckEnabled = prefs.autoUpdateEnabled,
                            betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                            ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                            updateNotice = updateNotice,
                            onAutoCheckEnabledChange = prefsViewModel::setAutoUpdateEnabled,
                            onBetaUpdatesEnabledChange = prefsViewModel::setBetaUpdatesEnabled,
                            onIgnoreUpdateVersion = prefsViewModel::setIgnoredUpdateVersionCode,
                            onUpdateFound = prefsViewModel::setUpdateNotice,
                            onUpdateNoticeCleared = prefsViewModel::clearUpdateNotice,
                            onDismiss = { showUpdateCheckDialog = false },
                        )
                    }
                    ReleaseAnnouncementGate(
                        lastSeenVersionCode = prefs.lastSeenVersionCode,
                        onSeen = prefsViewModel::setLastSeenVersionCode,
                        onDialogVisibleChange = { announcementVisible = it },
                    )
                    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
                    var showThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var showAppLanguageDialog by rememberSaveable { mutableStateOf(false) }
                    var showWidgetThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var showClearTermStartConfirm by rememberSaveable { mutableStateOf(false) }
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
                    // Save consumed feedback across Activity recreation so locale changes cannot replay old messages.
                    var lastShownStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
                    var lastSuppressedSyncCount by rememberSaveable { mutableIntStateOf(0) }
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
                    scheduleState.pendingImport?.let { pending ->
                        ImportDiffDialog(
                            diff = pending.diff,
                            suggestedTermName = pending.suggestedTermName,
                            onOverwrite = scheduleViewModel::confirmPendingImportOverwrite,
                            onCreateNewTerm = scheduleViewModel::confirmPendingImportAsNewTerm,
                            onDismiss = scheduleViewModel::dismissPendingImport,
                        )
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
                        prefs.themeCustomColorArgb,
                    ) {
                        if (scheduleState.initialized) {
                            container.refreshWidgets(scheduleState.timingProfile)
                        }
                    }

                    val effectiveTermStart = prefs.termStartDate
                    val today = remember(prefs.debugForcedDateTime, appZone) {
                        prefs.debugForcedDateTime?.toLocalDate() ?: LocalDate.now(appZone)
                    }
                    val pendingScheduleDate by scheduleDateRequest
                    LaunchedEffect(pendingScheduleDate, today) {
                        val target = pendingScheduleDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        scheduleDateRequest.value = null
                        if (target != null) {
                            currentScreen = AppScreen.Schedule
                            currentExtension = null
                            subScreen = null
                            scheduleViewMode = ScheduleViewMode.Day
                            dayOffset = ChronoUnit.DAYS.between(today, target).toInt()
                        }
                    }
                    val currentWeekIndex = resolveWeekIndexForDate(effectiveTermStart, today)
                    val dayWeekIndex = resolveWeekIndexForDate(
                        effectiveTermStart,
                        today.plusDays(dayOffset.toLong()),
                    )
                    val displayedWeekIndex = when (scheduleViewMode) {
                        ScheduleViewMode.Week -> currentWeekIndex + weekOffset
                        ScheduleViewMode.Day -> dayWeekIndex
                    }
                    // Track user-added blank weeks separately from course-derived weeks.
                    val activeTermExtraWeeks = termProfileState.terms
                        .firstOrNull { it.id == termProfileState.activeTermId }
                        ?.extraWeekCount
                        ?: 0
                    // Exclude the viewed week from inferred totals to avoid a growth loop.
                    val derivedWeeks = remember(
                        scheduleState.schedule,
                        scheduleState.manualCourses,
                        currentWeekIndex,
                    ) {
                        derivedWeekCount(
                            schedule = scheduleState.schedule,
                            manualCourses = scheduleState.manualCourses,
                            currentWeek = currentWeekIndex,
                        )
                    }
                    val weekPickerTotalWeeks = derivedWeeks + activeTermExtraWeeks
                    // Use an atomic repository update; UI values can lag repeated taps.
                    val addWeek: () -> Unit = {
                        scope.launch { container.termProfileRepository.adjustActiveTermExtraWeekCount(1) }
                    }
                    val deleteWeek: (Int) -> Unit = { week ->
                        // Only user-added weeks beyond the course range can be removed.
                        if (week > derivedWeeks) {
                            scope.launch { container.termProfileRepository.adjustActiveTermExtraWeekCount(-1) }
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerGesturesEnabled,
                        drawerContent = {
                            AppDrawer(
                                currentScreen = currentScreen,
                                extensionEntries = sidebarExtensions,
                                currentExtension = currentExtension,
                                onSelectExtension = {
                                    currentExtension = it
                                    scope.launch { drawerState.close() }
                                },
                                termStartDate = prefs.termStartDate,
                                currentWeekIndex = currentWeekIndex,
                                appVersionName = BuildConfig.VERSION_NAME,
                                updateBadgeVisible = updateBadgeVisible,
                                pluginUpdateBadgeVisible = pluginUpdateBadgeVisible,
                                onSelectScreen = {
                                    currentScreen = it
                                    currentExtension = null
                                    scope.launch { drawerState.close() }
                                },
                                onPickThemeAccent = {
                                    scope.launch { drawerState.close() }
                                    showThemeAccentDialog = true
                                },
                                onOpenTemporaryOverrides = {
                                scope.launch { drawerState.close() }
                                currentScreen = AppScreen.Settings
                                subScreen = null
                                openSettingsDestination = SettingsDestinationKey.TemporaryOverrides
                            },
                            // Open update checks directly from the drawer.
                            onOpenUpdateCheck = {
                                scope.launch { drawerState.close() }
                                showUpdateCheckDialog = true
                            },
                            onPickScheduleBackground = {
                                    scope.launch { drawerState.close() }
                                    openSettingsDestination = SettingsDestinationKey.ScheduleBackground
                                    currentScreen = AppScreen.Settings
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
                                        if (onScheduleScreen) {
                                            // Emit press interactions explicitly because detectTapGestures supplies no ripple.
                                            val weekTitleInteraction = remember { MutableInteractionSource() }
                                            Column(
                                                    modifier = Modifier
                                                        .guideAnchor(GuideAnchor.WeekTitle)
                                                    .indication(weekTitleInteraction, ripple())
                                                    .pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onPress = { offset ->
                                                                val press = PressInteraction.Press(offset)
                                                                weekTitleInteraction.emit(press)
                                                                val released = tryAwaitRelease()
                                                                weekTitleInteraction.emit(
                                                                    if (released) {
                                                                        PressInteraction.Release(press)
                                                                    } else {
                                                                        PressInteraction.Cancel(press)
                                                                    },
                                                                )
                                                            },
                                                            onTap = { showWeekMenu = true },
                                                            onDoubleTap = {
                                                                weekOffset = 0
                                                                dayOffset = 0
                                                            },
                                                        )
                                                    }
                                                    // Keep padding inside the ripple and leave room for rounded edges.
                                                    .padding(
                                                        horizontal = 20.dp,
                                                        vertical = 4.dp,
                                                    ),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                val isCurrentWeek = isCurrentTermWeek(
                                                    termStart = effectiveTermStart,
                                                    displayedWeekIndex = displayedWeekIndex,
                                                    currentWeekIndex = currentWeekIndex,
                                                )
                                                Surface(
                                                    color = if (isCurrentWeek) MaterialTheme.colorScheme.primaryContainer
                                                    else androidx.compose.ui.graphics.Color.Transparent,
                                                    // Apply rounded clipping only when the current-week badge is visible.
                                                    shape = if (isCurrentWeek) RoundedCornerShape(50)
                                                    else androidx.compose.ui.graphics.RectangleShape,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(
                                                            horizontal = if (isCurrentWeek) 12.dp else 0.dp,
                                                            vertical = if (isCurrentWeek) 2.dp else 0.dp,
                                                        ),
                                                    ) {
                                                        Text(
                                                            // Missing term dates use week one; the warning icon provides the date hint.
                                                            text = LocalContext.current.termWeekText(
                                                                if (effectiveTermStart == null) termWeekLabel(displayedWeekIndex)
                                                                else termWeekLabel(effectiveTermStart, displayedWeekIndex),
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
                                                text = currentExtension?.let { extensionTitles[it] }
                                                    ?: stringResource(currentScreen.labelRes),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } },
                                                modifier = Modifier.guideAnchor(GuideAnchor.Drawer),
                                            ) {
                                                Box {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Menu,
                                                        contentDescription = stringResource(R.string.main_open_drawer),
                                                    )
                                                    if (updateBadgeVisible || pluginUpdateBadgeVisible) {
                                                        UpdateBadgeDot(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 2.dp, y = (-2).dp)
                                                                .testTag("menu-update-dot"),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    actions = {
                                        if (currentExtension == null && currentScreen == AppScreen.Memos) {
                                            AppToolbarIconButton(
                                                icon = Icons.Rounded.Search,
                                                contentDescription = stringResource(R.string.main_memo_search),
                                                onClick = { memoSearchOpen = !memoSearchOpen },
                                                selected = memoSearchOpen,
                                                modifier = Modifier.padding(end = 12.dp).testTag("memo-search-action"),
                                            )
                                        }
                                        if (prefs.loaded && prefs.termStartDate == null && onScheduleScreen) {
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
                                        if (onScheduleScreen) {
                                            ScheduleToolbarButton(
                                                label = stringResource(
                                                    if (scheduleViewMode == ScheduleViewMode.Week) R.string.schedule_view_mode_week
                                                    else R.string.schedule_view_mode_day,
                                                ),
                                                selected = scheduleViewMode == ScheduleViewMode.Week,
                                                onClick = {
                                                    scheduleViewMode = if (scheduleViewMode == ScheduleViewMode.Week) {
                                                        dayOffset = 0
                                                        ScheduleViewMode.Day
                                                    } else {
                                                        ScheduleViewMode.Week
                                                    }
                                                },
                                            )
                                            Box {
                                                AppToolbarIconButton(
                                                    icon = Icons.Rounded.Add,
                                                    contentDescription = stringResource(R.string.main_add_to_schedule),
                                                    onClick = { showAddMenu = true },
                                                    modifier = Modifier.guideAnchor(GuideAnchor.Add),
                                                )
                                                DropdownMenu(
                                                    expanded = showAddMenu,
                                                    onDismissRequest = { showAddMenu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.main_import_from_school)) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.School,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            subScreen = MainActivity.SubScreen.SchoolImport
                                                        },
                                                    )
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
                                val routeTarget = currentScreen to currentExtension
                                AnimatedContent(
                                    targetState = routeTarget,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith
                                            (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 8 })
                                    },
                                    label = "main_route_transition",
                                    modifier = Modifier.fillMaxSize(),
                                ) { (targetScreen, targetExtension) ->
                                val extensionRecord = targetExtension?.let { id ->
                                    installedPlugins.firstOrNull { it.installKey == id && it.isExtension }
                                        ?: com.x500x.cursimple.app.extension.activeExtensionRecords(installedPlugins, prefs.enabledPluginIds).firstOrNull { it.pluginId == id }
                                }
                                if (extensionRecord != null) {
                                    com.x500x.cursimple.feature.plugin.extension.ExtensionFeedScreen(
                                        record = extensionRecord,
                                        actions = extensionActions,
                                        onOpenSettings = {
                                            openExtensionSettings = extensionRecord.installKey
                                            currentScreen = AppScreen.Plugins
                                            currentExtension = null
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else when (targetScreen) {
                                    AppScreen.Schedule -> ScheduleRoute(
                                        viewModel = scheduleViewModel,
                                        overrideTermStart = prefs.termStartDate,
                                        weekOffset = weekOffset,
                                        // Bound backward navigation by the current pre-term week or week one.
                                        minWeekOffset = (1 - currentWeekIndex).coerceAtMost(0),
                                        maxWeekOffset = weekPickerTotalWeeks - currentWeekIndex,
                                        onAddWeek = addWeek,
                                        onPrevWeek = { weekOffset -= 1 },
                                        onNextWeek = { weekOffset += 1 },
                                        onWeekOffsetChange = { weekOffset = it },
                                        viewMode = scheduleViewMode,
                                        dayOffset = dayOffset,
                                        onPrevDay = { dayOffset -= 1 },
                                        onNextDay = { dayOffset += 1 },
                                        onDayOffsetChange = { dayOffset = it },
                                        onResetDay = { dayOffset = 0 },
                                        onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                        onSetViewMode = { scheduleViewMode = it },
                                        onOpenLinkedEvent = { event ->
                                            val found = installedPlugins.asSequence().filter { it.isExtension }.mapNotNull { record ->
                                                val data = extensionData[record.pluginId] ?: return@mapNotNull null
                                                com.x500x.cursimple.app.extension.ExtensionScheduleBridge.findSource(event, data)?.let { record to it }
                                            }.firstOrNull()
                                            if (found != null) linkedExtensionContent = Triple(found.first, found.second, event)
                                            found != null
                                        },
                                        scheduleTextStyle = prefs.scheduleTextStyle,
                                        scheduleCardStyle = prefs.scheduleCardStyle,
                                        scheduleBackground = prefs.scheduleBackground,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        customColorsAdaptToTheme = prefs.scheduleCustomColorsAdaptToTheme,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        holidayCalendar = prefs.holidayCalendar,
                                        onUpsertTemporaryScheduleOverride = prefsViewModel::upsertTemporaryScheduleOverride,
                                        onRemoveTemporaryScheduleOverride = prefsViewModel::removeTemporaryScheduleOverride,
                                        onUpsertHolidayEntry = prefsViewModel::upsertHolidayCalendarEntry,
                                        onRemoveHolidayEntry = { date ->
                                            prefsViewModel.removeHolidayCalendarEntry(date.toString())
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> PluginMarketRoute(
                                        pluginMarketViewModel = pluginMarketViewModel,
                                        pluginSources = prefs.pluginSources,
                                        componentSources = prefs.componentSources,
                                        accountKey = gitHubSessionKey,
                                        enabledPluginIds = prefs.enabledPluginIds,
                                        syncingPluginId = if (scheduleState.isSyncing) scheduleState.pluginId else null,
                                        missingComponents = scheduleState.missingComponents,
                                        pendingWebSession = scheduleState.pendingWebSession,
                                        onSetPluginEnabled = prefsViewModel::setPluginEnabled,
                                        onSyncPlugin = scheduleViewModel::syncSchedule,
                                        onCompleteWebSession = scheduleViewModel::completeWebSession,
                                        onCancelWebSession = scheduleViewModel::cancelWebSession,
                                        modifier = Modifier.fillMaxSize(),
                                        syncStatusMessage = scheduleState.statusMessage,
                                        extensionActions = extensionActions,
                                        openExtensionPluginId = openExtensionSettings,
                                        onExtensionOpenConsumed = { openExtensionSettings = null },
                                    )

                                    AppScreen.Courses -> CourseLibraryRoute(
                                        viewModel = scheduleViewModel,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        maxWeekCount = resolveWeekPickerTotalWeeks(
                                            schedule = scheduleState.schedule,
                                            manualCourses = scheduleState.manualCourses,
                                            currentWeek = currentWeekIndex,
                                            extraWeekCount = activeTermExtraWeeks,
                                        ),
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Memos -> MemoRoute(
                                        viewModel = scheduleViewModel,
                                        modifier = Modifier.fillMaxSize(),
                                        searchOpen = memoSearchOpen,
                                        onCloseSearch = { memoSearchOpen = false },
                                        openNoteId = pendingMemo?.takeIf { it.isNotBlank() },
                                        onNoteOpened = { memoRequest.value = null },
                                    )

                                    AppScreen.Reminders -> RemindersScreen(
                                        classNotice = prefs.classNotice,
                                        onClassNoticeEnabledChange = prefsViewModel::setClassNoticeEnabled,
                                        onClassNoticeAdvanceMinutesChange =
                                            prefsViewModel::setClassNoticeAdvanceMinutes,
                                        onClassNoticeHeadsUpChange =
                                            prefsViewModel::setClassNoticeHeadsUpEnabled,
                                        onClassNoticeLockScreenChange =
                                            prefsViewModel::setClassNoticeLockScreenEnabled,
                                        onClassNoticeFocusChange =
                                            prefsViewModel::setClassNoticeFocusNotificationEnabled,
                                        onClassNoticeSkinChange = prefsViewModel::setClassNoticeSkin,
                                        onClassNoticeAnimationChange = prefsViewModel::setClassNoticeAnimation,
                                        onClassNoticeBlurChange = prefsViewModel::setClassNoticeBlurEnabled,
                                        onClassNoticeBlurStrengthChange =
                                            prefsViewModel::setClassNoticeBlurStrength,
                                        onClassNoticeBannerDurationChange =
                                            prefsViewModel::setClassNoticeBannerDurationSeconds,
                                        alarmsContent = { alarmsModifier ->
                                            SettingsRoute(
                                                viewModel = scheduleViewModel,
                                                alarmRingtoneUri = prefs.alarmRingtoneUri,
                                                alarmAlertMode = prefs.alarmAlertMode,
                                                alarmRingDurationSeconds = prefs.alarmRingDurationSeconds,
                                                alarmRepeatIntervalSeconds = prefs.alarmRepeatIntervalSeconds,
                                                alarmRepeatCount = prefs.alarmRepeatCount,
                                                onAlarmRingtoneUriChange = prefsViewModel::setAlarmRingtoneUri,
                                                onAlarmAlertModeChange = prefsViewModel::setAlarmAlertMode,
                                                onAlarmRingDurationSecondsChange = prefsViewModel::setAlarmRingDurationSeconds,
                                                onAlarmRepeatIntervalSecondsChange = prefsViewModel::setAlarmRepeatIntervalSeconds,
                                                onAlarmRepeatCountChange = prefsViewModel::setAlarmRepeatCount,
                                                onPickSystemRingtone = ::pickSystemRingtone,
                                                onPickLocalAudio = ::pickLocalAudio,
                                                modifier = alarmsModifier,
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Settings -> AppSettingsRoute(
                                        themeMode = prefs.themeMode,
                                        themeAccentLabel = themeAccentLabel(prefs.themeAccent, prefs.themeCustomColorArgb),
                                        termStartDate = prefs.termStartDate,
                                        termStartUserDecided = prefs.termStartUserDecided,
                                        scheduleTextStyle = prefs.scheduleTextStyle,
                                        scheduleCardStyle = prefs.scheduleCardStyle,
                                        scheduleBackground = prefs.scheduleBackground,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        scheduleCustomColorsAdaptToTheme =
                                            prefs.scheduleCustomColorsAdaptToTheme,
                                        widgetThemePreferences = widgetPrefs,
                                        currentWeekIndex = currentWeekIndex,
                                        alarmRingDurationSeconds = prefs.alarmRingDurationSeconds,
                                        alarmRepeatIntervalSeconds = prefs.alarmRepeatIntervalSeconds,
                                        alarmRepeatCount = prefs.alarmRepeatCount,
                                        temporaryScheduleOverrides = prefs.temporaryScheduleOverrides,
                                        appTimeZoneId = prefs.appTimeZoneId,
                                        pluginSources = prefs.pluginSources,
                                        componentSources = prefs.componentSources,
                                        marketSourceServices = container.marketSourceServices,
                                        privateFilesProviderEnabled = prefs.privateFilesProviderEnabled,
                                        webDavUrl = prefs.webDavUrl,
                                        webDavUsername = prefs.webDavUsername,
                                        webDavPassword = prefs.webDavPassword,
                                        aiImportApiUrl = prefs.aiImportApiUrl,
                                        aiImportApiKey = prefs.aiImportApiKey,
                                        aiImportModel = prefs.aiImportModel,
                                        aiImportTimeoutSeconds = prefs.aiImportTimeoutSeconds,
                                        advancedToolsEnabled = prefs.advancedToolsEnabled,
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
                                        onScheduleAutoShrinkLongTitlesChange = prefsViewModel::setScheduleAutoShrinkLongTitles,
                                        onScheduleTruncationEllipsisChange = prefsViewModel::setScheduleTruncationEllipsis,
                                        classNotice = prefs.classNotice,
                                        onClassNoticeEnabledChange = prefsViewModel::setClassNoticeEnabled,
                                        onClassNoticeAdvanceMinutesChange =
                                            prefsViewModel::setClassNoticeAdvanceMinutes,
                                        onClassNoticeHeadsUpChange =
                                            prefsViewModel::setClassNoticeHeadsUpEnabled,
                                        onClassNoticeLockScreenChange =
                                            prefsViewModel::setClassNoticeLockScreenEnabled,
                                        onClassNoticeFocusChange =
                                            prefsViewModel::setClassNoticeFocusNotificationEnabled,
                                        onClassNoticeSkinChange =
                                            prefsViewModel::setClassNoticeSkin,
                                        onClassNoticeAnimationChange =
                                            prefsViewModel::setClassNoticeAnimation,
                                        onClassNoticeBlurChange =
                                            prefsViewModel::setClassNoticeBlurEnabled,
                                        onClassNoticeBlurStrengthChange =
                                            prefsViewModel::setClassNoticeBlurStrength,
                                        onClassNoticeBannerDurationChange =
                                            prefsViewModel::setClassNoticeBannerDurationSeconds,
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
                                        onScheduleBackgroundImageTransparencyPercentChange =
                                            prefsViewModel::setScheduleBackgroundImageTransparencyPercent,
                                        onClearScheduleBackgroundImage = prefsViewModel::clearScheduleBackgroundImage,
                                        onScheduleBackgroundUseHeaderColor =
                                            prefsViewModel::setScheduleBackgroundUseHeaderColor,
                                        onScheduleCustomColorsAdaptToThemeChange =
                                            prefsViewModel::setScheduleCustomColorsAdaptToTheme,
                                        onScheduleNodeColumnTimeEnabledChange = prefsViewModel::setScheduleNodeColumnTimeEnabled,
                                        onScheduleSaturdayVisibleChange = prefsViewModel::setScheduleSaturdayVisible,
                                        onScheduleWeekStartDayChange =
                                            prefsViewModel::setScheduleWeekStartDay,
                                        onCourseDragEnabledChange =
                                            prefsViewModel::setCourseDragEnabled,
                                        onSchedulePinchZoomEnabledChange =
                                            prefsViewModel::setSchedulePinchZoomEnabled,
                                        onTodayOverviewEnabledChange = prefsViewModel::setTodayOverviewEnabled,
                                        onScheduleWeekendVisibleChange = prefsViewModel::setScheduleWeekendVisible,
                                        onScheduleRowFitModeChange = prefsViewModel::setScheduleRowFitMode,
                                        onScheduleLocationVisibleChange = prefsViewModel::setScheduleLocationVisible,
                                        onScheduleTeacherVisibleChange = prefsViewModel::setScheduleTeacherVisible,
                                        onTotalScheduleDisplayChange = prefsViewModel::setTotalScheduleDisplayEnabled,
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
                                        alarmKeepAliveEnabled = prefs.alarmKeepAliveEnabled,
                                        onAlarmKeepAliveEnabledChange =
                                            prefsViewModel::setAlarmKeepAliveEnabled,
                                        onOpenWidgetPicker = { showWidgetPicker = true },
                                        onPickWidgetThemeAccent = { showWidgetThemeAccentDialog = true },
                                        onWidgetBackgroundImageUriChange = widgetPrefsViewModel::setWidgetBackgroundImageUri,
                                        onClearWidgetBackgroundImage = widgetPrefsViewModel::clearWidgetBackgroundImage,
                                        onWidgetBackgroundImageTransparencyPercentChange =
                                            widgetPrefsViewModel::setWidgetBackgroundImageTransparencyPercent,
                                        vendorPermissionAcks = prefs.vendorPermissionAcks,
                                        onVendorPermissionAckChange = prefsViewModel::setVendorPermissionAck,
                                        onWidgetOpenAppOnDoubleClickChange =
                                            widgetPrefsViewModel::setWidgetOpenAppOnDoubleClickEnabled,
                                        onAppTimeZoneChange = prefsViewModel::setAppTimeZoneId,
                                        onPluginSourcesChange = prefsViewModel::setPluginSources,
                                        onComponentSourcesChange = prefsViewModel::setComponentSources,
                                        onPrivateFilesProviderEnabledChange =
                                            prefsViewModel::setPrivateFilesProviderEnabled,
                                        onWebDavSettingsChange = prefsViewModel::setWebDavSettings,
                                        onTestWebDavSettings = { config ->
                                            withContext(Dispatchers.IO) {
                                                runCatching { webDavClient.test(config) }
                                            }
                                        },
                                        onAiImportSettingsChange = prefsViewModel::setAiImportSettings,
                                        onSetAdvancedTools = prefsViewModel::setAdvancedToolsEnabled,
                                        onSetDebugForcedDateTime = prefsViewModel::setDebugForcedDateTime,
                                        onResetScheduleAppearanceAndDisplay =
                                            prefsViewModel::resetScheduleAppearanceAndDisplay,
                                        onReplayFirstRunGuide = {
                                            currentScreen = AppScreen.Schedule
                                            subScreen = null
                                            prefsViewModel.setFirstRunGuideCompleted(false)
                                        },
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
                                        onOpenCourseSwap = { target, source ->
                                            swapTargetDate = target
                                            swapSourceDate = source
                                            subScreen = MainActivity.SubScreen.CourseSwap
                                        },
                                        courseTitleOf = { id ->
                                            scheduleState.schedule
                                                .allCoursesWith(scheduleState.manualCourses)
                                                .firstOrNull { it.id == id }
                                                ?.title
                                        },
                                        scheduleCourses = scheduleState.schedule
                                            .allCoursesWith(scheduleState.manualCourses),
                                        scheduleTimingProfile = scheduleState.timingProfile,
                                        onApplyCancelPlan = prefsViewModel::applyCancelCoursePlan,
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
                                        advancedToolsEnabled = prefs.advancedToolsEnabled,
                                        onSetAdvancedTools = prefsViewModel::setAdvancedToolsEnabled,
                                        autoUpdateEnabled = prefs.autoUpdateEnabled,
                                        betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                                        ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                                        updateNotice = updateNotice,
                                        onAutoUpdateEnabledChange = prefsViewModel::setAutoUpdateEnabled,
                                        onBetaUpdatesEnabledChange = prefsViewModel::setBetaUpdatesEnabled,
                                        onIgnoreUpdateVersion = prefsViewModel::setIgnoredUpdateVersionCode,
                                        onUpdateFound = prefsViewModel::setUpdateNotice,
                                        onUpdateNoticeCleared = prefsViewModel::clearUpdateNotice,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
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
                                weekStartDay = prefs.scheduleDisplay.weekStartDay,
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
                                onApplyImport = { schedule, manual, newTermName, onDone ->
                                    scheduleViewModel.applyImportedSchedule(
                                        schedule = schedule,
                                        manualCourses = manual,
                                        asNewTermNamed = newTermName,
                                        onComplete = onDone,
                                    )
                                },
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
                        MainActivity.SubScreen.CourseSwap -> {
                            val today = LocalAppZone.current.today()
                            val swapLeft = swapTargetDate ?: today
                            val swapRight = swapSourceDate ?: today.plusDays(1)
                            var pickingSwapLeft by rememberSaveable { mutableStateOf(false) }
                            var pickingSwapRight by rememberSaveable { mutableStateOf(false) }
                            CourseSwapScreen(
                                leftDate = swapLeft,
                                rightDate = swapRight,
                                timingProfile = scheduleState.timingProfile,
                                termStartDate = prefs.termStartDate,
                                allCourses = scheduleState.schedule.allCoursesWith(scheduleState.manualCourses),
                                overrides = prefs.temporaryScheduleOverrides,
                                holidayCalendar = prefs.holidayCalendar,
                                onPickLeftDate = { pickingSwapLeft = true },
                                onPickRightDate = { pickingSwapRight = true },
                                onMove = { course, from, to, startNode, endNode ->
                                    val natural = scheduleState.schedule
                                        .allCoursesWith(scheduleState.manualCourses)
                                        .firstOrNull { it.id == course.id }
                                        ?.time ?: course.time
                                    prefsViewModel.applyCourseMove(
                                        planCourseMove(
                                            overrides = prefs.temporaryScheduleOverrides,
                                            courseId = course.id,
                                            from = from,
                                            to = to,
                                            toStartNode = startNode,
                                            toEndNode = endNode,
                                            naturalStartNode = natural.startNode,
                                            naturalEndNode = natural.endNode,
                                            newId = { java.util.UUID.randomUUID().toString() },
                                        ),
                                    )
                                },
                                onBack = { subScreen = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (pickingSwapLeft) {
                                SettingsDatePickerDialog(
                                    initial = swapLeft,
                                    onConfirm = { swapTargetDate = it; pickingSwapLeft = false },
                                    onDismiss = { pickingSwapLeft = false },
                                )
                            }
                            if (pickingSwapRight) {
                                SettingsDatePickerDialog(
                                    initial = swapRight,
                                    onConfirm = { swapSourceDate = it; pickingSwapRight = false },
                                    onDismiss = { pickingSwapRight = false },
                                )
                            }
                            androidx.activity.compose.BackHandler { subScreen = null }
                        }
                        MainActivity.SubScreen.SchoolImport -> {
                            SchoolImportRoute(
                                pluginMarketViewModel = pluginMarketViewModel,
                                pluginSources = prefs.pluginSources,
                                componentSources = prefs.componentSources,
                                accountKey = gitHubSessionKey,
                                syncingPluginId = if (scheduleState.isSyncing) scheduleState.pluginId else null,
                                syncStatusMessage = scheduleState.statusMessage,
                                pendingWebSession = scheduleState.pendingWebSession,
                                onSyncPlugin = scheduleViewModel::syncSchedule,
                                onCompleteWebSession = scheduleViewModel::completeWebSession,
                                onCancelWebSession = scheduleViewModel::cancelWebSession,
                                onBack = { subScreen = null },
                                onBrowseAllPlugins = {
                                    subScreen = null
                                    currentScreen = AppScreen.Plugins
                                },
                                onAddCourseManually = {
                                    subScreen = null
                                    showAddCourseDialog = true
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.activity.compose.BackHandler(
                                enabled = scheduleState.pendingWebSession == null,
                            ) { subScreen = null }
                        }
                        null -> Unit
                    }

                    // Let the open drawer consume Back first; other pages return to the timetable.
                    androidx.activity.compose.BackHandler(
                        enabled = subScreen == null &&
                            (currentScreen != AppScreen.Schedule || currentExtension != null) &&
                            !drawerState.isOpen,
                    ) {
                        if (currentExtension != null) currentExtension = null else currentScreen = AppScreen.Schedule
                    }

                    if (showDatePicker) {
                        TermStartDatePicker(
                            initial = prefs.termStartDate,
                            onDismiss = { showDatePicker = false },
                            onConfirm = { date ->
                                setActiveTermStartDate(date)
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
                                    // Locale changes require Activity recreation to reattach the base context.
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
                            customArgb = prefs.themeCustomColorArgb,
                            onDismiss = { showThemeAccentDialog = false },
                            onSelect = {
                                prefsViewModel.setThemeAccent(it)
                                showThemeAccentDialog = false
                            },
                            onSelectCustom = {
                                prefsViewModel.setThemeCustomColor(it)
                                showThemeAccentDialog = false
                            },
                        )
                    }

                    if (showWidgetThemeAccentDialog) {
                        ThemeAccentDialog(
                            current = widgetPrefs.themeAccent,
                            customArgb = if (widgetPrefs.followsAppThemeAccent) {
                                prefs.themeCustomColorArgb
                            } else {
                                widgetPrefs.customColorArgb
                            },
                            onDismiss = { showWidgetThemeAccentDialog = false },
                            onSelect = {
                                widgetPrefsViewModel.setWidgetThemeAccent(it)
                                showWidgetThemeAccentDialog = false
                            },
                            onSelectCustom = {
                                widgetPrefsViewModel.setWidgetThemeCustomColor(it)
                                showWidgetThemeAccentDialog = false
                            },
                            followAppSelected = widgetPrefs.followsAppThemeAccent,
                            onSelectFollowApp = {
                                widgetPrefsViewModel.followAppThemeAccent()
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
                                AppOutlinedButton(onClick = {
                                    setActiveTermStartDate(null)
                                    showClearTermStartConfirm = false
                                }) { Text(stringResource(R.string.main_clear)) }
                            },
                            dismissButton = {
                                AppOutlinedButton(onClick = { showClearTermStartConfirm = false }) { Text(stringResource(R.string.main_cancel)) }
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
                            derivedWeeks = derivedWeeks,
                            onAddWeek = addWeek,
                            onDeleteWeek = deleteWeek,
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
                                pendingCurrentWeek = selectedWeek
                                showWeekMenu = false
                            },
                            onDismiss = { showWeekMenu = false },
                        )
                    }

                    pendingCurrentWeek?.let { week ->
                        val derivedStart = deriveTermStartForCurrentWeek(today = today, currentWeek = week)
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { pendingCurrentWeek = null },
                            title = { Text(stringResource(R.string.main_set_current_week_confirm_title, week)) },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.main_set_current_week_confirm_body,
                                        DateTimeFormatter.ofPattern("yyyy/M/d").format(derivedStart),
                                    ),
                                )
                            },
                            confirmButton = {
                                AppOutlinedButton(onClick = {
                                    setActiveTermStartDate(derivedStart)
                                    weekOffset = 0
                                    dayOffset = 0
                                    pendingCurrentWeek = null
                                }) { Text(stringResource(R.string.main_set_current_week_confirm_action)) }
                            },
                            dismissButton = {
                                AppOutlinedButton(onClick = { pendingCurrentWeek = null }) {
                                    Text(stringResource(R.string.main_cancel))
                                }
                            },
                        )
                    }

                    if (showWidgetPicker) {
                        WidgetPickerSheet(
                            onDismiss = { showWidgetPicker = false },
                            // Use Toast above the widget window; Scaffold snackbars render behind it.
                            onShowMessage = { msg ->
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    msg,
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                            vendorPermissionAcks = prefs.vendorPermissionAcks,
                            onVendorPermissionAckChange = prefsViewModel::setVendorPermissionAck,
                            pinUnsupportedOnDevice = prefs.widgetPinUnsupportedOnDevice,
                            onPinUnsupportedOnDeviceChange = prefsViewModel::setWidgetPinUnsupportedOnDevice,
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

                    if (
                        shouldShowFirstRunGuide(
                            loaded = prefs.loaded,
                            disclaimerAccepted = prefs.disclaimerAccepted,
                            guideCompleted = prefs.firstRunGuideCompleted,
                            blockingDialogVisible = showTermStartReminder || showDatePicker ||
                                announcementVisible,
                        )
                    ) {
                        FirstRunGuideOverlay(
                            onNavigate = { destination ->
                                when (destination) {
                                    GuideDestination.Schedule -> {
                                        currentScreen = AppScreen.Schedule
                                        subScreen = null
                                    }

                                    GuideDestination.SchoolImport -> {
                                        currentScreen = AppScreen.Schedule
                                        subScreen = MainActivity.SubScreen.SchoolImport
                                    }

                                    GuideDestination.Courses -> {
                                        currentScreen = AppScreen.Courses
                                        subScreen = null
                                    }

                                    GuideDestination.Plugins -> {
                                        currentScreen = AppScreen.Plugins
                                        subScreen = null
                                    }
                                }
                            },
                            onFinish = {
                                currentScreen = AppScreen.Schedule
                                subScreen = null
                                prefsViewModel.setFirstRunGuideCompleted(true)
                            },
                        )
                    }
                    }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_EXTENSION_FEED = WidgetDeepLinks.EXTRA_OPEN_COMPONENT_PAGE

        const val EXTRA_OPEN_SCHEDULE_DATE = WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE

        const val EXTRA_OPEN_EXTENSION_SETTINGS = "com.x500x.cursimple.extra.OPEN_EXTENSION_SETTINGS"

        /**
         * Settings destination from [EXTRA_OPEN_EXTENSION_SETTINGS]; clear after consumption.
         */
        internal val openExtensionSettingsRequest = androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    enum class AppScreen(
        val labelRes: Int,
        val icon: ImageVector,
    ) {
        Schedule(R.string.screen_schedule, Icons.AutoMirrored.Rounded.MenuBook),
        Courses(R.string.screen_courses, Icons.AutoMirrored.Rounded.ListAlt),
        Memos(R.string.screen_memos, Icons.Rounded.EditNote),
        Plugins(R.string.screen_plugins, Icons.Rounded.Extension),
        Reminders(R.string.screen_reminders, Icons.Rounded.Notifications),
        Settings(R.string.screen_settings, Icons.Rounded.Settings),
        About(R.string.screen_about, Icons.Rounded.Info),
    }

    enum class SubScreen { TermManagement, ImportExport, SchoolImport, CourseSwap }
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
    extensionEntries: List<Pair<String, String>>,
    currentExtension: String?,
    onSelectExtension: (String) -> Unit,
    termStartDate: LocalDate?,
    currentWeekIndex: Int,
    appVersionName: String,
    updateBadgeVisible: Boolean,
    pluginUpdateBadgeVisible: Boolean,
    onSelectScreen: (MainActivity.AppScreen) -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickScheduleBackground: () -> Unit,
    onOpenTemporaryOverrides: () -> Unit,
    onOpenUpdateCheck: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.72f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scrollState)
                .padding(start = 14.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
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
                        pluralStringResource(R.plurals.main_drawer_before_term, 1 - currentWeekIndex, 1 - currentWeekIndex)
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
                    badge = if ((updateBadgeVisible && screen == MainActivity.AppScreen.About) || (pluginUpdateBadgeVisible && screen == MainActivity.AppScreen.Plugins)) {
                        { UpdateBadgeDot() }
                    } else null,
                    selected = screen == currentScreen && currentExtension == null,
                    onClick = { onSelectScreen(screen) },
                    modifier = Modifier.height(44.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            if (extensionEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))
                extensionEntries.forEach { (pluginId, title) ->
                    NavigationDrawerItem(
                        label = {
                            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        selected = pluginId == currentExtension,
                        onClick = { onSelectExtension(pluginId) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DrawerAppearanceShortcut(
                    icon = Icons.Rounded.Style,
                    label = stringResource(R.string.main_drawer_theme_shortcut),
                    onClick = onPickThemeAccent,
                    modifier = Modifier.weight(1f),
                )
                DrawerAppearanceShortcut(
                    icon = Icons.Rounded.Wallpaper,
                    label = stringResource(R.string.main_drawer_background_shortcut),
                    onClick = onPickScheduleBackground,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DrawerAppearanceShortcut(
                    icon = Icons.Rounded.SwapHoriz,
                    label = stringResource(R.string.main_drawer_overrides_shortcut),
                    onClick = onOpenTemporaryOverrides,
                    modifier = Modifier.weight(1f),
                )
                DrawerAppearanceShortcut(
                    icon = Icons.Rounded.SystemUpdate,
                    label = stringResource(R.string.main_drawer_update_shortcut),
                    onClick = onOpenUpdateCheck,
                    badge = updateBadgeVisible,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.main_drawer_version, appVersionName),
                style = MaterialTheme.typography.labelSmall,
                color = if (updateBadgeVisible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenUpdateCheck)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DrawerAppearanceShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
) {
    AppOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
    var selectedDate by remember(initial) { mutableStateOf(initial ?: LocalDate.now(zone)) }
    // AlertDialog permits the custom calendar height without DatePickerDialog clipping.
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppOutlinedButton(onClick = { onConfirm(selectedDate) }) {
                Text(stringResource(R.string.main_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
        },
        text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (showHint) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
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
        CalendarMonthPicker(
            selected = selectedDate,
            onSelect = { selectedDate = it },
            title = {
                Text(
                    text = stringResource(R.string.main_pick_term_start),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            },
            headline = {
                val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.main_date_pattern))
                Text(
                    text = stringResource(R.string.main_term_start_value, fmt.format(selectedDate)),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            },
        )
        }
        },
    )
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
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
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
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
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
private fun themeAccentLabel(accent: ThemeAccent, customArgb: Int): String =
    if (accent == ThemeAccent.Custom) {
        stringResource(R.string.main_accent_custom_value, formatRgbHex(customArgb))
    } else {
        themeAccentOptions.firstOrNull { it.accent == accent }?.let { stringResource(it.labelRes) }
            ?: accent.name
    }

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
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
        },
    )
}

@Composable
private fun ThemeAccentDialog(
    current: ThemeAccent,
    customArgb: Int,
    onDismiss: () -> Unit,
    onSelect: (ThemeAccent) -> Unit,
    onSelectCustom: (Int) -> Unit,
    followAppSelected: Boolean = false,
    onSelectFollowApp: (() -> Unit)? = null,
) {
    var pickingCustom by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_theme)) },
        text = {
            Column {
                if (onSelectFollowApp != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectFollowApp() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = followAppSelected,
                            onClick = onSelectFollowApp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_accent_follow_app),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
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
                            selected = option.accent == current && !followAppSelected,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { pickingCustom = true }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = current == ThemeAccent.Custom && !followAppSelected,
                        onClick = { pickingCustom = true },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(customArgb)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.main_accent_custom), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = formatRgbHex(customArgb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
        },
    )
    if (pickingCustom) {
        ThemeColorPickerDialog(
            initialArgb = customArgb,
            onDismiss = { pickingCustom = false },
            onConfirm = {
                pickingCustom = false
                onSelectCustom(it)
            },
        )
    }
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

private const val PLUGIN_PREFETCH_DELAY_MILLIS = 3_000L
