package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.Serializable
import java.security.MessageDigest

internal class EamsScheduleParser {
    private val eamsSlotNodeByLabel = mapOf(
        "第一节" to 1,
        "第二节" to 2,
        "第三节" to 4,
        "第四节" to 5,
        "第五节" to 7,
        "第六节" to 8,
        "午间课" to 3,
        "晚间课" to 6,
    )

    private val defaultSlotNodeByIndex = mapOf(
        0 to 1,
        1 to 2,
        2 to 4,
        3 to 5,
        4 to 7,
        5 to 8,
        6 to 3,
        7 to 6,
    )

    fun extractMetadata(pageHtml: String): EamsCourseTableMeta {
        val maxWeek = Regex("""<option value="(\d+)">第\d+周</option>""")
            .findAll(pageHtml)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()
            ?: error("未找到教学周上限")
        val semesterId = Regex("semesterCalendar\\(\\{[^}]*value:\"([^\"]+)\"")
            .find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?: error("未找到 semester.id")
        val ids = Regex("bg\\.form\\.addInput\\(form,\"ids\",\"([^\"]+)\"\\)")
            .find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?: error("未找到学生 ids")
        val projectId = Regex("""project\.id=([^&']+)""")
            .find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?: "1"
        return EamsCourseTableMeta(
            semesterId = semesterId,
            ids = ids,
            projectId = projectId,
            maxWeek = maxWeek,
        )
    }

