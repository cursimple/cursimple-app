package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
data class TemporaryScheduleOverride(
    @SerialName("id") val id: String,
    @SerialName("type") val type: TemporaryScheduleOverrideType = TemporaryScheduleOverrideType.MakeUp,
    @SerialName("targetDate") val targetDate: String = "",
    @SerialName("sourceDate") val sourceDate: String = "",
    @SerialName("startDate") val startDate: String = "",
    @SerialName("endDate") val endDate: String = "",
    @SerialName("sourceDayOfWeek") val sourceDayOfWeek: Int? = null,
    @SerialName("cancelStartNode") val cancelStartNode: Int? = null,
    @SerialName("cancelEndNode") val cancelEndNode: Int? = null,
    @SerialName("cancelCourseId") val cancelCourseId: String? = null,
    /** 只调这个区间的课，区间外仍按本日自己的安排；两个都为空表示整天调课。 */
    @SerialName("makeUpStartNode") val makeUpStartNode: Int? = null,
    @SerialName("makeUpEndNode") val makeUpEndNode: Int? = null,
    /**
     * [TemporaryScheduleOverrideType.MoveCourse] 专用：把哪一门课挪走。
     *
     * 原本那天由 [sourceDate] 指定，挪到哪天由 [targetDate] 指定，
     * 落到的节次由 [moveToStartNode]、[moveToEndNode] 指定。
     */
    @SerialName("moveCourseId") val moveCourseId: String? = null,
    @SerialName("moveToStartNode") val moveToStartNode: Int? = null,
    @SerialName("moveToEndNode") val moveToEndNode: Int? = null,
)

@Serializable
enum class TemporaryScheduleOverrideType {
    @SerialName("make_up")
    MakeUp,

    @SerialName("cancel_course")
    CancelCourse,

    /**
     * 把单独一门课从某天某节挪到另一天的某节。
     *
     * 和 [MakeUp] 的区别：[MakeUp] 换的是「这一天按哪天的课表上」，整天或整段节次一起换；
     * 这个只动一门课，其余课程原地不动。
     */
    @SerialName("move_course")
    MoveCourse,
}

fun TemporaryScheduleOverride.containsDate(date: LocalDate): Boolean {
    return sourceDateFor(date) != null
}

fun TemporaryScheduleOverride.sourceDateFor(date: LocalDate): LocalDate? {
    if (type != TemporaryScheduleOverrideType.MakeUp) return null

    val explicitTarget = parseOverrideDate(targetDate)
    val explicitSource = parseOverrideDate(sourceDate)
    if (explicitTarget != null || explicitSource != null) {
        return if (explicitTarget == date) explicitSource else null
    }

    val legacySourceDayOfWeek = sourceDayOfWeek?.takeIf { it in 1..7 } ?: return null
    val start = parseOverrideDate(startDate) ?: return null
    val end = parseOverrideDate(endDate) ?: return null
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    if (date.isBefore(normalizedStart) || date.isAfter(normalizedEnd)) return null
    // 旧记录只存了来源星期几，当初是按周一起算写下的，这个基准属于已落盘数据的语义，不跟随显示起始日
    return date
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusDays((legacySourceDayOfWeek - 1).toLong())
}

fun TemporaryScheduleOverride.targetDates(): List<LocalDate> {
    val explicitTarget = parseOverrideDate(targetDate)
    if (explicitTarget != null) return listOf(explicitTarget)

    val start = parseOverrideDate(startDate) ?: return emptyList()
    val end = parseOverrideDate(endDate) ?: return emptyList()
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    val days = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd).toInt()
    return (0..days).map { offset -> normalizedStart.plusDays(offset.toLong()) }
}

fun TemporaryScheduleOverride.cancelsCourseOn(date: LocalDate, course: CourseItem): Boolean {
    if (type != TemporaryScheduleOverrideType.CancelCourse) return false
    if (date !in targetDates()) return false
    val requiredCourseId = cancelCourseId?.takeIf { it.isNotBlank() }
    if (requiredCourseId != null && requiredCourseId != course.id) return false
    val start = cancelStartNode ?: return false
    val end = cancelEndNode ?: start
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    return course.time.startNode <= normalizedEnd && course.time.endNode >= normalizedStart
}

/** 只调部分节次时的节次区间；整天调课返回 null。 */
fun TemporaryScheduleOverride.makeUpNodeRange(): IntRange? {
    if (type != TemporaryScheduleOverrideType.MakeUp) return null
    val start = makeUpStartNode ?: makeUpEndNode ?: return null
    val end = makeUpEndNode ?: makeUpStartNode ?: return null
    return minOf(start, end)..maxOf(start, end)
}

