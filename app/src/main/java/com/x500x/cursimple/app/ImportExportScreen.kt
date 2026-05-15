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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    termName: String?,
    termStartDate: LocalDate?,
    webDavConfig: WebDavConfig,
    webDavClient: WebDavClient,
    aiImportConfig: AiImportConfig,
    aiImportClient: AiScheduleImportClient,
    onApplyImport: (TermSchedule?, List<CourseItem>, (Result<Pair<Int, Int>>) -> Unit) -> Unit,
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
    var aiBusy by remember { mutableStateOf(false) }
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

    fun runAiImport(uri: Uri) {
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
                pendingImport = ScheduleSharePayload(
                    termName = termName,
                    termStartDate = termStartDate?.toString(),
                    schedule = payload.schedule,
                    manualCourses = payload.manualCourses,
                )
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
                .padding(16.dp),
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
                onRestore = { backup ->
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
                    }
                },
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

    pendingImport?.let { payload ->
        val courseCount = payload.schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0
        val manualCount = payload.manualCourses.size
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
                    payload.termStartDate?.takeIf(String::isNotBlank)?.let { iso ->
                        Text("开学日期：$iso", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("导入课程：$courseCount 门", style = MaterialTheme.typography.bodyMedium)
                    Text("手动添加课程：$manualCount 门", style = MaterialTheme.typography.bodyMedium)
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
                    "同步范围包含课表数据、学期、提醒、插件记录与用户设置。远端目录：cursimple/backups。已加载 $backupCount 个备份。"
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
