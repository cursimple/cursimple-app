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

/** 下拉列表最高这么高，课再多就在框里接着滑，不会铺满整屏；键盘弹起时 Material 自己再往矮里缩 */
private val DROPDOWN_LIST_MAX_HEIGHT = 300.dp

/** 「点外面收起」和按在选择框上前后差不到这么久，就当是同一下，不收 */
private const val ANCHOR_PRESS_WINDOW_MS = 300L

/** 一项大约多高（两行：课名 + 老师地点），打开时据此把选中项滚到眼前 */
private val MENU_ITEM_ESTIMATE = 60.dp

/**
 * 选笔记本的下拉框：总览顶上和编辑页共用。
 *
 * 课一多，横着排一串的选择条就翻不到了；下拉框按待办多少排好，
 * 每项带课程颜色、老师地点和待办数，有固定的最大高度，里面自己滚。
 * 点开之后选择框本身变成搜索框（Material 可输入的下拉框），
 * 列表始终挂在它下面，键盘弹起时跟着缩矮，不会跳到上面把选择框盖住。
 *
 * [includeAll] 为真时第一项是「全部」，选中它回调 [onSelect] 传 [ALL_NOTEBOOKS]。
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
    // 收起时选择框的高度：点开换成搜索框后保持一样高，不上下跳
    var collapsedHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val selected = notebooks.firstOrNull { it.key == selectedKey }
    // 搜索词；收起时清掉，下次打开从头来
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val shown = remember(notebooks, query) { notebooks.filter { memoNotebookMatches(it, query) } }
    val searchFocus = remember { FocusRequester() }
    // 菜单把点在它外面的都当成「点外面收起」，搜索框、清空按钮也在它外面。
    // 记下最近一次按在选择框上的时间，这种「外面」不收
    var lastAnchorPress by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    // 菜单开着时，点在外面的那一下只用来收起菜单，不再落到下面的卡片上
    val shield = LocalMemoTapShield.current
    DisposableEffect(shield, expanded) {
        // 记下这一轮是不是开着的：onDispose 跑的时候 expanded 已经变成新值了
        val armed = shield != null && expanded
        if (armed) {
            shield.active = true
            shield.onOutside = { expanded = false }
        }
        onDispose { if (armed) shield.active = false }
    }
    // 每次打开都把选中的那一项滚到眼前（上面留一项），不停在上回滑到的地方
    val menuScroll = rememberScrollState()
    val itemHeightPx = with(density) { MENU_ITEM_ESTIMATE.toPx() }
    LaunchedEffect(expanded) {
        if (!expanded) {
            query = ""
            return@LaunchedEffect
        }
        // 点开就能直接打字
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
        // 菜单排好版之后才有滚动范围；列表短到不用滚的话就一直是 0，等一会儿就算了
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
                // 打开以后点框里是在搜索框里挪光标，不该把菜单收起来；收起靠右边的箭头、点外面或返回
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
                    // 收着的时候点整个框就展开，箭头只是个样子，不再单独接点击（免得一次点两下来回切）
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.memo_notebook_pick))
                    }
                }
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                // 「点外面」和按在选择框上的那一下谁先到不一定，稍等一下再比两者的时间；
                // 比的是两件事发生的时刻，不是等了多久：手机一卡，这一下可能等上好几百毫秒
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
            // 搜的时候「全部」不算一门课，先藏起来
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
 * 下拉框里搜课：课名、老师、地点里包含搜索词就算；
 * 两个字以上时再认按顺序的缩写，「数分」找得到「数值分析」，「汇微」找得到「汇编语言与微型计算机技术」。
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
 * 可输入的下拉菜单不抢焦点（抢了键盘就归它了），代价是点在菜单外面的那一下会落到下面去：
 * 想收起菜单，结果点开了底下的笔记。把整页包在 [MemoTapShieldHost] 里，
 * 菜单开着时点在选择框以外的地方只收起菜单，这一下连同后面的滑动、抬起都吞掉。
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

/** 选中「全部」时回调给的值：和「其他」（null）区分开 */
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

/** 选择框里的小圆按钮：比 IconButton 小一圈，打开、收起时框的高度不变 */
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

/** 课程颜色的圆点；「全部」没有颜色，换成一个格子图标 */
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
