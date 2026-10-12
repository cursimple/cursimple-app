package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.random.Random

class MemoShareCodeTest {
    private val note = MemoNote("original", courseKey = "sender-only", courseTitle = "高等数学",
        title = "复习 📝", body = "**重点**\n- [ ] 习题\n- [x] 完成\n1. 公式",
        priority = MemoPriority.High, pinned = true, dueAt = "2026-10-16T08:00")

    @Test fun `codes preserve editable note content and use recipient course names`() {
        val decoded = MemoShareCode.decode(MemoShareCode.encode(listOf(note, MemoNote("general", title = "购物"))))
        assertNull(decoded.notes.first().courseKey)
        val courses = listOf(CourseItem("local", "高等数学", time = CourseTimeSlot(2, 1, 2)))
        val imported = MemoTransferCodec.prepare(decoded, courses, emptyList(), 123L).notes
        assertEquals(note.body, imported.first().body)
        assertEquals(note.dueAt, imported.first().dueAt)
        assertEquals(note.priority, imported.first().priority)
        assertTrue(imported.first().pinned)
        assertEquals(memoCourseKey(courses.single().title), imported.first().courseKey)
        assertNull(imported.last().courseKey)
    }

    @Test fun `messaging whitespace is tolerated without changing content`() {
        val code = MemoShareCode.encode(listOf(note))
        assertEquals(MemoShareCode.decode(code), MemoShareCode.decode(" \n" + code.chunked(20).joinToString(" \n\t") + "\n"))
    }

    @Test fun `repeat code imports never replace local edits`() {
        val bundle = MemoShareCode.decode(MemoShareCode.encode(listOf(note)))
        val imported = MemoTransferCodec.prepare(bundle, emptyList(), emptyList(), 1L).notes.single()
        val again = MemoTransferCodec.prepare(bundle, emptyList(), listOf(imported.copy(body = "自己的修改")), 2L)
        assertTrue(again.notes.isEmpty())
        assertEquals(1, again.skipped)
    }

    @Test fun `invalid prefix corrupt gzip and unsupported bundles are refused`() {
        val code = MemoShareCode.encode(listOf(note))
        val badBundle = "{\"format\":\"cursimple.notes\",\"version\":99,\"notes\":[]}"
        for (bad in listOf("CSV1:abc", "CSN1:@@@@", code.dropLast(12), gzipCode(badBundle.toByteArray()))) {
            val failure = runCatching { MemoShareCode.decode(bad) }.exceptionOrNull()
            assertTrue(failure is MemoShareCodeException)
            assertEquals(MemoShareCodeFailure.Invalid, (failure as MemoShareCodeException).reason)
        }
    }

    @Test fun `oversized decompression and pasted codes are bounded`() {
        val bomb = gzipCode(ByteArray(MemoTransferCodec.MAX_BYTES + 1) { 32 })
        for (bad in listOf(bomb, "CSN1:" + "a".repeat(MemoShareCode.MAX_CHARS))) {
            val failure = runCatching { MemoShareCode.decode(bad) }.exceptionOrNull() as MemoShareCodeException
            assertEquals(MemoShareCodeFailure.TooLarge, failure.reason)
        }
    }

    @Test fun `large incompressible exports request file format instead of exceeding text limit`() {
        val random = Random(8)
        val body = CharArray(200_000) { (33 + random.nextInt(90)).toChar() }.concatToString()
        val failure = runCatching { MemoShareCode.encode(listOf(note.copy(body = body))) }.exceptionOrNull() as MemoShareCodeException
        assertEquals(MemoShareCodeFailure.TooLarge, failure.reason)
    }

    private fun gzipCode(bytes: ByteArray): String = MemoShareCode.PREFIX + Base64.UrlSafe.encode(
        ByteArrayOutputStream().use { out -> GZIPOutputStream(out).use { it.write(bytes) }; out.toByteArray() })
}
