package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * 备忘录里的一条笔记。
 *
 * 按课名挂到一门课上（[courseKey]），同一门课一周上几次也只有一个笔记本；
 * 换学期、重新导课，课名不变笔记就还在。[courseKey] 为空的是不属于哪门课的「其他」。
 *
 * 复选框直接写在正文里（`- [ ] 交实验报告` / `- [x] 预习第三章`），不单独存一份清单：
 * 编辑时就是一个文本框，卡片上点一下就改回正文，进度从正文里数出来。
 */
@Serializable
data class MemoNote(
    @SerialName("id") val id: String,
    @SerialName("courseKey") val courseKey: String? = null,
    /** 写下这条时的课名；课被删了、改名了，笔记本标题仍有东西可显示 */
    @SerialName("courseTitle") val courseTitle: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("priority") val priority: MemoPriority = MemoPriority.None,
    @SerialName("pinned") val pinned: Boolean = false,
    /** 「2026-10-01T23:59」，按应用时区的本地时间；为空表示没有截止时间 */
    @SerialName("dueAt") val dueAt: String? = null,
    /** 整条标记完成：卡片变淡、沉到底，截止时间也不再催 */
    @SerialName("completed") val completed: Boolean = false,
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
) {
    val dueDateTime: LocalDateTime?
        get() = dueAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    val checklist: ChecklistProgress get() = checklistProgress(body)

    /** 标题和正文都空着的笔记没有保存的意义 */
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
}

@Serializable
enum class MemoPriority(val rank: Int) {
    @SerialName("none") None(0),
    @SerialName("low") Low(1),
    @SerialName("medium") Medium(2),
    @SerialName("high") High(3),
}

data class ChecklistProgress(val done: Int, val total: Int) {
    val hasItems: Boolean get() = total > 0
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** 正文里的一行复选框：`- [ ] 文字`，前面可以有缩进，`-` 也可以是 `*`，勾上是 `x` 或 `X`。 */
private val CHECKBOX_LINE = Regex("""^(\s*[-*]\s+\[)([ xX])(]\s?)(.*)$""")

/** 这一行是不是复选框；是的话给出是否勾上和后面的文字。 */
fun parseChecklistLine(line: String): Pair<Boolean, String>? {
    val match = CHECKBOX_LINE.matchEntire(line) ?: return null
    return (match.groupValues[2] != " ") to match.groupValues[4]
}

fun checklistProgress(body: String): ChecklistProgress {
    var done = 0
    var total = 0
    body.lineSequence().forEach { line ->
        val parsed = parseChecklistLine(line) ?: return@forEach
        total++
        if (parsed.first) done++
    }
    return ChecklistProgress(done, total)
}

/** 把正文第 [lineIndex] 行的复选框勾上或取消；那一行不是复选框就原样返回。 */
fun toggleChecklistLine(body: String, lineIndex: Int): String {
    val lines = body.split('\n').toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return body
    val match = CHECKBOX_LINE.matchEntire(line) ?: return body
    val checked = match.groupValues[2] != " "
    lines[lineIndex] = match.groupValues[1] + (if (checked) " " else "x") + match.groupValues[3] + match.groupValues[4]
    return lines.joinToString("\n")
}

/** 笔记本按课名归：去掉首尾空白、大小写一致，「高等数学 」和「高等数学」是同一本。 */
fun memoCourseKey(courseTitle: String): String = courseTitle.trim().lowercase()

/** 截止时间离现在多远，卡片上据此换颜色和说法。 */
enum class MemoDueState { None, Overdue, Today, Soon, Later }

fun MemoNote.dueState(now: LocalDateTime): MemoDueState {
    val due = dueDateTime ?: return MemoDueState.None
    if (completed) return MemoDueState.Later
    return when {
        due.isBefore(now) -> MemoDueState.Overdue
        due.toLocalDate() == now.toLocalDate() -> MemoDueState.Today
        due.isBefore(now.plusDays(3)) -> MemoDueState.Soon
        else -> MemoDueState.Later
    }
}

/**
 * 笔记的先后：置顶的在最前；完成的沉底；
 * 中间先看截止时间（逾期的、快到的往前），再看优先级，最后看最近改过的。
 */
fun memoComparator(now: LocalDateTime): Comparator<MemoNote> =
    compareBy<MemoNote> { it.completed }
        .thenByDescending { it.pinned && !it.completed }
        .thenBy { note ->
            when (note.dueState(now)) {
                MemoDueState.Overdue -> 0
                MemoDueState.Today -> 1
                MemoDueState.Soon -> 2
                else -> 3
            }
        }
        .thenBy { if (it.completed) null else it.dueDateTime ?: LocalDateTime.MAX }
        .thenByDescending { it.priority.rank }
        .thenByDescending { it.updatedAt }
