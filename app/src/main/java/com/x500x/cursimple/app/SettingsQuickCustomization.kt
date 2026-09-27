package com.x500x.cursimple.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 「常用」里能放的一项。[id] 存进本地记住用户挑了哪些，[group] 是它在首页属于哪一组，
 * 「＋」弹出的选择框按它分段。
 */
internal data class SettingsQuickItem(
    val id: String,
    val group: String,
    val spec: SettingsQuickTileSpec,
)

/**
 * 「常用」挑了哪几项、什么顺序。和首页排法一样只关乎这台手机上怎么看，存本地，不跟着备份走。
 */
internal object SettingsQuickStore {
    private const val PREFS = "settings_ui"
    private const val KEY = "quick_ids"

    /** 没动过时的样子：最常改的六项。 */
    val DEFAULT_IDS = listOf("theme_mode", "theme_accent", "background", "current_week", "class_notice", "widget")

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { raw -> raw.split(',').filter { it.isNotBlank() } }
            ?: DEFAULT_IDS

    fun save(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, ids.joinToString(",")).apply()
    }
}

/**
 * 首页拖动的状态：长按下面任意一项拖进「常用」就加进来；「常用」里的格子长按可以换位置，
 * 拖出去松手就移走。坐标全按根布局算，拖动的「影子」由 [SettingsQuickDragGhost] 画在整页最上面。
 */
@Stable
internal class SettingsQuickDragState(
    initialIds: List<String>,
    private val onIdsChange: (List<String>) -> Unit,
) {
    val ids = mutableStateListOf<String>().apply { addAll(initialIds) }

    var draggingId by mutableStateOf<String?>(null)
        private set
    var fromQuick by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** 「常用」那块深色底的范围，判断松手时落没落在里面。 */
    var quickBounds by mutableStateOf(Rect.Zero)

    /** 可见的滚动区域，拖到上下边缘时自动滚。 */
    var viewport by mutableStateOf(Rect.Zero)

    /** 「常用」里一格的宽度，影子按它画，拖进来之前就是加进来后的大小。 */
    var cellWidthPx by mutableStateOf(0)

    val tileBounds = mutableMapOf<String, Rect>()

    private var lastDragAt = 0L

    val insideQuick: Boolean get() = draggingId != null && quickBounds.contains(pointer)

    /** 长按拖动之后松手，底下那一项的点击也会跟着触发，拖过就吞掉这一下。 */
    fun consumeClickAfterDrag(): Boolean =
        draggingId != null || SystemClock.uptimeMillis() - lastDragAt < CLICK_SUPPRESS_MILLIS

    fun start(id: String, fromQuick: Boolean, at: Offset) {
        draggingId = id
        this.fromQuick = fromQuick
        pointer = at
        lastDragAt = SystemClock.uptimeMillis()
    }

    fun move(at: Offset) {
        val id = draggingId ?: return
        pointer = at
        // 「常用」里拖着换位置：压到别的格子上就挪过去，边拖边看到新顺序
        if (fromQuick && insideQuick) {
            val target = tileBounds.entries.firstOrNull { (other, rect) -> other != id && rect.contains(at) }?.key
                ?: return
            val from = ids.indexOf(id)
            val to = ids.indexOf(target)
            if (from >= 0 && to >= 0 && from != to) {
                ids.removeAt(from)
                ids.add(to, id)
            }
        }
    }

    fun end() {
        val id = draggingId ?: return
        if (fromQuick) {
            if (!insideQuick) ids.remove(id)
        } else if (insideQuick) {
            // 落在哪一格上就插在那一格的位置，落在空白处就接在最后
            val target = tileBounds.entries.firstOrNull { (other, rect) -> other != id && rect.contains(pointer) }?.key
            ids.remove(id)
            val index = target?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: ids.size
            ids.add(index, id)
        }
        finish()
        onIdsChange(ids.toList())
    }

    fun cancel() = finish()

    fun toggle(id: String) {
        if (!ids.remove(id)) ids.add(id)
        onIdsChange(ids.toList())
    }

    private fun finish() {
        draggingId = null
        lastDragAt = SystemClock.uptimeMillis()
    }

    private companion object {
        const val CLICK_SUPPRESS_MILLIS = 400L
    }
}

internal val LocalSettingsQuickDrag = staticCompositionLocalOf<SettingsQuickDragState?> { null }

/** 首页上可以长按拖进「常用」的一项；不在首页（没有拖动状态）时什么都不做。 */
internal fun Modifier.settingsQuickDragSource(id: String?, fromQuick: Boolean = false): Modifier = composed {
    val state = LocalSettingsQuickDrag.current
    if (id == null || state == null) return@composed this
    val haptic = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(id, fromQuick) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(id, fromQuick, origin + offset)
                },
                onDrag = { change, _ ->
                    change.consume()
                    state.move(origin + change.position)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.cancel() },
            )
        }
}

/** 拖到滚动区上下边缘时自动滚，「常用」在页顶，从底下拖上来不用松手先滑回去。 */
@Composable
internal fun SettingsQuickAutoScroll(state: SettingsQuickDragState, scrollState: ScrollState) {
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val dragging = state.draggingId != null
    LaunchedEffect(dragging) {
        while (dragging) {
            val view = state.viewport
            val y = state.pointer.y
            val delta = when {
                view.height <= 0f -> 0f
                y < view.top + edge -> -((view.top + edge - y) / edge).coerceIn(0f, 1f) * MAX_SCROLL_STEP
                y > view.bottom - edge -> ((y - (view.bottom - edge)) / edge).coerceIn(0f, 1f) * MAX_SCROLL_STEP
                else -> 0f
            }
            if (delta != 0f) scrollState.scrollBy(delta)
            delay(16)
        }
    }
}

