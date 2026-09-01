package com.x500x.cursimple.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ImportExportScreenTest {
    @Test
    fun `parses iso term start date`() {
        assertEquals(LocalDate.of(2026, 2, 23), parseImportedTermStartDate("2026-02-23"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(LocalDate.of(2026, 2, 23), parseImportedTermStartDate("  2026-02-23\n"))
    }

    @Test
    fun `missing value keeps current setting`() {
        assertNull(parseImportedTermStartDate(null))
        assertNull(parseImportedTermStartDate(""))
        assertNull(parseImportedTermStartDate("   "))
    }

    @Test
    fun `malformed value is skipped instead of throwing`() {
        assertNull(parseImportedTermStartDate("2026/02/23"))
        assertNull(parseImportedTermStartDate("2026-13-01"))
        assertNull(parseImportedTermStartDate("2026-02-30"))
        assertNull(parseImportedTermStartDate("下周一"))
        assertNull(parseImportedTermStartDate("2026-02-23T08:00:00"))
    }
}
