package com.x500x.cursimple.core.data.widget

import android.content.Context
import com.x500x.cursimple.core.data.R
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import java.time.LocalTime

/** 节次编号允许的范围，与课程录入保持一致。 */
const val MIN_SLOT_NODE = 1
const val MAX_SLOT_NODE = 32

/** 编辑界面里一行的原始输入，节次和时间都以字符串保存，便于用户逐字修改。 */
data class SlotDraftInput(
    val startNode: String,
    val endNode: String,
    val startTime: String,
    val endTime: String,
    val label: String,
)

/** 校验不通过的原因，带上定位所需的行号与节次。 */
sealed interface TimingDraftError {
    data object EmptyDraft : TimingDraftError
    data class NodeNotNumber(val row: Int) : TimingDraftError
    data class NodeOutOfRange(val row: Int, val min: Int, val max: Int) : TimingDraftError
    data class NodeOrderReversed(val row: Int) : TimingDraftError
    data class TimeFormatInvalid(val row: Int) : TimingDraftError
    data class TimeOrderReversed(val row: Int) : TimingDraftError
    data class NodeRangeOverlap(
        val previousStartNode: Int,
        val previousEndNode: Int,
        val currentStartNode: Int,
        val currentEndNode: Int,
    ) : TimingDraftError
}

fun Context.timingDraftErrorText(error: TimingDraftError): String = when (error) {
    TimingDraftError.EmptyDraft -> getString(R.string.data_timing_error_no_slots)
    is TimingDraftError.NodeNotNumber ->
        getString(R.string.data_timing_error_node_not_number, error.row)
    is TimingDraftError.NodeOutOfRange ->
        getString(R.string.data_timing_error_node_out_of_range, error.row, error.min, error.max)
    is TimingDraftError.NodeOrderReversed ->
        getString(R.string.data_timing_error_node_reversed, error.row)
    is TimingDraftError.TimeFormatInvalid ->
        getString(R.string.data_timing_error_time_format, error.row)
    is TimingDraftError.TimeOrderReversed ->
        getString(R.string.data_timing_error_time_reversed, error.row)
    is TimingDraftError.NodeRangeOverlap -> getString(
        R.string.data_timing_error_node_overlap,
        error.previousStartNode,
        error.previousEndNode,
        error.currentStartNode,
        error.currentEndNode,
    )
}

/** 校验并归一化后的结果。errors 非空时 slots 为空，表示不能保存。 */
data class TimingDraftResult(
    val slots: List<ClassSlotTime>,
    val errors: List<TimingDraftError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** 模板里的一个节次。标签取自资源，labelArg 非空时作为占位符实参。 */
data class TimingTemplateSlot(
    val startNode: Int,
    val endNode: Int,
    val startTime: String,
    val endTime: String,
    val labelRes: Int,
    val labelArg: Int? = null,
)

/** 常用作息模板。时间仅为示例，需用户按本校作息改动。 */
data class TimingTemplate(
    val id: String,
    val nameRes: Int,
    val summaryRes: Int,
    val slots: List<TimingTemplateSlot>,
)

fun TimingTemplate.slotTimes(context: Context): List<ClassSlotTime> = slots.map { slot ->
    ClassSlotTime(
        startNode = slot.startNode,
        endNode = slot.endNode,
        startTime = slot.startTime,
        endTime = slot.endTime,
        label = slot.labelArg
            ?.let { context.getString(slot.labelRes, it) }
            ?: context.getString(slot.labelRes),
    )
}

/** 把用户输入的 "8:0" 之类补齐成 "08:00"，无法解析成合法时刻时返回 null。 */
fun normalizeTimeOrNull(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    return runCatching {
        val time = LocalTime.of(hour, minute)
        "%02d:%02d".format(time.hour, time.minute)
    }.getOrNull()
}

/** 把编辑行解析并校验成节次时间表。校验失败时逐条返回问题。 */
fun buildTimingSlots(drafts: List<SlotDraftInput>): TimingDraftResult {
    if (drafts.isEmpty()) {
        return TimingDraftResult(emptyList(), listOf(TimingDraftError.EmptyDraft))
    }

    val errors = mutableListOf<TimingDraftError>()
    val parsed = mutableListOf<ClassSlotTime>()

    drafts.forEachIndexed { index, draft ->
        val row = index + 1
        val startNode = draft.startNode.trim().toIntOrNull()
        val endNode = draft.endNode.trim().toIntOrNull()
        if (startNode == null || endNode == null) {
            errors += TimingDraftError.NodeNotNumber(row)
            return@forEachIndexed
        }
        if (startNode !in MIN_SLOT_NODE..MAX_SLOT_NODE || endNode !in MIN_SLOT_NODE..MAX_SLOT_NODE) {
            errors += TimingDraftError.NodeOutOfRange(row, MIN_SLOT_NODE, MAX_SLOT_NODE)
            return@forEachIndexed
        }
        if (startNode > endNode) {
            errors += TimingDraftError.NodeOrderReversed(row)
            return@forEachIndexed
        }
        val startTime = normalizeTimeOrNull(draft.startTime)
        val endTime = normalizeTimeOrNull(draft.endTime)
        if (startTime == null || endTime == null) {
            errors += TimingDraftError.TimeFormatInvalid(row)
            return@forEachIndexed
        }
        if (LocalTime.parse(startTime) >= LocalTime.parse(endTime)) {
            errors += TimingDraftError.TimeOrderReversed(row)
            return@forEachIndexed
        }
        parsed += ClassSlotTime(
            startNode = startNode,
            endNode = endNode,
            startTime = startTime,
            endTime = endTime,
            label = draft.label.trim(),
        )
    }

    if (errors.isNotEmpty()) {
        return TimingDraftResult(emptyList(), errors)
    }

    val sorted = parsed.sortedWith(compareBy({ it.startNode }, { it.endNode }))
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val current = sorted[i]
        if (current.startNode <= prev.endNode) {
            errors += TimingDraftError.NodeRangeOverlap(
                previousStartNode = prev.startNode,
                previousEndNode = prev.endNode,
                currentStartNode = current.startNode,
                currentEndNode = current.endNode,
            )
        }
    }

    return if (errors.isEmpty()) {
        TimingDraftResult(sorted, emptyList())
    } else {
        TimingDraftResult(emptyList(), errors)
    }
}

