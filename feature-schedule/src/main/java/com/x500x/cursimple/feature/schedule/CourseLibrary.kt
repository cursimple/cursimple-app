package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.util.Locale

/** 课程从哪来。改过的插件课会存成同 id 的手动课，来源随之变成 [Manual]。 */
internal enum class CourseSource { Plugin, Manual }

/** 课程库里的一条。 */
internal data class CourseLibraryEntry(
    val course: CourseItem,
    val source: CourseSource,
    /** 原件来自插件，眼下显示的是用户改过的那一份。 */
    val overridesPlugin: Boolean = false,
) {
    /** 插件课也能改，改完会转成手动课，所以这里一律可编辑。 */
    val editable: Boolean get() = true

    /** 插件原件删不掉，下次同步还会回来；改过的那份只能还原成插件数据。 */
    val removable: Boolean get() = source == CourseSource.Manual && !overridesPlugin

    /** 丢掉手动改动、退回插件下发的原样。 */
    val restorable: Boolean get() = overridesPlugin
}

/** 课程库的排序方式。文案由界面层渲染。 */
internal enum class CourseSortMode { ByWeekday, ByTitle, BySource }

/**
 * 合并插件课表与手动课程。
 *
 * 只用于展示，因此排除仅供提醒占位的条目；两边出现同一个 id 时以手动课程为准，
 * 手动课程是用户自己改过的那一份，同时记下它盖掉了一份插件原件，
 * 界面据此提供"还原插件数据"而不是"删除"。
 */
internal fun buildCourseLibrary(
    pluginCourses: List<CourseItem>,
    manualCourses: List<CourseItem>,
): List<CourseLibraryEntry> {
    val manual = manualCourses.visibleScheduleCourses()
    val pluginIds = pluginCourses.mapTo(mutableSetOf()) { it.id }
    val manualIds = manual.mapTo(mutableSetOf()) { it.id }
    val plugin = pluginCourses.visibleScheduleCourses().filterNot { it.id in manualIds }
    return plugin.map { CourseLibraryEntry(it, CourseSource.Plugin) } +
        manual.map { CourseLibraryEntry(it, CourseSource.Manual, overridesPlugin = it.id in pluginIds) }
}

/** 课名、教师、地点任一命中即算命中；查询为空时全部命中。 */
internal fun matchesCourseQuery(course: CourseItem, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return true
    return listOf(course.title, course.teacher, course.location)
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}

/** 按指定方式排序，同序时按星期、节次、课名兜底，保证顺序稳定。 */
internal fun sortCourseLibrary(
    entries: List<CourseLibraryEntry>,
    mode: CourseSortMode,
): List<CourseLibraryEntry> {
    val fallback = compareBy<CourseLibraryEntry>(
        { it.course.time.dayOfWeek },
        { it.course.time.startNode },
        { it.course.title },
        { it.course.id },
    )
    return when (mode) {
        CourseSortMode.ByWeekday -> entries.sortedWith(fallback)
        CourseSortMode.ByTitle -> entries.sortedWith(compareBy<CourseLibraryEntry> { it.course.title }.then(fallback))
        CourseSortMode.BySource -> entries.sortedWith(compareBy<CourseLibraryEntry> { it.source }.then(fallback))
    }
}

/**
 * 按显示列序把课程分到每个星期下。
 * [columnDayOfWeeks] 与课表网格用同一份列序，避免两处显示的星期顺序不一致。
 */
internal fun groupCourseLibraryByWeekday(
    entries: List<CourseLibraryEntry>,
    columnDayOfWeeks: List<Int>,
): List<Pair<Int, List<CourseLibraryEntry>>> {
    val byDay = entries.groupBy { it.course.time.dayOfWeek }
    val ordered = columnDayOfWeeks + (1..7).filterNot { it in columnDayOfWeeks }
    return ordered.mapNotNull { day ->
        byDay[day]?.takeIf { it.isNotEmpty() }?.let { day to it }
    }
}
