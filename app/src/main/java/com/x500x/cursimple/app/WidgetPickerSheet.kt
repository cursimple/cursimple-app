@file:Suppress("LocalContextGetResourceValueCall")

package com.x500x.cursimple.app

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.widget.WidgetCatalog
import com.x500x.cursimple.feature.widget.WidgetCatalogEntry
import com.x500x.cursimple.feature.widget.WidgetDiagnostics
import com.x500x.cursimple.core.data.VendorPermissionKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
    vendorPermissionAcks: Set<String> = emptySet(),
    onVendorPermissionAckChange: (String, Boolean) -> Unit = { _, _ -> },
    pinUnsupportedOnDevice: Boolean = false,
    onPinUnsupportedOnDeviceChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = WidgetCatalog.entries(context)
    val pinSupported = WidgetCatalog.isPinSupported(context)

    var refreshTick by remember { mutableIntStateOf(0) }
    var lastResumeAtMillis by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                // 系统的「添加到桌面」弹窗盖在应用上面时我们不算超时，
                // 只有用户回到应用之后再确认不出来，才判定这次添加没成功
                lastResumeAtMillis = android.os.SystemClock.elapsedRealtime()
            }
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

    // 每次进入组合都立即重新检查，用户可能没有离开应用就移除了小组件（例如在启动器浮层里操作）。
    LaunchedEffect(Unit) { refreshTick++ }

    val installedCounts = remember(refreshTick) {
        entries.associate { it.id to WidgetCatalog.installedCount(context, it) }
    }

    var pendingConfirm by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    // 发出添加请求后盯着实际装上的数量，启动器悄悄丢掉请求时用户不会一直以为加上了
    var pinWatch by remember { mutableStateOf<PinWatch?>(null) }
    var pinUnconfirmed by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var manualGuideEntry by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var showWidgetHelp by remember { mutableStateOf(false) }
    var permissionNoticeEntry by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    // 用户在手动步骤里点了「再试一次」：只放行这一次，不改动已记下的判定
    var forcePinOnce by remember { mutableStateOf(false) }
    // 已知不响应的桌面（vivo / OPPO），或这台机器实测失败过
    val preferManualAdd = remember(pinUnsupportedOnDevice, forcePinOnce) {
        !forcePinOnce && (pinUnsupportedOnDevice || WidgetCatalog.pinLikelyIgnored(context))
    }
    // 只有识别到厂商桌面、且用户还没把「桌面快捷方式」勾成已开启时才提示
    val needsVendorPermissionNotice = remember(vendorPermissionAcks) {
        WidgetCatalog.detectLauncherVendor(context) != WidgetCatalog.LauncherVendor.Other &&
            VendorPermissionKey.SHORTCUT_PIN !in vendorPermissionAcks
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
                    text = stringResource(R.string.widget_sheet_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showWidgetHelp = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.widget_help_desc),
                    )
                }
            }
            Text(
                text = when {
                    preferManualAdd -> stringResource(R.string.widget_sheet_intro_pin_dead)
                    pinSupported -> stringResource(R.string.widget_sheet_intro_supported)
                    else -> stringResource(R.string.widget_sheet_intro_unsupported)
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

    LaunchedEffect(pinWatch) {
        val watch = pinWatch ?: return@LaunchedEffect
        val entry = watch.entry
        val baseline = watch.baseline
        val requestedAt = android.os.SystemClock.elapsedRealtime()
        var pinned = false
        while (true) {
            if (WidgetCatalog.installedCount(context, entry) > baseline) {
                pinned = true
                break
            }
            val now = android.os.SystemClock.elapsedRealtime()
            // 用户已经从系统弹窗回到应用，且又等了一小会儿还是没出现，才算失败
            val backInApp = lastResumeAtMillis > requestedAt &&
                now - lastResumeAtMillis > PIN_SETTLE_MILLIS
            if (backInApp || now - requestedAt > PIN_GIVE_UP_MILLIS) break
            // 前半分钟盯紧一点，之后放慢，用户一直停在系统弹窗上也不会空转几百次
            val elapsed = now - requestedAt
            kotlinx.coroutines.delay(if (elapsed < PIN_FAST_POLL_WINDOW_MILLIS) PIN_POLL_MILLIS else PIN_SLOW_POLL_MILLIS)
        }
        pinWatch = null
        refreshTick++
        when {
            pinned -> onShowMessage(context.getString(R.string.widget_toast_added, entry.title))
            // 厂商桌面受理了请求又悄悄丢掉时，换下一份 provider 再来一次，
            // 别一次落空就让用户去走手动添加
            watch.hasMore -> {
                when (val retry = WidgetCatalog.requestPin(context, entry, attempt = watch.attempt + 1)) {
                    is WidgetCatalog.PinRequestResult.Started -> {
                        onShowMessage(context.getString(R.string.widget_toast_retrying))
                        pinWatch = PinWatch(
                            entry = entry,
                            baseline = WidgetCatalog.installedCount(context, entry),
                            attempt = watch.attempt + 1,
                            hasMore = retry.hasMore,
                        )
                    }
                    else -> {
                        pinUnconfirmed = entry
                        onPinUnsupportedOnDeviceChange(true)
                    }
                }
            }
            else -> {
                // 所有 provider 都试过还是没出现，记下这台手机不吃这一套
                pinUnconfirmed = entry
                forcePinOnce = false
                onPinUnsupportedOnDeviceChange(true)
            }
        }
    }


    permissionNoticeEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { permissionNoticeEntry = null },
            title = { Text(stringResource(R.string.widget_pin_permission_notice_title)) },
            text = { Text(stringResource(R.string.widget_pin_permission_notice_body)) },
            confirmButton = {
                TextButton(onClick = {
                    permissionNoticeEntry = null
                    WidgetCatalog.openShortcutPermission(context)
                }) { Text(stringResource(R.string.widget_pin_permission_notice_open)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        permissionNoticeEntry = null
                        pendingConfirm = entry
                    }) { Text(stringResource(R.string.widget_pin_permission_notice_skip)) }
                    TextButton(onClick = {
                        permissionNoticeEntry = null
                        onVendorPermissionAckChange(VendorPermissionKey.SHORTCUT_PIN, true)
                        pendingConfirm = entry
                    }) { Text(stringResource(R.string.widget_pin_permission_notice_done)) }
                }
            },
        )
    }

    val unconfirmed = pinUnconfirmed
    if (unconfirmed != null) {
        AlertDialog(
            onDismissRequest = { pinUnconfirmed = null },
            title = { Text(stringResource(R.string.widget_pin_unconfirmed_title)) },
            text = { Text(stringResource(R.string.widget_pin_unconfirmed_body, unconfirmed.title)) },
            confirmButton = {
                // 厂商机型上先给权限入口：这项没给的话再点几次一键添加也还是没反应
                TextButton(onClick = {
                    pinUnconfirmed = null
                    WidgetCatalog.openShortcutPermission(context)
                }) { Text(stringResource(R.string.widget_pin_unconfirmed_permission)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinUnconfirmed = null
                    manualGuideEntry = unconfirmed
                }) {
                    Text(stringResource(R.string.widget_pin_unconfirmed_manual))
                }
            },
        )
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
            onRetryPin = if (preferManualAdd) {
                {
                    forcePinOnce = true
                    onPinUnsupportedOnDeviceChange(false)
                    pendingConfirm = guideEntry
                }
            } else {
                null
            },
        )
    }

    val pending = pendingConfirm
    if (pending != null) {
        val installed = (installedCounts[pending.id] ?: 0) > 0
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(stringResource(R.string.widget_sheet_title)) },
            text = {
                Text(
                    if (installed) {
                        stringResource(R.string.widget_confirm_installed, pending.title)
                    } else {
                        stringResource(R.string.widget_confirm_new, pending.title)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pending
                    pendingConfirm = null
                    // 这家桌面不响应一键添加，别再让用户点一次等十几秒
                    if (preferManualAdd) {
                        manualGuideEntry = entry
                        return@TextButton
                    }
                    // 厂商机型上缺这两项时桌面会把添加请求静默丢掉，先问一次再发请求。
                    // 用户选「先不管」就直接继续，下次点添加还会再问，直到勾了已开启
                    if (needsVendorPermissionNotice) {
                        permissionNoticeEntry = entry
                        return@TextButton
                    }
                    when (val result = WidgetCatalog.requestPin(context, entry)) {
                        is WidgetCatalog.PinRequestResult.Started -> {
                            onShowMessage(context.getString(R.string.widget_toast_requested))
                            pinWatch = PinWatch(
                                entry = entry,
                                baseline = installedCounts[entry.id] ?: 0,
                                attempt = 0,
                                hasMore = result.hasMore,
                            )
                        }
                        WidgetCatalog.PinRequestResult.Unsupported,
                        is WidgetCatalog.PinRequestResult.Failed -> {
                            manualGuideEntry = entry
                        }
                    }
                }) { Text(stringResource(R.string.widget_confirm_add)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text(stringResource(R.string.widget_confirm_cancel)) }
            },
        )
    }
}

