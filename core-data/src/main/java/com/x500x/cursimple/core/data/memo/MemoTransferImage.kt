package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.MemoImageRef
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.io.encoding.Base64

@Serializable
data class MemoTransferImage(val id: String, val data: String) {
    fun bytes(): ByteArray {
        require(MemoImageRef.validId(id) && data.length <= MAX_BYTES * 4 / 3 + 4) { "invalid_note_image" }
        return Base64.Default.decode(data).also { bytes ->
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES && imageMime(bytes) != null && imageId(bytes) == id) {
                "invalid_note_image"
            }
        }
    }
    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        fun from(bytes: ByteArray): MemoTransferImage = MemoTransferImage(imageId(bytes), Base64.Default.encode(bytes)).also { it.bytes() }
        fun imageId(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        fun imageMime(bytes: ByteArray): String? = when {
            bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)) -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            else -> null
        }
    }
}