    fun buildSchedule(
        meta: EamsCourseTableMeta,
        detailHtml: String,
        termId: String,
        updatedAt: String,
    ): TermSchedule {
        val unitCount = Regex("""var unitCount = (\d+);""")
            .find(detailHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 8
        val teacherMap = parseTeacherMap(detailHtml)
        val activities = parseActivities(detailHtml, unitCount, meta.maxWeek, teacherMap)
        val grouped = activities
            .groupBy { it.time.dayOfWeek }
            .toSortedMap()
            .map { (dayOfWeek, courses) ->
                DailySchedule(
                    dayOfWeek = dayOfWeek,
                    courses = courses.sortedWith(compareBy<CourseItem>({ it.time.startNode }, { it.time.endNode }, { it.title })),
                )
            }
        return TermSchedule(
            termId = termId,
            updatedAt = updatedAt,
            dailySchedules = grouped,
        )
    }

    private fun parseTeacherMap(detailHtml: String): Map<String, String> {
        val rowPattern = Regex(
            """taskTable\.action\?lesson\.id=\d+".*?>(\d+)</a>\s*</td><td>([^<]*)</td>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        return rowPattern.findAll(detailHtml).associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
    }

    private fun parseActivities(
        detailHtml: String,
        unitCount: Int,
        maxWeek: Int,
        teacherMap: Map<String, String>,
    ): List<CourseItem> {
        val slotNodeMap = parseSlotNodeMap(detailHtml, unitCount)
        val blockPattern = Regex(
            """var teachers = \[(.*?)];.*?activity = new TaskActivity\((.*?)\);\s*((?:index\s*=\s*\d+\s*\*\s*unitCount\s*\+\s*\d+\s*;\s*table\d+\.activities\[index]\[table\d+\.activities\[index]\.length]=activity;\s*)+)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        return blockPattern.findAll(detailHtml).mapIndexed { position, match ->
            val teacherBlock = match.groupValues[1]
            val args = match.groupValues[2]
            val indexBlock = match.groupValues[3]
            val literals = Regex("\"((?:\\\\.|[^\"])*)\"")
                .findAll(args)
                .map { decodeJsString(it.groupValues[1]) }
                .toList()
            require(literals.size >= 5) { "TaskActivity 参数不足，无法解析课表" }
            val taskToken = literals[0]
            val rawCourseLabel = literals[1]
            val location = literals[3]
            val validWeeks = literals[4]
            val sequence = extractSequence(rawCourseLabel) ?: extractSequence(taskToken) ?: "activity-$position"
            val teacher = Regex("name:\"([^\"]+)\"")
                .find(teacherBlock)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: teacherMap[sequence].orEmpty()
            val indices = Regex("""index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+)\s*;""")
                .findAll(indexBlock)
                .map {
                    val dayIndex = it.groupValues[1].toInt()
                    val slotIndex = it.groupValues[2].toInt()
                    dayIndex to slotIndex
                }
                .toList()
            require(indices.isNotEmpty()) { "未找到课表位置索引" }
            val dayOfWeek = indices.first().first + 1
            val nodes = indices.map { (_, slotIndex) -> resolveSlotNode(slotIndex, unitCount, slotNodeMap) }.sorted()
            val startNode = nodes.first()
            val endNode = nodes.last()
            val title = normalizeCourseTitle(rawCourseLabel)
            CourseItem(
                id = stableCourseId(
                    sequence = sequence,
                    dayOfWeek = dayOfWeek,
                    startNode = startNode,
                    endNode = endNode,
                    title = title,
                    teacher = teacher,
                    location = location,
                    validWeeks = validWeeks,
                ),
                title = title,
                teacher = teacher,
                location = location,
                weeks = parseWeeks(validWeeks, maxWeek),
                time = CourseTimeSlot(
                    dayOfWeek = dayOfWeek,
                    startNode = startNode,
                    endNode = endNode,
                ),
            )
        }.distinctBy { it.id }.toList()
    }

    private fun parseSlotNodeMap(detailHtml: String, unitCount: Int): Map<Int, Int> {
        val rowPattern = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL))
        val slotPattern = Regex("""id=["']TD(\d+)_\d+["']""")
        return rowPattern.findAll(detailHtml).mapNotNull { match ->
            val rowHtml = match.groupValues.getOrNull(1).orEmpty()
            val label = Regex("""<td\b[^>]*>\s*(?:<font\b[^>]*>)?\s*([^<]+?)\s*(?:</font>)?\s*</td>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(rowHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::normalizeHtmlText)
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val node = eamsSlotNodeByLabel[label] ?: return@mapNotNull null
            val slotIndex = slotPattern.find(rowHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.rem(unitCount)
                ?: return@mapNotNull null
            slotIndex to node
        }.toMap()
    }

    private fun resolveSlotNode(
        slotIndex: Int,
        unitCount: Int,
        slotNodeMap: Map<Int, Int>,
    ): Int {
        slotNodeMap[slotIndex]?.let { return it }
        if (unitCount == defaultSlotNodeByIndex.size) {
            defaultSlotNodeByIndex[slotIndex]?.let { return it }
        }
        error("未找到 EAMS 节次索引映射: unitCount=$unitCount, slotIndex=$slotIndex")
    }

    private fun parseWeeks(validWeeks: String, maxWeek: Int): List<Int> {
        return validWeeks.withIndex()
            .filter { indexed -> indexed.index in 1..maxWeek && indexed.value == '1' }
            .map { it.index }
    }

    private fun normalizeCourseTitle(rawCourseLabel: String): String {
        return rawCourseLabel.replace(Regex("""\(\d+\)$"""), "").trim()
    }

    private fun extractSequence(raw: String): String? {
        return Regex("""\((\d+)\)$""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun decodeJsString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun normalizeHtmlText(value: String): String {
        return value
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun stableCourseId(
        sequence: String,
        dayOfWeek: Int,
        startNode: Int,
        endNode: Int,
        title: String,
        teacher: String,
        location: String,
        validWeeks: String,
    ): String {
        val signature = listOf(title, teacher, location, validWeeks).joinToString("|")
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)
        return "$sequence-$dayOfWeek-$startNode-$endNode-$suffix"
    }
}

@Serializable
internal data class EamsCourseTableMeta(
    val semesterId: String,
    val ids: String,
    val projectId: String = "1",
    val maxWeek: Int,
)
