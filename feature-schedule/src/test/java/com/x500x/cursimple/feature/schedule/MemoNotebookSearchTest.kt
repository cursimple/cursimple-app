package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoNotebookSearchTest {

    private fun nb(title: String, subtitle: String = "") =
        MemoNotebook(key = title, title = title, subtitle = subtitle, orphan = false)

    @Test
    fun matchesTitleTeacherAndRoom() {
        val course = nb("数值分析", "王少芳 · 东13-C-216c")
        assertTrue(memoNotebookMatches(course, ""))
        assertTrue(memoNotebookMatches(course, "分析"))
        assertTrue(memoNotebookMatches(course, "王少芳"))
        assertTrue(memoNotebookMatches(course, "216C"))
        assertFalse(memoNotebookMatches(course, "线代"))
    }

    @Test
    fun matchesInOrderAbbreviations() {
        assertTrue(memoNotebookMatches(nb("数值分析"), "数分"))
        assertTrue(memoNotebookMatches(nb("汇编语言与微型计算机技术"), "汇微"))
        assertTrue(memoNotebookMatches(nb("MATLAB应用"), "mat 应用"))
        // Abbreviation order matters; single characters use substring matching only.
        assertFalse(memoNotebookMatches(nb("数值分析"), "分数"))
        assertFalse(memoNotebookMatches(nb("数值分析"), "计"))
    }
}
