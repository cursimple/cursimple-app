package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.AppBackupPayload
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalBackupArchiveTest {
    private val payload = AppBackupPayload(
        version = AppBackupPayload.CURRENT_VERSION,
        createdAt = 1750000000000L,
        stores = listOf(
            PreferencesStoreSnapshot(AppBackupStores.USER_PREFERENCES, emptyList()),
            PreferencesStoreSnapshot(AppBackupStores.MEMOS, emptyList()),
        ),
    )

    @Test
    fun `round trip keeps payload and manifest`() {
        val decoded = LocalBackupArchive.decode(LocalBackupArchive.encode(payload))

        assertEquals(payload, decoded.payload)
        assertEquals(1, decoded.manifest.formatVersion)
        assertEquals(setOf(AppBackupStores.USER_PREFERENCES, AppBackupStores.MEMOS), decoded.manifest.storeNames.toSet())
    }

    @Test
    fun `selecting stores exports only selected stores`() {
        val decoded = LocalBackupArchive.decode(
            LocalBackupArchive.encode(payload, setOf(AppBackupStores.MEMOS)),
        )

        assertEquals(listOf(AppBackupStores.MEMOS), decoded.manifest.storeNames)
        assertEquals(listOf(AppBackupStores.MEMOS), decoded.payload.stores.map { it.storeName })
    }

    @Test
    fun `rejects an archive with an unexpected entry`() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../../payload.json"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }.toByteArray()

        assertTrue(runCatching { LocalBackupArchive.decode(bytes) }.isFailure)
    }

    @Test
    fun `rejects an archive without the backup marker`() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(LocalBackupArchive.PAYLOAD_ENTRY))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertTrue(runCatching { LocalBackupArchive.decode(bytes) }.isFailure)
    }

    @Test
    fun `all selection includes every store in source order`() {
        val selection = LocalBackupSelection.all(
            listOf(AppBackupStores.MEMOS, AppBackupStores.SCHEDULE),
        )

        assertEquals(
            listOf(AppBackupStores.MEMOS, AppBackupStores.SCHEDULE),
            selection.selectedStoreNames,
        )
    }

    @Test
    fun `toggling a store removes it and toggling again restores it`() {
        val selection = LocalBackupSelection.all(listOf(AppBackupStores.MEMOS))

        assertEquals(
            emptyList<String>(),
            selection.toggle(AppBackupStores.MEMOS).selectedStoreNames,
        )
        assertEquals(
            listOf(AppBackupStores.MEMOS),
            selection.toggle(AppBackupStores.MEMOS).toggle(AppBackupStores.MEMOS)
                .selectedStoreNames,
        )
    }

    @Test
    fun `empty selection is rejected by archive export`() {
        assertTrue(
            runCatching {
                LocalBackupArchive.encode(payload, emptySet())
            }.isFailure,
        )
    }
}