/**
 * [course] 在 [date] 当天按哪一天的安排上；当天根本不上这门课时返回 null。
 *
 * [sourceDate] 是 [ScheduleDayResolution] 给出的整天来源日。整天调课时全天的课都来自来源日；
 * 只调部分节次时，区间内的课来自来源日，区间外的课仍是本日自己的，调一节课不会把整天都搬走。
 */
fun temporaryScheduleCourseSourceDate(
    date: LocalDate,
    course: CourseItem,
    sourceDate: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): LocalDate? {
    if (sourceDate == date) {
        return date.takeIf { course.time.dayOfWeek == date.dayOfWeek.value }
    }
    val range = matchingTemporaryScheduleOverride(date, overrides)?.makeUpNodeRange()
        ?: return sourceDate.takeIf { course.time.dayOfWeek == sourceDate.dayOfWeek.value }
    val inRange = course.time.startNode <= range.last && course.time.endNode >= range.first
    return when {
        inRange && course.time.dayOfWeek == sourceDate.dayOfWeek.value -> sourceDate
        !inRange && course.time.dayOfWeek == date.dayOfWeek.value -> date
        else -> null
    }
}

/** 这条规则挪课的目标节次；字段不全时返回 null。 */
fun TemporaryScheduleOverride.moveToNodeRange(): IntRange? {
    if (type != TemporaryScheduleOverrideType.MoveCourse) return null
    val start = moveToStartNode ?: return null
    val end = moveToEndNode ?: start
    return minOf(start, end)..maxOf(start, end)
}

/**
 * [course] 在 [date] 当天是不是被挪到别处去了。
 *
 * 被挪走的课在原来那天不该再出现，否则一门课会在两天同时露面。
 */
