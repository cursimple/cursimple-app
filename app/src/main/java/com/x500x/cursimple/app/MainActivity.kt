package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.home.PluginSyncStamp
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics

import com.x500x.cursimple.feature.plugin.ui.AppToolbarIconButton
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import com.x500x.cursimple.core.plugin.logging.PluginLogBuffer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import com.x500x.cursimple.feature.plugin.ui.AppModalBottomSheet
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker
import com.x500x.cursimple.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.x500x.cursimple.app.guide.FirstRunGuideDialog
import com.x500x.cursimple.app.guide.FirstRunPermissionsPage
import com.x500x.cursimple.app.guide.shouldShowFirstRunGuide
import com.x500x.cursimple.app.guide.shouldReviewFirstRunPermissions
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
import com.x500x.cursimple.core.kernel.model.courseWeekPreview
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.kernel.time.displayWeekStartOf
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
import androidx.compose.material.icons.rounded.Event
import com.x500x.cursimple.feature.plugin.SchoolImportRoute
import com.x500x.cursimple.feature.schedule.AddCourseDialog
import com.x500x.cursimple.feature.schedule.CourseLibraryRoute
import com.x500x.cursimple.feature.schedule.MemoRoute
import com.x500x.cursimple.feature.schedule.ScheduleItemType
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
import com.x500x.cursimple.app.navigation.toDestination
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import com.x500x.cursimple.app.navigation.toAppScreen
import com.x500x.cursimple.app.navigation.declaredLayoutShells
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber

class MainActivity : ComponentActivity() {

