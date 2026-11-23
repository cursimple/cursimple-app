package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.feature.plugin.ui.AppInfoBlock
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import androidx.compose.material.icons.rounded.Download

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
    installedCount: Int = 0,
    marketCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val haptics = rememberAppHaptics()
                MarketCatalogTab.entries.forEach { item ->
                    val baseLabel = stringResource(
                        if (item == MarketCatalogTab.Installed) R.string.plugin_catalog_installed
                        else R.string.plugin_catalog_market,
                    )
                    val count = if (item == MarketCatalogTab.Installed) installedCount else marketCount
                    val picked = item == tab
                    val tabBgColor by animateColorAsState(
                        targetValue = if (picked) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "CatalogTabBg",
                    )
                    val tabContentColor by animateColorAsState(
                        targetValue = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "CatalogTabContent",
                    )
                    Surface(
                        onClick = { haptics.click(); onSelectTab(item) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            .testTag("catalog-${item.name.lowercase()}")
                            .semantics { selected = picked; role = Role.Tab },
                        shape = RoundedCornerShape(50),
                        color = tabBgColor,
                        contentColor = tabContentColor,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = baseLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            if (count > 0) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (picked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (picked) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        AppSearchField(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.plugin_catalog_search_hint),
            clearLabel = stringResource(R.string.plugin_catalog_clear_search),
            label = stringResource(
                if (extensionMode) R.string.extension_search_label else R.string.plugin_catalog_search_label,
            ),
            fieldModifier = Modifier.testTag("catalog-search"),
            clearModifier = Modifier.testTag("catalog-clear"),
            cornerRadius = 26.dp,
            minHeight = 52.dp,
            borderWidth = 1.5.dp,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AppOutlinedButton(
                onClick = onImport,
                enabled = !isLoading,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(19.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.plugin_market_action_import_zip),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
            AppCircleIconButton(
                onClick = onRefresh,
                enabled = !isLoading && !isRefreshingReleases,
                modifier = Modifier.testTag("catalog-refresh"),
                size = 40.dp,
                containerColor = Color.Transparent,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.plugin_market_action_refresh), modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = isLoading || isRefreshingReleases,
            enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp)),
            )
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
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MarketStatusBadge(label: String, attention: Boolean = false, neutral: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        // Neutral chips carry plain facts (install origin) beside the version pill.
        color = when {
            attention -> scheme.tertiaryContainer
            neutral -> scheme.surfaceVariant
            else -> scheme.secondaryContainer
        },
        contentColor = when {
            attention -> scheme.tertiary
            neutral -> scheme.onSurfaceVariant
            else -> scheme.onSecondaryContainer
        },
    ) {
        Box(
            modifier = Modifier.height(24.dp).padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MarketItemSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
internal fun MarketDetailTopBar(title: String, onBack: () -> Unit) {
    // The host already shows this title and back action in its own bar.
    if (LocalPluginHostTopBar.current) return
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 44.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, letterSpacing = 0.5.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("detail-back"),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.plugin_action_back),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun MarketDetailHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.5.sp,
            lineHeight = 20.sp,
            lineBreak = LineBreak.Heading,
        ),
        fontWeight = FontWeight.Bold,
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
    showDivider: Boolean = true,
    dense: Boolean = false,
    monospace: Boolean = false,
) {
    AppInfoBlock(
        label = label,
        value = value,
        singleLine = singleLine,
        valueModifier = valueModifier,
        showDivider = showDivider,
        dense = dense,
        monospace = monospace,
    )
}

/** Collapsible technical section header: icon chip, title, summary and a chevron. */
@Composable
internal fun MarketDetailsToggle(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (expanded) R.string.plugin_detail_collapse_technical else R.string.plugin_detail_expand_technical,
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.plugin_detail_technical_summary),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
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
    AppAlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("market-download"),
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onCancel, modifier = Modifier.testTag("download-cancel")) {
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
