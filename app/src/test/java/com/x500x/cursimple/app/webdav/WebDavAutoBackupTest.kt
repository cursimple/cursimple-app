package com.x500x.cursimple.app.webdav

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WebDavAutoBackupTest {
    private fun file(name: String) = WebDavBackupFile(name, "https://dav.example/$name", 1L, null)

    @Test
    fun onlyAutomaticBackupsBeyondTheNewestKeepAreDeleted() {
        val files = listOf(
            file("cursimple-backup-auto-20261001-020000.json"),
            file("cursimple-backup-auto-20261008-020000.json"),
            file("cursimple-backup-auto-20261015-020000.json"),
            file("cursimple-backup-auto-20261022-020000.json"),
            file("cursimple-backup-20260901-101010.json"),
            file("notes.json"),
        )
        val doomed = automaticBackupsToDelete(files, keep = 2).map { it.name }
        assertEquals(listOf("cursimple-backup-auto-20261008-020000.json", "cursimple-backup-auto-20261001-020000.json"), doomed)
    }

    @Test
    fun keepingMoreThanExistNeverDeletesAnything() {
        val files = listOf(file("cursimple-backup-auto-20261001-020000.json"))
        assertEquals(emptyList<WebDavBackupFile>(), automaticBackupsToDelete(files, keep = 5))
    }

    @Test
    fun theKeepCountNeverDropsBelowOne() {
        val files = listOf(file("cursimple-backup-auto-20261001-020000.json"), file("cursimple-backup-auto-20261008-020000.json"))
        assertEquals(1, automaticBackupsToDelete(files, keep = 0).size)
    }

    @Test
    fun automaticFileNamesSortByTimeAndCarryTheirOwnPrefix() {
        val earlier = autoBackupFileName(LocalDateTime.of(2026, 10, 1, 2, 0, 0))
        val later = autoBackupFileName(LocalDateTime.of(2026, 10, 8, 2, 0, 0))
        assertEquals("cursimple-backup-auto-20261001-020000.json", earlier)
        assert(later > earlier)
    }
}