fun isCourseMovedAwayFrom(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean = overrides.any { rule ->
    rule.type == TemporaryScheduleOverrideType.MoveCourse &&
        rule.moveCourseId == course.id &&
        parseOverrideDate(rule.sourceDate) == date
}

/** [course] 是不是被单独挪到了 [date] 这一天——卡片上要标「调」的就是这种。 */
fun isCourseMovedTo(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean = overrides.any { rule ->
    rule.type == TemporaryScheduleOverrideType.MoveCourse &&
        rule.moveCourseId == course.id &&
        parseOverrideDate(rule.targetDate) == date
}

/**
 * 被挪到 [date] 当天的课，时间已改写到目标节次与当天的星期。
 *
 * [courseById] 由调用方提供，按 id 找出那门课的原件；找不到（课被删了）就跳过。
 * [isOriginallyActive] 判断这门课在它**原本那天**是不是真的上——原本那周就不上的课，
 * 挪过来也不该凭空多出一节。
 */
fun coursesMovedTo(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    courseById: (String) -> CourseItem?,
    isOriginallyActive: (CourseItem, LocalDate) -> Boolean = { _, _ -> true },
): List<CourseItem> =
    coursesMovedToWithOrigin(date, overrides, courseById, isOriginallyActive).map { it.first }

/** 同 [coursesMovedTo]，额外给出这门课原本是哪一天的，供需要标注「调课落位」的地方使用。 */
fun coursesMovedToWithOrigin(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
    courseById: (String) -> CourseItem?,
    isOriginallyActive: (CourseItem, LocalDate) -> Boolean = { _, _ -> true },
): List<Pair<CourseItem, LocalDate>> = overrides.mapNotNull { rule ->
    if (rule.type != TemporaryScheduleOverrideType.MoveCourse) return@mapNotNull null
    if (parseOverrideDate(rule.targetDate) != date) return@mapNotNull null
    val from = parseOverrideDate(rule.sourceDate) ?: return@mapNotNull null
    val courseId = rule.moveCourseId ?: return@mapNotNull null
    val course = courseById(courseId) ?: return@mapNotNull null
    if (!isOriginallyActive(course, from)) return@mapNotNull null
    val nodes = rule.moveToNodeRange() ?: return@mapNotNull null
    course.copy(
        time = course.time.copy(
            dayOfWeek = date.dayOfWeek.value,
            startNode = nodes.first,
            endNode = nodes.last,
        ),
    ) to from
}

/** 拖动调课要对调课列表做的改动：先删 [removeIds]，再写入 [upsert]。 */
data class CourseMovePlan(
    val removeIds: List<String> = emptyList(),
    val upsert: TemporaryScheduleOverride? = null,
)

/**
 * 拖动调课：把 [courseId] 在 [from] 这天显示的那一份挪到 [to] 的第 [toStartNode]..[toEndNode] 节。
 *
 * [from] 上的这门课可能本来就是从别天挪过来的（有一条目标日是 [from] 的挪课记录）。
 * 这时不能再叠一条「from → to」：隐藏原课只对当天原本排着的课生效，挪进来的那份不会被隐藏，
 * 拖一次就多出一份。要改写原来那条记录，让它从课程原本那天直接挪到新位置；
 * 挪回原本那天的原本节次就等于撤销，直接删掉记录。
 *
 * @param naturalStartNode 这门课在课表里原本的起始节，用来判断是不是挪回了原位
 * @param naturalEndNode 这门课在课表里原本的结束节
 */
fun planCourseMove(
    overrides: List<TemporaryScheduleOverride>,
    courseId: String,
    from: LocalDate,
    to: LocalDate,
    toStartNode: Int,
    toEndNode: Int,
    naturalStartNode: Int,
    naturalEndNode: Int,
    newId: () -> String,
): CourseMovePlan {
    val backToNaturalNodes = toStartNode == naturalStartNode && toEndNode == naturalEndNode
    val movedIn = overrides.lastOrNull { rule ->
        rule.type == TemporaryScheduleOverrideType.MoveCourse &&
            rule.moveCourseId == courseId &&
            parseOverrideDate(rule.targetDate) == from
    }
    if (movedIn != null) {
        val origin = parseOverrideDate(movedIn.sourceDate) ?: from
        if (to == origin && backToNaturalNodes) {
            return CourseMovePlan(removeIds = listOf(movedIn.id))
        }
        return CourseMovePlan(
            upsert = movedIn.copy(
                targetDate = to.toString(),
                moveToStartNode = toStartNode,
                moveToEndNode = toEndNode,
            ),
        )
    }
    if (to == from && backToNaturalNodes) return CourseMovePlan()
    return CourseMovePlan(
        upsert = TemporaryScheduleOverride(
            id = newId(),
            type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = from.toString(),
            targetDate = to.toString(),
            moveCourseId = courseId,
            moveToStartNode = toStartNode,
            moveToEndNode = toEndNode,
        ),
    )
}

/** 逐门停课/恢复要对调课列表做的改动：先删 [removeIds]，再依次写入 [upserts]。 */
data class CancelCoursePlan(
    val removeIds: List<String> = emptyList(),
    val upserts: List<TemporaryScheduleOverride> = emptyList(),
)

/** 只停 [date] 这天的 [course] 这一门：规则记下课程 id，同一时段的别的课不受牵连。 */
fun planCancelCourse(date: LocalDate, course: CourseItem, newId: () -> String): CancelCoursePlan =
    CancelCoursePlan(upserts = listOf(courseCancelRule(date, course, newId())))

/**
 * 恢复 [date] 这天被停掉的 [course]。
 *
 * 停掉它的可能是只针对它的规则，也可能是旧版按节次写的规则——那种会把同一时段的
 * 好几门一起停掉。恢复一门时删掉这些规则，同一天被顺带停掉的其他课（在 [dayCourses] 里）
 * 各补一条只针对自己的规则，保持停课状态。
 *
 * 规则跨了好几天（旧版的起止日期写法）时拆不开，返回 null，只能在规则列表里整条删除。
 */
fun planRestoreCourse(
    date: LocalDate,
    course: CourseItem,
    dayCourses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    newId: () -> String,
): CancelCoursePlan? {
    val matching = overrides.filter { it.cancelsCourseOn(date, course) }
    if (matching.isEmpty()) return CancelCoursePlan()
    if (matching.any { it.targetDates().size > 1 }) return null
    val remaining = overrides - matching.toSet()
    val keepCancelled = matching
        .filter { it.cancelCourseId.isNullOrBlank() }
        .flatMap { rule -> dayCourses.filter { rule.cancelsCourseOn(date, it) } }
        .filter { it.id != course.id && !isCourseTemporarilyCancelled(date, it, remaining) }
        .distinctBy { it.id }
    return CancelCoursePlan(
        removeIds = matching.map { it.id },
        upserts = keepCancelled.map { courseCancelRule(date, it, newId()) },
    )
}

private fun courseCancelRule(date: LocalDate, course: CourseItem, id: String) = TemporaryScheduleOverride(
    id = id,
    type = TemporaryScheduleOverrideType.CancelCourse,
    targetDate = date.toString(),
    cancelCourseId = course.id,
    cancelStartNode = course.time.startNode,
    cancelEndNode = course.time.endNode,
)

fun matchingTemporaryScheduleOverride(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): TemporaryScheduleOverride? {
    return overrides.asReversed().firstOrNull { it.containsDate(date) }
}

fun resolveTemporaryScheduleSourceDate(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): LocalDate {
    return matchingTemporaryScheduleOverride(date, overrides)?.sourceDateFor(date) ?: date
}

fun isCourseTemporarilyCancelled(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean {
    return overrides.any { it.cancelsCourseOn(date, course) }
}

fun filterTemporaryCancelledCourses(
    date: LocalDate,
    courses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
): List<CourseItem> {
    if (overrides.none { it.type == TemporaryScheduleOverrideType.CancelCourse }) return courses
    return courses.filterNot { course -> isCourseTemporarilyCancelled(date, course, overrides) }
}

fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
}

private fun parseOverrideDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
