package com.x500x.cursimple.feature.plugin.home

import com.x500x.cursimple.feature.plugin.homeBadgeColumn
import com.x500x.cursimple.feature.plugin.homeFilterColumns
import com.x500x.cursimple.feature.plugin.homeFilterValues
import com.x500x.cursimple.feature.plugin.homeRowMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHomeFilterTest {
    private val section = PluginHomeSection(
        id = "grades",
        columns = listOf("课程名称", "总评成绩", "学期", "学分"),
        rows = (1..6).map { listOf("课程$it", "${70 + it}", if (it <= 3) "2024-1" else "2024-2", if (it % 2 == 0) "2" else "3") },
    )

    @Test fun scoreColumnsAreBadgesNotFilters() {
        assertEquals(1, homeBadgeColumn(section))
        assertTrue(1 !in homeFilterColumns(section))
    }

    @Test fun termColumnIsPreferredAndListsItsValuesInOrder() {
        val columns = homeFilterColumns(section)
        assertEquals(2, columns.first())
        assertEquals(listOf("2024-1", "2024-2"), homeFilterValues(section, 2))
    }

    @Test fun traditionalScoreHeadersAreBadgesToo() {
        val traditional = section.copy(columns = listOf("課程名稱", "總評成績", "學期", "學分"))
        assertEquals(1, homeBadgeColumn(traditional))
        assertTrue(1 !in homeFilterColumns(traditional))
    }

    @Test fun tooFewRowsHaveNoFilters() {
        assertTrue(homeFilterColumns(section.copy(rows = section.rows.take(3))).isEmpty())
    }

    @Test fun searchAndFilterCombine() {
        val row = section.rows[0]
        assertTrue(homeRowMatches(row, "", null, null))
        assertTrue(homeRowMatches(row, "课程1", 2, "2024-1"))
        assertFalse(homeRowMatches(row, "课程1", 2, "2024-2"))
        assertFalse(homeRowMatches(row, "不存在", null, null))
    }
}
