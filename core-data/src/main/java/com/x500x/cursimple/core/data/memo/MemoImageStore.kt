package com.x500x.cursimple.core.data.memo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.core.kernel.model.MemoNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Durable, immutable images survive gallery permission changes, undo and note duplication. */
class MemoImageStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "memo-images")
    fun file(id: String): File? = if (MemoImageRef.validId(id)) File(root, "$id.img").takeIf { it.isFile } else null

    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = File.createTempFile("memo-image-", ".tmp", app.cacheDir)
        try {
            checkNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                source.outputStream().use { out ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        total += read; require(total <= 32 * 1024 * 1024) { "note_image_too_large" }
                        out.write(buffer, 0, read)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768) { "invalid_note_image" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
            val decoded = requireNotNull(BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }))
            val orientation = runCatching { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                var bitmap = oriented
                var scaled: Bitmap? = null
                try {
                    var bytes: ByteArray
                    while (true) {
                        bytes = ByteArrayOutputStream().use { out ->
                            check(bitmap.compress(if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out))
                            out.toByteArray()
                        }
                        if (bytes.size <= MemoTransferImage.MAX_BYTES) break
                        require(maxOf(bitmap.width, bitmap.height) > 640) { "note_image_too_large" }
                        val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * .75f).toInt().coerceAtLeast(1),
                            (bitmap.height * .75f).toInt().coerceAtLeast(1), true)
                        scaled?.recycle(); scaled = smaller; bitmap = smaller
                    }
                    val packet = MemoTransferImage.from(bytes)
                    install(listOf(packet))
                    packet.id
                } finally { scaled?.recycle() }
            } finally { if (oriented !== decoded) oriented.recycle(); decoded.recycle() }
        } finally { source.delete() }
    }

    fun exportImages(notes: List<MemoNote>, maxBytes: Int = MemoTransferCodec.MAX_BYTES): List<MemoTransferImage> {
        var total = 0
        return notes.flatMap { MemoImageRef.ids(it.body) }.distinct().map { id ->
            val source = requireNotNull(file(id)) { "note_image_missing" }
            require(source.length() <= MemoTransferImage.MAX_BYTES)
            val bytes = source.readBytes()
            total += bytes.size; require(total <= maxBytes) { "note_images_too_large" }
            MemoTransferImage.from(bytes).also { require(it.id == id) }
        }
    }

    /** Validate everything before installing, and never replace an existing content hash. */
    fun install(images: List<MemoTransferImage>) {
        val decoded = images.map { it to it.bytes() }
        root.mkdirs()
        decoded.forEach { (image, bytes) ->
            val destination = File(root, "${image.id}.img")
            if (!destination.isFile) {
                val temp = File.createTempFile("image-", ".tmp", root)
                try { temp.writeBytes(bytes); check(temp.renameTo(destination) || destination.isFile) }
                finally { temp.delete() }
            }
        }
    }
}
