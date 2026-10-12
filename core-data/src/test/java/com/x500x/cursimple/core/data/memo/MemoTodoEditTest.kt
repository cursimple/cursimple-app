package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import org.junit.Assert.*
import org.junit.Test

class MemoTodoEditTest {
    private val note = MemoNote("n", title = "实验", body = "说明\n  * [ ] 提交报告\n- [x] 完成测试", pinned = true)
    private val draft = MemoTodoEditDraft.from(note, 1)!!

    @Test fun `editing a task keeps its marker indentation and other lines`() {
        val saved = editedMemoTodo(note, note, 1, draft.copy(text = "提交报告和附件"), 20)!!
        assertEquals(note.copy(body = "说明\n  * [ ] 提交报告和附件\n- [x] 完成测试", updatedAt = 20), saved)
    }

    @Test fun `editing preserves a concurrent completion and metadata change`() {
        val latest = note.copy(body = note.body.replace("[ ]", "[x]"), pinned = false)
        val saved = editedMemoTodo(latest, note, 1, draft.copy(text = "提交附件", priority = MemoPriority.High), 20)!!
        assertEquals("说明\n  * [x] 提交附件\n- [x] 完成测试", saved.body)
        assertFalse(saved.pinned)
        assertEquals(MemoPriority.High, saved.priority)
    }

    @Test fun `concurrent checklist edits reject obsolete line indices`() {
        assertNull(editedMemoTodo(note.copy(body = "新行\n${note.body}"), note, 1, draft.copy(text = "改标题"), 20))
        assertNull(editedMemoTodo(note.copy(body = note.body.replace("报告", "资料")), note, 1, draft.copy(text = "改标题"), 20))
    }

    @Test fun `conflicting fields are not overwritten`() {
        assertNull(editedMemoTodo(note.copy(priority = MemoPriority.Low), note, 1,
            draft.copy(priority = MemoPriority.High), 20))
    }

    @Test fun `plain notes can edit title content and deadline without clearing completion`() {
        val plain = note.copy(body = "备注", completed = true)
        val edit = MemoTodoEditDraft.from(plain, null)!!.copy(text = "新标题", body = "新内容", dueAt = "2026-10-12T09:30")
        assertEquals(plain.copy(title = "新标题", body = "新内容", dueAt = "2026-10-12T09:30", updatedAt = 20),
            editedMemoTodo(plain, plain, null, edit, 20))
    }

    @Test fun `moving a note to a selected course preserves its task progress`() {
        val saved = editedMemoTodo(note, note, 1, draft.copy(courseKey = "physics", courseTitle = "大学物理"), 20)!!
        assertEquals("physics", saved.courseKey)
        assertEquals("大学物理", saved.courseTitle)
        assertEquals(note.body, saved.body)
    }

    @Test fun `course identity and display name are merged as one association`() {
        val original = note.copy(courseKey = "physics", courseTitle = "物理")
        val edit = MemoTodoEditDraft.from(original, 1)!!.copy(courseTitle = "大学物理")
        assertNull(editedMemoTodo(original.copy(courseKey = "math", courseTitle = "物理"), original, 1, edit, 20))
    }

    @Test fun `mini editor saves structured lists and checklist progress together`() {
        val edit = MemoTodoEditDraft.from(note, null)!!.copy(body = "说明\n- [x] 提交报告\n- [x] 完成测试\n1. 带资料\n2. 带电脑")
        val saved = editedMemoTodo(note, note, null, edit, 20)!!
        assertEquals(edit.body, saved.body)
        assertEquals(note.title, saved.title)
        assertEquals(note.id, saved.id)
        assertTrue(saved.pinned)
    }

    @Test fun `full mini editing never silently overwrites concurrent body changes`() {
        val edit = MemoTodoEditDraft.from(note, null)!!.copy(body = note.body + "\n新列表")
        val latest = note.copy(body = note.body.replace("[ ]", "[x]"))
        assertNull(editedMemoTodo(latest, note, null, edit, 20))
        val metadataOnly = MemoTodoEditDraft.from(note, null)!!.copy(priority = MemoPriority.High)
        assertEquals(latest.body, editedMemoTodo(latest, note, null, metadataOnly, 20)!!.body)
    }
}
