package com.x500x.cursimple.core.data.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TermProfileTest {

    private val terms = listOf(
        TermProfile(id = "a", name = "2025 秋", termStartDate = "2025-09-01", createdAt = 1L),
        TermProfile(id = "b", name = "2026 春", termStartDate = "2026-02-23", createdAt = 2L),
        TermProfile(id = "c", name = "小学期", termStartDate = null, createdAt = 3L),
    )

    @Test
    fun `term start date follows the active term instead of the first one`() {
        val remaining = terms.filterNot { it.id == "c" }

        assertEquals("2026-02-23", remaining.termStartDateIsoOf("b"))
        assertEquals("2025-09-01", remaining.termStartDateIsoOf("a"))
    }

    @Test
    fun `active term without a start date mirrors no date`() {
        assertNull(terms.termStartDateIsoOf("c"))
    }

    @Test
    fun `unknown active term id mirrors no date`() {
        assertNull(terms.termStartDateIsoOf(""))
        assertNull(terms.termStartDateIsoOf("missing"))
    }
}
