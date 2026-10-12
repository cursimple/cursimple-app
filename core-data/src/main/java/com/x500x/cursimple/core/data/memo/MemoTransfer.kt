package com.x500x.cursimple.core.data.memo

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.data.AppBackupStores
import com.x500x.cursimple.core.data.PreferencesStoreSnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Portable notes contain course names, never the sender's timetable identifiers. */
@Serializable
data class MemoTransferBundle(
    val format: String = MemoTransferCodec.FORMAT,
    val version: Int = 1,
    val notes: List<MemoNote>,
    val images: List<MemoTransferImage> = emptyList(),
)

data class MemoImportPlan(val notes: List<MemoNote>, val skipped: Int, val courseTitles: List<String>)

/** Read exactly the memo snapshot being backed up, avoiding races with later note edits. */
fun memoNotesInBackup(snapshot: PreferencesStoreSnapshot?): List<MemoNote> {
    if (snapshot == null) return emptyList()
    require(snapshot.storeName == AppBackupStores.MEMOS)
    val content = snapshot.entries.firstOrNull { it.name == "memo_notes_json" }?.stringValue ?: return emptyList()
    return Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(MemoNote.serializer()), content)
}

object MemoTransferCodec {
    const val FORMAT = "cursimple.notes"
    const val MIME = "application/vnd.cursimple.notes+json"
    const val EXTENSION = "csnotes"
    const val MAX_BYTES = 8 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(notes: List<MemoNote>, images: List<MemoTransferImage> = emptyList()): ByteArray {
        val portable = notes.map { it.copy(courseKey = null, sharedOriginId = it.sharedOriginId ?: it.id) }
        val bundle = MemoTransferBundle(version = if (images.isEmpty()) 1 else 2, notes = portable, images = images)
        validate(bundle)
        return json.encodeToString(MemoTransferBundle.serializer(), bundle)
            .toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "notes_too_large" } }
    }

    fun decode(bytes: ByteArray): MemoTransferBundle {
        require(bytes.size <= MAX_BYTES) { "notes_too_large" }
        val value = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: error("not_cursimple_notes")
        require((value["format"] as? JsonPrimitive)?.content == FORMAT) { "not_cursimple_notes" }
        require((value["version"] as? JsonPrimitive)?.intOrNull in 1..2) { "unsupported_notes_version" }
        return json.decodeFromJsonElement(MemoTransferBundle.serializer(), value).also(::validate)
    }

    private fun validate(bundle: MemoTransferBundle) {
        require(bundle.format == FORMAT) { "not_cursimple_notes" }
        require(bundle.version in 1..2 && (bundle.version == 2 || bundle.images.isEmpty())) { "unsupported_notes_version" }
        require(bundle.notes.size <= 2000) { "too_many_notes" }
        val references = bundle.notes.flatMap { MemoImageRef.ids(it.body) }.toSet()
        require(bundle.images.size <= 2000 && bundle.images.map { it.id }.toSet().size == bundle.images.size)
        require(references == bundle.images.map { it.id }.toSet()) { "note_images_missing" }
        var imageBytes = 0
        bundle.images.forEach { imageBytes += it.bytes().size; require(imageBytes <= MAX_BYTES) }
        bundle.notes.forEach { note ->
            require(note.id.isNotBlank() && note.id.length <= 256 && note.id.none(Char::isISOControl))
            note.sharedOriginId?.let { require(it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl)) }
            require(note.title.length <= 2000 && note.body.length <= 500_000 && note.courseTitle.length <= 500)
            require(!note.isBlank && (note.dueAt == null || note.dueDateTime != null))
        }
    }

    fun prepare(bundle: MemoTransferBundle, courses: List<CourseItem>, existing: List<MemoNote>, now: Long): MemoImportPlan {
        validate(bundle)
        val local = courses.groupBy { courseName(it.title) }
        val known = existing.mapTo(mutableSetOf()) { fingerprint(it) }
        val ids = existing.mapTo(mutableSetOf()) { it.id }
        var skipped = 0
        val imported = bundle.notes.mapNotNull { incoming ->
            val sourceTitle = incoming.courseTitle.trim()
            val courseTitle = local[courseName(sourceTitle)]?.firstOrNull()?.title ?: sourceTitle
            val mapped = incoming.copy(courseTitle = courseTitle, courseKey = courseTitle.takeIf(String::isNotBlank)?.let(::memoCourseKey))
            val fingerprint = fingerprint(mapped)
            val id = "shared-$fingerprint"
            if (fingerprint in known || id in ids) {
                skipped++
                null
            } else {
                known += fingerprint
                ids += id
                mapped.copy(id = id, sharedOriginId = incoming.sharedOriginId ?: incoming.id, createdAt = now, updatedAt = now)
            }
        }
        return MemoImportPlan(imported, skipped, imported.map { it.courseTitle }.filter(String::isNotBlank).distinct())
    }

    private fun courseName(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private fun fingerprint(note: MemoNote): String {
        val origin = note.sharedOriginId ?: note.id
        val content = json.encodeToString(MemoNote.serializer(), note.copy(
            id = "", courseKey = null, courseTitle = courseName(note.courseTitle),
            sharedOriginId = null, createdAt = 0, updatedAt = 0,
        ))
        return MessageDigest.getInstance("SHA-256").digest("$origin\u0000$content".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
