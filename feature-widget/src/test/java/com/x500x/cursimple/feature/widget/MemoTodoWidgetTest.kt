package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.MemoNote
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MemoTodoWidgetTest {
    private val now = LocalDateTime.parse("2026-10-05T10:00")

    @Test fun `unfinished checklist lines retain their source note and line index`() {
        val note = MemoNote("note", title = "Lab", body = "Introduction\n- [x] Done\n- [ ] Upload report\n  * [ ] Review")
        val rows = memoTodoRows(listOf(note), now)
        assertEquals(listOf("Upload report", "Review"), rows.map { it.title })
        assertEquals(listOf(2, 3), rows.map { it.lineIndex })
        assertTrue(rows.all { it.note.id == note.id })
        assertNotEquals(rows[0].stableId, rows[1].stableId)
    }

    @Test fun `completed notes checked lists and blank notes disappear`() {
        val rows = memoTodoRows(listOf(MemoNote("blank"),
            MemoNote("completed", title = "Old", completed = true, body = "- [ ] Task"),
            MemoNote("checked", title = "Done", body = "- [x] Done"),
            MemoNote("plain", title = "Write summary")), now)
        assertEquals(listOf("Write summary"), rows.map { it.title })
    }

    @Test fun `pinned notes and due dates preserve memo ordering`() {
        val rows = memoTodoRows(listOf(MemoNote("later", title = "Later", dueAt = "2026-10-09T12:00"),
            MemoNote("overdue", title = "Overdue", dueAt = "2026-10-04T12:00"),
            MemoNote("pin", title = "Pinned", pinned = true)), now)
        assertEquals(listOf("pin", "overdue", "later"), rows.map { it.note.id })
    }
}