    private val extensionFeedRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val componentAboutRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val scheduleDateRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val memoRequest = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val memoImportRequest = androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)
    private val memoCodeImportRequest = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        memoTransferUri(intent)?.let { memoImportRequest.value = it; memoCodeImportRequest.value = null }
        memoTransferCode(intent)?.let { memoCodeImportRequest.value = it; memoImportRequest.value = null }
        intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO)?.let { memoRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_EXTENSION_FEED)?.let { extensionFeedRequest.value = it }
        intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_COMPONENT_ABOUT)?.let { componentAboutRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_EXTENSION_SETTINGS)?.let { openExtensionSettingsRequest.value = it }
        intent.getStringExtra(EXTRA_OPEN_SCHEDULE_DATE)?.let { scheduleDateRequest.value = it }
        com.x500x.cursimple.app.vendor.vendorScheduleDate(intent.dataString, packageName)?.let { scheduleDateRequest.value = it }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClassScheduleApplication).appContainer
        if (savedInstanceState == null) {
            memoTransferUri(intent)?.let { memoImportRequest.value = it; memoCodeImportRequest.value = null }
            memoTransferCode(intent)?.let { memoCodeImportRequest.value = it; memoImportRequest.value = null }
            intent?.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO)?.let { memoRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_EXTENSION_FEED)?.let { extensionFeedRequest.value = it }
            intent?.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_COMPONENT_ABOUT)?.let { componentAboutRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_EXTENSION_SETTINGS)?.let { openExtensionSettingsRequest.value = it }
            intent?.getStringExtra(EXTRA_OPEN_SCHEDULE_DATE)?.let { scheduleDateRequest.value = it }
            com.x500x.cursimple.app.vendor.vendorScheduleDate(intent?.dataString, packageName)?.let { scheduleDateRequest.value = it }
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
                paletteStyle = prefs.scheduleCardStyle.coursePaletteStyle,
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
                    if (prefs.loaded && !prefs.disclaimerAccepted) {
                        OnboardingGate(
                            onAccept = { prefsViewModel.setDisclaimerAccepted(true) },
                            onReject = { finishAndRemoveTask() },
                        )
                    }
                    if (prefs.loaded && prefs.disclaimerAccepted) {
                    val appZone = remember { BeijingTime.zone }
                    val embeddedPageGestures = remember { com.x500x.cursimple.feature.plugin.ui.EmbeddedPageGestures() }
                    val reusableCourses by rememberCourseReuseCatalog(container.termProfileRepository)
                    val timetableRowDefinitions by rememberPluginTimetableRowDefinitions(container.pluginManager)
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalAppZone provides appZone,
                        com.x500x.cursimple.feature.schedule.LocalCourseReuseCatalog provides reusableCourses,
                        com.x500x.cursimple.feature.schedule.LocalPluginTimetableRowPreferences provides prefs.pluginTimetableRowVisibility,
                        com.x500x.cursimple.feature.schedule.LocalPluginTimetableRowDefinitions provides timetableRowDefinitions,
                        com.x500x.cursimple.feature.plugin.ui.LocalEmbeddedPageGestures provides embeddedPageGestures,
                    ) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    val firstRunSession = rememberSaveable { !prefs.firstRunGuideCompleted }
                    var replayFirstRunGuide by rememberSaveable { mutableStateOf(false) }
                    var memoSearchOpen by rememberSaveable { mutableStateOf(false) }
                    var memoShareOpen by remember { mutableStateOf(false) }
                    var settingsTopBar by remember { mutableStateOf<SettingsTopBar?>(null) }
                    var pluginTopBar by remember { mutableStateOf<com.x500x.cursimple.feature.plugin.PluginTopBar?>(null) }
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
                    // Null until the first registry emission, so a saved layout survives the cold start.
                    val extensionShells by androidx.compose.runtime.produceState<Pair<List<com.x500x.cursimple.core.plugin.install.InstalledPluginRecord>, Map<String, Set<String>>>?>(null) {
                        container.pluginManager.installedPluginsFlow.collect { records ->
                            value = records to container.pluginManager.declaredLayoutShells(records)
                        }
                    }
                    val effectiveLayout = com.x500x.cursimple.app.navigation.resolveEffectiveLayout(
                        prefs.appLayout,
                        extensionShells?.let { (records, shells) -> com.x500x.cursimple.app.navigation.activeLayoutShells(records, shells, prefs.enabledPluginIds) },
                    )
                    // Null while the host drawer is in charge.
                    val barItems = effectiveLayout?.takeIf { it.shell == com.x500x.cursimple.core.data.navigation.SHELL_BOTTOM_BAR }?.items
                    var openMarketExtensionsTab by remember { mutableStateOf(false) }
                    // A component page that switched back to the drawer stays open until the user leaves it.
                    LaunchedEffect(barItems == null, currentExtension == null) {
                        if (barItems == null && currentExtension == null &&
                            (currentScreen == AppScreen.Me || currentScreen == AppScreen.ComponentsHub)
                        ) {
                            currentScreen = AppScreen.Schedule
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
                    val pendingMemoImport by memoImportRequest
                    val pendingMemoCodeImport by memoCodeImportRequest
                    LaunchedEffect(pendingMemoImport, pendingMemoCodeImport) {
                        if (pendingMemoImport != null || pendingMemoCodeImport != null) {
                            currentExtension = null
                            currentScreen = AppScreen.Memos
                        }
                    }
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
                    val pendingComponentAbout by componentAboutRequest
                    LaunchedEffect(pendingComponentAbout) {
                        pendingComponentAbout?.let {
                            currentScreen = AppScreen.Plugins
                            currentExtension = it
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

                            override fun syncAfterLogin(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, sessionRevision: Long): Boolean {
                                container.extensionCoordinator.syncAfterLogin(record, sessionRevision)
                                return true
                            }

                            override fun onDataChanged(pluginId: String) =
                                container.extensionCoordinator.onDataChanged(pluginId)

                            override suspend fun markRead(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, itemId: String) =
                                container.extensionCoordinator.markRead(record, itemId)

                            override suspend fun setItemIgnored(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, itemId: String, ignored: Boolean) =
                                container.extensionCoordinator.setItemIgnored(record, itemId, ignored)

                            override suspend fun notificationCommand(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, command: String, payload: kotlinx.serialization.json.JsonObject) =
                                container.notificationDeliveryCoordinator.command(record, command, payload)

                            override suspend fun debugLogs(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord): kotlinx.serialization.json.JsonElement =
                                buildJsonArray {
                                    PluginLogBuffer.instance.snapshot()
                                        .filter { it.pluginId == record.pluginId }
                                        .takeLast(80)
                                        .forEach { entry ->
                                            add(buildJsonObject {
                                                put("time", entry.timestampMs)
                                                put("level", entry.level.short)
                                                put("event", entry.event)
                                                put("source", entry.source.token)
                                                put("message", entry.fields.entries.joinToString(" · ") { (key, value) -> "$key=$value" })
                                            })
                                        }
                                }

                            override suspend fun applyLayout(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord, payload: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement {
                                val declared = container.pluginManager.loadExtensionPackage(record).first
                                    .extension?.layouts.orEmpty().map { it.shell }.toSet()
                                val selection = com.x500x.cursimple.feature.plugin.extension.applyLayoutRequest(record.pluginId, declared, payload)
                                val repository = container.userPreferencesRepository
                                if (selection != null) {
                                    repository.setAppLayout(selection)
                                } else if (repository.preferencesFlow.first().appLayout?.ownerId == record.pluginId) {
                                    repository.setAppLayout(null)
                                }
                                return currentLayout(record)
                            }

                            override suspend fun currentLayout(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord): kotlinx.serialization.json.JsonElement {
                                val saved = container.userPreferencesRepository.preferencesFlow.first()
                                val records = container.pluginManager.getInstalledPlugins()
                                val effective = com.x500x.cursimple.app.navigation.resolveEffectiveLayout(
                                    saved.appLayout,
                                    com.x500x.cursimple.app.navigation.activeLayoutShells(records, container.pluginManager.declaredLayoutShells(records), saved.enabledPluginIds),
                                )
                                return com.x500x.cursimple.feature.plugin.extension.currentLayoutJson(effective, record.pluginId)
                            }

                            override fun refreshWidget(record: com.x500x.cursimple.core.plugin.install.InstalledPluginRecord) {
                                container.extensionCoordinator.onDataChanged(record.pluginId)
                            }

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
                    LaunchedEffect(pendingMemoImport) { if (pendingMemoImport != null) subScreen = null }
                    var swapTargetDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
                    var swapSourceDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
                    var openSettingsDestination by rememberSaveable { mutableStateOf<SettingsDestinationKey?>(null) }
                    var settingsReturnTarget by rememberSaveable { mutableStateOf<SettingsReturnTargetKey?>(null) }
                    var showAddMenu by remember { mutableStateOf(false) }
                    var showScheduleEvents by rememberSaveable { mutableStateOf(false) }
                    var endLegend by remember { mutableStateOf(false to false) }
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
                            attendanceRepository = container.attendanceRepository,
                            normalizeTimingProfile = { profile ->
                                container.normalizeTimingProfileForActiveTerm(profile)
                            },
                            onSyncCompleted = { profile -> container.refreshWidgets(profile) },
                            onSyncSucceeded = { pluginId -> PluginSyncStamp.record(this@MainActivity, pluginId) },
                            onSchoolTimingImported = { name, profile ->
                                container.widgetPreferencesRepository.selectImportedTimingProfile(name, profile)
                            },
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
                            merger = { ids, name, alignment, anchorTermId -> container.termMerger.merge(ids, name, alignment, anchorTermId) },
                            readTerm = { id -> container.readTermSnapshot(id) },
                            writeTerm = { id, manual, schedule -> container.writeTermCopy(id, manual, schedule) },
                            attendance = { container.attendanceRepository.attendanceFlow.first() },
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
                    val haptics = rememberAppHaptics()
                    // Pages listed on the me page return there instead of to the timetable.
                    val underMe = barItems != null && currentScreen != AppScreen.Me &&
                        currentScreen.toDestination()?.let { it !in barItems } == true
                    val drawerGesturesEnabled = barItems == null && !scheduleState.isSyncing && scheduleState.pendingWebSession == null &&
                        (drawerState.isOpen || (currentExtension == null && !embeddedPageGestures.ownsContentGestures))
                    // Turning gestures off mid-drag can cancel the drawer's settle and leave a partial
                    // offset that shifts the whole content pane right with the panel itself hidden.
                    // Settle back to closed so the pane cannot stay misaligned.
                    androidx.compose.runtime.LaunchedEffect(drawerGesturesEnabled) {
                        if (!drawerGesturesEnabled && drawerState.currentValue == DrawerValue.Closed) {
                            drawerState.close()
                        }
                    }
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
                        if (!firstRunSession && prefs.termStartDate == null &&
                            !autoPromptedThisSession && !blocked
                        ) {
                            autoPromptedThisSession = true
                            showTermStartReminder = true
                        }
                    }
                    if (showTermStartReminder) {
                        AppAlertDialog(
                            onDismissRequest = { showTermStartReminder = false },
                            tone = AppDialogTone.Warning,
                            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            title = { Text(stringResource(R.string.main_term_start_missing_title)) },
                            text = {
                                Text(stringResource(R.string.main_term_start_missing_body))
                            },
                            confirmButton = {
                                AppDialogPrimaryButton(onClick = {
                                    showTermStartReminder = false
                                    showDatePicker = true
                                }) { Text(stringResource(R.string.main_go_to_settings)) }
                            },
                            dismissButton = {
                                AppDialogSecondaryButton(onClick = { showTermStartReminder = false }) {
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
                    if (!firstRunSession && showForceStopPrompt) {
                        AppAlertDialog(
                            tone = AppDialogTone.Warning,
                            onDismissRequest = {
                                showForceStopPrompt = false
                                com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(appContext)
                            },
                            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            title = { Text(stringResource(R.string.force_stop_prompt_title)) },
                            text = { Text(stringResource(R.string.force_stop_prompt_body)) },
                            confirmButton = {
                                AppDialogPrimaryButton(onClick = {
                                    showForceStopPrompt = false
                                    com.x500x.cursimple.app.reminder.ForceStopMonitor.markPrompted(appContext)
                                    currentScreen = AppScreen.Settings
                                    subScreen = null
                                    openSettingsDestination = SettingsDestinationKey.Permissions
                                }) { Text(stringResource(R.string.main_go_to_settings)) }
                            },
                            dismissButton = {
                                AppDialogSecondaryButton(onClick = {
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
                    if (prefs.firstRunGuideCompleted) {
                        ReleaseAnnouncementGate(
                            lastSeenVersionCode = prefs.lastSeenVersionCode,
                            onSeen = prefsViewModel::setLastSeenVersionCode,
                            announcementAutoPlayEnabled = prefs.announcementAutoPlayEnabled,
                            onDialogVisibleChange = { announcementVisible = it },
                        )
                    }
                    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
                    var showThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var drawerAppearance by rememberSaveable { mutableStateOf<DrawerAppearance?>(null) }
                    var showAppLanguageDialog by rememberSaveable { mutableStateOf(false) }
                    var showWidgetThemeAccentDialog by rememberSaveable { mutableStateOf(false) }
                    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }
                    var addCourseInitialType by rememberSaveable { mutableStateOf(ScheduleItemType.Course) }
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
                    val weekPreview = remember(effectiveTermStart, scheduleState.schedule, scheduleState.manualCourses) {
                        if (effectiveTermStart != null) null
                        else courseWeekPreview(scheduleState.schedule.allCoursesWith(scheduleState.manualCourses))
                    }
                    val selectedDayDate = today.plusDays(dayOffset.toLong())
                    val currentWeekIndex = weekPreview?.firstCourseWeek ?: resolveWeekIndexForDate(effectiveTermStart, today)
                    val dayWeekIndex = weekPreview?.sourceWeekForPageDate(today, selectedDayDate, prefs.scheduleDisplay.weekStartDay)
                        ?: resolveWeekIndexForDate(
                        effectiveTermStart,
                        selectedDayDate,
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
                    LaunchedEffect(weekPreview, weekPickerTotalWeeks, scheduleState.initialized) {
                        if (weekPreview != null && scheduleState.initialized) {
                            weekOffset = weekOffset.coerceIn(0, (weekPickerTotalWeeks - currentWeekIndex).coerceAtLeast(0))
                        }
                    }
                    // Use an atomic repository update; UI values can lag repeated taps.
                    val addWeek: () -> Unit = {
                        scope.launch { container.termProfileRepository.adjustActiveTermExtraWeekCount(1) }
                    }
                    val deleteWeek: (Int) -> Unit = { week ->
                        // Only user-added weeks beyond the course range can be removed.
                        if (week > derivedWeeks) {
                            // Step back onto the last remaining week instead of staying on one that no longer exists.
                            val remaining = (weekPickerTotalWeeks - 1).coerceAtLeast(1)
                            val overshoot = displayedWeekIndex - remaining
                            if (overshoot > 0) {
                                when (scheduleViewMode) {
                                    ScheduleViewMode.Week -> weekOffset -= overshoot
                                    ScheduleViewMode.Day -> dayOffset -= overshoot * 7
                                }
                            }
                            scope.launch { container.termProfileRepository.adjustActiveTermExtraWeekCount(-1) }
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerGesturesEnabled,
                        drawerContent = {
                            AppDrawer(
                                drawerLayout = prefs.drawerLayout,
                                currentScreen = currentScreen,
                                extensionEntries = sidebarExtensions,
                                currentExtension = currentExtension,
                                onSelectExtension = {
                                    currentExtension = it
                                    scope.launch { drawerState.close() }
                                },
                                onShowAllExtensions = {
                                    subScreen = MainActivity.SubScreen.AllExtensions
                                    scope.launch { drawerState.close() }
                                },
                                termStartDate = prefs.termStartDate,
                                currentWeekIndex = currentWeekIndex,
                                appVersionName = BuildConfig.VERSION_NAME,
                                updateBadgeVisible = updateBadgeVisible,
                                pluginUpdateBadgeVisible = pluginUpdateBadgeVisible,
                                onSelectScreen = {
                                    haptics.click()
                                    currentScreen = it
                                    currentExtension = null
                                    scope.launch { drawerState.close() }
                                },
                                onPickThemeAccent = {
                                    scope.launch { drawerState.close() }
                                    drawerAppearance = DrawerAppearance.Theme
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
                                    drawerAppearance = DrawerAppearance.Background
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
                            bottomBar = {
                                if (barItems != null) {
                                    com.x500x.cursimple.app.navigation.AppBottomBar(
                                        items = barItems,
                                        selected = com.x500x.cursimple.app.navigation.selectedBarTab(
                                            barItems,
                                            currentScreen.toDestination(),
                                        ),
                                        meBadge = updateBadgeVisible || pluginUpdateBadgeVisible,
                                        size = prefs.bottomBarSize,
                                        onSelect = { tab ->
                                            haptics.click()
                                            currentExtension = null
                                            currentScreen = when (tab) {
                                                is com.x500x.cursimple.app.navigation.BarTab.Destination -> tab.destination.toAppScreen()
                                                com.x500x.cursimple.app.navigation.BarTab.Me -> AppScreen.Me
                                            }
                                        },
                                    )
                                }
                            },
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        if (onScheduleScreen) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                val isCurrentWeek = isCurrentTermWeek(
                                                    termStart = effectiveTermStart,
                                                    displayedWeekIndex = displayedWeekIndex,
                                                    currentWeekIndex = currentWeekIndex,
                                                )
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = CircleShape,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    shadowElevation = 1.dp,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .combinedClickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = androidx.compose.material3.ripple(bounded = true),
                                                            onClick = {
                                                                haptics.click()
                                                                showWeekMenu = true
                                                            },
                                                            onDoubleClick = {
                                                                haptics.tick()
                                                                weekOffset = 0
                                                                dayOffset = 0
                                                            },
                                                        ),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .heightIn(min = 38.dp)
                                                            .padding(start = 16.dp, end = 8.dp),
                                                    ) {
                                                        AppAtomicText(
                                                            text = LocalContext.current.termWeekText(
                                                                if (weekPreview != null) termWeekLabel(weekPreview.displayWeek(displayedWeekIndex))
                                                                else termWeekLabel(effectiveTermStart, displayedWeekIndex),
                                                            ),
                                                            style = MaterialTheme.typography.titleMedium.copy(
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        if (isCurrentWeek) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(9.dp),
                                                            ) {
                                                                AppAtomicText(
                                                                    text = stringResource(R.string.main_this_week),
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 2.dp,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.Rounded.ArrowDropDown,
                                                            contentDescription = stringResource(R.string.main_switch_week),
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(26.dp),
                                                        )
                                                    }
                                                }
                                                val termLabel = if (weekPreview != null) stringResource(R.string.main_drawer_term_start_unset)
                                                    else formatTermLabel(effectiveTermStart)
                                                val (legendFinal, legendNear) = endLegend
                                                if (termLabel.isNotBlank() || legendFinal || legendNear) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.padding(top = 2.dp),
                                                    ) {
                                                        if (termLabel.isNotBlank()) {
                                                            Text(
                                                                text = termLabel,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                            )
                                                        }
                                                        if (legendFinal) EndWarningLegendEntry(final = true)
                                                        if (legendNear) EndWarningLegendEntry(final = false)
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = currentExtension?.let { extensionTitles[it] }
                                                    ?: settingsTopBar?.title?.takeIf { currentScreen == AppScreen.Settings }
                                                    ?: pluginTopBar?.title?.takeIf { currentScreen == AppScreen.Plugins }
                                                    ?: stringResource(currentScreen.labelRes),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = 16.sp,
                                                    letterSpacing = 0.5.sp,
                                                ),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        val settingsBack = settingsTopBar?.onBack
                                            ?.takeIf { currentExtension == null && currentScreen == AppScreen.Settings }
                                        val pluginBack = pluginTopBar?.onBack
                                            ?.takeIf { currentExtension == null && currentScreen == AppScreen.Plugins }
                                        if (settingsBack != null) {
                                            AppToolbarIconButton(
                                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = stringResource(R.string.settings_back),
                                                onClick = settingsBack,
                                            )
                                        } else if (pluginBack != null) {
                                            AppToolbarIconButton(
                                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = stringResource(R.string.settings_back),
                                                onClick = pluginBack,
                                            )
                                        } else if (barItems != null) {
                                            if (currentExtension != null || underMe) {
                                                AppToolbarIconButton(
                                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                                    contentDescription = stringResource(R.string.settings_back),
                                                    onClick = {
                                                        if (currentExtension != null) {
                                                            currentExtension = null
                                                            componentAboutRequest.value = null
                                                        } else currentScreen = AppScreen.Me
                                                    },
                                                    modifier = Modifier.testTag("bar-back"),
                                                )
                                            }
                                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } },
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
                                                icon = Icons.Rounded.Share,
                                                contentDescription = stringResource(R.string.main_memo_share),
                                                onClick = { memoShareOpen = true },
                                                selected = memoShareOpen,
                                                modifier = Modifier.testTag("memo-share-action"),
                                            )
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
                                                    highlighted = true,
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
                                                            addCourseInitialType = ScheduleItemType.Course
                                                            showAddCourseDialog = true
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.main_add_event_manually)) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Event,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showAddMenu = false
                                                            addCourseInitialType = ScheduleItemType.Event
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
                                                    if (scheduleState.events.isNotEmpty()) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(if (showScheduleEvents) R.string.main_hide_schedule_events else R.string.main_show_schedule_events)) },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.Event, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                showScheduleEvents = !showScheduleEvents
                                                                showAddMenu = false
                                                            },
                                                        )
                                                    }
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
                                        val initialScreen = initialState.first
                                        val targetScreen = targetState.first
                                        val initialExt = initialState.second
                                        val targetExt = targetState.second
                                        val forward = when {
                                            initialExt == null && targetExt != null -> true
                                            initialExt != null && targetExt == null -> false
                                            initialScreen != targetScreen -> targetScreen.navigationRank() >= initialScreen.navigationRank()
                                            else -> true
                                        }
                                        com.x500x.cursimple.app.navigation.routeTransition(forward = forward)
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
                                        openWidgetAbout = componentAboutRequest.value == extensionRecord.installKey ||
                                            componentAboutRequest.value == extensionRecord.pluginId,
                                        onRemove = {
                                            pluginMarketViewModel.removePlugin(extensionRecord.installKey)
                                            currentExtension = null
                                            currentScreen = AppScreen.Plugins
                                        },
                                        onOpenSettings = {
                                            openExtensionSettings = extensionRecord.installKey
                                            currentScreen = AppScreen.Plugins
                                            currentExtension = null
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else when (targetScreen) {
                                    AppScreen.Me -> com.x500x.cursimple.app.navigation.MeScreen(
                                        overflow = com.x500x.cursimple.core.data.navigation.bottomBarOverflow(barItems.orEmpty()),
                                        weekLabel = LocalContext.current.termWeekText(
                                            if (weekPreview != null) termWeekLabel(weekPreview.displayWeek(currentWeekIndex))
                                            else termWeekLabel(effectiveTermStart, currentWeekIndex),
                                        ),
                                        termLabel = formatTermLabel(effectiveTermStart),
                                        versionName = BuildConfig.VERSION_NAME,
                                        updateBadge = updateBadgeVisible,
                                        badgeFor = { destination ->
                                            (updateBadgeVisible && destination == com.x500x.cursimple.core.data.navigation.NavDestination.About) ||
                                                (pluginUpdateBadgeVisible && destination == com.x500x.cursimple.core.data.navigation.NavDestination.Plugins)
                                        },
                                        onOpen = { currentScreen = it.toAppScreen() },
                                        onShortcut = { id ->
                                            when (id) {
                                                "action.theme" -> showThemeAccentDialog = true
                                                "action.background" -> {
                                                    openSettingsDestination = SettingsDestinationKey.ScheduleBackground
                                                    currentScreen = AppScreen.Settings
                                                }
                                                "action.overrides" -> {
                                                    openSettingsDestination = SettingsDestinationKey.TemporaryOverrides
                                                    currentScreen = AppScreen.Settings
                                                }
                                                "action.update" -> showUpdateCheckDialog = true
                                            }
                                        },
                                        onOpenUpdate = { showUpdateCheckDialog = true },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.ComponentsHub -> com.x500x.cursimple.app.navigation.ComponentsHubScreen(
                                        entries = sidebarExtensions,
                                        current = currentExtension,
                                        onOpen = { currentExtension = it },
                                        onOpenMarket = {
                                            openMarketExtensionsTab = true
                                            currentScreen = AppScreen.Plugins
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Schedule -> ScheduleRoute(
                                        viewModel = scheduleViewModel,
                                        overrideTermStart = prefs.termStartDate,
                                        weekOffset = weekOffset,
                                        // Bound backward navigation by the current pre-term week or week one.
                                        minWeekOffset = if (weekPreview != null) 0 else (1 - currentWeekIndex).coerceAtMost(0),
                                        maxWeekOffset = weekPickerTotalWeeks - currentWeekIndex,
                                        termWeekCount = weekPickerTotalWeeks,
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
                                        onSetCustomCourseColor = prefsViewModel::setCustomCourseColor,
                                        showEvents = showScheduleEvents,
                                        onShowEventsChange = { showScheduleEvents = it },
                                        onEndWarningLegendChange = { final, near -> endLegend = final to near },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Plugins -> CompositionLocalProvider(
                                        com.x500x.cursimple.feature.plugin.LocalPluginHostTopBar provides true,
                                    ) { PluginMarketRoute(
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
                                        openExtensionsTab = openMarketExtensionsTab,
                                        onExtensionsTabConsumed = { openMarketExtensionsTab = false },
                                        onTopBarChange = { pluginTopBar = it },
                                    ) }

                                    AppScreen.Courses -> CourseLibraryRoute(
                                        viewModel = scheduleViewModel,
                                        scheduleDisplay = prefs.scheduleDisplay,
                                        maxWeekCount = resolveWeekPickerTotalWeeks(
                                            schedule = scheduleState.schedule,
                                            manualCourses = scheduleState.manualCourses,
                                            currentWeek = currentWeekIndex,
                                            extraWeekCount = activeTermExtraWeeks,
                                        ),
                                        termStartDate = prefs.termStartDate,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    AppScreen.Memos -> MemoRoute(
                                        viewModel = scheduleViewModel,
                                        modifier = Modifier.fillMaxSize(),
                                        searchOpen = memoSearchOpen,
                                        onCloseSearch = { memoSearchOpen = false },
                                        openNoteId = pendingMemo?.takeIf { it.isNotBlank() },
                                        onNoteOpened = { memoRequest.value = null },
                                        shareOpen = memoShareOpen,
                                        onCloseShare = { memoShareOpen = false },
                                        importUri = pendingMemoImport,
                                        importCode = pendingMemoCodeImport,
                                        onCloseImport = { memoImportRequest.value = null; memoCodeImportRequest.value = null },
                                    )

                                    AppScreen.Reminders -> RemindersScreen(
                                        classNotice = prefs.classNotice,
                                        onClassNoticeEnabledChange = prefsViewModel::setClassNoticeEnabled,
                                        onClassNoticeAdvanceMinutesChange =
                                            prefsViewModel::setClassNoticeAdvanceMinutes,
                                        onClassNoticeHeadsUpChange =
                                            prefsViewModel::setClassNoticeHeadsUpEnabled,
                                        onClassNoticeVibrationChange =
                                            prefsViewModel::setClassNoticeVibrationEnabled,
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
                                        onClassNoticeEveningBriefingChange =
                                            prefsViewModel::setEveningBriefingEnabled,
                                        onClassNoticeTransitReminderChange =
                                            prefsViewModel::setTransitReminderEnabled,
                                        onClassNoticeExamAlertChange =
                                            prefsViewModel::setExamAlertEnabled,
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
                                        onClassNoticeVibrationChange =
                                            prefsViewModel::setClassNoticeVibrationEnabled,
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
                                        onClassNoticeEveningBriefingChange =
                                            prefsViewModel::setEveningBriefingEnabled,
                                        onClassNoticeTransitReminderChange =
                                            prefsViewModel::setTransitReminderEnabled,
                                        onClassNoticeExamAlertChange =
                                            prefsViewModel::setExamAlertEnabled,
                                        onCoursePaletteStyleChange = prefsViewModel::setCoursePaletteStyle,
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
                                        onCourseEndWarningEnabledChange = prefsViewModel::setCourseEndWarningEnabled,
                                        onCourseSessionCountIncludeHolidaysChange = prefsViewModel::setCourseSessionCountIncludeHolidays,
                                        onScheduleWeekendVisibleChange = prefsViewModel::setScheduleWeekendVisible,
                                        onScheduleRowFitModeChange = prefsViewModel::setScheduleRowFitMode,
                                        onScheduleAutoScrollWhenCrowdedChange = prefsViewModel::setScheduleAutoScrollWhenCrowded,
                                        onScheduleLocationVisibleChange = prefsViewModel::setScheduleLocationVisible,
                                        onScheduleLocationAtPrefixChange = prefsViewModel::setScheduleLocationAtPrefix,
                                        onScheduleTeacherVisibleChange = prefsViewModel::setScheduleTeacherVisible,
                                        onTotalScheduleDisplayChange = prefsViewModel::setTotalScheduleDisplayEnabled,
                                        onScheduleTimelineIndicatorEnabledChange = prefsViewModel::setTimelineIndicatorEnabled,
                                        onScheduleGridDashedLinesEnabledChange = prefsViewModel::setGridDashedLinesEnabled,
                                        onInactiveCourseDisplayModeChange = prefsViewModel::setInactiveCourseDisplayMode,
                                        onDayViewEventsEnabledChange = prefsViewModel::setDayViewEventsEnabled,
                                        onScheduleTimeColumnWidthDpChange = prefsViewModel::setTimeColumnWidthDp,
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
                                        onWidgetBackgroundOpacityPercentChange =
                                            widgetPrefsViewModel::setWidgetBackgroundOpacityPercent,
                                        onWidgetFrostedGlassEnabledChange =
                                            widgetPrefsViewModel::setWidgetFrostedGlassEnabled,
                                        onWidgetThemeModeChange =
                                            widgetPrefsViewModel::setWidgetThemeMode,
                                        onWidgetOpenAppOnDoubleClickChange =
                                            widgetPrefsViewModel::setWidgetOpenAppOnDoubleClickEnabled,
                                        onWidgetShowEventsChange = widgetPrefsViewModel::setWidgetShowEvents,
                                        onAppTimeZoneChange = prefsViewModel::setAppTimeZoneId,
                                        onPluginSourcesChange = prefsViewModel::setPluginSources,
                                        onComponentSourcesChange = prefsViewModel::setComponentSources,
                                        drawerLayout = prefs.drawerLayout,
                                        componentLayoutActive = effectiveLayout != null,
                                        onDrawerLayoutChange = prefsViewModel::setDrawerLayout,
                                        onRestoreDrawer = prefsViewModel::clearAppLayout,
                                        bottomBarSize = prefs.bottomBarSize.takeIf { barItems != null },
                                        onBottomBarSizeChange = prefsViewModel::setBottomBarSize,
                                        onPrivateFilesProviderEnabledChange =
                                            prefsViewModel::setPrivateFilesProviderEnabled,
                                        onCreateAppBackup = container::exportAppBackup,
                                        onRestoreAppBackup = container::restoreAppBackup,
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
                                        onReplayFirstRunGuide = { replayFirstRunGuide = true },
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
                                        onTopBarChange = { settingsTopBar = it },
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
                                        announcementAutoPlayEnabled = prefs.announcementAutoPlayEnabled,
                                        ignoredUpdateVersionCode = prefs.ignoredUpdateVersionCode,
                                        updateNotice = updateNotice,
                                        onAutoUpdateEnabledChange = prefsViewModel::setAutoUpdateEnabled,
                                        onBetaUpdatesEnabledChange = prefsViewModel::setBetaUpdatesEnabled,
                                        onAnnouncementAutoPlayEnabledChange = prefsViewModel::setAnnouncementAutoPlayEnabled,
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

                    com.x500x.cursimple.app.navigation.AnimatedPage(subScreen) { page ->
                    when (page) {
                        MainActivity.SubScreen.TermManagement -> {
                            // 先注册返回键，界面内的多选模式后注册，优先拦截返回。
                            androidx.activity.compose.BackHandler { subScreen = null }
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
                                onCopy = { sourceId, name, date, keepReminders ->
                                    termProfileViewModel.copyTerm(sourceId, name, date, keepReminders) { copiedName ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.term_copy_done, copiedName),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                loadStats = { id, onResult -> termProfileViewModel.loadStats(id, onResult) },
                                onArchive = { id, archived -> termProfileViewModel.setArchived(id, archived) },
                                onMerge = { ids, name ->
                                    termProfileViewModel.merge(ids, name) { mergedName ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.term_merge_done, mergedName),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                onMergeWithAlignment = { ids, name, alignment, anchorTermId ->
                                    termProfileViewModel.merge(ids, name, alignment, anchorTermId) { mergedName ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.term_merge_done, mergedName),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    weekOffset = 0
                                    dayOffset = 0
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        MainActivity.SubScreen.ImportExport -> {
                            val activeTermProfile = termProfileState.terms
                                .firstOrNull { it.id == termProfileState.activeTermId }
                            ImportExportScreen(
                                weekStartDay = prefs.scheduleDisplay.weekStartDay,
                                schedule = scheduleState.schedule,
                                manualCourses = scheduleState.manualCourses,
                                scheduleEvents = scheduleState.events,
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
                                onOpenSchoolPortal = scheduleViewModel::startSchoolPortal,
                                onBack = { subScreen = null },
                                onBrowseAllPlugins = {
                                    subScreen = null
                                    currentScreen = AppScreen.Plugins
                                },
                                onAddCourseManually = {
                                    subScreen = null
                                    addCourseInitialType = ScheduleItemType.Course
                                    showAddCourseDialog = true
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.activity.compose.BackHandler(
                                enabled = scheduleState.pendingWebSession == null,
                            ) { subScreen = null }
                        }
                        MainActivity.SubScreen.AllExtensions -> {
                            AllExtensionsScreen(
                                entries = sidebarExtensions,
                                current = currentExtension,
                                onOpen = {
                                    currentExtension = it
                                    subScreen = null
                                },
                                onBack = { subScreen = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            androidx.activity.compose.BackHandler { subScreen = null }
                        }
                    }
                    }

                    // Let the open drawer consume Back first; other pages return to the timetable.
                    androidx.activity.compose.BackHandler(
                        enabled = subScreen == null &&
                            (currentScreen != AppScreen.Schedule || currentExtension != null) &&
                            !drawerState.isOpen,
                    ) {
                        if (currentExtension != null) {
                            currentExtension = null
                            componentAboutRequest.value = null
                        } else currentScreen = if (underMe) AppScreen.Me else AppScreen.Schedule
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

                    drawerAppearance?.let { kind ->
                        DrawerAppearanceChooser(
                            kind = kind,
                            onDismiss = { drawerAppearance = null },
                            themeMode = prefs.themeMode,
                            onThemeMode = prefsViewModel::setThemeMode,
                            themeAccent = prefs.themeAccent,
                            themeCustomArgb = prefs.themeCustomColorArgb,
                            onThemeAccent = prefsViewModel::setThemeAccent,
                            onThemeColor = {
                                drawerAppearance = null
                                showThemeAccentDialog = true
                            },
                            onScheduleStyle = {
                                drawerAppearance = null
                                openSettingsDestination = SettingsDestinationKey.ScheduleStyle
                                currentScreen = AppScreen.Settings
                                subScreen = null
                            },
                            onScheduleBackground = {
                                drawerAppearance = null
                                openSettingsDestination = SettingsDestinationKey.ScheduleBackground
                                currentScreen = AppScreen.Settings
                                subScreen = null
                            },
                            onWidgetBackground = {
                                drawerAppearance = null
                                openSettingsDestination = SettingsDestinationKey.WidgetBackground
                                currentScreen = AppScreen.Settings
                                subScreen = null
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
                        AppAlertDialog(
                            onDismissRequest = { showClearTermStartConfirm = false },
                            tone = AppDialogTone.Danger,
                            icon = { Icon(Icons.Rounded.EventBusy, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            title = { Text(stringResource(R.string.main_clear_term_start_title)) },
                            text = { Text(stringResource(R.string.main_clear_term_start_body)) },
                            confirmButton = {
                                AppDialogDangerButton(onClick = {
                                    setActiveTermStartDate(null)
                                    showClearTermStartConfirm = false
                                }) { Text(stringResource(R.string.main_clear)) }
                            },
                            dismissButton = {
                                AppDialogSecondaryButton(onClick = { showClearTermStartConfirm = false }) { Text(stringResource(R.string.main_cancel)) }
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
                            onConfirmBatch = { courses ->
                                scheduleViewModel.addManualCourses(courses)
                                showAddCourseDialog = false
                            },
                            onConfirmEvent = { event ->
                                scheduleViewModel.saveEvent(event)
                                showAddCourseDialog = false
                            },
                            initialScheduleType = addCourseInitialType,
                            defaultDate = today,
                            termStartDate = effectiveTermStart,
                            maxWeekCount = weekPickerTotalWeeks,
                            slotTimes = scheduleState.timingProfile?.slotTimes.orEmpty(),
                        )
                    }

                    if (showWeekMenu) {
                        WeekPickerSheet(
                            termStart = effectiveTermStart,
                            preview = weekPreview,
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
                                        previewWeekStart = prefs.scheduleDisplay.weekStartDay,
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
                        AppAlertDialog(
                            onDismissRequest = { pendingCurrentWeek = null },
                            icon = { Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
                                AppDialogPrimaryButton(onClick = {
                                    setActiveTermStartDate(derivedStart)
                                    weekOffset = 0
                                    dayOffset = 0
                                    pendingCurrentWeek = null
                                }) { Text(stringResource(R.string.main_set_current_week_confirm_action)) }
                            },
                            dismissButton = {
                                AppDialogSecondaryButton(onClick = { pendingCurrentWeek = null }) {
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
                        replayFirstRunGuide || shouldShowFirstRunGuide(
                            loaded = prefs.loaded,
                            disclaimerAccepted = prefs.disclaimerAccepted,
                            guideCompleted = prefs.firstRunGuideCompleted,
                            blockingDialogVisible = showTermStartReminder || showDatePicker ||
                                announcementVisible || (!firstRunSession && showForceStopPrompt),
                        )
                    ) {
                        fun closeGuide() {
                            replayFirstRunGuide = false
                            if (!prefs.firstRunGuideCompleted) {
                                prefsViewModel.setFirstRunGuideCompleted(true)
                            }
                        }
                        if (!replayFirstRunGuide && shouldReviewFirstRunPermissions(
                                guideCompleted = prefs.firstRunGuideCompleted,
                                permissionsReviewed = prefs.firstRunPermissionsReviewed,
                            )
                        ) {
                            FirstRunPermissionsPage(
                                classNotice = prefs.classNotice,
                                alarmKeepAliveEnabled = prefs.alarmKeepAliveEnabled,
                                onAlarmKeepAliveEnabledChange = prefsViewModel::setAlarmKeepAliveEnabled,
                                onContinue = prefsViewModel::markFirstRunPermissionsReviewed,
                            )
                        } else {
                            FirstRunGuideDialog(onFinish = ::closeGuide)
                        }
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
        /** Line icon for unselected navigation rows; [icon] marks the selected one. */
        val outlinedIcon: ImageVector,
    ) {
        Schedule(R.string.screen_schedule, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
        Courses(R.string.screen_courses, Icons.AutoMirrored.Rounded.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
        Memos(R.string.screen_memos, Icons.Rounded.EditNote, Icons.Outlined.EditNote),
        Plugins(R.string.screen_plugins, Icons.Rounded.Extension, Icons.Outlined.Extension),
        Reminders(R.string.screen_reminders, Icons.Rounded.Notifications, Icons.Outlined.Notifications),
        Settings(R.string.screen_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
        About(R.string.screen_about, Icons.Rounded.Info, Icons.Outlined.Info),

        /** Bottom bar shell only. */
        Me(R.string.nav_me, Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),

        /** Bottom bar shell only. */
        ComponentsHub(R.string.nav_components, Icons.Rounded.Widgets, Icons.Outlined.Widgets);

        fun navigationRank(): Int = when (this) {
            Schedule -> 0
            Courses -> 1
            Memos -> 2
            Plugins, ComponentsHub -> 3
            Reminders -> 4
            Settings -> 5
            About -> 6
            Me -> 7
        }
    }

    enum class SubScreen { TermManagement, ImportExport, SchoolImport, CourseSwap, AllExtensions }
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
    drawerLayout: com.x500x.cursimple.core.data.navigation.DrawerLayout,
    currentScreen: MainActivity.AppScreen,
    extensionEntries: List<Pair<String, String>>,
    currentExtension: String?,
    onSelectExtension: (String) -> Unit,
    onShowAllExtensions: () -> Unit,
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
        // Cap at the Material drawer width so landscape does not stretch rows across the screen.
        modifier = Modifier.width(minOf(LocalConfiguration.current.screenWidthDp * 0.72f, 360f).dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 14.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.main_app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    letterSpacing = 1.sp,
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (termStartDate != null && currentWeekIndex >= 1) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(9.dp)) {
                        Text(
                            text = stringResource(R.string.main_this_week),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = when {
                        termStartDate == null -> stringResource(R.string.main_drawer_term_start_unset)
                        currentWeekIndex >= 1 ->
                            stringResource(R.string.main_drawer_current_week, currentWeekIndex)
                        else ->
                            pluralStringResource(R.plurals.main_drawer_before_term, 1 - currentWeekIndex, 1 - currentWeekIndex)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val badgeFor = { screen: MainActivity.AppScreen ->
                (updateBadgeVisible && screen == MainActivity.AppScreen.About) ||
                    (pluginUpdateBadgeVisible && screen == MainActivity.AppScreen.Plugins)
            }
            drawerLayout.visible.map { it.toAppScreen() }.forEach { screen ->
                val selected = screen == currentScreen && currentExtension == null
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(screen.labelRes),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) screen.icon else screen.outlinedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    badge = if (badgeFor(screen)) {
                        { UpdateBadgeDot() }
                    } else null,
                    selected = selected,
                    onClick = { onSelectScreen(screen) },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            if (extensionEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))
                sidebarExtensionPreview(extensionEntries, currentExtension).forEach { (pluginId, title) ->
                    val selected = pluginId == currentExtension
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        selected = selected,
                        onClick = { onSelectExtension(pluginId) },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (extensionEntries.size > SIDEBAR_EXTENSION_LIMIT) {
                    AppOutlinedButton(
                        onClick = onShowAllExtensions,
                        modifier = Modifier.padding(start = 8.dp).testTag("drawer-all-extensions"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.main_drawer_extensions_all, extensionEntries.size),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(8.dp))

            if (drawerLayout.shortcutsVisible) {
                val shortcuts = listOf(
                    DrawerShortcut(Icons.Rounded.Style, R.string.main_drawer_theme_shortcut, onPickThemeAccent, false),
                    DrawerShortcut(Icons.Rounded.Wallpaper, R.string.main_drawer_background_shortcut, onPickScheduleBackground, false),
                    DrawerShortcut(Icons.Rounded.SwapHoriz, R.string.main_drawer_overrides_shortcut, onOpenTemporaryOverrides, false),
                    DrawerShortcut(Icons.Rounded.SystemUpdate, R.string.main_drawer_update_shortcut, onOpenUpdateCheck, updateBadgeVisible),
                )
                // Two columns match the design; narrow drawers or large text fall back to full-width chips so labels stay whole.
                val twoColumns = (DRAWER_SHORTCUT_ROW_WIDTH_DP * LocalDensity.current.fontScale).dp
                BoxWithConstraints {
                    val available = maxWidth
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (available >= twoColumns) {
                            shortcuts.chunked(2).forEach { pair ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    pair.forEach { shortcut ->
                                        DrawerAppearanceShortcut(
                                            icon = shortcut.icon,
                                            label = stringResource(shortcut.labelRes),
                                            onClick = shortcut.onClick,
                                            badge = shortcut.badge,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            shortcuts.forEach { shortcut ->
                                DrawerAppearanceShortcut(
                                    icon = shortcut.icon,
                                    label = stringResource(shortcut.labelRes),
                                    onClick = shortcut.onClick,
                                    badge = shortcut.badge,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            val overflowScreens = drawerLayout.overflow.map { it.toAppScreen() }
            val hiddenShortcuts = !drawerLayout.shortcutsVisible
            var moreOpen by remember { mutableStateOf(false) }
            if (overflowScreens.isNotEmpty() || hiddenShortcuts) {
                Spacer(modifier = Modifier.height(4.dp))
                AppOutlinedButton(
                    onClick = { moreOpen = true },
                    modifier = Modifier.padding(start = 8.dp).testTag("drawer-more"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        if (overflowScreens.any(badgeFor) || (hiddenShortcuts && updateBadgeVisible)) {
                            UpdateBadgeDot(modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.main_drawer_more, overflowScreens.size + if (hiddenShortcuts) 4 else 0),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
            if (moreOpen) {
                DrawerMoreSheet(
                    screens = overflowScreens,
                    showShortcuts = hiddenShortcuts,
                    badgeFor = badgeFor,
                    updateBadgeVisible = updateBadgeVisible,
                    onDismiss = { moreOpen = false },
                    onSelectScreen = { moreOpen = false; onSelectScreen(it) },
                    onShortcut = { action -> moreOpen = false; action() },
                    onPickThemeAccent = onPickThemeAccent,
                    onPickScheduleBackground = onPickScheduleBackground,
                    onOpenTemporaryOverrides = onOpenTemporaryOverrides,
                    onOpenUpdateCheck = onOpenUpdateCheck,
                )
            }

        }
            // Pinned to the drawer foot, like the reference; the rows above scroll on their own.
            Text(
                text = stringResource(R.string.main_drawer_version, appVersionName),
                style = MaterialTheme.typography.labelSmall,
                color = if (updateBadgeVisible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(start = 20.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenUpdateCheck)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** Sidebar items and shortcuts the user hid; they stay one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerMoreSheet(
    screens: List<MainActivity.AppScreen>,
    showShortcuts: Boolean,
    badgeFor: (MainActivity.AppScreen) -> Boolean,
    updateBadgeVisible: Boolean,
    onDismiss: () -> Unit,
    onSelectScreen: (MainActivity.AppScreen) -> Unit,
    onShortcut: (() -> Unit) -> Unit,
    onPickThemeAccent: () -> Unit,
    onPickScheduleBackground: () -> Unit,
    onOpenTemporaryOverrides: () -> Unit,
    onOpenUpdateCheck: () -> Unit,
) {
    // Open fully so the last row is never left under the half-expanded edge or the gesture bar.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .testTag("drawer-more-sheet"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.main_drawer_more_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            val rowColors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            screens.forEach { screen ->
                NavigationDrawerItem(
                    label = { Text(stringResource(screen.labelRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
                    icon = { Icon(screen.outlinedIcon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    badge = if (badgeFor(screen)) {
                        { UpdateBadgeDot() }
                    } else null,
                    selected = false,
                    onClick = { onSelectScreen(screen) },
                    colors = rowColors,
                )
            }
            if (showShortcuts) {
                if (screens.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                listOf(
                    Triple(Icons.Rounded.Style, R.string.main_drawer_theme_shortcut, onPickThemeAccent),
                    Triple(Icons.Rounded.Wallpaper, R.string.main_drawer_background_shortcut, onPickScheduleBackground),
                    Triple(Icons.Rounded.SwapHoriz, R.string.main_drawer_overrides_shortcut, onOpenTemporaryOverrides),
                    Triple(Icons.Rounded.SystemUpdate, R.string.main_drawer_update_shortcut, onOpenUpdateCheck),
                ).forEach { (icon, label, action) ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        badge = if (updateBadgeVisible && label == R.string.main_drawer_update_shortcut) {
                            { UpdateBadgeDot() }
                        } else null,
                        selected = false,
                        onClick = { onShortcut(action) },
                        colors = rowColors,
                    )
                }
            }
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
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Drawer appearance shortcut rendered as a filled chip. */
private data class DrawerShortcut(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
    val badge: Boolean,
)

/** Narrowest row that still fits two chips with their labels at 1× font scale. */
private const val DRAWER_SHORTCUT_ROW_WIDTH_DP = 250f

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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppDialogPrimaryButton(onClick = { onConfirm(selectedDate) }) {
                Text(stringResource(R.string.main_confirm))
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
        },
        icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.main_set_current_week_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.main_set_current_week_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppOutlinedTextField(
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
            AppDialogPrimaryButton(
                onClick = { parsedWeek?.let(onConfirm) },
                enabled = weekValid,
            ) { Text(stringResource(R.string.main_set)) }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
        },
    )
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.main_appearance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.System -> stringResource(R.string.main_theme_system)
                        ThemeMode.Light -> stringResource(R.string.main_theme_light)
                        ThemeMode.Dark -> stringResource(R.string.main_theme_dark)
                    }
                    SettingsRadioRow(
                        title = label,
                        subtitle = null,
                        selected = mode == current,
                        onSelect = { onSelect(mode) },
                    )
                }
            }
        },
        confirmButton = {
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
        },
    )
}

private data class ThemeAccentOption(
    val accent: ThemeAccent,
    val labelRes: Int,
    val swatch: androidx.compose.ui.graphics.Color,
)

private val themeAccentOptions = listOf(
    ThemeAccentOption(ThemeAccent.Green, R.string.main_accent_green, androidx.compose.ui.graphics.Color(0xFF26805A)),
    ThemeAccentOption(ThemeAccent.Blue, R.string.main_accent_blue, androidx.compose.ui.graphics.Color(0xFF2563A8)),
    ThemeAccentOption(ThemeAccent.Purple, R.string.main_accent_purple, androidx.compose.ui.graphics.Color(0xFF7A4FC4)),
    ThemeAccentOption(ThemeAccent.Orange, R.string.main_accent_orange, androidx.compose.ui.graphics.Color(0xFFC2410C)),
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.main_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLanguage.entries.forEach { language ->
                    SettingsRadioRow(
                        title = appLanguageLabel(language),
                        subtitle = null,
                        selected = language == current,
                        onSelect = { onSelect(language) },
                    )
                }
            }
        },
        confirmButton = {
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.main_cancel)) }
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(R.string.main_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onSelectFollowApp != null) {
                    SettingsRadioRow(
                        title = stringResource(R.string.settings_accent_follow_app),
                        subtitle = null,
                        selected = followAppSelected,
                        onSelect = { onSelectFollowApp() },
                    )
                }
                themeAccentOptions.forEach { option ->
                    SettingsRadioRow(
                        title = stringResource(option.labelRes),
                        subtitle = null,
                        selected = option.accent == current && !followAppSelected,
                        onSelect = { onSelect(option.accent) },
                        trailing = { AccentSwatch(option.swatch) },
                    )
                }
                SettingsRadioRow(
                    title = stringResource(R.string.main_accent_custom),
                    subtitle = formatRgbHex(customArgb),
                    selected = current == ThemeAccent.Custom && !followAppSelected,
                    onSelect = { pickingCustom = true },
                    trailing = { AccentSwatch(androidx.compose.ui.graphics.Color(customArgb)) },
                )
            }
        },
        confirmButton = {
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.main_close)) }
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

/** Small round colour preview at the end of an accent option row. */
@Composable
private fun AccentSwatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                CircleShape,
            ),
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
    previewWeekStart: WeekStartDay = WeekStartDay.Monday,
): Int {
    val currentTargetDate = today.plusDays(currentDayOffset.toLong())
    if (termStart == null) {
        val currentPageStart = displayWeekStartOf(currentTargetDate, previewWeekStart)
        val targetPageStart = displayWeekStartOf(today, previewWeekStart)
            .plusWeeks((selectedWeek - currentWeek).toLong())
        return ChronoUnit.DAYS.between(
            today, targetPageStart.plusDays(ChronoUnit.DAYS.between(currentPageStart, currentTargetDate)),
        ).toInt()
    }
    val currentTargetMonday = currentTargetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val targetWeek = selectedWeek.coerceAtLeast(1)
    val targetMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks((targetWeek - 1).toLong())
    val weekdayOffset = ChronoUnit.DAYS.between(currentTargetMonday, currentTargetDate)
    val targetDate = targetMonday.plusDays(weekdayOffset)
    return ChronoUnit.DAYS.between(today, targetDate).toInt()
}

private const val PLUGIN_PREFETCH_DELAY_MILLIS = 3_000L

/** Which appearance shortcut opened the chooser: theme (colour or schedule style) or background. */
internal enum class DrawerAppearance { Theme, Background }

/**
 * First step of the drawer's appearance shortcuts: pick what to change before opening its editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerAppearanceChooser(
    kind: DrawerAppearance,
    onDismiss: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    themeAccent: ThemeAccent,
    themeCustomArgb: Int,
    onThemeAccent: (ThemeAccent) -> Unit,
    onThemeColor: () -> Unit,
    onScheduleStyle: () -> Unit,
    onScheduleBackground: () -> Unit,
    onWidgetBackground: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (kind == DrawerAppearance.Theme) R.string.drawer_chooser_theme_title else R.string.drawer_chooser_background_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (kind == DrawerAppearance.Theme) {
                // Light/dark and the accent apply on tap and the sheet stays open, so the result shows behind it.
                DrawerChooserSectionLabel(stringResource(R.string.drawer_chooser_light_dark))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        SettingsOptionChip(
                            text = stringResource(
                                when (mode) {
                                    ThemeMode.System -> R.string.main_theme_system
                                    ThemeMode.Light -> R.string.main_theme_light
                                    ThemeMode.Dark -> R.string.main_theme_dark
                                },
                            ),
                            selected = mode == themeMode,
                            onClick = { onThemeMode(mode) },
                        )
                    }
                }
                DrawerChooserSectionLabel(stringResource(R.string.drawer_chooser_theme_color))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    themeAccentOptions.forEach { option ->
                        val chosen = option.accent == themeAccent
                        val label = stringResource(option.labelRes)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onThemeAccent(option.accent) }
                                .semantics { contentDescription = label; selected = chosen },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(option.swatch)
                                    .then(
                                        if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .border(5.dp, option.swatch, CircleShape)
                                        else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (chosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Custom colours need the full picker, so this swatch hands over to the existing dialog.
                    val customChosen = themeAccent == ThemeAccent.Custom
                    val customLabel = stringResource(R.string.main_accent_custom)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onThemeColor)
                            .semantics { contentDescription = customLabel; selected = customChosen },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (customChosen) androidx.compose.ui.graphics.Color(themeCustomArgb) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = null,
                                tint = if (customChosen) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                DrawerChooserRow(
                    icon = Icons.Rounded.Style,
                    title = stringResource(R.string.settings_dest_schedule_style),
                    subtitle = stringResource(R.string.drawer_chooser_schedule_style_hint),
                    onClick = onScheduleStyle,
                )
            } else {
                DrawerChooserRow(
                    icon = Icons.Rounded.Wallpaper,
                    title = stringResource(R.string.settings_schedule_background),
                    subtitle = stringResource(R.string.drawer_chooser_schedule_background_hint),
                    onClick = onScheduleBackground,
                )
                DrawerChooserRow(
                    icon = Icons.Rounded.Widgets,
                    title = stringResource(R.string.settings_dest_widget_background),
                    subtitle = stringResource(R.string.drawer_chooser_widget_background_hint),
                    onClick = onWidgetBackground,
                )
            }
        }
    }
}

@Composable
private fun DrawerChooserSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DrawerChooserRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Top-bar legend entry that explains the round end-of-course badge drawn on timetable cards. */
@Composable
private fun EndWarningLegendEntry(final: Boolean) {
    val color = if (final) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier.size(13.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (final) "1" else "2",
                color = if (final) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = stringResource(if (final) R.string.main_end_legend_final else R.string.main_end_legend_near),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
