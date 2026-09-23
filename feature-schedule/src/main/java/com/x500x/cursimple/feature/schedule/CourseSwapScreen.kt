package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.x500x.cursimple.core.data.widget.classSlotLabelText
import com.x500x.cursimple.core.data.widget.courseSlotLabelText
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate

/** 一格的高度：两列并排后宽度有限，高度给足才放得下课名与地点。 */
private val CELL_HEIGHT = 64.dp
private val NODE_COLUMN_WIDTH = 44.dp

/**
 * 正在拖的那门课。
 *
 * [pointer] 是手指在根坐标系里的位置，[grab] 是按下时手指落在卡片内的偏移，
 * [size] 是原卡片的像素尺寸——浮块按这两项还原成整张卡片跟着手走，
 * 而不是另画一个小标签。
 */
private data class SwapDrag(
    val course: CourseItem,
    val fromLeft: Boolean,
    val pointer: Offset,
    val grab: Offset,
    val size: IntSize,
)

/** 待确认的调课。 */
private data class PendingSwap(
    val course: CourseItem,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val startNode: Int,
    val endNode: Int,
)

/**
 * 两天并排的调课编辑器。
 *
 * 左右各铺满一整天的节次（空节也留格），长按课程块拖到另一列的某一格即可调课，
 * 松手前先确认调到哪天哪几节。整页全屏，不套弹窗——两列网格在弹窗里根本摆不下。
 */
