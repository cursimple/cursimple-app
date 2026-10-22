@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.schedule.sharing.QrCodeCodec
import com.x500x.cursimple.app.util.launchScheduleShare
import com.x500x.cursimple.app.util.ScheduleShareCodec
import com.x500x.cursimple.app.util.ScheduleSharePayload
import com.x500x.cursimple.app.ai.AiImportConfig
import com.x500x.cursimple.app.ai.aiImportErrorText
import com.x500x.cursimple.app.ai.AiScheduleImportClient
import com.x500x.cursimple.app.webdav.WebDavBackupFile
import com.x500x.cursimple.app.webdav.webDavErrorText
import com.x500x.cursimple.app.webdav.WebDavClient
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.app.util.ScheduleIcsExporter
import com.x500x.cursimple.app.util.ScheduleIcsImporter
import com.x500x.cursimple.app.util.ScheduleWakeUpImporter
import com.x500x.cursimple.app.util.AiScheduleCompat
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Extension
import com.x500x.cursimple.app.util.ScheduleImageExporter
import com.x500x.cursimple.app.util.ScheduleImageOptionsStore
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.app.util.ScheduleImageLayout
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.app.util.ScheduleShareDecodeException
import com.x500x.cursimple.app.util.ScheduleShareDecodeReason
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    weekStartDay: WeekStartDay = WeekStartDay.Monday,
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    scheduleEvents: List<ScheduleEvent> = emptyList(),
    termName: String?,
    termStartDate: LocalDate?,
    timingProfile: TermTimingProfile?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    webDavConfig: WebDavConfig,
    webDavClient: WebDavClient,
    aiImportConfig: AiImportConfig,
    aiImportClient: AiScheduleImportClient,
    onApplyImport: (TermSchedule?, List<CourseItem>, String?, (Result<Pair<Int, Int>>) -> Unit) -> Unit,
    onApplyTermStartDate: (LocalDate) -> Unit,
    onCreateAppBackup: suspend () -> AppBackupPayload,
    onRestoreAppBackup: suspend (AppBackupPayload) -> Unit,
    onOpenWebDavSettings: () -> Unit,
    onOpenAiImportSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ScheduleSharePayload?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var webDavTask by remember { mutableStateOf<WebDavTask?>(null) }
    val webDavBusy = webDavTask != null
    var remoteBackups by remember { mutableStateOf<List<WebDavBackupFile>>(emptyList()) }
    var backupsLoaded by remember { mutableStateOf(false) }
    var showImageDialog by rememberSaveable { mutableStateOf(false) }
    var showCalendarDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<WebDavBackupFile?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var icsBusy by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    var showTextShare by rememberSaveable { mutableStateOf(false) }
    var showTextImport by rememberSaveable { mutableStateOf(false) }
    var showAiScheduleDialog by rememberSaveable { mutableStateOf(false) }
    val maxImageWeek = remember(schedule, manualCourses) {
        ScheduleImageLayout.maxWeekNumber(schedule, manualCourses)
    }
    var imageWeek by remember(termStartDate, maxImageWeek) {
        val current = termStartDate
            ?.let { ScheduleImageLayout.currentWeekNumber(it, BeijingTime.today()) }
            ?.coerceIn(1, maxImageWeek)
            ?: 1
        mutableIntStateOf(current)
    }
    var imageAllWeeks by rememberSaveable { mutableStateOf(true) }
    var imageWallpaperMode by rememberSaveable { mutableStateOf(false) }
    var imageOptions by remember { mutableStateOf(ScheduleImageOptionsStore.load(context)) }
    val contentScrollState = rememberScrollState()
    val appBackupJson = remember {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    var aiCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun consumeScannedText(text: String) {
        ScheduleShareCodec.decode(text)
            .onSuccess { pendingImport = it }
            .onFailure { error ->
                Toast.makeText(context, context.shareDecodeMessage(error), Toast.LENGTH_SHORT).show()
            }
    }

    fun exportIcs() {
        if (icsBusy) return
        icsBusy = true
        scope.launch {
            val outcome = ScheduleIcsExporter.export(
                context = context,
                termName = termName,
                termStartDate = termStartDate,
                schedule = schedule,
                manualCourses = manualCourses,
                timingProfile = timingProfile,
                overrides = temporaryScheduleOverrides,
                holidayCalendar = holidayCalendar,
            )
            icsBusy = false
            val intent = outcome.intent
            if (intent == null) {
                Toast.makeText(
                    context,
                    outcome.failureReason ?: context.getString(R.string.ie_export_failed_retry),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val skippedNote = if (outcome.skipped.isNotEmpty()) {
                context.resources.getQuantityString(
                    R.plurals.ie_ics_skipped_note,
                    outcome.skipped.size,
                    outcome.skipped.size,
                )
            } else {
                ""
            }
            Toast.makeText(
                context,
                context.resources.getQuantityString(
                    R.plurals.ie_ics_generated,
                    outcome.eventCount,
                    outcome.eventCount,
                    skippedNote,
                ),
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                val chooser = android.content.Intent.createChooser(intent, context.getString(R.string.ie_ics_chooser_title)).apply {
                    clipData = intent.clipData
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.ie_share_launch_failed, it.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runAiImport(uri: Uri) {
        if (aiBusy) return
        if (!aiImportConfig.isComplete) {
            Toast.makeText(context, context.getString(R.string.ie_ai_configure_first), Toast.LENGTH_SHORT).show()
            onOpenAiImportSettings()
            return
        }
        aiBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { aiImportClient.importFromImage(context, uri, aiImportConfig) }
            }.onSuccess { payload ->
                val courseCount = payload.schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0
                val manualCount = payload.manualCourses.size
                pendingImport = ScheduleSharePayload(
                    termName = termName,
                    termStartDate = termStartDate?.toString(),
                    schedule = payload.schedule,
                    manualCourses = payload.manualCourses,
                )
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.ie_ai_recognized,
                        courseCount + manualCount,
                        courseCount + manualCount,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.ie_ai_import_failed,
                        context.aiImportErrorText(it)
                            ?: it.message
                            ?: context.getString(R.string.ie_unknown_error),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            aiBusy = false
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = withContext(Dispatchers.IO) { decodeQrFromUri(context, uri) }
            payload.onSuccess { pendingImport = it }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: context.getString(R.string.ie_qr_unrecognized), Toast.LENGTH_SHORT).show()
                }
        }
    }

    val aiImagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) runAiImport(uri)
    }

    val aiCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = aiCameraUri
        if (saved && uri != null) runAiImport(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            Toast.makeText(context, context.getString(R.string.ie_camera_permission_denied_scan), Toast.LENGTH_SHORT).show()
        }
    }

    val aiCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, context.getString(R.string.ie_camera_permission_denied_photo), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val uri = createAiCameraUri(context)
        aiCameraUri = uri
        aiCameraLauncher.launch(uri)
    }

    val pickIcsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { ScheduleIcsImporter.parse(it) }
                }
                if (payload != null) {
                    pendingImport = payload
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ICS 导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickWakeUpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { ScheduleWakeUpImporter.parse(it) }
                }
                if (payload != null) {
                    pendingImport = payload
                }
            } catch (e: Exception) {
                Toast.makeText(context, "WakeUp 导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restoreRemoteBackup(backup: WebDavBackupFile) {
        webDavTask = WebDavTask.Restore
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val text = webDavClient.download(webDavConfig, backup.href).toString(Charsets.UTF_8)
                    appBackupJson.decodeFromString(AppBackupPayload.serializer(), text)
                }
            }.onSuccess {
                runCatching {
                    withContext(Dispatchers.IO) { onRestoreAppBackup(it) }
                }.onSuccess {
                    Toast.makeText(context, context.getString(R.string.ie_webdav_restore_success), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.ie_restore_failed,
                            context.webDavErrorText(error)
                                ?: error.message
                                ?: context.getString(R.string.ie_unknown_error),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.ie_restore_failed,
                        it.message ?: context.getString(R.string.ie_unknown_error),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            webDavTask = null
            pendingRestore = null
        }
    }

    fun openScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openAiCamera() {
        if (!aiImportConfig.isComplete) {
            Toast.makeText(context, context.getString(R.string.ie_ai_configure_first), Toast.LENGTH_SHORT).show()
            onOpenAiImportSettings()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createAiCameraUri(context)
            aiCameraUri = uri
            aiCameraLauncher.launch(uri)
        } else {
            aiCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showScanner) {
        ScannerOverlay(
            onScanned = { text ->
                showScanner = false
                consumeScannedText(text)
            },
            onCancel = { showScanner = false },
        )
        BackHandler { showScanner = false }
        return
    }

    val canExport = schedule != null || manualCourses.isNotEmpty()

    fun uploadBackup() {
        if (!webDavConfig.isComplete) {
            onOpenWebDavSettings()
            return
        }
        val fileName = "cursimple-backup-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())}${AppBackupPayload.FILE_EXTENSION}"
        webDavTask = WebDavTask.Upload
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val payload = onCreateAppBackup()
                    val encoded = appBackupJson.encodeToString(AppBackupPayload.serializer(), payload)
                        .toByteArray(Charsets.UTF_8)
                    webDavClient.uploadBackup(webDavConfig, fileName, encoded)
                }
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.ie_webdav_upload_success, it.name),
                    Toast.LENGTH_SHORT,
                ).show()
                if (backupsLoaded) {
                    remoteBackups = withContext(Dispatchers.IO) {
                        runCatching { webDavClient.listBackups(webDavConfig) }.getOrDefault(remoteBackups)
                    }
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.ie_upload_failed,
                        context.webDavErrorText(it)
                            ?: it.message
                            ?: context.getString(R.string.ie_unknown_error),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            webDavTask = null
        }
    }

    fun refreshBackups() {
        if (!webDavConfig.isComplete) {
            onOpenWebDavSettings()
            return
        }
        webDavTask = WebDavTask.List
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { webDavClient.listBackups(webDavConfig) }
            }.onSuccess {
                remoteBackups = it
                backupsLoaded = true
                if (it.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.resources.getQuantityString(R.plurals.ie_backups_found, 0, 0),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.ie_list_backups_failed,
                        context.webDavErrorText(it)
                            ?: it.message
                            ?: context.getString(R.string.ie_unknown_error),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            webDavTask = null
        }
    }

    val sharePayload = remember(termName, termStartDate, schedule, manualCourses) {
        ScheduleSharePayload(termName = termName, termStartDate = termStartDate?.toString(),
            schedule = schedule, manualCourses = manualCourses)
    }
    fun generateQr() {
        if (qrBusy) return
        qrBusy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { QrCodeCodec.encodeToBitmap(ScheduleShareCodec.encode(sharePayload), size = 720) }
            }
            qrBusy = false
            result.onSuccess { qrBitmap = it; qrError = null }
                .onFailure { qrError = context.getString(R.string.ie_qr_too_large) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    AppAtomicText(stringResource(R.string.ie_screen_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                },
                navigationIcon = {
                    AppCircleIconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ie_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(contentScrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCardGroup(title = stringResource(R.string.ie_section_import)) {
                ImportBlock(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = stringResource(R.string.ie_import_panel_title),
                    body = stringResource(R.string.ie_import_panel_body),
                ) {
                    Button(
                        onClick = { openScanner() },
                        enabled = !importing,
                        contentPadding = BlockButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) { BlockButtonText(stringResource(R.string.ie_scan)) }
                    AppOutlinedButton(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !importing,
                        contentPadding = BlockButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) { BlockButtonText(stringResource(R.string.ie_pick_from_gallery)) }
                }
                ImportExportRow(
                    icon = Icons.Rounded.ContentPaste,
                    title = stringResource(R.string.ie_text_import_title),
                    subtitle = stringResource(R.string.ie_text_import_body),
                    enabled = !importing,
                    onClick = { showTextImport = true },
                )
                ImportExportRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.ie_import_ics_title),
                    subtitle = stringResource(R.string.ie_import_ics_body),
                    enabled = !importing,
                    onClick = { pickIcsLauncher.launch(arrayOf("*/*", "text/calendar")) },
                )
                ImportExportRow(
                    icon = Icons.Rounded.School,
                    title = stringResource(R.string.ie_import_wakeup_title),
                    subtitle = stringResource(R.string.ie_import_wakeup_body),
                    enabled = !importing,
                    onClick = { pickWakeUpLauncher.launch(arrayOf("*/*", "application/json", "application/zip")) },
                )
                ImportExportRow(
                    icon = Icons.Rounded.Extension,
                    title = stringResource(R.string.ie_import_aischedule_title),
                    subtitle = stringResource(R.string.ie_import_aischedule_body),
                    enabled = !importing,
                    onClick = { showAiScheduleDialog = true },
                )
                ImportBlock(
                    icon = Icons.Rounded.ImageSearch,
                    title = stringResource(R.string.ie_ai_panel_title),
                    body = if (aiImportConfig.isComplete) {
                        stringResource(R.string.ie_ai_panel_body)
                    } else {
                        stringResource(R.string.ie_ai_panel_unconfigured)
                    },
                ) {
                    if (!aiImportConfig.isComplete) {
                        AppOutlinedButton(
                            onClick = onOpenAiImportSettings,
                            contentPadding = BlockButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) { BlockButtonText(stringResource(R.string.ie_go_configure)) }
                    } else {
                        AppOutlinedButton(
                            onClick = {
                                aiImagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !aiBusy,
                            contentPadding = BlockButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            BlockButtonText(
                                if (aiBusy) {
                                    stringResource(R.string.ie_ai_recognizing)
                                } else {
                                    stringResource(R.string.ie_ai_pick_image)
                                },
                            )
                        }
                        AppOutlinedButton(
                            onClick = { openAiCamera() },
                            enabled = !aiBusy,
                            contentPadding = BlockButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) { BlockButtonText(stringResource(R.string.ie_ai_take_photo)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsCardGroup(title = stringResource(R.string.ie_section_export)) {
                    ImportExportRow(
                        icon = Icons.Rounded.QrCode2,
                        title = stringResource(R.string.ie_qr_export_title),
                        subtitle = stringResource(if (qrBusy) R.string.ie_generating else R.string.ie_qr_export_body),
                        enabled = canExport && !qrBusy,
                        onClick = { generateQr() },
                    )
                    ImportExportRow(
                        icon = Icons.AutoMirrored.Rounded.TextSnippet,
                        title = stringResource(R.string.ie_text_share_title),
                        subtitle = stringResource(R.string.ie_text_share_body),
                        enabled = canExport,
                        onClick = { showTextShare = true },
                    )
                    ImportExportRow(
                        icon = Icons.Rounded.Image,
                        title = stringResource(R.string.ie_image_panel_title),
                        subtitle = stringResource(R.string.ie_image_row_body),
                        enabled = canExport,
                        onClick = { showImageDialog = true },
                    )
                    // Stays enabled without data so a previous calendar write can still be undone.
                    ImportExportRow(
                        icon = Icons.Rounded.EventAvailable,
                        title = stringResource(R.string.ie_calendar_row_title),
                        subtitle = stringResource(R.string.ie_calendar_row_body),
                        onClick = { showCalendarDialog = true },
                    )
                }
                if (!canExport) FootnoteText(stringResource(R.string.ie_no_schedule_body))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsCardGroup(title = stringResource(R.string.ie_section_backup)) {
                    if (!webDavConfig.isComplete) {
                        ImportExportRow(
                            icon = Icons.Rounded.CloudUpload,
                            title = stringResource(R.string.ie_webdav_panel_title),
                            subtitle = stringResource(R.string.ie_webdav_panel_unconfigured),
                            onClick = onOpenWebDavSettings,
                        )
                    } else {
                        ImportExportRow(
                            icon = Icons.Rounded.CloudUpload,
                            title = stringResource(R.string.ie_webdav_upload_action),
                            subtitle = if (webDavTask == WebDavTask.Upload) {
                                stringResource(R.string.ie_processing)
                            } else {
                                stringResource(R.string.ie_webdav_upload_body)
                            },
                            enabled = webDavTask == null,
                            onClick = { uploadBackup() },
                        )
                        ImportExportRow(
                            icon = Icons.Rounded.CloudDownload,
                            title = stringResource(R.string.ie_webdav_remote_backups),
                            subtitle = when {
                                webDavTask == WebDavTask.List -> stringResource(R.string.ie_processing)
                                backupsLoaded -> pluralStringResource(
                                    R.plurals.ie_webdav_panel_body,
                                    remoteBackups.size,
                                    remoteBackups.size,
                                )
                                else -> stringResource(R.string.ie_webdav_restore_body)
                            },
                            enabled = webDavTask == null,
                            onClick = { refreshBackups() },
                        )
                        remoteBackups.take(5).forEach { backup ->
                            ImportExportRow(
                                icon = Icons.Rounded.History,
                                title = backup.name,
                                atomicTitle = false,
                                subtitle = backup.lastModified ?: "${backup.size} bytes",
                                enabled = webDavTask == null,
                                onClick = { pendingRestore = backup },
                            )
                        }
                    }
                }
                if (webDavConfig.isComplete) FootnoteText(stringResource(R.string.ie_webdav_note))
            }

            FootnoteText(stringResource(R.string.ie_local_notice))
        }
    }

    if (showImageDialog) {
        ScheduleImageDialog(
            weekNumber = imageWeek,
            maxWeekNumber = maxImageWeek,
            allWeeks = imageAllWeeks,
            wallpaperMode = imageWallpaperMode,
            options = imageOptions,
            generationKey = listOf(schedule, manualCourses, scheduleEvents, termName, termStartDate, timingProfile,
                temporaryScheduleOverrides, holidayCalendar, weekStartDay, localeTags, imageWallpaperMode),
            generateImage = { wallpaper, options ->
                ScheduleImageExporter.export(context = context, termName = termName, termStartDate = termStartDate,
                    weekNumber = imageWeek, schedule = schedule, manualCourses = manualCourses,
                    timingProfile = timingProfile, overrides = temporaryScheduleOverrides,
                    holidayCalendar = holidayCalendar, weekStartDay = weekStartDay, allWeeks = imageAllWeeks,
                    wallpaperMode = wallpaper, events = scheduleEvents, options = options)
            },
            onOptionsChange = { imageOptions = it; ScheduleImageOptionsStore.save(context, it) },
            onAllWeeksChange = { imageAllWeeks = it },
            onWallpaperModeChange = { imageWallpaperMode = it },
            onWeekChange = { imageWeek = it.coerceIn(1, maxImageWeek) },
            onShare = { intent ->
                runCatching { launchScheduleShare(context, intent, context.getString(R.string.ie_image_chooser_title)) }
                    .onSuccess { showImageDialog = false }
                    .onFailure { Toast.makeText(context, context.getString(R.string.ie_share_launch_failed, it.message), Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { showImageDialog = false },
        )
    }

    if (showCalendarDialog) {
        SystemCalendarExportDialog(
            canExport = canExport,
            termStartDate = termStartDate,
            schedule = schedule,
            manualCourses = manualCourses,
            timingProfile = timingProfile,
            overrides = temporaryScheduleOverrides,
            holidayCalendar = holidayCalendar,
            icsBusy = icsBusy,
            onExportIcs = { exportIcs() },
            onDismiss = { showCalendarDialog = false },
        )
    }

    if (showTextShare) ScheduleTextShareDialog(sharePayload, onDismiss = { showTextShare = false })
    if (showTextImport) ScheduleTextImportDialog(onDecoded = {
        showTextImport = false
        pendingImport = it
    }, onDismiss = { showTextImport = false })

    if (showAiScheduleDialog) {
        var aiScheduleInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAiScheduleDialog = false },
            title = { Text(stringResource(R.string.ie_import_aischedule_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.ie_import_aischedule_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = aiScheduleInput,
                        onValueChange = { aiScheduleInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        placeholder = { Text("[{\"name\": \"...\", \"weeks\": [1,2], \"day\": 1, \"sections\": [1,2]}]") },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = aiScheduleInput.isNotBlank(),
                    onClick = {
                        showAiScheduleDialog = false
                        try {
                            val payload = AiScheduleCompat.parseJson(aiScheduleInput)
                            pendingImport = payload
                        } catch (e: Exception) {
                            Toast.makeText(context, "小爱课程表解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.ie_section_import))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showAiScheduleDialog = false }) {
                    Text(stringResource(R.string.ie_cancel))
                }
            },
        )
    }

    qrBitmap?.let { bitmap ->
        ScheduleQrShareDialog(bitmap, onDismiss = { qrBitmap = null })
    }

    qrError?.let { message ->
        AlertDialog(
            onDismissRequest = { qrError = null },
            title = { Text(stringResource(R.string.ie_qr_error_title)) },
            text = { Text(message) },
            confirmButton = { Button(onClick = { qrError = null; showTextShare = true }) {
                AppAtomicText(stringResource(R.string.ie_text_share_title))
            } },
            dismissButton = { AppOutlinedButton(onClick = { qrError = null }) { AppAtomicText(stringResource(R.string.ie_close)) } },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!webDavBusy) pendingRestore = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.ie_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.ie_restore_file, backup.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.ie_restore_time,
                            backup.lastModified ?: stringResource(R.string.ie_unknown),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ie_restore_size, backup.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ie_restore_warning_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.ie_restore_warning_irreversible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.ie_restore_warning_secrets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !webDavBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = { restoreRemoteBackup(backup) },
                ) {
                    Text(
                        if (webDavBusy) {
                            stringResource(R.string.ie_restoring)
                        } else {
                            stringResource(R.string.ie_restore_confirm_action)
                        },
                    )
                }
            },
            dismissButton = {
                AppOutlinedButton(
                    enabled = !webDavBusy,
                    onClick = { pendingRestore = null },
                ) { Text(stringResource(R.string.ie_cancel)) }
            },
        )
    }

    pendingImport?.let { payload ->
        val courseCount = payload.schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0
        val manualCount = payload.manualCourses.size
        val defaultTermName = payload.termName?.takeIf(String::isNotBlank)
            ?: stringResource(com.x500x.cursimple.feature.schedule.R.string.schedule_import_new_term_default)
        var asNewTerm by remember(payload) { mutableStateOf(true) }
        var termNameInput by remember(payload) { mutableStateOf(defaultTermName) }
        val newTermName = termNameInput.trim().takeIf { asNewTerm && it.isNotBlank() }
        val importedTermStart = remember(payload.termStartDate) {
            parseImportedTermStartDate(payload.termStartDate)
        }
        AlertDialog(
            onDismissRequest = { if (!importing) pendingImport = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.ie_import_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    payload.termName?.takeIf(String::isNotBlank)?.let { name ->
                        Text(
                            text = stringResource(R.string.ie_import_term, name),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    importedTermStart?.let { date ->
                        Text(
                            text = stringResource(R.string.ie_import_term_start, date.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ie_import_course_count, courseCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ie_import_manual_count, manualCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    importedTermStart?.takeIf { it != termStartDate }?.let { date ->
                        Text(
                            text = stringResource(
                                R.string.ie_import_term_start_change,
                                date.toString(),
                                termStartDate?.toString() ?: stringResource(R.string.ie_not_set),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = if (asNewTerm) {
                            stringResource(R.string.ie_import_new_term_notice)
                        } else {
                            stringResource(R.string.ie_import_overwrite_notice)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = asNewTerm,
                            onCheckedChange = { asNewTerm = it },
                            enabled = !importing,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ie_import_as_new_term),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (asNewTerm) {
                        OutlinedTextField(
                            value = termNameInput,
                            onValueChange = { termNameInput = it },
                            singleLine = true,
                            enabled = !importing,
                            label = { Text(stringResource(R.string.import_diff_new_term_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !importing,
                    onClick = {
                        importing = true
                        onApplyImport(payload.schedule, payload.manualCourses, newTermName) { result ->
                            importing = false
                            pendingImport = null
                            result
                                .onSuccess { (imported, manual) ->
                                    importedTermStart?.let(onApplyTermStartDate)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ie_import_success, imported, manual),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.ie_import_failed,
                                            context.webDavErrorText(it)
                                        ?: it.message
                                        ?: context.getString(R.string.ie_unknown_error),
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        }
                    },
                ) {
                    Text(
                        if (importing) {
                            stringResource(R.string.ie_importing)
                        } else {
                            stringResource(R.string.ie_import_confirm_action)
                        },
                    )
                }
            },
            dismissButton = {
                AppOutlinedButton(
                    enabled = !importing,
                    onClick = { pendingImport = null },
                ) { Text(stringResource(R.string.ie_cancel)) }
            },
        )
    }
}

private enum class WebDavTask { Upload, List, Restore }

private val BlockButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun BlockButtonText(text: String) {
    AppAtomicText(text = text, style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center))
}

/** Titled card section with a row of actions spanning the card width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportBlock(
    icon: ImageVector,
    title: String,
    body: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            FlowRow(modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = if (maxWidth >= (280 * fontScale).dp) 2 else 1,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

/** Mirrors SettingsActionRow insets so SettingsCardGroup dividers line up, with a disabled state. */
@Composable
private fun ImportExportRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    atomicTitle: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (atomicTitle) {
                AppAtomicText(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            } else {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FootnoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

internal fun parseImportedTermStartDate(iso: String?): LocalDate? {
    val trimmed = iso?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed) }.getOrNull()
}

private fun decodeQrFromUri(
    context: android.content.Context,
    uri: Uri,
): Result<ScheduleSharePayload> = runCatching {
    // Downsample gallery QR images to a 2048-pixel long edge to limit decoding memory.
    val bitmap = com.x500x.cursimple.app.util.decodeSampledBitmap(context, uri, QR_IMPORT_MAX_EDGE_PX)
        ?: error(context.getString(R.string.ie_image_format_unsupported))
    try {
        val text = QrCodeCodec.decodeBitmap(bitmap) ?: error(context.getString(R.string.ie_image_no_qr))
        ScheduleShareCodec.decode(text).getOrThrow()
    } finally {
        bitmap.recycle()
    }
}

private const val QR_IMPORT_MAX_EDGE_PX = 2048

private fun createAiCameraUri(context: android.content.Context): Uri {
    val file = java.io.File(context.cacheDir, "ai-import/capture-${System.currentTimeMillis()}.jpg")
    file.parentFile?.mkdirs()
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

internal fun android.content.Context.shareDecodeMessage(error: Throwable): String = when {
    error is ScheduleShareDecodeException -> getString(
        when (error.reason) {
            ScheduleShareDecodeReason.NotShareData -> R.string.share_decode_not_share_data
            ScheduleShareDecodeReason.TooLarge -> R.string.share_decode_too_large
        },
    )
    else -> getString(R.string.share_decode_not_share_data)
}