/** 一次一键添加请求的跟踪状态：请求的是第几个 provider，落空后还有没有下一个可试。 */
private data class PinWatch(
    val entry: WidgetCatalogEntry,
    val baseline: Int,
    val attempt: Int,
    val hasMore: Boolean,
)

@Composable
private fun WidgetHelpDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 隔着屏幕猜不出「为什么这台手机上找不到小组件」，把判断依据列出来让用户能直接反馈
    val report = remember { WidgetDiagnostics.collect(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_help_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.widget_help_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WidgetGuideSection(
                    title = stringResource(R.string.widget_help_generic_title),
                    steps = listOf(
                        stringResource(R.string.widget_help_generic_step1),
                        stringResource(R.string.widget_help_generic_step2),
                        stringResource(R.string.widget_help_generic_step3),
                        // 桌面的小组件清单有缓存，这一条是各家课表应用的共同经验
                        stringResource(R.string.widget_help_generic_step4),
                    ),
                )
                WidgetGuideSection(
                    title = stringResource(R.string.widget_help_resize_title),
                    steps = listOf(
                        stringResource(R.string.widget_help_resize_step1),
                        stringResource(R.string.widget_help_resize_step2),
                        stringResource(R.string.widget_help_resize_step3),
                    ),
                )
                widgetVendorGuides().forEach { guide ->
                    WidgetGuideSection(
                        title = stringResource(guide.titleRes),
                        steps = guide.stepRes.map { stringResource(it) },
                    )
                }
                WidgetGuideSection(
                    title = stringResource(R.string.widget_diagnostics_title),
                    steps = report.lines.map { (label, value) -> "$label: $value" },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_help_got_it)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("cursimple-widget-diagnostics", report.asText()),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_diagnostics_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) { Text(stringResource(R.string.widget_diagnostics_copy)) }
        },
    )
}

