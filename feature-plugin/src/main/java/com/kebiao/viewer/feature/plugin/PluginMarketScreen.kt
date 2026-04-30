package com.kebiao.viewer.feature.plugin

import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PluginMarketRoute(
    viewModel: PluginMarketViewModel,
    activePluginId: String,
    onSelectInstalledPlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PluginMarketScreen(
        state = state,
        activePluginId = activePluginId,
        onRemoteIndexUrlChange = viewModel::onRemoteIndexUrlChange,
        onLoadRemoteMarket = viewModel::loadRemoteMarket,
        onPreviewRemotePackage = viewModel::previewRemotePackage,
        onPreviewLocalPackage = viewModel::previewLocalPackage,
        onConfirmInstall = viewModel::confirmInstall,
        onDismissInstallPreview = viewModel::dismissInstallPreview,
        onSelectInstalledPlugin = onSelectInstalledPlugin,
        onRemovePlugin = viewModel::removePlugin,
        modifier = modifier,
    )
}

@Composable
fun PluginMarketScreen(
    state: PluginMarketUiState,
    activePluginId: String,
    onRemoteIndexUrlChange: (String) -> Unit,
    onLoadRemoteMarket: () -> Unit,
    onPreviewRemotePackage: (com.kebiao.viewer.core.plugin.market.MarketPluginEntry) -> Unit,
    onPreviewLocalPackage: (ByteArray) -> Unit,
    onConfirmInstall: () -> Unit,
    onDismissInstallPreview: () -> Unit,
    onSelectInstalledPlugin: (String) -> Unit,
    onRemovePlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val localPackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                onPreviewLocalPackage(input.readBytes())
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                        text = "管理学校插件、远程市场和本地导入包。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("远程市场", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.remoteIndexUrl,
                        onValueChange = onRemoteIndexUrlChange,
                        label = { Text("远程市场索引 URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onLoadRemoteMarket,
                            enabled = !state.isLoading && URLUtil.isValidUrl(state.remoteIndexUrl),
                        ) {
                            Text("加载市场")
                        }
                        Button(
                            onClick = { localPackageLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isLoading,
                        ) {
                            Text("导入本地插件")
                        }
                    }
                    if (!state.statusMessage.isNullOrBlank()) {
                        Text(
                            text = state.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "已安装插件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.installedPlugins.isEmpty()) {
            item {
                EmptyStateCard("当前没有已安装插件", "可以从远程市场预检安装，或者直接导入本地插件包。")
            }
        } else {
            items(state.installedPlugins, key = { it.pluginId }) { plugin ->
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
                        Text("来源：${plugin.source.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "权限：${plugin.declaredPermissions.joinToString { it.name }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (plugin.pluginId == activePluginId) {
                                Text(
                                    "当前使用中",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Button(onClick = { onSelectInstalledPlugin(plugin.pluginId) }) {
                                    Text("设为当前插件")
                                }
                            }
                            TextButton(onClick = { onRemovePlugin(plugin.pluginId) }) {
                                Text("移除插件")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "远程市场插件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.marketPlugins.isEmpty()) {
            item {
                EmptyStateCard("还没有加载到市场条目", "填写一个有效的市场索引地址后，就能在这里看到可安装插件。")
            }
        } else {
            items(state.marketPlugins, key = { it.pluginId }) { plugin ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${plugin.name} (${plugin.version})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(plugin.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("发布者：${plugin.publisher}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onPreviewRemotePackage(plugin) }) {
                            Text("下载并预检")
                        }
                    }
                }
            }
        }
    }

    state.installPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissInstallPreview,
            confirmButton = {
                Button(
                    onClick = onConfirmInstall,
                    enabled = preview.checksumVerified && preview.signatureVerified,
                ) {
                    Text("确认安装")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissInstallPreview) {
                    Text("取消")
                }
            },
            title = {
                Text("安装插件：${preview.manifest.name}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("插件 ID：${preview.manifest.pluginId}")
                    Text("发布者：${preview.manifest.publisher}")
                    Text("版本：${preview.manifest.version}")
                    Text("来源：${preview.source.name}")
                    Text("权限：${preview.manifest.declaredPermissions.joinToString { it.name }}")
                    Text("站点白名单：${preview.manifest.allowedHosts.joinToString()}")
                    Text("摘要校验：${if (preview.checksumVerified) "通过" else "失败"}")
                    Text("签名校验：${if (preview.signatureVerified) "通过" else "失败"}")
                }
            },
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
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
