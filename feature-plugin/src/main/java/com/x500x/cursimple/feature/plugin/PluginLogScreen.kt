package com.x500x.cursimple.feature.plugin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.plugin.logging.PluginLogBuffer
import com.x500x.cursimple.core.plugin.logging.PluginLogEntry
import com.x500x.cursimple.core.plugin.logging.PluginLogLevel
import com.x500x.cursimple.core.plugin.logging.PluginLogSource
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buffer = remember { PluginLogBuffer.instance }
    val entries by buffer.snapshots.collectAsStateWithLifecycle()
    var levelFilter by rememberSaveable { mutableStateOf<PluginLogLevel?>(null) }
    var sourceFilter by rememberSaveable { mutableStateOf<PluginLogSource?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pinnedTraceId by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val filtered = remember(entries, levelFilter, sourceFilter, query, pinnedTraceId) {
        val q = query.trim().lowercase()
        entries.asReversed().filter { entry ->
            (levelFilter == null || entry.level == levelFilter) &&
                (sourceFilter == null || entry.source == sourceFilter) &&
                (pinnedTraceId == null || entry.traceId == pinnedTraceId) &&
                (q.isEmpty() || entry.matches(q))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("插件日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(filtered.joinToString("\n") { it.toJsonLine() }))
                        Toast.makeText(context, "已复制 ${filtered.size} 条到剪贴板", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制全部")
                    }
                    IconButton(onClick = {
                        buffer.clear()
                        Toast.makeText(context, "已清空日志", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "清空")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch { listState.scrollToItem(0) }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "回到顶部")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterBar(
                levelFilter = levelFilter,
                onLevelFilter = { levelFilter = it },
                sourceFilter = sourceFilter,
                onSourceFilter = { sourceFilter = it },
                query = query,
                onQueryChange = { query = it },
                pinnedTraceId = pinnedTraceId,
                onClearPinnedTrace = { pinnedTraceId = null },
                totalCount = entries.size,
                shownCount = filtered.size,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (entries.isEmpty()) "暂无日志，操作插件后会自动产生" else "当前筛选条件下没有匹配日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.sequence }) { entry ->
                        LogEntryRow(
                            entry = entry,
                            onPinTrace = { entry.traceId?.let { pinnedTraceId = it } },
                            onCopy = {
                                clipboard.setText(AnnotatedString(entry.toJsonLine()))
                                Toast.makeText(context, "已复制该条日志", Toast.LENGTH_SHORT).show()
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    levelFilter: PluginLogLevel?,
    onLevelFilter: (PluginLogLevel?) -> Unit,
    sourceFilter: PluginLogSource?,
    onSourceFilter: (PluginLogSource?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    pinnedTraceId: String?,
    onClearPinnedTrace: () -> Unit,
    totalCount: Int,
    shownCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索事件名/插件 ID/字段值…") },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = levelFilter == null,
                onClick = { onLevelFilter(null) },
                label = { Text("全部") },
            )
            PluginLogLevel.values().forEach { lv ->
                FilterChip(
                    selected = levelFilter == lv,
                    onClick = { onLevelFilter(lv) },
                    label = { Text(lv.short) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = sourceFilter == null,
                onClick = { onSourceFilter(null) },
                label = { Text("全部来源") },
            )
            PluginLogSource.values().forEach { src ->
                FilterChip(
                    selected = sourceFilter == src,
                    onClick = { onSourceFilter(src) },
                    label = { Text(src.token) },
                )
            }
        }
        if (pinnedTraceId != null) {
            AssistChip(
                onClick = onClearPinnedTrace,
                label = { Text("trace=${pinnedTraceId.takeLast(8)} × 取消") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        Text(
            text = "$shownCount / $totalCount 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogEntryRow(
    entry: PluginLogEntry,
    onPinTrace: () -> Unit,
    onCopy: () -> Unit,
) {
    var expanded by remember(entry.sequence) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelPill(entry.level)
            Spacer(modifier = Modifier.width(6.dp))
            SourcePill(entry.source)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.event,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.formatTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            entry.traceId?.let { trace ->
                AssistChip(
                    onClick = onPinTrace,
                    label = { Text(trace.takeLast(8), fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.height(24.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            entry.pluginId?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.fields.isNotEmpty()) {
            Text(
                text = entry.fieldsSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            entry.errorStack?.let { stack ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = stack,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制本条 JSON")
                }
            }
        }
    }
}

@Composable
private fun LevelPill(level: PluginLogLevel) {
    val color = when (level) {
        PluginLogLevel.DEBUG -> MaterialTheme.colorScheme.outline
        PluginLogLevel.INFO -> MaterialTheme.colorScheme.primary
        PluginLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        PluginLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = level.short,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SourcePill(source: PluginLogSource) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = source.token,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun PluginLogEntry.matches(query: String): Boolean {
    if (event.lowercase().contains(query)) return true
    if (pluginId?.lowercase()?.contains(query) == true) return true
    if (traceId?.lowercase()?.contains(query) == true) return true
    if (sessionId?.lowercase()?.contains(query) == true) return true
    return fields.any { (k, v) -> k.lowercase().contains(query) || v.lowercase().contains(query) }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private fun PluginLogEntry.formatTime(): String = timeFormatter.format(Instant.ofEpochMilli(timestampMs))

private fun PluginLogEntry.fieldsSummary(): String =
    fields.entries.joinToString(" ") { (k, v) -> "$k=$v" }

private fun PluginLogEntry.toJsonLine(): String {
    val builder = StringBuilder()
    builder.append('{')
    builder.append("\"ts\":").append(timestampMs)
    builder.append(",\"lv\":\"").append(level.short).append('"')
    builder.append(",\"src\":\"").append(source.token).append('"')
    builder.append(",\"ev\":\"").append(event.escapeJson()).append('"')
    traceId?.let { builder.append(",\"tid\":\"").append(it.escapeJson()).append('"') }
    pluginId?.let { builder.append(",\"plg\":\"").append(it.escapeJson()).append('"') }
    sessionId?.let { builder.append(",\"sid\":\"").append(it.escapeJson()).append('"') }
    if (fields.isNotEmpty()) {
        builder.append(",\"f\":{")
        fields.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) builder.append(',')
            builder.append('"').append(k.escapeJson()).append("\":\"").append(v.escapeJson()).append('"')
        }
        builder.append('}')
    }
    errorStack?.let { builder.append(",\"stack\":\"").append(it.escapeJson()).append('"') }
    builder.append('}')
    return builder.toString()
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
