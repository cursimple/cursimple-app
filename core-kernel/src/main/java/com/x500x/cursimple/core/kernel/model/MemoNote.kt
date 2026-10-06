package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Associate notebooks by [courseKey] across reimports; null denotes general notes. Checklists
 * remain Markdown in the body and are updated there directly.
 */
@Serializable
data class MemoNote(
    @SerialName("id") val id: String,
    @SerialName("courseKey") val courseKey: String? = null,
    @SerialName("courseTitle") val courseTitle: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("priority") val priority: MemoPriority = MemoPriority.None,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("dueAt") val dueAt: String? = null,
    @SerialName("completed") val completed: Boolean = false,
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
) {
    val dueDateTime: LocalDateTime?
        get() = dueAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    val checklist: ChecklistProgress get() = checklistProgress(body)

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

/** Markdown checkbox line; accepts indentation, dash or star bullets and either-case x. */
private val CHECKBOX_LINE = Regex("""^(\s*[-*]\s+\[)([ xX])(]\s?)(.*)$""")

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

/** Toggle the checkbox at [lineIndex]; preserve non-checkbox lines. */
fun toggleChecklistLine(body: String, lineIndex: Int): String {
    val lines = body.split('\n').toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return body
    val match = CHECKBOX_LINE.matchEntire(line) ?: return body
    val checked = match.groupValues[2] != " "
    lines[lineIndex] = match.groupValues[1] + (if (checked) " " else "x") + match.groupValues[3] + match.groupValues[4]
    return lines.joinToString("\n")
}

fun memoCourseKey(courseTitle: String): String = courseTitle.trim().lowercase()

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

/** Pinned first, completed last; remaining notes sort by deadline, priority and latest edit. */
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