/** 内置作息模板。名称点明制式，摘要说明这是示例、需按本校作息调整。 */
fun timingTemplates(): List<TimingTemplate> = listOf(
    TimingTemplate(
        id = "single_11",
        nameRes = R.string.data_timing_template_single11_name,
        summaryRes = R.string.data_timing_template_single11_summary,
        slots = listOf(
            periodSlot(1, "08:00", "08:45"),
            periodSlot(2, "08:55", "09:40"),
            periodSlot(3, "10:00", "10:45"),
            periodSlot(4, "10:55", "11:40"),
            periodSlot(5, "14:00", "14:45"),
            periodSlot(6, "14:55", "15:40"),
            periodSlot(7, "16:00", "16:45"),
            periodSlot(8, "16:55", "17:40"),
            periodSlot(9, "19:00", "19:45"),
            periodSlot(10, "19:55", "20:40"),
            periodSlot(11, "20:50", "21:35"),
        ),
    ),
    TimingTemplate(
        id = "block_5",
        nameRes = R.string.data_timing_template_block5_name,
        summaryRes = R.string.data_timing_template_block5_summary,
        slots = listOf(
            TimingTemplateSlot(1, 2, "08:00", "09:40", R.string.data_timing_slot_label_block_1),
            TimingTemplateSlot(3, 4, "10:00", "11:40", R.string.data_timing_slot_label_block_2),
            TimingTemplateSlot(5, 6, "14:00", "15:40", R.string.data_timing_slot_label_block_3),
            TimingTemplateSlot(7, 8, "16:00", "17:40", R.string.data_timing_slot_label_block_4),
            TimingTemplateSlot(9, 10, "19:00", "20:40", R.string.data_timing_slot_label_block_5),
        ),
    ),
    TimingTemplate(
        id = "single_8",
        nameRes = R.string.data_timing_template_single8_name,
        summaryRes = R.string.data_timing_template_single8_summary,
        slots = listOf(
            periodSlot(1, "08:00", "08:45"),
            periodSlot(2, "08:55", "09:40"),
            periodSlot(3, "10:00", "10:45"),
            periodSlot(4, "10:55", "11:40"),
            periodSlot(5, "14:00", "14:45"),
            periodSlot(6, "14:55", "15:40"),
            periodSlot(7, "16:00", "16:45"),
            periodSlot(8, "16:55", "17:40"),
        ),
    ),
)

private fun periodSlot(node: Int, startTime: String, endTime: String): TimingTemplateSlot =
    TimingTemplateSlot(
        startNode = node,
        endNode = node,
        startTime = startTime,
        endTime = endTime,
        labelRes = R.string.data_timing_slot_label_period,
        labelArg = node,
    )

/** 编辑界面回填模板时，把模型转换成可继续修改的输入行。 */
fun ClassSlotTime.toDraftInput(): SlotDraftInput = SlotDraftInput(
    startNode = startNode.toString(),
    endNode = endNode.toString(),
    startTime = startTime,
    endTime = endTime,
    label = label,
)
