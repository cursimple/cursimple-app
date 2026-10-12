package com.x500x.cursimple.feature.schedule

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.core.data.memo.MemoImageStore
import com.x500x.cursimple.core.data.memo.MemoTransferImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Resolve note labels with app resources; [scopeLabel] null marks a single-note export. */
internal fun buildMemoShareDocument(
    context: Context,
    notes: List<MemoNote>,
    notebookOf: Map<String?, MemoNotebook>,
    scopeLabel: String?,
    now: LocalDateTime,
): MemoShareDocument {
    val res = context.resources
    val locale: Locale = res.configuration.locales[0]
    fun format(due: LocalDateTime): String {
        val pattern = context.getString(if (due.year == now.year) R.string.memo_due_pattern else R.string.memo_due_pattern_year)
        return due.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val entries = notes.map { note ->
        val notebook = notebookOf[note.courseKey]
        val priority = when (note.priority) {
            MemoPriority.High -> R.string.memo_priority_high
            MemoPriority.Medium -> R.string.memo_priority_medium
            MemoPriority.Low -> R.string.memo_priority_low
            MemoPriority.None -> null
        }
        MemoShareEntry(
            title = note.title.trim().ifBlank { context.getString(R.string.memo_untitled) },
            body = note.body,
            notebook = notebook?.title ?: note.courseTitle.ifBlank { context.getString(R.string.memo_notebook_other) },
            notebookSeed = note.courseKey?.let { notebook?.title ?: note.courseTitle.trim().ifBlank { null } },
            priority = note.priority,
            priorityLabel = priority?.let { context.getString(R.string.memo_share_priority, context.getString(it)) },
            dueLabel = note.dueDateTime?.let { context.getString(R.string.memo_due_later, format(it)) },
            overdue = !note.completed && note.dueDateTime?.isBefore(now) == true,
            pinnedLabel = context.getString(R.string.memo_pinned).takeIf { note.pinned && !note.completed },
            completedLabel = context.getString(R.string.memo_share_completed).takeIf { note.completed },
        )
    }
    if (scopeLabel == null) return MemoShareDocument(title = null, subtitle = null, entries = entries)
    val stamp = now.format(DateTimeFormatter.ofPattern(context.getString(R.string.memo_due_pattern_year), locale))
    return MemoShareDocument(
        title = context.getString(R.string.memo_share_doc_title, scopeLabel),
        subtitle = res.getQuantityString(R.plurals.memo_share_doc_subtitle, entries.size, stamp, entries.size),
        entries = entries,
    )
}

/** Write share files under cache/memos and build the matching send intent. */
internal object MemoShareExporter {
    private const val DIR = "memos"
    private const val KEEP_MS = 10 * 60 * 1000L
    // Binder transactions cap EXTRA_TEXT; larger plain text goes out as a file.
    private const val MAX_INLINE_TEXT = 100_000

    suspend fun export(context: Context, originalDoc: MemoShareDocument, format: MemoShareFormat, accentArgb: Int, sourceNotes: List<MemoNote> = emptyList()): Intent =
        withContext(Dispatchers.IO) {
            val images = if (format == MemoShareFormat.PlainText) emptyMap() else loadImages(context, originalDoc)
            val doc = originalDoc.copy(images = images)
            val root = File(context.cacheDir, DIR)
            cleanup(root)
            val subject = doc.title ?: doc.entries.firstOrNull()?.title.orEmpty()
            if (format == MemoShareFormat.PlainText) {
                val text = memoSharePlainText(doc)
                if (text.length <= MAX_INLINE_TEXT) {
                    return@withContext Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                }
            }
            val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
            val stem = fileStem(subject)
            val files = when (format) {
                MemoShareFormat.ShareCode, MemoShareFormat.QrCode -> error("Code exports use the preview sheet")
                MemoShareFormat.CurSimple -> listOf(File(dir, "$stem.csnotes").also {
                    it.writeBytes(com.x500x.cursimple.core.data.memo.MemoTransferCodec.encode(sourceNotes,
                        images.values.map { image -> MemoTransferImage.from(image.bytes) }))
                })
                MemoShareFormat.Image -> {
                    val renderer = MemoShareImageRenderer(MemoShareImageColors.fromAccent(accentArgb), images = images)
                    val written = mutableListOf<File>()
                    renderer.render(doc, pageLabel = { page, total -> "$page / $total" }) { index, bitmap ->
                        ensureActive()
                        val file = File(dir, "$stem-${index + 1}.png")
                        file.outputStream().buffered().use { out ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "compress failed" }
                        }
                        written += file
                    }
                    // A lone page drops the numeric suffix.
                    written.singleOrNull()?.let { only ->
                        val renamed = File(dir, "$stem.png")
                        if (only.renameTo(renamed)) listOf(renamed) else written
                    } ?: written
                }
                MemoShareFormat.Word -> listOf(File(dir, "$stem.docx").also { file ->
                    file.outputStream().buffered().use {
                        writeMemoDocx(doc, MemoShareImageColors.fromAccent(accentArgb).accent, it)
                    }
                })
                MemoShareFormat.Markdown -> if (images.isEmpty()) {
                    listOf(File(dir, "$stem.md").also { it.writeText(memoShareMarkdown(doc)) })
                } else listOf(File(dir, "$stem.zip").also { file ->
                    ZipOutputStream(file.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry("$stem.md")); zip.write(memoShareMarkdown(doc).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                        images.values.forEach { image ->
                            zip.putNextEntry(ZipEntry(image.path)); zip.write(image.bytes); zip.closeEntry()
                        }
                    }
                })
                MemoShareFormat.PlainText -> listOf(File(dir, "$stem.txt").also { it.writeText(memoSharePlainText(doc)) })
            }
            check(files.isNotEmpty()) { "no output" }
            ensureActive()
            sendIntent(context, files, if (format == MemoShareFormat.Markdown && images.isNotEmpty()) "application/zip" else format.mime, subject)
        }

    private fun loadImages(context: Context, doc: MemoShareDocument): Map<String, MemoShareEmbeddedImage> {
        val store = MemoImageStore(context)
        var total = 0
        return doc.entries.flatMap { MemoImageRef.ids(it.body) }.distinct().associateWith { id ->
            val file = requireNotNull(store.file(id)) { "note_image_missing" }
            require(file.length() <= MemoTransferImage.MAX_BYTES)
            val bytes = file.readBytes()
            total += bytes.size; require(total <= com.x500x.cursimple.core.data.memo.MemoTransferCodec.MAX_BYTES)
            val packet = MemoTransferImage.from(bytes); require(packet.id == id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768)
            MemoShareEmbeddedImage(id, bytes, bounds.outWidth, bounds.outHeight, requireNotNull(MemoTransferImage.imageMime(bytes)))
        }
    }

    private fun sendIntent(context: Context, files: List<File>, mime: String, subject: String): Intent {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }
        return intent.apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Remove exports older than [KEEP_MS]; recent ones may still be read by the receiving app. */
    private fun cleanup(root: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        root.listFiles()?.forEach { entry ->
            if (entry.lastModified() < cutoff) entry.deleteRecursively()
        }
    }

    private val UNSAFE_NAME = Regex("""[\\/:*?"<>|\p{Cntrl}]+""")

    internal fun fileStem(raw: String): String =
        raw.replace(UNSAFE_NAME, "_").trim().trim('.', '_').take(60).ifBlank { "notes" }
}
