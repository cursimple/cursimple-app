package com.x500x.cursimple.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动检查更新该不该查：每次启动先查一次，之后查成的隔半小时、没查成的隔五分钟。 */
class UpdatePollStateTest {

    private val minute = 60 * 1000L
    private val now = 1_000_000_000L

    @Test
    fun `每次启动都先查一次，哪怕刚查过`() {
        assertTrue(
            autoUpdateCheckDue(
                nowMillis = now,
                checkedThisLaunch = false,
                lastCheckAtMillis = now - minute,
                lastFailureAtMillis = 0L,
            ),
        )
    }

    @Test
    fun `这次启动查过了，半小时内不再查`() {
        assertFalse(autoUpdateCheckDue(now, true, lastCheckAtMillis = now - 10 * minute, lastFailureAtMillis = 0L))
        assertTrue(autoUpdateCheckDue(now, true, lastCheckAtMillis = now - 31 * minute, lastFailureAtMillis = 0L))
    }

    @Test
    fun `没查成的隔五分钟就重试，不用等半小时`() {
        val lastCheck = now - 60 * minute
        assertFalse(autoUpdateCheckDue(now, true, lastCheck, lastFailureAtMillis = now - 2 * minute))
        assertTrue(autoUpdateCheckDue(now, true, lastCheck, lastFailureAtMillis = now - 6 * minute))
    }

    @Test
    fun `失败之后又查成了，按查成的时间算`() {
        assertFalse(
            autoUpdateCheckDue(now, true, lastCheckAtMillis = now - 3 * minute, lastFailureAtMillis = now - 10 * minute),
        )
    }

    @Test
    fun `切换了测试版开关马上重查`() {
        assertTrue(
            autoUpdateCheckDue(
                now, true,
                lastCheckAtMillis = now - minute,
                lastFailureAtMillis = 0L,
                channelChanged = true,
            ),
        )
    }
}
