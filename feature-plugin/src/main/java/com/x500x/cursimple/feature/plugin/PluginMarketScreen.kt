package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest

data class BundledPluginCatalogEntry(
    val pluginId: String,
    val name: String,
    val description: String,
)

@Composable
fun PluginMarketRoute(
    installedPlugins: List<InstalledPluginRecord>,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    pendingWebSession: WebSessionRequest?,
    bundledCatalog: List<BundledPluginCatalogEntry>,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onAddBundledPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PluginMarketScreen(
        installedPlugins = installedPlugins,
        enabledPluginIds = enabledPluginIds,
        syncingPluginId = syncingPluginId,
        syncStatusMessage = syncStatusMessage,
        pendingWebSession = pendingWebSession,
        bundledCatalog = bundledCatalog,
        onSetPluginEnabled = onSetPluginEnabled,
        onSyncPlugin = onSyncPlugin,
        onAddBundledPlugin = onAddBundledPlugin,
        onCompleteWebSession = onCompleteWebSession,
        onCancelWebSession = onCancelWebSession,
        modifier = modifier,
    )
}

@Composable
fun PluginMarketScreen(
    installedPlugins: List<InstalledPluginRecord>,
    enabledPluginIds: Set<String>,
    syncingPluginId: String?,
    syncStatusMessage: String?,
    pendingWebSession: WebSessionRequest?,
    bundledCatalog: List<BundledPluginCatalogEntry>,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onSyncPlugin: (String) -> Unit,
    onAddBundledPlugin: (String) -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val detailPlugin = detailPluginId?.let { id -> installedPlugins.firstOrNull { it.pluginId == id } }
    if (detailPlugin != null) {
        PluginDetailScreen(
            plugin = detailPlugin,
            isEnabled = detailPlugin.pluginId in enabledPluginIds,
            isSyncing = syncingPluginId == detailPlugin.pluginId,
            pendingWebSession = pendingWebSession,
            onBack = { detailPluginId = null },
            onSetEnabled = { onSetPluginEnabled(detailPlugin.pluginId, it) },
            onSync = { onSyncPlugin(detailPlugin.pluginId) },
            onCompleteWebSession = onCompleteWebSession,
            onCancelWebSession = onCancelWebSession,
            modifier = modifier,
        )
        return
    }
    val enabledCount = installedPlugins.count { it.pluginId in enabledPluginIds }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PluginCountHeader(
                    enabledCount = enabledCount,
                    totalCount = installedPlugins.size,
                    canAdd = bundledCatalog.any { entry ->
                        installedPlugins.none { it.pluginId == entry.pluginId }
                    },
                    onAddClick = { showAddSheet = true },
                )
            }

            if (syncStatusMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(
                            text = syncStatusMessage,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (installedPlugins.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "还没有任何插件",
                        subtitle = "点击右上角的「添加」按钮，从内置目录中加入插件。添加后还需要在卡片上手动开启。",
                    )
                }
            } else {
                items(installedPlugins, key = { it.pluginId }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        isEnabled = plugin.pluginId in enabledPluginIds,
                        isSyncing = syncingPluginId == plugin.pluginId,
                        onSetEnabled = { onSetPluginEnabled(plugin.pluginId, it) },
                        onSync = { onSyncPlugin(plugin.pluginId) },
                        onOpenDetail = { detailPluginId = plugin.pluginId },
                    )
                }
            }
        }

        pendingWebSession?.let { request ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xD9000000))
                    .padding(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    PluginWebSessionScreen(
                        request = request,
                        onFinish = onCompleteWebSession,
                        onCancel = onCancelWebSession,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddBundledPluginDialog(
            entries = bundledCatalog,
            installedIds = installedPlugins.map { it.pluginId }.toSet(),
            onDismiss = { showAddSheet = false },
            onAdd = { id ->
                onAddBundledPlugin(id)
                showAddSheet = false
            },
        )
    }
}

@Composable
private fun AddBundledPluginDialog(
    entries: List<BundledPluginCatalogEntry>,
    installedIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加插件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        "暂无可添加的内置插件。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    entries.forEach { entry ->
                        val installed = entry.pluginId in installedIds
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        entry.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                if (installed) {
                                    Text(
                                        "已添加",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Button(onClick = { onAdd(entry.pluginId) }) {
                                        Text("添加")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun PluginCountHeader(
    enabledCount: Int,
    totalCount: Int,
    canAdd: Boolean,
    onAddClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "插件",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$enabledCount / $totalCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "已启用 / 已安装",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onAddClick,
                enabled = canAdd,
            ) {
                Text("添加")
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: InstalledPluginRecord,
    isEnabled: Boolean,
    isSyncing: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onSetEnabled,
                )
            }
            if (isEnabled) {
                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                ) {
                    Text(if (isSyncing) "同步中..." else "同步课表")
                }
            }
        }
    }
}

@Composable
private fun EnabledBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "已启用",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PluginDetailScreen(
    plugin: InstalledPluginRecord,
    isEnabled: Boolean,
    isSyncing: Boolean,
    pendingWebSession: WebSessionRequest?,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSync: () -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Text("← 返回")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "插件详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "v${plugin.version}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isEnabled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    EnabledBadge()
                                }
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = onSetEnabled,
                            )
                        }
                        if (isEnabled) {
                            Button(
                                onClick = onSync,
                                enabled = !isSyncing,
                            ) {
                                Text(if (isSyncing) "同步中..." else "同步课表")
                            }
                        }
                    }
                }
            }

            item {
                DetailSection("基本信息") {
                    DetailRow("发布者", plugin.publisher)
                    DetailRow("类型", if (plugin.isBundled) "内置插件" else "外部插件")
                    DetailRow("插件 ID", plugin.pluginId)
                }
            }

            item {
                DetailSection("权限") {
                    Text(
                        text = plugin.declaredPermissions.joinToString { it.name }
                            .ifBlank { "无" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                DetailSection("站点白名单") {
                    if (plugin.allowedHosts.isEmpty()) {
                        Text("无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            plugin.allowedHosts.forEach { host ->
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        pendingWebSession?.let { request ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xD9000000))
                    .padding(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    PluginWebSessionScreen(
                        request = request,
                        onFinish = onCompleteWebSession,
                        onCancel = onCancelWebSession,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
