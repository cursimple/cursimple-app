package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test

class MemoTransferTest {
    private val note = MemoNote("sender-note", courseKey = "foreign-id", courseTitle = "高等数学", title = "复习", body = "**重点**\n1. 习题", priority = MemoPriority.High, pinned = true)
    private val course = CourseItem("recipient-course", "高等数学", time = CourseTimeSlot(2, 1, 2))
    private fun bundle() = MemoTransferCodec.decode(MemoTransferCodec.encode(listOf(note)))

    @Test fun `recipient course name decides notebook and keeps note content`() {
        val result = MemoTransferCodec.prepare(bundle(), listOf(course), emptyList(), 123L)
        val imported = result.notes.single()
        assertEquals(memoCourseKey(course.title), imported.courseKey)
        assertEquals(note.body, imported.body)
        assertEquals(MemoPriority.High, imported.priority)
        assertTrue(imported.pinned)
        assertNotEquals(note.id, imported.id)
    }
    @Test fun `missing course preserves course notebook without assigning an unrelated course`() {
        val result = MemoTransferCodec.prepare(bundle(), listOf(course.copy(title = "大学物理")), emptyList(), 123L)
        assertEquals("高等数学", result.notes.single().courseTitle)
        assertEquals(memoCourseKey("高等数学"), result.notes.single().courseKey)
    }
    @Test fun `repeat imports preserve local edits and do not create duplicates`() {
        val imported = MemoTransferCodec.prepare(bundle(), listOf(course), emptyList(), 123L).notes.single()
        val edited = imported.copy(body = "本地修改")
        val repeat = MemoTransferCodec.prepare(bundle(), listOf(course), listOf(edited), 456L)
        assertTrue(repeat.notes.isEmpty())
        assertEquals(1, repeat.skipped)
        assertEquals("本地修改", edited.body)
    }
    @Test fun `a relayed unchanged note is recognized by its original sender`() {
        val imported = MemoTransferCodec.prepare(bundle(), listOf(course), emptyList(), 123L).notes.single()
        val relay = MemoTransferCodec.decode(MemoTransferCodec.encode(listOf(imported)))
        assertTrue(MemoTransferCodec.prepare(relay, listOf(course), listOf(note), 456L).notes.isEmpty())
    }
    @Test fun `sharing a changed version appends rather than overwrites`() {
        val old = MemoTransferCodec.prepare(bundle(), listOf(course), emptyList(), 123L).notes.single()
        val changed = MemoTransferCodec.decode(MemoTransferCodec.encode(listOf(note.copy(body = "新版"))))
        val result = MemoTransferCodec.prepare(changed, listOf(course), listOf(old), 456L)
        assertEquals("新版", result.notes.single().body)
        assertNotEquals(old.id, result.notes.single().id)
    }
    @Test fun `arbitrary JSON and oversized files are refused`() {
        for (bytes in listOf("{\"notes\":[]}".toByteArray(), ByteArray(MemoTransferCodec.MAX_BYTES + 1))) {
            assertTrue(runCatching { MemoTransferCodec.decode(bytes) }.isFailure)
        }
    }
}
