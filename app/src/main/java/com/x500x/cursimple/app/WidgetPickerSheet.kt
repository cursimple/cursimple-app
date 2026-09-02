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
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.widget.WidgetCatalog
import com.x500x.cursimple.feature.widget.WidgetCatalogEntry

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

    // 每次进入组合都立即重新检查，用户可能没有离开应用就移除了小组件（例如在启动器浮层里操作）。
    LaunchedEffect(Unit) { refreshTick++ }

    val installedCounts = remember(refreshTick) {
        entries.associate { it.id to WidgetCatalog.installedCount(context, it) }
    }

    var pendingConfirm by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var manualGuideEntry by remember { mutableStateOf<WidgetCatalogEntry?>(null) }
    var showWidgetHelp by remember { mutableStateOf(false) }

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
                text = if (pinSupported) {
                    stringResource(R.string.widget_sheet_intro_supported)
                } else {
                    stringResource(R.string.widget_sheet_intro_unsupported)
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
                    when (WidgetCatalog.requestPin(context, entry)) {
                        WidgetCatalog.PinRequestResult.Started -> {
                            onShowMessage(context.getString(R.string.widget_toast_requested))
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

@Composable
private fun WidgetHelpDialog(
    onDismiss: () -> Unit,
) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_help_got_it)) }
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
) {
    val genericSteps = listOf(
        stringResource(R.string.widget_manual_step1),
        stringResource(R.string.widget_manual_step2),
        stringResource(R.string.widget_manual_step3),
        stringResource(R.string.widget_manual_step4, entry.title),
    )
    val vendorGuide = widgetVendorGuide(vendor)
    val permissionTipRes = when (vendor) {
        WidgetCatalog.LauncherVendor.Miui -> R.string.widget_manual_hint_miui
        WidgetCatalog.LauncherVendor.Huawei -> R.string.widget_manual_hint_huawei
        WidgetCatalog.LauncherVendor.Oppo -> R.string.widget_manual_hint_oppo
        WidgetCatalog.LauncherVendor.Vivo -> R.string.widget_manual_hint_other
        WidgetCatalog.LauncherVendor.Samsung,
        WidgetCatalog.LauncherVendor.Other -> null
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
        dismissButton = if (permissionTip != null) {
            {
                TextButton(onClick = {
                    onOpenAppDetails()
                    onDismiss()
                }) { Text(stringResource(R.string.widget_manual_open_settings)) }
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
