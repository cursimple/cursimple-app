package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R

/**
 * 设置首页顶上的搜索框。设置项多、藏得深，记得名字的直接搜，省得一层层点进去找。
 */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    AppSearchField(
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.settings_search_hint),
        clearLabel = stringResource(R.string.settings_search_clear),
        modifier = modifier,
        onSearch = { focusManager.clearFocus() },
        onClear = { focusManager.clearFocus() },
    )
}

/** 「常用」里的一格：最常改的几项摆成方块，一眼能看到当前值，点一下就改。 */
internal data class SettingsQuickTileSpec(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
)

@Composable
internal fun SettingsQuickTile(
    tile: SettingsQuickTileSpec,
    modifier: Modifier = Modifier,
    valueMaxLines: Int = 1,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(18.dp),
        color = color,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (tile.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = if (tile.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 设置首页怎么排：一行一项的列表，或一行三格的网格。 */
internal enum class SettingsHomeLayout { List, Grid }

/**
 * 首页排法只影响这台手机上怎么看，不跟着备份走，存在本地就够了。
 */
internal object SettingsHomeLayoutStore {
    private const val PREFS = "settings_ui"
    private const val KEY = "home_layout"

    fun load(context: Context): SettingsHomeLayout =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { name -> SettingsHomeLayout.entries.firstOrNull { it.name == name } }
            ?: SettingsHomeLayout.List

    fun save(context: Context, layout: SettingsHomeLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, layout.name).apply()
    }
}

/** 搜索框右边那个按钮：列表和网格来回切。图标画的是点下去会变成的样子。 */
@Composable
internal fun SettingsHomeLayoutToggle(layout: SettingsHomeLayout, onToggle: () -> Unit) {
    val toGrid = layout == SettingsHomeLayout.List
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (toGrid) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = stringResource(
                    if (toGrid) R.string.settings_layout_to_grid else R.string.settings_layout_to_list,
                ),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 为 true 时 [SettingsActionRow] 画成一格方块，交给 [SettingsTileGrid] 排。 */
internal val LocalSettingsRowAsTile = staticCompositionLocalOf { false }

/**
 * 网格视图下的一组：一行 [SETTINGS_GRID_COLUMNS] 格，同一行的格子一样高，
 * 说明文字长短不一时也齐整。没显示出来（高度为 0）的条目不占格子。
 */
@Composable
internal fun SettingsTileGrid(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    CompositionLocalProvider(LocalSettingsRowAsTile provides true) {
        Layout(content = content) { measurables, constraints ->
            val columns = SETTINGS_GRID_COLUMNS
            val cellWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
            val shown = measurables.filter { it.minIntrinsicHeight(cellWidth) > 0 }
            val rows = shown.chunked(columns)
            val rowHeights = rows.map { row -> row.maxOf { it.minIntrinsicHeight(cellWidth) } }
            val placeables = rows.mapIndexed { index, row ->
                row.map { it.measure(Constraints.fixed(cellWidth, rowHeights[index])) }
            }
            val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.forEachIndexed { index, row ->
                    row.forEachIndexed { column, placeable ->
                        placeable.placeRelative(column * (cellWidth + gap), y)
                    }
                    y += rowHeights[index] + gap
                }
            }
        }
    }
}

private const val SETTINGS_GRID_COLUMNS = 3

/**
 * 可搜到的一项设置。[path] 是它在哪一页（「课程与时间 › 显示」），搜出来时写在标题下面，
 * [keywords] 是这一页里的其它条目名，搜里面的开关也能找到这一页。
 */
internal data class SettingsSearchEntry(
    val icon: ImageVector,
    val title: String,
    val path: String,
    val keywords: List<String> = emptyList(),
    val onClick: () -> Unit,
)

@Composable
internal fun SettingsSearchResults(
    entries: List<SettingsSearchEntry>,
    query: String,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    val results = remember(entries, query) { rankSettingsSearch(entries, query) }
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        results.forEach { entry ->
            SettingsActionRow(
                icon = entry.icon,
                title = entry.title,
                subtitle = entry.path,
                onClick = { onPick(entry) },
            )
        }
    }
}

internal fun rankSettingsSearch(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> =
    entries
        .mapIndexedNotNull { index, entry ->
            settingsSearchRank(entry.title, entry.path, entry.keywords, query)?.let { Triple(it, index, entry) }
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { it.third }
        .distinctBy { it.title to it.path }

/**
 * 一项设置和搜索词对不对得上，对得上时数越小越靠前：
 * 标题开头 → 标题里有 → 这一页的其它条目里有 → 所在页的名字里有 → 标题按顺序含着这几个字（「提前分」找「提前几分钟」）。
 */
internal fun settingsSearchRank(title: String, path: String, keywords: List<String>, query: String): Int? {
    val q = query.normalizedForSearch()
    if (q.isEmpty()) return null
    val t = title.normalizedForSearch()
    return when {
        t.startsWith(q) -> 0
        t.contains(q) -> 1
        keywords.any { it.normalizedForSearch().contains(q) } -> 2
        path.normalizedForSearch().contains(q) -> 3
        q.length >= 2 && t.containsInOrder(q) -> 4
        else -> null
    }
}

private fun String.normalizedForSearch(): String = lowercase().filterNot { it.isWhitespace() }

private fun String.containsInOrder(chars: String): Boolean {
    var from = 0
    for (c in chars) {
        val at = indexOf(c, from)
        if (at < 0) return false
        from = at + 1
    }
    return true
}