private const val MAX_SCROLL_STEP = 28f

/**
 * 「常用」：单独一块深色底，和下面几组分开。右上角「＋」从全部设置里挑；
 * 也可以把下面任意一项长按拖进来。格子长按能换位置，拖出这块松手就移走。
 */
@Composable
internal fun SettingsQuickSection(
    state: SettingsQuickDragState,
    items: List<SettingsQuickItem>,
) {
    val byId = remember(items) { items.associateBy { it.id } }
    val shown = state.ids.mapNotNull { byId[it] }
    var showPicker by remember { mutableStateOf(false) }
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = if (darkTheme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val onContainer = if (darkTheme) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    val addingFromBelow = state.draggingId != null && !state.fromQuick
    val removing = state.draggingId != null && state.fromQuick && !state.insideQuick
    val border by animateColorAsState(
        targetValue = if (addingFromBelow && state.insideQuick) onContainer else container,
        label = "quickBorder",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = onContainer,
        border = BorderStroke(2.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { state.quickBounds = it.boundsInRoot() },
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_group_quick),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = { showPicker = true },
                    shape = CircleShape,
                    color = onContainer.copy(alpha = 0.16f),
                    contentColor = onContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.settings_quick_add),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_quick_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(end = 4.dp, top = 8.dp),
                )
            } else {
                QuickTilesLayout(state = state, tiles = shown)
            }
            AnimatedVisibility(visible = addingFromBelow || removing) {
                Text(
                    text = stringResource(
                        when {
                            removing -> R.string.settings_quick_drop_remove
                            state.insideQuick -> R.string.settings_quick_drop_release
                            else -> R.string.settings_quick_drop_here
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    if (showPicker) {
        SettingsQuickPickerDialog(
            items = items,
            selected = state.ids.toSet(),
            onToggle = state::toggle,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 常用格子：一行三个、同一行一样高。所有格子是同一层的子项（按 id 标记），
 * 拖着换位置时格子节点跟着移动而不是重建，手指底下那一格的拖动才不会断。
 */
@Composable
private fun QuickTilesLayout(state: SettingsQuickDragState, tiles: List<SettingsQuickItem>) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Layout(
        content = {
            tiles.forEach { item ->
                key(item.id) {
                    val dragged = state.draggingId == item.id && state.fromQuick
                    SettingsQuickTile(
                        tile = item.spec.copy(onClick = {
                            if (!state.consumeClickAfterDrag()) item.spec.onClick()
                        }),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .onGloballyPositioned { state.tileBounds[item.id] = it.boundsInRoot() }
                            .settingsQuickDragSource(item.id, fromQuick = true)
                            .alpha(if (dragged) 0.25f else 1f),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val columns = QUICK_COLUMNS
        val cellWidth = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        state.cellWidthPx = cellWidth
        val rows = measurables.chunked(columns)
        val rowHeights = rows.map { row -> row.maxOf { it.minIntrinsicHeight(cellWidth) } }
        val placeables = rows.mapIndexed { index, row ->
            row.map { it.measure(Constraints.fixed(cellWidth, rowHeights[index])) }
        }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(column * (cellWidth + gap), y) }
                y += rowHeights[index] + gap
            }
        }
    }
    // 移走的格子不再占着判断落点
    SideEffect {
        val present = tiles.map { it.id }.toSet()
        state.tileBounds.keys.retainAll(present)
    }
}

private const val QUICK_COLUMNS = 3

/** 拖动时跟着手指走的那一格，画在整页最上面，拖到哪都看得见。 */
@Composable
internal fun SettingsQuickDragGhost(state: SettingsQuickDragState, items: List<SettingsQuickItem>) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val id = state.draggingId ?: return@Box
        val item = items.firstOrNull { it.id == id } ?: return@Box
        val widthPx = state.cellWidthPx.takeIf { it > 0 } ?: with(density) { 110.dp.roundToPx() }
        val width = with(density) { widthPx.toDp() }
        SettingsQuickTile(
            tile = item.spec.copy(onClick = {}),
            // 主题里的 surface 为了透出课表背景带点透明，影子压在别的格子上会透字，先合成成实色
            color = MaterialTheme.colorScheme.surface.compositeOver(MaterialTheme.colorScheme.background),
            modifier = Modifier
                .offset {
                    IntOffset(
                        (state.pointer.x - origin.x - widthPx / 2f).roundToInt(),
                        (state.pointer.y - origin.y - GHOST_FINGER_OFFSET_DP.dp.toPx()).roundToInt(),
                    )
                }
                .width(width)
                .graphicsLayer {
                    scaleX = 1.06f
                    scaleY = 1.06f
                    alpha = if (state.fromQuick && !state.insideQuick) 0.7f else 1f
                }
                .shadow(10.dp, RoundedCornerShape(18.dp)),
        )
    }
}

// 影子画在手指上方一点，别被手指挡住
private const val GHOST_FINGER_OFFSET_DP = 56

/** 「＋」弹出的选择框：按首页的分组列出全部可放进「常用」的项，勾上就加，取消就移走。 */
@Composable
private fun SettingsQuickPickerDialog(
    items: List<SettingsQuickItem>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_quick_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.settings_quick_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                items.groupBy { it.group }.forEach { (group, groupItems) ->
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    groupItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item.id) }
                                .padding(vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = item.spec.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = item.spec.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(checked = item.id in selected, onCheckedChange = { onToggle(item.id) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_quick_done)) }
        },
    )
}
