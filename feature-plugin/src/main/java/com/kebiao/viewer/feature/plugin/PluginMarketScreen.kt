package com.kebiao.viewer.feature.plugin

import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PluginMarketRoute(
    viewModel: PluginMarketViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PluginMarketScreen(
        state = state,
        onRemoteIndexUrlChange = viewModel::onRemoteIndexUrlChange,
        onLoadRemoteMarket = viewModel::loadRemoteMarket,
        onPreviewRemotePackage = viewModel::previewRemotePackage,
        onPreviewLocalPackage = viewModel::previewLocalPackage,
        onConfirmInstall = viewModel::confirmInstall,
        onDismissInstallPreview = viewModel::dismissInstallPreview,
        onRemovePlugin = viewModel::removePlugin,
        modifier = modifier,
    )
}

@Composable
fun PluginMarketScreen(
    state: PluginMarketUiState,
    onRemoteIndexUrlChange: (String) -> Unit,
    onLoadRemoteMarket: () -> Unit,
    onPreviewRemotePackage: (com.kebiao.viewer.core.plugin.market.MarketPluginEntry) -> Unit,
    onPreviewLocalPackage: (ByteArray) -> Unit,
    onConfirmInstall: () -> Unit,
    onDismissInstallPreview: () -> Unit,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("插件市场", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(
                value = state.remoteIndexUrl,
                onValueChange = onRemoteIndexUrlChange,
                label = { Text("远程市场索引 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onLoadRemoteMarket,
                    enabled = !state.isLoading && URLUtil.isValidUrl(state.remoteIndexUrl),
                ) {
                    Text("加载远程市场")
                }
                Button(
                    onClick = { localPackageLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isLoading,
                ) {
                    Text("导入本地插件")
                }
            }
        }
        item {
            Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Text("已安装插件", style = MaterialTheme.typography.titleMedium)
        }
        items(state.installedPlugins, key = { it.pluginId }) { plugin ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${plugin.name} (${plugin.version})", style = MaterialTheme.typography.titleSmall)
                    Text("发布者：${plugin.publisher}")
                    Text("来源：${plugin.source.name}")
                    Text("权限：${plugin.declaredPermissions.joinToString { it.name }}")
                    TextButton(onClick = { onRemovePlugin(plugin.pluginId) }) {
                        Text("移除")
                    }
                }
            }
        }
        item {
            Text("远程市场插件", style = MaterialTheme.typography.titleMedium)
        }
        items(state.marketPlugins, key = { it.pluginId }) { plugin ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${plugin.name} (${plugin.version})", style = MaterialTheme.typography.titleSmall)
                    Text(plugin.description)
                    Text("发布者：${plugin.publisher}")
                    TextButton(onClick = { onPreviewRemotePackage(plugin) }) {
                        Text("下载并预检")
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
