package com.x500x.cursimple.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.app.util.QrCodeCodec
import com.x500x.cursimple.app.util.QrScannerView
import com.x500x.cursimple.app.util.ScheduleShareCodec
import com.x500x.cursimple.app.util.ScheduleSharePayload
import com.x500x.cursimple.app.ai.AiImportConfig
import com.x500x.cursimple.app.ai.AiScheduleImportClient
import com.x500x.cursimple.app.webdav.WebDavBackupFile
import com.x500x.cursimple.app.webdav.WebDavClient
import com.x500x.cursimple.app.webdav.WebDavConfig
import com.x500x.cursimple.app.util.ScheduleIcsExporter
import com.x500x.cursimple.app.util.ScheduleImageExporter
import com.x500x.cursimple.app.util.ScheduleImageLayout
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    termName: String?,
    termStartDate: LocalDate?,
    timingProfile: TermTimingProfile?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    webDavConfig: WebDavConfig,
    webDavClient: WebDavClient,
    aiImportConfig: AiImportConfig,
    aiImportClient: AiScheduleImportClient,
    onApplyImport: (TermSchedule?, List<CourseItem>, (Result<Pair<Int, Int>>) -> Unit) -> Unit,
    onApplyTermStartDate: (LocalDate) -> Unit,
    onCreateAppBackup: suspend () -> AppBackupPayload,
    onRestoreAppBackup: suspend (AppBackupPayload) -> Unit,
    onOpenWebDavSettings: () -> Unit,
    onOpenAiImportSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ScheduleSharePayload?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var webDavBusy by remember { mutableStateOf(false) }
    var remoteBackups by remember { mutableStateOf<List<WebDavBackupFile>>(emptyList()) }
    var pendingRestore by remember { mutableStateOf<WebDavBackupFile?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var icsBusy by remember { mutableStateOf(false) }
    var imageBusy by remember { mutableStateOf(false) }
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
                Toast.makeText(context, error.message ?: "无法识别二维码", Toast.LENGTH_SHORT).show()
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
                    outcome.failureReason ?: "导出失败，请稍后重试",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val skippedNote = if (outcome.skipped.isNotEmpty()) {
                "，${outcome.skipped.size} 门课程缺少上课时间未导出"
            } else {
                ""
            }
            Toast.makeText(
                context,
                "已生成 ${outcome.eventCount} 个日历事件$skippedNote",
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                val chooser = android.content.Intent.createChooser(intent, "导出课表日历").apply {
                    clipData = intent.clipData
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }.onFailure {
                Toast.makeText(context, "无法启动分享：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportScheduleImage() {
        if (imageBusy) return
        imageBusy = true
        scope.launch {
            val outcome = ScheduleImageExporter.export(
                context = context,
                termName = termName,
                termStartDate = termStartDate,
                weekNumber = imageWeek,
                schedule = schedule,
                manualCourses = manualCourses,
                timingProfile = timingProfile,
                overrides = temporaryScheduleOverrides,
                holidayCalendar = holidayCalendar,
            )
            imageBusy = false
            val intent = outcome.intent
            if (intent == null) {
                Toast.makeText(
                    context,
                    outcome.failureReason ?: "导出失败，请稍后重试",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            runCatching {
                val chooser = android.content.Intent.createChooser(intent, "分享课表图片").apply {
                    clipData = intent.clipData
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }.onFailure {
                Toast.makeText(context, "无法启动分享：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runAiImport(uri: Uri) {
        if (aiBusy) return
        if (!aiImportConfig.isComplete) {
            Toast.makeText(context, "请先配置 AI 识图导入", Toast.LENGTH_SHORT).show()
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
                    "AI 已识别 ${courseCount + manualCount} 门课程，请确认导入",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(context, "AI 导入失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, error.message ?: "无法识别二维码", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "未授予相机权限，无法扫码", Toast.LENGTH_SHORT).show()
        }
    }

    val aiCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予相机权限，无法拍照导入", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val uri = createAiCameraUri(context)
        aiCameraUri = uri
        aiCameraLauncher.launch(uri)
    }

    fun restoreRemoteBackup(backup: WebDavBackupFile) {
        webDavBusy = true
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
                    Toast.makeText(context, "WebDAV 备份已恢复", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "恢复失败：${error.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, "恢复失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
            webDavBusy = false
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
            Toast.makeText(context, "请先配置 AI 识图导入", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "导入 / 导出课程",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(
                icon = Icons.Rounded.Upload,
                title = "导出二维码",
                body = if (canExport) {
                    "把当前学期的课表生成二维码，让同学用本应用扫码或选图导入。"
                } else {
                    "暂无课表数据，先同步或添加课程后再导出。"
                },
                actionLabel = "生成二维码",
                actionEnabled = canExport,
                onAction = {
                    val payload = ScheduleSharePayload(
                        termName = termName,
                        termStartDate = termStartDate?.toString(),
                        schedule = schedule,
                        manualCourses = manualCourses,
                    )
                    val encoded = ScheduleShareCodec.encode(payload)
                    runCatching { QrCodeCodec.encodeToBitmap(encoded, size = 720) }
                        .onSuccess {
                            qrBitmap = it
                            qrError = null
                        }
                        .onFailure {
                            qrError = "课表数据过大，二维码无法容纳。请减少课程后再试。"
                        }
                },
            )

            Panel(
                icon = Icons.Rounded.CalendarMonth,
                title = "导出到日历 (ICS)",
                body = if (canExport) {
                    "把当前学期课表导出为通用日历文件，可导入系统日历、Google 日历、Outlook 等，无需联网或额外权限。"
                } else {
                    "暂无课表数据，先同步或添加课程后再导出。"
                },
                actionLabel = if (icsBusy) "生成中..." else "生成 .ics 文件",
                actionEnabled = canExport && !icsBusy,
                onAction = { exportIcs() },
            )

            ScheduleImagePanel(
                weekNumber = imageWeek,
                maxWeekNumber = maxImageWeek,
                canExport = canExport,
                busy = imageBusy,
                onWeekChange = { imageWeek = it.coerceIn(1, maxImageWeek) },
                onExport = { exportScheduleImage() },
            )

            ImportPanel(
                enabled = !importing,
                onScan = { openScanner() },
                onPickImage = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            WebDavPanel(
                enabled = !webDavBusy,
                configured = webDavConfig.isComplete,
                backupCount = remoteBackups.size,
                onUpload = {
                    if (!webDavConfig.isComplete) {
                        Toast.makeText(context, "请先在设置页配置 WebDAV", Toast.LENGTH_SHORT).show()
                        onOpenWebDavSettings()
                        return@WebDavPanel
                    }
                    val fileName = "cursimple-backup-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())}${AppBackupPayload.FILE_EXTENSION}"
                    webDavBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val payload = onCreateAppBackup()
                                val encoded = appBackupJson.encodeToString(AppBackupPayload.serializer(), payload)
                                    .toByteArray(Charsets.UTF_8)
                                webDavClient.uploadBackup(webDavConfig, fileName, encoded)
                            }
                        }.onSuccess {
                            Toast.makeText(context, "WebDAV 备份已上传：${it.name}", Toast.LENGTH_SHORT).show()
                            remoteBackups = withContext(Dispatchers.IO) {
                                runCatching { webDavClient.listBackups(webDavConfig) }.getOrDefault(remoteBackups)
                            }
                        }.onFailure {
                            Toast.makeText(context, "上传失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                        webDavBusy = false
                    }
                },
                onRefresh = {
                    if (!webDavConfig.isComplete) {
                        Toast.makeText(context, "请先在设置页配置 WebDAV", Toast.LENGTH_SHORT).show()
                        onOpenWebDavSettings()
                        return@WebDavPanel
                    }
                    webDavBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { webDavClient.listBackups(webDavConfig) }
                        }.onSuccess {
                            remoteBackups = it
                            Toast.makeText(context, "已找到 ${it.size} 个备份", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "获取备份失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                        webDavBusy = false
                    }
                },
                backups = remoteBackups,
                onRestore = { backup -> pendingRestore = backup },
            )

            AiImportPanel(
                enabled = !aiBusy,
                configured = aiImportConfig.isComplete,
                onPickImage = {
                    if (!aiImportConfig.isComplete) {
                        Toast.makeText(context, "请先配置 AI 识图导入", Toast.LENGTH_SHORT).show()
                        onOpenAiImportSettings()
                    } else {
                        aiImagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                },
                onTakePhoto = { openAiCamera() },
            )

            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "二维码完全在本地生成与解析，不依赖任何服务器。导入会覆盖当前学期已有的课表与手动添加的课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }

    qrBitmap?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { qrBitmap = null },
            title = { Text("课表二维码") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "课表二维码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "请同学使用本应用「导入二维码」从相册导入这张图片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrBitmap = null }) { Text("关闭") }
            },
        )
    }

    qrError?.let { message ->
        AlertDialog(
            onDismissRequest = { qrError = null },
            title = { Text("生成二维码失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { qrError = null }) { Text("知道了") }
            },
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
            title = { Text("用这个备份覆盖本机数据？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("备份文件：${backup.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "备份时间：${backup.lastModified ?: "未知"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("文件大小：${backup.size} 字节", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "恢复会先清空本机现有数据，再用这个备份整体覆盖：课表、手动课程、学期档案与开学日期、提醒规则、插件与组件记录、小组件外观和全部应用设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "本机现有数据会被全部丢弃，且无法撤销。请确认这就是你要用的备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "备份不含 WebDAV 密码与 AI 接口密钥，恢复后这两项仍是本机当前填写的值。",
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
                ) { Text(if (webDavBusy) "恢复中..." else "覆盖并恢复") }
            },
            dismissButton = {
                TextButton(
                    enabled = !webDavBusy,
                    onClick = { pendingRestore = null },
                ) { Text("取消") }
            },
        )
    }

    pendingImport?.let { payload ->
        val courseCount = payload.schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0
        val manualCount = payload.manualCourses.size
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
            title = { Text("确认导入课表") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    payload.termName?.takeIf(String::isNotBlank)?.let { name ->
                        Text("学期：$name", style = MaterialTheme.typography.bodyMedium)
                    }
                    importedTermStart?.let { date ->
                        Text("开学日期：$date", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("导入课程：$courseCount 门", style = MaterialTheme.typography.bodyMedium)
                    Text("手动添加课程：$manualCount 门", style = MaterialTheme.typography.bodyMedium)
                    importedTermStart?.takeIf { it != termStartDate }?.let { date ->
                        Text(
                            text = "将同时把开学日期改为 $date（当前：${termStartDate?.toString() ?: "未设置"}），周次会按新日期重新计算。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = "导入会覆盖当前学期已有的课表与手动课程，请确认无误。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !importing,
                    onClick = {
                        importing = true
                        onApplyImport(payload.schedule, payload.manualCourses) { result ->
                            importing = false
                            pendingImport = null
                            result
                                .onSuccess { (imported, manual) ->
                                    importedTermStart?.let(onApplyTermStartDate)
                                    Toast.makeText(
                                        context,
                                        "已导入 $imported 门课程，$manual 门手动课程",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "导入失败：${it.message ?: "未知错误"}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        }
                    },
                ) { Text(if (importing) "导入中..." else "确认导入") }
            },
            dismissButton = {
                TextButton(
                    enabled = !importing,
                    onClick = { pendingImport = null },
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AiImportPanel(
    enabled: Boolean,
    configured: Boolean,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ImageSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "AI 识图导入",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (configured) {
                    "从相册选择课表截图，或直接拍照识别并生成当前课表。"
                } else {
                    "请先在设置页配置 API URL 和 Key。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!configured) {
                    Button(
                        onClick = onPickImage,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text("去配置")
                    }
                } else {
                    AiImportActionCard(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = if (enabled) "相册导入" else "识别中",
                        body = "选择已有课表截图",
                        enabled = enabled,
                        onClick = onPickImage,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 118.dp),
                    )
                    AiImportActionCard(
                        icon = Icons.Rounded.PhotoCamera,
                        title = "拍照导入",
                        body = "需要相机权限",
                        enabled = enabled,
                        onClick = onTakePhoto,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 118.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiImportActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 118.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WebDavPanel(
    enabled: Boolean,
    configured: Boolean,
    backupCount: Int,
    backups: List<WebDavBackupFile>,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (WebDavBackupFile) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "WebDAV 备份 / 恢复",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (configured) {
                    "同步范围包含课表数据、学期、提醒、插件记录与用户设置。为避免密码上传到网盘，备份不含 WebDAV 密码与 AI 接口密钥，换机后需要重新填写。远端目录：cursimple/backups。已加载 $backupCount 个备份。"
                } else {
                    "请先在设置页配置 WebDAV URL、账号和密码。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!configured) {
                    Button(
                        onClick = onUpload,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("去配置")
                    }
                } else {
                    Button(
                        onClick = onUpload,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (enabled) "上传备份" else "处理中")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("远端备份")
                    }
                }
            }
            backups.take(5).forEach { backup ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { onRestore(backup) },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = backup.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = backup.lastModified ?: "${backup.size} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPanel(
    enabled: Boolean,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "导入二维码",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "扫描同学手机上的二维码，或从相册选择二维码图片；本地解析后覆盖当前学期的课表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScan,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("扫一扫")
                }
                OutlinedButton(
                    onClick = onPickImage,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("相册选图")
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        QrScannerView(
            onScanned = onScanned,
            modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.material3.Surface(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
        ) {
            Text(
                text = "将二维码对准屏幕，识别成功后自动返回。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScheduleImagePanel(
    weekNumber: Int,
    maxWeekNumber: Int,
    canExport: Boolean,
    busy: Boolean,
    onWeekChange: (Int) -> Unit,
    onExport: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "导出课表图片",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (canExport) {
                    "把选定一周的课表画成一张 PNG，直接发给同学或家长，对方不用装应用也能看。已排除假日，并按临时调课取课。"
                } else {
                    "暂无课表数据，先同步或添加课程后再导出。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onWeekChange(weekNumber - 1) },
                    enabled = canExport && !busy && weekNumber > 1,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "上一周",
                    )
                }
                Text(
                    text = "第 $weekNumber 周",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { onWeekChange(weekNumber + 1) },
                    enabled = canExport && !busy && weekNumber < maxWeekNumber,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "下一周",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onExport,
                enabled = canExport && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "生成中..." else "生成并分享图片")
            }
        }
    }
}

@Composable
private fun Panel(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

// 分享负载里的开学日期为 ISO yyyy-MM-dd，缺失或格式非法时返回 null
internal fun parseImportedTermStartDate(iso: String?): LocalDate? {
    val trimmed = iso?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed) }.getOrNull()
}

private fun decodeQrFromUri(
    context: android.content.Context,
    uri: Uri,
): Result<ScheduleSharePayload> = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri).use { stream ->
        requireNotNull(stream) { "无法打开图片" }
        BitmapFactory.decodeStream(stream)
    } ?: error("图片格式不支持")
    val text = QrCodeCodec.decodeBitmap(bitmap) ?: error("图片中没有可识别的二维码")
    ScheduleShareCodec.decode(text).getOrThrow()
}

private fun createAiCameraUri(context: android.content.Context): Uri {
    val file = java.io.File(context.cacheDir, "ai-import/capture-${System.currentTimeMillis()}.jpg")
    file.parentFile?.mkdirs()
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
