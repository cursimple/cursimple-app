package com.x500x.cursimple.core.data.note

import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Maximum note length in Unicode code points. */
const val COURSE_NOTE_MAX_LENGTH = 500

/** Maximum retained course notes per term. */
const val COURSE_NOTE_MAX_COUNT = 400

/** Use [courseId] first; stored course characteristics support reimported IDs. */
@Serializable
data class CourseNoteAnchor(
    @SerialName("courseId") val courseId: String,
    @SerialName("title") val title: String,
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("teacher") val teacher: String = "",
    @SerialName("location") val location: String = "",
)

@Serializable
data class CourseNote(
    @SerialName("anchor") val anchor: CourseNoteAnchor,
    @SerialName("text") val text: String,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

/** [byCourseId] maps current course IDs; [orphans] contains unmatched notes. */
data class CourseNoteIndex(
    val byCourseId: Map<String, CourseNote> = emptyMap(),
    val orphans: List<CourseNote> = emptyList(),
) {
    fun textOf(courseId: String): String = byCourseId[courseId]?.text.orEmpty()

    fun hasNote(courseId: String): Boolean = byCourseId[courseId]?.text?.isNotBlank() == true
}

sealed interface CourseNoteInput {
    data class Accepted(val text: String) : CourseNoteInput

    data class TooLong(val length: Int, val limit: Int) : CourseNoteInput
}

fun courseNoteSignature(title: String, dayOfWeek: Int, startNode: Int, endNode: Int): String =
    "${normalizeNoteTitle(title)}|$dayOfWeek|$startNode|$endNode"

val CourseNoteAnchor.signature: String
    get() = courseNoteSignature(title, dayOfWeek, startNode, endNode)

val CourseItem.noteSignature: String
    get() = courseNoteSignature(title, time.dayOfWeek, time.startNode, time.endNode)

fun CourseItem.noteAnchor(): CourseNoteAnchor = CourseNoteAnchor(
    courseId = id,
    title = title,
    dayOfWeek = time.dayOfWeek,
    startNode = time.startNode,
    endNode = time.endNode,
    weeks = weeks.distinct().sorted(),
    teacher = teacher,
    location = location,
)

/** Count Unicode code points rather than UTF-16 units. */
fun courseNoteLength(text: String): Int =
    if (text.isEmpty()) 0 else text.codePointCount(0, text.length)

fun normalizeCourseNoteText(raw: String): String =
    raw.replace("\r\n", "\n").replace('\r', '\n').trim()

fun validateCourseNote(raw: String, limit: Int = COURSE_NOTE_MAX_LENGTH): CourseNoteInput {
    val normalized = normalizeCourseNoteText(raw)
    val length = courseNoteLength(normalized)
    return if (length > limit) {
        CourseNoteInput.TooLong(length = length, limit = limit)
    } else {
        CourseNoteInput.Accepted(normalized)
    }
}

/**
 * Match IDs first, then title/day/periods. Resolve duplicates by weeks, teacher and location,
 * then stable order; retain unmatched notes as orphans.
 */
fun resolveCourseNotes(courses: List<CourseItem>, notes: List<CourseNote>): CourseNoteIndex {
    if (notes.isEmpty() || courses.isEmpty()) {
        return CourseNoteIndex(orphans = notes)
    }
    val uniqueCourses = courses.distinctBy { it.id }
    val bound = LinkedHashMap<String, CourseNote>()
    val usedNotes = HashSet<Int>()

    val courseById = uniqueCourses.associateBy { it.id }
    notes.forEachIndexed { noteIndex, note ->
        val course = courseById[note.anchor.courseId] ?: return@forEachIndexed
        if (bound.containsKey(course.id)) return@forEachIndexed
        bound[course.id] = note
        usedNotes += noteIndex
    }

    val coursesBySignature = uniqueCourses.groupBy { it.noteSignature }
    notes.withIndex()
        .filterNot { it.index in usedNotes }
        .groupBy { it.value.anchor.signature }
        .forEach { (signature, groupNotes) ->
            val groupCourses = coursesBySignature[signature]
                .orEmpty()
                .filterNot { bound.containsKey(it.id) }
            if (groupCourses.isEmpty()) return@forEach
            if (groupNotes.size == 1 && groupCourses.size == 1) {
                bound[groupCourses.first().id] = groupNotes.first().value
                usedNotes += groupNotes.first().index
                return@forEach
            }
            bindContestedGroup(groupNotes, groupCourses, bound, usedNotes)
        }

    return CourseNoteIndex(
        byCourseId = bound,
        orphans = notes.filterIndexed { index, _ -> index !in usedNotes },
    )
}

/**
 * Refresh matched anchors; enforce [limit] by evicting oldest orphans before associated notes.
 */
fun reconcileCourseNotes(
    courses: List<CourseItem>,
    notes: List<CourseNote>,
    limit: Int = COURSE_NOTE_MAX_COUNT,
): List<CourseNote> {
    if (notes.isEmpty()) return notes
    val index = resolveCourseNotes(courses, notes)
    val courseById = courses.distinctBy { it.id }.associateBy { it.id }
    val rebound = index.byCourseId.mapNotNull { (courseId, note) ->
        val course = courseById[courseId] ?: return@mapNotNull null
        val anchor = course.noteAnchor()
        if (anchor == note.anchor) note else note.copy(anchor = anchor)
    }
    val keptOrphans = index.orphans
        .sortedByDescending { it.updatedAt }
        .take((limit - rebound.size).coerceAtLeast(0))
        .sortedBy { it.updatedAt }
    return rebound + keptOrphans
}

fun upsertCourseNote(
    courses: List<CourseItem>,
    notes: List<CourseNote>,
    course: CourseItem,
    text: String,
    updatedAt: Long,
    limit: Int = COURSE_NOTE_MAX_COUNT,
): List<CourseNote> {
    val allCourses = if (courses.any { it.id == course.id }) courses else courses + course
    val existing = resolveCourseNotes(allCourses, notes).byCourseId[course.id]
    val remaining = if (existing == null) notes else notes.filterNot { it === existing }
    val normalized = normalizeCourseNoteText(text)
    if (normalized.isEmpty()) {
        return reconcileCourseNotes(allCourses, remaining, limit)
    }
    val updated = remaining + CourseNote(
        anchor = course.noteAnchor(),
        text = normalized,
        updatedAt = updatedAt,
    )
    return reconcileCourseNotes(allCourses, updated, limit)
}

private class AnchorMatch(
    val note: IndexedValue<CourseNote>,
    val course: CourseItem,
    val courseIndex: Int,
    val score: Int,
)

private fun bindContestedGroup(
    groupNotes: List<IndexedValue<CourseNote>>,
    groupCourses: List<CourseItem>,
    bound: MutableMap<String, CourseNote>,
    usedNotes: MutableSet<Int>,
) {
    val pairs = groupNotes
        .flatMap { note ->
            groupCourses.mapIndexed { courseIndex, course ->
                AnchorMatch(note, course, courseIndex, anchorMatchScore(note.value.anchor, course))
            }
        }
        .filter { it.score > 0 }
        .sortedWith(
            compareByDescending<AnchorMatch> { it.score }
                .thenBy { it.note.index }
                .thenBy { it.courseIndex },
        )

    pairs.forEach { candidate ->
        if (candidate.note.index in usedNotes) return@forEach
        if (bound.containsKey(candidate.course.id)) return@forEach
        bound[candidate.course.id] = candidate.note.value
        usedNotes += candidate.note.index
    }

    // Use stable order for otherwise indistinguishable courses rather than losing their notes.
    val leftoverCourses = groupCourses.filterNot { bound.containsKey(it.id) }.iterator()
    groupNotes.filterNot { it.index in usedNotes }.forEach { note ->
        if (!leftoverCourses.hasNext()) return
        val course = leftoverCourses.next()
        bound[course.id] = note.value
        usedNotes += note.index
    }
}

private fun anchorMatchScore(anchor: CourseNoteAnchor, course: CourseItem): Int {
    var score = 0
    val courseWeeks = course.weeks.distinct().sorted()
    if (anchor.weeks.isNotEmpty() && courseWeeks.isNotEmpty()) {
        score += when {
            anchor.weeks == courseWeeks -> 4
            anchor.weeks.any { it in courseWeeks } -> 2
            else -> 0
        }
    }
    if (anchor.teacher.isNotBlank() && anchor.teacher.trim() == course.teacher.trim()) score += 2
    if (anchor.location.isNotBlank() && anchor.location.trim() == course.location.trim()) score += 1
    return score
}

private val NOTE_TITLE_WHITESPACE = Regex("\\s+")

private fun normalizeNoteTitle(title: String): String =
    title.replace(NOTE_TITLE_WHITESPACE, "").lowercase()
