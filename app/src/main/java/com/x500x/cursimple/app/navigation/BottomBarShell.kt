package com.x500x.cursimple.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.UpdateBadgeDot
import com.x500x.cursimple.app.extensionEntryItems
import com.x500x.cursimple.core.data.navigation.NavDestination
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton

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

@Composable
fun AppBottomBar(
    items: List<NavDestination>,
    selected: BarTab,
    meBadge: Boolean,
    onSelect: (BarTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("bottom-bar"),
    ) {
        items.forEach { destination ->
            val tab = BarTab.Destination(destination)
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { BarLabel(stringResource(destination.labelRes)) },
                colors = colors,
                modifier = Modifier.testTag("bar-tab-${destination.id}"),
            )
        }
        NavigationBarItem(
            selected = selected == BarTab.Me,
            onClick = { onSelect(BarTab.Me) },
            icon = {
                BadgedBox(badge = { if (meBadge) Badge(modifier = Modifier.testTag("me-update-dot")) }) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                }
            },
            label = { BarLabel(stringResource(R.string.nav_me)) },
            colors = colors,
            modifier = Modifier.testTag("bar-tab-me"),
        )
    }
}

@Composable
private fun BarLabel(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            MeSummaryCard(termLabel, versionName, updateBadge, onOpenUpdate)
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
            Surface(
                onClick = { onOpen(destination) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("me-item-${destination.id}"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp))
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeSummaryCard(termLabel: String, versionName: String, updateBadge: Boolean, onOpenUpdate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
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
                Text(
                    text = stringResource(R.string.main_app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = termLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Surface(
                onClick = onOpenUpdate,
                shape = RoundedCornerShape(50),
                color = if (updateBadge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.components_hub_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
                )
            }
        }
        extensionEntryItems(entries, current, onOpen)
        item(key = "market") {
            AppOutlinedButton(
                onClick = onOpenMarket,
                modifier = Modifier.padding(top = 8.dp).testTag("components-hub-market"),
            ) {
                Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.components_hub_market))
            }
        }
    }
}
