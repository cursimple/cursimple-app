package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.io.encoding.Base64

class MemoImageTransferTest {
    private val png = Base64.Default.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")
    private val image = MemoTransferImage.from(png)
    private val note = MemoNote("with-image", courseTitle = "物理", body = MemoImageRef.markdown(image.id, "实验图"))

    @Test fun `notes files and share codes include image bytes and preserve course matching`() {
        val bundle = MemoTransferCodec.decode(MemoTransferCodec.encode(listOf(note), listOf(image)))
        assertEquals(2, bundle.version)
        assertArrayEquals(png, bundle.images.single().bytes())
        val courses = listOf(CourseItem("local", "物理", time = CourseTimeSlot(2, 1, 2)))
        val imported = MemoTransferCodec.prepare(bundle, courses, emptyList(), 123L).notes.single()
        assertEquals(memoCourseKey("物理"), imported.courseKey)
        assertEquals(note.body, imported.body)
        val code = MemoShareCode.encode(listOf(note), listOf(image))
        assertArrayEquals(png, MemoShareCode.decode(code).images.single().bytes())
    }
    @Test fun `unreferenced missing duplicate or tampered attachments are rejected`() {
        assertTrue(runCatching { MemoTransferCodec.encode(listOf(note)) }.isFailure)
        assertTrue(runCatching { MemoTransferCodec.encode(listOf(note.copy(body = "text")), listOf(image)) }.isFailure)
        assertTrue(runCatching { MemoTransferCodec.encode(listOf(note), listOf(image, image)) }.isFailure)
        assertTrue(runCatching { MemoTransferCodec.encode(listOf(note), listOf(image.copy(id = "f".repeat(64)))) }.isFailure)
    }
    @Test fun `new format retains support for old text notes`() {
        val old = """{"format":"cursimple.notes","version":1,"notes":[{"id":"old","title":"Old note"}]}"""
        assertTrue(MemoTransferCodec.decode(old.toByteArray()).images.isEmpty())
        assertEquals(1, MemoTransferCodec.decode(MemoTransferCodec.encode(listOf(note.copy(body = "text")))).version)
    }
    @Test fun `a corrupt image type is rejected even with the correct content hash`() {
        val bytes = "Not an image".toByteArray()
        val fake = MemoTransferImage(MemoTransferImage.imageId(bytes), Base64.Default.encode(bytes))
        assertTrue(runCatching { fake.bytes() }.isFailure)
    }
    @Test fun `backup serialization retains image attachments and accepts old backups`() {
        val payload = com.x500x.cursimple.core.data.AppBackupPayload(2, stores = emptyList(), memoImages = listOf(image))
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString(com.x500x.cursimple.core.data.AppBackupPayload.serializer(),
            json.encodeToString(com.x500x.cursimple.core.data.AppBackupPayload.serializer(), payload))
        assertArrayEquals(png, restored.memoImages.single().bytes())
        assertTrue(json.decodeFromString(com.x500x.cursimple.core.data.AppBackupPayload.serializer(), """{"version":1,"stores":[]}""").memoImages.isEmpty())
    }
    @Test fun `backup images are selected from the frozen snapshot rather than later edits`() {
        val json = Json { encodeDefaults = true }
        val content = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MemoNote.serializer()), listOf(note))
        val snapshot = com.x500x.cursimple.core.data.PreferencesStoreSnapshot(com.x500x.cursimple.core.data.AppBackupStores.MEMOS,
            listOf(com.x500x.cursimple.core.data.PreferencesBackupEntry("memo_notes_json", com.x500x.cursimple.core.data.PreferencesBackupValueType.String, stringValue = content)))
        assertEquals(setOf(image.id), memoNotesInBackup(snapshot).flatMap { MemoImageRef.ids(it.body) }.toSet())
        assertTrue(memoNotesInBackup(null).isEmpty())
    }
}
