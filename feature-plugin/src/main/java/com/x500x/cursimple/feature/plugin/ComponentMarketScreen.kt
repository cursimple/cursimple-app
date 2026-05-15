package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.plugin.component.InstalledPluginComponentRecord
import com.x500x.cursimple.core.plugin.component.PluginComponentStatus
import com.x500x.cursimple.core.plugin.component.PluginComponentType

@Composable
fun ComponentMarketScreen(
    uiState: ComponentMarketUiState,
    onPickLocalPackage: () -> Unit,
    onRemotePackageUrlChange: (String) -> Unit,
    onInstallRemoteUrl: () -> Unit,
    onInstallRemoteEntry: (ComponentMarketEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ComponentHeader(
                    installedCount = uiState.installedComponents.size,
                    onPickLocalPackage = onPickLocalPackage,
                    isLoading = uiState.isLoading,
                )
            }

            uiState.statusMessage?.let { message ->
                item {
                    StatusCard(message = message)
                }
            }

            item {
                RemoteComponentUrlCard(
                    url = uiState.remotePackageUrl,
                    isLoading = uiState.isLoading,
                    onUrlChange = onRemotePackageUrlChange,
                    onInstall = onInstallRemoteUrl,
                )
            }

            item {
                SectionTitle("已安装组件")
            }

            if (uiState.installedComponents.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "还没有组件",
                        subtitle = "插件声明必需组件时，会在这里查看安装状态。组件不会被静默安装。",
                    )
                }
            } else {
                items(uiState.installedComponents, key = { it.id }) { component ->
                    InstalledComponentCard(component = component)
                }
            }

            item {
                SectionTitle("远程组件")
            }

            if (uiState.knownComponents.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "暂无远程组件索引",
                        subtitle = "当前阶段支持本地组件包导入，也可输入组件包 URL 通过镜像下载后安装。",
                    )
                }
            } else {
                items(uiState.knownComponents, key = { it.id }) { entry ->
                    KnownComponentCard(
                        entry = entry,
                        isLoading = uiState.isLoading,
                        onInstall = { onInstallRemoteEntry(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentHeader(
    installedCount: Int,
    isLoading: Boolean,
    onPickLocalPackage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "组件",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$installedCount",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "已安装",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onPickLocalPackage,
                enabled = !isLoading,
            ) {
                Text("导入组件包")
            }
        }
    }
}

@Composable
private fun RemoteComponentUrlCard(
    url: String,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "组件包 URL",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("https://...") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onInstall,
                    enabled = !isLoading,
                ) {
                    Text(if (isLoading) "处理中" else "安装")
                }
            }
        }
    }
}

@Composable
private fun InstalledComponentCard(component: InstalledPluginComponentRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = component.id,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${componentTypeLabel(component.type)} · v${component.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ComponentStatusBadge(status = component.status)
            }
            DetailLine("ABI", component.abi ?: "any")
            DetailLine("来源", component.source.name.lowercase())
            DetailLine("SHA-256", component.sha256)
            component.message?.takeIf { it.isNotBlank() }?.let {
                DetailLine("状态说明", it)
            }
        }
    }
}

@Composable
private fun KnownComponentCard(
    entry: ComponentMarketEntry,
    isLoading: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${componentTypeLabel(entry.type)} · v${entry.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onInstall,
                    enabled = !isLoading && !entry.downloadUrl.isNullOrBlank(),
                ) {
                    Text("下载")
                }
            }
            if (entry.description.isNotBlank()) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.abi?.let { DetailLine("ABI", it) }
        }
    }
}

@Composable
private fun ComponentStatusBadge(status: PluginComponentStatus) {
    val (label, color, contentColor) = when (status) {
        PluginComponentStatus.Installed -> Triple(
            "已安装",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PluginComponentStatus.Failed,
        PluginComponentStatus.Incompatible -> Triple(
            statusLabel(status),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Triple(
            statusLabel(status),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatusCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
        shape = RoundedCornerShape(16.dp),
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

private fun componentTypeLabel(type: PluginComponentType): String = when (type) {
    PluginComponentType.EngineChromium -> "Chromium 引擎"
    PluginComponentType.OpenCvNative -> "OpenCV Native"
    PluginComponentType.OnnxRuntime -> "ONNX Runtime"
    PluginComponentType.OnnxModel -> "ONNX Model"
    PluginComponentType.GenericAsset -> "通用资产"
}

private fun statusLabel(status: PluginComponentStatus): String = when (status) {
    PluginComponentStatus.NotInstalled -> "未安装"
    PluginComponentStatus.NeedsConsent -> "待确认"
    PluginComponentStatus.Downloading -> "下载中"
    PluginComponentStatus.Installed -> "已安装"
    PluginComponentStatus.Failed -> "失败"
    PluginComponentStatus.Incompatible -> "不兼容"
}
