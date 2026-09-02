package com.x500x.cursimple.feature.schedule

import android.content.Context
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot

/**
 * 用于预览的示例课表。覆盖以下场景：
 * - 普通全周课（高数、大物等）
 * - 单/双周交替课（同一时段不同周不同课）
 * - 多节连堂（操作系统 3 节连）
 * - 短期课（仅 5-12 周）
 * - 早 / 中 / 晚不同节次
 * - 周末课
 * 课程名、教师名与地点按当前语言取自资源，只有语言无关的英文名直接内联。
 */
internal fun sampleManualCourses(context: Context): List<CourseItem> {
    val all = (1..16).toList()
    val odd = all.filter { it % 2 == 1 }
    val even = all.filter { it % 2 == 0 }
    val short = (5..12).toList()

    return listOf(
        // ───── 周一
        course(
            id = "sample-monday-math",
            title = context.getString(R.string.schedule_sample_monday_math_title),
            teacher = context.getString(R.string.schedule_sample_monday_math_teacher),
            location = context.getString(R.string.schedule_sample_monday_math_location),
            day = 1, start = 1, end = 2,
            weeks = all,
        ),
        // 同一时段、单周
        course(
            id = "sample-monday-c",
            title = context.getString(R.string.schedule_sample_monday_c_title),
            teacher = context.getString(R.string.schedule_sample_monday_c_teacher),
            location = context.getString(R.string.schedule_sample_monday_c_location),
            day = 1, start = 3, end = 4,
            weeks = odd,
        ),
        // 同一时段、双周（与上面同位置但相反周次）
        course(
            id = "sample-monday-ds",
            title = context.getString(R.string.schedule_sample_monday_ds_title),
            teacher = context.getString(R.string.schedule_sample_monday_ds_teacher),
            location = context.getString(R.string.schedule_sample_monday_ds_location),
            day = 1, start = 3, end = 4,
            weeks = even,
        ),
        course(
            id = "sample-monday-python",
            title = context.getString(R.string.schedule_sample_monday_python_title),
            teacher = context.getString(R.string.schedule_sample_monday_python_teacher),
            location = context.getString(R.string.schedule_sample_monday_python_location),
            day = 1, start = 5, end = 6,
            weeks = short,
        ),

        // ───── 周二
        course(
            id = "sample-tuesday-physics",
            title = context.getString(R.string.schedule_sample_tuesday_physics_title),
            teacher = context.getString(R.string.schedule_sample_tuesday_physics_teacher),
            location = context.getString(R.string.schedule_sample_tuesday_physics_location),
            day = 2, start = 1, end = 3,
            weeks = all,
        ),
        course(
            id = "sample-tuesday-english",
            title = context.getString(R.string.schedule_sample_tuesday_english_title),
            teacher = "Lisa",
            location = context.getString(R.string.schedule_sample_tuesday_english_location),
            day = 2, start = 7, end = 8,
            weeks = all,
        ),

        // ───── 周三
        course(
            id = "sample-wednesday-linear",
            title = context.getString(R.string.schedule_sample_wednesday_linear_title),
            teacher = context.getString(R.string.schedule_sample_wednesday_linear_teacher),
            location = context.getString(R.string.schedule_sample_wednesday_linear_location),
            day = 3, start = 1, end = 2,
            weeks = odd,
        ),
        course(
            id = "sample-wednesday-prob",
            title = context.getString(R.string.schedule_sample_wednesday_prob_title),
            teacher = context.getString(R.string.schedule_sample_wednesday_prob_teacher),
            location = context.getString(R.string.schedule_sample_wednesday_prob_location),
            day = 3, start = 1, end = 2,
            weeks = even,
        ),
        course(
            id = "sample-wednesday-marx",
            title = context.getString(R.string.schedule_sample_wednesday_marx_title),
            teacher = context.getString(R.string.schedule_sample_wednesday_marx_teacher),
            location = context.getString(R.string.schedule_sample_wednesday_marx_location),
            day = 3, start = 5, end = 6,
            weeks = all,
        ),

        // ───── 周四
        course(
            id = "sample-thursday-os",
            title = context.getString(R.string.schedule_sample_thursday_os_title),
            teacher = context.getString(R.string.schedule_sample_thursday_os_teacher),
            location = context.getString(R.string.schedule_sample_thursday_os_location),
            day = 4, start = 3, end = 5,
            weeks = all,
        ),
        course(
            id = "sample-thursday-advprog",
            title = context.getString(R.string.schedule_sample_thursday_advprog_title),
            teacher = context.getString(R.string.schedule_sample_thursday_advprog_teacher),
            location = context.getString(R.string.schedule_sample_thursday_advprog_location),
            day = 4, start = 9, end = 10,
            weeks = all,
        ),

        // ───── 周五
        course(
            id = "sample-friday-network",
            title = context.getString(R.string.schedule_sample_friday_network_title),
            teacher = context.getString(R.string.schedule_sample_friday_network_teacher),
            location = context.getString(R.string.schedule_sample_friday_network_location),
            day = 5, start = 1, end = 2,
            weeks = all,
        ),
        course(
            id = "sample-friday-pe",
            title = context.getString(R.string.schedule_sample_friday_pe_title),
            teacher = context.getString(R.string.schedule_sample_friday_pe_teacher),
            location = context.getString(R.string.schedule_sample_friday_pe_location),
            day = 5, start = 5, end = 6,
            weeks = all,
        ),

        // ───── 周六
        course(
            id = "sample-saturday-elab",
            title = context.getString(R.string.schedule_sample_saturday_elab_title),
            teacher = context.getString(R.string.schedule_sample_saturday_elab_teacher),
            location = context.getString(R.string.schedule_sample_saturday_elab_location),
            day = 6, start = 3, end = 4,
            weeks = all,
        ),

        // ───── 周日
        course(
            id = "sample-sunday-culture",
            title = context.getString(R.string.schedule_sample_sunday_culture_title),
            teacher = context.getString(R.string.schedule_sample_sunday_culture_teacher),
            location = context.getString(R.string.schedule_sample_sunday_culture_location),
            day = 7, start = 1, end = 2,
            weeks = odd,
        ),
    )
}

private fun course(
    id: String,
    title: String,
    teacher: String,
    location: String,
    day: Int,
    start: Int,
    end: Int,
    weeks: List<Int>,
): CourseItem = CourseItem(
    id = id,
    title = title,
    teacher = teacher,
    location = location,
    weeks = weeks,
    time = CourseTimeSlot(dayOfWeek = day, startNode = start, endNode = end),
)
