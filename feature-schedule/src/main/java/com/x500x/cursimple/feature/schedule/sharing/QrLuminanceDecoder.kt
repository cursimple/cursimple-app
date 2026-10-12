package com.x500x.cursimple.feature.schedule.sharing

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Outside pixels never enter the binarizer. */
fun decodeQrLuminance(
    bytes: ByteArray,
    width: Int,
    height: Int,
    region: QrPixelRegion = QrPixelRegion(0, 0, width, height),
): String? {
    val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8"))
    }
    val source = PlanarYUVLuminanceSource(bytes, width, height,
        region.left, region.top, region.width, region.height, false)
    return runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
        ?: runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text }.getOrNull()
}
