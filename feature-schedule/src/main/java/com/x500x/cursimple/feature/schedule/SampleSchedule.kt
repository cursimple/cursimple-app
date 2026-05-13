package com.x500x.cursimple.feature.schedule

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
 */
internal fun sampleManualCourses(): List<CourseItem> {
    val all = (1..16).toList()
    val odd = all.filter { it % 2 == 1 }
    val even = all.filter { it % 2 == 0 }
    val short = (5..12).toList()

    return listOf(
        // ───── 周一
        course(
            id = "sample-monday-math",
            title = "高等数学",
            teacher = "李教授",
            location = "教学楼A101",
            day = 1, start = 1, end = 2,
            weeks = all,
        ),
        // 同一时段、单周
        course(
            id = "sample-monday-c",
            title = "C 程序设计",
            teacher = "王老师",
            location = "实验楼B202",
            day = 1, start = 3, end = 4,
            weeks = odd,
        ),
        // 同一时段、双周（与上面同位置但相反周次）
        course(
            id = "sample-monday-ds",
            title = "数据结构",
            teacher = "张老师",
            location = "实验楼B202",
            day = 1, start = 3, end = 4,
            weeks = even,
        ),
        course(
            id = "sample-monday-python",
            title = "Python 入门",
            teacher = "赵老师",
            location = "机房C301",
            day = 1, start = 5, end = 6,
            weeks = short,
        ),

        // ───── 周二
        course(
            id = "sample-tuesday-physics",
            title = "大学物理",
            teacher = "陈教授",
            location = "教学楼A203",
            day = 2, start = 1, end = 3,
            weeks = all,
        ),
        course(
            id = "sample-tuesday-english",
            title = "英语听说",
            teacher = "Lisa",
            location = "外语楼D102",
            day = 2, start = 7, end = 8,
            weeks = all,
        ),

        // ───── 周三
        course(
            id = "sample-wednesday-linear",
            title = "线性代数",
            teacher = "刘老师",
            location = "教学楼A101",
            day = 3, start = 1, end = 2,
            weeks = odd,
        ),
        course(
            id = "sample-wednesday-prob",
            title = "概率论",
            teacher = "林老师",
            location = "教学楼A101",
            day = 3, start = 1, end = 2,
            weeks = even,
        ),
        course(
            id = "sample-wednesday-marx",
            title = "马克思主义基本原理",
            teacher = "周老师",
            location = "教学楼A301",
            day = 3, start = 5, end = 6,
            weeks = all,
        ),

        // ───── 周四
        course(
            id = "sample-thursday-os",
            title = "操作系统",
            teacher = "孙教授",
            location = "实验楼B305",
            day = 4, start = 3, end = 5,
            weeks = all,
        ),
        course(
            id = "sample-thursday-advprog",
            title = "高级编程技术",
            teacher = "吴老师",
            location = "实验楼B202",
            day = 4, start = 9, end = 10,
            weeks = all,
        ),

        // ───── 周五
        course(
            id = "sample-friday-network",
            title = "计算机网络",
            teacher = "郑教授",
            location = "实验楼B203",
            day = 5, start = 1, end = 2,
            weeks = all,
        ),
        course(
            id = "sample-friday-pe",
            title = "体育（羽毛球）",
            teacher = "周教练",
            location = "体育馆",
            day = 5, start = 5, end = 6,
            weeks = all,
        ),

        // ───── 周六
        course(
            id = "sample-saturday-elab",
            title = "电子技术实验",
            teacher = "李实验员",
            location = "实验楼E101",
            day = 6, start = 3, end = 4,
            weeks = all,
        ),

        // ───── 周日
        course(
            id = "sample-sunday-culture",
            title = "中西文化对比（慕课）",
            teacher = "教务处",
            location = "线上",
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
