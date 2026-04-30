package com.kebiao.viewer.feature.plugin

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest

@Composable
fun PluginMarketRoute(
    installedPlugins: List<InstalledPluginRecord>,
    activePluginId: String,
    syncStatusMessage: String?,
    isSyncing: Boolean,
    pendingWebSession: WebSessionRequest?,
    onSelectInstalledPlugin: (String) -> Unit,
    onSyncInstalledPlugin: () -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PluginMarketScreen(
        installedPlugins = installedPlugins,
        activePluginId = activePluginId,
        syncStatusMessage = syncStatusMessage,
        isSyncing = isSyncing,
        pendingWebSession = pendingWebSession,
        onSelectInstalledPlugin = onSelectInstalledPlugin,
        onSyncInstalledPlugin = onSyncInstalledPlugin,
        onCompleteWebSession = onCompleteWebSession,
        onCancelWebSession = onCancelWebSession,
        modifier = modifier,
    )
}

@Composable
fun PluginMarketScreen(
    installedPlugins: List<InstalledPluginRecord>,
    activePluginId: String,
    syncStatusMessage: String?,
    isSyncing: Boolean,
    pendingWebSession: WebSessionRequest?,
    onSelectInstalledPlugin: (String) -> Unit,
    onSyncInstalledPlugin: () -> Unit,
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "插件",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = syncStatusMessage ?: "这里只保留长江大学插件。点击当前插件卡片里的“同步课表”后，会直接进入统一认证登录取数流程。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "当前可用插件",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (installedPlugins.isEmpty()) {
                item {
                    EmptyStateCard("当前没有可用插件", "应用启动时会自动装入长江大学教务插件；如果这里还是空的，说明内置安装过程出了问题。")
                }
            } else {
                items(installedPlugins, key = { it.pluginId }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        isActive = plugin.pluginId == activePluginId,
                        isSyncing = isSyncing && plugin.pluginId == activePluginId,
                        onSelectInstalledPlugin = onSelectInstalledPlugin,
                        onSyncInstalledPlugin = onSyncInstalledPlugin,
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
}

@Composable
private fun PluginCard(
    plugin: InstalledPluginRecord,
    isActive: Boolean,
    isSyncing: Boolean,
    onSelectInstalledPlugin: (String) -> Unit,
    onSyncInstalledPlugin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${plugin.name} (${plugin.version})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("发布者：${plugin.publisher}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("类型：${if (plugin.isBundled) "内置插件" else "外部插件"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "权限：${plugin.declaredPermissions.joinToString { it.name }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "站点白名单：${plugin.allowedHosts.joinToString()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isActive) {
                Text(
                    "当前插件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = onSyncInstalledPlugin,
                    enabled = !isSyncing,
                ) {
                    Text(if (isSyncing) "同步中..." else "同步课表")
                }
            } else {
                TextButton(onClick = { onSelectInstalledPlugin(plugin.pluginId) }) {
                    Text("设为当前插件")
                }
            }
        }
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
