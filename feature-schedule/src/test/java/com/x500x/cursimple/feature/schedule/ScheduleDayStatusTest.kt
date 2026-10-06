package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expanded day status must include every potentially truncated header declaration. */
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
        // Today alone does not describe whether classes run.
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
        // Expose source weekday in full even when the header has room for its date only.
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
        // Keep day-sheet status in sync with every header status type.
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
