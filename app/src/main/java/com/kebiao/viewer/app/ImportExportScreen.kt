package com.kebiao.viewer.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
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
import com.kebiao.viewer.app.util.QrCodeCodec
import com.kebiao.viewer.app.util.QrScannerView
import com.kebiao.viewer.app.util.ScheduleShareCodec
import com.kebiao.viewer.app.util.ScheduleSharePayload
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    termName: String?,
    termStartDate: LocalDate?,
    onApplyImport: (TermSchedule?, List<CourseItem>, (Result<Pair<Int, Int>>) -> Unit) -> Unit,
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

    fun consumeScannedText(text: String) {
        ScheduleShareCodec.decode(text)
            .onSuccess { pendingImport = it }
            .onFailure { error ->
                Toast.makeText(context, error.message ?: "无法识别二维码", Toast.LENGTH_SHORT).show()
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            Toast.makeText(context, "未授予相机权限，无法扫码", Toast.LENGTH_SHORT).show()
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
