package com.x500x.cursimple.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.UpdateBadgeDot
import com.x500x.cursimple.app.extensionEntryItems
import com.x500x.cursimple.core.data.navigation.BottomBarSize
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.appItemMotion

sealed interface BarTab {
    data class Destination(val destination: NavDestination) : BarTab
    data object Me : BarTab
}

/**
 * Bar destinations select their own tab; everything else (the me page and pages listed on it)
 * selects "me". [current] is null on the me page.
 */
fun selectedBarTab(items: List<NavDestination>, current: NavDestination?): BarTab =
    current?.takeIf { it in items }?.let(BarTab::Destination) ?: BarTab.Me

/** Bar metrics per size; [BottomBarSize.Large] matches the stock Material bar. */
private data class BarMetrics(val height: Dp, val icon: Dp, val indicatorWidth: Dp, val indicatorHeight: Dp, val label: TextUnit)

private fun BottomBarSize.metrics(): BarMetrics = when (this) {
    BottomBarSize.Small -> BarMetrics(56.dp, 20.dp, 48.dp, 26.dp, 11.sp)
    BottomBarSize.Medium -> BarMetrics(64.dp, 22.dp, 56.dp, 28.dp, 12.sp)
    BottomBarSize.Large -> BarMetrics(80.dp, 24.dp, 64.dp, 32.dp, 12.sp)
}

@Composable
fun AppBottomBar(
    items: List<NavDestination>,
    selected: BarTab,
    meBadge: Boolean,
    onSelect: (BarTab) -> Unit,
    modifier: Modifier = Modifier,
    size: BottomBarSize = BottomBarSize.Medium,
) {
    val metrics = size.metrics()
    // Stock NavigationBar has a fixed 80dp height, so the bar is drawn here to honour the size setting.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.testTag("bottom-bar"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(metrics.height)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { destination ->
                val tab = BarTab.Destination(destination)
                BarItem(
                    selected = selected == tab,
                    label = stringResource(destination.labelRes),
                    metrics = metrics,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).testTag("bar-tab-${destination.id}"),
                ) { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(metrics.icon)) }
            }
            BarItem(
                selected = selected == BarTab.Me,
                label = stringResource(R.string.nav_me),
                metrics = metrics,
                onClick = { onSelect(BarTab.Me) },
                modifier = Modifier.weight(1f).testTag("bar-tab-me"),
            ) {
                BadgedBox(badge = { if (meBadge) Badge(modifier = Modifier.testTag("me-update-dot")) }) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(metrics.icon))
                }
            }
        }
    }
}

@Composable
private fun BarItem(
    selected: Boolean,
    label: String,
    metrics: BarMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val indicator by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "bar-indicator",
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "bar-icon",
    )
    val indicatorWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) metrics.indicatorWidth else (metrics.indicatorWidth * 0.72f),
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "bar-indicator-width",
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "bar-icon-scale",
    )
    // The rounded cell is both the press surface and the click target.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(indicatorWidth, metrics.indicatorHeight)
                .clip(RoundedCornerShape(50))
                .background(indicator),
        ) {
            CompositionLocalProvider(LocalContentColor provides content) {
                Box(modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                    icon()
                }
            }
        }
        Text(
            text = label,
            fontSize = metrics.label,
            lineHeight = metrics.label * 1.25f,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shortcut actions shared with the sidebar; ids are the public layout vocabulary. */
private data class MeShortcut(val id: String, val icon: ImageVector, val labelRes: Int)

private val meShortcuts = listOf(
    MeShortcut("action.theme", Icons.Rounded.Style, R.string.main_drawer_theme_shortcut),
    MeShortcut("action.background", Icons.Rounded.Wallpaper, R.string.main_drawer_background_shortcut),
    MeShortcut("action.overrides", Icons.Rounded.SwapHoriz, R.string.main_drawer_overrides_shortcut),
    MeShortcut("action.update", Icons.Rounded.SystemUpdate, R.string.main_drawer_update_shortcut),
)

/** "Me" tab: app summary, shortcuts and every destination that is not on the bar. */
@Composable
fun MeScreen(
    overflow: List<NavDestination>,
    weekLabel: String,
    termLabel: String,
    versionName: String,
    updateBadge: Boolean,
    badgeFor: (NavDestination) -> Boolean,
    onOpen: (NavDestination) -> Unit,
    onShortcut: (String) -> Unit,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("me-screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            MeSummaryCard(weekLabel, termLabel, versionName, updateBadge, onOpenUpdate)
        }
        item(key = "shortcuts") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                meShortcuts.forEach { shortcut ->
                    val label = stringResource(shortcut.labelRes)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box {
                            AppCircleIconButton(
                                onClick = { onShortcut(shortcut.id) },
                                modifier = Modifier.semantics { contentDescription = label },
                            ) {
                                Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                            if (updateBadge && shortcut.id == "action.update") {
                                UpdateBadgeDot(modifier = Modifier.align(Alignment.TopEnd))
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        items(overflow, key = { it.id }) { destination ->
            Box(appItemMotion()) {
                Surface(
                    onClick = { onOpen(destination) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("me-item-${destination.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (badgeFor(destination)) {
                            UpdateBadgeDot()
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeSummaryCard(
    weekLabel: String,
    termLabel: String,
    versionName: String,
    updateBadge: Boolean,
    onOpenUpdate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The version chip shares the name row so week and term keep the full width; it wraps when narrow.
                FlowRow(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.main_app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Surface(
                        onClick = onOpenUpdate,
                        shape = RoundedCornerShape(50),
                        color = if (updateBadge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.me_version, versionName),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (updateBadge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (weekLabel.isNotBlank()) {
                    Text(
                        text = weekLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (termLabel.isNotBlank()) {
                    Text(
                        text = termLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** Components tab: enabled components shown in navigation, plus the component market. */
@Composable
fun ComponentsHubScreen(
    entries: List<Pair<String, String>>,
    current: String?,
    onOpen: (String) -> Unit,
    onOpenMarket: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("components-hub"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entries.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.85f), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Widgets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.components_hub_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.components_hub_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = onOpenMarket,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("components-hub-market"),
                            ) {
                                Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.components_hub_market), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            extensionEntryItems(entries, current, onOpen)
            item(key = "market") {
                AppOutlinedButton(
                    onClick = onOpenMarket,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("components-hub-market"),
                ) {
                    Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.components_hub_market), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
