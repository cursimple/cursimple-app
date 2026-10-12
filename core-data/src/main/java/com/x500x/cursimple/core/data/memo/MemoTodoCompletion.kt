package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.model.toggleChecklistLine
import java.security.MessageDigest

/** A small revision token keeps widget intents independent of note length. */
fun memoTodoRevision(body: String): String {
    // Checking another item does not invalidate the remaining task actions.
    val unchecked = body.lines().map { line ->
        if (parseChecklistLine(line)?.first == true) toggleChecklistLine(line, 0) else line
    }.joinToString("\n")
    return MessageDigest.getInstance("SHA-256")
        .digest(unchecked.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Complete the rendered task once; never apply a stale line index to an edited checklist. */
internal fun completedMemoTodo(
    note: MemoNote,
    lineIndex: Int?,
    expectedRevision: String,
    nowMillis: Long,
): MemoNote? {
    if (note.completed || memoTodoRevision(note.body) != expectedRevision) return null
    if (lineIndex == null) {
        if (note.checklist.hasItems) return null
        return note.copy(completed = true, updatedAt = nowMillis)
    }
    val line = note.body.lines().getOrNull(lineIndex) ?: return null
    val item = parseChecklistLine(line) ?: return null
    if (item.first) return null
    return note.copy(body = toggleChecklistLine(note.body, lineIndex), updatedAt = nowMillis)
}
