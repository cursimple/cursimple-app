package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule

/**
 * 导入前的课表比对结果。
 *
 * 教务系统改了课，用户看到的只是「同步成功」，到底动了哪几门全靠自己翻。
 * 这里在写进去之前先算出差异，让用户能先看一眼再决定是覆盖还是另存一份。
 */
data class ScheduleImportDiff(
    val added: List<CourseItem>,
    val removed: List<CourseItem>,
    /** 两边都有、没有变化的门数。 */
    val keptCount: Int,
) {
    val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    val changeCount: Int get() = added.size + removed.size
}

/**
 * 比对新旧课表。
 *
 * 课程 id 在不同次抓取之间不稳定（教务系统常常每次都换一串），按 id 比会把整张表
 * 判成「全删全增」。这里按「课名 + 星期 + 起止节次 + 周次 + 地点」当身份，
 * 同一门课改了地点算一增一删——这正是用户想知道的那种变化。
 */
fun diffSchedules(current: TermSchedule?, incoming: TermSchedule?): ScheduleImportDiff {
    val before = current.visibleCourses().groupingBy { it.identity() }.eachCount()
    val after = incoming.visibleCourses().groupingBy { it.identity() }.eachCount()

    val added = mutableListOf<CourseItem>()
    val removed = mutableListOf<CourseItem>()
    var kept = 0

    // 同一身份可能出现多次（单双周分两条之类），按条数差取增减，不是简单的有无
    incoming.visibleCourses().forEach { course ->
        val identity = course.identity()
        val surplus = after.getValue(identity) - (before[identity] ?: 0)
        if (surplus > 0 && added.count { it.identity() == identity } < surplus) {
            added += course
        } else {
            kept++
        }
    }
    current.visibleCourses().forEach { course ->
        val identity = course.identity()
        val shortfall = (before.getValue(identity)) - (after[identity] ?: 0)
        if (shortfall > 0 && removed.count { it.identity() == identity } < shortfall) {
            removed += course
        }
    }

    return ScheduleImportDiff(added = added, removed = removed, keptCount = kept)
}

private fun TermSchedule?.visibleCourses(): List<CourseItem> =
    this?.dailySchedules.orEmpty().flatMap { it.courses }.filterNot { it.hidden }

/** 与 id 无关的课程身份。 */
private fun CourseItem.identity(): String = listOf(
    title.trim(),
    time.dayOfWeek.toString(),
    time.startNode.toString(),
    time.endNode.toString(),
    weeks.sorted().joinToString(","),
    location.trim(),
).joinToString("|")
