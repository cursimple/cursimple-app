package com.x500x.cursimple.core.data.widget

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

/** 校验并归一化后的结果。errors 非空时 slots 为空，表示不能保存。 */
data class TimingDraftResult(
    val slots: List<ClassSlotTime>,
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** 常用作息模板。时间仅为示例，需用户按本校作息改动。 */
data class TimingTemplate(
    val id: String,
    val name: String,
    val summary: String,
    val slots: List<ClassSlotTime>,
)

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

/** 把编辑行解析并校验成节次时间表。校验失败时把每条问题以中文返回。 */
fun buildTimingSlots(drafts: List<SlotDraftInput>): TimingDraftResult {
    if (drafts.isEmpty()) {
        return TimingDraftResult(emptyList(), listOf("请至少添加一个节次时间段"))
    }

    val errors = mutableListOf<String>()
    val parsed = mutableListOf<ClassSlotTime>()

    drafts.forEachIndexed { index, draft ->
        val row = index + 1
        val startNode = draft.startNode.trim().toIntOrNull()
        val endNode = draft.endNode.trim().toIntOrNull()
        if (startNode == null || endNode == null) {
            errors += "第 $row 行：节次必须填数字"
            return@forEachIndexed
        }
        if (startNode !in MIN_SLOT_NODE..MAX_SLOT_NODE || endNode !in MIN_SLOT_NODE..MAX_SLOT_NODE) {
            errors += "第 $row 行：节次必须在 $MIN_SLOT_NODE-$MAX_SLOT_NODE 之间"
            return@forEachIndexed
        }
        if (startNode > endNode) {
            errors += "第 $row 行：起始节不能大于结束节"
            return@forEachIndexed
        }
        val startTime = normalizeTimeOrNull(draft.startTime)
        val endTime = normalizeTimeOrNull(draft.endTime)
        if (startTime == null || endTime == null) {
            errors += "第 $row 行：时间格式应为 HH:mm"
            return@forEachIndexed
        }
        if (LocalTime.parse(startTime) >= LocalTime.parse(endTime)) {
            errors += "第 $row 行：开始时间必须早于结束时间"
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
            errors += "节次区间重叠：第 ${prev.startNode}-${prev.endNode} 节与第 ${current.startNode}-${current.endNode} 节"
        }
    }

    return if (errors.isEmpty()) {
        TimingDraftResult(sorted, emptyList())
    } else {
        TimingDraftResult(emptyList(), errors)
    }
}

/** 内置作息模板。名称点明制式，summary 说明这是示例、需按本校作息调整。 */
fun timingTemplates(): List<TimingTemplate> = listOf(
    TimingTemplate(
        id = "single_11",
        name = "单节制 · 11 节",
        summary = "上午 4 节、下午 4 节、晚上 3 节，示例时间，请按本校作息调整",
        slots = listOf(
            ClassSlotTime(1, 1, "08:00", "08:45", "第1节"),
            ClassSlotTime(2, 2, "08:55", "09:40", "第2节"),
            ClassSlotTime(3, 3, "10:00", "10:45", "第3节"),
            ClassSlotTime(4, 4, "10:55", "11:40", "第4节"),
            ClassSlotTime(5, 5, "14:00", "14:45", "第5节"),
            ClassSlotTime(6, 6, "14:55", "15:40", "第6节"),
            ClassSlotTime(7, 7, "16:00", "16:45", "第7节"),
            ClassSlotTime(8, 8, "16:55", "17:40", "第8节"),
            ClassSlotTime(9, 9, "19:00", "19:45", "第9节"),
            ClassSlotTime(10, 10, "19:55", "20:40", "第10节"),
            ClassSlotTime(11, 11, "20:50", "21:35", "第11节"),
        ),
    ),
    TimingTemplate(
        id = "block_5",
        name = "大节制 · 5 大节",
        summary = "每大节连排 2 小节，示例时间，请按本校作息调整",
        slots = listOf(
            ClassSlotTime(1, 2, "08:00", "09:40", "第一大节"),
            ClassSlotTime(3, 4, "10:00", "11:40", "第二大节"),
            ClassSlotTime(5, 6, "14:00", "15:40", "第三大节"),
            ClassSlotTime(7, 8, "16:00", "17:40", "第四大节"),
            ClassSlotTime(9, 10, "19:00", "20:40", "第五大节"),
        ),
    ),
    TimingTemplate(
        id = "single_8",
        name = "单节制 · 8 节（无晚课）",
        summary = "上午 4 节、下午 4 节，示例时间，请按本校作息调整",
        slots = listOf(
            ClassSlotTime(1, 1, "08:00", "08:45", "第1节"),
            ClassSlotTime(2, 2, "08:55", "09:40", "第2节"),
            ClassSlotTime(3, 3, "10:00", "10:45", "第3节"),
            ClassSlotTime(4, 4, "10:55", "11:40", "第4节"),
            ClassSlotTime(5, 5, "14:00", "14:45", "第5节"),
            ClassSlotTime(6, 6, "14:55", "15:40", "第6节"),
            ClassSlotTime(7, 7, "16:00", "16:45", "第7节"),
            ClassSlotTime(8, 8, "16:55", "17:40", "第8节"),
        ),
    ),
)

/** 编辑界面回填模板时，把模型转换成可继续修改的输入行。 */
fun ClassSlotTime.toDraftInput(): SlotDraftInput = SlotDraftInput(
    startNode = startNode.toString(),
    endNode = endNode.toString(),
    startTime = startTime,
    endTime = endTime,
    label = label,
)
