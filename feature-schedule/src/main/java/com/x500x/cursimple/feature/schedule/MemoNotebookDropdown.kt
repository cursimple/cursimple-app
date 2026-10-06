package com.x500x.cursimple.feature.schedule

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bound menu height and allow internal scrolling with keyboard-aware resizing. */
private val DROPDOWN_LIST_MAX_HEIGHT = 300.dp

private const val ANCHOR_PRESS_WINDOW_MS = 300L

private val MENU_ITEM_ESTIMATE = 60.dp

/**
 * Shared searchable notebook picker stays below its anchor, with bounded scrolling.
 * [includeAll] adds an entry returning [ALL_NOTEBOOKS].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoNotebookDropdown(
    notebooks: List<MemoNotebook>,
    openCounts: Map<String?, Int>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = false,
    allSelected: Boolean = false,
    allCount: Int = 0,
) {
    var expanded by remember { mutableStateOf(false) }
    var collapsedHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val selected = notebooks.firstOrNull { it.key == selectedKey }
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val shown = remember(notebooks, query) { notebooks.filter { memoNotebookMatches(it, query) } }
    val searchFocus = remember { FocusRequester() }
    // Anchor and clear-button taps count as picker interaction despite being outside the popup.
    var lastAnchorPress by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    // Outside taps dismiss without activating the underlying note.
    val shield = LocalMemoTapShield.current
    DisposableEffect(shield, expanded) {
        val armed = shield != null && expanded
        if (armed) {
            shield.active = true
            shield.onOutside = { expanded = false }
        }
        onDispose { if (armed) shield.active = false }
    }
    val menuScroll = rememberScrollState()
    val itemHeightPx = with(density) { MENU_ITEM_ESTIMATE.toPx() }
    LaunchedEffect(expanded) {
        if (!expanded) {
            query = ""
            return@LaunchedEffect
        }
        runCatching { searchFocus.requestFocus() }
        val index = when {
            includeAll && allSelected -> 0
            else -> notebooks.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0) + if (includeAll) 1 else 0
        }
        val target = ((index - 1).coerceAtLeast(0) * itemHeightPx).roundToInt()
        if (target == 0) {
            menuScroll.scrollTo(0)
            return@LaunchedEffect
        }
        withTimeoutOrNull(600) { snapshotFlow { menuScroll.maxValue }.first { it > 0 } }
        menuScroll.scrollTo(target)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                if (expanded) 1.5.dp else 1.dp,
                if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = !expanded)
                .fillMaxWidth()
                .onGloballyPositioned { shield?.anchor = it.boundsInRoot() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastAnchorPress = SystemClock.uptimeMillis()
                    }
                }
                .then(
                    if (expanded) {
                        Modifier.heightIn(min = with(density) { collapsedHeight.toDp() })
                    } else {
                        Modifier.onSizeChanged { collapsedHeight = it.height }
                    },
                ),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expanded) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.memo_notebook_search),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (searching) {
                        FieldIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.memo_notebook_search_clear),
                            onClick = { query = "" },
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                } else if (includeAll && allSelected) {
                    NotebookGlyph(color = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.memo_notebook_all),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    CountBadge(allCount, highlighted = true)
                } else if (selected != null) {
                    NotebookGlyph(color = memoNotebookColor(selected)?.container ?: MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = selected.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        notebookSubtitle(selected)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CountBadge(openCounts[selected.key] ?: 0, highlighted = true)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(4.dp))
                if (expanded) {
                    FieldIconButton(
                        icon = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.memo_notebook_pick),
                        onClick = { expanded = false },
                        modifier = Modifier.rotate(180f),
                    )
                } else {
                    // The whole collapsed anchor owns expansion; the arrow must not toggle again.
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.memo_notebook_pick))
                    }
                }
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                // Compare event timestamps after both callbacks arrive, not elapsed handler delay.
                val dismissAt = SystemClock.uptimeMillis()
                scope.launch {
                    delay(80)
                    if (abs(lastAnchorPress - dismissAt) > ANCHOR_PRESS_WINDOW_MS) expanded = false
                }
            },
            scrollState = menuScroll,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.heightIn(max = DROPDOWN_LIST_MAX_HEIGHT),
        ) {
            if (searching && shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.memo_notebook_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            if (includeAll && !searching) {
                NotebookMenuItem(
                    title = stringResource(R.string.memo_notebook_all),
                    subtitle = null,
                    color = null,
                    count = allCount,
                    selected = allSelected,
                    onClick = {
                        expanded = false
                        onSelect(ALL_NOTEBOOKS)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            shown.forEach { notebook ->
                NotebookMenuItem(
                    title = notebook.title,
                    subtitle = notebookSubtitle(notebook),
                    color = memoNotebookColor(notebook)?.container ?: MaterialTheme.colorScheme.outlineVariant,
                    count = openCounts[notebook.key] ?: 0,
                    selected = !allSelected && notebook.key == selectedKey,
                    onClick = {
                        expanded = false
                        onSelect(notebook.key)
                    },
                )
            }
        }
    }
}

/**
 * Search course, teacher and location substrings; multi-character queries also support ordered
 * abbreviations.
 */
internal fun memoNotebookMatches(notebook: MemoNotebook, query: String): Boolean {
    val q = query.trim().lowercase().filterNot { it.isWhitespace() }
    if (q.isEmpty()) return true
    val title = notebook.title.lowercase()
    if (title.contains(q) || notebook.subtitle.lowercase().contains(q)) return true
    if (q.length < 2) return false
    var from = 0
    for (c in q) {
        val at = title.indexOf(c, from)
        if (at < 0) return false
        from = at + 1
    }
    return true
}

/**
 * [MemoTapShieldHost] consumes outside touch sequences while a non-focusable menu is open,
 * preserving editor keyboard focus.
 */
internal class MemoTapShield {
    var active = false
    var anchor = Rect.Zero
    var hostOrigin = Offset.Zero
    var onOutside: () -> Unit = {}
}

internal val LocalMemoTapShield = staticCompositionLocalOf<MemoTapShield?> { null }

@Composable
internal fun MemoTapShieldHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shield = remember { MemoTapShield() }
    CompositionLocalProvider(LocalMemoTapShield provides shield) {
        Box(
            modifier = modifier
                .onGloballyPositioned { shield.hostOrigin = it.positionInRoot() }
                .pointerInput(shield) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!shield.active || shield.anchor.contains(down.position + shield.hostOrigin)) return@awaitEachGesture
                        down.consume()
                        shield.onOutside()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            content = content,
        )
    }
}

internal const val ALL_NOTEBOOKS = "\u0000all"

@Composable
private fun notebookSubtitle(notebook: MemoNotebook): String? = when {
    notebook.orphan -> stringResource(R.string.memo_notebook_orphan_short)
    notebook.key == null -> stringResource(R.string.memo_notebook_other_desc)
    else -> notebook.subtitle.takeIf { it.isNotBlank() }
}

@Composable
private fun NotebookMenuItem(
    title: String,
    subtitle: String?,
    color: Color?,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = { NotebookGlyph(color) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountBadge(count, highlighted = selected)
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        modifier = if (selected) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
        } else {
            Modifier
        },
    )
}

@Composable
private fun FieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun NotebookGlyph(color: Color?) {
    if (color == null) {
        Icon(
            imageVector = Icons.Rounded.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        return
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
    )
}

@Composable
private fun CountBadge(count: Int, highlighted: Boolean) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