/** 厂商指引的稳定标识，避免用会随语言变化的标题去匹配。 */
private enum class WidgetGuideKey { OnePlus, Huawei, Xiaomi, Oppo, Vivo }

private data class WidgetGuide(
    val key: WidgetGuideKey,
    val titleRes: Int,
    val stepRes: List<Int>,
)

private fun widgetVendorGuides(): List<WidgetGuide> = listOf(
    WidgetGuide(
        key = WidgetGuideKey.OnePlus,
        titleRes = R.string.widget_guide_oneplus_title,
        stepRes = listOf(
            R.string.widget_guide_oneplus_step1,
            R.string.widget_guide_oneplus_step2,
            R.string.widget_guide_oneplus_step3,
            R.string.widget_guide_oneplus_step4,
        ),
    ),
    WidgetGuide(
        key = WidgetGuideKey.Huawei,
        titleRes = R.string.widget_guide_huawei_title,
        stepRes = listOf(
            R.string.widget_guide_huawei_step1,
            R.string.widget_guide_huawei_step2,
            R.string.widget_guide_huawei_step3,
            R.string.widget_guide_huawei_step4,
        ),
    ),
    WidgetGuide(
        key = WidgetGuideKey.Xiaomi,
        titleRes = R.string.widget_guide_xiaomi_title,
        stepRes = listOf(
            R.string.widget_guide_xiaomi_step1,
            R.string.widget_guide_xiaomi_step2,
            R.string.widget_guide_xiaomi_step3,
        ),
    ),
    WidgetGuide(
        key = WidgetGuideKey.Oppo,
        titleRes = R.string.widget_guide_oppo_title,
        stepRes = listOf(
            R.string.widget_guide_oppo_step1,
            R.string.widget_guide_oppo_step2,
            R.string.widget_guide_oppo_step3,
        ),
    ),
    WidgetGuide(
        key = WidgetGuideKey.Vivo,
        titleRes = R.string.widget_guide_vivo_title,
        stepRes = listOf(
            R.string.widget_guide_vivo_step1,
            R.string.widget_guide_vivo_step2,
            R.string.widget_guide_vivo_step3,
            R.string.widget_guide_vivo_step4,
        ),
    ),
)

