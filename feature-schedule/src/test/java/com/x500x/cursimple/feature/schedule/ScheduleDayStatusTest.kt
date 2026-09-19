package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双击日期弹出的面板要把表头上能出现的东西全列出来。
 *
 * 表头一列就那么宽，节日名、「按10/10」这些多半显示不全，双击进来就是为了看完整的。
 * 少列一项，就意味着有信息只能在表头里被截断着看。
 */
class ScheduleDayStatusTest {

    @Test
    fun `什么都没有时只说照常上课`() {
        val statuses = scheduleDayStatuses(
            isToday = false,
            hasHoliday = false,
            makeUpWorkday = false,
            followsOtherDay = false,
        )

        assertEquals(listOf(ScheduleDayStatus.Normal), statuses)
    }

    @Test
    fun `今天但没有特殊情况时仍要说明照常上课`() {
        // 只显示「当前：今天」的话，看不出这天到底上不上课
        val statuses = scheduleDayStatuses(
            isToday = true,
            hasHoliday = false,
            makeUpWorkday = false,
            followsOtherDay = false,
        )

        assertEquals(listOf(ScheduleDayStatus.Today, ScheduleDayStatus.Normal), statuses)
    }

    @Test
    fun `放假时列出节日而不再说照常上课`() {
        val statuses = scheduleDayStatuses(
            isToday = false,
            hasHoliday = true,
            makeUpWorkday = false,
            followsOtherDay = false,
        )

        assertEquals(listOf(ScheduleDayStatus.Holiday), statuses)
    }

    @Test
    fun `调休补班单独成一条`() {
        val statuses = scheduleDayStatuses(
            isToday = false,
            hasHoliday = false,
            makeUpWorkday = true,
            followsOtherDay = false,
        )

        assertEquals(listOf(ScheduleDayStatus.MakeUpWorkday), statuses)
    }

    @Test
    fun `按别的日子上课单独成一条`() {
        // 表头那格只放得下「按10/10」，星期几从来显示不出来，这条必须有
        val statuses = scheduleDayStatuses(
            isToday = false,
            hasHoliday = false,
            makeUpWorkday = false,
            followsOtherDay = true,
        )

        assertEquals(listOf(ScheduleDayStatus.FollowsOtherDay), statuses)
    }

    @Test
    fun `几种情况同时成立时一条都不少`() {
        val statuses = scheduleDayStatuses(
            isToday = true,
            hasHoliday = false,
            makeUpWorkday = true,
            followsOtherDay = true,
        )

        assertEquals(
            listOf(
                ScheduleDayStatus.Today,
                ScheduleDayStatus.MakeUpWorkday,
                ScheduleDayStatus.FollowsOtherDay,
            ),
            statuses,
        )
    }

    @Test
    fun `表头能出现的每一项都有对应的状态`() {
        // 表头上那行小字目前只有这三种来源，加了新的种类必须同步加到面板里
        val headerTagKinds = listOf(
            ScheduleDayStatus.Holiday,
            ScheduleDayStatus.MakeUpWorkday,
            ScheduleDayStatus.FollowsOtherDay,
        )
        val covered = headerTagKinds.all { kind ->
            scheduleDayStatuses(
                isToday = false,
                hasHoliday = kind == ScheduleDayStatus.Holiday,
                makeUpWorkday = kind == ScheduleDayStatus.MakeUpWorkday,
                followsOtherDay = kind == ScheduleDayStatus.FollowsOtherDay,
            ).contains(kind)
        }

        assertTrue("表头上的每一种标记都要能在面板里看到", covered)
    }
}
