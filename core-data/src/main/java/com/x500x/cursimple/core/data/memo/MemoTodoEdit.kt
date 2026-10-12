package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.parseChecklistLine

data class MemoTodoEditDraft(
    val text: String,
    val body: String,
    val priority: MemoPriority,
    val pinned: Boolean,
    val dueAt: String?,
    val courseKey: String? = null,
    val courseTitle: String = "",
) {
    companion object {
        fun from(note: MemoNote, lineIndex: Int?): MemoTodoEditDraft? {
            val text = if (lineIndex == null) note.title else
                note.body.lines().getOrNull(lineIndex)?.let(::parseChecklistLine)?.second ?: return null
            return MemoTodoEditDraft(text, note.body, note.priority, note.pinned, note.dueAt, note.courseKey, note.courseTitle)
        }
    }
}

enum class MemoTodoEditResult { Saved, Conflict, Missing }

/** Merge only edited fields; concurrent checkbox clicks and unrelated edits are preserved. */
internal fun editedMemoTodo(current: MemoNote, original: MemoNote, lineIndex: Int?, draft: MemoTodoEditDraft,
    nowMillis: Long): MemoNote? {
    if (current.id != original.id) return null
    val base = MemoTodoEditDraft.from(original, lineIndex) ?: return null
    fun <T> merge(latest: T, before: T, after: T): T = when {
        after == before -> latest
        latest == before || latest == after -> after
        else -> throw EditConflict()
    }
    return try {
        var title = current.title
        val body = if (lineIndex == null) {
            title = merge(current.title, base.text, draft.text.trim())
            merge(current.body, base.body, draft.body)
        } else if (draft.text.trim() == base.text) current.body else {
            if (draft.text.isBlank() || '\n' in draft.text || '\r' in draft.text ||
                memoTodoRevision(current.body) != memoTodoRevision(original.body)) return null
            val lines = current.body.lines().toMutableList()
            val line = lines.getOrNull(lineIndex) ?: return null
            val prefix = Regex("""^(\s*[-*]\s+\[[ xX]]\s?)(.*)$""").matchEntire(line)?.groupValues?.get(1)
                ?: return null
            lines[lineIndex] = prefix + draft.text.trim()
            lines.joinToString("\n")
        }
        if (title.isBlank() && body.isBlank()) return null
        val course = merge(current.courseKey to current.courseTitle, base.courseKey to base.courseTitle,
            draft.courseKey to draft.courseTitle)
        current.copy(title = title, body = body,
            priority = merge(current.priority, base.priority, draft.priority),
            pinned = merge(current.pinned, base.pinned, draft.pinned),
            dueAt = merge(current.dueAt, base.dueAt, draft.dueAt),
            courseKey = course.first, courseTitle = course.second, updatedAt = nowMillis)
    } catch (_: EditConflict) { null }
}

private class EditConflict : RuntimeException()
