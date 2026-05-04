package com.kebiao.viewer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kebiao.viewer.feature.widget.WidgetCatalog
import com.kebiao.viewer.feature.widget.WidgetCatalogEntry
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = WidgetCatalog.entries(context)
    val pinSupported = WidgetCatalog.isPinSupported(context)

    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                refreshTick++
            }
        }
        val filter = android.content.IntentFilter(WidgetCatalog.ACTION_WIDGET_INSTALLED_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Recheck immediately each time the sheet enters composition, since the user
    // may have removed a widget without leaving the app (e.g. from a launcher overlay).
    LaunchedEffect(Unit) { refreshTick++ }

    val installedCounts = remember(refreshTick) {
        entries.associate { it.id to WidgetCatalog.installedCount(context, it) }
    }

    var pendingConfirm by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var manualGuideEntry by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var pendingPinWatch by remember { mutableStateOf<PinWatch?>(null) }
    var showWidgetHelp by remember { mutableStateOf(false) }

    val pinWatch = pendingPinWatch
    if (pinWatch != null) {
        LaunchedEffect(pinWatch.token) {
            delay(6_000)
            val installedNow = WidgetCatalog.installedCount(context, pinWatch.entry)
            if (installedNow <= pinWatch.baselineCount) {
                manualGuideEntry = pinWatch.entry
            }
            pendingPinWatch = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "添加桌面小组件",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showWidgetHelp = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = "查看桌面小组件帮助",
                    )
                }
            }
            Text(
                text = if (pinSupported) {
                    "选择一种样式，确认后系统会弹出「添加到桌面」确认框。\n" +
                        "如果系统弹窗没出现，请点右上角帮助，或长按桌面空白处 → 小组件 → 搜索「课表查看」。"
                } else {
                    "当前启动器不支持一键添加。请点右上角帮助，或长按桌面空白处 → 小组件 → 搜索「课表查看」。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            entries.forEach { entry ->
                val installed = (installedCounts[entry.id] ?: 0) > 0
                val count = installedCounts[entry.id] ?: 0
                WidgetPickerRow(
                    entry = entry,
                    installed = installed,
                    installedCount = count,
                    enabled = true,
                    onClick = {
                        if (!pinSupported) {
                            manualGuideEntry = entry
                            return@WidgetPickerRow
                        }
                        pendingConfirm = entry
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showWidgetHelp) {
        WidgetHelpDialog(
            onDismiss = { showWidgetHelp = false },
        )
    }

    val guideEntry = manualGuideEntry
    if (guideEntry != null) {
        ManualAddGuideDialog(
            entry = guideEntry,
            vendor = remember { WidgetCatalog.detectLauncherVendor(context) },
            onOpenAppDetails = { WidgetCatalog.openAppDetails(context) },
            onDismiss = { manualGuideEntry = null },
        )
    }

    val pending = pendingConfirm
    if (pending != null) {
        val installed = (installedCounts[pending.id] ?: 0) > 0
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text("添加桌面小组件") },
            text = {
                Text(
                    if (installed) {
                        "「${pending.title}」已经添加到桌面，仍要再添加一个吗？"
                    } else {
                        "确定要把「${pending.title}」添加到桌面吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pending
                    pendingConfirm = null
                    val baseline = WidgetCatalog.installedCount(context, entry)
                    val ok = WidgetCatalog.requestPin(context, entry)
                    if (ok) {
                        onShowMessage("已发起添加请求，请在系统弹窗中确认")
                        pendingPinWatch = PinWatch(
                            entry = entry,
                            baselineCount = baseline,
                            token = System.nanoTime(),
                        )
                    } else {
                        manualGuideEntry = entry
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun WidgetHelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("桌面小组件帮助") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "不同系统会把同一个入口叫作「小组件」「小部件」「插件」或「窗口小工具」。找不到时优先搜索「课表查看」或「课表查看器」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WidgetGuideSection(
                    title = "通用添加",
                    steps = listOf(
                        "长按桌面空白处，或在桌面双指捏合，进入桌面编辑。",
                        "进入「小组件 / 小部件 / 插件 / Widgets」。",
                        "找到「课表查看器」，长按需要的样式并拖到桌面空位后松手。",
                    ),
                )
                WidgetGuideSection(
                    title = "调整大小",
                    steps = listOf(
                        "添加后，在桌面长按小组件并松手。",
                        "如果小组件支持调整大小，边缘会出现边框或控制点。",
                        "拖动边缘或控制点调整大小，完成后点一下桌面空白处。",
                    ),
                )
                WidgetGuideSection(
                    title = "一加 / ColorOS",
                    steps = listOf(
                        "长按桌面空白处，进入桌面编辑。",
                        "依次选择「卡片」→「插件」，找到「课表查看器」。",
                        "长按要添加的小组件，拖到桌面空位后松手。",
                        "想改尺寸时，长按桌面小组件，松手后等待一会；出现边框或控制点后即可拖动调整大小。",
                    ),
                )
                WidgetGuideSection(
                    title = "华为 / 荣耀",
                    steps = listOf(
                        "在桌面双指捏合，进入桌面编辑。",
                        "进入「服务卡片」，再进入底部的「窗口小工具」。",
                        "找到「课表查看器」并添加到桌面。",
                    ),
                )
                WidgetGuideSection(
                    title = "小米 / 红米",
                    steps = listOf(
                        "在桌面双指捏合，点击底部「添加小部件」。",
                        "点右上角搜索；如果当前页只有系统卡片，继续进入底部「安卓小部件」。",
                        "搜索或找到「课表查看器」，选择要添加的样式。",
                    ),
                )
                WidgetGuideSection(
                    title = "OPPO / realme",
                    steps = listOf(
                        "在桌面双指捏合，进入桌面编辑。",
                        "选择「插件」或「小组件」。",
                        "找到「课表查看器」，长按并拖到桌面。",
                    ),
                )
                WidgetGuideSection(
                    title = "vivo / iQOO",
                    steps = listOf(
                        "在桌面双指捏合，点击底部「组件」。",
                        "如果列表里没有看到应用，向上滑到底部进入「更多组件」。",
                        "找到「课表查看器」后添加到桌面。",
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@Composable
private fun WidgetGuideSection(
    title: String,
    steps: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        steps.forEach { step ->
            Text(
                text = "• $step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetPickerRow(
    entry: WidgetCatalogEntry,
    installed: Boolean,
    installedCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when (entry.id) {
        "next" -> Icons.Rounded.AccessTime
        "today" -> Icons.Rounded.Today
        "reminder" -> Icons.Rounded.NotificationsActive
        else -> Icons.Rounded.Widgets
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TrailingStatus(installed = installed, installedCount = installedCount)
        }
    }
}

private data class PinWatch(
    val entry: WidgetCatalogEntry,
    val baselineCount: Int,
    val token: Long,
)

@Composable
private fun ManualAddGuideDialog(
    entry: WidgetCatalogEntry,
    vendor: WidgetCatalog.LauncherVendor,
    onOpenAppDetails: () -> Boolean,
    onDismiss: () -> Unit,
) {
    val genericSteps = listOf(
        "1. 长按桌面空白处，或在桌面双指捏合",
        "2. 选择「小部件 / 小组件 / 插件 / Widgets」",
        "3. 找到「课表查看器」或搜索「课表查看」",
        "4. 长按「${entry.title}」并拖到桌面空位后松手",
    )
    val vendorSteps = when (vendor) {
        WidgetCatalog.LauncherVendor.Miui ->
            listOf("小米 / 红米：进入「添加小部件」后可点右上角搜索；如果当前页没有应用小组件，请进入底部「安卓小部件」。")
        WidgetCatalog.LauncherVendor.Huawei ->
            listOf("华为 / 荣耀：在桌面双指捏合后，进入「服务卡片」→「窗口小工具」，再找「课表查看器」。")
        WidgetCatalog.LauncherVendor.Oppo ->
            listOf("OPPO / realme / 一加：入口通常叫「插件」或「小组件」；一加可从「卡片」→「插件」进入。")
        WidgetCatalog.LauncherVendor.Vivo ->
            listOf("vivo / iQOO：进入底部「组件」后，如果没有看到应用，请滑到底部点「更多组件」。")
        WidgetCatalog.LauncherVendor.Samsung,
        WidgetCatalog.LauncherVendor.Other -> null
    }
    val permissionTip = when (vendor) {
        WidgetCatalog.LauncherVendor.Miui ->
            "如果一键添加仍然没有弹窗，可到「应用信息 → 权限管理」检查「显示弹出式窗口」与「后台弹出界面」。"
        WidgetCatalog.LauncherVendor.Huawei ->
            "如果仍无法唤起添加确认，可到「应用信息 → 权限」检查悬浮窗相关权限。"
        WidgetCatalog.LauncherVendor.Oppo ->
            "如果一键添加仍然没有弹窗，可到「应用信息 → 权限管理」检查「悬浮窗」和后台弹出相关权限。"
        WidgetCatalog.LauncherVendor.Vivo ->
            "如果仍无法唤起添加确认，可到「应用信息 → 权限」检查「悬浮窗」。"
        WidgetCatalog.LauncherVendor.Samsung,
        WidgetCatalog.LauncherVendor.Other -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加到桌面") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "系统没有弹出添加确认框，请按以下步骤操作：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                genericSteps.forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodySmall)
                }
                if (vendorSteps != null) {
                    Spacer(Modifier.height(4.dp))
                    vendorSteps.forEach { step ->
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (permissionTip != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        permissionTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        dismissButton = if (permissionTip != null) {
            {
                TextButton(onClick = {
                    onOpenAppDetails()
                    onDismiss()
                }) { Text("打开应用设置") }
            }
        } else null,
    )
}

@Composable
private fun TrailingStatus(installed: Boolean, installedCount: Int) {
    if (installed) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (installedCount > 1) "已添加 ×$installedCount" else "已添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "添加",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
