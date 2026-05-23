package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAlarmGuardSchedulerTest {
    @Test
    fun `guard plan creates three five-minute guard slots`() {
        val now = 12_345L
        val plan = WidgetAlarmGuardScheduler.schedulePlan(nowElapsedRealtime = now)

        assertEquals(WidgetAlarmGuardScheduler.GUARD_COUNT, plan.size)
        assertEquals(
            listOf(
                now + WidgetAlarmGuardScheduler.GUARD_INTERVAL_MILLIS,
                now + WidgetAlarmGuardScheduler.GUARD_INTERVAL_MILLIS * 2,
                now + WidgetAlarmGuardScheduler.GUARD_INTERVAL_MILLIS * 3,
            ),
            plan.map { it.triggerElapsedRealtime },
        )
    }

    @Test
    fun `guard plan keeps stable pending intent identities across reschedules`() {
        val early = WidgetAlarmGuardScheduler.schedulePlan(nowElapsedRealtime = 1L)
        val late = WidgetAlarmGuardScheduler.schedulePlan(
            nowElapsedRealtime = WidgetAlarmGuardScheduler.GUARD_INTERVAL_MILLIS - 1,
        )

        assertEquals(early.map { it.index }, late.map { it.index })
        assertEquals(early.map { it.requestCode }, late.map { it.requestCode })
    }

    @Test
    fun `guard slots use distinct request codes`() {
        val plan = WidgetAlarmGuardScheduler.schedulePlan(
            nowElapsedRealtime = WidgetAlarmGuardScheduler.GUARD_INTERVAL_MILLIS * 42,
        )

        assertEquals(
            plan.size,
            plan.map { it.requestCode }.distinct().size,
        )
        assertTrue(plan.all { it.requestCode >= WidgetAlarmGuardScheduler.REQUEST_CODE_BASE })
    }
}
