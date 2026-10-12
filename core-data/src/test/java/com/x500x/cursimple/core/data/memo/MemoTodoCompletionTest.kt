package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import org.junit.Assert.*
import org.junit.Test

class MemoTodoCompletionTest {
    private val note = MemoNote("todo", title = "实验报告", body = "说明\n- [ ] 整理报告\n  * [ ] 提交附件",
        courseKey = "lab", pinned = true, priority = MemoPriority.High, dueAt = "2026-10-10T23:59", createdAt = 10)
    private val revision = memoTodoRevision(note.body)

    @Test fun `checklist completion updates only the selected item and edit time`() {
        val completed = completedMemoTodo(note, 1, revision, 20)!!
        assertEquals(note.copy(body = "说明\n- [x] 整理报告\n  * [ ] 提交附件", updatedAt = 20), completed)
        assertFalse(completed.completed)
    }

    @Test fun `plain task marks the note complete without changing its text`() {
        val plain = note.copy(body = "完成实验报告")
        assertEquals(plain.copy(completed = true, updatedAt = 20),
            completedMemoTodo(plain, null, memoTodoRevision(plain.body), 20))
    }

    @Test fun `repeated completion does not uncheck the task`() {
        val completed = completedMemoTodo(note, 1, revision, 20)!!
        assertNull(completedMemoTodo(completed, 1, revision, 30))
        assertNull(completedMemoTodo(note.copy(completed = true), 1, revision, 30))
    }

    @Test fun `other tasks can be checked before the widget refresh finishes`() {
        val first = completedMemoTodo(note, 1, revision, 20)!!
        val second = completedMemoTodo(first, 2, revision, 30)!!
        assertEquals(2, second.checklist.done)
        assertEquals(revision, memoTodoRevision(second.body))
    }

    @Test fun `shifted reordered and edited checklists reject stale actions`() {
        listOf("新说明\n${note.body}", "说明\n  * [ ] 提交附件\n- [ ] 整理报告", note.body.replace("报告", "资料"))
            .forEach { assertNull(completedMemoTodo(note.copy(body = it), 1, revision, 20)) }
    }

    @Test fun `invalid targets and formerly plain notes cannot complete a whole checklist`() {
        assertNull(completedMemoTodo(note, 0, revision, 20))
        assertNull(completedMemoTodo(note, -1, revision, 20))
        assertNull(completedMemoTodo(note, 99, revision, 20))
        assertNull(completedMemoTodo(note, null, revision, 20))
    }

    @Test fun `newer metadata is preserved when completing a displayed task`() {
        val latest = note.copy(title = "改后的标题", priority = MemoPriority.Low, pinned = false)
        assertEquals(latest.copy(body = "说明\n- [x] 整理报告\n  * [ ] 提交附件", updatedAt = 30),
            completedMemoTodo(latest, 1, revision, 30))
    }
}