@Composable
fun CourseSwapScreen(
    leftDate: LocalDate,
    rightDate: LocalDate,
    timingProfile: TermTimingProfile?,
    termStartDate: LocalDate?,
    allCourses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    onPickLeftDate: () -> Unit,
    onPickRightDate: () -> Unit,
    onMove: (course: CourseItem, from: LocalDate, to: LocalDate, startNode: Int, endNode: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(timingProfile) {
        timingProfile?.slotTimes
            ?.filter { it.endNode >= it.startNode }
            ?.sortedWith(compareBy({ it.startNode }, { it.endNode }))
            .orEmpty()
    }
    val context = LocalContext.current
    // 序号跟课表左侧节次栏一样按排序后的作息表取，没写名字的时段也能生成「第 N 节」
    val slotLabels = remember(slots, context) {
        slots.mapIndexed { index, slot -> context.classSlotLabelText(slot, index + 1) }
    }
    val visible = remember(allCourses) { allCourses.visibleScheduleCourses() }
    val leftCourses = remember(visible, leftDate, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(leftDate, visible, overrides, holidayCalendar, termStartDate)
    }
    val rightCourses = remember(visible, rightDate, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(rightDate, visible, overrides, holidayCalendar, termStartDate)
    }

    // 每一格在根坐标系里的范围：(是否左列, 节次下标) -> 矩形，拖拽落点靠它判定
    val cellBounds = remember { mutableStateMapOf<Pair<Boolean, Int>, Rect>() }
    var drag by remember { mutableStateOf<SwapDrag?>(null) }
    var pending by remember { mutableStateOf<PendingSwap?>(null) }
    val density = LocalDensity.current
    // 浮块画在下面那个 Box 里，而拖拽坐标记的是根坐标；
    // 不减掉这个 Box 自己的根偏移，浮块就会整体掉到手指下方标题栏那么高的地方
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.schedule_action_close),
                )
            }
            Text(
                text = stringResource(R.string.schedule_swap_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = if (slots.isEmpty()) {
                stringResource(R.string.schedule_swap_no_timing)
            } else {
                stringResource(R.string.schedule_swap_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (slots.isEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        val leftHoliday = remember(leftDate, overrides, holidayCalendar) {
            resolveScheduleDay(leftDate, overrides, holidayCalendar).takeIf { it.isHoliday }
        }
        val rightHoliday = remember(rightDate, overrides, holidayCalendar) {
            resolveScheduleDay(rightDate, overrides, holidayCalendar).takeIf { it.isHoliday }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Spacer(Modifier.width(NODE_COLUMN_WIDTH))
            SwapDayHeader(
                date = leftDate,
                enabled = true,
                holidayName = leftHoliday?.let {
                    it.holidayName?.takeIf { n -> n.isNotBlank() }
                        ?: stringResource(R.string.schedule_holiday_unnamed)
                },
                onClick = onPickLeftDate,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            SwapDayHeader(
                date = rightDate,
                enabled = true,
                holidayName = rightHoliday?.let {
                    it.holidayName?.takeIf { n -> n.isNotBlank() }
                        ?: stringResource(R.string.schedule_holiday_unnamed)
                },
                onClick = onPickRightDate,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragLayerOrigin = it.positionInRoot() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 拖拽中锁住滚动：一滚，记下来的格子坐标就和屏幕对不上了
                    .verticalScroll(scrollState, enabled = drag == null)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.width(NODE_COLUMN_WIDTH)) {
                    slots.forEachIndexed { index, slot ->
                        Column(
                            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // 和课表左侧的节次栏同一套叫法，节号是内部编号，直接写出来对不上课表
                            Text(
                                text = slotLabels.getOrElse(index) { nodeLabelOf(slot) },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                            if (slot.startTime.isNotBlank()) {
                                Text(
                                    text = slot.startTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                SwapDayColumn(
                    isLeft = true,
                    slots = slots,
                    courses = leftCourses,
                    draggable = true,
                    dragging = drag,
                    cellBounds = cellBounds,
                    onDragStateChange = { drag = it },
                    onDrop = { d -> pending = resolveDrop(d, cellBounds, slots, leftDate, rightDate) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                SwapDayColumn(
                    isLeft = false,
                    slots = slots,
                    courses = rightCourses,
                    draggable = true,
                    dragging = drag,
                    cellBounds = cellBounds,
                    onDragStateChange = { drag = it },
                    onDrop = { d -> pending = resolveDrop(d, cellBounds, slots, leftDate, rightDate) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 跟手的浮块就是整张卡片本身：尺寸、配色、内容都和原来那格一样，
            // 并且保持按下时手指在卡片里的相对位置，拖起来不会突然跳开
            drag?.let { d ->
                SwapCourseCard(
                    course = d.course,
                    elevated = true,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (d.pointer.x - d.grab.x - dragLayerOrigin.x).toInt(),
                                (d.pointer.y - d.grab.y - dragLayerOrigin.y).toInt(),
                            )
                        }
                        .size(
                            width = with(density) { d.size.width.toDp() },
                            height = with(density) { d.size.height.toDp() },
                        ),
                )
            }
        }

    }

    pending?.let { swap ->
        // 调课可以推翻放假，但得让人知道自己正把课排进休息日
        val targetHoliday = remember(swap.toDate, overrides, holidayCalendar) {
            resolveScheduleDay(swap.toDate, overrides, holidayCalendar)
                .takeIf { it.isHoliday }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.schedule_swap_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.schedule_swap_confirm_body,
                            swap.course.title,
                            formatSwapDate(swap.toDate),
                            nodeSpanText(swap.startNode, swap.endNode),
                        ).let { numbered ->
                            // 落点正好是一个时段就说「第六节」，跨了几个时段才说节号
                            context.courseSlotLabelText(timingProfile, swap.startNode, swap.endNode)
                                ?.let { name ->
                                    stringResource(
                                        R.string.schedule_swap_confirm_body_slot,
                                        swap.course.title,
                                        formatSwapDate(swap.toDate),
                                        name,
                                    )
                                }
                                ?: numbered
                        },
                    )
                    targetHoliday?.let { holiday ->
                        Text(
                            text = stringResource(
                                R.string.schedule_swap_confirm_holiday,
                                holiday.holidayName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.schedule_holiday_unnamed),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    onMove(swap.course, swap.fromDate, swap.toDate, swap.startNode, swap.endNode)
                    pending = null
                }) { Text(stringResource(R.string.schedule_swap_confirm_action)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
}

/** [holidayName] 非空表示这天放假；仍然能往里调课，只是先把话说在前面。 */
@Composable
private fun SwapDayHeader(
    date: LocalDate,
    holidayName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (holidayName != null) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        val onColor = if (holidayName != null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatSwapDate(date),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
            )
            Text(
                text = holidayName
                    ?: if (enabled) stringResource(R.string.schedule_swap_change_day) else "",
                style = MaterialTheme.typography.labelSmall,
                color = onColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwapDayColumn(
    isLeft: Boolean,
    slots: List<ClassSlotTime>,
    courses: List<CourseItem>,
    draggable: Boolean,
    dragging: SwapDrag?,
    cellBounds: MutableMap<Pair<Boolean, Int>, Rect>,
    onDragStateChange: (SwapDrag?) -> Unit,
    onDrop: (SwapDrag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        slots.forEachIndexed { index, slot ->
            // 只在课程起始的那一格画块，跨节的课不重复画
            val starting = courses.filter { it.time.startNode in slot.startNode..slot.endNode }
            // 高亮走和落点一模一样的判定，松手前看到哪一格亮就是会落到哪一格
            val hovered = dragging != null &&
                dragging.fromLeft != isLeft &&
                targetCell(dragging.pointer, isLeft, cellBounds) == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CELL_HEIGHT)
                    .padding(2.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        cellBounds[isLeft to index] = Rect(
                            pos.x,
                            pos.y,
                            pos.x + coords.size.width,
                            pos.y + coords.size.height,
                        )
                    }
                    .background(
                        color = if (hovered) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                starting.forEach { course ->
                    SwapCourseBlock(
                        course = course,
                        isLeft = isLeft,
                        draggable = draggable,
                        beingDragged = dragging?.course?.id == course.id && dragging.fromLeft == isLeft,
                        onDragStateChange = onDragStateChange,
                        onDrop = onDrop,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwapCourseBlock(
    course: CourseItem,
    isLeft: Boolean,
    draggable: Boolean,
    beingDragged: Boolean,
    onDragStateChange: (SwapDrag?) -> Unit,
    onDrop: (SwapDrag) -> Unit,
) {
    var blockRoot by remember { mutableStateOf(Offset.Zero) }
    var blockSize by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<SwapDrag?>(null) }
    val gesture = if (!draggable) {
        Modifier
    } else {
        Modifier.pointerInput(course.id, isLeft) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val state = SwapDrag(
                        course = course,
                        fromLeft = isLeft,
                        pointer = blockRoot + local,
                        grab = local,
                        size = blockSize,
                    )
                    current = state
                    onDragStateChange(state)
                },
                onDrag = { change, delta ->
                    change.consume()
                    val next = current?.let { it.copy(pointer = it.pointer + delta) }
                    current = next
                    onDragStateChange(next)
                },
                onDragEnd = {
                    current?.let(onDrop)
                    current = null
                    onDragStateChange(null)
                },
                onDragCancel = {
                    current = null
                    onDragStateChange(null)
                },
            )
        }
    }
    // 拖起来之后原位留个淡淡的影子，让人看清这门课是从哪一格走的
    SwapCourseCard(
        course = course,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (beingDragged) 0.35f else 1f)
            .onGloballyPositioned {
                blockRoot = it.positionInRoot()
                blockSize = it.size
            }
            .then(gesture),
    )
}

/** 课程卡片本体：格子里和拖动时的浮块共用同一份，拖起来才是「整张卡片」跟着走。 */
@Composable
private fun SwapCourseCard(
    course: CourseItem,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, accents) { courseColor(course.title, accents.coursePalette) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.container,
        shadowElevation = if (elevated) 8.dp else 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 目标列里手指落在哪一格。
 *
 * 精确落进格子当然算；但两列之间有缝、每格自己还带内边距，
 * 手指偏个几 dp 就会掉进空档里，严格判定会让人白拖一趟、以为拖不动，
 * 所以差一点也认，按就近吸附；只有横向离这一列太远才算真没落上。
 */
private fun targetCell(
    pointer: Offset,
    targetIsLeft: Boolean,
    cellBounds: Map<Pair<Boolean, Int>, Rect>,
): Int? {
    val candidates = cellBounds.entries.filter { it.key.first == targetIsLeft }
    if (candidates.isEmpty()) return null
    candidates.firstOrNull { it.value.contains(pointer) }?.let { return it.key.second }

    fun gap(low: Float, high: Float, value: Float) = when {
        value < low -> low - value
        value > high -> value - high
        else -> 0f
    }
    val nearest = candidates.minByOrNull { (_, rect) ->
        // 横向权重给大些：宁可吸到同一列的上下格，也别横着跳列
        gap(rect.left, rect.right, pointer.x) * 2f + gap(rect.top, rect.bottom, pointer.y)
    } ?: return null
    val rect = nearest.value
    val slack = rect.width * 0.5f
    if (pointer.x < rect.left - slack || pointer.x > rect.right + slack) return null
    return nearest.key.second
}

/** 松手时落在哪一格；落在原列或离目标列太远都返回 null。 */
private fun resolveDrop(
    drag: SwapDrag,
    cellBounds: Map<Pair<Boolean, Int>, Rect>,
    slots: List<ClassSlotTime>,
    leftDate: LocalDate,
    rightDate: LocalDate,
): PendingSwap? {
    val targetIsLeft = !drag.fromLeft
    val index = targetCell(drag.pointer, targetIsLeft, cellBounds) ?: return null
    val slot = slots.getOrNull(index) ?: return null
    // 保持课程原本跨了几节，落到目标格的起始节次上
    val span = (drag.course.time.endNode - drag.course.time.startNode).coerceAtLeast(0)
    return PendingSwap(
        course = drag.course,
        fromDate = if (drag.fromLeft) leftDate else rightDate,
        toDate = if (targetIsLeft) leftDate else rightDate,
        startNode = slot.startNode,
        endNode = slot.startNode + span,
    )
}

private fun nodeLabelOf(slot: ClassSlotTime): String = nodeSpanText(slot.startNode, slot.endNode)

/** 单节写「3」，跨节写「3-4」，不写成「3-3」。 */
private fun nodeSpanText(startNode: Int, endNode: Int): String =
    if (startNode == endNode) "$startNode" else "$startNode-$endNode"

private fun formatSwapDate(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"
