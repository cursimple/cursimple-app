package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.data.ShareCodeCompression
import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Pins how much the compact envelope saves on a typical note bundle. */
class MemoShareSizeProbeTest {
    private fun notes(): List<MemoNote> = (1..12).map { index ->
        MemoNote(
            id = "note-$index",
            title = "第 $index 次作业",
            body = "把第三章的习题做完，拍照上传到学习通。截止到周五晚上。".repeat(3),
            courseTitle = "数据库原理及应用",
            dueAt = null,
        )
    }

    @Test fun compactEnvelopeIsShorter() {
        val notes = notes()
        // Legacy: the same JSON the old codec wrote (defaults and nulls included), gzipped.
        val legacyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacyBytes = legacyJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(MemoNote.serializer()), notes,
        ).toByteArray()
        val gz = ByteArrayOutputStream().use { out -> GZIPOutputStream(out).use { it.write(legacyBytes) }; out.toByteArray() }
        val legacy = "CSN1:" + java.util.Base64.getUrlEncoder().encodeToString(gz)

        val compactJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
        val raw = compactJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(MemoNote.serializer()), notes,
        ).toByteArray()
        val compact = "CSN2:" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ShareCodeCompression.compress(raw))

        println("memo code size: old=${legacy.length} new=${compact.length} saving=${100 - compact.length * 100 / legacy.length}%")
        assertTrue(compact.length < legacy.length)
    }
}
