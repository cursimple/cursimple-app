package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppInfoBlock
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarketCatalogControls(
    tab: MarketCatalogTab,
    onSelectTab: (MarketCatalogTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    extensionMode: Boolean,
    isLoading: Boolean,
    isRefreshingReleases: Boolean,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MarketCatalogTab.entries.forEach { item ->
                    val label = stringResource(
                        if (item == MarketCatalogTab.Installed) R.string.plugin_catalog_installed
                        else R.string.plugin_catalog_market,
                    )
                    val picked = item == tab
                    Surface(
                        onClick = { onSelectTab(item) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            .testTag("catalog-${item.name.lowercase()}")
                            .semantics { selected = picked; role = Role.Tab },
                        shape = RoundedCornerShape(12.dp),
                        color = if (picked) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = {
                Text(stringResource(if (extensionMode) R.string.extension_search_label else R.string.plugin_catalog_search_label))
            },
            placeholder = { Text(stringResource(R.string.plugin_catalog_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    AppOutlinedButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.padding(end = 8.dp).testTag("catalog-clear"),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.plugin_catalog_clear_search),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(onClick = onImport, enabled = !isLoading) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.plugin_market_action_import_zip))
            }
            AppOutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading && !isRefreshingReleases,
                modifier = Modifier.testTag("catalog-refresh"),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (isLoading) R.string.plugin_market_action_loading else R.string.plugin_market_action_refresh))
            }
        }
        if (isLoading || isRefreshingReleases) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun MarketSourceLabel(source: String) {
    Text(
        text = stringResource(
            R.string.plugin_catalog_source,
            if (isPublicMarketSource(source)) stringResource(R.string.plugin_catalog_public_source) else source,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MarketStatusBadge(label: String, attention: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (attention) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun MarketItemSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
internal fun MarketDetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppOutlinedButton(
            onClick = onBack,
            modifier = Modifier.testTag("detail-back"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.plugin_action_back))
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun MarketDetailHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun MarketInfoBlock(
    label: String,
    value: String,
    singleLine: Boolean = false,
    valueModifier: Modifier = Modifier,
) {
    AppInfoBlock(
        label = label,
        value = value,
        singleLine = singleLine,
        valueModifier = valueModifier,
    )
}

@Composable
internal fun MarketDetailsToggle(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppOutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (expanded) R.string.plugin_detail_collapse_technical else R.string.plugin_detail_expand_technical))
    }
}

/** Show the shared progress overlay only after measured download size reaches the threshold. */
@Composable
internal fun MarketDownloadOverlay(
    downloading: Boolean,
    progress: PluginDownloadProgress?,
    displayName: String?,
    extensionMode: Boolean,
    onCancel: () -> Unit,
) {
    if (!downloading || progress == null) return
    val total = progress.totalBytes?.takeIf { it > 0 }
    val downloaded = progress.downloadedBytes.coerceAtLeast(0)
    val threshold = 5L * 1024 * 1024
    val largePackage = if (total != null) total >= threshold else downloaded >= threshold
    if (!largePackage) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("market-download"),
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(if (extensionMode) R.string.extension_download_title else R.string.plugin_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                displayName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val downloadedLabel = Formatter.formatShortFileSize(context, downloaded)
                if (total != null) {
                    val fraction = (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.plugin_download_bytes_total, downloadedLabel, Formatter.formatShortFileSize(context, total)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("download-bytes"),
                    )
                    Text(stringResource(R.string.plugin_download_percent, (fraction * 100).toInt()), modifier = Modifier.testTag("download-percent"))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.plugin_download_bytes, downloadedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("download-bytes"),
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onCancel, modifier = Modifier.testTag("download-cancel")) {
                Text(stringResource(R.string.plugin_download_cancel))
            }
        },
    )
}

@Composable
internal fun MarketSourceErrorCard(failure: PluginMarketSourceError, isLoading: Boolean, onRetry: () -> Unit) {
    val context = LocalContext.current
    val sourceLabel = if (isPublicMarketSource(failure.source)) {
        stringResource(R.string.plugin_catalog_public_source)
    } else failure.source
    val message = when (failure.check) {
        MarketSourceCheck.NotFoundOrPrivate -> stringResource(R.string.plugin_catalog_source_login_required)
        MarketSourceCheck.NotFound -> stringResource(R.string.plugin_catalog_source_no_access)
        MarketSourceCheck.AccountExpired -> stringResource(R.string.plugin_catalog_source_account_expired)
        MarketSourceCheck.NothingPublished -> stringResource(R.string.plugin_catalog_source_no_release)
        else -> context.pluginMarketStatusText(PluginMarketStatus.MarketLoadFailed(failure.error))
    }
    MarketItemSurface(Modifier.testTag("source-error:${failure.source}")) {
        Text(
            stringResource(R.string.plugin_catalog_source_failed, sourceLabel),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppOutlinedButton(onClick = onRetry, enabled = !isLoading) {
            Text(stringResource(R.string.plugin_catalog_source_retry))
        }
    }
}
