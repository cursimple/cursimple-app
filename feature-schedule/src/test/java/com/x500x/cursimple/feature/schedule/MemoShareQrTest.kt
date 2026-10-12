package com.x500x.cursimple.feature.schedule

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.x500x.cursimple.core.data.memo.MemoShareCode
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.feature.schedule.sharing.decodeQrLuminance
import org.junit.Assert.*
import org.junit.Test

class MemoShareQrTest {
    private fun readQr(code: String): String? {
        val qr = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 1024, 1024, mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4))
        val pixels = ByteArray(qr.width * qr.height) { index -> if (qr[index % qr.width, index / qr.width]) 0 else 255.toByte() }
        return decodeQrLuminance(pixels, qr.width, qr.height)
    }

    @Test fun `rendered notes QR decodes into complete editable checklist`() {
        val note = MemoNote("qr-note", courseTitle = "大学物理", title = "课后任务", body = "- [ ] 提交实验\n- [x] 复习")
        val code = MemoShareCode.encode(listOf(note))
        assertTrue(code.length <= MemoShareCode.MAX_QR_CHARS)
        val decoded = MemoShareCode.decode(requireNotNull(readQr(code))).notes.single()
        assertEquals(note.body, decoded.body)
        assertEquals(note.courseTitle, decoded.courseTitle)
    }

    @Test fun `maximum accepted QR code fits and can be read back`() {
        val code = MemoShareCode.PREFIX + "a-_0".repeat(600).take(MemoShareCode.MAX_QR_CHARS - MemoShareCode.PREFIX.length)
        assertEquals(MemoShareCode.MAX_QR_CHARS, code.length)
        assertEquals(code, readQr(code))
    }
}