private fun widgetVendorGuide(vendor: WidgetCatalog.LauncherVendor): WidgetGuide? = when (vendor) {
    WidgetCatalog.LauncherVendor.Miui -> guideOf(WidgetGuideKey.Xiaomi)
    WidgetCatalog.LauncherVendor.Huawei -> guideOf(WidgetGuideKey.Huawei)
    WidgetCatalog.LauncherVendor.Oppo -> guideOf(WidgetGuideKey.OnePlus)
    WidgetCatalog.LauncherVendor.Vivo -> guideOf(WidgetGuideKey.Vivo)
    WidgetCatalog.LauncherVendor.Samsung,
    WidgetCatalog.LauncherVendor.Other -> null
}

private fun guideOf(key: WidgetGuideKey): WidgetGuide =
    widgetVendorGuides().first { it.key == key }

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

@Composable
private fun ManualAddGuideDialog(
    entry: WidgetCatalogEntry,
    vendor: WidgetCatalog.LauncherVendor,
    onOpenAppDetails: () -> Boolean,
    onDismiss: () -> Unit,
    /** 非空表示当前已判定这台手机的一键添加没反应，留一条重试的退路。 */
    onRetryPin: (() -> Unit)? = null,
) {
    val genericSteps = listOf(
        stringResource(R.string.widget_manual_step1),
        stringResource(R.string.widget_manual_step2),
        stringResource(R.string.widget_manual_step3),
        stringResource(R.string.widget_manual_step4, entry.title),
    )
    val vendorGuide = widgetVendorGuide(vendor)
    // vivo 这类已知不响应一键添加的桌面，去应用设置里开什么都没用——
    // 实测「桌面快捷方式」开着也照样不弹窗。别再给一个点了也解决不了问题的按钮
    val permissionTipRes = when {
        onRetryPin != null -> null
        else -> when (vendor) {
            WidgetCatalog.LauncherVendor.Miui -> R.string.widget_manual_hint_miui
            WidgetCatalog.LauncherVendor.Huawei -> R.string.widget_manual_hint_huawei
            WidgetCatalog.LauncherVendor.Oppo -> R.string.widget_manual_hint_oppo
            WidgetCatalog.LauncherVendor.Vivo -> R.string.widget_manual_hint_other
            WidgetCatalog.LauncherVendor.Samsung,
            WidgetCatalog.LauncherVendor.Other -> null
        }
    }
    val permissionTip = permissionTipRes?.let { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.widget_manual_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                genericSteps.forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodySmall)
                }
                if (vendorGuide != null) {
                    Spacer(Modifier.height(4.dp))
                    vendorGuide.stepRes.forEach { step ->
                        Text(
                            stringResource(step),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_help_got_it)) }
        },
        dismissButton = {
            Row {
                // 判定有可能是误判（换了桌面、系统升级），留一条回去的路
                onRetryPin?.let { retry ->
                    TextButton(onClick = {
                        onDismiss()
                        retry()
                    }) { Text(stringResource(R.string.widget_manual_retry_pin)) }
                }
                if (permissionTip != null) {
                    TextButton(onClick = {
                        onOpenAppDetails()
                        onDismiss()
                    }) { Text(stringResource(R.string.widget_manual_open_settings)) }
                }
            }
        },
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
                    text = if (installedCount > 1) stringResource(R.string.widget_installed_count, installedCount) else stringResource(R.string.widget_installed),
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
                contentDescription = stringResource(R.string.widget_add_desc),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 回到应用后再观察这么久，仍没出现就认为添加没成功。 */
private const val PIN_SETTLE_MILLIS = 2_500L

/** 用户一直没回来时的兜底上限。 */
private const val PIN_GIVE_UP_MILLIS = 180_000L
private const val PIN_POLL_MILLIS = 400L
private const val PIN_SLOW_POLL_MILLIS = 2_000L
private const val PIN_FAST_POLL_WINDOW_MILLIS = 30_000L
