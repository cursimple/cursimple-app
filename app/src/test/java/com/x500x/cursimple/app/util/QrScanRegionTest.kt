package com.x500x.cursimple.app.util

import com.x500x.cursimple.feature.schedule.sharing.QrScanRegion
import com.x500x.cursimple.feature.schedule.sharing.QrPixelRegion
import com.x500x.cursimple.feature.schedule.sharing.inCameraCrop
import com.x500x.cursimple.feature.schedule.sharing.decodeQrLuminance

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class QrScanRegionTest {
    private val crop = QrPixelRegion(100, 50, 900, 650)
    private val frame = QrScanRegion(.125f, .25f, .5f, .75f)

    @Test fun `portrait camera rotation maps to native crop`() {
        assertEquals(QrPixelRegion(300, 350, 700, 575), frame.inCameraCrop(crop, 90))
    }
    @Test fun `other rotations preserve offsets and dimensions`() {
        assertEquals(QrPixelRegion(200, 200, 500, 500), frame.inCameraCrop(crop, 0))
        assertEquals(QrPixelRegion(500, 200, 800, 500), frame.inCameraCrop(crop, 180))
        assertEquals(QrPixelRegion(300, 125, 700, 350), frame.inCameraCrop(crop, 270))
    }
    @Test fun `clamps to viewport and rejects missing or invalid region`() {
        assertEquals(crop, QrScanRegion(-1f, -1f, 2f, 2f).inCameraCrop(crop, 90))
        assertNull(QrScanRegion(1f, 1f, 2f, 2f).inCameraCrop(crop, 0))
        assertNull(QrScanRegion(Float.NaN, 0f, 1f, 1f).inCameraCrop(crop, 0))
        assertNull(frame.inCameraCrop(crop, 45))
        assertNull(frame.inCameraCrop(QrPixelRegion(0, 0, 0, 10), 0))
    }
    @Test fun `fractional pixel edges round inward`() {
        assertEquals(QrPixelRegion(1, 1, 9, 9), QrScanRegion(.01f, .01f, .99f, .99f)
            .inCameraCrop(QrPixelRegion(0, 0, 10, 10), 0))
    }

    private fun place(bytes: ByteArray, width: Int, x: Int, y: Int, text: String, inverted: Boolean = false) {
        val qr = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 220, 220, mapOf(EncodeHintType.MARGIN to 4))
        for (dy in 0 until qr.height) for (dx in 0 until qr.width) {
            bytes[(y + dy) * width + x + dx] = if (qr[dx, dy] != inverted) 0 else 255.toByte()
        }
    }
    @Test fun `frame ignores a readable code outside and reads the inside code`() {
        val bytes = ByteArray(1000 * 700) { 255.toByte() }
        place(bytes, 1000, 30, 50, "outside")
        assertEquals("outside", decodeQrLuminance(bytes, 1000, 700, QrPixelRegion(0, 0, 300, 300)))
        val inside = QrPixelRegion(350, 150, 750, 550)
        assertNull(decodeQrLuminance(bytes, 1000, 700, inside))
        place(bytes, 1000, 440, 240, "CSV1:inside")
        assertEquals("CSV1:inside", decodeQrLuminance(bytes, 1000, 700, inside))
    }
    @Test fun `rotation maps a visible frame onto its code in a cropped sensor buffer`() {
        val bytes = ByteArray(1000 * 700) { 255.toByte() }
        place(bytes, 1000, 360, 355, "portrait-code")
        val pixels = frame.inCameraCrop(crop, 90)!!
        assertEquals("portrait-code", decodeQrLuminance(bytes, 1000, 700, pixels))
    }
    @Test fun `inverted codes are also decoded only in frame`() {
        val bytes = ByteArray(800 * 600)
        place(bytes, 800, 300, 200, "inverted", inverted = true)
        assertEquals("inverted", decodeQrLuminance(bytes, 800, 600, QrPixelRegion(250, 150, 570, 470)))
        assertNull(decodeQrLuminance(bytes, 800, 600, QrPixelRegion(0, 0, 220, 220)))
    }
}
