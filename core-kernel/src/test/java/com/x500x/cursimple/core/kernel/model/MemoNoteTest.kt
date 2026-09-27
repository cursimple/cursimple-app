package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MemoNoteTest {

    private val body = """
        # 期中复习
        - [ ] 第三章习题
        - [x] 整理错题
          * [X] 缩进的也算
        - 普通列表不算
        [ ] 没有前缀的不算
    """.trimIndent()

    @Test
    fun countsOnlyRealCheckboxes() {
        assertEquals(ChecklistProgress(done = 2, total = 3), checklistProgress(body))
    }

    @Test
    fun toggleFlipsOnlyThatLine() {
        val once = toggleChecklistLine(body, 1)
        assertEquals("- [x] 第三章习题", once.lines()[1])
        assertEquals(body.lines()[2], once.lines()[2])
        val twice = toggleChecklistLine(once, 1)
        assertEquals(body, twice)
        // 缩进和星号保留
        assertEquals("  * [ ] 缩进的也算", toggleChecklistLine(body, 3).lines()[3])
        // 不是复选框的行原样返回
        assertEquals(body, toggleChecklistLine(body, 4))
        assertEquals(body, toggleChecklistLine(body, 99))
    }

    @Test
    fun parseLine() {
        assertEquals(false to "买书", parseChecklistLine("- [ ] 买书"))
        assertEquals(true to "", parseChecklistLine("- [x]"))
        assertNull(parseChecklistLine("-[ ] 缺空格"))
    }

    @Test
    fun dueStates() {
        val now = LocalDateTime.of(2026, 9, 29, 10, 0)
        fun due(at: String?) = MemoNote(id = "n", dueAt = at).dueState(now)
        assertEquals(MemoDueState.None, due(null))
        assertEquals(MemoDueState.Overdue, due("2026-09-29T09:00"))
        assertEquals(MemoDueState.Today, due("2026-09-29T23:59"))
        assertEquals(MemoDueState.Soon, due("2026-10-01T12:00"))
        assertEquals(MemoDueState.Later, due("2026-10-10T12:00"))
        assertEquals(
            MemoDueState.Later,
            MemoNote(id = "n", dueAt = "2026-09-01T00:00", completed = true).dueState(now),
        )
    }

    @Test
    fun ordering_pinnedThenUrgentThenPriorityThenRecent() {
        val now = LocalDateTime.of(2026, 9, 29, 10, 0)
        val notes = listOf(
            MemoNote(id = "done", completed = true, pinned = true, updatedAt = 99),
            MemoNote(id = "old-high", priority = MemoPriority.High, updatedAt = 1),
            MemoNote(id = "new-low", priority = MemoPriority.Low, updatedAt = 50),
            MemoNote(id = "overdue", dueAt = "2026-09-28T12:00", updatedAt = 2),
            MemoNote(id = "soon", dueAt = "2026-09-30T12:00", updatedAt = 3),
            MemoNote(id = "pinned", pinned = true, updatedAt = 0),
        )
        assertEquals(
            listOf("pinned", "overdue", "soon", "old-high", "new-low", "done"),
            notes.sortedWith(memoComparator(now)).map { it.id },
        )
    }

    @Test
    fun courseKeyIgnoresCaseAndSpaces() {
        assertEquals(memoCourseKey("MATLAB应用"), memoCourseKey(" matlab应用 "))
    }
}
