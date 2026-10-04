package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.memoComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MemoSearchTest {
    @Test
    fun matchesChineseAndEnglishTitlesIgnoringCaseAndOuterWhitespace() {
        val note = MemoNote(id = "title", title = "线性代数 Linear ALGEBRA")
        for (query in listOf("线性", "aLgEbRa", "  linear  ")) {
            assertEquals(listOf(note), searchMemoNotes(listOf(note), query))
        }
        assertTrue(searchMemoNotes(listOf(note), "微积分").isEmpty())
    }

    @Test
    fun searchesWholeBodyIncludingCheckedAndUncheckedMarkdownAndHiddenPreviewLines() {
        val note = MemoNote(
            id = "body",
            body = "- [ ] 提交报告\n- [x] Review LAB\n" +
                (1..12).joinToString("\n") { "第 $it 行" } + "\n**附录答案**",
        )
        for (query in listOf("报告", "review lab", "附录答案")) {
            assertEquals(listOf(note), searchMemoNotes(listOf(note), query))
        }
        assertEquals(1, note.checklist.done)
        assertEquals(2, note.checklist.total)
    }

    @Test
    fun matchesSavedCourseTitlesAndOrphanCourseKeys() {
        val saved = MemoNote(id = "saved", courseKey = "高等数学", courseTitle = "高等数学 Calculus")
        val orphan = MemoNote(id = "orphan", courseKey = "OLD physics")
        assertEquals(listOf(saved), searchMemoNotes(listOf(saved, orphan), "CALCULUS"))
        assertEquals(listOf(saved), searchMemoNotes(listOf(saved, orphan), "高等"))
        assertEquals(listOf(orphan), searchMemoNotes(listOf(saved, orphan), "PHYSICS"))
    }

    @Test
    fun matchesCurrentCourseMetadataAndOtherNotebookLabel() {
        val course = MemoNote(id = "course", courseKey = "math")
        val other = MemoNote(id = "other")
        val metadata = mapOf("math" to "Mathematics · 王老师 · Room 216C", null to "其他")
        for (query in listOf("mathematics", "王老师", "216c")) {
            assertEquals(listOf(course), searchMemoNotes(listOf(course, other), query, courseSearchText = metadata))
        }
        assertEquals(listOf(other), searchMemoNotes(listOf(course, other), "其他", courseSearchText = metadata))
    }

    @Test
    fun nonBlankSearchIncludesCompletedAndUnselectedNotebooksDespiteCurrentFilter() {
        val selected = MemoNote(id = "selected", title = "report", courseKey = "math")
        val unselected = MemoNote(id = "unselected", title = "REPORT", courseKey = "physics")
        val completed = MemoNote(id = "completed", title = "Report", courseKey = "history", completed = true)
        val all = listOf(selected, unselected, completed)
        val current = listOf(selected)
        assertEquals(all, searchMemoNotes(all, "report", currentMemos = current))
        assertEquals(listOf(selected), current)
        assertTrue(completed.completed)
    }

    @Test
    fun emptyOrWhitespaceQueryRestoresExactCurrentFilteredList() {
        val all = listOf(MemoNote(id = "one"), MemoNote(id = "two", completed = true))
        val current = listOf(all[0])
        for (query in listOf("", "   ", "\n\t")) {
            assertSame(current, searchMemoNotes(all, query, currentMemos = current))
            assertSame(all, searchMemoNotes(all, query))
            assertTrue(searchMemoNotes(all, query, currentMemos = emptyList()).isEmpty())
        }
    }

    @Test
    fun multipleWordsMatchOneContiguousSubstringWithoutFuzzyOrCrossFieldMatching() {
        val phrase = MemoNote(id = "phrase", title = "Prepare Linear Algebra report")
        val separated = MemoNote(id = "separated", title = "Linear", body = "Algebra")
        val interrupted = MemoNote(id = "interrupted", title = "Linear and Algebra")
        val all = listOf(phrase, separated, interrupted)
        assertEquals(listOf(phrase), searchMemoNotes(all, "linear algebra"))
        assertTrue(searchMemoNotes(all, "algebra linear").isEmpty())
        assertTrue(searchMemoNotes(listOf(MemoNote(id = "cn", title = "数值分析")), "数分").isEmpty())
    }

    @Test
    fun preservesExistingOrderAndNoteIdentityIncludingCompletedDisplay() {
        val now = LocalDateTime.of(2026, 10, 4, 12, 0)
        val all = listOf(
            MemoNote(id = "done", title = "report", completed = true, pinned = true, updatedAt = 100),
            MemoNote(id = "ordinary", title = "report", updatedAt = 30),
            MemoNote(id = "pinned", title = "report", pinned = true, updatedAt = 10),
            MemoNote(id = "irrelevant", title = "lecture", pinned = true),
            MemoNote(id = "due", title = "report", dueAt = "2026-10-04T13:00"),
            MemoNote(id = "priority", title = "report", priority = MemoPriority.High),
        ).sortedWith(memoComparator(now))
        val results = searchMemoNotes(all, "report")
        assertEquals(listOf("pinned", "due", "priority", "ordinary", "done"), results.map { it.id })
        results.forEach { result -> assertSame(all.first { it.id == result.id }, result) }
        assertEquals(listOf("done"), results.filter { it.completed }.map { it.id })
    }
}
